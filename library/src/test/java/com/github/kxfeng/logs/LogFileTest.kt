package com.github.kxfeng.logs

import org.junit.Assert
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val LOG_DIR = "test_logs"

class LogFileTest {

    /**
     * @throws IOException
     */
    @Test
    fun testSingleThread() {
        val logName = "test_log_testSingleThread_${System.currentTimeMillis()}"
        val logFile = LogFile(LOG_DIR, logName, 128 * 1024, 3)

        for (page in 1..2) {
            for (i in 1..(16 * 1024)) {
                logFile.write("${page}23456${i % 8}\n".toByteArray())
            }
        }

        val archiveFiles = logFile.archive()

        Assert.assertEquals(2, archiveFiles.size)

        for (i in 0 until archiveFiles.size) {
            val file = archiveFiles[i]
            val lines = file.readLines()

            Assert.assertEquals(16 * 1024, lines.size)
            Assert.assertEquals("${i + 1}234561", lines[0])
            Assert.assertEquals("${i + 1}234562", lines[1])
            Assert.assertEquals("${i + 1}234567", lines[1022])
            Assert.assertEquals("${i + 1}234560", lines[1023])

            Assert.assertTrue(file.delete())
        }

        logFile.close()
    }

    /**
     * @throws IOException
     */
    @Test
    fun testMultiThread() {
        val logName = "test_log_testMultiThread_${System.currentTimeMillis()}"
        val countDownLatch = CountDownLatch(4)

        for (i in 1..4) {
            thread(start = true) {
                val logFile = LogFile(LOG_DIR, logName, 128 * 1024, 5)

                for (j in 1..(16 * 1024)) {
                    logFile.write("${i}23456${j % 2}\n".toByteArray())
                }

                logFile.close()
                countDownLatch.countDown()
            }
        }

        Assert.assertTrue(countDownLatch.await(10, TimeUnit.SECONDS))

        val logFile = LogFile(LOG_DIR, logName, 128 * 1024, 5)
        val archiveFiles = logFile.archive()

        Assert.assertEquals(4, archiveFiles.size)

        for (file in archiveFiles) {
            val lines = file.readLines()
            Assert.assertEquals(16 * 1024, lines.size)

            Assert.assertTrue(file.delete())
        }

        logFile.close()
    }

    /**
     * @throws IOException
     */
    @Test
    fun testRotate() {
        val logName = "test_log_testRotate_${System.currentTimeMillis()}"
        val logFile = LogFile(LOG_DIR, logName, 128 * 1024, 3)

        for (page in 1..3) {
            for (i in 1..(16 * 1024)) {
                logFile.write("${page}23456${i % 8}\n".toByteArray())
            }
        }
        for (i in 1..7) {
            logFile.write("${4}23456${i % 8}\n".toByteArray())
        }

        val archiveFiles = logFile.archive()

        Assert.assertEquals(3, archiveFiles.size)

        val line1 = archiveFiles[0].readLines()
        Assert.assertEquals(16 * 1024, line1.size)
        Assert.assertEquals("${2}234561", line1[0])

        val line2 = archiveFiles[1].readLines()
        Assert.assertEquals(16 * 1024, line2.size)
        Assert.assertEquals("${3}234561", line2[0])

        val line3 = archiveFiles[2].readLines()
        Assert.assertEquals(7, line3.size)
        Assert.assertEquals("${4}234561", line3[0])
        Assert.assertEquals("${4}234567", line3[6])

        Assert.assertTrue(archiveFiles[0].delete())
        Assert.assertTrue(archiveFiles[1].delete())
        Assert.assertTrue(archiveFiles[2].delete())

        logFile.close()
    }

    /**
     * @throws IOException
     */
    @Test
    fun testMultiThreadRotate() {
        val logName = "test_log_testMultiThreadRotate_${System.currentTimeMillis()}"

        val countDownLatch = CountDownLatch(2)

        val notify = Object()

        thread(start = true) {
            val logFile = LogFile(LOG_DIR, logName, 128 * 1024, 3)

            for (page in 1..2) {
                for (i in 1..(16 * 1024)) {
                    logFile.write("${page}23456${i % 8}\n".toByteArray())

                    if (page == 1 && i == 1024) {
                        synchronized(notify) {
                            notify.notifyAll()
                        }
                    }
                }
            }

            logFile.close()
            countDownLatch.countDown()
        }

        thread(start = true) {
            val logFile = LogFile(LOG_DIR, logName, 128 * 1024, 3)

            synchronized(notify) {
                notify.wait(10_000)
            }

            logFile.archive()
            logFile.close()
            countDownLatch.countDown()
        }

        Assert.assertTrue(countDownLatch.await(10, TimeUnit.SECONDS))

        val logFile = LogFile(LOG_DIR, logName, 128 * 1024, 3)
        val archiveFiles = logFile.archive()
        logFile.close()

        Assert.assertEquals(3, archiveFiles.size)

        var totalCount = 0
        for (file in archiveFiles) {
            totalCount += file.readLines().size
            Assert.assertTrue(file.delete())
        }

        Assert.assertEquals(16 * 1024 * 2, totalCount)
    }
}