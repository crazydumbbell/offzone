package com.exchip.offzone

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        BackHandler(route != "home") { route = "home" }
        Surface(Modifier.fillMaxSize()) {
            when (route) {
                "welcome" -> OnboardingScreen { _, _ -> editing = null; route = if (rules.isEmpty()) "edit" else "home" }
                "edit" -> RuleEditorScreen(rules.firstOrNull { it.id == editing }, when (OnboardingProfile.window(this)) { "afternoon" -> 840; "evening" -> 1200; else -> 540 },
                    onBack = { route = "home" }, onSaved = { route = "home" })
                "account" -> AccountScreen(account, onBack = { route = "home" }, initialGoal = OnboardingProfile.goal(this))
                "journal" -> JournalScreen(hasPro = identity.pro && account.hasProAccess, accessCheck = { account.hasProAccess }, onOffer = { route = "account" }, onBack = { route = "home" })
                "quick" -> QuickFocus(onBack = { route = "home" }, onEnable = { disclosure = true })
                else -> {
                    var deleting by remember { mutableStateOf<FocusRule?>(null) }
                    val notifications by FocusController.notificationsEnabled.collectAsState()
                    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("offzone", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { route = "account" }) { Text(stringResource(R.string.m_account)) }
                        } }
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(if (state.session != null) R.string.focus_title else R.string.ready_title), style = MaterialTheme.typography.headlineLarge)
                                Text(stringResource(goalLabel(OnboardingProfile.goal(this@MainActivity))), style = MaterialTheme.typography.bodyLarge)
                            }
                            NookCatView(
                                expression = if (state.session != null) NookExpression.FOCUSED_FULL else NookExpression.READY_FULL,
                                modifier = Modifier.size(width = 132.dp, height = 140.dp)
                            )
                        } }
                        item { Surface(color = Ink, contentColor = Butter, shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(state.appliedRule?.name ?: stringResource(R.string.m_no_rule), style = MaterialTheme.typography.titleLarge)
                                if (state.session != null) {
                                    val seconds = (state.remaining + 999) / 1000
                                    Text("%d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 44.sp))
                                    Button(onClick = { FocusController.stop() }, colors = ButtonDefaults.buttonColors(containerColor = Butter, contentColor = Ink), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.restore)) }
                                } else if (!state.connected) {
                                    Text(stringResource(R.string.setup_hint))
                                    Button(onClick = { disclosure = true }, colors = ButtonDefaults.buttonColors(containerColor = Butter, contentColor = Ink)) { Text(stringResource(R.string.enable)) }
                                } else if (state.ruleEnabled) {
                                    Text(stringResource(R.string.m_arrival_note))
                                    state.appliedRule?.let { Text("${timeLabel(it.startMinutes)} – ${timeLabel(it.endMinutes)}") }
                                    Button(onClick = {
                                        requestPlace(1)
                                    }, enabled = !state.checkingPlace, colors = ButtonDefaults.buttonColors(containerColor = Butter, contentColor = Ink), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.start_focus)) }
                                    TextButton(onClick = {
                                        requestPlace(0)
                                    }, enabled = !state.checkingPlace, colors = ButtonDefaults.textButtonColors(contentColor = Butter)) { Text(stringResource(R.string.m_check_arrival)) }
                                } else Text(stringResource(R.string.m_activate_hint))
                                (error ?: state.message)?.let { Text(stringResource(it)) }
                                if (state.checkingPlace) LinearProgressIndicator(Modifier.fillMaxWidth())
                                state.distanceMeters?.let { Text(stringResource(R.string.m_distance, it.toInt(), state.accuracyMeters?.toInt() ?: 0), style = MaterialTheme.typography.bodySmall) }
                            }
                        } }
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.m_rules), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editing = null; route = "edit" }, enabled = !storageError) { Text(stringResource(R.string.m_add)) }
                        } }
                        if (storageError) item { Text(stringResource(R.string.engine_storage_error)) }
                        items(rules, key = { it.id }) { rule ->
                            val applied = state.appliedRule
                            val unapplied = applied?.id == rule.id && applied != rule
                            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(rule.name, style = MaterialTheme.typography.titleLarge)
                                Text("${timeLabel(rule.startMinutes)} – ${timeLabel(rule.endMinutes)} · ${rule.placeLabel.ifBlank { "%.4f, %.4f".format(rule.latitude, rule.longitude) }}")
                                Text(stringResource(R.string.apps_selected, rule.packages.size))
                                if (unapplied) Text(stringResource(R.string.m_unapplied))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { editing = rule.id; route = "edit" }) { Text(stringResource(R.string.m_edit)) }
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
                            OutlinedButton(onClick = { route = "journal" }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.m_journal)) }
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
