package com.exchip.offzone

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.listSaver
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun timeLabel(minutes: Int): String = LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
fun RuleEditorScreen(rule: FocusRule?, defaultStart: Int, onBack: () -> Unit, onSaved: (FocusRule) -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(rule?.name ?: "") }
    var start by rememberSaveable { mutableIntStateOf(rule?.startMinutes ?: defaultStart) }
    var end by rememberSaveable { mutableIntStateOf(rule?.endMinutes ?: (defaultStart + 60) % 1440) }
    var packages by rememberSaveable(stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(rule?.packages ?: emptySet()) }
    var latitude by rememberSaveable { mutableStateOf(rule?.latitude) }
    var longitude by rememberSaveable { mutableStateOf(rule?.longitude) }
    var label by rememberSaveable { mutableStateOf(rule?.placeLabel ?: "") }
    var picker by remember { mutableStateOf(false) }
    var place by rememberSaveable { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    BackHandler(place) { place = false }
    if (place) {
        PlacePickerScreen(latitude, longitude, label, onBack = { place = false }) { lat, lon, title ->
            latitude = lat; longitude = lon; label = title; place = false
        }
        return
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.m_back)) }
        Text(stringResource(if (rule == null) R.string.m_new_rule else R.string.m_edit_rule), style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(name, { name = it.take(120) }, label = { Text(stringResource(R.string.m_rule_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.apps_selected, packages.size)) }
        OutlinedButton(onClick = { place = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (latitude == null) stringResource(R.string.m_choose_place) else label.ifBlank { "%.5f, %.5f".format(latitude, longitude) })
        }
        Text(stringResource(R.string.m_schedule), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> start = h * 60 + m }, start / 60, start % 60, DateFormat.is24HourFormat(context)).show() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.m_start_time, timeLabel(start))) }
            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> end = h * 60 + m }, end / 60, end % 60, DateFormat.is24HourFormat(context)).show() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.m_end_time, timeLabel(end))) }
        }
        Text(stringResource(R.string.m_schedule_note))
        Text(stringResource(R.string.m_rule_review))
        val valid = name.isNotBlank() && packages.isNotEmpty() && latitude != null && longitude != null && RulePolicy.intervalMinutes(start, end) >= 15
        if (failed) Text(stringResource(R.string.save_error))
        Button(onClick = {
            val draft = FocusRule(id = rule?.id ?: java.util.UUID.randomUUID().toString(), name = name.trim(), packages = packages,
                startMinutes = start, endMinutes = end, latitude = latitude!!, longitude = longitude!!, placeLabel = label)
            if (FocusController.ruleStore.save(draft)) onSaved(draft) else failed = true
        }, enabled = valid, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.m_save_rule)) }
    }
    if (picker) AppPicker(packages, onClose = { picker = false }) { packages = it; picker = false }
}
