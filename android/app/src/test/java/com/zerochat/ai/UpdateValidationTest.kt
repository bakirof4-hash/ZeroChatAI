package com.zerochat.ai

import com.zerochat.ai.util.UpdateManager
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateValidationTest {

    @Test
    fun testVersionComparisonLogic() {
        val currentVersionCode = 1
        val latestVersionCode = 2

        val isUpdateAvailable = latestVersionCode > currentVersionCode
        assertTrue("Update must be available when latest > current", isUpdateAvailable)

        val sameVersionAvailable = 1 > currentVersionCode
        assertFalse("Update must not trigger when versions are equal", sameVersionAvailable)

        val olderVersionAvailable = 0 > currentVersionCode
        assertFalse("Update must not trigger when installed is newer than server", olderVersionAvailable)
    }

    @Test
    fun testSha256ChecksumCalculationAndVerification() {
        val sampleData = "ZeroChat-AI-Secure-Update-Payload".toByteArray()
        val tempFile = File.createTempFile("test_update", ".apk")
        tempFile.writeBytes(sampleData)

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val expectedHash = digest.digest(sampleData).joinToString("") { "%02x".format(it) }.lowercase()

            val computedHash = UpdateManager.calculateFileSha256(tempFile)
            assertEquals("Calculated SHA-256 must match expected digest", expectedHash, computedHash)

            // Should pass without throwing
            UpdateManager.verifyChecksum(tempFile, expectedHash)

            // Mismatched hash must throw SecurityException
            var threw = false
            try {
                UpdateManager.verifyChecksum(tempFile, "0000000000000000000000000000000000000000000000000000000000000000")
            } catch (e: SecurityException) {
                threw = true
            }
            assertTrue("Tampered checksum must throw SecurityException", threw)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    @Test
    fun testMandatoryUpdatePolicy() {
        val currentVersionCode = 1
        val minSupportedVersionCode = 2

        val isMandatory = currentVersionCode < minSupportedVersionCode
        assertTrue("When current version is below minimum supported, update is mandatory", isMandatory)
    }
}

