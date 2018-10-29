package com.github.kxfeng.logs

/**
 * Logger is log class which support multiple logger printer.
 *
 * [Supplier] object is supported in parameter args of these method:
 * Logger.v(message, args),
 * Logger.v(throwable, message, args),
 * Logger.i(message, args)
 * ...
 */
class Logger internal constructor() {

    private val printerList: ArrayList<LoggerPrinter> = ArrayList()
    private var printerArray: Array<LoggerPrinter> = emptyArray()

    private val localTag = ThreadLocal<String>()
    private val localMethodCount = ThreadLocal<Int>()

    private var defaultTag: String = "Logs"
    private var defaultMethodCount: Int = 0

    fun addPrinters(vararg printers: LoggerPrinter): Logger {
        synchronized(printerList) {
            printerList.addAll(printers)
            printerArray = printerList.toTypedArray()
        }
        return this
    }

    fun clearPrinters(): Logger {
        synchronized(printerList) {
            printerList.clear()
            printerArray = emptyArray()
        }
        return this
    }

    /**
     * [tag] default tag for all log
     * [methodCount] default method stack trace count: -1 for as many as possible, 0 for none.
     */
    fun defaultConfig(tag: String, methodCount: Int): Logger {
        defaultTag = tag
        defaultMethodCount = methodCount
        return this
    }

    fun tag(tag: String): Logger {
        localTag.set(tag)
        return this
    }

    fun config(tag: String, methodCount: Int): Logger {
        localTag.set(tag)
        localMethodCount.set(methodCount)
        return this
    }

    private fun <T> popLocalValue(local: ThreadLocal<T>, defaultValue: T): T {
        val value = local.get() ?: return defaultValue
        local.set(null)
        return value
    }

    fun v(message: String, vararg args: Any?) {
        prepareLog(LogLevel.VERBOSE, null, message, args)
    }

    fun v(throwable: Throwable, message: String, vararg args: Any?) {
        prepareLog(LogLevel.VERBOSE, throwable, message, args)
    }

    fun v(throwable: Throwable) {
        prepareLog(LogLevel.DEBUG, throwable, null, null)
    }

    fun d(message: String, vararg args: Any?) {
        prepareLog(LogLevel.DEBUG, null, message, args)
    }

    fun d(throwable: Throwable, message: String, vararg args: Any?) {
        prepareLog(LogLevel.DEBUG, throwable, message, args)
    }

    fun d(throwable: Throwable) {
        prepareLog(LogLevel.INFO, throwable, null, null)
    }

    fun i(message: String, vararg args: Any?) {
        prepareLog(LogLevel.INFO, null, message, args)
    }

    fun i(throwable: Throwable, message: String, vararg args: Any?) {
        prepareLog(LogLevel.INFO, throwable, message, args)
    }

    fun i(throwable: Throwable) {
        prepareLog(LogLevel.INFO, throwable, null, null)
    }

    fun w(message: String, vararg args: Any?) {
        prepareLog(LogLevel.WARN, null, message, args)
    }

    fun w(throwable: Throwable, message: String, vararg args: Any?) {
        prepareLog(LogLevel.WARN, throwable, message, args)
    }

    fun w(throwable: Throwable) {
        prepareLog(LogLevel.WARN, throwable, null, null)
    }

    fun e(message: String, vararg args: Any?) {
        prepareLog(LogLevel.ERROR, null, message, args)
    }

    fun e(throwable: Throwable, message: String, vararg args: Any?) {
        prepareLog(LogLevel.ERROR, throwable, message, args)
    }

    fun e(throwable: Throwable) {
        prepareLog(LogLevel.ERROR, throwable, null, null)
    }

    fun wtf(message: String, vararg args: Any?) {
        prepareLog(LogLevel.ERROR, null, message, args)
    }

    fun wtf(throwable: Throwable, message: String, vararg args: Any?) {
        prepareLog(LogLevel.ERROR, throwable, message, args)
    }

    fun wtf(throwable: Throwable) {
        prepareLog(LogLevel.ERROR, throwable, null, null)
    }

    fun log(level: Int, message: String, vararg args: Any?) {
        prepareLog(level, null, message, args)
    }

    fun log(level: Int, throwable: Throwable, message: String, vararg args: Any?) {
        prepareLog(level, throwable, message, args)
    }

    fun log(level: Int, throwable: Throwable) {
        prepareLog(level, throwable, null, null)
    }

    /**
     * This method is synchronized to prevent messy log order for printers
     */
    @Synchronized
    private fun prepareLog(
        level: Int,
        throwable: Throwable?,
        message: String?,
        args: Array<out Any?>?
    ) {
        val tag: String = popLocalValue(localTag, defaultTag)
        val methodCount: Int = popLocalValue(localMethodCount, defaultMethodCount)

        val printers = printerArray

        var finalArgs: Array<out Any?>? = null
        var finalMessage: String? = null
        var methodStacks: Array<StackTraceElement>? = null

        for (printer in printers) {
            if (!printer.isLoggable(level, tag)) continue

            if (finalArgs == null) {
                finalArgs = if (args == null || args.isEmpty()) null else getFinalArgs(args)
                finalMessage = if (message != null && finalArgs != null) message.format(*finalArgs) else message
            }

            if (methodCount != 0 && methodStacks == null) {
                methodStacks = getStackTrace(methodCount)
            }

            printer.print(level, tag, methodStacks, finalMessage, throwable)
        }
    }

    private fun getFinalArgs(args: Array<out Any?>): Array<out Any?> {
        var supplierResult: Array<Any?>? = null

        args.forEachIndexed { index, arg ->
            val finalArg: Any?
            if (arg is Supplier<*>) {
                finalArg = arg.get()

                if (supplierResult == null) {
                    supplierResult = Array(args.size) { null }
                }

            } else {
                finalArg = arg
            }

            if (supplierResult != null) {
                supplierResult!![index] = finalArg
            }
        }

        return supplierResult ?: args
    }


    private fun getStackTrace(methodCount: Int): Array<StackTraceElement> {
        val stacks = Throwable().stackTrace

        var offset = -1
        for (i in MIN_STACK_TRACE_OFFSET until stacks.size) {
            val className = stacks[i].className

            if (className != Logs::class.java.name && className != Logs.Companion::class.java.name) {
                offset = i
                break
            }
        }
        if (offset == -1) {
            offset = stacks.size
        }

        val count = if (methodCount < 0) stacks.size - offset else Math.min(stacks.size - offset, methodCount)

        val result = Array<StackTraceElement?>(count) { null }
        System.arraycopy(stacks, offset, result, 0, count)

        @Suppress("UNCHECKED_CAST")
        return result as Array<StackTraceElement>
    }

    companion object {
        private const val MIN_STACK_TRACE_OFFSET = 3
    }
}