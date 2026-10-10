package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {
    @Test
    fun formatsSizes() {
        assertEquals("512 B", ByteFormat.human(512))
        assertEquals("2 KB", ByteFormat.human(2048))
        assertEquals("1.5 MB", ByteFormat.human(1_572_864))
        assertEquals("1.00 GB", ByteFormat.human(1_073_741_824))
        assertEquals("", ByteFormat.speed(0))
        assertEquals("2.0 MB/s", ByteFormat.speed(2_097_152))
    }
}
