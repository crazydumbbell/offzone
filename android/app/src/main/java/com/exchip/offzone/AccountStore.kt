package com.exchip.offzone

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import com.google.firebase.functions.FirebaseFunctions
import com.revenuecat.purchases.*
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.models.StoreProduct
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.File
import java.security.MessageDigest

// Account services never own or disable local restrictions or free recovery.
data class AccountState(
    val configured: Boolean = false, val uid: String? = null, val email: String = "",
    val name: String = "", val emailVerified: Boolean = false, val providers: Set<String> = emptySet(), val verified: Boolean = false,
    val busy: Boolean = false, val error: String? = null, val notice: String? = null,
    val savedGoal: String? = null, val pro: Boolean = false, val proExpiresAt: Long? = null,
    val managementURL: String? = null, val purchasesConfigured: Boolean = false, val packages: List<Package> = emptyList(),
    val unlockBalance: Int? = null, val unlockProduct: StoreProduct? = null,
    val pendingUnlockSessionID: String? = null,
)

internal object AccountIdentity {
    fun verified(providers: Set<String>, emailVerified: Boolean) =
        "apple.com" in providers || "google.com" in providers || ("password" in providers && emailVerified)
    fun accepts(currentUID: String?, requestedUID: String?, currentGeneration: Long, requestedGeneration: Long) =
        currentUID != null && currentUID == requestedUID && currentGeneration == requestedGeneration
    fun proAccess(uid: String?, entitlementUID: String?, active: Boolean, expiresAt: Long?, now: Long) =
        uid != null && uid == entitlementUID && active && (expiresAt == null || expiresAt > now)
    fun sessionID(value: String) = value.matches(Regex("[a-f0-9]{64}"))
    val goals = setOf("work", "rest", "presence", "personal")
}

class AccountStore(context: Context, firebaseApp: FirebaseApp? = null) {
    private val context = context.applicationContext
    // A supplied Firebase app isolates emulator tests from the process-wide billing identity.
    private val purchasesAllowed = firebaseApp == null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val purchaseLock = Mutex()
    private val mutable = MutableStateFlow(AccountState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var purchaseUID: String? = null
    private var entitlementUID: String? = null
    private var identityJob: Job? = null
    private var expiryJob: Job? = null
    private val auth: FirebaseAuth?
    private val db: FirebaseFirestore?
    private val listener = FirebaseAuth.AuthStateListener { identityChanged() }
    val googleConfigured get() = state.value.configured && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    val offersEnabled get() = BuildConfig.PRO_OFFER_READY && BuildConfig.PRO_ENTITLEMENT_ID.isNotBlank() &&
        BuildConfig.PRO_OFFERING_ID.isNotBlank() && https(BuildConfig.TERMS_URL) && https(BuildConfig.PRIVACY_URL)
    val unlockPassEnabled get() = BuildConfig.UNLOCK_PASS_READY && BuildConfig.UNLOCK_PRODUCT_ID.isNotBlank()
    val hasProAccess get() = AccountIdentity.proAccess(currentPurchaseUID(), entitlementUID,
        state.value.pro, state.value.proExpiresAt, System.currentTimeMillis())

    init {
        val configured = listOf(BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_PROJECT_ID).all { it.isNotBlank() }
        val app = firebaseApp ?: if (configured) FirebaseApp.getApps(this.context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME } ?: FirebaseApp.initializeApp(
            this.context, FirebaseOptions.Builder().setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID).setProjectId(BuildConfig.FIREBASE_PROJECT_ID).build()) else null
        auth = app?.let(FirebaseAuth::getInstance)
        db = app?.let(FirebaseFirestore::getInstance)
        db?.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        mutable.update { it.copy(configured = app != null) }
        auth?.addAuthStateListener(listener)
    }

    private fun message(id: Int) = context.getString(id)
    private fun fail(id: Int): Nothing = throw IllegalStateException(message(id))
    private fun user() = auth?.currentUser ?: fail(R.string.account_identity_error)
    private fun verifiedUser(): FirebaseUser = user().also {
        if (!AccountIdentity.verified(it.providerData.map { p -> p.providerId }.toSet(), it.isEmailVerified))
            fail(R.string.account_verify_required)
    }
    private fun same(uid: String, stamp: Long) = AccountIdentity.accepts(auth?.currentUser?.uid, uid, generation, stamp)
    private fun currentPurchaseUID(): String? = auth?.currentUser?.uid?.takeIf {
        state.value.verified && purchaseUID == it && Purchases.isConfigured && Purchases.sharedInstance.appUserID == it
    }
    private fun identityChanged(force: Boolean = false) {
        val user = auth?.currentUser
        val providers = user?.providerData?.map { it.providerId }?.toSet().orEmpty()
        val verified = AccountIdentity.verified(providers, user?.isEmailVerified == true)
        if (!force && identityJob != null && state.value.uid == user?.uid && state.value.verified == verified && state.value.providers == providers) return
        val stamp = ++generation
        purchaseUID = null; entitlementUID = null; expiryJob?.cancel()
        mutable.update { AccountState(configured = it.configured, uid = user?.uid, email = user?.email.orEmpty(),
            name = user?.displayName ?: user?.email.orEmpty(), emailVerified = user?.isEmailVerified == true, providers = providers, verified = verified,
            busy = it.busy, error = it.error, notice = it.notice) }
        identityJob = scope.launch {
            purchaseLock.withLock {
                if (generation != stamp) return@withLock
                try {
                    if (user != null && verified) {
                        val uid = user.uid
                        if (purchasesAllowed && !Purchases.isConfigured && BuildConfig.REVENUECAT_PUBLIC_KEY.startsWith("goog_")) {
                            Purchases.configure(PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_PUBLIC_KEY)
                                .appUserID(uid).entitlementVerificationMode(EntitlementVerificationMode.INFORMATIONAL).build())
                        }
                        if (purchasesAllowed && Purchases.isConfigured) {
                            val info = Purchases.sharedInstance.awaitLogIn(uid).customerInfo
                            if (!same(uid, stamp)) return@withLock
                            purchaseUID = uid
                            mutable.update { it.copy(purchasesConfigured = true) }
                            applyInfo(info, uid, stamp)
                            // Listener events are only invalidations: their buffered value can belong to an old identity.
                            Purchases.sharedInstance.updatedCustomerInfoListener = com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener { refresh() }
                        }
                        val profile = db!!.collection("users").document(uid).get(Source.SERVER).await()
                        if (same(uid, stamp)) mutable.update { it.copy(savedGoal = profile.getString("goal")?.takeIf(AccountIdentity.goals::contains),
                            pendingUnlockSessionID = pending(uid)) }
                    } else if (purchasesAllowed && Purchases.isConfigured && !Purchases.sharedInstance.isAnonymous) {
                        Purchases.sharedInstance.updatedCustomerInfoListener = null
                        Purchases.sharedInstance.awaitLogOut()
                    }
                } catch (e: Exception) { if (generation == stamp) report(e) }
            }
        }
    }

    private fun report(e: Exception) {
        if (e is CancellationException || e is GetCredentialCancellationException ||
            (e is FirebaseAuthException && e.errorCode == "ERROR_WEB_CONTEXT_CANCELED")) return
        if (e is PurchasesTransactionException && e.userCancelled) { notice(R.string.account_purchase_cancelled); return }
        if (e is PurchasesException && e.code == PurchasesErrorCode.PaymentPendingError) { notice(R.string.account_purchase_pending); return }
        val text = if (e is FirebaseAuthUserCollisionException) message(R.string.account_collision) else e.localizedMessage ?: message(R.string.account_unavailable)
        mutable.update { it.copy(error = text) }
    }
    private fun action(block: suspend () -> Unit) {
        if (!state.value.configured || state.value.busy) return
        mutable.update { it.copy(busy = true, error = null, notice = null) }
        scope.launch {
            try { auth?.useAppLanguage(); block() }
            catch (e: Exception) { report(e) }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private fun authAction(block: suspend () -> Unit) = action {
        try { block() } finally { identityChanged(force = true); identityJob?.join() }
        loadOffersInternal()
    }
    private fun email(value: String): String = value.trim().also {
        if (it.length > 254 || !android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches()) fail(R.string.account_invalid_email)
    }
    private fun password(value: String) { if (value.length !in 8..4096) fail(R.string.account_invalid_password) }
    fun signIn(email: String, password: String) = authAction {
        check(auth!!.currentUser == null); if (password.isEmpty()) fail(R.string.account_invalid_password)
        auth.signInWithEmailAndPassword(email(email), password).await()
    }
    fun createAccount(email: String, password: String) = authAction {
        check(auth!!.currentUser == null); password(password)
        auth.createUserWithEmailAndPassword(email(email), password).await().user!!.sendEmailVerification().await()
        notice(R.string.account_verification_sent)
    }
    fun linkEmail(email: String, password: String) = authAction {
        val user = verifiedUser(); val uid = user.uid; password(password)
        user.linkWithCredential(EmailAuthProvider.getCredential(email(email), password)).await()
        check(auth?.currentUser?.uid == uid)
        if (!user.isEmailVerified) user.sendEmailVerification().await()
        notice(R.string.account_linked)
    }
    fun sendVerification() = authAction { user().sendEmailVerification().await(); notice(R.string.account_verification_sent) }
    fun reloadVerification() = authAction {
        val user = user(); user.reload().await(); user.getIdToken(true).await()
        notice(if (user.isEmailVerified) R.string.account_verified else R.string.account_verify_required)
    }
    fun resetPassword(email: String) = action {
        try { auth!!.sendPasswordResetEmail(email(email)).await() }
        catch (e: FirebaseAuthException) { if (e.errorCode != "ERROR_USER_NOT_FOUND") throw e }
        notice(R.string.account_reset_sent)
    }
    private suspend fun googleCredential(activity: Activity): AuthCredential {
        if (!googleConfigured) fail(R.string.account_unavailable)
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val credential = try {
            CredentialManager.create(activity).getCredential(activity,
                GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
        } catch (_: NoCredentialException) { fail(R.string.account_google_credential_missing) }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            fail(R.string.account_identity_error)
        return GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
    }
    fun google(activity: Activity, linking: Boolean = false, deleting: Boolean = false) = authAction {
        val previous = auth!!.currentUser
        if (linking || deleting) verifiedUser() else check(previous == null)
        val credential = googleCredential(activity)
        check(auth.currentUser?.uid == previous?.uid)
        when {
            deleting -> { check("apple.com" !in state.value.providers); previous!!.reauthenticate(credential).await(); delete(previous) }
            linking -> { previous!!.linkWithCredential(credential).await(); check(auth.currentUser?.uid == previous.uid); notice(R.string.account_linked) }
            else -> auth.signInWithCredential(credential).await()
        }
    }
    fun apple(activity: Activity, linking: Boolean = false, deleting: Boolean = false) = authAction {
        val previous = auth!!.currentUser
        if (linking) verifiedUser() else if (deleting) user() else check(previous == null)
        val provider = OAuthProvider.newBuilder("apple.com", auth).setScopes(listOf("email", "name")).build()
        val result = when {
            deleting -> previous!!.startActivityForReauthenticateWithProvider(activity, provider).await()
            linking -> previous!!.startActivityForLinkWithProvider(activity, provider).await()
            else -> (auth.pendingAuthResult ?: auth.startActivityForSignInWithProvider(activity, provider)).await()
        }
        if (linking || deleting) check(auth.currentUser?.uid == previous?.uid && result.user?.uid == previous?.uid)
        if (deleting) {
            val token = (result.credential as? OAuthCredential)?.accessToken ?: fail(R.string.account_identity_error)
            auth.revokeAccessToken(token).await(); delete(previous!!)
        } else if (linking) notice(R.string.account_linked)
    }
    fun deleteWithPassword(password: String) = authAction {
        val user = user(); check("apple.com" !in state.value.providers)
        user.reauthenticate(EmailAuthProvider.getCredential(user.email ?: fail(R.string.account_identity_error), password)).await()
        delete(user)
    }
    private suspend fun delete(user: FirebaseUser) {
        check(auth?.currentUser?.uid == user.uid)
        db!!.collection("users").document(user.uid).delete().await()
        check(auth?.currentUser?.uid == user.uid)
        user.delete().await() // Existing Auth onDelete trigger removes the RevenueCat customer.
        pendingFile(user.uid).delete()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }
    fun signOut() = authAction {
        auth!!.signOut(); identityChanged()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }
    fun saveGoal(goal: String) = action {
        require(goal in AccountIdentity.goals); val uid = verifiedUser().uid; val stamp = generation
        db!!.collection("users").document(uid).set(mapOf("goal" to goal, "updatedAt" to FieldValue.serverTimestamp())).await()
        if (same(uid, stamp)) mutable.update { it.copy(savedGoal = goal) }
    }
    private fun notice(id: Int) { mutable.update { it.copy(notice = message(id)) } }
    fun refresh() = action {
        identityJob?.join()
        val uid = currentPurchaseUID() ?: return@action; val stamp = generation
        purchaseLock.withLock { applyInfo(Purchases.sharedInstance.awaitCustomerInfo(), uid, stamp) }
        loadOffersInternal()
        if (unlockPassEnabled) refreshBalance(uid, stamp)
    }
    fun loadOfferings() = action { identityJob?.join(); loadOffersInternal() }
    private suspend fun loadOffersInternal() {
        val uid = currentPurchaseUID() ?: return; val stamp = generation
        if (!offersEnabled) return
        val offerings = Purchases.sharedInstance.awaitOfferings()
        val plans = offerings[BuildConfig.PRO_OFFERING_ID]?.availablePackages.orEmpty().filter {
            it.packageType in setOf(PackageType.ANNUAL, PackageType.MONTHLY) && it.product.type == ProductType.SUBS
        }
        if (same(uid, stamp)) mutable.update { it.copy(packages = plans) }
    }
    fun purchase(activity: Activity, plan: Package) = action {
        val uid = currentPurchaseUID() ?: fail(R.string.account_verify_required); val stamp = generation
        check(offersEnabled && !hasProAccess && state.value.packages.any { it === plan })
        purchaseLock.withLock {
            check(same(uid, stamp) && currentPurchaseUID() == uid)
            applyInfo(Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, plan).build()).customerInfo, uid, stamp)
        }
    }
    fun restorePurchases() = action {
        identityJob?.join(); val uid = currentPurchaseUID() ?: fail(R.string.account_unavailable); val stamp = generation
        purchaseLock.withLock {
            check(same(uid, stamp) && currentPurchaseUID() == uid)
            applyInfo(Purchases.sharedInstance.awaitRestore(), uid, stamp)
        }
        if (same(uid, stamp)) notice(if (hasProAccess) R.string.account_pro_active else R.string.account_no_purchase)
    }
    private fun applyInfo(info: CustomerInfo, uid: String, stamp: Long) {
        if (!same(uid, stamp) || currentPurchaseUID() != uid) return
        val entitlement = info.entitlements.active[BuildConfig.PRO_ENTITLEMENT_ID]
        entitlementUID = uid
        val active = info.entitlements.verification.isVerified && entitlement != null && (BuildConfig.DEBUG || !entitlement.isSandbox)
        val expires = entitlement?.expirationDate?.time
        mutable.update { it.copy(pro = AccountIdentity.proAccess(uid, uid, active, expires, System.currentTimeMillis()), proExpiresAt = expires,
            managementURL = info.managementURL?.toString()?.takeIf(::https)) }
        expiryJob?.cancel()
        if (state.value.pro && expires != null) expiryJob = scope.launch {
            delay((expires - System.currentTimeMillis()).coerceAtLeast(0))
            if (same(uid, stamp)) { mutable.update { it.copy(pro = false) }; refresh() }
        }
    }
    fun manageSubscription(activity: Activity) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.value.managementURL ?: "https://play.google.com/store/account/subscriptions")))
    }
    private fun pendingFile(uid: String) = AtomicFile(File(context.noBackupFilesDir, "unlock-" +
        MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }))
    private fun pending(uid: String): String? {
        val file = pendingFile(uid)
        if (!file.baseFile.exists()) return null
        return file.readFully().toString(Charsets.UTF_8).also { check(AccountIdentity.sessionID(it)) }
    }
    private fun persistPending(uid: String, sessionID: String) {
        val file = pendingFile(uid); val stream = file.startWrite()
        try { stream.write(sessionID.toByteArray()); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    private suspend fun refreshBalance(uid: String, stamp: Long) {
        Purchases.sharedInstance.invalidateVirtualCurrenciesCache()
        val currencies = Purchases.sharedInstance.awaitGetVirtualCurrencies()
        if (same(uid, stamp)) mutable.update { it.copy(unlockBalance = currencies["PASS"]?.balance ?: 0) }
    }
    fun loadUnlockPass() = action {
        if (!unlockPassEnabled) return@action
        identityJob?.join(); val uid = currentPurchaseUID() ?: return@action; val stamp = generation
        val product = Purchases.sharedInstance.awaitGetProducts(listOf(BuildConfig.UNLOCK_PRODUCT_ID), ProductType.INAPP).firstOrNull()
        if (same(uid, stamp)) mutable.update { it.copy(unlockProduct = product) }
        refreshBalance(uid, stamp)
    }
    fun purchaseUnlockPass(activity: Activity) = action {
        check(unlockPassEnabled); val uid = currentPurchaseUID() ?: fail(R.string.account_unavailable); val stamp = generation
        val product = state.value.unlockProduct ?: fail(R.string.account_unavailable)
        purchaseLock.withLock {
            check(same(uid, stamp) && currentPurchaseUID() == uid)
            applyInfo(Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, product).build()).customerInfo, uid, stamp)
        }
        refreshBalance(uid, stamp)
    }
    fun spendUnlockPass(sessionID: String, onConfirmed: (String) -> Unit) = action {
        check(unlockPassEnabled && AccountIdentity.sessionID(sessionID))
        val uid = currentPurchaseUID() ?: fail(R.string.account_unavailable); val stamp = generation
        val previous = pending(uid)
        check(previous == null || previous == sessionID) { message(R.string.account_pending_pass) }
        check(previous == sessionID || (state.value.unlockBalance ?: 0) > 0)
        if (previous == null) persistPending(uid, sessionID)
        mutable.update { it.copy(pendingUnlockSessionID = sessionID) }
        val response = FirebaseFunctions.getInstance("us-central1").getHttpsCallable("spendUnlockPass")
            .call(mapOf("sessionID" to sessionID)).await().data as? Map<*, *>
        check(same(uid, stamp) && response?.get("sessionID") == sessionID && response["spent"] == true)
        // Caller removes the durable pending receipt ONLY after successfully releasing this exact session.
        onConfirmed(sessionID)
        refreshBalance(uid, stamp)
    }
    fun completeUnlockPass(sessionID: String) {
        val uid = currentPurchaseUID() ?: return
        if (pending(uid) == sessionID) { pendingFile(uid).delete(); mutable.update { it.copy(pendingUnlockSessionID = null) } }
    }
    fun reconcilePreviousUnlockPass() = action {
        check(unlockPassEnabled); val uid = currentPurchaseUID() ?: fail(R.string.account_unavailable); val stamp = generation
        val sessionID = pending(uid) ?: return@action
        val response = FirebaseFunctions.getInstance("us-central1").getHttpsCallable("reconcileUnlockPass")
            .call(mapOf("sessionID" to sessionID)).await().data as? Map<*, *>
        check(same(uid, stamp) && response?.get("sessionID") == sessionID && pending(uid) == sessionID)
        when (response["status"]) {
            "cancelled" -> { completeUnlockPass(sessionID); notice(R.string.account_pass_cancelled) }
            "spent" -> notice(R.string.account_pass_spent)
            "pending" -> notice(R.string.account_pending_pass)
            else -> fail(R.string.account_identity_error)
        }
        refreshBalance(uid, stamp)
    }
    fun close() { auth?.removeAuthStateListener(listener); if (purchasesAllowed && Purchases.isConfigured) Purchases.sharedInstance.updatedCustomerInfoListener = null; scope.cancel() }
    companion object { fun https(value: String) = runCatching { Uri.parse(value).let { it.scheme == "https" && !it.host.isNullOrBlank() } }.getOrDefault(false) }
}
