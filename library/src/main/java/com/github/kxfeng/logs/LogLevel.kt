package com.github.kxfeng.logs

class LogLevel {

    companion object {
        /**
         * Level constant for the println method; use Logger.v.
         */
        @JvmField
        val VERBOSE = 0

        /**
         * Level constant for the println method; use Logger.d.
         */
        @JvmField
        val DEBUG = 1

        /**
         * Level constant for the println method; use Logger.i.
         */
        @JvmField
        val INFO = 2

        /**
         * Level constant for the println method; use Logger.w.
         */
        @JvmField
        val WARN = 3

        /**
         * Level constant for the println method; use Logger.e.
         */
        @JvmField
        val ERROR = 4

        /**
         * Minimum log level , just a alias for [Int.MIN_VALUE]
         */
        @JvmField
        val MIN = Int.MIN_VALUE

        /**
         * Maximum log level, just a alias for [Int.MAX_VALUE]
         */
        @JvmField
        val MAX = Int.MAX_VALUE
    }
}