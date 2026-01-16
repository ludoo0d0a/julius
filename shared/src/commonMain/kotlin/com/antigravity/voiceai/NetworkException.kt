package com.antigravity.voiceai.shared

class NetworkException(val httpCode: Int?, message: String) : Exception(message)
