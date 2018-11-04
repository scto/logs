package com.github.kxfeng.logs

import java.io.*
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * LogFile is a multi-thread and multi-process safe class for writing content to log. It's thread and process safe in
 * log path level, not just in instance level.
 *
 * For example, you can create one instance to record log and another
 * instance to archive log:
 * ```
 * Thread1 :
 * val writeInstance = LogFile("/tmp/logs/", "log", 1024*1024, 5)
 * writeInstance.write("xxx")
 *
 * Thread2 :
 * val archiveInstance = LogFile("/tmp/logs/", "log", 1024*1024, 5)
 * val archiveFiles = archiveInstance.archive()
 * archiveInstance.close()
 * ```
 *
 * @param directory The directory to save log file. This class relies on the path of [directory] and [name] to ensure
 *                   thread and process safety, so it's better use absolute path here to prevent conflict, you can use
 *                   relative path unless you are sure there won't exist conflict.
 * @param name The log name
 * @param maxSize The maximum number of bytes to write to one file.
 *                 If this value is not greater than zero, file size will not be limited.
 * @param maxCount The maximum log file maxCount. If count of log files reach this limit, oldest log file will be deleted.
 */
class LogFile(
    private val directory: String,
    private val name: String,
    private var maxSize: Long,
    private val maxCount: Int
) : AutoCloseable {

    init {
        if (maxSize <= 0) maxSize = Long.MAX_VALUE
        if (maxCount <= 0) throw IllegalArgumentException("maxCount should be greater than 0")
    }

    private val lockFilePath: String = File(directory, getFileName(EXT_LOCK)).path
    @Volatile
    private var lockFileChannel: FileChannel? = null

    // Saved open file info to prevent open/close stream every write operation
    @Volatile
    private var openFileIndex: Int? = null
    @Volatile
    private var openFileStream: OutputStream? = null

    companion object {
        private const val EXT_LOCK = ".lock"
        private const val EXT_LOG = ".log"
        private const val EXT_ARCHIVE = ".bak"

        private val THREAD_LOCK_MAP: ConcurrentHashMap<String, Lock?> = ConcurrentHashMap()

        private val FILE_SORT_COMPARATOR = Comparator<File> { o1, o2 ->
            if (o1 == null || o2 == null) {
                return@Comparator (if (o1 == null) 0 else 1) - (if (o2 == null) 0 else 1)
            }
            if (o1.name.length != o2.name.length) {
                return@Comparator o1.name.length - o2.name.length
            }
            return@Comparator o1.name.compareTo(o2.name)
        }

        // Log or archive file name is in this format: xxx.bak1, xxx.log2, ...
        private fun isValidFileName(fileName: String, baseName: String): Boolean {
            if (!fileName.startsWith(baseName)) return false
            val endStr = fileName.substring(baseName.length)

            when (endStr.length) {
                0 -> {
                    return false
                }
                else -> {
                    for (i in 0 until endStr.length) {
                        val ch = endStr[i]
                        if (ch < '0' || ch > '9') return false
                        if (i == 0 && ch == '0') return false
                    }
                    return true
                }
            }
        }
    }

    /**
     * Write content to the log
     *
     * @throws IOException Some I/O error occurs
     */
    fun write(content: ByteArray) {
        // Lock to prevent doing operation concurrently in different thread of current process
        val threadLock = getThreadLock(lockFilePath)
        threadLock.lock()

        var exception: Throwable? = null
        var fileLock: FileLock? = null
        try {
            // Request exclusive file lock to prevent doing operation concurrently in multi-process
            fileLock = lockOnFile()

            val fileNameList = File(directory).list() ?: throw IOException("Unable to list files: $directory")

            val archiveRange = findFileIndexRange(fileNameList, getFileName(EXT_ARCHIVE))
            val logRange = findFileIndexRange(fileNameList, getFileName(EXT_LOG))

            var headIndex = Math.max(1, Math.max(logRange.second, archiveRange.second + 1))
            var headFile = File(directory, getFileName(EXT_LOG, headIndex))

            if (headFile.length() >= maxSize) {
                headIndex++
                headFile = File(directory, getFileName(EXT_LOG, headIndex))
            }
            if (headIndex > maxCount) {
                openFileStream.catchClose(null)?.printStackTrace()  // ignore error
                openFileStream = null
                openFileIndex = null

                rotate()
                headIndex = maxCount
                headFile = File(directory, getFileName(EXT_LOG, headIndex))
            }

            var tmpOpenIndex: Int? = openFileIndex
            var tmpOpenStream: OutputStream? = openFileStream

            // If there's no need to change open index, but the open file has been deleted or moved by another programs,
            // we can still write content to open stream, no better way to detect this, so just let it happen.
            if (tmpOpenIndex != headIndex || tmpOpenStream == null) {
                tmpOpenIndex = headIndex
                tmpOpenStream = BufferedOutputStream(FileOutputStream(headFile, true))

                openFileStream.catchClose(null)?.printStackTrace()  // ignore error
                openFileStream = tmpOpenStream
                openFileIndex = tmpOpenIndex
            }

            tmpOpenStream.write(content)
            tmpOpenStream.flush()
        } catch (ex: Throwable) {
            exception = ex
            throw ex
        } finally {
            val newCloseError: Throwable? = fileLock.catchClose(exception)
            threadLock.unlock()
            if (newCloseError != null) throw newCloseError
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
        // Lock to prevent doing operation concurrently in different thread of current process
        val threadLock = getThreadLock(lockFilePath)
        threadLock.lock()

        var exception: Throwable? = null
        var fileLock: FileLock? = null
        try {
            // Request exclusive file lock to prevent doing operation concurrently in multi-process
            fileLock = lockOnFile()

            openFileStream.catchClose(null)?.printStackTrace()  // ignore error
            openFileStream = null
            openFileIndex = null

            val logFiles = File(directory).listFiles(LogFileFilter(getFileName(EXT_LOG), true))
                ?: throw IOException("Unable to list files: $directory")

            for (file in logFiles) {
                // FileFilter ensure the file name is valid
                val index = getFileIndex(file.name, getFileName(EXT_LOG))

                val toFile = File(directory, getFileName(EXT_ARCHIVE, index))
                if (toFile.exists()) {
                    toFile.delete()
                }
                val result = file.renameTo(toFile)

                if (!result) {
                    throw IOException("Rename to archive file failed:${file.name}")
                }
            }

            val archiveFiles = File(directory).listFiles(LogFileFilter(getFileName(EXT_ARCHIVE), true))
                ?: throw IOException("Unable to list files: $directory")

            Arrays.sort(archiveFiles, FILE_SORT_COMPARATOR)

            return archiveFiles
        } catch (ex: Throwable) {
            exception = ex
            throw ex
        } finally {
            val newCloseError = fileLock.catchClose(exception)
            threadLock.unlock()
            if (newCloseError != null) throw newCloseError
        }
    }

    /**
     * Rotate log and archive files to prevent over reach the [maxCount] limit
     */
    private fun rotate() {
        for (ext in arrayOf(EXT_LOG, EXT_ARCHIVE)) {
            var preFile: File? = null

            for (i in 1..maxCount) {
                val curFile = File(directory, getFileName(ext, i))

                if (preFile == null) {
                    if (curFile.exists()) {
                        curFile.delete()
                    }
                } else {
                    if (preFile.exists()) {
                        preFile.delete()
                    }
                    if (curFile.exists()) {
                        curFile.renameTo(preFile)
                    }
                }
                preFile = curFile
            }
        }
    }

    /**
     * Release resources. This method can be called more than once.
     *
     * If the instance is alive as long as the process, it's safe not to close this manually, just let system to release
     * resources when the process exit. But if you create multiple short live instance, you should close them.
     *
     * @throws IOException Some I/O error occurs
     */
    override fun close() {
        val threadLock = getThreadLock(lockFilePath)
        threadLock.lock()

        var newError = openFileStream.catchClose(null)
        newError = lockFileChannel.catchClose(newError)

        openFileIndex = null
        openFileStream = null
        lockFileChannel = null

        threadLock.unlock()
        if (newError != null) throw newError
    }

    /**
     * Lock on file and save the open file channel. This method should be synchronized externally.
     *
     * @throws FileNotFoundException If lock file create failed
     * @throws OverlappingFileLockException See [FileChannel.lock]
     * @throws IOException If some other I/O error occurs
     */
    private fun lockOnFile(): FileLock {
        var channel = lockFileChannel
        // channel closed by system or other programs?
        if (channel == null || !channel.isOpen) {
            val file = File(lockFilePath)
            if (!file.exists()) {
                // ignore result, let RandomAccessFile constructor throw exception if fail
                file.parentFile.mkdirs()
            }
            channel = RandomAccessFile(file, "rw").channel
        }
        lockFileChannel = channel
        return channel!!.lock(0, Long.MAX_VALUE, false)
    }

    private fun getThreadLock(key: String): Lock {
        var lock = THREAD_LOCK_MAP[key]
        if (lock != null) return lock

        synchronized(THREAD_LOCK_MAP) {
            lock = THREAD_LOCK_MAP[key]
            if (lock == null) {
                lock = ReentrantLock()
                THREAD_LOCK_MAP[key] = lock
            }
            return lock!!
        }
    }

    /**
     * Find index range of log files. If no valid file found, return Pair(-1, -1).
     *
     * @throws IOException If failed to list files
     */
    private fun findFileIndexRange(fileNameList: Array<String>, baseName: String): Pair<Int, Int> {
        var min = Int.MAX_VALUE
        var max = -1
        for (fileName in fileNameList) {
            if (isValidFileName(fileName, baseName)) {
                val index = getFileIndex(fileName, baseName)
                min = Math.min(index, min)
                max = Math.max(index, max)
            }
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
    private fun getFileIndex(fileName: String, baseName: String): Int {
        return fileName.substring(baseName.length).toInt()
    }

    /**
     * Verify if the file is a valid log or archive file.
     *
     * @param baseName log or archive base name
     * @param checkIsFile If true, filter file which is not a normal file (see [File.isFile]), this require I/O operation.
     */
    class LogFileFilter(private val baseName: String, private val checkIsFile: Boolean) : FileFilter {
        override fun accept(file: File): Boolean {
            if (checkIsFile) {
                if (!file.isFile) return false
            }
            return isValidFileName(file.name, baseName)
        }
    }
}

/**
 * Close this [AutoCloseable] and return new exception when close it.
 *
 * If [cause] is not null, this method always return null, the closing exception (if exist) will be added to [cause] as suppressed exception.
 * If [cause] is null, return the closing exception if exist, otherwise return null.
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