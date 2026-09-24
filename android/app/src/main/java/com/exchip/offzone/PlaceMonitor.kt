package com.exchip.offzone

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

/** Location runs only after an explicit in-app start, with an ongoing notification and free stop. */
class PlaceMonitor : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val manager by lazy { getSystemService(LocationManager::class.java) }
    private var token = -1L
    private var awaitingStart = true
    private var checkStartedAt = 0L
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (FocusController.generation != token) return
            FocusController.acceptLocation(location, token, awaitingStart)
            if (!FocusController.state.value.checkingPlace) awaitingStart = false
        }
        override fun onProviderDisabled(provider: String) { if (!permissionReady(this@PlaceMonitor)) FocusController.locationFailure(token) }
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (token != FocusController.generation) return
            if (FocusController.state.value.checkingPlace && SystemClock.elapsedRealtime() - checkStartedAt >= 15_000) FocusController.locationFailure(token)
            FocusController.tick()
            if (!awaitingStart && !FocusController.state.value.placeSession && !FocusController.state.value.monitoringPlace) { stopSelf(); return }
            handler.postDelayed(this, 1_000)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "restore") {
            FocusController.stop(); stopSelf(); return START_NOT_STICKY
        }
        token = intent?.getLongExtra("generation", -1L) ?: -1L
        if (token != FocusController.generation || !FocusController.state.value.checkingPlace) {
            stopSelf(); return START_NOT_STICKY
        }
        try {
            startForeground(EngineNotifications.MONITOR_ID, EngineNotifications.monitor(this))
            if (!permissionReady(this)) throw SecurityException("Location unavailable")
            manager.removeUpdates(listener)
            val providers = providers(manager)
            if (providers.isEmpty()) throw IllegalStateException("No provider")
            for (provider in providers) manager.requestLocationUpdates(provider, 5_000L, 0f, listener, Looper.getMainLooper())
            checkStartedAt = SystemClock.elapsedRealtime()
            awaitingStart = intent?.getBooleanExtra("monitorOnly", false) != true
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        } catch (_: SecurityException) {
            FocusController.locationFailure(token); stopSelf()
        } catch (_: RuntimeException) { FocusController.locationFailure(token); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        manager.removeUpdates(listener)
        if (token == FocusController.generation) FocusController.locationFailure(token)
        super.onDestroy()
    }
    companion object {
        fun permissionReady(context: Context): Boolean {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
            return runCatching { providers(context.getSystemService(LocationManager::class.java)).isNotEmpty() }.getOrDefault(false)
        }
        private fun providers(manager: LocationManager): List<String> =
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { manager.isProviderEnabled(it) }

        /** Caller must cancel when its screen closes. Never accepts an old last-known location. */
        fun requestFix(context: Context, onLocation: (Location) -> Unit, onFailure: () -> Unit): () -> Unit {
            val manager = context.getSystemService(LocationManager::class.java)
            val handler = Handler(Looper.getMainLooper())
            var finished = false
            lateinit var listener: LocationListener
            val cancel = {
                finished = true
                manager.removeUpdates(listener)
                handler.removeCallbacksAndMessages(null)
            }
            listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (finished) return
                    val age = SystemClock.elapsedRealtime() - location.elapsedRealtimeNanos / 1_000_000
                    if (!location.hasAccuracy() || RulePolicy.presence(0.0, location.accuracy.toDouble(), age, false) == null) return
                    cancel()
                    onLocation(location)
                }
            }
            try {
                check(permissionReady(context))
                for (provider in providers(manager)) manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
                handler.postDelayed({ if (!finished) { cancel(); onFailure() } }, 15_000)
            } catch (_: SecurityException) {
                cancel(); handler.post(onFailure)
            } catch (_: RuntimeException) { cancel(); handler.post(onFailure) }
            return cancel
        }
    }
}
