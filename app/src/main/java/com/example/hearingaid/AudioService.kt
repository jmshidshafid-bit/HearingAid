
package com.example.hearingaid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AudioService : Service() {

    private val binder = LocalBinder()
    lateinit var engine: AudioEngine
        private set

    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onCreate() {
        super.onCreate()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        engine = AudioEngine(audioManager)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        return START_STICKY
    }

    fun startListening() {
        engine.start()
    }

    fun stopListening() {
        engine.stop()
    }

    private fun buildNotification(): Notification {
        val channelId = "hearing_aid_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Hearing Aid",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Hearing Aid Active")
            .setContentText("Listening and boosting quiet sounds")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1
    }
}
