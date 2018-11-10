package com.github.kxfeng.logs

object Util {
    val LINE_REGEX = "\r?\n".toRegex()
    val LINE_SEPARATOR = System.getProperty("line.separator") ?: "\n"
}