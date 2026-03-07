package rip.build.courier.domain.model

import android.net.Uri

data class ContactInfo(
    val displayName: String?,
    val photoUri: Uri?,
    val initials: String?
)
