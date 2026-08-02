package com.app.resn8.storage.indexer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import kotlin.coroutines.coroutineContext

data class DiscoveredFile(
    val documentUri: Uri,
    val documentId: String,
    val relativePath: String,
    val filename: String,
    val mimeType: String,
    val size: Long,
    val modifiedTimeMs: Long
)

data class DiscoveredFolder(
    val relativePath: String,
    val parentRelativePath: String?,
    val displayName: String
)

data class ScanTraversalProgress(
    val scannedFolders: Int,
    val inspectedDocuments: Int,
    val admittedAudio: Int,
    val unsupportedDocuments: Int,
    val unreadableBranches: Int,
    val artworkCandidates: Int,
    val currentRelativePath: String
)

data class ArtworkCandidate(
    val documentUri: Uri,
    val relativePath: String,
    val filename: String,
    val size: Long?,
    val modifiedTimeMs: Long?
)

class DocumentTreeScanner(
    private val context: Context,
    private val batchSize: Int = 100
) {
    private data class DirectoryNode(
        val documentId: String,
        val relativePath: String,
        val displayName: String
    )

    private val artworkExtensions = setOf("jpg", "jpeg", "png", "webp")
    private val artworkNames = setOf("cover", "folder", "front", "album", "albumart")

    suspend fun scanTree(
        treeUri: Uri,
        onFolderBatch: suspend (List<DiscoveredFolder>) -> Unit,
        onMediaBatch: suspend (List<DiscoveredFile>) -> Unit,
        onArtworkCandidate: suspend (ArtworkCandidate) -> Unit = {},
        onProgressUpdate: (ScanTraversalProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        require(rootDocId.isNotBlank()) { "Selected folder does not expose a valid document tree" }

        val folderQueue = ArrayDeque<DirectoryNode>()
        val visitedDirectoryIds = mutableSetOf<String>()
        folderQueue.add(DirectoryNode(documentId = rootDocId, relativePath = "", displayName = ""))

        val folderBatch = mutableListOf<DiscoveredFolder>()
        val mediaBatch = mutableListOf<DiscoveredFile>()
        var scannedFolders = 0
        var inspectedDocuments = 0
        var admittedAudio = 0
        var unsupportedDocuments = 0
        var unreadableBranches = 0
        var artworkCandidates = 0

        fun emitProgress(path: String) {
            onProgressUpdate(
                ScanTraversalProgress(
                    scannedFolders = scannedFolders,
                    inspectedDocuments = inspectedDocuments,
                    admittedAudio = admittedAudio,
                    unsupportedDocuments = unsupportedDocuments,
                    unreadableBranches = unreadableBranches,
                    artworkCandidates = artworkCandidates,
                    currentRelativePath = path
                )
            )
        }

        while (folderQueue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val currentNode = folderQueue.removeFirst()
            if (!visitedDirectoryIds.add(currentNode.documentId)) {
                unreadableBranches++
                emitProgress(currentNode.relativePath)
                continue
            }
            scannedFolders++

            if (currentNode.relativePath.isNotEmpty()) {
                folderBatch.add(
                    DiscoveredFolder(
                        relativePath = currentNode.relativePath,
                        parentRelativePath = currentNode.relativePath.substringBeforeLast('/', "").ifEmpty { null },
                        displayName = currentNode.displayName
                    )
                )
                if (folderBatch.size >= batchSize) {
                    onFolderBatch(folderBatch.toList())
                    folderBatch.clear()
                }
            }

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentNode.documentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            try {
                val cursor = resolver.query(childrenUri, projection, null, null, null)
                    ?: throw IllegalStateException("Document provider returned no cursor")
                cursor.use {
                    val idCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modifiedCol = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (idCol < 0 || nameCol < 0 || mimeCol < 0) {
                        throw IllegalStateException("Document provider omitted required columns")
                    }

                    while (it.moveToNext()) {
                        coroutineContext.ensureActive()
                        inspectedDocuments++
                        val childDocId = it.getString(idCol)
                        val displayName = it.getString(nameCol)
                        val mimeType = it.getString(mimeCol).orEmpty()
                        if (childDocId.isNullOrBlank() || displayName.isNullOrBlank()) {
                            unsupportedDocuments++
                            continue
                        }
                        val size = if (sizeCol < 0 || it.isNull(sizeCol)) null else it.getLong(sizeCol)
                        val modified = if (modifiedCol < 0 || it.isNull(modifiedCol)) null else it.getLong(modifiedCol)
                        val relativePath = if (currentNode.relativePath.isEmpty()) {
                            displayName
                        } else {
                            "${currentNode.relativePath}/$displayName"
                        }

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            folderQueue.add(DirectoryNode(childDocId, relativePath, displayName))
                            continue
                        }

                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        if (AudioAdmissionPolicy.isSupported(displayName, mimeType, size)) {
                            mediaBatch.add(
                                DiscoveredFile(
                                    documentUri = fileUri,
                                    documentId = childDocId,
                                    relativePath = relativePath,
                                    filename = displayName,
                                    mimeType = mimeType,
                                    size = size ?: -1L,
                                    modifiedTimeMs = modified ?: -1L
                                )
                            )
                            admittedAudio++
                            if (mediaBatch.size >= batchSize) {
                                onMediaBatch(mediaBatch.toList())
                                mediaBatch.clear()
                            }
                        } else if (isArtworkCandidate(displayName)) {
                            artworkCandidates++
                            onArtworkCandidate(
                                ArtworkCandidate(fileUri, relativePath, displayName, size, modified)
                            )
                        } else {
                            unsupportedDocuments++
                        }
                    }
                }
            } catch (error: SecurityException) {
                if (currentNode.relativePath.isEmpty()) throw error
                unreadableBranches++
            } catch (error: Exception) {
                if (currentNode.relativePath.isEmpty()) throw error
                unreadableBranches++
            }
            emitProgress(currentNode.relativePath)
        }

        if (folderBatch.isNotEmpty()) onFolderBatch(folderBatch.toList())
        if (mediaBatch.isNotEmpty()) onMediaBatch(mediaBatch.toList())
        emitProgress("")
    }

    fun isSupportedAudio(filename: String, mimeType: String, size: Long? = null): Boolean =
        AudioAdmissionPolicy.isSupported(filename, mimeType, size)

    private fun isArtworkCandidate(filename: String): Boolean {
        val extension = filename.substringAfterLast('.', "").lowercase()
        val stem = filename.substringBeforeLast('.', filename).trim().lowercase()
        return extension in artworkExtensions && stem in artworkNames
    }
}
