package com.github.kxfeng.logs

import android.util.Log

class AndroidPrinter : StringPrinter {

    override fun print(level: Int, tag: String, message: String) {
        // In Android Studio, if log content has multiple lines, the fist line will not align start to others,
        // this will lead to ugly text layout sometimes, e.g. when log content has a border.
        // We just add a blank line instead of logging line by line, because performance may be better.
        var fixContent: String = message
        if (message.contains("\n")) {
            fixContent = " \n$fixContent"
        }
        Log.println(logcatPriority(level), tag, fixContent)
    }

    private fun logcatPriority(level: Int): Int {
        return when {
            level <= LogLevel.VERBOSE -> Log.VERBOSE
            level == LogLevel.DEBUG -> Log.DEBUG
            level == LogLevel.INFO -> Log.INFO
            level == LogLevel.WARN -> Log.WARN
            level == LogLevel.ERROR -> Log.ERROR
            else -> Log.ERROR
        }
    }
}