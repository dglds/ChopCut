package com.chopcut.ui.components.console

import android.content.Context
import android.os.Environment
import com.chopcut.util.debug.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class LogSession(
    val id: String,
    val name: String,
    val createdAt: Long,
    val logs: List<LogEntry>,
    val metadata: SessionMetadata
)

data class SessionMetadata(
    val appVersion: String,
    val deviceModel: String,
    val androidVersion: String,
    val logCount: Int,
    val tags: Set<String>
)

class SessionPersistence(
    private val context: Context
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    private val sessionsDir: File by lazy {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "LogSessions")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }
    
    suspend fun saveSession(
        name: String,
        logs: List<LogEntry>
    ): Result<LogSession> = withContext(Dispatchers.IO) {
        try {
            val sessionId = UUID.randomUUID().toString()
            val createdAt = System.currentTimeMillis()
            
            val metadata = SessionMetadata(
                appVersion = getAppVersion(),
                deviceModel = android.os.Build.MODEL,
                androidVersion = "Android ${android.os.Build.VERSION.RELEASE}",
                logCount = logs.size,
                tags = logs.map { it.tag }.toSet()
            )
            
            val session = LogSession(
                id = sessionId,
                name = name,
                createdAt = createdAt,
                logs = logs,
                metadata = metadata
            )
            
            val sessionFile = File(sessionsDir, "$sessionId.json")
            saveSessionToFile(session, sessionFile)
            
            val manifestFile = File(sessionsDir, "manifest.json")
            updateManifest(manifestFile, session)
            
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun loadSession(sessionId: String): Result<LogSession> = withContext(Dispatchers.IO) {
        try {
            val sessionFile = File(sessionsDir, "$sessionId.json")
            if (!sessionFile.exists()) {
                return@withContext Result.failure(Exception("Session not found"))
            }
            
            val session = loadSessionFromFile(sessionFile)
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun listSessions(): Result<List<SessionInfo>> = withContext(Dispatchers.IO) {
        try {
            val manifestFile = File(sessionsDir, "manifest.json")
            if (!manifestFile.exists()) {
                return@withContext Result.success(emptyList())
            }
            
            val manifestContent = manifestFile.readText()
            val manifest = JSONObject(manifestContent)
            val sessionsArray = manifest.getJSONArray("sessions")
            
            val sessions = mutableListOf<SessionInfo>()
            for (i in 0 until sessionsArray.length()) {
                val sessionObj = sessionsArray.getJSONObject(i)
                sessions.add(
                    SessionInfo(
                        id = sessionObj.getString("id"),
                        name = sessionObj.getString("name"),
                        createdAt = sessionObj.getLong("createdAt"),
                        logCount = sessionObj.getInt("logCount"),
                        tags = sessionObj.getJSONArray("tags").let { tagsArray ->
                            (0 until tagsArray.length()).map { tagsArray.getString(it) }.toSet()
                        }
                    )
                )
            }
            
            Result.success(sessions.sortedByDescending { it.createdAt })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sessionFile = File(sessionsDir, "$sessionId.json")
            if (sessionFile.exists()) {
                sessionFile.delete()
            }
            
            val manifestFile = File(sessionsDir, "manifest.json")
            removeFromManifest(manifestFile, sessionId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun clearAllSessions(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            sessionsDir.listFiles()?.forEach { file ->
                file.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun saveSessionToFile(session: LogSession, file: File) {
        val json = JSONObject().apply {
            put("id", session.id)
            put("name", session.name)
            put("createdAt", session.createdAt)
            put("metadata", JSONObject().apply {
                put("appVersion", session.metadata.appVersion)
                put("deviceModel", session.metadata.deviceModel)
                put("androidVersion", session.metadata.androidVersion)
                put("logCount", session.metadata.logCount)
                put("tags", JSONArray(session.metadata.tags))
            })
            put("logs", JSONArray().apply {
                session.logs.forEach { entry ->
                    put(JSONObject().apply {
                        put("timestamp", entry.timestamp)
                        put("level", entry.level.toString())
                        put("tag", entry.tag)
                        put("message", entry.message)
                        put("count", entry.count)
                    })
                }
            })
        }
        
        FileOutputStream(file).use { output ->
            output.write(json.toString(2).toByteArray())
        }
    }
    
    private fun loadSessionFromFile(file: File): LogSession {
        val json = JSONObject(file.readText())
        val logsArray = json.getJSONArray("logs")
        val logs = mutableListOf<LogEntry>()
        
        for (i in 0 until logsArray.length()) {
            val entryObj = logsArray.getJSONObject(i)
            logs.add(
                LogEntry(
                    level = com.chopcut.util.debug.LogLevel.valueOf(entryObj.getString("level")),
                    tag = entryObj.getString("tag"),
                    message = entryObj.getString("message"),
                    count = entryObj.getInt("count"),
                    timestamp = entryObj.getLong("timestamp")
                )
            )
        }
        
        val metadataObj = json.getJSONObject("metadata")
        val tags = mutableSetOf<String>()
        val tagsArray = metadataObj.getJSONArray("tags")
        for (i in 0 until tagsArray.length()) {
            tags.add(tagsArray.getString(i))
        }
        
        return LogSession(
            id = json.getString("id"),
            name = json.getString("name"),
            createdAt = json.getLong("createdAt"),
            logs = logs,
            metadata = SessionMetadata(
                appVersion = metadataObj.getString("appVersion"),
                deviceModel = metadataObj.getString("deviceModel"),
                androidVersion = metadataObj.getString("androidVersion"),
                logCount = metadataObj.getInt("logCount"),
                tags = tags
            )
        )
    }
    
    private fun updateManifest(manifestFile: File, session: LogSession) {
        val manifest = if (manifestFile.exists()) {
            JSONObject(manifestFile.readText())
        } else {
            JSONObject()
        }
        
        val sessionsArray = manifest.optJSONArray("sessions") ?: JSONArray()
        
        val sessionInfo = JSONObject().apply {
            put("id", session.id)
            put("name", session.name)
            put("createdAt", session.createdAt)
            put("logCount", session.metadata.logCount)
            put("tags", JSONArray(session.metadata.tags))
        }
        
        val newSessionsArray = JSONArray()
        var found = false
        for (i in 0 until sessionsArray.length()) {
            val existingSession = sessionsArray.getJSONObject(i)
            if (existingSession.getString("id") == session.id) {
                newSessionsArray.put(sessionInfo)
                found = true
            } else {
                newSessionsArray.put(existingSession)
            }
        }
        
        if (!found) {
            newSessionsArray.put(sessionInfo)
        }
        
        manifest.put("sessions", newSessionsArray)
        
        FileOutputStream(manifestFile).use { output ->
            output.write(manifest.toString(2).toByteArray())
        }
    }
    
    private fun removeFromManifest(manifestFile: File, sessionId: String) {
        if (!manifestFile.exists()) return
        
        val manifest = JSONObject(manifestFile.readText())
        val sessionsArray = manifest.optJSONArray("sessions") ?: JSONArray()
        
        val newSessionsArray = JSONArray()
        for (i in 0 until sessionsArray.length()) {
            val sessionObj = sessionsArray.getJSONObject(i)
            if (sessionObj.getString("id") != sessionId) {
                newSessionsArray.put(sessionObj)
            }
        }
        
        manifest.put("sessions", newSessionsArray)
        
        FileOutputStream(manifestFile).use { output ->
            output.write(manifest.toString(2).toByteArray())
        }
    }
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }
}

data class SessionInfo(
    val id: String,
    val name: String,
    val createdAt: Long,
    val logCount: Int,
    val tags: Set<String>
) {
    fun formattedDate(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(createdAt))
    }
    
    fun formattedTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(createdAt))
    }
}