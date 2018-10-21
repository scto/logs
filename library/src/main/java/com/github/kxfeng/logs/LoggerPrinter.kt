package com.github.kxfeng.logs

interface LoggerPrinter {

    fun isLoggable(level: Int, tag: String): Boolean

    /**
     * @param level log level
     * @param tag  log tag
     * @param stackTrace  stack trace for this log event. Item at lower index is the latest stack trace element.
     * @param message log message
     * @param throwable a throwable to be logged
     *
     * [message] and [throwable] will not be null at same time
     */
    fun print(
            level: Int,
            tag: String,
            stackTrace: Array<StackTraceElement>?,
            message: String?,
            throwable: Throwable?
    )
}