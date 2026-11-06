package com.example.internalrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.IBinder
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import java.io.File
import java.io.FileOutputStream

class RecordService : Service() {
    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false

    private val sampleRate = 44100
    private val bitRate = 128 // kbps

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopRecording(); stopSelf(); return START_NOT_STICKY
        }
        startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val code = intent!!.getIntExtra("code", 0)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>("data")!!
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data).also {
            it.registerCallback(object : MediaProjection.Callback() {}, null) // مطلوب في أندرويد 14+
        }
        startRecording()
        return START_NOT_STICKY
    }

    private fun startRecording() {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val bufBytes = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
        ) * 4

        record = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufBytes)
            .build()

        val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)!!
        val file = File(dir, "rec_${System.currentTimeMillis()}.mp3")

        // مشفّر MP3 (LAME)
        val lame: AndroidLame = LameBuilder()
            .setInSampleRate(sampleRate)
            .setOutChannels(2)
            .setOutBitrate(bitRate)
            .setOutSampleRate(sampleRate)
            .setMode(LameBuilder.Mode.STEREO)
            .setQuality(5)
            .build()

        running = true
        record!!.startRecording()

        worker = Thread {
            val pcm = ShortArray(bufBytes / 2)
            // حجم المخرجات المقترح من LAME: 1.25 * samples + 7200
            val mp3Buf = ByteArray((1.25 * pcm.size / 2).toInt() + 7200)
            FileOutputStream(file).use { out ->
                while (running) {
                    val read = record!!.read(pcm, 0, pcm.size) // عدد الـ shorts
                    if (read <= 0) continue
                    val samplesPerChannel = read / 2
                    val encoded = lame.encodeBufferInterleaved(pcm, samplesPerChannel, mp3Buf)
                    if (encoded > 0) out.write(mp3Buf, 0, encoded)
                }
                val last = lame.lameFlush(mp3Buf)
                if (last > 0) out.write(mp3Buf, 0, last)
            }
            lame.close()
        }.also { it.start() }
    }

    private fun stopRecording() {
        running = false
        worker?.join()
        record?.stop(); record?.release(); record = null
        projection?.stop(); projection = null
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("rec", "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, "rec")
            .setContentTitle("تسجيل الصوت الداخلي شغال")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onDestroy() { stopRecording(); super.onDestroy() }
}
