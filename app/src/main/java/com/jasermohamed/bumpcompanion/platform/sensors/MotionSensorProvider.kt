package com.jasermohamed.bumpcompanion.platform.sensors

import android.content.Context
import android.hardware.*
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.detection.OrientationEstimator
import com.jasermohamed.bumpcompanion.domain.model.MotionSample
import com.jasermohamed.bumpcompanion.domain.model.SensorCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

interface MotionSensorProvider {
    val capabilities: SensorCapabilities
    fun start(listener: Listener): Boolean
    fun stop()

    interface Listener {
        fun onMotionSample(sample: MotionSample, stabilityScore: Float)
        fun onSensorError(message: String)
    }
}

@Singleton
class AndroidMotionSensorProvider @Inject constructor(
    @ApplicationContext context: Context,
) : MotionSensorProvider, SensorEventListener {
    private val appContext = context
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val orientationEstimator = OrientationEstimator()
    private var handlerThread: HandlerThread? = null
    private var listener: MotionSensorProvider.Listener? = null
    private var latestGyroX = 0f
    private var latestGyroY = 0f
    private var latestGyroZ = 0f
    private var latestLinearX = Float.NaN
    private var latestLinearY = Float.NaN
    private var latestLinearZ = Float.NaN
    private var latestGravityX = Float.NaN
    private var latestGravityY = Float.NaN
    private var latestGravityZ = Float.NaN
    private var latestRotationX = Float.NaN
    private var latestRotationY = Float.NaN
    private var latestRotationZ = Float.NaN
    private var latestRotationW = Float.NaN
    private val rotationMatrix = FloatArray(9)
    private var stability = 1f
    private var lastAccelTimestamp = 0L

    override val capabilities: SensorCapabilities = SensorCapabilities(
        hasAccelerometer = accelerometer != null,
        hasGyroscope = gyroscope != null,
        hasGravity = gravitySensor != null,
        hasLinearAcceleration = linearAcceleration != null,
        hasRotationVector = rotationVector?.type == Sensor.TYPE_ROTATION_VECTOR,
        hasGameRotationVector = rotationVector?.type == Sensor.TYPE_GAME_ROTATION_VECTOR,
        accelerometerMinDelayMicros = accelerometer?.minDelay ?: 0,
        accelerometerFifoMax = accelerometer?.fifoMaxEventCount ?: 0,
    )

    override fun start(listener: MotionSensorProvider.Listener): Boolean {
        stop()
        val accel = accelerometer ?: return false
        this.listener = listener
        val thread = HandlerThread("BumpMotionSensors").also { it.start() }
        handlerThread = thread
        val handler = Handler(thread.looper)
        var success = sensorManager.registerListener(this, accel, 10_000, 100_000, handler)
        gyroscope?.let { success = sensorManager.registerListener(this, it, 20_000, 100_000, handler) && success }
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        linearAcceleration?.let { sensorManager.registerListener(this, it, 10_000, 100_000, handler) }
        if (!success) listener.onSensorError(appContext.getString(R.string.sensor_registration_failed))
        return success
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        listener = null
        handlerThread?.quitSafely()
        handlerThread = null
        latestGyroX = 0f
        latestGyroY = 0f
        latestGyroZ = 0f
        latestLinearX = Float.NaN
        latestLinearY = Float.NaN
        latestLinearZ = Float.NaN
        latestGravityX = Float.NaN
        latestGravityY = Float.NaN
        latestGravityZ = Float.NaN
        latestRotationX = Float.NaN
        latestRotationY = Float.NaN
        latestRotationZ = Float.NaN
        latestRotationW = Float.NaN
        stability = 1f
        lastAccelTimestamp = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                latestGyroX = event.values[0]
                latestGyroY = event.values[1]
                latestGyroZ = event.values[2]
                val angularSpeed = sqrt(latestGyroX * latestGyroX + latestGyroY * latestGyroY + latestGyroZ * latestGyroZ)
                stability = (stability * 0.96f + (1f - (angularSpeed / 3.5f).coerceIn(0f, 1f)) * 0.04f).coerceIn(0f, 1f)
            }
            Sensor.TYPE_GRAVITY -> {
                latestGravityX = event.values[0]
                latestGravityY = event.values[1]
                latestGravityZ = event.values[2]
                orientationEstimator.updateGravity(latestGravityX, latestGravityY, latestGravityZ)
            }
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                latestRotationX = event.values.getOrElse(0) { Float.NaN }
                latestRotationY = event.values.getOrElse(1) { Float.NaN }
                latestRotationZ = event.values.getOrElse(2) { Float.NaN }
                latestRotationW = event.values.getOrElse(3) { Float.NaN }
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                orientationEstimator.updateRotationMatrix(rotationMatrix)
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                latestLinearX = event.values[0]
                latestLinearY = event.values[1]
                latestLinearZ = event.values[2]
            }
            Sensor.TYPE_ACCELEROMETER -> emitAccelerometer(event)
        }
    }

    private fun emitAccelerometer(event: SensorEvent) {
        if (lastAccelTimestamp > 0L) {
            val gapMillis = (event.timestamp - lastAccelTimestamp) / 1_000_000L
            if (gapMillis > 100L) stability = (stability - 0.05f).coerceAtLeast(0f)
        }
        lastAccelTimestamp = event.timestamp
        val transformed = orientationEstimator.transform(event.values[0], event.values[1], event.values[2])
        val epoch = System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000L
        listener?.onMotionSample(
            MotionSample(
                elapsedRealtimeNanos = event.timestamp,
                epochMillis = epoch,
                accelerationX = event.values[0],
                accelerationY = event.values[1],
                accelerationZ = event.values[2],
                gyroscopeX = latestGyroX,
                gyroscopeY = latestGyroY,
                gyroscopeZ = latestGyroZ,
                verticalAcceleration = transformed.vertical,
                longitudinalAcceleration = transformed.longitudinal,
                lateralAcceleration = transformed.lateral,
                orientationChangeRadians = transformed.orientationChange,
                orientationReliable = transformed.reliable,
                worldAccelerationEast = transformed.worldEast,
                worldAccelerationNorth = transformed.worldNorth,
                linearAccelerationAvailable = latestLinearX.isFinite(),
                linearAccelerationX = latestLinearX,
                linearAccelerationY = latestLinearY,
                linearAccelerationZ = latestLinearZ,
                gravityX = latestGravityX,
                gravityY = latestGravityY,
                gravityZ = latestGravityZ,
                rotationVectorX = latestRotationX,
                rotationVectorY = latestRotationY,
                rotationVectorZ = latestRotationZ,
                rotationVectorW = latestRotationW,
            ),
            stability,
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
