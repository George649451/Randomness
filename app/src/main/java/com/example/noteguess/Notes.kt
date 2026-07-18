package com.example.noteguess

/** Shared naming for the 12 semitones of the game octave (C4..B4). */
object Notes {
    const val BASE_MIDI = 60 // C4 (middle C)
    const val COUNT = 12

    val solfege =
        listOf("Do", "Do♯", "Re", "Re♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")
    val letters =
        listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

    /** Spellings that make English TTS pronounce solfège correctly. */
    val spoken = listOf(
        "doh", "doh sharp", "ray", "ray sharp", "mee", "fah",
        "fah sharp", "sol", "sol sharp", "lah", "lah sharp", "see",
    )

    /** Combined display name like "Do (C)" or "Fa♯ (F♯)". */
    fun display(note: Int) = "${solfege[note]} (${letters[note]})"
}
