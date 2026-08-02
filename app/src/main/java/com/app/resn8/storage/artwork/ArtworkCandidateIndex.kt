package com.app.resn8.storage.artwork

import android.content.Context
import com.app.resn8.storage.indexer.ArtworkCandidate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ArtworkCandidateIndex(context: Context) {
    @Serializable
    data class Candidate(
        val documentUri: String,
        val filename: String,
        val modifiedTimeMs: Long? = null,
        val size: Long? = null
    )

    private val directory = File(context.applicationContext.filesDir, "artwork-candidates")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val pending = mutableMapOf<String, Candidate>()

    fun record(candidate: ArtworkCandidate) {
        val folderPath = candidate.relativePath.substringBeforeLast('/', "")
        val incoming = Candidate(
            documentUri = candidate.documentUri.toString(),
            filename = candidate.filename,
            modifiedTimeMs = candidate.modifiedTimeMs,
            size = candidate.size
        )
        val current = pending[folderPath]
        if (current == null || rank(incoming.filename) < rank(current.filename) ||
            (rank(incoming.filename) == rank(current.filename) && incoming.filename < current.filename)
        ) {
            pending[folderPath] = incoming
        }
    }

    fun publish(sourceId: String) {
        directory.mkdirs()
        val target = fileFor(sourceId)
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeText(json.encodeToString(pending))
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IllegalStateException("Unable to publish artwork candidate index")
        }
    }

    fun find(sourceId: String, folderPath: String): Candidate? {
        val file = fileFor(sourceId)
        if (!file.isFile) return null
        return runCatching {
            json.decodeFromString<Map<String, Candidate>>(file.readText())[folderPath]
        }.getOrNull()
    }

    private fun fileFor(sourceId: String) = File(
        directory,
        sourceId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json"
    )

    private fun rank(filename: String): Int {
        val stem = filename.substringBeforeLast('.', filename).lowercase()
        val nameRank = listOf("cover", "folder", "front", "album", "albumart").indexOf(stem)
            .let { if (it < 0) 100 else it }
        val extensionRank = when (filename.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> 0
            "png" -> 1
            "webp" -> 2
            else -> 10
        }
        return nameRank * 10 + extensionRank
    }
}
