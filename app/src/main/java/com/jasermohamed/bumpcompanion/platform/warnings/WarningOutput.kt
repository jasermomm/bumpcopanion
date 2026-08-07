package com.jasermohamed.bumpcompanion.platform.warnings

import android.content.Context
import android.media.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.content.getSystemService
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.approach.WarningPhase
import com.jasermohamed.bumpcompanion.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

interface WarningOutput {
    fun warnSpeedBump(distanceMetres: Float, phase: WarningPhase, settings: AppSettings)
    fun warnMultiple(settings: AppSettings)
    fun announce(text: String, settings: AppSettings, utteranceId: String)
    fun release()
}

class AndroidWarningOutput @Inject constructor(
    @ApplicationContext private val context: Context,
) : WarningOutput, TextToSpeech.OnInitListener {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val tts = TextToSpeech(context, this)
    private var ttsReady = false
    private val pendingSpeech = ConcurrentLinkedQueue<Pair<String, String>>()
    private var toneGenerator: ToneGenerator? = null

    private val focusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
    } else null

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            val result = tts.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.UK)
            }
            while (pendingSpeech.isNotEmpty()) {
                pendingSpeech.poll()?.let { speakNow(it.first, it.second) }
            }
        }
    }

    override fun warnSpeedBump(distanceMetres: Float, phase: WarningPhase, settings: AppSettings) {
        if (!settings.warningsEnabled) return
        val text = if (phase == WarningPhase.MAIN && distanceMetres in 55f..220f) {
            if (settings.metricUnits) {
                val roundedMetres = ((distanceMetres / 10f).toInt() * 10).coerceAtLeast(10)
                context.getString(R.string.warning_speed_bump_distance, roundedMetres)
            } else {
                val feet = distanceMetres * 3.2808399f
                val roundedFeet = ((feet / 25f).toInt() * 25).coerceAtLeast(25)
                context.getString(R.string.warning_speed_bump_distance_feet, roundedFeet)
            }
        } else {
            context.getString(R.string.warning_speed_bump_ahead)
        }
        deliver(text, settings, "bump-${System.currentTimeMillis()}")
    }

    override fun warnMultiple(settings: AppSettings) {
        if (settings.warningsEnabled) deliver(context.getString(R.string.warning_multiple_bumps), settings, "multiple-${System.currentTimeMillis()}")
    }

    override fun announce(text: String, settings: AppSettings, utteranceId: String) = deliver(text, settings, utteranceId)

    private fun deliver(text: String, settings: AppSettings, utteranceId: String) {
        if (settings.voiceEnabled) {
            requestAudioFocus()
            if (ttsReady) speakNow(text, utteranceId) else pendingSpeech.offer(text to utteranceId)
        } else if (settings.toneEnabled) {
            toneGenerator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 72)
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        }
        if (settings.vibrationEnabled) vibrate()
    }

    private fun speakNow(text: String, utteranceId: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::requestAudioFocus)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 180), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 120, 80, 180), -1)
        }
    }

    override fun release() {
        tts.stop()
        tts.shutdown()
        toneGenerator?.release()
        toneGenerator = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let(audioManager::abandonAudioFocusRequest)
    }
}
