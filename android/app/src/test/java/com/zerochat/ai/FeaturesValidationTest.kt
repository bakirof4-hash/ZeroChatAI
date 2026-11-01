package com.zerochat.ai

import com.zerochat.ai.data.model.FileInspectionResponse
import com.zerochat.ai.data.model.InspectionFinding
import org.junit.Assert.*
import org.junit.Test
import java.util.regex.Pattern

class FeaturesValidationTest {

    @Test
    fun testUsernameFormatValidation() {
        val usernameRegex = Regex("^[a-zA-Z0-9_]{3,30}$")

        assertTrue(usernameRegex.matches("ZeroFounder"))
        assertTrue(usernameRegex.matches("alice_123"))
        assertTrue(usernameRegex.matches("bob"))
        assertFalse(usernameRegex.matches("ab"))          // too short (< 3)
        assertFalse(usernameRegex.matches("user@name"))   // invalid char @
        assertFalse(usernameRegex.matches("user space"))  // space not allowed
        assertFalse(usernameRegex.matches("user!name"))   // exclamation mark
    }

    @Test
    fun testQrPayloadParsing() {
        val deepLink = "zerochat://user?u=ZeroFounder&uid=1"
        val pattern = Pattern.compile("zerochat://user\\?u=([^&]+)(?:&uid=(\\d+))?")
        val matcher = pattern.matcher(deepLink)

        assertTrue("Matcher must find valid deep link", matcher.find())
        assertEquals("ZeroFounder", matcher.group(1))
        assertEquals("1", matcher.group(2))

        val invalidLink = "https://example.com/not-a-qr"
        val invalidMatcher = pattern.matcher(invalidLink)
        assertFalse("Invalid links must not match", invalidMatcher.find())
    }

    @Test
    fun testFileSecurityModelEvaluation() {
        val findings = listOf(
            InspectionFinding(severity = "HIGH", message = "android.permission.SYSTEM_ALERT_WINDOW detected"),
            InspectionFinding(severity = "HIGH", message = "android.permission.SEND_SMS detected")
        )

        val inspection = FileInspectionResponse(
            filename = "sample.apk",
            fileType = "Android Application Package (APK)",
            fileSize = 1024L,
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            threatLevel = "HIGH",
            riskScore = 40,
            findings = findings,
            disclaimer = "Heuristic static analysis inspects structural headers without executing code."
        )

        assertEquals("HIGH", inspection.threatLevel)
        assertEquals(40, inspection.riskScore)
        assertEquals(2, inspection.findings.size)
        assertTrue(inspection.disclaimer.contains("Heuristic static analysis"))
    }

    @Test
    fun testNetworkErrorFormatting() {
        val connectEx = java.net.ConnectException("Failed to connect to /10.0.2.2:8000")
        val formatted = com.zerochat.ai.data.api.ApiClient.formatNetworkError(connectEx)
        assertTrue(
            "Formatted error should guide user regarding 10.0.2.2 on physical device",
            formatted.contains("10.0.2.2") || formatted.contains("backend")
        )

        val timeoutEx = java.net.SocketTimeoutException("timeout")
        val timeoutFormatted = com.zerochat.ai.data.api.ApiClient.formatNetworkError(timeoutEx)
        assertTrue("Formatted error should mention timeout", timeoutFormatted.contains("timed out"))
    }

    @Test
    fun testConversationModelAndOnlinePresence() {
        val user = com.zerochat.ai.data.model.UserDto(
            id = 42,
            email = "bakirof4@gmail.com",
            username = "BakirofFounder",
            role = "SUPER_ADMIN",
            isFounder = true,
            isVerifiedBadge = true,
            isActive = true,
            emailVerified = true,
            createdAt = "2026-11-01T12:00:00Z"
        )
        assertTrue("Founder must have isSuperAdmin", user.isSuperAdmin)
        assertTrue("Founder must have isAdmin", user.isAdmin)

        val conv = com.zerochat.ai.data.model.ConversationDto(
            conversationId = "direct_42",
            isGroup = false,
            title = "BakirofFounder",
            recipientUser = user,
            lastMessage = "Hey there, ZeroChat is ready!",
            lastMessageTime = "2026-11-01T15:30:00Z",
            unreadCount = 2,
            isOnline = true
        )
        assertEquals("direct_42", conv.conversationId)
        assertFalse(conv.isGroup)
        assertTrue(conv.isOnline)
        assertEquals(2, conv.unreadCount)
        assertEquals("BakirofFounder", conv.recipientUser?.username)
        assertTrue(conv.recipientUser?.isVerifiedBadge == true)
    }
}
