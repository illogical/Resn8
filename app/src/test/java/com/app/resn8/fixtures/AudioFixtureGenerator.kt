package com.app.resn8.fixtures

import java.io.ByteArrayOutputStream

object AudioFixtureGenerator {

    /**
     * Creates a synthetic ID3v2.3 MP3 header with specified tags.
     */
    fun createMp3WithId3Tags(
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        trackNumber: String? = null
    ): ByteArray {
        val bos = ByteArrayOutputStream()

        val tagBody = ByteArrayOutputStream()
        title?.let { tagBody.write(createFrame("TIT2", it)) }
        artist?.let { tagBody.write(createFrame("TPE1", it)) }
        album?.let { tagBody.write(createFrame("TALB", it)) }
        trackNumber?.let { tagBody.write(createFrame("TRCK", it)) }

        val tagBytes = tagBody.toByteArray()
        val tagSize = tagBytes.size

        // ID3v2.3 header: "ID3" + version 3.0 + flags + synchsafe size
        bos.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()))
        bos.write(0x03) // major version
        bos.write(0x00) // revision
        bos.write(0x00) // flags
        bos.write(encodeSynchsafe(tagSize))
        bos.write(tagBytes)

        // Dummy silent MP3 frame header (0xFF 0xFB ...)
        bos.write(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64.toByte()))
        repeat(128) { bos.write(0) }

        return bos.toByteArray()
    }

    private fun createFrame(frameId: String, value: String): ByteArray {
        val bos = ByteArrayOutputStream()
        bos.write(frameId.toByteArray(Charsets.US_ASCII))
        val textBytes = value.toByteArray(Charsets.ISO_8859_1)
        val contentSize = textBytes.size + 1 // +1 for encoding byte 0x00 (ISO-8859-1)

        bos.write((contentSize shr 24) and 0xFF)
        bos.write((contentSize shr 16) and 0xFF)
        bos.write((contentSize shr 8) and 0xFF)
        bos.write(contentSize and 0xFF)

        bos.write(0x00) // Frame flags 1
        bos.write(0x00) // Frame flags 2
        bos.write(0x00) // Encoding byte (0 = ISO-8859-1)
        bos.write(textBytes)

        return bos.toByteArray()
    }

    private fun encodeSynchsafe(size: Int): ByteArray {
        return byteArrayOf(
            ((size shr 21) and 0x7F).toByte(),
            ((size shr 14) and 0x7F).toByte(),
            ((size shr 7) and 0x7F).toByte(),
            (size and 0x7F).toByte()
        )
    }
}
