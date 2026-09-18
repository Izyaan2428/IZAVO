package com.izavo.app.preferences

import org.junit.Assert.*
import org.junit.Test

class DisplayNameTest {
    @Test fun trimsWhitespace() { assertEquals("Adam", normalizeDisplayName("  Adam  ")) }
    @Test fun preservesUnicode() { assertEquals("އާދަމް 日本", normalizeDisplayName(" އާދަމް 日本 ")) }
    @Test fun blankNameIsEmpty() { assertEquals("", normalizeDisplayName("   \t")) }
    @Test fun limitsByCodepointWithoutBreakingEmoji() { val name = normalizeDisplayName("😀".repeat(45)); assertEquals(40, name.codePointCount(0, name.length)); assertTrue(name.endsWith("😀")) }
    @Test fun morningBoundaries() { assertEquals("Good morning", homeGreeting(5)); assertEquals("Good morning", homeGreeting(11)) }
    @Test fun afternoonBoundaries() { assertEquals("Good afternoon", homeGreeting(12)); assertEquals("Good afternoon", homeGreeting(16)) }
    @Test fun eveningBoundaries() { listOf(17,23,0,4).forEach { assertEquals("Good evening", homeGreeting(it)) } }
    @Test fun greetingWithName() { assertEquals("Good afternoon, Adam", homeGreeting(12, " Adam ")) }
    @Test fun greetingWithoutNameHasNoComma() { assertEquals("Good afternoon", homeGreeting(12, "  ")) }
    @Test fun greetingDoesNotUppercaseName() { assertEquals("Good morning, އާދަމް", homeGreeting(7, "އާދަމް")) }
}
