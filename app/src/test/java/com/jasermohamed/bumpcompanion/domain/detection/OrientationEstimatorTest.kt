package com.jasermohamed.bumpcompanion.domain.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationEstimatorTest {
    @Test
    fun `identity rotation removes gravity from vertical axis`() {
        val estimator = OrientationEstimator()
        estimator.updateRotationMatrix(
            floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
            )
        )

        val result = estimator.transform(0f, 0f, 12.30665f)

        assertTrue(result.reliable)
        assertEquals(2.5f, result.vertical, 0.0001f)
        assertEquals(0f, result.lateral, 0.0001f)
    }

    @Test
    fun `gravity fallback works without orientation sensor`() {
        val estimator = OrientationEstimator()
        estimator.updateGravity(0f, 9.80665f, 0f)

        val result = estimator.transform(0f, 11.80665f, 0f)

        assertFalse(result.reliable)
        assertEquals(2f, result.vertical, 0.001f)
    }

    @Test
    fun `vertical dashboard phone still produces world vertical acceleration`() {
        val estimator = OrientationEstimator()
        estimator.updateRotationMatrix(
            floatArrayOf(
                1f, 0f, 0f,
                0f, 0f, 1f,
                0f, -1f, 0f,
            )
        )

        val result = estimator.transform(0f, -12.30665f, 0f)

        assertTrue(result.reliable)
        assertEquals(2.5f, result.vertical, 0.0001f)
    }
}
