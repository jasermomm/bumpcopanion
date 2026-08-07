package com.jasermohamed.bumpcompanion.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jasermohamed.bumpcompanion.MainActivity
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.model.AppSettings
import com.jasermohamed.bumpcompanion.domain.model.DriveRuntimeState
import com.jasermohamed.bumpcompanion.domain.model.DriveServiceState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveNotificationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val ACTIVE_CHANNEL = "active_drive"
        const val WARNING_CHANNEL = "speed_bump_warning"
        const val ERROR_CHANNEL = "service_error"
        const val ACTIVE_NOTIFICATION_ID = 1001
        const val WARNING_NOTIFICATION_ID = 1002
        const val ERROR_NOTIFICATION_ID = 1003
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(ACTIVE_CHANNEL, context.getString(R.string.channel_active_drive), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_active_drive_description)
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(WARNING_CHANNEL, context.getString(R.string.channel_warning), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_warning_description)
                    enableVibration(true)
                },
                NotificationChannel(ERROR_CHANNEL, context.getString(R.string.channel_error), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_error_description)
                },
            )
        )
    }

    fun activeNotification(state: DriveRuntimeState, settings: AppSettings = AppSettings()): Notification {
        val title = if (state.serviceState == DriveServiceState.PAUSED) {
            context.getString(R.string.notification_drive_paused)
        } else {
            context.getString(R.string.notification_drive_title)
        }
        val contentText = state.nextBumpDistanceMetres?.let {
            context.getString(R.string.notification_next_bump, distanceText(it, settings.metricUnits))
        } ?: context.getString(R.string.notification_drive_running)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, ACTIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (state.serviceState == DriveServiceState.PAUSED) {
            builder.addAction(action(R.drawable.ic_notification, context.getString(R.string.resume_drive), DriveServiceActions.RESUME, 2))
        } else {
            builder.addAction(action(R.drawable.ic_notification, context.getString(R.string.pause_drive), DriveServiceActions.PAUSE, 1))
        }
        builder.addAction(action(R.drawable.ic_notification, context.getString(R.string.mark_bump), DriveServiceActions.MARK, 3))
        builder.addAction(action(R.drawable.ic_notification, context.getString(if (state.warningsMuted) R.string.unmute else R.string.mute), DriveServiceActions.MUTE, 4))
        builder.addAction(action(R.drawable.ic_notification, context.getString(R.string.stop_drive), DriveServiceActions.STOP, 5))
        return builder.build()
    }

    fun showWarning(distanceMetres: Float, settings: AppSettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val text = if (distanceMetres in 55f..220f) {
            if (settings.metricUnits) {
                context.getString(R.string.warning_speed_bump_distance, ((distanceMetres / 10).toInt() * 10).coerceAtLeast(10))
            } else {
                val feet = distanceMetres * 3.2808399f
                context.getString(R.string.warning_speed_bump_distance_feet, ((feet / 25).toInt() * 25).coerceAtLeast(25))
            }
        } else context.getString(R.string.warning_speed_bump_ahead)
        val notification = NotificationCompat.Builder(context, WARNING_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_warning_title))
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
        NotificationManagerCompat.from(context).notify(WARNING_NOTIFICATION_ID, notification)
    }

    fun showError(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, ERROR_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.service_error_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(ERROR_NOTIFICATION_ID, notification)
    }


    private fun distanceText(distanceMetres: Float, metric: Boolean): String = if (metric) {
        context.getString(R.string.metres_format, distanceMetres.toInt())
    } else {
        context.getString(R.string.feet_format, (distanceMetres * 3.2808399f).toInt())
    }

    private fun action(icon: Int, title: String, action: String, requestCode: Int): NotificationCompat.Action {
        val intent = Intent(context, DriveActionReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action(icon, title, pending)
    }
}
