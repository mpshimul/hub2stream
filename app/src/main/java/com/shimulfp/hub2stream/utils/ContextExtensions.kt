package com.shimulfp.hub2stream.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

// Helper function to detect if device is TV
fun Context.isTv(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}