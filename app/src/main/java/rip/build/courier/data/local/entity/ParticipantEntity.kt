package rip.build.courier.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "participants")
data class ParticipantEntity(
    @PrimaryKey val rowID: Long,
    val chatRowID: Long,
    val id: String,
    val service: String?
)
