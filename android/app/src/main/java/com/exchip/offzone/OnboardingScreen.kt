package com.exchip.offzone

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

object OnboardingProfile {
    private val windows = setOf("morning", "afternoon", "evening", "custom", "unsure")
    private fun prefs(context: Context) = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    fun completed(context: Context) = prefs(context).getBoolean("completed", false)
    fun goal(context: Context) = prefs(context).getString("goal", "work")?.takeIf { it in JournalStore.contexts } ?: "work"
    fun window(context: Context) = prefs(context).getString("window", "morning")?.takeIf { it in windows } ?: "morning"
    fun customStartMinutes(context: Context) = prefs(context).getInt("custom_start_minutes", 12 * 60).coerceIn(0, 1439)
    fun startMinutes(context: Context): Int = when (window(context)) {
        "afternoon" -> 14 * 60
        "evening" -> 20 * 60
        "custom" -> customStartMinutes(context)
        "unsure" -> if (goal(context) == "work") 9 * 60 else 20 * 60
        else -> 9 * 60
    }
    fun endMinutes(context: Context) = (startMinutes(context) + 60) % (24 * 60)

    /** Marks first-run onboarding as completed on this device. */
    fun complete(context: Context): Boolean = prefs(context).edit().putBoolean("completed", true).commit()

    fun save(context: Context, goal: String, window: String, customStartMinutes: Int = 12 * 60): Boolean {
        require(goal in JournalStore.contexts && window in windows && customStartMinutes in 0..1439)
        return prefs(context).edit().putString("goal", goal).putString("window", window)
            .putInt("custom_start_minutes", customStartMinutes).putBoolean("completed", true).commit()
    }
}

internal fun goalLabel(goal: String) = when (goal) {
    "rest" -> R.string.goal_rest
    "presence" -> R.string.goal_presence
    "personal" -> R.string.goal_personal
    else -> R.string.goal_work
}

/** Single-step welcome screen leading into first-rule creation or existing subscriber sign-in. */
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit, onSignIn: () -> Unit) {
    Scaffold(
        containerColor = Butter,
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PrimaryButton(onClick = onGetStarted, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_start))
                }
                TextButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.onboarding_sign_in), color = Ink)
                }
            }
        },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NookCatView(NookExpression.WELCOME_FULL, Modifier.fillMaxWidth().height(210.dp))
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineLarge,
                color = Ink,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.onboarding_detail),
                style = MaterialTheme.typography.bodyLarge,
                color = InkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
