package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.MokoOperatingMode
import com.example.data.SuaPreferences

/**
 * Restores Moko's voice service or posts a 1-tap resume notification after a device reboot
 * when the user had left Moko in ONLINE or SLEEP mode.
 */
class MokoBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val prefs = SuaPreferences(context)
        val savedMode = prefs.getSavedOperatingMode()
        if (savedMode == MokoOperatingMode.OFFLINE || !prefs.hasAcceptedVoicePrivacyConsent()) {
            return
        }

        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) return

        // On Android 14+ (UPSIDE_DOWN_CAKE), starting a MICROPHONE foreground service from BOOT_COMPLETED
        // is restricted by the OS; we try starting it and always fall back to a visible 1-tap restore notification.
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                SuaVoiceService.startServiceInMode(context, savedMode)
                return
            }
        } catch (_: Exception) {
        }

        postBootResumeNotification(context, prefs.getAssistantName(), savedMode)
    }

    private fun postBootResumeNotification(
        context: Context,
        assistantName: String,
        savedMode: MokoOperatingMode
    ) {
        try {
            val channelId = "moko_boot_restore_channel"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val chan = NotificationChannel(
                    channelId,
                    "$assistantName Boot Resume",
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(chan)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("auto_resume_mode", savedMode.name)
            }
            val pendingOpen = PendingIntent.getActivity(
                context,
                901,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("$assistantName Ready After Reboot")
                .setContentText("Tap to resume $assistantName ${savedMode.label} voice assistant.")
                .setAutoCancel(true)
                .setContentIntent(pendingOpen)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            nm.notify(1901, notif)
        } catch (_: Exception) {
        }
    }
}
