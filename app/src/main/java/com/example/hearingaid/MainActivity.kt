
package com.example.hearingaid

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var service: AudioService? = null
    private var isBound = false
    private var isListening = false
    private var pendingStart = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as AudioService.LocalBinder
            service = localBinder.getService()
            isBound = true
            applyCurrentSettings()
            if (pendingStart) {
                service?.startListening()
                pendingStart = false
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
        }
    }

    private val requestMicPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginListening()
    }

    private val requestNotifPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val toggleButton = findViewById<Button>(R.id.toggleButton)
        val gainSeekBar = findViewById<SeekBar>(R.id.gainSeekBar)
        val micSourceGroup = findViewById<RadioGroup>(R.id.micSourceGroup)

        micSourceGroup.setOnCheckedChangeListener { _, checkedId ->
            val source = if (checkedId == R.id.radioPhoneMic) MicSource.PHONE else MicSource.EARPHONE
            service?.engine?.micSource = source
            if (isListening) {
                service?.stopListening()
                service?.startListening()
            }
        }

        gainSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                service?.engine?.gain = 1.0f + (progress / 100f) * 5.0f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        toggleButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                if (isListening) stopListeningFlow() else beginListening()
            }
        }
    }

    private fun applyCurrentSettings() {
        val gainSeekBar = findViewById<SeekBar>(R.id.gainSeekBar)
        val micSourceGroup = findViewById<RadioGroup>(R.id.micSourceGroup)
        service?.engine?.gain = 1.0f + (gainSeekBar.progress / 100f) * 5.0f
        service?.engine?.micSource =
            if (micSourceGroup.checkedRadioButtonId == R.id.radioPhoneMic) MicSource.PHONE else MicSource.EARPHONE
    }

    private fun beginListening() {
        pendingStart = true
        val intent = Intent(this, AudioService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        isListening = true
        findViewById<TextView>(R.id.statusText).text = "Listening..."
        findViewById<Button>(R.id.toggleButton).text = "Stop"
    }

    private fun stopListeningFlow() {
        service?.stopListening()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        stopService(Intent(this, AudioService::class.java))
        isListening = false
        findViewById<TextView>(R.id.statusText).text = "Stopped"
        findViewById<Button>(R.id.toggleButton).text = "Start Listening"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
