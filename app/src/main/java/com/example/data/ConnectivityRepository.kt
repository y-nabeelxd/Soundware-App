package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ConnectivityRepository(context: Context) {
    private val dao = AppDatabase.getDatabase(context).connectivityLogDao()
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    val recentLogs: Flow<List<ConnectivityLog>> = dao.getRecentLogs()

    fun logStatus(status: String, networkType: String, details: String = "") {
        repositoryScope.launch {
            try {
                dao.insert(
                    ConnectivityLog(
                        status = status,
                        networkType = networkType,
                        details = details
                    )
                )
            } catch (e: Exception) {
                // Ignore DB insertion errors silently in production
            }
        }
    }
}
