package com.exchip.offzone

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

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
    fun save(context: Context, goal: String, window: String, customStartMinutes: Int = 12 * 60): Boolean {
        require(goal in JournalStore.contexts && window in windows && customStartMinutes in 0..1439)
        return prefs(context).edit().putString("goal", goal).putString("window", window)
            .putInt("custom_start_minutes", customStartMinutes).putBoolean("completed", true).commit()
    }
}

internal fun goalLabel(goal: String) = when (goal) { "work" -> R.string.goal_work; "rest" -> R.string.goal_rest; "presence" -> R.string.goal_presence; else -> R.string.goal_personal }


private fun timeRange(context: Context, start: Int): String {
    fun label(minutes: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
        }
        return DateFormat.getTimeFormat(context).format(calendar.time)
    }
    return label(start) + "–" + label((start + 60) % (24 * 60))
}

@Composable
private fun OnboardingChoice(title: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Mint else WarmIvory,
        border = if (selected) BorderStroke(1.5.dp, Pine) else BorderStroke(1.dp, PineLine)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            }
            // Purely visual: the row itself carries the radio-button semantics.
            Box(Modifier.size(24.dp).clip(CircleShape).background(if (selected) Pine else Color.Transparent)
                .border(1.5.dp, if (selected) Pine else InkMuted, CircleShape).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                if (selected) Text("✓", color = Butter, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun OnboardingScreen(onComplete: (goal: String, window: String) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var goal by rememberSaveable { mutableStateOf(OnboardingProfile.goal(context)) }
    var window by rememberSaveable { mutableStateOf(OnboardingProfile.window(context)) }
    var customStart by rememberSaveable { mutableIntStateOf(OnboardingProfile.customStartMinutes(context)) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val suggestedStart = if (goal == "work") 9 * 60 else 20 * 60
    val pickTime = {
        TimePickerDialog(context, { _, hour, minute ->
            customStart = hour * 60 + minute
            window = "custom"
        }, customStart / 60, customStart % 60, DateFormat.is24HourFormat(context)).show()
    }

    Scaffold(containerColor = Butter, bottomBar = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (failed) Text(stringResource(R.string.journal_error), color = MaterialTheme.colorScheme.error)
            PrimaryButton(
                onClick = {
                    if (step < 2) step++ else {
                        busy = true
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) { OnboardingProfile.save(context, goal, window, customStart) }
                            busy = false
                            if (saved) onComplete(goal, window) else failed = true
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(when (step) { 0 -> R.string.onboarding_start; 1 -> R.string.onboarding_continue; else -> R.string.onboarding_done }))
            }
        }
    }) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (step > 0) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { step--; failed = false }) { Text(stringResource(R.string.journal_back)) }
                    Text("OFFZONE", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.weight(1f))
                    Text(step.toString() + " / 6", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                }
                StepProgress(step / 6f)
            }
            if (step == 0) {
                NookCatView(NookExpression.WELCOME_FULL, Modifier.fillMaxWidth().height(190.dp))
            } else {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.CenterEnd) {
                    NookCatView(if (step == 1) NookExpression.READY else NookExpression.FOCUSED, Modifier.size(100.dp))
                }
            }
            Text(
                stringResource(when (step) { 0 -> R.string.onboarding_title; 1 -> R.string.onboarding_goal; else -> R.string.onboarding_rhythm }),
                style = MaterialTheme.typography.headlineMedium, color = Ink
            )
            if (step == 0) {
                Text(stringResource(R.string.onboarding_detail), style = MaterialTheme.typography.bodyLarge, color = InkMuted)
                Text(stringResource(R.string.onboarding_safe), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            }
            if (step == 1) {
                Text(stringResource(R.string.onboarding_goal_detail), style = MaterialTheme.typography.bodyLarge, color = InkMuted)
                JournalStore.contexts.forEach { item ->
                    val detail = when (item) { "work" -> R.string.goal_work_detail; "rest" -> R.string.goal_rest_detail; "presence" -> R.string.goal_presence_detail; else -> R.string.goal_personal_detail }
                    OnboardingChoice(stringResource(goalLabel(item)), stringResource(detail), goal == item) {
                        goal = item
                        window = if (item == "work") "morning" else "evening"
                    }
                }
            }
            if (step == 2) {
                Text(stringResource(R.string.onboarding_rhythm_detail), style = MaterialTheme.typography.bodyLarge, color = InkMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("custom" to R.string.onboarding_another_time, "unsure" to R.string.onboarding_not_sure).forEach { (key, label) ->
                        Surface(
                            Modifier.weight(1f).clickable { if (key == "custom") pickTime() else window = key },
                            shape = RoundedCornerShape(16.dp),
                            color = if (window == key) Mint else WarmIvory,
                            border = if (window == key) BorderStroke(1.5.dp, Pine) else BorderStroke(1.dp, PineLine)
                        ) {
                            Text(stringResource(label), Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = Ink)
                        }
                    }
                }
                if (window == "custom") {
                    Surface(color = Mint, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(timeRange(context, customStart), Modifier.weight(1f), color = Ink)
                            TextButton(onClick = pickTime) { Text(stringResource(R.string.onboarding_change_time)) }
                        }
                    }
                } else if (window == "unsure") {
                    Surface(color = Mint, shape = RoundedCornerShape(16.dp)) {
                        Text(stringResource(R.string.onboarding_suggested_time, timeRange(context, suggestedStart)), Modifier.padding(16.dp), color = Ink)
                    }
                }
                listOf(
                    Triple("morning", R.string.onboarding_morning_title, 9 * 60),
                    Triple("afternoon", R.string.onboarding_afternoon_title, 14 * 60),
                    Triple("evening", R.string.onboarding_evening_title, 20 * 60)
                ).forEach { (key, title, start) ->
                    OnboardingChoice(stringResource(title), timeRange(context, start), window == key) { window = key }
                }
            }
        }
    }
}
