package com.exchip.offzone

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var account: AccountStore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FocusController.initialize(this)
        account = (application as OffzoneApplication).accountStore
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Ink, onPrimary = Butter, background = Butter,
                surface = Butter, onSurface = Ink, secondaryContainer = Color(0xFFE4DCA9), onSecondaryContainer = Ink), typography = OffzoneTypography) { App() }
        }
    }
    override fun onResume() { super.onResume(); FocusController.tick(); if (::account.isInitialized) account.refresh() }

    @Composable private fun App() {
        val state by FocusController.state.collectAsState()
        val rules by FocusController.ruleStore.rules.collectAsState()
        val storageError by FocusController.ruleStore.error.collectAsState()
        val identity by account.state.collectAsState()
        var route by rememberSaveable { mutableStateOf(if (!OnboardingProfile.completed(this) && rules.isEmpty() && !storageError) "welcome" else "home") }
        var editing by rememberSaveable { mutableStateOf<String?>(null) }
        var editingFromReady by rememberSaveable { mutableStateOf(false) }
        var readyRuleId by rememberSaveable { mutableStateOf<String?>(null) }
        var accountReturnRoute by rememberSaveable { mutableStateOf("home") }
        var journalReturnRoute by rememberSaveable { mutableStateOf("home") }
        var paywallReturnRoute by rememberSaveable { mutableStateOf("home") }
        var disclosure by rememberSaveable { mutableStateOf(false) }
        var error by remember { mutableStateOf<Int?>(null) }
        val scope = rememberCoroutineScope()
        var pendingLocationAction by rememberSaveable { mutableIntStateOf(-1) }
        var locationDisclosure by rememberSaveable { mutableStateOf(false) }
        var pendingGeneration by rememberSaveable { mutableLongStateOf(-1L) }
        var pendingMonitorNotification by rememberSaveable { mutableStateOf(false) }
        fun dispatchPlace(action: Int) {
            error = null
            when (action) {
                1 -> FocusController.startPlaceFocus(this)
                2 -> { FocusController.setNotificationsEnabled(true); FocusController.startPlaceMonitoring(this) }
                else -> FocusController.checkArrival(this)
            }
        }
        fun cancelPlaceRequest() {
            locationDisclosure = false
            pendingLocationAction = -1; pendingGeneration = -1L; pendingMonitorNotification = false
        }
        fun placeRequestValid() = pendingLocationAction in 0..2 && pendingGeneration == FocusController.generation && route == "home"
        fun completePlaceRequest() {
            val action = pendingLocationAction
            val valid = placeRequestValid()
            cancelPlaceRequest()
            if (valid) dispatchPlace(action)
        }
        val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) completePlaceRequest()
            else {
                val valid = placeRequestValid()
                cancelPlaceRequest()
                if (valid) error = R.string.engine_location_needed
            }
        }
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            FocusController.setNotificationsEnabled(granted)
            val continueMonitor = pendingMonitorNotification && granted && placeRequestValid()
            pendingMonitorNotification = false
            if (continueMonitor) {
                if (PlaceMonitor.permissionReady(this)) completePlaceRequest()
                else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            } else cancelPlaceRequest()
        }
        fun continuePlaceRequest() {
            locationDisclosure = false
            if (!placeRequestValid()) { cancelPlaceRequest(); return }
            if (pendingLocationAction == 2 && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingMonitorNotification = true; notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (PlaceMonitor.permissionReady(this)) completePlaceRequest()
            else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        fun requestPlace(action: Int) {
            error = null
            pendingLocationAction = action; pendingGeneration = FocusController.generation
            // Reconfirm every continuous-location start, even when Android permission was granted before.
            if (action == 1 || action == 2) locationDisclosure = true
            else continuePlaceRequest()
        }
        BackHandler(route != "home") { route = when (route) {
            "ready" -> { editingFromReady = true; "edit" }; "account" -> accountReturnRoute; "journal" -> journalReturnRoute
            "paywall" -> paywallReturnRoute; else -> "home"
        } }
        Surface(Modifier.fillMaxSize()) {
            when (route) {
                "welcome" -> OnboardingScreen { _, _ -> editing = null; editingFromReady = false; route = if (rules.isEmpty()) "edit" else "home" }
                "edit" -> RuleEditorScreen(rules.firstOrNull { it.id == editing }, OnboardingProfile.startMinutes(this),
                    onBack = { editingFromReady = false; route = "home" }, onSaved = { saved ->
                        val showReady = editing == null || editingFromReady
                        error = null
                        editing = saved.id
                        editingFromReady = false
                        readyRuleId = saved.id
                        route = if (showReady) "ready" else "home"
                    }, defaultEnd = OnboardingProfile.endMinutes(this))
                "ready" -> ReadyScreen(rules.firstOrNull { it.id == readyRuleId }, onBack = { editingFromReady = true; route = "edit" }, onActivate = { rule ->
                    if (!state.connected) disclosure = true
                    else if (FocusController.activateRule(rule.id)) { error = null; paywallReturnRoute = "home"; route = "paywall" }
                    else error = R.string.save_error
                }, onHome = { route = "home" }, onAccount = { accountReturnRoute = "ready"; route = "account" },
                    showAccount = identity.configured && identity.uid == null, error = error)
                "paywall" -> ProPaywallScreen(account, onContinue = { route = paywallReturnRoute }, onOpenAccount = { accountReturnRoute = "paywall"; route = "account" })
                "account" -> AccountScreen(account, onBack = { route = accountReturnRoute }, initialGoal = OnboardingProfile.goal(this),
                    onJournal = { journalReturnRoute = "account"; route = "journal" })
                "journal" -> JournalScreen(hasPro = identity.pro && account.hasProAccess, accessCheck = { account.hasProAccess },
                    onOffer = { paywallReturnRoute = "journal"; route = "paywall" }, onBack = { route = journalReturnRoute })
                "quick" -> QuickFocus(onBack = { route = "home" }, onEnable = { disclosure = true })
                else -> {
                    var deleting by remember { mutableStateOf<FocusRule?>(null) }
                    val notifications by FocusController.notificationsEnabled.collectAsState()
                    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.home_your_space), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { accountReturnRoute = "home"; route = "account" }) { Text(stringResource(R.string.m_account)) }
                            TextButton(onClick = { editing = null; editingFromReady = false; route = "edit" }, enabled = !storageError) { Text(stringResource(R.string.m_add)) }
                        } }
                        item {
                            val active = state.appliedRule
                            val focused = state.session != null
                            val needsAction = storageError || !state.connected
                            val scheduled = state.ruleEnabled && active != null && !RulePolicy.scheduleActive(active)
                            val recentInside = state.insidePlace && state.observedAt?.let { SystemClock.elapsedRealtime() - it in 0..30_000 } == true
                            val canStart = active != null && !focused && state.connected && state.ruleEnabled && !scheduled && recentInside &&
                                !state.checkingPlace && PlaceMonitor.permissionReady(this@MainActivity)
                            val cardColor = when {
                                focused -> Ink
                                needsAction -> SoftButter
                                state.message == R.string.access_restored -> Mint
                                else -> WarmIvory
                            }
                            val foreground = if (focused) WarmIvory else Ink
                            val title = when {
                                storageError -> R.string.home_check_rules
                                focused -> R.string.home_in_focus
                                state.message == R.string.engine_paused -> R.string.home_paused
                                state.message == R.string.access_restored -> R.string.home_access_restored
                                !state.connected -> R.string.home_setup_needed
                                rules.isEmpty() -> R.string.home_create_first_rule
                                !state.ruleEnabled -> R.string.home_ready_when_you_are
                                scheduled -> R.string.home_scheduled
                                !recentInside -> R.string.home_check_location
                                else -> R.string.home_ready
                            }
                            Surface(color = cardColor, contentColor = foreground, shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            active?.let { Text(it.name, style = MaterialTheme.typography.bodyLarge) }
                                            Text(stringResource(title), style = MaterialTheme.typography.headlineLarge)
                                            if (focused) {
                                                val seconds = (state.remaining + 999) / 1000
                                                Text("%d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 44.sp))
                                            } else if (scheduled) Text(stringResource(R.string.home_starts_at, timeLabel(active.startMinutes)))
                                        }
                                        NookCatView(
                                            expression = if (focused) NookExpression.FOCUSED_FULL else if (needsAction) NookExpression.NEEDS_ACTION else NookExpression.READY_FULL,
                                            modifier = Modifier.size(width = 100.dp, height = 114.dp)
                                        )
                                    }
                                    when {
                                        focused -> Button(onClick = { FocusController.stop() }, colors = ButtonDefaults.buttonColors(containerColor = Butter, contentColor = Ink), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.restore)) }
                                        !state.connected -> Button(onClick = { disclosure = true }, colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Butter), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.enable)) }
                                        rules.isEmpty() -> Button(onClick = { editing = null; editingFromReady = false; route = "edit" }, colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Butter), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.home_create_rule)) }
                                        !state.ruleEnabled -> rules.firstOrNull()?.let { rule -> Button(onClick = { if (!FocusController.activateRule(rule.id)) error = R.string.save_error }, colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Butter), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.home_activate_rule, rule.name)) } }
                                        canStart -> Button(onClick = { requestPlace(1) }, colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Butter), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.start_focus)) }
                                    }
                                    (error ?: state.message)?.takeUnless { needsAction && it == R.string.engine_rule_ready }
                                        ?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
                                    if (state.checkingPlace) LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                        }
                        if (state.ruleEnabled && state.appliedRule != null && state.session == null) item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.m_arrival_note), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { requestPlace(0) }, enabled = !state.checkingPlace) { Text(stringResource(R.string.m_check_arrival)) }
                                state.distanceMeters?.let { Text(stringResource(R.string.m_distance, it.toInt(), state.accuracyMeters?.toInt() ?: 0), style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.m_rules), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.home_saved_count, rules.size), style = MaterialTheme.typography.bodySmall)
                        } }
                        if (storageError) item { Text(stringResource(R.string.engine_storage_error)) }
                        itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                            val applied = state.appliedRule
                            val unapplied = applied?.id == rule.id && applied != rule
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (index % 2 == 0) Mint else Blush), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(rule.name, style = MaterialTheme.typography.titleLarge)
                                Text("${timeLabel(rule.startMinutes)} – ${timeLabel(rule.endMinutes)} · ${rule.placeLabel.ifBlank { "%.4f, %.4f".format(rule.latitude, rule.longitude) }}")
                                Text(stringResource(R.string.apps_selected, rule.packages.size))
                                if (unapplied) Text(stringResource(R.string.m_unapplied))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { editing = rule.id; editingFromReady = false; route = "edit" }) { Text(stringResource(R.string.m_edit)) }
                                    TextButton(onClick = {
                                        if (state.appliedRule?.id == rule.id && !unapplied) FocusController.pauseRule()
                                        else if (!FocusController.activateRule(rule.id)) error = R.string.save_error
                                    }) { Text(stringResource(if (state.appliedRule?.id == rule.id && !unapplied) R.string.m_pause else R.string.m_apply)) }
                                    TextButton(onClick = { deleting = rule }) { Text(stringResource(R.string.m_delete)) }
                                }
                            } }
                        }
                        item {
                            OutlinedButton(onClick = { route = "quick" }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.m_quick)) }
                            OutlinedButton(onClick = { journalReturnRoute = "home"; route = "journal" }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.m_journal)) }
                        }
                        item {
                            if (state.ruleEnabled) OutlinedButton(onClick = {
                                if (state.monitoringPlace) FocusController.stopPlaceMonitoring()
                                else requestPlace(2)
                            }, enabled = state.monitoringPlace || (state.session == null && !state.checkingPlace), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Text(stringResource(if (state.monitoringPlace) R.string.engine_monitor_stop else R.string.engine_monitor_start))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.m_notifications), modifier = Modifier.weight(1f))
                                Switch(checked = notifications, onCheckedChange = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else FocusController.setNotificationsEnabled(enabled)
                                })
                            }
                            Text(stringResource(R.string.m_notifications_note), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.m_android_limits), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }) { Text(stringResource(R.string.m_settings)) }
                            TextButton(onClick = { route = "welcome" }) { Text(stringResource(R.string.m_goal)) }
                            TextButton(onClick = { FocusController.stop(); error = null }) { Text(stringResource(R.string.restore)) }
                        }
                    }
                    deleting?.let { rule -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.m_delete)) }, text = { Text(stringResource(R.string.m_delete_note, rule.name)) }, confirmButton = {
                        TextButton(onClick = { if (!FocusController.ruleStore.delete(rule.id)) error = R.string.save_error; deleting = null }) { Text(stringResource(R.string.m_delete)) }
                    }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.not_now)) } }) }
                }
            }
        }
        if (locationDisclosure) AlertDialog(onDismissRequest = { cancelPlaceRequest() },
            title = { Text(stringResource(R.string.location_disclosure_title)) },
            text = { Text(stringResource(R.string.location_disclosure_body), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { continuePlaceRequest() }) { Text(stringResource(R.string.location_disclosure_agree)) } },
            dismissButton = { TextButton(onClick = { cancelPlaceRequest() }) { Text(stringResource(R.string.not_now)) } })
        if (disclosure) AlertDialog(onDismissRequest = { disclosure = false }, title = { Text(stringResource(R.string.disclosure_title)) }, text = { Text(stringResource(R.string.disclosure_body), modifier = Modifier.verticalScroll(rememberScrollState())) }, confirmButton = {
            TextButton(onClick = { scope.launch {
                val saved = withContext(Dispatchers.IO) { AppSelection.preferences(this@MainActivity).edit().putBoolean("disclosure", true).commit() }
                disclosure = false
                if (!saved) error = R.string.save_error else runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.onFailure { error = R.string.setup_error }
            } }) { Text(stringResource(R.string.agree)) }
        }, dismissButton = { TextButton(onClick = { disclosure = false }) { Text(stringResource(R.string.not_now)) } })
    }

    @Composable private fun ReadyScreen(
        rule: FocusRule?, onBack: () -> Unit, onActivate: (FocusRule) -> Unit,
        onHome: () -> Unit, onAccount: () -> Unit, showAccount: Boolean, error: Int?
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.m_back)) }
                Surface(color = Mint, shape = RoundedCornerShape(24.dp)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                        NookCatView(NookExpression.READY_FULL, Modifier.size(width = 170.dp, height = 150.dp))
                    }
                }
                Text(stringResource(R.string.ready_headline), style = MaterialTheme.typography.headlineLarge)
                Surface(color = WarmIvory, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(rule?.name ?: stringResource(R.string.ready_rule_unavailable), style = MaterialTheme.typography.titleLarge)
                        rule?.let { Text("${timeLabel(it.startMinutes)} – ${timeLabel(it.endMinutes)}", style = MaterialTheme.typography.bodyLarge) }
                        Text(stringResource(R.string.ready_saved_note), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.ready_restore_note), style = MaterialTheme.typography.bodyLarge)
                if (showAccount) TextButton(onClick = onAccount) { Text(stringResource(R.string.ready_save_goal)) }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { rule?.let(onActivate) }, enabled = rule != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Butter),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.ready_activate)) }
                TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ready_go_home)) }
            }
        }
    }

    @Composable private fun QuickFocus(onBack: () -> Unit, onEnable: () -> Unit) {
        val state by FocusController.state.collectAsState()
        var selected by remember { mutableStateOf(AppSelection.selected(this)) }
        var picker by remember { mutableStateOf(false) }
        var minutes by rememberSaveable { mutableIntStateOf(25) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<Int?>(null) }
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.m_back)) }
            Text(stringResource(R.string.m_quick), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.m_quick_note))
            if (!state.connected) Button(onClick = onEnable) { Text(stringResource(R.string.enable)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FocusSession.durations.forEach { value -> FilterChip(minutes == value, onClick = { minutes = value }, label = { Text(stringResource(R.string.minutes, value)) }) } }
            OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.choose_apps)); Text(" · ${selected.size}") }
            Button(onClick = {
                busy = true; val generation = FocusController.generation
                scope.launch {
                    try {
                        val eligible = withContext(Dispatchers.IO) { AppSelection.load(this@MainActivity).map { it.packageName }.toSet() }
                        selected = selected.intersect(eligible)
                        if (selected.isEmpty()) error = R.string.select_hint
                        else if (!FocusController.start(selected, minutes, generation)) error = R.string.service_stopped
                        else onBack()
                    } catch (_: Exception) { error = R.string.load_error } finally { busy = false }
                }
            }, enabled = state.connected && selected.isNotEmpty() && state.session == null && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.start_focus)) }
            error?.let { Text(stringResource(it)) }
            TextButton(onClick = { FocusController.stop() }) { Text(stringResource(R.string.restore)) }
        }
        if (picker) AppPicker(selected, onClose = { picker = false }) { values ->
            scope.launch {
                val saved = withContext(Dispatchers.IO) { AppSelection.preferences(this@MainActivity).edit().putStringSet("selected", values).commit() }
                if (saved) { selected = values; picker = false } else error = R.string.save_error
            }
        }
    }
}
