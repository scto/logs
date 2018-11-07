package com.github.kxfeng.sample.logs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.github.kxfeng.logs.LogFile
import com.github.kxfeng.logs.LoggerPrinter
import com.github.kxfeng.logs.Logs
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread

private const val TEST_LOG_FILE = "app_logs_test_LogFile"
private const val TAG = "Logs"
private const val LOG_COUNT = 5
private const val LOG_SIZE = 256 * 1024L

private const val REQUEST_CODE_WRITE_PERMISSION = 0

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btn_logfile_write.setOnClickListener {
            withPermission {
                testLogFileWrite(it, getWriteCount())
            }
        }

        btn_logfile_archive.setOnClickListener {
            withPermission {
                thread(start = true) {
                    logFileArchive(it)
                }
            }
        }

        btn_logfile_archive_merge.setOnClickListener {
            withPermission {
                testLogFileArchiveAndMerge(it)
            }
        }

        btn_logger_performance.setOnClickListener {
            testLoggerPerformance(getWriteCount())
        }

        check_multi_process.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showResult("Multi process log directory:\n${getLogDirectory(true)}")
            } else {
                showResult("Single process log directory:\n${getLogDirectory(false)}")
            }
        }
    }

    private fun getLogDirectory(multiProcess: Boolean): String {
        return if (multiProcess) {
            File(Environment.getExternalStorageDirectory(), "logs").path
        } else {
            getExternalFilesDir("logs")!!.path
        }
    }

    private fun getWriteCount(): Int {
        return try {
            val value = edit_write_count.text.toString().toInt()
            if (value > 0) value else 10000
        } catch (ex: NumberFormatException) {
            10000
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_WRITE_PERMISSION) {
            if (grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission is not granted", Toast.LENGTH_SHORT).show()
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun withPermission(block: (directory: String) -> Unit) {
        if (!check_multi_process.isChecked) {
            block(getLogDirectory(false))
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            block(getLogDirectory(true))
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_WRITE_PERMISSION
            )
        }
    }

    private fun testLogFileWrite(directory: String, count: Int) {
        thread(start = true) {
            val start = System.nanoTime()

            val logFile = LogFile(directory, TEST_LOG_FILE, LOG_SIZE, LOG_COUNT)

            for (i in 1..count) {
                logFile.write("Index: $i\n".toByteArray())
            }

            logFile.close()

            val time = System.nanoTime() - start
            val result = "LogFile write $count: ${time / 1000_000}ms"
            Log.i(TAG, result)
            runOnUiThread { showResult(result) }
        }

        thread(start = true) {
            val start = System.nanoTime()
            val file = File(directory, "app_logs_test_OutputStream")

            file.parentFile.mkdirs()

            val outStream = BufferedOutputStream(FileOutputStream(file, true))

            for (i in 1..count) {
                outStream.write("Index: $i\n".toByteArray())
                outStream.flush()
            }
            outStream.close()

            val time = System.nanoTime() - start
            val result = "OutputStream write $count: ${time / 1000_000}ms"
            Log.i(TAG, result)
            runOnUiThread { showResult(result) }
        }
    }

    private fun testLogFileArchiveAndMerge(directory: String) {
        thread(start = true) {
            val archiveFiles = logFileArchive(directory)

            val start = System.nanoTime()

            val outputFile = File(directory, "merged.log")

            val outputStream =
                BufferedOutputStream(FileOutputStream(outputFile, false))

            val byteBuffer = ByteArray(8 * 1024)
            for (file in archiveFiles) {

                val inputStream = FileInputStream(file)
                var read = inputStream.read(byteBuffer)

                while (read >= 0) {
                    outputStream.write(byteBuffer, 0, read)
                    read = inputStream.read(byteBuffer)
                }

                inputStream.close()
                file.delete()
            }

            outputStream.close()

            val time = System.nanoTime() - start

            val mergeLines = outputFile.readLines().size

            val result = "ArchiveFiles merge by stream: ${time / 1000_000}ms, lines=$mergeLines"
            Log.i(TAG, result)
            runOnUiThread { showResult(result) }
        }
    }

    private fun logFileArchive(directory: String): Array<File> {
        val start = System.nanoTime()

        val logFile = LogFile(directory, TEST_LOG_FILE, LOG_SIZE, LOG_COUNT)
        val archiveFiles = logFile.archive()
        logFile.close()
        val sb = StringBuilder()
        for (file in archiveFiles) {
            sb.append(file.name).append(", ")
        }

        val time = System.nanoTime() - start
        val result = "ArchiveFiles: ${time / 1000_000}ms, list=$sb"
        Log.i(TAG, result)
        runOnUiThread { showResult(result) }
        return archiveFiles
    }

    private fun testLoggerPerformance(count: Int) {
        Logs.clearPrinters()
        Logs.addPrinters(object : LoggerPrinter {
            override fun isLoggable(level: Int, tag: String): Boolean {
                return true
            }

            override fun print(
                level: Int,
                tag: String,
                stackTrace: Array<StackTraceElement>?,
                message: String?,
                throwable: Throwable?
            ) {
                // do nothing
            }
        })

        thread(start = true) {
            val start = System.nanoTime()

            for (i in 1..count) {
                Logs.i("Index $i")
            }

            val time = System.nanoTime() - start
            val result = "Logs write $count: ${time / 1000_000}ms"
            Log.i(TAG, result)
            runOnUiThread { showResult(result) }
        }
    }

    private fun showResult(text: String) {
        tv_text.append("$text\n")
    }
}

