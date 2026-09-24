package com.exchip.offzone

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZonedDateTime

data class FocusState(
    val connected: Boolean = false,
    val session: FocusSession? = null,
    val remaining: Long = 0,
    val message: Int? = null,
    val appliedRule: FocusRule? = null,
    val ruleEnabled: Boolean = false,
    val insidePlace: Boolean = false,
    val checkingPlace: Boolean = false,
    val observedAt: Long? = null,
    val distanceMeters: Float? = null,
    val accuracyMeters: Float? = null,
    val placeSession: Boolean = false,
    val monitoringPlace: Boolean = false,
)

object FocusController {
    private val mutableState = MutableStateFlow(FocusState())
    val state = mutableState.asStateFlow()
    private var context: Context? = null
    lateinit var ruleStore: RuleStore
        private set
    var generation = 0L
        private set
    var onChange: (() -> Unit)? = null
    private var cancelCheck: (() -> Unit)? = null
    private var endWallMillis: Long? = null
    private val mutableNotifications = MutableStateFlow(false)
    val notificationsEnabled = mutableNotifications.asStateFlow()

    fun initialize(context: Context) {
        if (this.context != null) return
        this.context = context.applicationContext
        ruleStore = RuleStore(context)
        mutableNotifications.value = AppSelection.preferences(context).getBoolean("zoneNotifications", false)
        // Applied configuration survives; intent to block never survives process death/reboot.
        runCatching { ruleStore.applied() }.onSuccess {
            mutableState.value = state.value.copy(appliedRule = it, ruleEnabled = it != null)
        }.onFailure { mutableState.value = state.value.copy(message = R.string.engine_storage_error) }
    }
    fun setNotificationsEnabled(enabled: Boolean) {
        context?.let { AppSelection.preferences(it).edit().putBoolean("zoneNotifications", enabled).apply() }
        mutableNotifications.value = enabled
        if (!enabled) context?.getSystemService(android.app.NotificationManager::class.java)?.cancel(411)
    }
    fun connect() { stopInternal(null); mutableState.value = state.value.copy(connected = true) }
    fun disconnect() {
        stopInternal(R.string.service_stopped)
        mutableState.value = state.value.copy(connected = false)
        onChange = null
    }
    fun activateRule(id: String): Boolean {
        if (!::ruleStore.isInitialized || ruleStore.error.value) return false
        val rule = ruleStore.rules.value.firstOrNull { it.id == id } ?: return false
        if (!ruleStore.apply(rule)) return false
        stopInternal(null)
        mutableState.value = state.value.copy(appliedRule = rule, ruleEnabled = true, message = R.string.engine_rule_ready)
        return true
    }
    fun pauseRule() {
        stopInternal(R.string.engine_paused)
        val saved = !::ruleStore.isInitialized || ruleStore.apply(null)
        mutableState.value = state.value.copy(ruleEnabled = false, appliedRule = null,
            message = if (saved) R.string.engine_paused else R.string.engine_storage_error)
    }
    fun start(packages: Set<String>, minutes: Int, expectedGeneration: Long = generation): Boolean {
        if (expectedGeneration != generation || !state.value.connected || state.value.session != null || packages.isEmpty()) return false
        if (minutes !in FocusSession.durations) return false
        val safe = context?.let { packages - AppSelection.protectedPackages(it) } ?: packages
        if (safe.isEmpty()) return false
        stopInternal(null)
        val session = FocusSession.start(safe, minutes, SystemClock.elapsedRealtime())
        mutableState.value = state.value.copy(session = session, remaining = session.remaining(SystemClock.elapsedRealtime()), message = null, placeSession = false)
        onChange?.invoke()
        notify(R.string.engine_started)
        return true
    }
    fun stop(message: Int = R.string.access_restored) { stopInternal(message) }
    private fun invalidateChecks() {
        generation++
        cancelCheck?.invoke(); cancelCheck = null
    }
    private fun stopInternal(message: Int?) {
        val wasActive = state.value.session != null
        invalidateChecks()
        endWallMillis = null
        mutableState.value = state.value.copy(session = null, remaining = 0, message = message,
            insidePlace = false, checkingPlace = false, observedAt = null, placeSession = false, monitoringPlace = false)
        context?.stopService(Intent(context, PlaceMonitor::class.java))
        onChange?.invoke()
        if (wasActive) notify(R.string.engine_ended)
    }
    fun checkArrival(context: Context) {
        if (!state.value.monitoringPlace) requestArrival(context, false)
    }
    fun startPlaceFocus(context: Context) { requestArrival(context, true) }
    fun startPlaceMonitoring(context: Context) { requestArrival(context, false, monitor = true) }
    fun stopPlaceMonitoring() { stopInternal(R.string.engine_monitor_stopped) }
    private fun requestArrival(context: Context, start: Boolean, monitor: Boolean = false) {
        initialize(context)
        val current = state.value
        if (ruleStore.error.value) { mutableState.value = current.copy(message = R.string.engine_storage_error); return }
        if (current.checkingPlace || current.session != null) return
        if (!current.ruleEnabled || current.appliedRule == null || (start && !current.connected)) {
            mutableState.value = current.copy(message = R.string.engine_setup_needed); return
        }
        if (!PlaceMonitor.permissionReady(context)) {
            mutableState.value = current.copy(message = R.string.engine_location_needed); return
        }
        if (start && !RulePolicy.scheduleActive(current.appliedRule)) {
            mutableState.value = current.copy(message = R.string.engine_outside_schedule); return
        }
        invalidateChecks()
        mutableState.value = current.copy(checkingPlace = true, monitoringPlace = monitor || current.monitoringPlace, message = R.string.engine_checking)
        val token = generation
        if (start || monitor) {
            try {
                context.startForegroundService(Intent(context, PlaceMonitor::class.java).putExtra("generation", token).putExtra("monitorOnly", !start))
            } catch (_: RuntimeException) { locationFailure(token) }
        } else {
            cancelCheck = PlaceMonitor.requestFix(context, { location -> acceptLocation(location, token, false) }, { locationFailure(token) })
        }
    }
    internal fun acceptLocation(location: Location, token: Long, start: Boolean) {
        if (generation != token) return
        val current = state.value
        val rule = current.appliedRule ?: return
        if (!current.ruleEnabled || (start && !current.checkingPlace)) return
        val now = SystemClock.elapsedRealtime()
        val age = now - location.elapsedRealtimeNanos / 1_000_000
        val distance = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, rule.latitude, rule.longitude, distance)
        val inside = RulePolicy.presence(distance[0].toDouble(), location.accuracy.toDouble(), age, current.insidePlace)
        if (!location.hasAccuracy() || inside == null) { if (current.placeSession) locationFailure(token); return }
        cancelCheck?.invoke(); cancelCheck = null
        mutableState.value = current.copy(checkingPlace = false, insidePlace = inside, observedAt = now - age,
            distanceMeters = distance[0], accuracyMeters = location.accuracy,
            message = if (inside) R.string.engine_arrived else R.string.engine_outside_place)
        if (inside && !current.insidePlace) notify(R.string.engine_arrived)
        if (!inside && current.insidePlace) notify(R.string.engine_left)
        if (!inside && current.placeSession) { finishPlaceFocus(R.string.engine_left); return }
        if (!start) return
        if (!inside || !RulePolicy.scheduleActive(rule) || !current.connected) { finishPlaceFocus(R.string.engine_not_ready); return }
        val packages = rule.packages - AppSelection.protectedPackages(context!!)
        if (packages.isEmpty()) { finishPlaceFocus(R.string.engine_setup_needed); return }
        val wallNow = ZonedDateTime.now()
        val end = RulePolicy.scheduleEnd(rule, wallNow).toInstant().toEpochMilli()
        val duration = end - wallNow.toInstant().toEpochMilli()
        endWallMillis = end
        val session = FocusSession(packages.toSet(), now, now + duration)
        mutableState.value = state.value.copy(session = session, remaining = duration, placeSession = true, message = R.string.engine_started)
        onChange?.invoke()
        notify(R.string.engine_started)
    }
    private fun finishPlaceFocus(message: Int) {
        if (!state.value.monitoringPlace) { stop(message); return }
        val wasActive = state.value.session != null
        endWallMillis = null
        mutableState.value = state.value.copy(session = null, remaining = 0, placeSession = false, checkingPlace = false, message = message)
        onChange?.invoke()
        if (wasActive) notify(R.string.engine_ended)
    }
    internal fun locationFailure(token: Long) {
        if (generation == token) stop(R.string.engine_location_lost)
    }
    fun tick() {
        val current = state.value
        val now = SystemClock.elapsedRealtime()
        if (current.placeSession || current.monitoringPlace) {
            if (::ruleStore.isInitialized && ruleStore.error.value) { stop(R.string.engine_storage_error); return }
            if (context?.let { PlaceMonitor.permissionReady(it) } != true ||
                (!current.checkingPlace && current.observedAt?.let { now - it in 0..30_000 } != true)) {
                stop(R.string.engine_location_lost); return
            }
            if (current.placeSession && (current.appliedRule?.let { RulePolicy.scheduleActive(it) } != true ||
                endWallMillis?.let { System.currentTimeMillis() >= it } == true)) {
                finishPlaceFocus(R.string.session_finished); return
            }
        }
        val session = current.session ?: return
        val remaining = session.remaining(now)
        if (remaining == 0L) {
            if (current.placeSession) finishPlaceFocus(R.string.session_finished) else stop(R.string.session_finished)
        }
        else mutableState.value = current.copy(remaining = remaining)
    }
    private fun notify(message: Int) { if (notificationsEnabled.value) context?.let { EngineNotifications.send(it, message) } }
}
