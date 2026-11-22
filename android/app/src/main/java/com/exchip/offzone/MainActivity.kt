package com.exchip.offzone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle

@OptIn(ExperimentalLayoutApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var account: AccountStore
    private var foreground by mutableStateOf(false)
    private var debugProVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FocusController.initialize(this)
        account = (application as OffzoneApplication).accountStore
        EngineNotifications.refreshChannels(this)
        if (BuildConfig.DEBUG && intent?.hasExtra(ProAccessGate.EXTRA_DEBUG_PRO) == true) {
            ProAccessGate.debugProOverride = intent.getBooleanExtra(ProAccessGate.EXTRA_DEBUG_PRO, false)
        }
        ProPreview.reminderTest(this, intent)
        enableEdgeToEdge()
        setContent {
            OffzoneTheme { App() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (BuildConfig.DEBUG && intent.hasExtra(ProAccessGate.EXTRA_DEBUG_PRO)) {
            ProAccessGate.debugProOverride = intent.getBooleanExtra(ProAccessGate.EXTRA_DEBUG_PRO, false)
            debugProVersion++
        }
    }

    override fun onResume() {
        super.onResume()
        FocusController.tick()
        EngineNotifications.refreshChannels(this)
        if (::account.isInitialized) account.refresh()
        if (BuildConfig.DEBUG) debugProVersion++
    }

    override fun onStart() { super.onStart(); foreground = true }
    override fun onStop() { foreground = false; super.onStop() }

    @Composable
    private fun App() {
        val state by FocusController.state.collectAsState()
        val rules by FocusController.ruleStore.rules.collectAsState()
        val storageError by FocusController.ruleStore.error.collectAsState()
        val identity by account.state.collectAsState()
        val notifications by FocusController.notificationsEnabled.collectAsState()
        val hasProAccess = remember(identity, debugProVersion) { account.hasProAccessEffective }
        val previewPaywall = remember { ProPreview.variant(intent) }
        var route by rememberSaveable {
            mutableStateOf(
                if (previewPaywall != null) "paywall"
                else if (!OnboardingProfile.completed(this) && rules.isEmpty() && !storageError) "welcome"
                else "home",
            )
        }
        var homeClock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(route, foreground) {
            while (route in setOf("home", "settings") && foreground) {
                homeClock = SystemClock.elapsedRealtime()
                delay(1_000)
            }
        }
        var editing by rememberSaveable { mutableStateOf<String?>(null) }
        var accountReturnRoute by rememberSaveable { mutableStateOf("home") }
        var journalReturnRoute by rememberSaveable { mutableStateOf("home") }
        var paywallReturnRoute by rememberSaveable { mutableStateOf("home") }
        // D-50: the purchase button sends a signed-out user to the account screen; these bring them back to the same plan.
        var paywallResumePlan by rememberSaveable { mutableStateOf<String?>(null) }
        var paywallResumeRemind by rememberSaveable { mutableStateOf(false) }
        var disclosure by rememberSaveable { mutableStateOf(false) }
        var error by remember { mutableStateOf<Int?>(null) }
        val scope = rememberCoroutineScope()
        var pendingLocationAction by rememberSaveable { mutableIntStateOf(-1) }
        var pendingLocationRoute by rememberSaveable { mutableStateOf("home") }
        var locationDisclosure by rememberSaveable { mutableStateOf(false) }
        var pendingGeneration by rememberSaveable { mutableLongStateOf(-1L) }
        var pendingMonitorNotification by rememberSaveable { mutableStateOf(false) }

        fun openPaywall(returnTo: String) {
            paywallResumePlan = null
            paywallResumeRemind = false
            paywallReturnRoute = returnTo
            route = "paywall"
        }

        fun dispatchPlace(action: Int) {
            error = null
            when (action) {
                1 -> {
                    if (!ProAccessGate.canStartFocus(hasProAccess)) openPaywall("home")
                    else FocusController.startPlaceFocus(this)
                }
                2 -> { FocusController.setNotificationsEnabled(true); FocusController.startPlaceMonitoring(this) }
                else -> FocusController.checkArrival(this)
            }
        }

        fun cancelPlaceRequest() {
            locationDisclosure = false
            pendingLocationAction = -1; pendingGeneration = -1L; pendingMonitorNotification = false
        }

        fun placeRequestValid() = pendingLocationAction in 0..2 && pendingGeneration == FocusController.generation &&
            route == pendingLocationRoute && route in setOf("home", "settings")

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
            if (pendingLocationAction == 2 && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                pendingMonitorNotification = true; notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (PlaceMonitor.permissionReady(this)) completePlaceRequest()
            else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        fun requestPlace(action: Int) {
            error = null
            if (action == 1 && !ProAccessGate.canStartFocus(hasProAccess)) {
                openPaywall("home")
                return
            }
            // A time-only rule never reads location: no disclosure, no permission prompt.
            if (action == 1 && state.appliedRule?.anywhere == true) { dispatchPlace(1); return }
            pendingLocationAction = action; pendingGeneration = FocusController.generation
            pendingLocationRoute = route
            // Reconfirm every continuous-location start, even when Android permission was granted before.
            if (action == 1 || action == 2) locationDisclosure = true
            else continuePlaceRequest()
        }

        LaunchedEffect(identity.verified, route) {
            if (route == "account" && accountReturnRoute == "paywall" && paywallResumePlan != null && identity.verified) route = "paywall"
        }
        LaunchedEffect(hasProAccess, route) {
            if (route == "account" && accountReturnRoute == "welcome" && hasProAccess) {
                withContext(Dispatchers.IO) { OnboardingProfile.complete(this@MainActivity) }
                route = "home"
            }
        }
        BackHandler(route != "home") {
            route = when (route) {
                "edit" -> if (!OnboardingProfile.completed(this) && rules.isEmpty()) "welcome" else "home"
                "account" -> accountReturnRoute
                "journal" -> journalReturnRoute
                "paywall" -> paywallReturnRoute
                else -> "home"
            }
        }

        Surface(Modifier.fillMaxSize()) {
            when (route) {
                "welcome" -> OnboardingScreen(
                    onGetStarted = { editing = null; route = "edit" },
                    onSignIn = { accountReturnRoute = "welcome"; route = "account" },
                )
                "edit" -> RuleEditorScreen(
                    rule = rules.firstOrNull { it.id == editing },
                    defaultStart = OnboardingProfile.startMinutes(this),
                    onBack = { route = if (!OnboardingProfile.completed(this) && rules.isEmpty()) "welcome" else "home" },
                    onSaved = { saved ->
                        error = null
                        val firstTime = !OnboardingProfile.completed(this)
                        if (firstTime) OnboardingProfile.complete(this)
                        editing = saved.id
                        if (firstTime && !hasProAccess) openPaywall("home") else route = "home"
                    },
                    defaultEnd = OnboardingProfile.endMinutes(this),
                )
                "paywall" -> ProPaywallScreen(
                    store = account,
                    onContinue = { route = paywallReturnRoute },
                    onDone = {
                        if (hasProAccess && state.appliedRule == null) {
                            FocusController.autoArmSavedRule()
                        }
                        route = "home"
                    },
                    onOpenAccount = { plan, remind -> paywallResumePlan = plan; paywallResumeRemind = remind; accountReturnRoute = "paywall"; route = "account" },
                    resumePlanId = paywallResumePlan,
                    resumeRemind = paywallResumeRemind,
                    preview = previewPaywall,
                )
                "account" -> AccountScreen(
                    store = account,
                    onBack = { route = accountReturnRoute },
                    onJournal = { journalReturnRoute = "account"; route = "journal" },
                    onOpenPaywall = { openPaywall("account") },
                )
                "journal" -> JournalScreen(
                    hasPro = hasProAccess,
                    onOffer = { openPaywall("journal") },
                    onBack = { route = journalReturnRoute },
                    accessCheck = { account.hasProAccessEffective },
                )
                "settings" -> SettingsScreen(
                    state = state,
                    hasProAccess = hasProAccess,
                    notifications = notifications,
                    homeClock = homeClock,
                    error = error,
                    onBack = { route = "home" },
                    onOpenAccount = { accountReturnRoute = "settings"; route = "account" },
                    onOpenJournal = { journalReturnRoute = "settings"; route = "journal" },
                    onToggleNotifications = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else FocusController.setNotificationsEnabled(enabled)
                    },
                    onToggleMonitor = {
                        if (state.monitoringPlace) FocusController.stopPlaceMonitoring()
                        else requestPlace(2)
                    },
                    onCheckArrival = { requestPlace(0) },
                    onOpenAppSettings = {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    },
                    onRestore = { FocusController.stop(); error = null },
                )
                "quick" -> QuickFocus(
                    hasProAccess = hasProAccess,
                    onBack = { route = "home" },
                    onEnable = { disclosure = true },
                    onRequirePro = { openPaywall("home") },
                )
                else -> {
                    var deleting by remember { mutableStateOf<FocusRule?>(null) }
                    val locale = LocalConfiguration.current.locales[0]
                    val largeFont = LocalConfiguration.current.fontScale >= 1.4f
                    LazyColumn(
                        Modifier.fillMaxSize().safeDrawingPadding(),
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.home_your_space), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { route = "settings" }) {
                                        Icon(
                                            Icons.Filled.Notifications,
                                            contentDescription = stringResource(R.string.audit_settings),
                                            tint = if (notifications) Pine else InkMuted,
                                        )
                                    }
                                    IconButton(onClick = { accountReturnRoute = "home"; route = "account" }) {
                                        Icon(
                                            Icons.Filled.AccountCircle,
                                            contentDescription = stringResource(R.string.m_account),
                                            tint = Ink,
                                        )
                                    }
                                    if (!largeFont) {
                                        TextButton(onClick = { editing = null; route = "edit" }, enabled = !storageError) {
                                            Text(stringResource(R.string.home_new_rule))
                                        }
                                    }
                                }
                                if (largeFont) {
                                    TextButton(onClick = { editing = null; route = "edit" }, enabled = !storageError) {
                                        Text(stringResource(R.string.home_new_rule))
                                    }
                                }
                            }
                        }
                        item {
                            val active = state.appliedRule
                            val focused = state.session != null
                            val locked = !focused && !hasProAccess
                            val needsAction = locked || storageError || !state.connected
                            val scheduled = remember(homeClock, state.ruleEnabled, active) { state.ruleEnabled && active != null && !RulePolicy.scheduleActive(active) }
                            val recentInside = state.insidePlace && state.observedAt?.let { homeClock - it in 0..30_000 } == true
                            val canStart = hasProAccess && active != null && !focused && state.connected && state.ruleEnabled && !scheduled && !state.checkingPlace
                            val cardColor = when {
                                focused -> Pine
                                needsAction -> SoftButter
                                state.message == R.string.access_restored -> Mint
                                else -> WarmIvory
                            }
                            val foreground = if (focused) Butter else Ink
                            val title = when {
                                focused -> R.string.home_in_focus
                                locked -> R.string.home_locked_title
                                storageError -> R.string.home_check_rules
                                state.message == R.string.engine_paused -> R.string.home_paused
                                state.message == R.string.access_restored -> R.string.home_access_restored
                                !state.connected -> R.string.home_setup_needed
                                rules.isEmpty() -> R.string.home_create_first_rule
                                !state.ruleEnabled -> R.string.home_ready_when_you_are
                                scheduled -> R.string.home_scheduled
                                else -> R.string.home_ready
                            }
                            val sessionLabel = when {
                                focused && state.quickSession -> stringResource(R.string.m_quick)
                                else -> active?.name
                            }
                            Surface(color = cardColor, contentColor = foreground, shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (!locked) sessionLabel?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                                            Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
                                            if (focused) {
                                                val seconds = (state.remaining + 999) / 1000
                                                Text("%d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 44.sp))
                                            } else if (locked) {
                                                Text(stringResource(R.string.home_locked_body), style = MaterialTheme.typography.bodyLarge)
                                            } else if (scheduled) {
                                                RulePolicy.nextStart(active!!)?.let { next ->
                                                    val time = timeLabel(this@MainActivity, next.hour * 60 + next.minute)
                                                    Text(
                                                        if (next.toLocalDate() == LocalDate.now(next.zone)) stringResource(R.string.home_starts_at, time)
                                                        else stringResource(R.string.home_starts_on, dayLabel(next.dayOfWeek, TextStyle.FULL, locale), time),
                                                    )
                                                }
                                            }
                                        }
                                        if (LocalConfiguration.current.fontScale < 1.5f) {
                                            NookCatView(
                                                expression = if (focused) NookExpression.FOCUSED_FULL else if (needsAction) NookExpression.NEEDS_ACTION else NookExpression.READY_FULL,
                                                modifier = Modifier.size(width = 100.dp, height = 114.dp),
                                            )
                                        }
                                    }
                                    when {
                                        focused -> Button(
                                            onClick = { FocusController.stop() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Butter, contentColor = Ink),
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                        ) { Text(stringResource(R.string.restore)) }
                                        locked -> PrimaryButton(onClick = { openPaywall("home") }, modifier = Modifier.fillMaxWidth()) {
                                            Text(stringResource(R.string.home_unlock_pro))
                                        }
                                        !state.connected -> PrimaryButton(onClick = { disclosure = true }, modifier = Modifier.fillMaxWidth()) {
                                            Text(stringResource(R.string.enable))
                                        }
                                        rules.isEmpty() -> PrimaryButton(onClick = { editing = null; route = "edit" }, modifier = Modifier.fillMaxWidth()) {
                                            Text(stringResource(R.string.home_create_rule))
                                        }
                                        !state.ruleEnabled -> rules.firstOrNull()?.let { rule ->
                                            PrimaryButton(
                                                onClick = {
                                                    if (!ProAccessGate.canActivateRule(hasProAccess)) openPaywall("home")
                                                    else if (!FocusController.activateRule(rule.id)) error = R.string.save_error
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) { Text(stringResource(R.string.home_activate_rule, rule.name)) }
                                        }
                                        canStart -> PrimaryButton(onClick = { requestPlace(1) }, modifier = Modifier.fillMaxWidth()) {
                                            Text(stringResource(R.string.start_focus))
                                        }
                                    }
                                    if (canStart && active.anywhere) Text(stringResource(R.string.audit_start_time_only), style = MaterialTheme.typography.bodyMedium)
                                    else if (canStart && !recentInside) Text(stringResource(R.string.audit_start_location), style = MaterialTheme.typography.bodyMedium)
                                    (error ?: state.message)?.takeUnless { it == R.string.engine_rule_ready || it == R.string.engine_reconnected }
                                        ?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                                    if (state.checkingPlace) LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = PineHairline)
                                }
                            }
                        }
                        if (state.session == null) item {
                            SecondaryButton(
                                onClick = {
                                    if (ProAccessGate.canQuickFocus(hasProAccess)) route = "quick"
                                    else openPaywall("home")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.m_quick)) }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.m_rules), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.home_saved_count, rules.size), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (storageError) item { Text(stringResource(R.string.engine_storage_error)) }
                        items(rules, key = { it.id }) { rule ->
                            val applied = state.appliedRule
                            val unapplied = applied?.id == rule.id && applied != rule
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = WarmIvory),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, PineHairline),
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(rule.name, style = MaterialTheme.typography.titleLarge)
                                    Text(scheduleLabel(rule), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (rule.anywhere) getString(R.string.editor_time_only_place) else rule.placeLabel.ifBlank { getString(R.string.audit_saved_place) },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(stringResource(R.string.apps_selected, rule.packages.size))
                                    if (unapplied) Text(stringResource(R.string.m_unapplied))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { editing = rule.id; route = "edit" }) { Text(stringResource(R.string.m_edit)) }
                                        OutlinedButton(onClick = {
                                            if (state.appliedRule?.id == rule.id && !unapplied) {
                                                FocusController.pauseRule()
                                            } else if (!ProAccessGate.canActivateRule(hasProAccess)) {
                                                openPaywall("home")
                                            } else if (!FocusController.activateRule(rule.id)) {
                                                error = R.string.save_error
                                            }
                                        }) {
                                            Text(stringResource(if (state.appliedRule?.id == rule.id && !unapplied) R.string.m_pause else R.string.m_apply))
                                        }
                                        TextButton(onClick = { deleting = rule }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                            Text(stringResource(R.string.m_delete))
                                        }
                                    }
                                }
                            }
                        }
                        if (ProAccessGate.canRestoreActiveSession(state.session != null)) {
                            item {
                                TextButton(onClick = { FocusController.stop(); error = null }) { Text(stringResource(R.string.restore)) }
                            }
                        }
                    }
                    deleting?.let { rule ->
                        AlertDialog(
                            onDismissRequest = { deleting = null },
                            title = { Text(stringResource(R.string.m_delete)) },
                            text = { Text(stringResource(R.string.m_delete_note, rule.name)) },
                            confirmButton = {
                                TextButton(onClick = { if (!FocusController.ruleStore.delete(rule.id)) error = R.string.save_error; deleting = null }) {
                                    Text(stringResource(R.string.m_delete))
                                }
                            },
                            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.not_now)) } },
                        )
                    }
                }
            }
        }
        if (locationDisclosure) AlertDialog(
            onDismissRequest = { cancelPlaceRequest() },
            title = { Text(stringResource(R.string.location_disclosure_title)) },
            text = { Text(stringResource(R.string.location_disclosure_body), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { continuePlaceRequest() }) { Text(stringResource(R.string.location_disclosure_agree)) } },
            dismissButton = { TextButton(onClick = { cancelPlaceRequest() }) { Text(stringResource(R.string.not_now)) } },
        )
        if (disclosure) AlertDialog(
            onDismissRequest = { disclosure = false },
            title = { Text(stringResource(R.string.disclosure_title)) },
            text = { Text(stringResource(R.string.disclosure_body), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { AppSelection.preferences(this@MainActivity).edit().putBoolean("disclosure", true).commit() }
                        disclosure = false
                        if (!saved) error = R.string.save_error else runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.onFailure { error = R.string.setup_error }
                    }
                }) { Text(stringResource(R.string.agree)) }
            },
            dismissButton = { TextButton(onClick = { disclosure = false }) { Text(stringResource(R.string.not_now)) } },
        )
    }
}

@Composable
private fun SettingsScreen(
    state: FocusState,
    hasProAccess: Boolean,
    notifications: Boolean,
    homeClock: Long,
    error: Int?,
    onBack: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenJournal: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleMonitor: () -> Unit,
    onCheckArrival: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.m_back)) }
        Text(stringResource(R.string.audit_settings), style = MaterialTheme.typography.headlineLarge)
        Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.m_account), style = MaterialTheme.typography.titleMedium, color = Ink)
                            Text(
                                stringResource(if (hasProAccess) R.string.account_pro_active else R.string.account_free_space),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                            )
                        }
                    }
                }
                HorizontalDivider(color = PineHairline)
                TextButton(onClick = onOpenJournal, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.m_journal), style = MaterialTheme.typography.titleMedium, color = Ink)
                    }
                }
            }
        }
        Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.m_notifications), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = notifications,
                        colors = SwitchDefaults.colors(uncheckedThumbColor = InkMuted, uncheckedTrackColor = WarmIvory, uncheckedBorderColor = PineLine),
                        onCheckedChange = onToggleNotifications,
                    )
                }
                Text(stringResource(R.string.m_notifications_note), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                if (state.ruleEnabled && state.appliedRule?.anywhere != true) {
                    HorizontalDivider(color = PineHairline)
                    SecondaryButton(
                        onClick = onToggleMonitor,
                        enabled = state.monitoringPlace || (state.session == null && !state.checkingPlace),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(if (state.monitoringPlace) R.string.engine_monitor_stop else R.string.engine_monitor_start))
                    }
                    if (state.session == null) {
                        TextButton(onClick = onCheckArrival, enabled = !state.checkingPlace && !state.monitoringPlace) {
                            Text(stringResource(if (state.monitoringPlace) R.string.audit_monitoring else R.string.m_check_arrival))
                        }
                        if (state.checkingPlace) LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = PineHairline)
                        state.message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                        state.observedAt?.takeIf { homeClock - it in 0..30_000 }?.let {
                            state.distanceMeters?.let { distance ->
                                Text(stringResource(R.string.m_distance, distance.toInt(), state.accuracyMeters?.toInt() ?: 0))
                            }
                        }
                    }
                }
            }
        }
        Surface(color = WarmIvory, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, PineHairline)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.m_android_limits), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                Text(stringResource(R.string.engine_restart_note), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                SecondaryButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.m_settings))
                }
            }
        }
        if (ProAccessGate.canRestoreActiveSession(state.session != null)) {
            PrimaryButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.restore))
            }
        }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickFocus(
    hasProAccess: Boolean,
    onBack: () -> Unit,
    onEnable: () -> Unit,
    onRequirePro: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by FocusController.state.collectAsState()
    var selected by remember { mutableStateOf(AppSelection.selected(context)) }
    var picker by remember { mutableStateOf(false) }
    var minutes by rememberSaveable { mutableIntStateOf(25) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.m_back)) }
        Text(stringResource(R.string.m_quick), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.m_quick_note))
        if (!state.connected) PrimaryButton(onClick = onEnable, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.enable)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusSession.durations.forEach { value ->
                OffzoneChip(minutes == value, onClick = { minutes = value }) { Text(stringResource(R.string.minutes, value)) }
            }
        }
        SecondaryButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.choose_apps)); Text(" · ${selected.size}")
        }
        PrimaryButton(
            onClick = {
                if (!ProAccessGate.canQuickFocus(hasProAccess)) {
                    onRequirePro()
                    return@PrimaryButton
                }
                busy = true; val generation = FocusController.generation
                scope.launch {
                    try {
                        val eligible = withContext(Dispatchers.IO) { AppSelection.load(context).map { it.packageName }.toSet() }
                        selected = selected.intersect(eligible)
                        if (selected.isEmpty()) error = R.string.select_hint
                        else if (!FocusController.start(selected, minutes, generation)) error = R.string.service_stopped
                        else onBack()
                    } catch (_: Exception) { error = R.string.load_error } finally { busy = false }
                }
            },
            enabled = state.connected && selected.isNotEmpty() && state.session == null && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.start_focus)) }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (ProAccessGate.canRestoreActiveSession(state.session != null)) {
            TextButton(onClick = { FocusController.stop() }) { Text(stringResource(R.string.restore)) }
        }
    }
    if (picker) AppPicker(selected, onClose = { picker = false }) { values ->
        scope.launch {
            val saved = withContext(Dispatchers.IO) { AppSelection.preferences(context).edit().putStringSet("selected", values).commit() }
            if (saved) { selected = values; picker = false } else error = R.string.save_error
        }
    }
}
