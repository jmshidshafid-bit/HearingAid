
package com.example.hearingaid

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var engine: AudioEngine
    private var isListening = false

    private val requestMicPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            toggleListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        engine = AudioEngine(audioManager)

        val statusText = findViewById<TextView>(R.id.statusText)
        val toggleButton = findViewById<Button>(R.id.toggleButton)
        val gainSeekBar = findViewById<SeekBar>(R.id.gainSeekBar)

        gainSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                engine.gain = 1.0f + (progress / 100f) * 5.0f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        engine.gain = 1.0f + (gainSeekBar.progress / 100f) * 5.0f

        toggleButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                toggleListening()
            }
        }
    }

    private fun toggleListening() {
        val statusText = findViewById<TextView>(R.id.statusText)
        val toggleButton = findViewById<Button>(R.id.toggleButton)

        isListening = !isListening
        if (isListening) {
            engine.start()
            statusText.text = "Listening..."
            toggleButton.text = "Stop"
        } else {
            engine.stop()
            statusText.text = "Stopped"
            toggleButton.text = "Start Listening"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isListening) {
            engine.stop()
        }
    }
}
