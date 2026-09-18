package com.izavo.app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingMotionTimingTest {
    @Test fun greetingHoldRemainsExactlyTwoSeconds() {
        assertEquals(2_000L, ONBOARDING_GREETING_HOLD_MILLIS)
    }

    @Test fun homeTransformationIsExactlyThirtyFiveHundredMilliseconds() {
        assertEquals(3_500, ONBOARDING_HOME_TRANSFORM_MILLIS)
    }

    @Test fun liquidProgressIsBoundedAndFinishesExactlyAtOne() {
        assertEquals(0f, liquidProgress(-1f), 0f)
        assertEquals(1f, liquidProgress(1f), 0f)
        assertEquals(1f, liquidProgress(2f), 0f)
        var previous = 0f
        (0..100).forEach { step ->
            val current = liquidProgress(step / 100f)
            org.junit.Assert.assertTrue(current in previous..1f)
            previous = current
        }
    }

    @Test fun finalCoordinateEqualsMeasuredTargetWithoutOvershoot() {
        assertEquals(83.25f, interpolatedCoordinate(500f, 83.25f, 1f), 0f)
        assertEquals(83.25f, interpolatedCoordinate(500f, 83.25f, 2f), 0f)
    }

    @Test fun transitionGreetingUsesStableNonEllipsizingLayout() {
        assertEquals(2, TRANSITION_GREETING_MAX_LINES)
        org.junit.Assert.assertFalse(TRANSITION_GREETING_USES_ELLIPSIS)
    }

    @Test fun motionSpendsMoreTravelInTheSecondHalf() {
        val halfway = liquidProgress(.5f)
        org.junit.Assert.assertTrue(halfway < .5f)
        org.junit.Assert.assertTrue(1f - halfway > halfway)
    }
}
