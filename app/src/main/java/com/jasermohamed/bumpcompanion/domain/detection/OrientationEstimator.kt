package com.jasermohamed.bumpcompanion.domain.detection

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Transforms device acceleration to earth axes. Android rotation-vector matrices map device axes
 * to east/north/up; GPS bearing is intentionally applied later by the detector to obtain the true
 * vehicle longitudinal/lateral axes.
 */
class OrientationEstimator {
    private val rotationMatrix = FloatArray(9)
    private val previousRotationMatrix = FloatArray(9)
    private var hasRotationMatrix = false
    private var hasPreviousRotationMatrix = false
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 9.80665f
    private var orientationChange = 0f

    fun updateRotationMatrix(matrix: FloatArray) {
        if (matrix.size < 9) return
        if (hasRotationMatrix) {
            rotationMatrix.copyInto(previousRotationMatrix)
            hasPreviousRotationMatrix = true
        }
        matrix.copyInto(rotationMatrix, endIndex = 9)
        hasRotationMatrix = true
        if (hasPreviousRotationMatrix) {
            // trace(R_new * R_previous^T) yields an actual angular step, not a matrix norm.
            var trace = 0f
            for (row in 0 until 3) {
                var dot = 0f
                for (column in 0 until 3) {
                    dot += rotationMatrix[row * 3 + column] * previousRotationMatrix[row * 3 + column]
                }
                trace += dot
            }
            orientationChange = acos(((trace - 1f) * 0.5f).coerceIn(-1f, 1f))
        }
    }

    fun updateGravity(x: Float, y: Float, z: Float) {
        gravityX = x
        gravityY = y
        gravityZ = z
    }

    /** Retained for replay/API compatibility; rotation-matrix deltas are the reliable source. */
    fun updateOrientationVector(x: Float, y: Float, z: Float) = Unit

    data class TransformedAcceleration(
        val vertical: Float,
        val longitudinal: Float,
        val lateral: Float,
        val worldEast: Float,
        val worldNorth: Float,
        val orientationChange: Float,
        val reliable: Boolean,
    )

    fun transform(ax: Float, ay: Float, az: Float): TransformedAcceleration {
        if (hasRotationMatrix) {
            val worldEast = rotationMatrix[0] * ax + rotationMatrix[1] * ay + rotationMatrix[2] * az
            val worldNorth = rotationMatrix[3] * ax + rotationMatrix[4] * ay + rotationMatrix[5] * az
            val worldUp = rotationMatrix[6] * ax + rotationMatrix[7] * ay + rotationMatrix[8] * az
            val horizontalMagnitude = sqrt(worldEast * worldEast + worldNorth * worldNorth)
            return TransformedAcceleration(
                vertical = worldUp - 9.80665f,
                longitudinal = 0f,
                lateral = horizontalMagnitude,
                worldEast = worldEast,
                worldNorth = worldNorth,
                orientationChange = orientationChange,
                reliable = true,
            )
        }

        val gravityMagnitude = sqrt(gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ).coerceAtLeast(0.001f)
        val ux = gravityX / gravityMagnitude
        val uy = gravityY / gravityMagnitude
        val uz = gravityZ / gravityMagnitude
        val vertical = ax * ux + ay * uy + az * uz - gravityMagnitude
        val linearX = ax - gravityX
        val linearY = ay - gravityY
        val linearZ = az - gravityZ
        val horizontalSquare = (linearX * linearX + linearY * linearY + linearZ * linearZ - vertical * vertical).coerceAtLeast(0f)
        return TransformedAcceleration(
            vertical = vertical,
            longitudinal = 0f,
            lateral = sqrt(horizontalSquare),
            worldEast = Float.NaN,
            worldNorth = Float.NaN,
            orientationChange = orientationChange,
            reliable = false,
        )
    }
}
