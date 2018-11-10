package com.github.kxfeng.logs

import java.io.PrintWriter
import java.io.StringWriter

/**
 * A logger printer which create log content with pretty format, the final content will passed to the [printer].
 *
 * @property level log will be disabled when it's level is less than this value
 * @property border add border to log content
 * @property threadInfo add thread info to log content
 * @property printer the string printer
 *
 * If show border, the log will show as bellow format
 * ┌──────────────────────────
 * │ Thread: thread-name
 * ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
 * │ Method stack trace
 * ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
 * │ Log message
 * └──────────────────────────
 */
class PrettyFormatPrinter(
    private val level: Int = LogLevel.ALL,
    private val border: Boolean = true,
    private val threadInfo: Boolean = true,
    private val printer: StringPrinter
) : LoggerPrinter {

    override fun isLoggable(level: Int, tag: String): Boolean {
        return level >= this.level
    }

    override fun print(
        level: Int,
        tag: String,
        stackTrace: Array<StackTraceElement>?,
        message: String?,
        throwable: Throwable?
    ) {
        val threadTitle = if (threadInfo) "Thread: ${Thread.currentThread().name}" else null
        val stackText = if (stackTrace.isNullOrEmpty()) null else getStackTraceString(stackTrace)

        val finalMessage = (message ?: "") +
            (if (message != null && throwable != null) ": " else "") +
            (if (throwable != null) getThrowableStackTraceString(throwable) else "")

        val fullResult: String = if (border) {
            mergePartWithBorder(threadTitle, stackText, finalMessage)
        } else {
            mergePart(threadTitle, stackText, finalMessage)
        }

        printer.print(level, tag, fullResult)
    }

    private fun mergePart(vararg items: String?): String {
        val sb = StringBuilder()
        for (it in items) {
            if (it == null) continue
            if (sb.isNotEmpty()) {
                sb.append(Util.LINE_SEPARATOR)
            }
            sb.append(it)
        }
        return sb.toString()
    }

    private fun mergePartWithBorder(vararg items: String?): String {
        val sb = StringBuilder()
        sb.append(TOP_LEFT_CORNER).append(DOUBLE_DIVIDER).append(Util.LINE_SEPARATOR)
        var partCount = 0
        for (it in items) {
            if (it == null) continue

            if (partCount > 0) {
                sb.append(MIDDLE_CORNER).append(SINGLE_DIVIDER).append(Util.LINE_SEPARATOR)
            }

            val lines = it.split(Util.LINE_REGEX)

            for (ln in lines) {
                sb.append(HORIZONTAL_LINE).append(" ").append(ln).append(Util.LINE_SEPARATOR)
            }

            partCount++
        }
        sb.append(BOTTOM_LEFT_CORNER).append(DOUBLE_DIVIDER)
        return sb.toString()
    }

    companion object {
        private const val TOP_LEFT_CORNER = '┌'
        private const val BOTTOM_LEFT_CORNER = '└'
        private const val MIDDLE_CORNER = '├'
        private const val HORIZONTAL_LINE = '│'
        private const val DOUBLE_DIVIDER = "────────────────────────────────────────────────────────"
        private const val SINGLE_DIVIDER = "┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄"

        fun getStackTraceString(stacks: Array<StackTraceElement>): String {
            val result = StringBuilder()
            val offset = StringBuilder()
            for (i in stacks.size - 1 downTo 0) {
                if (!result.isEmpty())
                    result.append(Util.LINE_SEPARATOR).append(offset)
                result.append(stacks[i].toString())
                offset.append("  ")
            }
            return result.toString()
        }

        fun getThrowableStackTraceString(throwable: Throwable): String {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            pw.flush()
            val result = sw.toString()
            return when {
                result.endsWith("\n") -> result.substring(0, result.length - 1)
                result.endsWith("\r\n") -> result.substring(0, result.length - 2)
                else -> result
            }
        }
    }

    class Builder {
        private var level: Int = LogLevel.VERBOSE
        private var border: Boolean = true
        private var threadInfo: Boolean = true
        private var printer: StringPrinter? = null

        fun level(level: Int): Builder {
            this.level = level
            return this
        }

        fun border(border: Boolean): Builder {
            this.border = border
            return this
        }

        fun threadInfo(threadInfo: Boolean): Builder {
            this.threadInfo = threadInfo
            return this
        }

        fun printer(printer: StringPrinter): Builder {
            this.printer = printer
            return this
        }

        fun build(): PrettyFormatPrinter {
            if (printer == null) throw IllegalArgumentException("Printer is null")
            return PrettyFormatPrinter(level = level, border = border, threadInfo = threadInfo, printer = printer!!)
        }
    }
}