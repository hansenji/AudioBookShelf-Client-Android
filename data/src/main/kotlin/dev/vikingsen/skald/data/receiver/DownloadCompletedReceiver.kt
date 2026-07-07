package dev.vikingsen.skald.data.receiver

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dev.vikingsen.skald.core.database.AppDatabaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class DownloadCompletedReceiver : BroadcastReceiver(), KoinComponent {
    private val dbProvider: AppDatabaseProvider by inject()
    private val db get() = dbProvider.database

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId == -1L) return

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)

            runCatching {
                downloadManager.query(query).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusColumn)

                        val uriColumn = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
                        val remoteUriString = cursor.getString(uriColumn)

                        val localUriColumn = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val localUriString = cursor.getString(localUriColumn)

                        if (!remoteUriString.isNullOrEmpty()) {
                            // Match: /api/items/([^/]+)/file/([^/]+)/download
                            val regex = Regex(".*/api/items/([^/]+)/file/([^/]+)/download.*")
                            val match = regex.matchEntire(remoteUriString)
                            if (match != null) {
                                val bookId = match.groupValues[1]
                                val ino = match.groupValues[2]

                                if (status == DownloadManager.STATUS_SUCCESSFUL && !localUriString.isNullOrEmpty()) {
                                    val localPath = Uri.parse(localUriString).path
                                    if (localPath != null) {
                                        val tmpFile = File(localPath)
                                        if (tmpFile.exists()) {
                                            val finalPath = if (localPath.endsWith(".tmp")) {
                                                localPath.substringBeforeLast(".tmp")
                                            } else {
                                                localPath
                                            }
                                            val finalFile = File(finalPath)
                                            var moveSuccessful = false
                                            try {
                                                if (finalFile.exists()) {
                                                    finalFile.delete()
                                                }
                                                moveSuccessful = tmpFile.renameTo(finalFile)
                                            } catch (e: Exception) {
                                                Log.e("DownloadCompleted", "Failed to rename temp download file", e)
                                            }

                                            if (moveSuccessful) {
                                                downloadManager.remove(downloadId)
                                                updateDatabase(bookId, ino, "COMPLETED", finalPath)
                                            } else {
                                                updateDatabase(bookId, ino, "COMPLETED", localPath)
                                            }
                                        } else {
                                            updateDatabase(bookId, ino, "COMPLETED", localPath)
                                        }
                                    }
                                } else if (status == DownloadManager.STATUS_FAILED) {
                                    updateDatabase(bookId, ino, "NOT_DOWNLOADED", null)
                                }
                            }
                        }
                    }
                }
            }.onFailure {
                Log.e("DownloadCompleted", "Error processing download completion", it)
            }
        }
    }

    private fun updateDatabase(bookId: String, ino: String, status: String, localPath: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            val bookDao = db.bookDao()
            val book = bookDao.getBookById(bookId) ?: return@launch

            val updatedFiles = book.audioFiles.map {
                if (it.ino == ino) {
                    it.copy(localPath = localPath, downloadStatus = status)
                } else {
                    it
                }
            }
            val isAllDownloaded = updatedFiles.all { it.downloadStatus == "COMPLETED" }
            bookDao.insertBook(book.copy(audioFiles = updatedFiles, isDownloaded = isAllDownloaded))
            Log.d("DownloadCompleted", "Updated file $ino of book $bookId status to $status in database")
        }
    }
}
