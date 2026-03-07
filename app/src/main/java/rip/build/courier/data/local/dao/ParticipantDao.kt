package rip.build.courier.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import rip.build.courier.data.local.entity.ParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM participants WHERE chatRowID = :chatRowID")
    fun observeByChatId(chatRowID: Long): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM participants WHERE chatRowID = :chatRowID")
    suspend fun getByChatId(chatRowID: Long): List<ParticipantEntity>

    @Upsert
    suspend fun upsertAll(participants: List<ParticipantEntity>)

    @Query("DELETE FROM participants WHERE chatRowID = :chatRowID")
    suspend fun deleteByChatId(chatRowID: Long)
}
