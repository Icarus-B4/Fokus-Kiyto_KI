package com.deepcore.kiytoapp.services

import android.content.Context
import com.deepcore.kiytoapp.utils.ApiKeys
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.Video
import com.google.api.services.youtube.model.VideoListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Collections

class YouTubeService(private val context: Context) {
    private val youtube: YouTube by lazy {
        YouTube.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            null
        ).setApplicationName("KiytoApp").build()
    }

    private val client = OkHttpClient()

    suspend fun getVideoInfo(videoId: String): VideoInfo {
        return try {
            // Versuche zuerst die API-Methode
            getVideoInfoFromApi(videoId)
        } catch (e: Exception) {
            // Fallback zur Web-Scraping-Methode
            getVideoInfoFromWeb(videoId)
        }
    }

    private suspend fun getVideoInfoFromWeb(videoId: String): VideoInfo = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: throw IllegalStateException("Keine Antwort vom Server")

        // Extrahiere ytInitialData
        val dataMatch = Regex("ytInitialData = (.*?);</script>").find(html)
        val playerMatch = Regex("ytInitialPlayerResponse = (.*?);</script>").find(html)

        val playerData = playerMatch?.groupValues?.get(1)?.let { JSONObject(it) }
            ?: throw IllegalStateException("Video-Daten nicht gefunden")

        val videoDetails = playerData.getJSONObject("videoDetails")
        
        VideoInfo(
            title = videoDetails.getString("title"),
            description = videoDetails.getString("shortDescription"),
            duration = formatDuration(videoDetails.getString("lengthSeconds").toLong()),
            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
            videoUrl = url
        )
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60

        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, remainingSeconds)
            else -> String.format("%02d:%02d", minutes, remainingSeconds)
        }
    }

    private suspend fun getVideoInfoFromApi(videoId: String): VideoInfo {
        val apiKey = ApiKeys.getYouTubeApiKey(context)
            ?: throw IllegalStateException("YouTube API-Schlüssel nicht gefunden")

        val request = youtube.videos()
            .list(Collections.singletonList("snippet,contentDetails"))
            .setId(Collections.singletonList(videoId))
            .setKey(apiKey)

        val response: VideoListResponse = request.execute()
        val video: Video = response.items.firstOrNull() 
            ?: throw IllegalArgumentException("Video nicht gefunden")

        return VideoInfo(
            title = video.snippet.title,
            description = video.snippet.description,
            duration = video.contentDetails.duration,
            thumbnailUrl = video.snippet.thumbnails.high.url,
            videoUrl = "https://www.youtube.com/watch?v=$videoId"
        )
    }

    fun extractVideoId(url: String): String {
        val regex = Regex("""(?:youtube\.com\/watch\?v=|youtu\.be\/)([^&\n?#]+)""")
        return regex.find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Ungültige YouTube-URL")
    }

    data class VideoInfo(
        val title: String,
        val description: String,
        val duration: String,
        val thumbnailUrl: String,
        val videoUrl: String
    )
}
