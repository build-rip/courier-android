package rip.build.courier.data.repository

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StableIdsTest {
    @Test
    fun stableId_matchesUnsignedByteReferenceImplementation() {
        assertEquals(referenceStableId("message", "alpha"), stableId("message", "alpha"))
        assertEquals(referenceStableId("message", "beta"), stableId("message", "beta"))
        assertEquals(referenceStableId("conversation", "any;-;+19184187983"), stableId("conversation", "any;-;+19184187983"))
    }

    @Test
    fun stableId_distinguishes_inputsThatPreviouslyCollapsed() {
        val first = stableId("message", "6973F614-A597-4C7F-AB61-BFF6E751EB04")
        val second = stableId("message", "4F1E85CF-82FB-4377-A971-1DCE3808A877")

        assertNotEquals(first, second)
    }

    private fun referenceStableId(prefix: String, value: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest("$prefix:$value".toByteArray())
        var result = 0L
        repeat(8) { index ->
            result = (result shl 8) or (digest[index].toLong() and 0xffL)
        }
        return (result and Long.MAX_VALUE).takeIf { it != 0L } ?: 1L
    }
}
