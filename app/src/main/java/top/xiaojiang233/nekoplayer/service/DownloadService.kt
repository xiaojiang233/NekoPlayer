package top.xiaojiang233.nekoplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.repository.SongRepository
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val EXTRA_SONG = "EXTRA_SONG"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "download_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val song = intent?.getSerializableExtra(EXTRA_SONG) as? OnlineSong

        // Must call startForeground() within 5 seconds of startForegroundService()
        // even if we are about to stop.
        val initialNotification = createNotification(song?.title ?: "Music", "Preparing...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        if (song == null || song.songUrl.isNullOrBlank()) {
            stopForeground(true)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        serviceScope.launch {
            sendBroadcast(SongRepository.createDownloadStatusIntent(song.id, SongRepository.DownloadState.Downloading(0f)))
            try {
                val (finalUri, mediaStoreId) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    downloadWithMediaStore(song)
                } else {
                    Pair(downloadWithLegacyStorage(song), song.id)
                }

                // Download lyrics and cover too, and save full metadata
                downloadMetadata(song, mediaStoreId)

                sendBroadcast(SongRepository.createDownloadStatusIntent(song.id, SongRepository.DownloadState.Downloaded))

                // Update notification to show completion
                val completionNotification = createNotification(song.title, "Download complete")
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, completionNotification)
            } catch (e: Exception) {
                e.printStackTrace()
                sendBroadcast(SongRepository.createDownloadStatusIntent(song.id, SongRepository.DownloadState.Failed(e.message ?: "Unknown Error")))

                // Update notification to show error
                val errorNotification = createNotification(song.title, "Download failed: ${e.message}")
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, errorNotification)
            }
            stopForeground(true)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music download notifications"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher) // Use local launcher icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun downloadWithMediaStore(song: OnlineSong): Pair<String, String> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${song.title} - ${song.artist}.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val resolver = applicationContext.contentResolver
        val uri = resolver.insert(collection, contentValues) ?: throw Exception("MediaStore insert failed")
        val mediaStoreId = android.content.ContentUris.parseId(uri).toString()

        try {
            resolver.openOutputStream(uri)?.use { output ->
                val (input, totalSize) = getInputStreamAndSizeWithRedirects(song.songUrl!!)

                input.use { inputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var downloadedSize = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        if (totalSize > 0) {
                            val progress = downloadedSize.toFloat() / totalSize.toFloat()
                            sendBroadcast(SongRepository.createDownloadStatusIntent(song.id, SongRepository.DownloadState.Downloading(progress)))
                        }
                    }
                }
            }
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return Pair(uri.toString(), mediaStoreId)
    }

    private fun openConnectionWithRedirects(url: String, redirectLimit: Int = 5): HttpURLConnection {
        var currentUrl = url
        var redirects = 0
        while (redirects < redirectLimit) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false // We handle redirects manually.
            connection.connectTimeout = 15000 // 15s
            connection.readTimeout = 15000 // 15s

            val status = connection.responseCode
            if (status in 300..308) { // HTTP_MOVED_PERM, HTTP_MOVED_TEMP, HTTP_SEE_OTHER, etc.
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                if (newUrl != null) {
                    currentUrl = newUrl
                    redirects++
                    continue
                }
            }
            // If not a redirect, or a redirect without a Location header, return the connection for the caller to handle.
            return connection
        }
        throw Exception("Too many redirects")
    }

    private fun getInputStreamAndSizeWithRedirects(url: String): Pair<InputStream, Long> {
        val connection = openConnectionWithRedirects(url, redirectLimit = 10)
        if (connection.responseCode !in 200..299) {
            val error = "Server returned HTTP ${connection.responseCode}"
            connection.disconnect()
            throw Exception(error)
        }
        // The caller of this function will close the input stream, which in turn disconnects.
        return Pair(connection.inputStream, connection.contentLength.toLong())
    }

    private fun downloadWithLegacyStorage(song: OnlineSong): String {
        val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NekoPlayer")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, "${song.title} - ${song.artist}.mp3")

        val (input, totalSize) = getInputStreamAndSizeWithRedirects(song.songUrl!!)
        input.use { inputStream ->
            targetFile.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var downloadedSize = 0L
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedSize += bytesRead
                    if (totalSize > 0) {
                        val progress = downloadedSize.toFloat() / totalSize.toFloat()
                        sendBroadcast(SongRepository.createDownloadStatusIntent(song.id, SongRepository.DownloadState.Downloading(progress)))
                    }
                }
            }
        }
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = android.net.Uri.fromFile(targetFile)
        sendBroadcast(mediaScanIntent)

        return targetFile.absolutePath
    }

    private suspend fun downloadMetadata(song: OnlineSong, mediaStoreId: String) {
        val context = applicationContext
        val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val fileNameBase = "$safeTitle - $safeArtist".trim()
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        if (musicDir != null && !musicDir.exists()) musicDir.mkdirs()

        var hasLyrics = false
        var hasCover = false

        // Download lyrics
        if (!song.lyricUrl.isNullOrBlank() && song.lyricUrl.startsWith("http")) {
            var connection: HttpURLConnection? = null
            try {
                connection = openConnectionWithRedirects(song.lyricUrl)
                if (connection.responseCode in 200..299) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    if (text.isNotBlank()) {
                        File(musicDir, "$fileNameBase.lrc").writeText(text)
                        hasLyrics = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                connection?.disconnect()
            }
        }

        // Download cover
        if (!song.coverUrl.isNullOrBlank() && song.coverUrl.startsWith("http")) {
            try {
                val (input, _) = getInputStreamAndSizeWithRedirects(song.coverUrl)
                input.use { inputStream ->
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val coverFile = File(musicDir, "$fileNameBase.jpg")
                        coverFile.outputStream().use { output ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, output)
                        }
                        bitmap.recycle()
                        hasCover = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Always save metadata even if no lyrics/cover, to preserve original title/artist/platform
        SongRepository.saveLocalSongMetadata(
            SongRepository.LocalSongMetadata(
                songId = mediaStoreId,
                hasCover = hasCover,
                hasLyrics = hasLyrics,
                title = song.title,
                artist = song.artist,
                platform = song.platform,
                album = song.album
            )
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
