package com.github.kxfeng.logs

interface StringPrinter {
    fun print(level: Int, tag: String, message: String)
}