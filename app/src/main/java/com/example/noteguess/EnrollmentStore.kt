package com.example.noteguess

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Persists the user's recorded voice answers (raw 16 kHz mono PCM, one file
 * per note) in app-private storage.
 */
object EnrollmentStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "enrollment").apply { mkdirs() }

    private fun file(context: Context, note: Int): File =
        File(dir(context), "note_$note.pcm")

    fun exists(context: Context, note: Int): Boolean = file(context, note).exists()

    fun recordedCount(context: Context): Int =
        (0 until Notes.COUNT).count { exists(context, it) }

    fun isComplete(context: Context): Boolean = recordedCount(context) == Notes.COUNT

    fun save(context: Context, note: Int, pcm: ShortArray) {
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in pcm) bytes.putShort(sample)
        file(context, note).writeBytes(bytes.array())
    }

    fun load(context: Context, note: Int): ShortArray? {
        val target = file(context, note)
        if (!target.exists()) return null
        val bytes = target.readBytes()
        val pcm = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
        return pcm
    }
}
