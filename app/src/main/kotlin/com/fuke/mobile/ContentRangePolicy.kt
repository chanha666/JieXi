package com.fuke.mobile

/** Strict RFC 7233 Content-Range validation for resumable direct downloads. */
internal object ContentRangePolicy {
    data class Range(val start: Long, val end: Long, val total: Long) {
        val length: Long get() = end - start + 1L
    }

    fun parse(header: String?): Range? {
        val match = PATTERN.matchEntire(header?.trim().orEmpty()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        if (start < 0 || end < start || total <= 0 || end >= total) return null
        return Range(start, end, total)
    }

    fun matches(
        header: String?,
        requestedStart: Long,
        requestedEnd: Long?,
        expectedTotal: Long?
    ): Boolean {
        val range = parse(header) ?: return false
        if (range.start != requestedStart) return false
        if (requestedEnd != null && range.end > requestedEnd) return false
        if (expectedTotal != null && range.total != expectedTotal) return false
        return true
    }

    fun isWholeFile(header: String?): Boolean {
        val range = parse(header) ?: return false
        return range.start == 0L && range.end == range.total - 1L
    }

    private val PATTERN = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
}
