package com.izavo.app.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryCompositionGeometryTest {
    @Test fun emptyAndNonPositiveValuesProduceNoSegments() {
        assertTrue(categoryCompositionSegments(emptyList()).isEmpty())
        assertTrue(categoryCompositionSegments(listOf(0L, -4L, 0L)).isEmpty())
    }

    @Test fun oneValueFillsTheWholeTrack() {
        val segment = categoryCompositionSegments(listOf(25L)).single()
        assertEquals(0f, segment.startFraction, 0f)
        assertEquals(1f, segment.endFraction, 0f)
    }

    @Test fun tinyAndDominantValuesRemainExactOrderedAndBounded() {
        val segments = categoryCompositionSegments(listOf(1L, 999_998L, 1L))
        assertEquals(3, segments.size)
        assertEquals(0f, segments.first().startFraction, 0f)
        assertEquals(1f, segments.last().endFraction, 0f)
        segments.zipWithNext().forEach { (left, right) ->
            assertEquals(left.endFraction, right.startFraction, 0f)
        }
        segments.forEach {
            assertTrue(it.startFraction.isFinite())
            assertTrue(it.endFraction.isFinite())
            assertTrue(it.startFraction in 0f..1f)
            assertTrue(it.endFraction in 0f..1f)
            assertTrue(it.endFraction >= it.startFraction)
        }
    }

    @Test fun equalValuesPartitionWithoutOverlap() {
        val segments = categoryCompositionSegments(List(6) { 10L })
        assertEquals(6, segments.size)
        assertEquals(1f, segments.sumOf { (it.endFraction - it.startFraction).toDouble() }.toFloat(), .00001f)
    }

    @Test fun largeValuesDoNotOverflowOrProduceInvalidBounds() {
        val segments = categoryCompositionSegments(listOf(Long.MAX_VALUE, Long.MAX_VALUE, 1L))
        assertEquals(3, segments.size)
        assertEquals(1f, segments.last().endFraction, 0f)
        assertTrue(segments.all { it.startFraction.isFinite() && it.endFraction.isFinite() })
    }
}
