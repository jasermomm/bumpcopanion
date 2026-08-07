package com.jasermohamed.bumpcompanion.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface DriveServiceController {
    fun start()
    fun pause()
    fun resume()
    fun markBump()
    fun toggleMute()
    fun stop()
}

@Singleton
class AndroidDriveServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : DriveServiceController {
    override fun start() = send(DriveServiceActions.START, foreground = true)
    override fun pause() = send(DriveServiceActions.PAUSE)
    override fun resume() = send(DriveServiceActions.RESUME, foreground = true)
    override fun markBump() = send(DriveServiceActions.MARK)
    override fun toggleMute() = send(DriveServiceActions.MUTE)
    override fun stop() = send(DriveServiceActions.STOP)

    private fun send(action: String, foreground: Boolean = false) {
        val intent = Intent(context, DriveDetectionService::class.java).setAction(action)
        if (foreground) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
    }
}
