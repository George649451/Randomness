package com.example.noteguess

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Hands-free game loop for drivers: play a note, listen for the spoken
 * answer, match it against the user's enrolled recordings, and speak the
 * verdict. No touch needed after Start.
 */
class DrivingActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val player = NotePlayer()
    private val recorder = SpeechRecorder()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var matcher: VoiceMatcher? = null

    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var toggleButton: Button

    @Volatile
    private var running = false
    private var targetNote = -1
    private var retried = false
    private var correctCount = 0
    private var totalCount = 0
    private var utteranceCounter = 0
    private var pendingOnSpoken: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driving)
        title = getString(R.string.driving_title)

        statusText = findViewById(R.id.driving_status)
        scoreText = findViewById(R.id.driving_score)
        toggleButton = findViewById(R.id.driving_toggle)
        toggleButton.setOnClickListener { if (running) stopGame() else startGame() }

        tts = TextToSpeech(this, this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        matcher = VoiceMatcher.load(this)
        if (matcher == null) {
            statusText.text = getString(R.string.driving_needs_setup)
            toggleButton.text = getString(R.string.driving_go_to_setup)
            toggleButton.setOnClickListener {
                startActivity(Intent(this, SetupVoiceActivity::class.java))
                finish()
            }
        } else {
            statusText.text = getString(R.string.driving_ready)
        }
        updateScore()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale.US
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onError(utteranceId: String?) = finished()
                override fun onDone(utteranceId: String?) = finished()
                private fun finished() {
                    val action = pendingOnSpoken ?: return
                    pendingOnSpoken = null
                    handler.post(action)
                }
            })
        }
    }

    /** Speaks [text], then runs [onDone] on the main thread. */
    private fun speak(text: String, onDone: Runnable) {
        val engine = tts
        if (!ttsReady || engine == null) {
            // No TTS available: show the text and just pace the game with a delay.
            statusText.text = text
            handler.postDelayed(onDone, 1500)
            return
        }
        statusText.text = text
        pendingOnSpoken = onDone
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt${utteranceCounter++}")
    }

    private fun startGame() {
        if (matcher == null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            statusText.text = getString(R.string.mic_permission_needed)
            return
        }
        running = true
        toggleButton.text = getString(R.string.driving_stop)
        speak(getString(R.string.driving_get_ready)) { nextRound() }
    }

    private fun stopGame() {
        running = false
        pendingOnSpoken = null
        handler.removeCallbacksAndMessages(null)
        recorder.cancel()
        tts?.stop()
        player.stop()
        toggleButton.text = getString(R.string.driving_start)
        statusText.text = getString(R.string.driving_ready)
    }

    private fun nextRound() {
        if (!running) return
        targetNote = Random.nextInt(Notes.COUNT)
        retried = false
        statusText.text = getString(R.string.driving_listen_note)
        player.play(Notes.BASE_MIDI + targetNote)
        handler.postDelayed({ listen() }, NotePlayer.NOTE_DURATION_MS + 300L)
    }

    private fun listen() {
        if (!running) return
        statusText.text = getString(R.string.driving_say_answer)
        executor.execute {
            val utterance = recorder.record()
            handler.post { onUtterance(utterance) }
        }
    }

    private fun onUtterance(pcm: ShortArray?) {
        if (!running) return
        val result = pcm?.let { matcher?.match(it) }
        when {
            result == null && !retried -> {
                retried = true
                speak(getString(R.string.driving_not_heard)) { listen() }
            }
            result == null -> {
                // Two silent tries: reveal the answer, don't score the round.
                speak(getString(R.string.driving_reveal, Notes.spoken[targetNote])) {
                    scheduleNextRound()
                }
            }
            result.ambiguous && !retried -> {
                retried = true
                speak(getString(R.string.driving_say_again)) { listen() }
            }
            else -> respond(result.note)
        }
    }

    private fun respond(guess: Int) {
        totalCount++
        val correct = guess == targetNote
        if (correct) correctCount++
        updateScore()
        val verdict = if (correct) {
            getString(R.string.driving_correct)
        } else {
            getString(R.string.driving_wrong, Notes.spoken[targetNote])
        }
        speak(verdict) { scheduleNextRound() }
    }

    private fun scheduleNextRound() {
        if (!running) return
        handler.postDelayed({ nextRound() }, 1200)
    }

    private fun updateScore() {
        scoreText.text = getString(R.string.score_format, correctCount, totalCount)
    }

    override fun onPause() {
        // A driving game should never keep the mic or audio running in the background.
        if (running) stopGame()
        super.onPause()
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recorder.cancel()
        tts?.stop()
        tts?.shutdown()
        player.stop()
        executor.shutdown()
        super.onDestroy()
    }
}
