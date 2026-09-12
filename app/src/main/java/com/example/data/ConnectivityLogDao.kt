package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectivityLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ConnectivityLog)

    @Query("SELECT * FROM connectivity_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<ConnectivityLog>>

    @Query("SELECT COUNT(*) FROM connectivity_logs")
    suspend fun getCount(): Int

    @Query("DELETE FROM connectivity_logs")
    suspend fun clearLogs()
}
