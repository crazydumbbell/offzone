package com.exchip.offzone

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

internal fun timeLabel(minutes: Int): String = LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private val EditorPaper = Color(0xFFFAF7E8)
private val EditorMuted = Color(0xFF666963)

@Composable
private fun EditorCard(title: String, detail: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = EditorPaper, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = EditorMuted)
        }
    }
}

@Composable
private fun EditorSummary(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = EditorMuted)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Ink)
    }
}

@Composable
fun RuleEditorScreen(
    rule: FocusRule?, defaultStart: Int, onBack: () -> Unit, onSaved: (FocusRule) -> Unit,
    defaultEnd: Int = (defaultStart + 60) % 1440,
) {
    val context = LocalContext.current
    val suggestedName = when (OnboardingProfile.goal(context)) {
        "rest" -> R.string.editor_name_rest
        "presence" -> R.string.editor_name_presence
        "personal" -> R.string.editor_name_personal
        else -> R.string.editor_name_work
    }
    var name by rememberSaveable { mutableStateOf(rule?.name ?: context.getString(suggestedName)) }
    var start by rememberSaveable { mutableIntStateOf(rule?.startMinutes ?: defaultStart) }
    var end by rememberSaveable { mutableIntStateOf(rule?.endMinutes ?: defaultEnd) }
    var packages by rememberSaveable(stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(rule?.packages ?: emptySet()) }
    var latitude by rememberSaveable { mutableStateOf(rule?.latitude) }
    var longitude by rememberSaveable { mutableStateOf(rule?.longitude) }
    var label by rememberSaveable { mutableStateOf(rule?.placeLabel ?: "") }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf(false) }
    var place by rememberSaveable { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val placeName = if (latitude != null && longitude != null)
        label.ifBlank { String.format(Locale.getDefault(), "%.5f, %.5f", latitude, longitude) }
    else ""
    val durationValid = RulePolicy.intervalMinutes(start, end) >= 15
    val saveValid = name.isNotBlank() && packages.isNotEmpty() && latitude != null && longitude != null && durationValid
    val nextEnabled = when (step) { 0 -> packages.isNotEmpty(); 1 -> latitude != null && longitude != null; 2 -> durationValid; else -> saveValid }
    val nextLabel = when (step) { 0 -> R.string.editor_next_place; 1 -> R.string.editor_next_time; 2 -> R.string.editor_next_review; else -> R.string.m_save_rule }

    BackHandler(place) { place = false }
    BackHandler(step > 0 && !place && !picker) { step-- }
    if (place) {
        PlacePickerScreen(latitude, longitude, label, onBack = { place = false }) { lat, lon, title ->
            latitude = lat; longitude = lon; label = title; place = false
        }
        return
    }

    Scaffold(containerColor = Butter, bottomBar = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).navigationBarsPadding()) {
            Button(
                onClick = {
                    if (step < 3) step++ else {
                        if (!saveValid) return@Button
                        val draft = FocusRule(
                            id = rule?.id ?: UUID.randomUUID().toString(), name = name.trim(), packages = packages,
                            startMinutes = start, endMinutes = end, latitude = latitude!!, longitude = longitude!!, placeLabel = label,
                        )
                        if (FocusController.ruleStore.save(draft)) onSaved(draft) else failed = true
                    }
                },
                enabled = nextEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White), shape = RoundedCornerShape(16.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(nextLabel), modifier = Modifier.weight(1f))
                    Text("→", modifier = Modifier.clearAndSetSemantics { })
                }
            }
        }
    }) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (step == 0) onBack() else step-- }) { Text(stringResource(R.string.m_back), color = Ink) }
                Text("OFFZONE", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.weight(1f))
                Text("${step + 3} / 6", style = MaterialTheme.typography.bodyMedium, color = EditorMuted)
            }
            LinearProgressIndicator(progress = { (step + 3) / 6f }, modifier = Modifier.fillMaxWidth(), color = Ink, trackColor = Ink.copy(alpha = 0.14f))
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.CenterEnd) {
                NookCatView(NookExpression.READY, Modifier.width(110.dp).height(100.dp))
            }
            Text(stringResource(when (step) {
                0 -> R.string.editor_apps_title; 1 -> R.string.editor_place_title
                2 -> R.string.editor_schedule_title; else -> R.string.editor_review_title
            }), style = MaterialTheme.typography.headlineLarge, color = Ink)
            Text(stringResource(when (step) {
                0 -> R.string.editor_apps_detail; 1 -> R.string.editor_place_detail
                2 -> R.string.editor_schedule_detail; else -> R.string.editor_review_detail
            }), style = MaterialTheme.typography.bodyLarge, color = EditorMuted)
            when (step) {
                0 -> EditorCard(
                    if (packages.isEmpty()) stringResource(R.string.editor_apps_choose) else stringResource(R.string.editor_apps_choose_again),
                    if (packages.isEmpty()) stringResource(R.string.editor_apps_empty) else stringResource(R.string.apps_selected, packages.size),
                ) { picker = true }
                1 -> {
                    EditorCard(stringResource(R.string.m_choose_place), placeName.ifBlank { stringResource(R.string.editor_place_empty) }) { place = true }
                    Text(stringResource(R.string.editor_place_hint), style = MaterialTheme.typography.bodyMedium, color = EditorMuted)
                }
                2 -> {
                    Surface(color = EditorPaper, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = {
                                TimePickerDialog(context, { _, hour, minute -> start = hour * 60 + minute }, start / 60, start % 60, DateFormat.is24HourFormat(context)).show()
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.m_start_time, timeLabel(start))) }
                            OutlinedButton(onClick = {
                                TimePickerDialog(context, { _, hour, minute -> end = hour * 60 + minute }, end / 60, end % 60, DateFormat.is24HourFormat(context)).show()
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.m_end_time, timeLabel(end))) }
                        }
                    }
                    if (!durationValid) Text(stringResource(R.string.editor_schedule_warning), color = MaterialTheme.colorScheme.error)
                    else if (end < start) Text(stringResource(R.string.editor_schedule_overnight), color = EditorMuted)
                    Text(stringResource(R.string.m_schedule_note), style = MaterialTheme.typography.bodyMedium, color = EditorMuted)
                }
                else -> {
                    OutlinedTextField(name, { name = it.take(120) }, label = { Text(stringResource(R.string.m_rule_name)) },
                        placeholder = { Text(stringResource(R.string.editor_name_placeholder)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Surface(color = EditorPaper, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                            EditorSummary(stringResource(R.string.editor_apps), stringResource(R.string.apps_selected, packages.size))
                            HorizontalDivider()
                            EditorSummary(stringResource(R.string.editor_place), placeName)
                            HorizontalDivider()
                            EditorSummary(stringResource(R.string.editor_time), "${timeLabel(start)}–${timeLabel(end)}")
                        }
                    }
                    Text(stringResource(R.string.editor_save_note), style = MaterialTheme.typography.bodyMedium, color = EditorMuted)
                    if (failed) Text(stringResource(R.string.save_error), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (picker) AppPicker(packages, onClose = { picker = false }) { packages = it; picker = false }
}
