package com.izavo.app.ui.theme

import org.junit.Assert.*
import org.junit.Test

class ThemeRevealCoordinatorTest {
    @Test fun rapidSecondTapIsIgnoredUntilCompletion() {
        val coordinator = ThemeRevealCoordinator()
        assertTrue(coordinator.tryStart())
        assertFalse(coordinator.tryStart())
        assertTrue(coordinator.isRunning)
    }

    @Test fun revealCanStartAgainAfterCompletion() {
        val coordinator = ThemeRevealCoordinator()
        coordinator.tryStart()
        coordinator.complete()
        assertFalse(coordinator.isRunning)
        assertTrue(coordinator.tryStart())
    }
}
