package com.izavo.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.izavo.app.R

object FeedbackSupport {
    fun deviceMetadata(context: Context): String {
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "Unknown" }
        val model = Build.MODEL.orEmpty().ifBlank { "Unknown" }
        return "App: IZAVO\nVersion: ${packageInfo.versionName} Beta ($versionCode)\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nDevice: $manufacturer $model"
    }

    fun openEmail(context: Context, feedback: String): Boolean {
        val address = context.getString(R.string.feedback_email)
        val body = "Feedback:\n$feedback\n\n---\n${deviceMetadata(context)}"
        val uri = Uri.parse("mailto:${Uri.encode(address)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_SUBJECT, "IZAVO Beta Feedback")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
