package com.github.kxfeng.logs

import org.junit.Assert
import org.junit.Test
import java.io.IOException

class LoggerTest {

    @Test
    fun testDefaultConfig() {
        var callCount = 0

        Logs.defaultConfig("TAG1", 0)
        Logs.clearPrinters()
        Logs.addPrinters(object : LoggerPrinter {
            override fun isLoggable(level: Int, tag: String): Boolean {
                return true
            }

            override fun print(
                level: Int,
                tag: String,
                stackTrace: Array<StackTraceElement>?,
                message: String?,
                throwable: Throwable?
            ) {
                callCount += 1

                when (callCount) {
                    1 -> {
                        Assert.assertEquals(LogLevel.INFO, level)
                        Assert.assertEquals("TAG1", tag)
                        Assert.assertEquals("Test", message)
                        Assert.assertNull(stackTrace)
                        Assert.assertNull(throwable)
                    }
                    2 -> {
                        Assert.assertEquals(LogLevel.WARN, level)
                        Assert.assertEquals("TAG2", tag)
                        Assert.assertEquals(2, stackTrace!!.size)
                        Assert.assertNull(message)
                        Assert.assertTrue(throwable is IOException)
                    }
                }
            }
        })

        Logs.i("Test")
        Assert.assertEquals(1, callCount)

        Logs.defaultConfig("TAG2", 2)
        Logs.w(IOException())

        Assert.assertEquals(2, callCount)
    }

    @Test
    fun testChangeConfig() {
        var callCount = 0

        Logs.defaultConfig("TAG", 0)
        Logs.clearPrinters()
        Logs.addPrinters(object : LoggerPrinter {
            override fun isLoggable(level: Int, tag: String): Boolean {
                return true
            }

            override fun print(
                level: Int,
                tag: String,
                stackTrace: Array<StackTraceElement>?,
                message: String?,
                throwable: Throwable?
            ) {
                callCount++
                Assert.assertEquals("TAG1", tag)
                Assert.assertEquals(3, stackTrace!!.size)
            }
        })

        Logs.config("TAG1", 3).d("Test")
    }

    @Test
    fun testMessageFormat() {
        var callCount = 0

        Logs.defaultConfig("TAG", 0)
        Logs.clearPrinters()
        Logs.addPrinters(object : LoggerPrinter {
            override fun isLoggable(level: Int, tag: String): Boolean {
                return true
            }

            override fun print(
                level: Int,
                tag: String,
                stackTrace: Array<StackTraceElement>?,
                message: String?,
                throwable: Throwable?
            ) {
                callCount++

                when (callCount) {
                    1 -> {
                        Assert.assertEquals("Hello: %s", message)
                    }
                    2 -> {
                        Assert.assertEquals("Hello: 2", message)
                    }
                    3 -> {
                        Assert.assertEquals("Hello: 3", message)
                    }
                    4 -> {
                        Assert.assertEquals("Hello: 1 2 3 4", message)
                    }
                }
            }
        })

        Logs.d("Hello: %s")
        Logs.d("Hello: %s", 2)
        Logs.d("Hello: %s", Supplier { 3 })
        Logs.d("Hello: %s %s %s %s", 1, 2, Supplier { 3 }, 4)

        Assert.assertEquals(4, callCount)
    }

    @Test
    fun testSupplierCalledOnce() {
        var callCount = 0

        Logs.defaultConfig("TAG", 0)
        Logs.clearPrinters()
        Logs.addPrinters(
            object : LoggerPrinter {
                override fun isLoggable(level: Int, tag: String): Boolean {
                    return true
                }

                override fun print(
                    level: Int,
                    tag: String,
                    stackTrace: Array<StackTraceElement>?,
                    message: String?,
                    throwable: Throwable?
                ) {
                    callCount++

                    Assert.assertEquals("Hello: 1 supplier", message)

                }
            },
            object : LoggerPrinter {
                override fun isLoggable(level: Int, tag: String): Boolean {
                    return true
                }

                override fun print(
                    level: Int,
                    tag: String,
                    stackTrace: Array<StackTraceElement>?,
                    message: String?,
                    throwable: Throwable?
                ) {
                    // do nothing
                }
            }
        )

        var supplierCalled = 0
        val supplier = Supplier {
            supplierCalled++
            return@Supplier "supplier"
        }

        Logs.d("Hello: %s %s", 1, supplier)

        Assert.assertEquals(1, callCount)
        Assert.assertEquals(1, supplierCalled)
    }

    @Test
    fun testSupplierNotCalled() {
        var callCount = 0

        Logs.defaultConfig("TAG", 0)
        Logs.clearPrinters()
        Logs.addPrinters(
            object : LoggerPrinter {
                override fun isLoggable(level: Int, tag: String): Boolean {
                    return false
                }

                override fun print(
                    level: Int,
                    tag: String,
                    stackTrace: Array<StackTraceElement>?,
                    message: String?,
                    throwable: Throwable?
                ) {
                    callCount++
                }
            },
            object : LoggerPrinter {
                override fun isLoggable(level: Int, tag: String): Boolean {
                    return false
                }

                override fun print(
                    level: Int,
                    tag: String,
                    stackTrace: Array<StackTraceElement>?,
                    message: String?,
                    throwable: Throwable?
                ) {
                    callCount++
                }
            }
        )

        var supplierCalled = 0
        val supplier = Supplier {
            supplierCalled++
            return@Supplier "supplier"
        }

        Logs.d("Hello: %s %s", 1, supplier)

        Assert.assertEquals(0, callCount)
        Assert.assertEquals(0, supplierCalled)
    }
}