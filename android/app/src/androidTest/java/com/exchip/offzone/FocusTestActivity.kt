package com.exchip.offzone

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Installed only with the test APK; gives the shield a harmless real foreground target. */
class FocusTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Focus test app"; textSize = 30f })
    }
}
