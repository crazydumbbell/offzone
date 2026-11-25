package com.exchip.offzone

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AccountScreen(
    store: AccountStore,
    hasProAccess: Boolean,
    hasActiveSession: Boolean,
    onRestoreSession: () -> Unit,
    onBack: () -> Unit,
    onJournal: (() -> Unit)? = null,
    initialManage: Boolean = false,
) {
    val state by store.state.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    var managingAccount by rememberSaveable(initialManage) { mutableStateOf(initialManage) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("signin") }
    var deleting by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    var signInExpanded by rememberSaveable { mutableStateOf(false) }
    var previousUid by remember { mutableStateOf(state.uid) }
    LaunchedEffect(state.uid) {
        if (previousUid == null && state.uid != null && "password" in state.providers && !state.emailVerified) {
            managingAccount = true
        }
        previousUid = state.uid
        deleting = false; signInExpanded = false; password = ""; confirmation = ""; mode = "signin"
        store.refresh()
    }
    BackHandler(managingAccount && !initialManage) { managingAccount = false }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice, state.error) {
        (state.error ?: state.notice)?.let { snackbar.showSnackbar(it) }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!managingAccount) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.account_done)) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    NookCatView(NookExpression.WELCOME_FULL, Modifier.size(72.dp))
                }
                if (!hasProAccess && ProAccessGate.canRestoreActiveSession(hasActiveSession)) {
                    PrimaryButton(onClick = onRestoreSession, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.restore))
                    }
                }
                if (hasProAccess) {
                    Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(stringResource(R.string.account_pro_active), style = MaterialTheme.typography.bodyLarge)
                            PrimaryButton(
                                onClick = { activity?.let(store::manageSubscription) },
                                enabled = activity != null && !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.account_manage)) }
                        }
                    }
                } else if (state.configured && state.uid == null) {
                    AccountSignInCard(
                        store = store,
                        state = state,
                        activity = activity,
                        email = email, onEmail = { email = it },
                        password = password, onPassword = { password = it },
                        confirmation = confirmation, onConfirmation = { confirmation = it },
                        mode = mode, onMode = { mode = it },
                        mismatch = mismatch, onMismatch = { mismatch = it },
                    )
                } else {
                    Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(stringResource(R.string.account_free_space), style = MaterialTheme.typography.bodyLarge)
                            when {
                                state.purchasesConfigured -> PrimaryButton(
                                    onClick = store::restorePurchases,
                                    enabled = !state.busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.account_restore)) }
                                state.uid != null && "password" in state.providers && !state.emailVerified -> PrimaryButton(
                                    onClick = store::reloadVerification,
                                    enabled = !state.busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.account_check_verification)) }
                                !state.configured -> Text(
                                    stringResource(R.string.account_unavailable),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkMuted,
                                )
                            }
                        }
                    }
                }
                if (state.busy) CircularProgressIndicator()
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = { managingAccount = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            stringResource(R.string.account_manage_account),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted,
                        )
                    }
                }
            } else {
                TextButton(onClick = { if (initialManage) onBack() else managingAccount = false }) {
                    Text(stringResource(R.string.m_back))
                }
                Text(stringResource(R.string.account_manage_account), style = MaterialTheme.typography.headlineSmall)
                if (!hasProAccess && ProAccessGate.canRestoreActiveSession(hasActiveSession)) {
                    PrimaryButton(onClick = onRestoreSession, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.restore))
                    }
                }
                if (!state.configured) {
                    Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.account_unavailable), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                        }
                    }
                } else if (state.uid == null) {
                    if (hasProAccess && !signInExpanded) {
                        SecondaryButton(
                            onClick = { signInExpanded = true },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.account_signin_options)) }
                    } else {
                        AccountSignInCard(
                            store = store,
                            state = state,
                            activity = activity,
                            email = email, onEmail = { email = it },
                            password = password, onPassword = { password = it },
                            confirmation = confirmation, onConfirmation = { confirmation = it },
                            mode = mode, onMode = { mode = it },
                            mismatch = mismatch, onMismatch = { mismatch = it },
                        )
                    }
                } else {
                    Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(state.name.ifBlank { state.email }, style = MaterialTheme.typography.titleMedium)
                            if ("password" in state.providers && !state.emailVerified) {
                                Text(stringResource(R.string.account_verify_required), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = store::sendVerification, enabled = !state.busy) { Text(stringResource(R.string.account_resend)) }
                                TextButton(onClick = store::reloadVerification, enabled = !state.busy) { Text(stringResource(R.string.account_check_verification)) }
                            }
                            HorizontalDivider(color = PineHairline)
                            Text(stringResource(R.string.account_link_methods), style = MaterialTheme.typography.titleMedium)
                            state.providers.filter { it != "firebase" }.forEach { provider ->
                                Text(stringResource(R.string.account_provider_linked, when (provider) {
                                    "google.com" -> "Google"
                                    "apple.com" -> "Apple"
                                    "password" -> stringResource(R.string.account_email)
                                    else -> provider
                                }))
                            }
                            if (state.verified) {
                                if (activity != null && store.googleConfigured && "google.com" !in state.providers) {
                                    TextButton(onClick = { store.google(activity, linking = true) }, enabled = !state.busy) { Text(stringResource(R.string.account_link_google)) }
                                }
                                if (activity != null && "apple.com" !in state.providers) {
                                    TextButton(onClick = { store.apple(activity, linking = true) }, enabled = !state.busy) { Text(stringResource(R.string.account_link_apple)) }
                                }
                                if ("password" !in state.providers) {
                                    TextButton(onClick = { mode = if (mode == "link") "signin" else "link" }, enabled = !state.busy) { Text(stringResource(R.string.account_link_email)) }
                                    if (mode == "link") {
                                        AccountEmailFields(email, { email = it }, password, { password = it }, confirmation, { confirmation = it }, true)
                                        if (mismatch) Text(stringResource(R.string.account_password_mismatch), color = MaterialTheme.colorScheme.error)
                                        PrimaryButton(onClick = {
                                            mismatch = password != confirmation
                                            if (!mismatch) { store.linkEmail(email, password); password = ""; confirmation = "" }
                                        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.account_link_email)) }
                                    }
                                } else {
                                    TextButton(onClick = { store.resetPassword(state.email) }, enabled = !state.busy) { Text(stringResource(R.string.account_reset)) }
                                }
                            }
                            if (state.purchasesConfigured) {
                                HorizontalDivider(color = PineHairline)
                                TextButton(onClick = store::restorePurchases, enabled = !state.busy) { Text(stringResource(R.string.account_restore)) }
                            }
                            HorizontalDivider(color = PineHairline)
                            TextButton(onClick = store::signOut, enabled = !state.busy) { Text(stringResource(R.string.account_signout)) }
                            TextButton(onClick = { deleting = true; password = "" }, enabled = !state.busy) {
                                Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (state.busy) CircularProgressIndicator()
                val linkActions = buildList<Pair<Int, () -> Unit>> {
                    if (onJournal != null) add(R.string.m_journal to onJournal)
                    if (AccountStore.https(BuildConfig.TERMS_URL)) add(R.string.account_terms to { context.openExternalUrl(BuildConfig.TERMS_URL) })
                    if (AccountStore.https(BuildConfig.PRIVACY_URL)) add(R.string.account_privacy to { context.openExternalUrl(BuildConfig.PRIVACY_URL) })
                }
                if (linkActions.isNotEmpty()) {
                    Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            linkActions.forEachIndexed { index, (label, action) ->
                                if (index > 0) HorizontalDivider(color = PineHairline, modifier = Modifier.padding(horizontal = 8.dp))
                                TextButton(
                                    onClick = action,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        stringResource(label),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Ink,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().imePadding().padding(16.dp))
    }
    if (deleting && state.uid != null) AlertDialog(
        onDismissRequest = { if (!state.busy) deleting = false },
        title = { Text(stringResource(R.string.account_delete)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.account_delete_warning))
                if ("apple.com" !in state.providers && "google.com" !in state.providers) OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(stringResource(R.string.account_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    "apple.com" in state.providers -> activity?.let { store.apple(it, deleting = true) }
                    "google.com" in state.providers -> activity?.let { store.google(it, deleting = true) }
                    else -> { store.deleteWithPassword(password); password = "" }
                }
            }, enabled = !state.busy && activity != null) { Text(stringResource(R.string.account_confirm_delete)) }
        },
        dismissButton = { TextButton(onClick = { deleting = false; password = "" }, enabled = !state.busy) { Text(stringResource(R.string.account_keep)) } },
    )
}

@Composable
private fun AccountSignInCard(
    store: AccountStore,
    state: AccountState,
    activity: Activity?,
    email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    confirmation: String, onConfirmation: (String) -> Unit,
    mode: String, onMode: (String) -> Unit,
    mismatch: Boolean, onMismatch: (Boolean) -> Unit,
) {
    Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (activity != null) {
                if (store.googleConfigured) {
                    SecondaryButton(onClick = { store.google(activity) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.account_google))
                    }
                }
                SecondaryButton(onClick = { store.apple(activity) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.account_apple))
                }
            }
            AccountEmailFields(email, onEmail, password, onPassword, confirmation, onConfirmation, mode == "create")
            if (mismatch) Text(stringResource(R.string.account_password_mismatch), color = MaterialTheme.colorScheme.error)
            PrimaryButton(onClick = {
                val hasMismatch = mode == "create" && password != confirmation
                onMismatch(hasMismatch)
                if (!hasMismatch) {
                    if (mode == "create") store.createAccount(email, password) else store.signIn(email, password)
                    onPassword(""); onConfirmation("")
                }
            }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (mode == "create") R.string.account_create else R.string.account_signin))
            }
            TextButton(onClick = { onMode(if (mode == "create") "signin" else "create"); onPassword(""); onConfirmation(""); onMismatch(false) }, enabled = !state.busy) {
                Text(stringResource(if (mode == "create") R.string.account_signin else R.string.account_create))
            }
            TextButton(onClick = { store.resetPassword(email) }, enabled = !state.busy) { Text(stringResource(R.string.account_reset)) }
        }
    }
}

@Composable
private fun AccountEmailFields(
    email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    confirmation: String, onConfirmation: (String) -> Unit,
    creating: Boolean,
) {
    OutlinedTextField(email, onEmail, label = { Text(stringResource(R.string.account_email)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(password, onPassword, label = { Text(stringResource(R.string.account_password)) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
    if (creating) OutlinedTextField(confirmation, onConfirmation, label = { Text(stringResource(R.string.account_confirm_password)) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
}
