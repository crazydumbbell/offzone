package com.exchip.offzone

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
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
    onBack: () -> Unit,
    onJournal: (() -> Unit)? = null,
    onOpenPaywall: (() -> Unit)? = null,
) {
    val state by store.state.collectAsState()
    val context = LocalContext.current
    val activity = context.accountActivity()
    val hasPro = store.hasProAccessEffective
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("signin") }
    var deleting by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    LaunchedEffect(state.uid) { deleting = false; password = ""; confirmation = ""; mode = "signin"; store.refresh() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice, state.error) {
        (state.error ?: state.notice)?.let { snackbar.showSnackbar(it) }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.account_done)) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(if (hasPro) R.string.account_pro_active else R.string.account_free_space), style = MaterialTheme.typography.bodyLarge, color = InkMuted)
                }
                NookCatView(NookExpression.WELCOME_FULL, Modifier.size(72.dp))
            }
            Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.account_plan), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (hasPro) R.string.account_pro_active else R.string.account_free_space), style = MaterialTheme.typography.bodyLarge)
                    if (!hasPro && onOpenPaywall != null) {
                        PrimaryButton(onClick = onOpenPaywall, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.account_upgrade_pro))
                        }
                    }
                    if (state.purchasesConfigured) {
                        TextButton(onClick = store::restorePurchases, enabled = !state.busy) { Text(stringResource(R.string.account_restore)) }
                    }
                    if (activity != null && (hasPro || state.uid != null)) {
                        TextButton(onClick = { store.manageSubscription(activity) }) { Text(stringResource(R.string.account_manage)) }
                    }
                }
            }
            if (!state.configured) {
                Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.account_unavailable), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.account_local), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                    }
                }
            } else if (state.uid == null) {
                Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.account_signin_intro), style = MaterialTheme.typography.bodyLarge)
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
                        AccountEmailFields(email, { email = it }, password, { password = it }, confirmation, { confirmation = it }, mode == "create")
                        if (mismatch) Text(stringResource(R.string.account_password_mismatch), color = MaterialTheme.colorScheme.error)
                        PrimaryButton(onClick = {
                            mismatch = mode == "create" && password != confirmation
                            if (!mismatch) {
                                if (mode == "create") store.createAccount(email, password) else store.signIn(email, password)
                                password = ""; confirmation = ""
                            }
                        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (mode == "create") R.string.account_create else R.string.account_signin))
                        }
                        TextButton(onClick = { mode = if (mode == "create") "signin" else "create"; password = ""; confirmation = ""; mismatch = false }, enabled = !state.busy) {
                            Text(stringResource(if (mode == "create") R.string.account_signin else R.string.account_create))
                        }
                        TextButton(onClick = { store.resetPassword(email) }, enabled = !state.busy) { Text(stringResource(R.string.account_reset)) }
                    }
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
                        Text(stringResource(R.string.account_link_intro), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
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
                        HorizontalDivider(color = PineHairline)
                        TextButton(onClick = store::signOut, enabled = !state.busy) { Text(stringResource(R.string.account_signout)) }
                        TextButton(onClick = { deleting = true; password = "" }, enabled = !state.busy) {
                            Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (state.busy) CircularProgressIndicator()
            Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.account_local), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                    if (onJournal != null) {
                        TextButton(onClick = onJournal, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.m_journal)) }
                    }
                    listOf(R.string.account_terms to BuildConfig.TERMS_URL, R.string.account_privacy to BuildConfig.PRIVACY_URL).forEach { (label, url) ->
                        if (AccountStore.https(url)) {
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text(stringResource(label)) }
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

private fun Context.accountActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.accountActivity(); else -> null }
