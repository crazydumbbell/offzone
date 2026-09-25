package com.exchip.offzone

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.revenuecat.purchases.models.Period
import com.revenuecat.purchases.models.RecurrenceMode

@Composable
fun AccountScreen(store: AccountStore, onBack: () -> Unit, initialGoal: String? = null, onJournal: (() -> Unit)? = null) {
    val state by store.state.collectAsState()
    val context = LocalContext.current
    val activity = context.accountActivity()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("signin") }
    var deleting by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    var goal by remember(initialGoal) { mutableStateOf(initialGoal?.takeIf(AccountIdentity.goals::contains) ?: "work") }
    LaunchedEffect(state.uid) { deleting = false; password = ""; confirmation = ""; mode = "signin"; store.refresh() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.account_done)) }
        Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleMedium)
        Surface(color = SoftButter, shape = RoundedCornerShape(22.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 132.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                NookCatView(NookExpression.WELCOME_FULL, Modifier.size(width = 146.dp, height = 126.dp))
            }
        }
        Text(stringResource(if (state.pro) R.string.account_pro_active else R.string.account_free_space), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.account_local))
        if (onJournal != null) TextButton(onClick = onJournal, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.m_journal)) }
        if (!state.configured) Text(stringResource(R.string.account_unavailable))
        else if (state.uid == null) {
            Text(stringResource(R.string.account_signin_intro))
            if (activity != null) {
                Button(onClick = { store.apple(activity) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.account_apple)) }
                if (store.googleConfigured) OutlinedButton(onClick = { store.google(activity) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.account_google)) }
            }
            AccountEmailFields(email, { email = it }, password, { password = it }, confirmation, { confirmation = it }, mode == "create")
            if (mismatch) Text(stringResource(R.string.account_password_mismatch), color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                mismatch = mode == "create" && password != confirmation
                if (!mismatch) {
                    if (mode == "create") store.createAccount(email, password) else store.signIn(email, password)
                    password = ""; confirmation = ""
                }
            }, enabled = !state.busy) { Text(stringResource(if (mode == "create") R.string.account_create else R.string.account_signin)) }
            TextButton(onClick = { mode = if (mode == "create") "signin" else "create"; password = ""; confirmation = ""; mismatch = false }, enabled = !state.busy) {
                Text(stringResource(if (mode == "create") R.string.account_signin else R.string.account_create))
            }
            TextButton(onClick = { store.resetPassword(email) }, enabled = !state.busy) { Text(stringResource(R.string.account_reset)) }
        } else {
            Text(state.name.ifBlank { state.email }, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.account_goal_intro))
            AccountIdentity.goals.forEach { value ->
                Row { RadioButton(selected = goal == value, onClick = { goal = value }); TextButton(onClick = { goal = value }) { Text(stringResource(accountGoalLabel(value))) } }
            }
            state.savedGoal?.let { Text(stringResource(R.string.account_saved_goal, stringResource(accountGoalLabel(it)))) }
            Button(onClick = { store.saveGoal(goal) }, enabled = !state.busy && state.verified && state.savedGoal != goal) { Text(stringResource(R.string.account_save_goal)) }
            if ("password" in state.providers && !state.emailVerified) {
                Text(stringResource(R.string.account_verify_required))
                TextButton(onClick = store::sendVerification, enabled = !state.busy) { Text(stringResource(R.string.account_resend)) }
                TextButton(onClick = store::reloadVerification, enabled = !state.busy) { Text(stringResource(R.string.account_check_verification)) }
            }
            HorizontalDivider()
            Text(stringResource(R.string.account_link_methods), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.account_link_intro))
            state.providers.filter { it != "firebase" }.forEach { provider ->
                Text(stringResource(R.string.account_provider_linked, when (provider) { "google.com" -> "Google"; "apple.com" -> "Apple"; "password" -> stringResource(R.string.account_email); else -> provider }))
            }
            if (state.verified) {
                if (activity != null && "apple.com" !in state.providers) TextButton(onClick = { store.apple(activity, linking = true) }, enabled = !state.busy) { Text(stringResource(R.string.account_link_apple)) }
                if (activity != null && store.googleConfigured && "google.com" !in state.providers) TextButton(onClick = { store.google(activity, linking = true) }, enabled = !state.busy) { Text(stringResource(R.string.account_link_google)) }
                if ("password" !in state.providers) {
                    TextButton(onClick = { mode = if (mode == "link") "signin" else "link" }, enabled = !state.busy) { Text(stringResource(R.string.account_link_email)) }
                    if (mode == "link") {
                        AccountEmailFields(email, { email = it }, password, { password = it }, confirmation, { confirmation = it }, true)
                        if (mismatch) Text(stringResource(R.string.account_password_mismatch), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { mismatch = password != confirmation; if (!mismatch) { store.linkEmail(email, password); password = ""; confirmation = "" } }, enabled = !state.busy) { Text(stringResource(R.string.account_link_email)) }
                    }
                } else TextButton(onClick = { store.resetPassword(state.email) }, enabled = !state.busy) { Text(stringResource(R.string.account_reset)) }
            }
            HorizontalDivider()
            Text(stringResource(R.string.account_plan), style = MaterialTheme.typography.titleMedium)
            if (store.offersEnabled && state.packages.isNotEmpty() && !state.pro) {
                Text(stringResource(R.string.account_pro_benefits))
                Text(stringResource(R.string.account_journal_local))
                state.packages.forEach { plan ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(plan.product.name, style = MaterialTheme.typography.titleMedium)
                            val phases = plan.product.defaultOption?.pricingPhases.orEmpty()
                            phases.forEach { phase ->
                                val period = periodText(phase.billingPeriod)
                                Text(if (phase.recurrenceMode == RecurrenceMode.INFINITE_RECURRING)
                                    stringResource(R.string.account_price_renewal, phase.price.formatted, period)
                                else stringResource(R.string.account_price_phase, phase.price.formatted, period, phase.billingCycleCount ?: 1))
                            }
                            if (phases.isEmpty()) Text(plan.product.price.formatted)
                            Text(stringResource(R.string.account_play_confirms))
                            Button(onClick = { activity?.let { store.purchase(it, plan) } }, enabled = !state.busy && activity != null) { Text(stringResource(R.string.account_subscribe)) }
                        }
                    }
                }
                Text(stringResource(R.string.account_renews))
            } else if (!state.pro) Text(stringResource(R.string.account_plans_unavailable))
            TextButton(onClick = store::loadOfferings, enabled = !state.busy && state.verified) { Text(stringResource(R.string.account_refresh_plans)) }
            if (state.purchasesConfigured) TextButton(onClick = store::restorePurchases, enabled = !state.busy) { Text(stringResource(R.string.account_restore)) }
            if (activity != null) TextButton(onClick = { store.manageSubscription(activity) }) { Text(stringResource(R.string.account_manage)) }
            if (store.unlockPassEnabled) {
                TextButton(onClick = store::loadUnlockPass, enabled = !state.busy) { Text(stringResource(R.string.account_refresh_pass)) }
                state.unlockBalance?.let { Text(stringResource(R.string.account_pass_balance, it)) }
                state.unlockProduct?.let { product ->
                    Button(onClick = { activity?.let(store::purchaseUnlockPass) }, enabled = !state.busy && activity != null) { Text(stringResource(R.string.account_buy_pass, product.price.formatted)) }
                }
                if (state.pendingUnlockSessionID != null) TextButton(onClick = store::reconcilePreviousUnlockPass, enabled = !state.busy) { Text(stringResource(R.string.account_check_pass)) }
            }
            HorizontalDivider()
            TextButton(onClick = store::signOut, enabled = !state.busy) { Text(stringResource(R.string.account_signout)) }
            TextButton(onClick = { deleting = true; password = "" }, enabled = !state.busy) { Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error) }
        }
        if (state.busy) CircularProgressIndicator()
        state.notice?.let { Text(it) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        listOf(R.string.account_terms to BuildConfig.TERMS_URL, R.string.account_privacy to BuildConfig.PRIVACY_URL).forEach { (label, url) ->
            if (AccountStore.https(url)) TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text(stringResource(label)) }
        }
    }
    if (deleting && state.uid != null) AlertDialog(onDismissRequest = { if (!state.busy) deleting = false },
        title = { Text(stringResource(R.string.account_delete)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.account_delete_warning))
            if ("apple.com" !in state.providers && "google.com" !in state.providers) OutlinedTextField(value = password, onValueChange = { password = it },
                label = { Text(stringResource(R.string.account_password)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            when {
                "apple.com" in state.providers -> activity?.let { store.apple(it, deleting = true) }
                "google.com" in state.providers -> activity?.let { store.google(it, deleting = true) }
                else -> { store.deleteWithPassword(password); password = "" }
            }
        }, enabled = !state.busy && activity != null) { Text(stringResource(R.string.account_confirm_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false; password = "" }, enabled = !state.busy) { Text(stringResource(R.string.account_keep)) } })
}

@Composable private fun AccountEmailFields(email: String, onEmail: (String) -> Unit, password: String, onPassword: (String) -> Unit,
    confirmation: String, onConfirmation: (String) -> Unit, creating: Boolean) {
    OutlinedTextField(email, onEmail, label = { Text(stringResource(R.string.account_email)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(password, onPassword, label = { Text(stringResource(R.string.account_password)) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
    if (creating) OutlinedTextField(confirmation, onConfirmation, label = { Text(stringResource(R.string.account_confirm_password)) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
}
private fun Context.accountActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.accountActivity(); else -> null }
private fun accountGoalLabel(goal: String) = when (goal) { "rest" -> R.string.account_goal_rest; "presence" -> R.string.account_goal_presence; "personal" -> R.string.account_goal_personal; else -> R.string.account_goal_work }
@Composable private fun periodText(period: Period) = stringResource(when (period.unit) {
    Period.Unit.DAY -> R.string.account_days; Period.Unit.WEEK -> R.string.account_weeks; Period.Unit.MONTH -> R.string.account_months;
    Period.Unit.YEAR -> R.string.account_years; else -> R.string.account_periods
}, period.value)
