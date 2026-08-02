package com.app.resn8.storage.artwork

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.app.resn8.domain.model.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class ArtworkCache(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDirectory = File(appContext.cacheDir, "artwork")

    suspend fun resolveEmbedded(media: MediaFile): String? = withContext(Dispatchers.IO) {
        if (!media.isAvailable) return@withContext null
        media.artworkUri?.let { existing ->
            val uri = Uri.parse(existing)
            if (uri.scheme == "file" && uri.path?.let(::File)?.isFile == true) return@withContext existing
        }

        val folderPath = media.relativePath.substringBeforeLast('/', "")
        val external = ArtworkCandidateIndex(appContext).find(media.sourceId, folderPath)
        if (external != null) {
            val externalKey = sha256("${media.sourceId}\u0000$folderPath\u0000${external.modifiedTimeMs}\u0000${external.size}")
            val externalTarget = File(cacheDirectory, "$externalKey.img")
            if (externalTarget.isFile && externalTarget.length() > 0L) {
                return@withContext Uri.fromFile(externalTarget).toString()
            }
            val cached = runCatching {
                appContext.contentResolver.openInputStream(Uri.parse(external.documentUri))?.use { input ->
                    writeBounded(input, externalTarget)
                } ?: false
            }.getOrDefault(false)
            if (cached) return@withContext Uri.fromFile(externalTarget).toString()
        }

        val cacheKey = sha256("${media.id}\u0000${media.modifiedTimeMs}\u0000${media.size}")
        val target = File(cacheDirectory, "$cacheKey.img")
        if (target.isFile && target.length() > 0L) return@withContext Uri.fromFile(target).toString()

        val retriever = MediaMetadataRetriever()
        try {
            // MediaMetadataRetriever opens the persisted content URI read-only.
            retriever.setDataSource(appContext, Uri.parse(media.documentUri))
            val picture = retriever.embeddedPicture ?: return@withContext null
            if (picture.isEmpty() || picture.size > MAX_ARTWORK_BYTES) return@withContext null
            cacheDirectory.mkdirs()
            val temporary = File(cacheDirectory, "$cacheKey.tmp")
            temporary.outputStream().use { it.write(picture) }
            if (!temporary.renameTo(target)) {
                temporary.delete()
                return@withContext null
            }
            Uri.fromFile(target).toString()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun writeBounded(input: InputStream, target: File): Boolean {
        cacheDirectory.mkdirs()
        val temporary = File(cacheDirectory, "${target.name}.tmp")
        var total = 0L
        return try {
            temporary.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_ARTWORK_BYTES) return false
                    output.write(buffer, 0, read)
                }
            }
            total > 0L && temporary.renameTo(target)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_ARTWORK_BYTES = 20 * 1024 * 1024
    }
}
