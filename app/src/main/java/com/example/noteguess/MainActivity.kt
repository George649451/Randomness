package com.example.noteguess

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private val solfegeNames =
        listOf("Do", "Do♯", "Re", "Re♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")
    private val letterNames =
        listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val baseMidi = 60 // C4 (middle C); the keyboard covers one octave up to B4.

    private val player = NotePlayer()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var scoreText: TextView
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var playButton: Button
    private lateinit var replayButton: Button
    private lateinit var keyboard: PianoKeyboardView

    private var targetNote = -1
    private var awaitingGuess = false
    private var correctCount = 0
    private var totalCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scoreText = findViewById(R.id.score_text)
        statusText = findViewById(R.id.status_text)
        resultText = findViewById(R.id.result_text)
        playButton = findViewById(R.id.play_button)
        replayButton = findViewById(R.id.replay_button)
        keyboard = findViewById(R.id.keyboard)

        keyboard.onKeyPressed = { onGuess(it) }

        playButton.setOnClickListener { startRound() }
        replayButton.setOnClickListener {
            if (targetNote >= 0) player.play(baseMidi + targetNote)
        }

        setGuessingEnabled(false)
        replayButton.isEnabled = false
        updateScore()
    }

    /** Combined display name like "Do (C)" or "Fa♯ (F♯)". */
    private fun noteName(note: Int) = "${solfegeNames[note]} (${letterNames[note]})"

    private fun startRound() {
        targetNote = Random.nextInt(solfegeNames.size)
        awaitingGuess = true
        resultText.text = ""
        statusText.text = getString(R.string.status_guess)
        setGuessingEnabled(true)
        replayButton.isEnabled = true
        player.play(baseMidi + targetNote)
    }

    private fun onGuess(guess: Int) {
        if (!awaitingGuess) return
        awaitingGuess = false
        setGuessingEnabled(false)

        totalCount++
        val correct = guess == targetNote
        if (correct) correctCount++

        // Play the note the user picked, then reveal the answer once it has sounded.
        player.play(baseMidi + guess)
        statusText.text = getString(R.string.status_you_picked, noteName(guess))
        handler.postDelayed({
            resultText.text = if (correct) {
                getString(R.string.result_correct, noteName(targetNote))
            } else {
                getString(R.string.result_wrong, noteName(targetNote), noteName(guess))
            }
            statusText.text = getString(R.string.status_play_again)
            updateScore()
        }, NotePlayer.NOTE_DURATION_MS.toLong())
    }

    private fun setGuessingEnabled(enabled: Boolean) {
        keyboard.isEnabled = enabled
    }

    private fun updateScore() {
        scoreText.text = getString(R.string.score_format, correctCount, totalCount)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.stop()
        super.onDestroy()
    }
}
