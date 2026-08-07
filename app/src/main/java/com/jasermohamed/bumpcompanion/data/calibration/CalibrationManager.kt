package com.jasermohamed.bumpcompanion.data.calibration

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.data.local.CalibrationDao
import com.jasermohamed.bumpcompanion.data.local.CalibrationProfileEntity
import com.jasermohamed.bumpcompanion.domain.model.CalibrationProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.sqrt

interface CalibrationManager {
    val latest: Flow<CalibrationProfile?>
    suspend fun calibrate(durationMillis: Long = 5_000L): Result<CalibrationProfile>
    suspend fun reset()
}

@Singleton
class AndroidCalibrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CalibrationDao,
) : CalibrationManager {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override val latest: Flow<CalibrationProfile?> = dao.observeLatest().map { it?.toDomain() }

    override suspend fun calibrate(durationMillis: Long): Result<CalibrationProfile> = runCatching {
        val profile = collect(durationMillis).getOrThrow()
        dao.upsert(profile.toEntity())
        profile
    }

    override suspend fun reset() = dao.deleteAll()

    private suspend fun collect(durationMillis: Long): Result<CalibrationProfile> = suspendCancellableCoroutine { continuation ->
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            continuation.resume(Result.failure(IllegalStateException(context.getString(R.string.accelerometer_unavailable))))
            return@suspendCancellableCoroutine
        }
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val thread = HandlerThread("BumpCalibration").also { it.start() }
        val handler = Handler(thread.looper)
        val accelerations = ArrayList<FloatArray>(600)
        val gyroMagnitudes = ArrayList<Float>(300)
        val timestamps = ArrayList<Long>(600)
        var finished = false

        lateinit var listener: SensorEventListener
        fun finish(result: Result<CalibrationProfile>) {
            if (finished) return
            finished = true
            sensorManager.unregisterListener(listener)
            thread.quitSafely()
            if (continuation.isActive) continuation.resume(result)
        }

        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        accelerations += event.values.copyOf(3)
                        timestamps += event.timestamp
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        gyroMagnitudes += sqrt(x * x + y * y + z * z)
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        continuation.invokeOnCancellation {
            sensorManager.unregisterListener(listener)
            thread.quitSafely()
        }

        val accelerationRegistered = sensorManager.registerListener(listener, accelerometer, 20_000, handler)
        if (!accelerationRegistered) {
            finish(Result.failure(IllegalStateException(context.getString(R.string.calibration_failed))))
            return@suspendCancellableCoroutine
        }
        gyroscope?.let { sensorManager.registerListener(listener, it, 20_000, handler) }
        handler.postDelayed({
            if (accelerations.size < 80) {
                finish(Result.failure(IllegalStateException(context.getString(R.string.calibration_failed))))
                return@postDelayed
            }
            val gravityX = accelerations.map { it[0].toDouble() }.average().toFloat()
            val gravityY = accelerations.map { it[1].toDouble() }.average().toFloat()
            val gravityZ = accelerations.map { it[2].toDouble() }.average().toFloat()
            val accelerationNoise = accelerations.sumOf { sample ->
                val dx = sample[0] - gravityX
                val dy = sample[1] - gravityY
                val dz = sample[2] - gravityZ
                (dx * dx + dy * dy + dz * dz).toDouble()
            }.let { sqrt(it / accelerations.size).toFloat() }
            val gyroNoise = if (gyroMagnitudes.isEmpty()) 0f else {
                sqrt(gyroMagnitudes.sumOf { (it * it).toDouble() } / gyroMagnitudes.size).toFloat()
            }
            val intervals = timestamps.zipWithNext { first, second -> (second - first) / 1_000_000.0 }
            val intervalMean = intervals.average().takeIf { it.isFinite() && it > 0.0 } ?: 20.0
            val intervalDeviation = if (intervals.isEmpty()) intervalMean else {
                sqrt(intervals.sumOf { (it - intervalMean) * (it - intervalMean) } / intervals.size)
            }
            val consistency = (1.0 - intervalDeviation / intervalMean).coerceIn(0.0, 1.0).toFloat()
            finish(
                Result.success(
                    CalibrationProfile(
                        id = UUID.randomUUID().toString(),
                        accelerometerNoiseRms = accelerationNoise,
                        gyroscopeNoiseRms = gyroNoise,
                        gravityX = gravityX,
                        gravityY = gravityY,
                        gravityZ = gravityZ,
                        samplingConsistency = consistency,
                        engineVibrationRms = accelerationNoise,
                        sampleCount = accelerations.size,
                    )
                )
            )
        }, durationMillis.coerceIn(2_000L, 15_000L))
    }
}

private fun CalibrationProfileEntity.toDomain() = CalibrationProfile(
    id = id,
    createdAt = createdAt,
    accelerometerNoiseRms = accelerometerNoiseRms,
    gyroscopeNoiseRms = gyroscopeNoiseRms,
    gravityX = gravityX,
    gravityY = gravityY,
    gravityZ = gravityZ,
    samplingConsistency = samplingConsistency,
    engineVibrationRms = engineVibrationRms,
    sampleCount = sampleCount,
    algorithmVersion = algorithmVersion,
)

private fun CalibrationProfile.toEntity() = CalibrationProfileEntity(
    id = id,
    createdAt = createdAt,
    accelerometerNoiseRms = accelerometerNoiseRms,
    gyroscopeNoiseRms = gyroscopeNoiseRms,
    gravityX = gravityX,
    gravityY = gravityY,
    gravityZ = gravityZ,
    samplingConsistency = samplingConsistency,
    engineVibrationRms = engineVibrationRms,
    sampleCount = sampleCount,
    algorithmVersion = algorithmVersion,
)
