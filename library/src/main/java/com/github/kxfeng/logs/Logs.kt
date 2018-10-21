package com.github.kxfeng.logs

/**
 * This class consists of static methods which is backed by a global [Logger] instance.
 * And it support to create independent [Logger] instance.
 */
class Logs {

    companion object {
        private val global: Logger by lazy { Logger() }

        @JvmStatic
        fun asInstance(): Logger {
            return global
        }

        @JvmStatic
        fun newInstance(): Logger {
            return Logger()
        }

        @JvmStatic
        fun addPrinters(vararg printers: LoggerPrinter): Logger {
            return global.addPrinters(*printers)
        }

        @JvmStatic
        fun clearPrinters(): Logger {
            return global.clearPrinters()
        }

        @JvmStatic
        fun defaultConfig(tag: String, methodCount: Int): Logger {
            return global.defaultConfig(tag, methodCount)
        }

        @JvmStatic
        fun tag(tag: String): Logger {
            return global.tag(tag)
        }

        @JvmStatic
        fun config(tag: String, stackCount: Int): Logger {
            return global.config(tag, stackCount)
        }

        @JvmStatic
        fun v(message: String, vararg args: Any?) {
            global.v(message, *args)
        }

        @JvmStatic
        fun v(throwable: Throwable, message: String, vararg args: Any?) {
            global.v(throwable, message, *args)
        }

        @JvmStatic
        fun v(throwable: Throwable) {
            global.v(throwable)
        }

        @JvmStatic
        fun d(message: String, vararg args: Any?) {
            global.d(message, *args)
        }

        @JvmStatic
        fun d(throwable: Throwable, message: String, vararg args: Any?) {
            global.d(throwable, message, *args)
        }

        @JvmStatic
        fun d(throwable: Throwable) {
            global.d(throwable)
        }

        @JvmStatic
        fun i(message: String, vararg args: Any?) {
            global.i(message, *args)
        }

        @JvmStatic
        fun i(throwable: Throwable, message: String, vararg args: Any?) {
            global.i(throwable, message, *args)
        }

        @JvmStatic
        fun i(throwable: Throwable) {
            global.i(throwable)
        }

        @JvmStatic
        fun w(message: String, vararg args: Any?) {
            global.w(message, *args)
        }

        @JvmStatic
        fun w(throwable: Throwable, message: String, vararg args: Any?) {
            global.w(throwable, message, *args)
        }

        @JvmStatic
        fun w(throwable: Throwable) {
            global.w(throwable)
        }

        @JvmStatic
        fun e(message: String, vararg args: Any?) {
            global.e(message, *args)
        }

        @JvmStatic
        fun e(throwable: Throwable, message: String, vararg args: Any?) {
            global.e(throwable, message, *args)
        }

        @JvmStatic
        fun e(throwable: Throwable) {
            global.e(throwable)
        }

        @JvmStatic
        fun wtf(message: String, vararg args: Any?) {
            global.wtf(message, *args)
        }

        @JvmStatic
        fun wtf(throwable: Throwable, message: String, vararg args: Any?) {
            global.wtf(throwable, message, *args)
        }

        @JvmStatic
        fun wtf(throwable: Throwable) {
            global.wtf(throwable)
        }

        @JvmStatic
        fun log(level: Int, message: String, vararg args: Any?) {
            global.log(level, message, *args)
        }

        @JvmStatic
        fun log(level: Int, throwable: Throwable, message: String, vararg args: Any?) {
            global.log(level, throwable, message, *args)
        }

        @JvmStatic
        fun log(level: Int, throwable: Throwable) {
            global.log(level, throwable)
        }
    }
}