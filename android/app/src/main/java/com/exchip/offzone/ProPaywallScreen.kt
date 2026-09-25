package com.exchip.offzone

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProPaywallScreen(store: AccountStore, onContinue: () -> Unit, onOpenAccount: () -> Unit) {
    val account by store.state.collectAsState()
    val context = LocalContext.current
    val heading = when (OnboardingProfile.goal(context)) {
        "rest" -> R.string.pro_headline_rest
        "presence" -> R.string.pro_headline_presence
        "personal" -> R.string.pro_headline_personal
        else -> R.string.pro_headline_work
    }
    LaunchedEffect(store.offersEnabled, account.verified) {
        if (store.offersEnabled && account.verified) store.loadOfferings()
    }
    Column(Modifier.fillMaxSize().background(Butter).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pro_eyebrow), style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onContinue) { Text(stringResource(R.string.pro_close)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            NookCatView(NookExpression.WELCOME_FULL, Modifier.fillMaxWidth().height(184.dp))
            Text(stringResource(heading), style = MaterialTheme.typography.headlineLarge)
            Column(Modifier.fillMaxWidth().background(Color(0xFFFDFDF9), RoundedCornerShape(22.dp))
                .padding(horizontal = 20.dp, vertical = 8.dp)) {
                listOf(R.string.pro_benefit_plan, R.string.pro_benefit_journal, R.string.pro_benefit_review).forEachIndexed { index, label ->
                    if (index > 0) HorizontalDivider(color = Ink.copy(alpha = 0.12f))
                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✓", color = Ink, modifier = Modifier.padding(end = 14.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Surface(color = Color(0xFFE8EEDD), shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(if (store.hasProAccess) R.string.account_pro_active else R.string.account_plans_unavailable),
                    modifier = Modifier.fillMaxWidth().padding(18.dp), style = MaterialTheme.typography.bodyMedium)
            }
            if (store.offersEnabled && !store.hasProAccess) {
                Button(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Butter)) {
                    Text(stringResource(R.string.pro_view_plans))
                }
            }
            account.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            account.notice?.let { Text(it) }
            TextButton(onClick = {
                if (account.verified && account.purchasesConfigured) store.restorePurchases() else onOpenAccount()
            }, enabled = !account.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.account_restore))
            }
            if (AccountStore.https(BuildConfig.TERMS_URL)) {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.TERMS_URL))) },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.account_terms)) }
            }
            if (AccountStore.https(BuildConfig.PRIVACY_URL)) {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL))) },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.account_privacy)) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
            .heightIn(min = 56.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Butter),
            shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.pro_continue_free))
        }
    }
}
