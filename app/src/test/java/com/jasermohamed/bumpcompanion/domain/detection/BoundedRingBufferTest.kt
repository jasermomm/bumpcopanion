package com.jasermohamed.bumpcompanion.domain.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedRingBufferTest {
    @Test
    fun `oldest element is discarded at capacity`() {
        val buffer = BoundedRingBuffer<Int>(3)
        buffer.add(1)
        buffer.add(2)
        buffer.add(3)
        buffer.add(4)

        assertEquals(listOf(2, 3, 4), buffer.toList())
        assertEquals(3, buffer.size)
    }
}
