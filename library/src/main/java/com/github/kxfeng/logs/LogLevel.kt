package com.github.kxfeng.logs

class LogLevel {

    companion object {
        /**
         * Level constant for the println method; use Logger.v.
         */
        const val VERBOSE = 0

        /**
         * Level constant for the println method; use Logger.d.
         */
        const val DEBUG = 1

        /**
         * Level constant for the println method; use Logger.i.
         */
        const val INFO = 2

        /**
         * Level constant for the println method; use Logger.w.
         */
        const val WARN = 3

        /**
         * Level constant for the println method; use Logger.e.
         */
        const val ERROR = 4

        /**
         * Log level for comparison, use this to print all logs.
         */
        const val ALL = Int.MIN_VALUE

        /**
         * Log level for comparison, use this to disable logs.
         */
        const val NONE = Int.MAX_VALUE
    }
}