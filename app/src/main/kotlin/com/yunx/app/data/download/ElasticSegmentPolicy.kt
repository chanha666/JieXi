package com.yunx.app.data.download

import java.io.File

/** Validates persisted elastic segments before they are counted as resumed bytes. */
internal object ElasticSegmentPolicy {
    data class CompleteSegment(val file: File, val start: Long, val end: Long) {
        val size: Long get() = end - start + 1L
    }

    fun prepare(directory: File, elasticStart: Long, total: Long): List<CompleteSegment> {
        val candidates = directory.listFiles { file ->
            file.name.startsWith("seg_") && file.name.endsWith(".part")
        }.orEmpty()
        return candidates.mapNotNull { file ->
            val match = NAME.matchEntire(file.name)
            val start = match?.groupValues?.getOrNull(1)?.toLongOrNull()
            val end = match?.groupValues?.getOrNull(2)?.toLongOrNull()
            val validRange = start != null && end != null &&
                start >= elasticStart && end >= start && end < total
            val expected = if (validRange) end!! - start!! + 1L else -1L
            if (!validRange || file.length() != expected) {
                file.delete()
                null
            } else {
                CompleteSegment(file, start!!, end!!)
            }
        }.sortedBy { it.start }
    }
}

private val NAME = Regex("seg_(\\d+)_(\\d+)\\.part")
