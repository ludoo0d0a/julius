package fr.geoking.julius.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

fun Context.openExternalUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
