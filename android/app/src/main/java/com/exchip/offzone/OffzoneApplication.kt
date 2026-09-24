package com.exchip.offzone

import android.app.Application

class OffzoneApplication : Application() {
    val accountStore: AccountStore by lazy { AccountStore(this) }
}
