package com.yunx.app.data.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class DownloadSaverCopyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun copyStopsBetweenBuffersWhenLeaseIsInvalidated() {
        val source = ByteArray(12) { it.toByte() }
        val target = ByteArrayOutputStream()
        var checks = 0

        assertThrows(CancellationException::class.java) {
            DownloadSaver.copyCancellable(
                ByteArrayInputStream(source),
                target,
                shouldContinue = { ++checks < 3 },
                bufferSize = 4
            )
        }

        assertEquals(4, target.size())
    }

    @Test
    fun completeCopyReportsExactByteCount() {
        val source = ByteArray(17) { (it * 3).toByte() }
        val target = ByteArrayOutputStream()

        val copied = DownloadSaver.copyCancellable(
            ByteArrayInputStream(source), target, shouldContinue = { true }, bufferSize = 5
        )

        assertEquals(source.size.toLong(), copied)
        assertArrayEquals(source, target.toByteArray())
    }

    @Test
    fun concurrentSameNameClaimsAreAtomicAndNeverShareAFile() {
        val root = temporaryFolder.newFolder("legacy-downloads")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val futures = (1..2).map {
            pool.submit<java.io.File?> {
                ready.countDown()
                start.await()
                DownloadSaver.claimLegacyDestination(
                    root,
                    root,
                    listOf("same-name.mp4", "same-name-fallback.mp4")
                )
            }
        }
        ready.await()
        start.countDown()
        val claimed = futures.map { requireNotNull(it.get()) }
        pool.shutdownNow()

        assertEquals(2, claimed.distinctBy { it.canonicalPath }.size)
        claimed.forEach { org.junit.Assert.assertTrue(it.isFile) }
    }
}
