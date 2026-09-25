package com.exchip.offzone

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable fun JournalScreen(hasPro: Boolean, onOffer: () -> Unit, onBack: () -> Unit, accessCheck: () -> Boolean = { hasPro }) {
    val context = LocalContext.current
    val access = rememberUpdatedState(accessCheck)
    var store by remember { mutableStateOf<JournalStore?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var planRevision by remember { mutableIntStateOf(0) }
    var reflectionRevision by remember { mutableIntStateOf(0) }
    var week by remember { mutableStateOf(JournalStore.week(LocalDate.now())) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var intention by remember { mutableStateOf("") }; var goal by remember { mutableStateOf(OnboardingProfile.goal(context)) }
    var days by remember { mutableStateOf(setOf(2,3,4,5,6)) }
    var note by remember { mutableStateOf("") }; var outcome by remember { mutableStateOf(JournalStore.outcomes.first()) }
    var message by remember { mutableStateOf<Int?>(null) }; var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }; var exported by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store = withContext(Dispatchers.IO) { JournalStore(context) { access.value() } } }
    LaunchedEffect(week,store,planRevision) {
        val plan=store?.data?.plans?.get(week.toString())
        intention=plan?.intention ?: ""; goal=plan?.context ?: OnboardingProfile.goal(context); days=plan?.weekdays ?: setOf(2,3,4,5,6)
    }
    LaunchedEffect(date,store,reflectionRevision) { val entry=store?.data?.reflections?.get(date.toString()); note=entry?.note ?: ""; outcome=entry?.outcome ?: JournalStore.outcomes.first() }
    val data = remember(store, revision) { store?.data ?: JournalData() }
    fun action(reloadPlan: Boolean = false, reloadReflection: Boolean = false, block: () -> Unit) {
        if(busy) return
        busy=true
        scope.launch {
            try { withContext(Dispatchers.IO) { block() }; revision++; if(reloadPlan) planRevision++; if(reloadReflection) reflectionRevision++; exported=null; message=R.string.journal_saved }
            catch(_: Exception) { message=R.string.journal_error }
            finally { busy=false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri != null) {
            busy=true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { val bytes=checkNotNull(store).export(); checkNotNull(context.contentResolver.openOutputStream(uri,"wt")).use { it.write(bytes) } }
                    exported=uri; message=R.string.journal_exported
                } catch(_: Exception) { message=R.string.journal_error }
                finally { busy=false }
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) action(reloadPlan=true,reloadReflection=true) {
            val bytes=checkNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
                val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                while(true) { val n=stream.read(buffer); if(n<0) break; require(output.size()+n<=JournalStore.MAX_BYTES); output.write(buffer,0,n) }
                output.toByteArray()
            }
            checkNotNull(store).import(bytes)
        }
    }
    val muted = Color(0xFF666963)
    val paper = Color(0xFFFDFDF9)
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    Surface(Modifier.fillMaxSize(), color = Butter, contentColor = Ink) {
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_back)) }
                Text(stringResource(R.string.journal_title), style = MaterialTheme.typography.headlineSmall)
            }
            item {
                Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.journal_intro), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            NookCatView(NookExpression.REFLECTION, Modifier.size(96.dp))
                        }
                        Text(stringResource(R.string.journal_local), style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }
            if (!hasPro) item {
                Surface(color = Mint, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Pro", style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.journal_pro), style = MaterialTheme.typography.bodyMedium)
                        if (!BuildConfig.PRO_OFFER_READY) Text(stringResource(R.string.account_plans_unavailable), style = MaterialTheme.typography.bodySmall, color = muted)
                        OutlinedButton(onClick = onOffer, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_offer)) }
                    }
                }
            }
            if (store == null) item { CircularProgressIndicator() }
            else if (store!!.loadFailed) item {
                Surface(color = SoftButter, shape = RoundedCornerShape(18.dp)) {
                    Text(stringResource(R.string.journal_corrupt), modifier = Modifier.fillMaxWidth().padding(18.dp), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                item {
                    JournalPanel(stringResource(R.string.journal_weekly_overview)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val previous = stringResource(R.string.journal_previous)
                            val next = stringResource(R.string.journal_next)
                            IconButton(onClick = { week = week.minusWeeks(1) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = previous }) { Text("‹", fontSize = 30.sp) }
                            Text(week.format(dateFormat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            IconButton(onClick = { week = week.plusWeeks(1) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = next }) { Text("›", fontSize = 30.sp) }
                        }
                        val summary = store!!.summary(week)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            JournalMetric(summary.first, stringResource(R.string.journal_planned_days), Modifier.weight(1f))
                            JournalMetric(summary.second, stringResource(R.string.journal_reflected_days), Modifier.weight(1f))
                            JournalMetric(summary.third, stringResource(R.string.journal_kept_days), Modifier.weight(1f))
                        }
                        HorizontalDivider(color = Ink.copy(alpha = 0.12f))
                        Text(stringResource(R.string.journal_summary_note), style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
                item {
                    JournalPanel(stringResource(R.string.journal_weekly_intention)) {
                        OutlinedTextField(intention, onValueChange = { if (JournalStore.characterCount(it) <= 160) intention = it }, enabled = hasPro && !busy,
                            label = { Text(stringResource(R.string.journal_intention)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            JournalStore.contexts.forEach { key -> FilterChip(goal == key, onClick = { goal = key }, enabled = hasPro && !busy, label = { Text(stringResource(goalLabel(key))) }) }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 3, 4, 5, 6, 7, 1).forEach { day ->
                                FilterChip(day in days, onClick = { days = if (day in days) days - day else days + day }, enabled = hasPro && !busy,
                                    label = { Text(DayOfWeek.of(if (day == 1) 7 else day - 1).getDisplayName(TextStyle.SHORT, locale)) })
                            }
                        }
                        Button(onClick = { val plan = JournalPlan(intention.trim(), goal, days); val selectedWeek = week; action { store!!.setPlan(plan, selectedWeek) } },
                            enabled = hasPro && !busy && intention.isNotBlank() && days.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.journal_plan_save))
                        }
                        if (data.plans.containsKey(week.toString())) TextButton(onClick = { val selectedWeek = week; action(reloadPlan = true) { store!!.deletePlan(selectedWeek) } },
                            enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_plan_delete)) }
                    }
                }
                item {
                    JournalPanel(stringResource(R.string.journal_daily_reflection)) {
                        OutlinedButton(onClick = {
                            DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth)
                                .apply { datePicker.maxDate = System.currentTimeMillis(); show() }
                        }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_date) + ": " + date.format(dateFormat)) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            JournalStore.outcomes.forEachIndexed { i, value ->
                                FilterChip(outcome == value, onClick = { outcome = value }, enabled = hasPro && !busy, label = { Text(stringResource(outcomeLabel(i))) })
                            }
                        }
                        OutlinedTextField(note, onValueChange = { if (JournalStore.characterCount(it) <= 2000) note = it }, enabled = hasPro && !busy,
                            label = { Text(stringResource(R.string.journal_note)) }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("${JournalStore.characterCount(note)} / 2000") })
                        Button(onClick = { val reflection = JournalReflection(note.trim(), outcome); val selectedDate = date; action { store!!.reflect(reflection, selectedDate) } },
                            enabled = hasPro && !busy && note.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_reflect_save)) }
                        Text(stringResource(R.string.journal_once), style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
                item { Text(stringResource(R.string.journal_entries), style = MaterialTheme.typography.titleLarge) }
                val entries = data.reflections.filterKeys { it >= week.toString() && it < week.plusDays(7).toString() }.toSortedMap(reverseOrder())
                if (entries.isEmpty()) item {
                    Surface(color = paper, shape = RoundedCornerShape(18.dp)) {
                        Text(stringResource(R.string.journal_empty), modifier = Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = muted)
                    }
                }
                items(entries.toList(), key = { it.first }) { (key, reflection) ->
                    Surface(color = paper, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(key, style = MaterialTheme.typography.bodySmall, color = muted)
                            Text(stringResource(outcomeLabel(JournalStore.outcomes.indexOf(reflection.outcome))), style = MaterialTheme.typography.titleMedium)
                            SelectionContainer { Text(reflection.note, style = MaterialTheme.typography.bodyMedium) }
                            TextButton(onClick = { action(reloadReflection = key == date.toString()) { store!!.deleteReflection(key) } },
                                enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_reflect_delete)) }
                        }
                    }
                }
            }
            item {
                JournalPanel(stringResource(R.string.journal_your_data)) {
                    message?.let { Text(stringResource(it), color = if (it == R.string.journal_error) MaterialTheme.colorScheme.error else muted) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Button(onClick = { export.launch("offzone-journal-${LocalDate.now()}.json") }, enabled = store != null && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_export)) }
                    exported?.let { uri -> TextButton(onClick = {
                        try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = android.content.ClipData.newRawUri("Journal", uri) }, null)) }
                        catch (_: Exception) { message = R.string.journal_error }
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_share)) } }
                    Text(stringResource(R.string.journal_private), style = MaterialTheme.typography.bodySmall, color = muted)
                    OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        enabled = hasPro && store != null && store?.loadFailed == false && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.journal_import))
                    }
                    Text(stringResource(R.string.journal_import_note), style = MaterialTheme.typography.bodySmall, color = muted)
                    HorizontalDivider(color = Ink.copy(alpha = 0.12f))
                    TextButton(onClick = { confirmDelete = true }, enabled = store != null && !busy, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF896839))) { Text(stringResource(R.string.journal_delete_all)) }
                }
            }
        }
    }
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text(stringResource(R.string.journal_confirm))},confirmButton={TextButton(onClick={confirmDelete=false; action(reloadPlan=true,reloadReflection=true) { store!!.deleteAll() }}) { Text(stringResource(R.string.journal_delete_all)) }},dismissButton={TextButton(onClick={confirmDelete=false}) { Text(stringResource(R.string.journal_cancel)) }})
}
@Composable private fun JournalPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFFFDFDF9), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
@Composable private fun JournalMetric(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF666963))
    }
}
private fun outcomeLabel(index: Int) = when(index) { 0 -> R.string.journal_kept; 1 -> R.string.journal_partly; else -> R.string.journal_again }
