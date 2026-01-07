package com.antigravity.voiceai.shared

import platform.Foundation.NSDate

actual fun getCurrentTimeMillis(): Long {
    val date = NSDate()
    return (date.timeIntervalSince1970 * 1000.0).toLong()
}

