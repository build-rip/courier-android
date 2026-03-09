package rip.build.courier.data.repository

import java.security.MessageDigest

internal fun stableId(prefix: String, value: String): Long {
    val digest = MessageDigest.getInstance("SHA-256").digest("$prefix:$value".toByteArray())
    var result = 0L
    repeat(8) { index ->
        result = (result shl 8) or (digest[index].toLong() and 0xffL)
    }
    return (result and Long.MAX_VALUE).takeIf { it != 0L } ?: 1L
}
