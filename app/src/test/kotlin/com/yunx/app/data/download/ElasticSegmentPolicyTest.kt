package com.yunx.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ElasticSegmentPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun incompleteAndMalformedSegmentsAreDeletedBeforeResumeBytesAreCounted() {
        val directory = temporaryFolder.newFolder("chunks")
        val complete = File(directory, "seg_100_109.part").apply { writeBytes(ByteArray(10)) }
        val incomplete = File(directory, "seg_110_119.part").apply { writeBytes(ByteArray(4)) }
        val oversized = File(directory, "seg_120_129.part").apply { writeBytes(ByteArray(11)) }
        val outside = File(directory, "seg_90_99.part").apply { writeBytes(ByteArray(10)) }
        val malformed = File(directory, "seg_bad.part").apply { writeBytes(ByteArray(99)) }

        val valid = ElasticSegmentPolicy.prepare(directory, elasticStart = 100, total = 200)

        assertEquals(listOf(complete), valid.map { it.file })
        assertEquals(10L, valid.sumOf { it.size })
        assertTrue(complete.exists())
        listOf(incomplete, oversized, outside, malformed).forEach { assertFalse(it.exists()) }
    }

    @Test
    fun secondPreparationCannotCountDeletedPartialBytesAgain() {
        val directory = temporaryFolder.newFolder("chunks")
        File(directory, "seg_100_109.part").writeBytes(ByteArray(6))

        assertEquals(0L, ElasticSegmentPolicy.prepare(directory, 100, 200).sumOf { it.size })
        assertEquals(0L, ElasticSegmentPolicy.prepare(directory, 100, 200).sumOf { it.size })
    }
}
