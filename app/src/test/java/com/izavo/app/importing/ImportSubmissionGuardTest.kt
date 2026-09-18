package com.izavo.app.importing

import org.junit.Assert.*
import org.junit.Test

class ImportSubmissionGuardTest {
    @Test fun rapidSecondSubmissionIsRejected() {
        val guard = ImportSubmissionGuard()
        assertTrue(guard.tryStart())
        assertFalse(guard.tryStart())
    }

    @Test fun submissionCanRetryAfterCompletionOrHandledError() {
        val guard = ImportSubmissionGuard()
        assertTrue(guard.tryStart())
        guard.finish()
        assertTrue(guard.tryStart())
    }
}
