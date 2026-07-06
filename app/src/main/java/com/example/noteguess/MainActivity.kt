package com.example.noteguess

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private val noteNames = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val baseMidi = 60 // C4 (middle C); buttons cover one octave up to B4.

    private val player = NotePlayer()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var scoreText: TextView
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var playButton: Button
    private lateinit var replayButton: Button
    private val noteButtons = mutableListOf<Button>()

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

        buildNoteButtons(findViewById(R.id.note_grid))

        playButton.setOnClickListener { startRound() }
        replayButton.setOnClickListener {
            if (targetNote >= 0) player.play(baseMidi + targetNote)
        }

        setGuessingEnabled(false)
        replayButton.isEnabled = false
        updateScore()
    }

    private fun buildNoteButtons(grid: GridLayout) {
        noteNames.forEachIndexed { index, name ->
            val button = Button(this).apply {
                text = name
                textSize = 18f
                setOnClickListener { onGuess(index) }
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply { width = 0 }
            grid.addView(button, params)
            noteButtons.add(button)
        }
    }

    private fun startRound() {
        targetNote = Random.nextInt(noteNames.size)
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
        statusText.text = getString(R.string.status_you_picked, noteNames[guess])
        handler.postDelayed({
            resultText.text = if (correct) {
                getString(R.string.result_correct, noteNames[targetNote])
            } else {
                getString(R.string.result_wrong, noteNames[targetNote], noteNames[guess])
            }
            statusText.text = getString(R.string.status_play_again)
            updateScore()
        }, NotePlayer.NOTE_DURATION_MS.toLong())
    }

    private fun setGuessingEnabled(enabled: Boolean) {
        noteButtons.forEach { it.isEnabled = enabled }
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
