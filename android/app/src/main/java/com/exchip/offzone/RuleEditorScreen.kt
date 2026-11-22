package com.exchip.offzone

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/** Formats a minute-of-day value using the system 12/24-hour preference on [context]. */
internal fun timeLabel(context: Context, minutes: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minutes / 60)
        set(Calendar.MINUTE, minutes % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

internal fun dayLabel(day: DayOfWeek, style: TextStyle = TextStyle.FULL, locale: Locale = Locale.getDefault()): String =
    day.getDisplayName(style, locale)

@Composable
internal fun daysLabel(days: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (days) {
        RulePolicy.EVERY_DAY -> stringResource(R.string.days_every)
        RulePolicy.WEEKDAYS -> stringResource(R.string.days_weekdays)
        RulePolicy.EVERY_DAY - RulePolicy.WEEKDAYS -> stringResource(R.string.days_weekends)
        else -> DayOfWeek.entries.filter { RulePolicy.dayOn(days, it) }.joinToString(", ") { dayLabel(it, TextStyle.SHORT, locale) }
    }
}

@Composable
internal fun scheduleLabel(rule: FocusRule): String {
    val context = LocalContext.current
    return "${timeLabel(context, rule.startMinutes)} – ${timeLabel(context, rule.endMinutes)} · ${daysLabel(rule.days)}"
}

@Composable
private fun EditorCard(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = WarmIvory,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, PineHairline),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(
    rule: FocusRule?,
    defaultStart: Int,
    onBack: () -> Unit,
    onSaved: (FocusRule) -> Unit,
    defaultEnd: Int = (defaultStart + 60) % 1440,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val suggestedName = when (OnboardingProfile.goal(context)) {
        "rest" -> R.string.editor_name_rest
        "presence" -> R.string.editor_name_presence
        "personal" -> R.string.editor_name_personal
        else -> R.string.editor_name_work
    }
    var name by rememberSaveable { mutableStateOf(rule?.name ?: context.getString(suggestedName)) }
    var start by rememberSaveable { mutableIntStateOf(rule?.startMinutes ?: defaultStart) }
    var end by rememberSaveable { mutableIntStateOf(rule?.endMinutes ?: defaultEnd) }
    var days by rememberSaveable { mutableIntStateOf(rule?.days ?: RulePolicy.EVERY_DAY) }
    var packages by rememberSaveable(stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) {
        mutableStateOf(rule?.packages ?: emptySet())
    }
    var latitude by rememberSaveable { mutableStateOf(if (rule?.anywhere == false) rule.latitude else null) }
    var longitude by rememberSaveable { mutableStateOf(if (rule?.anywhere == false) rule.longitude else null) }
    var label by rememberSaveable { mutableStateOf(if (rule?.anywhere == false) rule.placeLabel else "") }
    var anywhere by rememberSaveable { mutableStateOf(rule?.anywhere ?: true) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf(false) }
    var place by rememberSaveable { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val hasPlace = !anywhere && latitude != null && longitude != null
    val placeName = if (!hasPlace) {
        stringResource(R.string.editor_place_empty)
    } else {
        label.ifBlank { String.format(locale, "%.5f, %.5f", latitude, longitude) }
    }
    val overnight = end <= start && end != start
    val durationValid = RulePolicy.intervalMinutes(start, end) >= 15 && days != 0
    val saveValid = name.isNotBlank() && packages.isNotEmpty() && durationValid
    val nextEnabled = if (step == 0) packages.isNotEmpty() else saveValid
    val nextLabel = if (step == 0) R.string.onboarding_continue else R.string.m_save_rule

    BackHandler(place) { place = false }
    BackHandler(step > 0 && !place && !picker) { step = 0 }
    if (place) {
        PlacePickerScreen(latitude, longitude, label, onBack = { place = false }) { lat, lon, title ->
            latitude = lat; longitude = lon; label = title; anywhere = false; place = false
        }
        return
    }

    Scaffold(
        containerColor = Butter,
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).navigationBarsPadding()) {
                PrimaryButton(
                    onClick = {
                        if (step == 0) step = 1 else {
                            if (!saveValid) return@PrimaryButton
                            val draft = FocusRule(
                                id = rule?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                packages = packages,
                                startMinutes = start,
                                endMinutes = end,
                                latitude = if (hasPlace) latitude!! else 0.0,
                                longitude = if (hasPlace) longitude!! else 0.0,
                                placeLabel = if (hasPlace) label else "",
                                days = days,
                                anywhere = !hasPlace,
                            )
                            if (FocusController.ruleStore.save(draft)) onSaved(draft) else failed = true
                        }
                    },
                    enabled = nextEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(nextLabel), modifier = Modifier.weight(1f))
                        Text("→", modifier = Modifier.clearAndSetSemantics { })
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (step == 0) onBack() else step = 0 }) {
                    Text(stringResource(if (step == 0) R.string.editor_cancel else R.string.m_back))
                }
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.pro_step_of, step + 1, 2), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            }
            StepProgress((step + 1) / 2f)
            Text(
                stringResource(if (step == 0) R.string.editor_apps_title else R.string.editor_schedule_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
            )
            Text(
                stringResource(if (step == 0) R.string.editor_apps_detail else R.string.editor_schedule_detail),
                style = MaterialTheme.typography.bodyLarge,
                color = InkMuted,
            )
            if (step == 0) {
                EditorCard(
                    if (packages.isEmpty()) stringResource(R.string.editor_apps_choose) else stringResource(R.string.editor_apps_choose_again),
                    if (packages.isEmpty()) stringResource(R.string.editor_apps_empty) else stringResource(R.string.apps_selected, packages.size),
                ) { picker = true }
            } else {
                Surface(
                    color = WarmIvory,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, PineHairline),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(120) },
                            label = { Text(stringResource(R.string.m_rule_name)) },
                            placeholder = { Text(stringResource(R.string.editor_name_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
                Surface(
                    color = WarmIvory,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, PineHairline),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(stringResource(R.string.editor_days), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DayOfWeek.entries.forEach { day ->
                                val bit = 1 shl (day.value - 1)
                                val full = dayLabel(day, TextStyle.FULL, locale)
                                OffzoneChip(
                                    selected = days and bit != 0,
                                    onClick = { days = days xor bit },
                                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = full },
                                ) { Text(full) }
                            }
                        }
                        if (days == 0) Text(stringResource(R.string.editor_days_warning), color = MaterialTheme.colorScheme.error)
                        HorizontalDivider(color = PineHairline)
                        Text(stringResource(R.string.m_schedule), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                        SecondaryButton(
                            onClick = {
                                TimePickerDialog(context, { _, hour, minute -> start = hour * 60 + minute }, start / 60, start % 60, DateFormat.is24HourFormat(context)).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.m_start_time, timeLabel(context, start))) }
                        SecondaryButton(
                            onClick = {
                                TimePickerDialog(context, { _, hour, minute -> end = hour * 60 + minute }, end / 60, end % 60, DateFormat.is24HourFormat(context)).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.m_end_time, timeLabel(context, end)))
                                if (overnight) {
                                    Surface(color = Mint, shape = CircleShape) {
                                        Text(
                                            stringResource(R.string.editor_next_day_badge),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Pine,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                        if (days != 0 && !durationValid) Text(stringResource(R.string.editor_schedule_warning), color = MaterialTheme.colorScheme.error)
                        else if (days != 0 && overnight) Text(stringResource(R.string.editor_schedule_overnight), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                        Text(stringResource(R.string.m_schedule_note), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                    }
                }
                Surface(
                    color = WarmIvory,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, PineHairline),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.editor_place_optional), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(placeName, style = MaterialTheme.typography.bodyLarge, color = Ink)
                        Text(
                            stringResource(if (hasPlace) R.string.editor_place_hint else R.string.editor_place_optional_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(onClick = { place = true }) { Text(stringResource(R.string.m_choose_place)) }
                            if (hasPlace) {
                                TextButton(
                                    onClick = { anywhere = true; latitude = null; longitude = null; label = "" },
                                    modifier = Modifier.heightIn(min = 56.dp),
                                ) { Text(stringResource(R.string.editor_clear_place)) }
                            }
                        }
                    }
                }
                if (failed) Text(stringResource(R.string.save_error), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (picker) AppPicker(packages, onClose = { picker = false }) { packages = it; picker = false }
}
