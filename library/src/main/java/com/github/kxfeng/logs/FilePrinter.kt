package com.github.kxfeng.logs

import android.util.Log

class FilePrinter : StringPrinter {
    override fun print(level: Int, tag: String, message: String) {
        Log.d(tag, "TODO: FilePrinter, level=$level")
    }
}