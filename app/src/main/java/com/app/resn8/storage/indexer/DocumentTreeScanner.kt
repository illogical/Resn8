package com.app.resn8.storage.indexer

import android.content.ContentResolver
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

class DocumentTreeScanner(
    private val context: Context,
    private val batchSize: Int = 100
) {
    private val supportedMimeTypes = setOf(
        "audio/mpeg", "audio/mp4", "audio/aac", "audio/flac", "audio/ogg",
        "audio/x-wav", "audio/wav", "audio/opus", "audio/x-matroska", "audio/m4a"
    )

    private val supportedExtensions = setOf(
        "mp3", "m4a", "aac", "flac", "ogg", "wav", "opus", "oga", "mka", "wma"
    )

    private data class DirectoryNode(
        val documentId: String,
        val relativePath: String,
        val displayName: String
    )

    suspend fun scanTree(
        treeUri: Uri,
        onFolderBatch: suspend (List<DiscoveredFolder>) -> Unit,
        onMediaBatch: suspend (List<DiscoveredFile>) -> Unit,
        onProgressUpdate: (scannedFolders: Int, discoveredMedia: Int, currentPath: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri) ?: return@withContext

        val folderQueue = ArrayDeque<DirectoryNode>()
        folderQueue.add(DirectoryNode(documentId = rootDocId, relativePath = "", displayName = ""))

        val folderBatch = mutableListOf<DiscoveredFolder>()
        val mediaBatch = mutableListOf<DiscoveredFile>()

        var totalScannedFolders = 0
        var totalDiscoveredMedia = 0

        while (folderQueue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val currentNode = folderQueue.poll() ?: break
            totalScannedFolders++

            if (currentNode.relativePath.isNotEmpty()) {
                val parentPath = currentNode.relativePath.substringBeforeLast('/', "").ifEmpty { null }
                folderBatch.add(
                    DiscoveredFolder(
                        relativePath = currentNode.relativePath,
                        parentRelativePath = parentPath,
                        displayName = currentNode.displayName
                    )
                )
                if (folderBatch.size >= batchSize) {
                    onFolderBatch(folderBatch.toList())
                    folderBatch.clear()
                }
            }

            onProgressUpdate(totalScannedFolders, totalDiscoveredMedia, currentNode.relativePath)

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentNode.documentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            try {
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        val childDocId = cursor.getString(idCol) ?: continue
                        val displayName = cursor.getString(nameCol) ?: continue
                        val mimeType = cursor.getString(mimeCol) ?: ""
                        val size = cursor.getLong(sizeCol)
                        val lastModified = cursor.getLong(modCol)

                        if (displayName.startsWith(".")) continue

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            val newRelativePath = if (currentNode.relativePath.isEmpty()) {
                                displayName
                            } else {
                                "${currentNode.relativePath}/$displayName"
                            }
                            folderQueue.add(DirectoryNode(childDocId, newRelativePath, displayName))
                        } else if (isSupportedAudio(displayName, mimeType)) {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                            val relativePath = if (currentNode.relativePath.isEmpty()) {
                                displayName
                            } else {
                                "${currentNode.relativePath}/$displayName"
                            }

                            mediaBatch.add(
                                DiscoveredFile(
                                    documentUri = fileUri,
                                    documentId = childDocId,
                                    relativePath = relativePath,
                                    filename = displayName,
                                    mimeType = mimeType.ifEmpty { "audio/mpeg" },
                                    size = size,
                                    modifiedTimeMs = lastModified
                                )
                            )
                            totalDiscoveredMedia++

                            if (mediaBatch.size >= batchSize) {
                                onMediaBatch(mediaBatch.toList())
                                mediaBatch.clear()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore inaccessible single directory nodes to keep scan resilient
            }
        }

        if (folderBatch.isNotEmpty()) {
            onFolderBatch(folderBatch.toList())
            folderBatch.clear()
        }
        if (mediaBatch.isNotEmpty()) {
            onMediaBatch(mediaBatch.toList())
            mediaBatch.clear()
        }
    }

    fun isSupportedAudio(filename: String, mimeType: String): Boolean {
        if (supportedMimeTypes.contains(mimeType.lowercase())) return true
        val ext = filename.substringAfterLast('.', "").lowercase()
        return supportedExtensions.contains(ext)
    }
}
