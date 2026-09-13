
package com.example.hearingaid

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

class AudioEngine(private val audioManager: AudioManager) {

    private val sampleRate = 44100
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    @Volatile
    var gain: Float = 3.0f

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    @Volatile
    private var isRunning = false
    private var thread: Thread? = null

    fun start() {
        if (isRunning) return

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        val minRecordBuf = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
        val minTrackBuf = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelIn,
            encoding,
            minRecordBuf * 2
        )

        audioRecord?.audioSessionId?.let { sessionId ->
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(channelOut)
            .setEncoding(encoding)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(minTrackBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        audioRecord?.startRecording()
        audioTrack?.play()
        isRunning = true

        thread = Thread {
            val bufferSize = minRecordBuf
            val buffer = ShortArray(bufferSize)
            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (read > 0) {
                    processBuffer(buffer, read)
                    audioTrack?.write(buffer, 0, read)
                }
            }
        }
        thread?.start()
    }

    private fun processBuffer(buffer: ShortArray, length: Int) {
        val threshold = 0.15f
        for (i in 0 until length) {
            val normalized = buffer[i] / 32768.0f
            val boosted = if (abs(normalized) < threshold) {
                normalized * gain
            } else {
                sign(normalized) * threshold * gain +
                    (normalized - sign(normalized) * threshold) * (gain * 0.3f)
            }
            val clipped = tanh(boosted.toDouble()).toFloat()
            buffer[i] = (clipped * 32767).toInt().toShort()
        }
    }

    fun stop() {
        isRunning = false
        thread?.join(200)
        thread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null

        audioManager.mode = AudioManager.MODE_NORMAL
    }
}
