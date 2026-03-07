package rip.build.courier.domain.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.PhoneLookup
import rip.build.courier.domain.model.ContactInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val EMPTY = ContactInfo(null, null, null)
    private val cache = ConcurrentHashMap<String, ContactInfo>()

    fun resolve(participantId: String): ContactInfo? {
        val cached = cache[participantId]
        if (cached != null) return cached.takeIf { it !== EMPTY }
        val resolved = if (participantId.contains("@")) {
            resolveEmail(participantId)
        } else {
            resolvePhone(participantId)
        }
        cache[participantId] = resolved ?: EMPTY
        return resolved
    }

    fun clearCache() {
        cache.clear()
    }

    private fun resolvePhone(phone: String): ContactInfo? {
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        val projection = arrayOf(PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_URI)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(PhoneLookup.DISPLAY_NAME))
                    val photoUriStr = cursor.getString(cursor.getColumnIndexOrThrow(PhoneLookup.PHOTO_URI))
                    ContactInfo(
                        displayName = name,
                        photoUri = photoUriStr?.let { Uri.parse(it) },
                        initials = initialsFrom(name)
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveEmail(email: String): ContactInfo? {
        val projection = arrayOf(
            Email.DISPLAY_NAME,
            Email.PHOTO_URI
        )
        val selection = "${Email.ADDRESS} = ?"
        val selectionArgs = arrayOf(email)
        return try {
            context.contentResolver.query(
                Email.CONTENT_URI, projection, selection, selectionArgs, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(Email.DISPLAY_NAME))
                    val photoUriStr = cursor.getString(cursor.getColumnIndexOrThrow(Email.PHOTO_URI))
                    ContactInfo(
                        displayName = name,
                        photoUri = photoUriStr?.let { Uri.parse(it) },
                        initials = initialsFrom(name)
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun initialsFrom(name: String?): String? {
            if (name.isNullOrBlank()) return null
            val parts = name.trim().split("\\s+".toRegex())
            return when {
                parts.size >= 2 -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
                parts.size == 1 -> "${parts.first().first().uppercaseChar()}"
                else -> null
            }
        }
    }
}
