package com.exchip.offzone

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import com.revenuecat.purchases.*
import com.revenuecat.purchases.Package
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.IOException

// Account services never own or disable local restrictions or free active-session recovery.
data class AccountState(
    val configured: Boolean = false,
    val uid: String? = null,
    val email: String = "",
    val name: String = "",
    val emailVerified: Boolean = false,
    val providers: Set<String> = emptySet(),
    val verified: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val savedGoal: String? = null,
    val pro: Boolean = false,
    val proExpiresAt: Long? = null,
    val managementURL: String? = null,
    val purchasesConfigured: Boolean = false,
    val packages: List<Package> = emptyList(),
)

internal object AccountIdentity {
    fun verified(providers: Set<String>, emailVerified: Boolean) =
        "apple.com" in providers || "google.com" in providers || ("password" in providers && emailVerified)

    fun accepts(currentUID: String?, requestedUID: String?, currentGeneration: Long, requestedGeneration: Long) =
        currentUID != null && currentUID == requestedUID && currentGeneration == requestedGeneration

    fun proAccess(uid: String?, entitlementUID: String?, active: Boolean, expiresAt: Long?, now: Long) =
        uid != null && uid == entitlementUID && active && (expiresAt == null || expiresAt > now)

    fun entitlementActive(
        verificationFailed: Boolean,
        hasEntitlement: Boolean,
        isDebug: Boolean,
        isSandbox: Boolean,
        isPlayStore: Boolean,
    ) = !verificationFailed && hasEntitlement && (isDebug || !isSandbox || isPlayStore)

    fun sessionID(value: String) = value.matches(Regex("[a-f0-9]{64}"))

    val goals = setOf("work", "rest", "presence", "personal")
}

private class LocalizedAccountException(@param:StringRes val resId: Int) : IllegalStateException()

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
    val hasProAccess get() = AccountIdentity.proAccess(currentPurchaseUID(), entitlementUID,
        state.value.pro, state.value.proExpiresAt, System.currentTimeMillis())

    /** Effective Pro entitlement after applying any debug-only override in debug builds. */
    val hasProAccessEffective: Boolean
        get() = ProAccessGate.effectiveProAccess(hasProAccess)

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

    private fun message(@StringRes id: Int) = context.getString(id)
    private fun fail(@StringRes id: Int): Nothing = throw LocalizedAccountException(id)
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
                        if (same(uid, stamp)) mutable.update { it.copy(savedGoal = profile.getString("goal")?.takeIf(AccountIdentity.goals::contains)) }
                    } else if (purchasesAllowed && Purchases.isConfigured && !Purchases.sharedInstance.isAnonymous) {
                        Purchases.sharedInstance.updatedCustomerInfoListener = null
                        Purchases.sharedInstance.awaitLogOut()
                    }
                } catch (e: Exception) { if (generation == stamp) report(e) }
            }
        }
    }

    @StringRes
    private fun errorMessageRes(e: Exception): Int = when (e) {
        is LocalizedAccountException -> e.resId
        is PurchasesException -> when (e.code) {
            PurchasesErrorCode.NetworkError -> R.string.account_error_network
            PurchasesErrorCode.StoreProblemError,
            PurchasesErrorCode.UnexpectedBackendResponseError,
            PurchasesErrorCode.UnknownBackendError -> R.string.account_error_store
            PurchasesErrorCode.PurchaseNotAllowedError,
            PurchasesErrorCode.IneligibleError,
            PurchasesErrorCode.InsufficientPermissionsError -> R.string.account_error_purchase_not_allowed
            PurchasesErrorCode.ProductAlreadyPurchasedError,
            PurchasesErrorCode.ReceiptAlreadyInUseError -> R.string.account_error_already_purchased
            else -> R.string.account_unavailable
        }
        is FirebaseNetworkException -> R.string.account_error_network
        is FirebaseTooManyRequestsException -> R.string.account_error_too_many_requests
        is FirebaseAuthUserCollisionException -> R.string.account_collision
        is FirebaseAuthInvalidCredentialsException -> R.string.account_error_invalid_credentials
        is FirebaseAuthInvalidUserException -> R.string.account_error_user_not_found
        is FirebaseAuthRecentLoginRequiredException -> R.string.account_error_recent_login
        is FirebaseAuthWeakPasswordException -> R.string.account_invalid_password
        is FirebaseAuthEmailException -> R.string.account_invalid_email
        is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> R.string.account_invalid_email
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> R.string.account_error_invalid_credentials
            "ERROR_USER_NOT_FOUND", "ERROR_USER_DISABLED" -> R.string.account_error_user_not_found
            "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL",
            "ERROR_CREDENTIAL_ALREADY_IN_USE" -> R.string.account_collision
            "ERROR_REQUIRES_RECENT_LOGIN" -> R.string.account_error_recent_login
            "ERROR_TOO_MANY_REQUESTS" -> R.string.account_error_too_many_requests
            "ERROR_NETWORK_REQUEST_FAILED" -> R.string.account_error_network
            else -> R.string.account_identity_error
        }
        is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> R.string.account_error_network
            else -> R.string.account_unavailable
        }
        is IOException -> R.string.account_error_network
        else -> R.string.account_unavailable
    }

    private fun report(e: Exception) {
        if (e is CancellationException || e is GetCredentialCancellationException ||
            (e is FirebaseAuthException && e.errorCode == "ERROR_WEB_CONTEXT_CANCELED")) return
        if (e is PurchasesTransactionException && e.userCancelled) { notice(R.string.account_purchase_cancelled); return }
        if (e is PurchasesException && e.code == PurchasesErrorCode.PurchaseCancelledError) { notice(R.string.account_purchase_cancelled); return }
        if (e is PurchasesException && e.code == PurchasesErrorCode.PaymentPendingError) { notice(R.string.account_purchase_pending); return }
        mutable.update { it.copy(error = message(errorMessageRes(e))) }
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
        check(auth!!.currentUser?.uid == uid)
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
        check(auth!!.currentUser?.uid == user.uid)
        db!!.collection("users").document(user.uid).delete().await()
        check(auth.currentUser?.uid == user.uid)
        user.delete().await() // Existing Auth onDelete trigger removes the RevenueCat customer.
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

    private fun notice(@StringRes id: Int) { mutable.update { it.copy(notice = message(id)) } }

    fun refresh() = action {
        identityJob?.join()
        val uid = currentPurchaseUID() ?: return@action; val stamp = generation
        purchaseLock.withLock { applyInfo(Purchases.sharedInstance.awaitCustomerInfo(), uid, stamp) }
        loadOffersInternal()
    }

    /** Prices are public, so this skips the busy flag: the first-run funnel must not wait on, or be dropped by, another action (D-50). */
    fun loadOfferings(): Job = scope.launch {
        identityJob?.join()
        try { loadOffersInternal() } catch (e: Exception) { report(e) }
    }

    /** Purchases is configured without an identity until someone signs in; `logIn` later moves it onto the account (D-50). */
    private fun ensurePurchases(): Boolean {
        if (!purchasesAllowed || !BuildConfig.REVENUECAT_PUBLIC_KEY.startsWith("goog_")) return false
        if (!Purchases.isConfigured) Purchases.configure(PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_PUBLIC_KEY)
            .entitlementVerificationMode(EntitlementVerificationMode.INFORMATIONAL).build())
        return true
    }

    /** Signed out: prices for display only (no purchase identity). Signed in: the same list, tied to the verified account. */
    private suspend fun loadOffersInternal() {
        if (!offersEnabled || !ensurePurchases()) return
        val uid = currentPurchaseUID(); val stamp = generation
        val offerings = Purchases.sharedInstance.awaitOfferings()
        val plans = offerings[BuildConfig.PRO_OFFERING_ID]?.availablePackages.orEmpty().filter {
            it.packageType in setOf(PackageType.ANNUAL, PackageType.MONTHLY) && it.product.type == ProductType.SUBS
        }
        val current = if (uid != null) same(uid, stamp) else generation == stamp && currentPurchaseUID() == null
        if (current) mutable.update { it.copy(packages = plans) }
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
        identityJob?.join(); val uid = currentPurchaseUID() ?: fail(R.string.account_verify_required); val stamp = generation
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
        val active = AccountIdentity.entitlementActive(
            verificationFailed = info.entitlements.verification == VerificationResult.FAILED,
            hasEntitlement = entitlement != null,
            isDebug = BuildConfig.DEBUG,
            isSandbox = entitlement?.isSandbox == true,
            isPlayStore = entitlement?.store == Store.PLAY_STORE,
        )
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
        activity.openExternalUrl(state.value.managementURL ?: "https://play.google.com/store/account/subscriptions")
    }

    fun close() {
        auth?.removeAuthStateListener(listener)
        if (purchasesAllowed && Purchases.isConfigured) Purchases.sharedInstance.updatedCustomerInfoListener = null
        scope.cancel()
    }

    companion object {
        fun https(value: String) = runCatching { value.toUri().let { it.scheme == "https" && !it.host.isNullOrBlank() } }.getOrDefault(false)
    }
}
