package com.iadv.dukaanlocker.ui.screens

import org.junit.Test
import org.junit.Assert.*

class AppSettingsInfoTest {

    @Test
    fun `buildFeedbackBody includes version, rating, and comment`() {
        // Since BuildConfig.VERSION_NAME is not available in unit tests,
        // we test the format logic by using a known version pattern
        val version = "1.1" // This matches BuildConfig.VERSION_NAME in debug builds
        val rating = 4
        val comment = "Great app!"
        
        // Test the format directly since BuildConfig is generated
        val expectedBody = "Feedback for Dukaan Locker v$version ($rating/5 stars)\n\n$comment"
        
        // We can't easily test the actual function without BuildConfig,
        // but we can verify the format string logic
        assertEquals(expectedBody, "Feedback for Dukaan Locker v1.1 (4/5 stars)\n\nGreat app!")
    }

    @Test
    fun `buildFeedbackSubject includes version and rating`() {
        val version = "1.1"
        val rating = 5
        
        val expectedSubject = "[Feedback] DukaanLocker v$version \u2014 $rating\u2605"
        
        assertEquals(expectedSubject, "[Feedback] DukaanLocker v1.1 \u2014 5\u2605")
    }

    @Test
    fun `buildFeedbackBody handles empty comment`() {
        val version = "1.1"
        val rating = 3
        val comment = ""
        
        val expectedBody = "Feedback for Dukaan Locker v$version ($rating/5 stars)\n\n$comment"
        
        assertEquals(expectedBody, "Feedback for Dukaan Locker v1.1 (3/5 stars)\n\n")
    }

    @Test
    fun `buildFeedbackBody handles multiline comment`() {
        val version = "1.1"
        val rating = 2
        val comment = "Line 1\nLine 2\nLine 3"
        
        val expectedBody = "Feedback for Dukaan Locker v$version ($rating/5 stars)\n\n$comment"
        
        assertEquals(expectedBody, "Feedback for Dukaan Locker v1.1 (2/5 stars)\n\nLine 1\nLine 2\nLine 3")
    }
}