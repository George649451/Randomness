package com.example.noteguess

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * One-time setup: the user records their own voice saying each of the 12
 * answers (e.g. "that's fa sharp"). These recordings become the vocabulary
 * that the hands-free driving mode matches against.
 */
class SetupVoiceActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val recorder = SpeechRecorder()

    private lateinit var statusText: TextView
    private lateinit var progressText: TextView
    private val rowLabels = mutableListOf<TextView>()
    private val recordButtons = mutableListOf<Button>()
    private val playButtons = mutableListOf<Button>()

    private var playback: AudioTrack? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_voice)
        title = getString(R.string.setup_title)

        statusText = findViewById(R.id.setup_status)
        progressText = findViewById(R.id.setup_progress)
        val list = findViewById<LinearLayout>(R.id.setup_list)

        for (note in 0 until Notes.COUNT) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(this).apply {
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val playButton = Button(this).apply {
                text = getString(R.string.setup_play)
                setOnClickListener { playRecording(note) }
            }
            val recordButton = Button(this).apply {
                text = getString(R.string.setup_record)
                setOnClickListener { recordAnswer(note) }
            }
            row.addView(label)
            row.addView(playButton)
            row.addView(recordButton)
            list.addView(row)
            rowLabels.add(label)
            recordButtons.add(recordButton)
            playButtons.add(playButton)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        refresh()
    }

    private fun refresh() {
        for (note in 0 until Notes.COUNT) {
            val recorded = EnrollmentStore.exists(this, note)
            rowLabels[note].text =
                (if (recorded) "✅ " else "◻ ") + Notes.display(note)
            playButtons[note].isEnabled = recorded && !busy
            recordButtons[note].isEnabled = !busy
        }
        val count = EnrollmentStore.recordedCount(this)
        progressText.text = getString(R.string.setup_progress, count, Notes.COUNT)
        if (!busy) {
            statusText.text = if (count == Notes.COUNT) {
                getString(R.string.setup_done)
            } else {
                getString(R.string.setup_hint)
            }
        }
    }

    private fun recordAnswer(note: Int) {
        if (busy) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, R.string.mic_permission_needed, Toast.LENGTH_LONG).show()
            return
        }
        busy = true
        statusText.text = getString(R.string.setup_listening, Notes.display(note))
        refresh()
        executor.execute {
            val pcm = recorder.record(timeoutMs = 8000)
            runOnUiThread {
                if (pcm != null) {
                    EnrollmentStore.save(this, note, pcm)
                } else {
                    Toast.makeText(this, R.string.setup_nothing_heard, Toast.LENGTH_SHORT).show()
                }
                busy = false
                refresh()
            }
        }
    }

    private fun playRecording(note: Int) {
        val pcm = EnrollmentStore.load(this, note) ?: return
        playback?.release()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SpeechRecorder.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        playback = track
    }

    override fun onDestroy() {
        recorder.cancel()
        playback?.release()
        playback = null
        executor.shutdown()
        super.onDestroy()
    }
}
