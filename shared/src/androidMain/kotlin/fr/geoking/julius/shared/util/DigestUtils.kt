package fr.geoking.julius.shared.util

import java.security.MessageDigest

actual fun md5(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray())
    return digest.joinToString("") {
        "%02x".format(it)
    }
}
