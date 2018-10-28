package com.github.kxfeng.logs

import java.io.*
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * LogFile is a multi-thread and multi-process safe class for writing content to log.
 *
 * @param directory The directory (absolute path) to save log file
 * @param name The log name
 * @param limit The maximum number of bytes to write to any one file
 */
class LogFile(
    private val directory: String,
    private val name: String,
    private val limit: Long
) {
    private val lockFilePath: String = File(directory, getFileName(EXT_LOCK)).path
    @Volatile
    private var lockFileChannel: FileChannel? = null

    private val logFileFilter: FileFilter = LogFileFilter(getFileName(EXT_LOG))
    private val archiveFileFilter: FileFilter = LogFileFilter(getFileName(EXT_ARCHIVE))

    // Saved open file info to prevent open/close stream every write operation
    @Volatile
    private var openFileIndex: Int? = null
    @Volatile
    private var openFileStream: OutputStream? = null

    companion object {
        private const val EXT_LOCK = ".lock"
        private const val EXT_LOG = ".log"
        private const val EXT_ARCHIVE = ".archive"
        private val INDEX_REGEX = Regex("\\d|[1-9]\\d")

        private val FILE_SORT_COMPARATOR = Comparator<File> { o1, o2 ->
            if (o1 == null || o2 == null) {
                return@Comparator (if (o1 == null) 0 else 1) - (if (o2 == null) 0 else 1)
            }
            if (o1.name.length != o2.name.length) {
                return@Comparator o1.name.length - o2.name.length
            }
            return@Comparator o1.name.compareTo(o2.name)
        }

        // Map lock file path to the count of threads which are writing to log concurrently
        private val sharedRunningMap: MutableMap<String, Int?> = ConcurrentHashMap()
        // Map lock file path to the shared FileLock of that lock file
        private val sharedFileLockMap: MutableMap<String, FileLock?> = ConcurrentHashMap()
        // Map lock file path to the ReadWriteLock which is used to synchronize thread
        private val readWriteLockMap: MutableMap<String, ReadWriteLock?> = HashMap()

        /**
         * Archive log files based on [directory] and [name]
         * @see [LogFile.archive]
         */
        @JvmStatic
        fun archive(directory: String, name: String): Array<File> {
            return LogFile(directory, name, Long.MAX_VALUE).archive()
        }
    }

    /**
     * Write content to the log
     *
     * @throws IOException Some I/O error occurs
     */
    fun write(content: String) {
        // Lock on read lock, prevent doing archive operation concurrently
        val readLock = getReadWriteLock(lockFilePath).readLock()
        readLock.lock()

        var exception: Throwable? = null
        var fileLock: FileLock? = null
        try {
            // Lock on lockFile path related object, prevent other threads running this block concurrently
            synchronized(readLock) {
                val running: Int = sharedRunningMap[lockFilePath] ?: 0
                if (running == 0) {
                    // Request shared file lock to prevent doing archive operation concurrently in multi-process
                    fileLock = getFileLock(true)
                    sharedFileLockMap[lockFilePath] = fileLock
                } else {
                    fileLock = sharedFileLockMap[lockFilePath]!!
                }
                sharedRunningMap[lockFilePath] = running + 1
            }

            val archiveRange = findFileIndexRange(true)
            val logRange = findFileIndexRange(false)

            var headIndex = Math.max(0, Math.max(logRange.second, archiveRange.second + 1))
            var headFile = File(directory, getFileName(EXT_LOG, headIndex))

            if (headFile.length() > limit) {
                headIndex++
                headFile = File(directory, getFileName(EXT_LOG, headIndex))
            }

            var tmpOpenIndex: Int? = null
            var tmpOpenStream: OutputStream? = null

            synchronized(this) {
                tmpOpenIndex = openFileIndex
                tmpOpenStream = openFileStream
            }
            if (tmpOpenIndex != headIndex) {
                tmpOpenIndex = headIndex
                tmpOpenStream = BufferedOutputStream(FileOutputStream(headFile, true))
            }

            tmpOpenStream!!.write(content.toByteArray())
            tmpOpenStream!!.flush()

            synchronized(this) {
                if (openFileStream != tmpOpenStream) {
                    try {
                        openFileStream?.close()
                    } catch (ex: IOException) {
                        // ignore
                        ex.printStackTrace()
                    }
                    openFileIndex = tmpOpenIndex
                    openFileStream = tmpOpenStream
                }
            }
        } catch (ex: Throwable) {
            exception = ex
            throw ex
        } finally {
            var newCloseError: Throwable? = null

            synchronized(readLock) {
                val running: Int = sharedRunningMap[lockFilePath]!!
                sharedRunningMap[lockFilePath] = running - 1
                if (running == 1) {
                    newCloseError = fileLock.catchClose(exception)
                    sharedFileLockMap.remove(lockFilePath)
                }
            }

            readLock.unlock()
            if (newCloseError != null) throw newCloseError!!
        }
    }

    /**
     * Archive log files. All new content will be written to new created file after this, so it's safe to delete these archived
     * files, for example after you have uploaded them to server.
     *
     * @return All archived files. This array contains newly and previously archived files. Head of the array is the oldest file.
     * @throws IOException Some I/O error occurs
     */
    fun archive(): Array<File> {
        // Lock on write lock to prevent doing write or archive operation concurrently
        val writeLock = getReadWriteLock(lockFilePath).writeLock()
        writeLock.lock()

        var exception: Throwable? = null
        var fileLock: FileLock? = null
        try {
            // Request exclusive file lock to prevent doing write or archive operation concurrently in multi-process
            fileLock = getFileLock(false)

            val logFiles = File(directory).listFiles(logFileFilter)
                ?: throw IOException("Unable to list files: $directory")

            for (file in logFiles) {
                // FileFilter ensure the file name is valid
                val index = getFileIndex(file.name, false)
                val result = file.renameTo(File(directory, getFileName(EXT_ARCHIVE, index)))

                if (!result) {
                    throw IOException("Rename to archive file failed:${file.name}")
                }
            }

            val archiveFiles = File(directory).listFiles(archiveFileFilter)
                ?: throw IOException("Unable to list files: $directory")

            Arrays.sort(archiveFiles, FILE_SORT_COMPARATOR)

            return archiveFiles
        } catch (ex: Throwable) {
            exception = ex
            throw ex
        } finally {
            val newCloseError = fileLock.catchClose(exception)
            writeLock.unlock()
            if (newCloseError != null) throw newCloseError
        }
    }

    /**
     * Release resources
     *
     * @throws IOException Some I/O error occurs
     */
    fun release() {
        openFileStream?.close()
    }

    /**
     * @throws FileNotFoundException If lock file create failed
     * @throws OverlappingFileLockException See [FileChannel.lock]
     * @throws IOException If some other I/O error occurs
     */
    private fun getFileLock(shared: Boolean): FileLock {
        if (lockFileChannel == null) {
            synchronized(this) {
                if (lockFileChannel == null) {
                    val file = File(lockFilePath)
                    if (!file.exists()) {
                        // ignore result, let RandomAccessFile constructor throw exception if fail
                        file.parentFile.mkdirs()
                    }
                    lockFileChannel = RandomAccessFile(file, "rw").channel
                }
            }
        }
        return lockFileChannel!!.lock(0, Long.MAX_VALUE, shared)
    }

    private fun getReadWriteLock(key: String): ReadWriteLock {
        synchronized(readWriteLockMap) {
            var lock = readWriteLockMap[key]
            if (lock == null) {
                lock = ReentrantReadWriteLock()
                readWriteLockMap[key] = lock
            }
            return lock
        }
    }

    /**
     * Find index range of log files. If no files found, return Pair(-1, -1).
     *
     * @throws IOException If failed to list files
     */
    private fun findFileIndexRange(archive: Boolean): Pair<Int, Int> {
        val list = File(directory).listFiles(if (archive) archiveFileFilter else logFileFilter)
            ?: throw IOException("Unable to list files: $directory")

        var min = Int.MAX_VALUE
        var max = -1
        for (file in list) {
            // FileFilter ensure the file name is valid
            val index = getFileIndex(file.name, archive)
            min = Math.min(index, min)
            max = Math.max(index, max)
        }

        return if (max == -1) {
            Pair(-1, -1)
        } else {
            Pair(min, max)
        }
    }

    /**
     * Get file name with extension and index
     */
    private fun getFileName(extension: String, index: Int? = null): String {
        return if (index == null) "$name$extension" else "$name$extension$index"
    }

    /**
     * Get file index form log or archive files.
     *
     * @throws IndexOutOfBoundsException If not a valid log or archive file name
     * @throws NumberFormatException If not a valid log or archive file name
     */
    private fun getFileIndex(fileName: String, archive: Boolean): Int {
        val baseName = getFileName(if (archive) EXT_ARCHIVE else EXT_LOG)
        return fileName.substring(baseName.length).toInt()
    }

    private class LogFileFilter(val baseName: String) : FileFilter {
        override fun accept(file: File): Boolean {
            if (!file.isFile) return false
            val fileName = file.name
            if (!fileName.startsWith(baseName)) return false
            val endStr = fileName.substring(baseName.length)
            return endStr.matches(LogFile.INDEX_REGEX)
        }
    }
}

/**
 * Close this [AutoCloseable]. If [cause] is not null, will return null, but exception which is thrown when closing
 * will be added to [cause] as suppressed exception. If [cause] is null, return null when close without exception,
 * otherwise return the close exception.
 */
private fun AutoCloseable?.catchClose(cause: Throwable?): Throwable? = when {
    this == null -> null
    cause == null -> {
        try {
            close()
            null
        } catch (closeException: Throwable) {
            closeException
        }
    }
    else -> {
        try {
            close()
        } catch (closeException: Throwable) {
            cause.addSuppressed(closeException)
        }
        null
    }
}