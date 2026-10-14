package com.example.data.util

import android.util.Base64
import android.util.Log
import com.example.data.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class Program(
    val title: String,
    val progress: Float = 0.0f,
    val startTime: String = "",
    val endTime: String = "",
    val description: String = ""
)

object EPGGenerator {

    private const val TAG = "EPGGenerator"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val cache = ConcurrentHashMap<Int, List<Program>>()

    val fallbackProgram = Program(
        title = "Programação Indisponível",
        progress = 0.0f,
        startTime = "",
        endTime = "",
        description = "Nenhuma informação de programação disponível no momento."
    )

    val fallbackList = listOf(fallbackProgram)

    suspend fun generateEPG(
        username: String,
        password: String,
        streamId: Int,
        serverUrl: String = "http://streatv.elementfx.com"
    ): List<Program> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank() || streamId <= 0) {
            return@withContext fallbackList
        }

        // Return cached EPG if available
        cache[streamId]?.let { return@withContext it }

        val baseUrl = cleanServerUrl(serverUrl)
        val url = "$baseUrl/player_api.php?username=$username&password=$password&action=get_short_epg&stream_id=$streamId"

        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP request failed with code ${response.code} for streamId $streamId")
                return@withContext fallbackList
            }

            val bodyStr = response.body?.string() ?: ""
            if (bodyStr.isBlank()) {
                return@withContext fallbackList
            }

            val jsonObject = JSONObject(bodyStr)
            val epgListings = jsonObject.optJSONArray("epg_listings")
            if (epgListings == null || epgListings.length() == 0) {
                return@withContext fallbackList
            }

            val programs = mutableListOf<Program>()
            val nowMs = System.currentTimeMillis()
            val seenKeys = mutableSetOf<String>()

            for (i in 0 until epgListings.length()) {
                val itemObj = epgListings.getJSONObject(i)

                val rawTitle = itemObj.optString("title")
                val rawDesc = itemObj.optString("description")

                val title = decodeBase64(rawTitle).ifBlank { "Sem Título" }
                val description = decodeBase64(rawDesc).ifBlank { "Sem descrição disponível." }

                val startSec = itemObj.optString("start_timestamp").toLongOrNull()
                    ?: itemObj.optLong("start_timestamp", 0L)
                val stopSec = itemObj.optString("stop_timestamp").toLongOrNull()
                    ?: itemObj.optLong("stop_timestamp", 0L)

                val startMs = startSec * 1000L
                val stopMs = stopSec * 1000L

                // 1. Filter out past programs (stopMs <= nowMs)
                if (stopMs in 1L..nowMs) {
                    continue
                }

                // 2. Remove duplicate programs with the same title and start/stop timestamps
                val duplicateKey = "${title.lowercase()}_${startSec}_${stopSec}"
                if (!seenKeys.add(duplicateKey)) {
                    continue
                }

                val startTimeStr = formatTimePortuguese(startMs)
                val endTimeStr = formatTimePortuguese(stopMs)

                val progress = if (nowMs in startMs..stopMs && stopMs > startMs) {
                    ((nowMs - startMs).toFloat() / (stopMs - startMs).toFloat()).coerceIn(0.0f, 1.0f)
                } else {
                    0.0f
                }

                programs.add(
                    Program(
                        title = title,
                        progress = progress,
                        startTime = startTimeStr,
                        endTime = endTimeStr,
                        description = description
                    )
                )
            }

            val result = if (programs.isEmpty()) fallbackList else programs
            cache[streamId] = result
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching EPG for stream_id $streamId", e)
            fallbackList
        }
    }

    suspend fun fetchEPGForChannel(
        username: String,
        password: String,
        streamId: Int,
        serverUrl: String = "http://streatv.elementfx.com"
    ): List<Program> = generateEPG(username, password, streamId, serverUrl)

    fun extractStreamId(item: PlaylistItem): Int {
        if (item.id in 1..19999999) return item.id
        if (item.id >= 20000000) return item.id - 20000000

        val url = item.url
        if (url.isNotBlank()) {
            try {
                val lastSegment = url.substringAfterLast("/").substringBefore("?")
                val streamIdStr = lastSegment.substringBefore(".")
                streamIdStr.toIntOrNull()?.let { return it }
            } catch (e: Exception) {
                // Fallback
            }
        }
        return 0
    }

    private fun decodeBase64(encodedStr: String?): String {
        if (encodedStr.isNullOrBlank()) return ""
        return try {
            val bytes = Base64.decode(encodedStr.trim(), Base64.DEFAULT)
            String(bytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            encodedStr.trim()
        }
    }

    private fun formatTimePortuguese(timeMs: Long): String {
        if (timeMs <= 0L) return ""
        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"))
        cal.timeInMillis = timeMs

        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val h12 = if (hour24 % 12 == 0) 12 else hour24 % 12
        val period = when {
            hour24 in 0..5 -> "da madrugada"
            hour24 in 6..11 -> "da manhã"
            hour24 in 12..17 -> "da tarde"
            else -> "da noite"
        }
        val minStr = "%02d".format(minute)
        return "$h12:$minStr $period"
    }

    private fun cleanServerUrl(serverUrl: String): String {
        if (serverUrl.isBlank()) return "http://streatv.elementfx.com"
        var cleaned = serverUrl.trim()
        val removeSuffixes = listOf("/get.php", "/player_api.php", "/xmltv.php")
        for (ending in removeSuffixes) {
            if (cleaned.endsWith(ending)) {
                cleaned = cleaned.substring(0, cleaned.length - ending.length)
            }
        }
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length - 1)
        }
        return if (cleaned.isNotBlank()) cleaned else "http://streatv.elementfx.com"
    }
}
