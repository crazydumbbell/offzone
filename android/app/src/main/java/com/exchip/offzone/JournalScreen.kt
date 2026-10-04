package com.exchip.offzone

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val dateSaver = Saver<LocalDate, String>(save = { it.toString() }, restore = { LocalDate.parse(it) })
    var week by rememberSaveable(stateSaver = dateSaver) { mutableStateOf(JournalStore.week(LocalDate.now())) }
    var date by rememberSaveable(stateSaver = dateSaver) { mutableStateOf(LocalDate.now()) }
    var intention by rememberSaveable { mutableStateOf("") }; var goal by rememberSaveable { mutableStateOf(OnboardingProfile.goal(context)) }
    var days by rememberSaveable(stateSaver = Saver<Set<Int>, ArrayList<Int>>(save = { ArrayList(it) }, restore = { it.toSet() })) { mutableStateOf(setOf(2,3,4,5,6)) }
    var note by rememberSaveable { mutableStateOf("") }; var outcome by rememberSaveable { mutableStateOf(JournalStore.outcomes.first()) }
    var message by remember { mutableStateOf<Int?>(null) }; var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }; var exported by remember { mutableStateOf<Uri?>(null) }
    var planDirty by rememberSaveable { mutableStateOf(false) }
    var reflectionDirty by rememberSaveable { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun navigate(next: () -> Unit) {
        if (busy) return
        if (planDirty || reflectionDirty) pendingNavigation = next else next()
    }
    BackHandler { navigate(onBack) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(context.getString(it)); message = null }
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { store = withContext(Dispatchers.IO) { JournalStore(context) { access.value() } } }
    LaunchedEffect(week,store,planRevision) {
        if (store == null || planDirty) return@LaunchedEffect
        val plan=store?.data?.plans?.get(week.toString())
        intention=plan?.intention ?: ""; goal=plan?.context ?: OnboardingProfile.goal(context); days=plan?.weekdays ?: setOf(2,3,4,5,6)
    }
    LaunchedEffect(date,store,reflectionRevision) { if (store == null || reflectionDirty) return@LaunchedEffect; val entry=store?.data?.reflections?.get(date.toString()); note=entry?.note ?: ""; outcome=entry?.outcome ?: JournalStore.outcomes.first() }
    val data = remember(store, revision) { store?.data ?: JournalData() }
    fun action(reloadPlan: Boolean = false, reloadReflection: Boolean = false, block: () -> Unit) {
        if(busy) return
        busy=true
        scope.launch {
            try { withContext(Dispatchers.IO) { block() }; revision++; if(reloadPlan) { planDirty=false; planRevision++ }; if(reloadReflection) { reflectionDirty=false; reflectionRevision++ }; exported=null; message=R.string.journal_saved }
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
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    Surface(Modifier.fillMaxSize(), color = Butter, contentColor = Ink) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                TextButton(onClick = { navigate(onBack) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_back)) }
                Text(stringResource(R.string.journal_title), style = MaterialTheme.typography.headlineSmall)
            }
            item {
                Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.journal_intro), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            NookCatView(NookExpression.REFLECTION, Modifier.size(96.dp))
                        }
                        Text(stringResource(R.string.journal_local), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                    }
                }
            }
            if (!hasPro) item {
                Surface(color = Mint, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Pro", style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.audit_journal_read_only), style = MaterialTheme.typography.bodyLarge)
                        if (!BuildConfig.PRO_OFFER_READY) Text(stringResource(R.string.account_plans_unavailable), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                        if (BuildConfig.PRO_OFFER_READY) SecondaryButton(onClick = { navigate(onOffer) }) { Text(stringResource(R.string.journal_offer)) }
                    }
                }
            }
            if (store == null) item { CircularProgressIndicator() }
            else if (store!!.loadFailed) item {
                Surface(color = SoftButter, shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(R.string.journal_corrupt), modifier = Modifier.fillMaxWidth().padding(18.dp), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                item {
                    JournalPanel(stringResource(R.string.journal_weekly_overview)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val previous = stringResource(R.string.journal_previous)
                            val next = stringResource(R.string.journal_next)
                            IconButton(onClick = { navigate { week = week.minusWeeks(1) } }, enabled = !busy, modifier = Modifier.semantics { contentDescription = previous }) { Text("‹", fontSize = 30.sp) }
                            Text(week.format(dateFormat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            IconButton(onClick = { navigate { week = week.plusWeeks(1) } }, enabled = !busy, modifier = Modifier.semantics { contentDescription = next }) { Text("›", fontSize = 30.sp) }
                        }
                        val summary = store!!.summary(week)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            JournalMetric(summary.first, stringResource(R.string.journal_planned_days), Modifier.weight(1f))
                            JournalMetric(summary.second, stringResource(R.string.journal_reflected_days), Modifier.weight(1f))
                            JournalMetric(summary.third, stringResource(R.string.journal_kept_days), Modifier.weight(1f))
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.journal_summary_note), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                    }
                }
                item {
                    JournalPanel(stringResource(R.string.journal_weekly_intention)) {
                        if (hasPro) {
                        OutlinedTextField(intention, onValueChange = { if (it != intention && JournalStore.characterCount(it) <= 160) { intention = it; planDirty = true } }, enabled = hasPro && !busy,
                            label = { Text(stringResource(R.string.journal_intention)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            JournalStore.contexts.forEach { key -> OffzoneChip(goal == key, onClick = { if (goal != key) { goal = key; planDirty = true } }, enabled = hasPro && !busy) { Text(stringResource(goalLabel(key))) } }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 3, 4, 5, 6, 7, 1).forEach { day ->
                                OffzoneChip(day in days, onClick = { days = if (day in days) days - day else days + day; planDirty = true }, enabled = hasPro && !busy) {
                                    Text(DayOfWeek.of(if (day == 1) 7 else day - 1).getDisplayName(TextStyle.SHORT, locale))
                                }
                            }
                        }
                        PrimaryButton(onClick = { val plan = JournalPlan(intention.trim(), goal, days); val selectedWeek = week; action(reloadPlan = true) { store!!.setPlan(plan, selectedWeek) } },
                            enabled = hasPro && !busy && intention.isNotBlank() && days.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.journal_plan_save))
                        }
                        } else {
                            val savedPlan = data.plans[week.toString()]
                            SelectionContainer { Text(savedPlan?.intention ?: stringResource(R.string.journal_empty), style = MaterialTheme.typography.bodyLarge) }
                            savedPlan?.let { plan ->
                                Text(stringResource(goalLabel(plan.context)), style = MaterialTheme.typography.bodyMedium)
                                Text(listOf(2, 3, 4, 5, 6, 7, 1).filter { it in plan.weekdays }.joinToString(" · ") {
                                    DayOfWeek.of(if (it == 1) 7 else it - 1).getDisplayName(TextStyle.SHORT, locale)
                                }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (data.plans.containsKey(week.toString())) TextButton(onClick = { val selectedWeek = week; pendingDelete = { action(reloadPlan = true) { store!!.deletePlan(selectedWeek) } } },
                            enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_plan_delete)) }
                    }
                }
                if (hasPro) item {
                    JournalPanel(stringResource(R.string.journal_daily_reflection)) {
                        SecondaryButton(onClick = {
                            DatePickerDialog(context, { _, y, m, d ->
                                val selected = LocalDate.of(y, m + 1, d)
                                if (selected != date) navigate { date = selected }
                            }, date.year, date.monthValue - 1, date.dayOfMonth)
                                .apply { datePicker.maxDate = System.currentTimeMillis(); show() }
                        }, enabled = !busy) { Text(stringResource(R.string.journal_date) + ": " + date.format(dateFormat)) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            JournalStore.outcomes.forEachIndexed { i, value ->
                                OffzoneChip(outcome == value, onClick = { if (outcome != value) { outcome = value; reflectionDirty = true } }, enabled = hasPro && !busy) { Text(stringResource(outcomeLabel(i))) }
                            }
                        }
                        OutlinedTextField(note, onValueChange = { if (it != note && JournalStore.characterCount(it) <= 2000) { note = it; reflectionDirty = true } }, enabled = hasPro && !busy,
                            label = { Text(stringResource(R.string.journal_note)) }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("${JournalStore.characterCount(note)} / 2000") })
                        PrimaryButton(onClick = { val reflection = JournalReflection(note.trim(), outcome); val selectedDate = date; action(reloadReflection = true) { store!!.reflect(reflection, selectedDate) } },
                            enabled = hasPro && !busy && note.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.journal_reflect_save)) }
                        Text(stringResource(R.string.journal_once), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                    }
                }
                item { Text(stringResource(R.string.journal_entries), style = MaterialTheme.typography.titleLarge) }
                val entries = data.reflections.filterKeys { it >= week.toString() && it < week.plusDays(7).toString() }.toSortedMap(reverseOrder())
                if (entries.isEmpty()) item {
                    Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
                        Text(stringResource(R.string.journal_empty), modifier = Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                    }
                }
                items(entries.toList(), key = { it.first }) { (key, reflection) ->
                    Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(key, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                            Text(stringResource(outcomeLabel(JournalStore.outcomes.indexOf(reflection.outcome))), style = MaterialTheme.typography.titleMedium)
                            SelectionContainer { Text(reflection.note, style = MaterialTheme.typography.bodyMedium) }
                            TextButton(onClick = { pendingDelete = { action(reloadReflection = key == date.toString()) { store!!.deleteReflection(key) } } },
                                enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_reflect_delete)) }
                        }
                    }
                }
            }
            item {
                JournalPanel(stringResource(R.string.journal_your_data)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = PineHairline)
                    PrimaryButton(onClick = { export.launch("offzone-journal-${LocalDate.now()}.json") }, enabled = store != null && !busy,
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.journal_export)) }
                    exported?.let { uri -> TextButton(onClick = {
                        try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = android.content.ClipData.newRawUri("Journal", uri) }, null)) }
                        catch (_: Exception) { message = R.string.journal_error }
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.journal_share)) } }
                    Text(stringResource(R.string.journal_private), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                    if (hasPro) {
                    SecondaryButton(onClick = { navigate { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } },
                        enabled = hasPro && store != null && store?.loadFailed == false && !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.journal_import))
                    }
                    Text(stringResource(R.string.journal_import_note), style = MaterialTheme.typography.bodyLarge, color = InkMuted)
                    }
                    HorizontalDivider()
                    TextButton(onClick = { confirmDelete = true }, enabled = store != null && !busy, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Warning)) { Text(stringResource(R.string.journal_delete_all)) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
    if (pendingNavigation != null) AlertDialog(onDismissRequest = { pendingNavigation = null },
        title = { Text(stringResource(R.string.audit_journal_unsaved)) },
        text = { Text(stringResource(R.string.audit_journal_discard_message)) },
        confirmButton = { TextButton(onClick = {
            val next = pendingNavigation; pendingNavigation = null
            planDirty = false; reflectionDirty = false; planRevision++; reflectionRevision++
            next?.invoke()
        }) { Text(stringResource(R.string.audit_journal_discard)) } },
        dismissButton = { TextButton(onClick = { pendingNavigation = null }) { Text(stringResource(R.string.audit_journal_keep_editing)) } })
    if (pendingDelete != null) AlertDialog(onDismissRequest = { pendingDelete = null },
        title = { Text(stringResource(R.string.audit_journal_delete_entry)) },
        text = { Text(stringResource(R.string.audit_journal_delete_message)) },
        confirmButton = { TextButton(onClick = { val remove = pendingDelete; pendingDelete = null; remove?.invoke() }) { Text(stringResource(R.string.audit_journal_delete)) } },
        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.journal_cancel)) } })
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text(stringResource(R.string.journal_confirm))},confirmButton={TextButton(onClick={confirmDelete=false; action(reloadPlan=true,reloadReflection=true) { store!!.deleteAll() }}) { Text(stringResource(R.string.journal_delete_all)) }},dismissButton={TextButton(onClick={confirmDelete=false}) { Text(stringResource(R.string.journal_cancel)) }})
}
@Composable private fun JournalPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
@Composable private fun JournalMetric(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
    }
}
private fun outcomeLabel(index: Int) = when(index) { 0 -> R.string.journal_kept; 1 -> R.string.journal_partly; else -> R.string.journal_again }
