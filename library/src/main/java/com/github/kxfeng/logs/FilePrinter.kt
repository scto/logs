package com.github.kxfeng.logs

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque

class FilePrinter(private val logFile: LogFile) : StringPrinter {
    private var worker = Worker(this)

    override fun print(level: Int, tag: String, message: String) {
        worker.start()
        worker.enqueue(LogItem(level, tag, message))
    }

    private fun printInWorker(level: Int, tag: String, message: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val head = "${sdf.format(Date())} ${levelText(level)}/$tag: "
        val spaceHead = " ".repeat(head.length)

        val lines = message.split(Util.LINE_REGEX)
        val sb = StringBuilder()

        for (line in lines) {
            sb.append(if (sb.isEmpty()) head else spaceHead)
            sb.append(line).append(Util.LINE_SEPARATOR)
        }

        try {
            logFile.write(sb.toString())
        } catch (ex: IOException) {
            // ignore
            ex.printStackTrace()
        }
    }

    private fun levelText(level: Int): String {
        return when {
            level < LogLevel.VERBOSE -> "V-${LogLevel.VERBOSE - level}"
            level == LogLevel.VERBOSE -> "V"
            level == LogLevel.DEBUG -> "D"
            level == LogLevel.INFO -> "I"
            level == LogLevel.WARN -> "W"
            level == LogLevel.ERROR -> "E"
            else -> "E+${level - LogLevel.ERROR}"
        }
    }

    private class Worker(private val printer: FilePrinter) : Runnable {
        private val blockingQueue: BlockingDeque<LogItem> = LinkedBlockingDeque<LogItem>()
        private var started: Boolean = false

        fun enqueue(logItem: LogItem) {
            val result = blockingQueue.offer(logItem)
            if (!result) {
                // ignore
                IllegalStateException("FilePrinter offer log item failed").printStackTrace()
            }
        }

        @Synchronized
        fun start() {
            if (!started) {
                Thread(this).start()
                started = true
            }
        }

        override fun run() {
            try {
                do {
                    val logItem: LogItem = blockingQueue.take()
                    printer.printInWorker(logItem.level, logItem.tag, logItem.message)
                } while (true)
            } catch (ex: InterruptedException) {
                synchronized(this) {
                    started = false
                }
            }
        }
    }

    private class LogItem(val level: Int, val tag: String, val message: String)
}