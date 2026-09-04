package com.fuke.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRangePolicyTest {
    @Test
    fun parsesValidRange() {
        assertEquals(ContentRangePolicy.Range(100, 199, 1000), ContentRangePolicy.parse("bytes 100-199/1000"))
    }

    @Test
    fun rejectsWrongStartOverlongEndAndChangedTotal() {
        assertFalse(ContentRangePolicy.matches("bytes 99-199/1000", 100, 199, 1000))
        assertFalse(ContentRangePolicy.matches("bytes 100-200/1000", 100, 199, 1000))
        assertFalse(ContentRangePolicy.matches("bytes 100-199/999", 100, 199, 1000))
        assertTrue(ContentRangePolicy.matches("bytes 100-150/1000", 100, 199, 1000))
    }

    @Test
    fun rejectsMalformedOrImpossibleRanges() {
        assertNull(ContentRangePolicy.parse(null))
        assertNull(ContentRangePolicy.parse("bytes */1000"))
        assertNull(ContentRangePolicy.parse("bytes 200-100/1000"))
        assertNull(ContentRangePolicy.parse("bytes 0-1000/1000"))
    }

    @Test
    fun wholeFileRequiresZeroThroughTotalMinusOne() {
        assertTrue(ContentRangePolicy.isWholeFile("bytes 0-999/1000"))
        assertFalse(ContentRangePolicy.isWholeFile("bytes 0-499/1000"))
    }
}
