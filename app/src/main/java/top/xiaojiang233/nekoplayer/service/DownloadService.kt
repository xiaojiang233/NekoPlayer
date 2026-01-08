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
        if (song == null || song.songUrl.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Must call startForeground() within 5 seconds of startForegroundService()
        val notification = createNotification(song.title, "Starting download...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            sendBroadcast(SongRepository.createDownloadStatusIntent(song.id, SongRepository.DownloadState.Downloading(0f)))
            try {
                val finalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    downloadWithMediaStore(song)
                } else {
                    downloadWithLegacyStorage(song)
                }
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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun downloadWithMediaStore(song: OnlineSong): String {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${song.title} - ${song.artist}.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val resolver = applicationContext.contentResolver
        val uri = resolver.insert(collection, contentValues) ?: throw Exception("MediaStore insert failed")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                val connection = URL(song.songUrl).openConnection()
                val totalSize = connection.contentLength.toLong()
                var downloadedSize = 0L

                connection.getInputStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
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
        return uri.toString()
    }

    private fun downloadWithLegacyStorage(song: OnlineSong): String {
        val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NekoPlayer")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, "${song.title} - ${song.artist}.mp3")

        URL(song.songUrl).openStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        mediaScanIntent.data = android.net.Uri.fromFile(targetFile)
        sendBroadcast(mediaScanIntent)

        return targetFile.absolutePath
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}