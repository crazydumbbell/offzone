package com.exchip.offzone

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView

class FocusAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var timeLabel: TextView? = null
    private var foreground: String? = null
    private var receiverRegistered = false
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Never leave an overlay over the lock screen; wait for the next app event on unlock.
            foreground = null
            hideOverlay()
            FocusController.tick()
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            FocusController.tick()
            reconcile()
            if (FocusController.state.value.session != null) handler.postDelayed(this, 500)
        }
    }

    override fun onServiceConnected() {
        if (!AppSelection.preferences(this).getBoolean("disclosure", false)) {
            disableSelf()
            return
        }
        FocusController.initialize(this)
        FocusController.connect()
        FocusController.onChange = {
            handler.removeCallbacks(ticker)
            reconcile()
            if (FocusController.state.value.session != null) handler.post(ticker)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, filter)
        receiverRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        FocusController.tick()
        val packageName = event.packageName?.toString() ?: return
        // Overlay events use our package too. Only the actual activity means the user opened Offzone.
        if (packageName == this.packageName && event.className?.toString() != MainActivity::class.java.name) return
        foreground = packageName
        reconcile()
    }

    private fun reconcile() {
        val state = FocusController.state.value
        val packageName = foreground
        val unlocked = !getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
            getSystemService(PowerManager::class.java).isInteractive
        val block = unlocked && packageName != null && state.session?.blocks(
            packageName, SystemClock.elapsedRealtime(), AppSelection.protectedPackages(this)
        ) == true
        if (!block) { hideOverlay(); return }
        if (overlay == null) showOverlay()
        val seconds = (state.remaining + 999) / 1000
        timeLabel?.text = getString(R.string.remaining, "%d:%02d".format(seconds / 60, seconds % 60))
    }

    private fun showOverlay() {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(BUTTER)
        }
        fun label(text: String, size: Float) = TextView(this).apply {
            this.text = text
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(INK)
            typeface = resources.getFont(R.font.suit_regular)
            setPadding(0, pad / 2, 0, pad / 2)
            layout.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        label("offzone", 18f)
        label(getString(R.string.blocked_title), 30f).setTypeface(resources.getFont(R.font.suit_bold), Typeface.NORMAL)
        label(getString(R.string.blocked_body), 17f)
        timeLabel = label("", 22f)
        fun button(text: Int, primary: Boolean, action: () -> Unit) {
            val density = resources.displayMetrics.density
            val shape = GradientDrawable().apply {
                cornerRadius = 28 * density
                setColor(if (primary) PINE else IVORY)
                if (!primary) setStroke((1 * density).toInt(), PINE_LINE)
            }
            layout.addView(Button(this).apply {
                setText(text)
                isAllCaps = false
                textSize = 17f
                typeface = resources.getFont(R.font.suit_medium)
                setTextColor(if (primary) BUTTER else INK)
                stateListAnimator = null
                background = RippleDrawable(ColorStateList.valueOf(if (primary) Color.argb(60, 247, 240, 199) else Color.argb(40, 61, 91, 63)), shape, null)
                minHeight = (56 * density).toInt()
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() })
        }
        button(R.string.go_home, primary = true) {
            // Keep the shield if Android rejects navigation; free restore remains available.
            if (performGlobalAction(GLOBAL_ACTION_HOME)) { foreground = null; hideOverlay() }
        }
        button(R.string.restore, primary = false) { FocusController.stop() }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BUTTER)
            addView(layout, android.widget.FrameLayout.LayoutParams(-1, -2))
        }
        try {
            windows.addView(scroll, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.OPAQUE,
            ))
            overlay = scroll
        } catch (_: RuntimeException) {
            timeLabel = null
            FocusController.stop(R.string.blocking_error)
        }
    }

    private fun hideOverlay() {
        overlay?.let { view -> runCatching { windows.removeViewImmediate(view) } }
        overlay = null
        timeLabel = null
    }

    private companion object {
        // Same palette as OffzoneTheme.kt; this overlay is plain Views, outside Compose.
        val BUTTER = Color.rgb(247, 240, 199)
        val IVORY = Color.rgb(250, 247, 232)
        val PINE = Color.rgb(61, 91, 63)
        val PINE_LINE = Color.argb(71, 61, 91, 63)
        val INK = Color.rgb(31, 42, 34)
    }

    override fun onInterrupt() { FocusController.stop(R.string.service_stopped) }
    override fun onUnbind(intent: Intent?): Boolean { cleanUp(); return super.onUnbind(intent) }
    override fun onDestroy() { cleanUp(); super.onDestroy() }
    private fun cleanUp() {
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        foreground = null
        if (receiverRegistered) { unregisterReceiver(screenReceiver); receiverRegistered = false }
        FocusController.disconnect()
    }
}
