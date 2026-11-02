package com.zerochat.ai

import com.zerochat.ai.data.model.UserDto
import org.junit.Assert.*
import org.junit.Test
import java.util.regex.Pattern

class AuthValidationTest {

    private val emailPattern = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}$"
    )

    @Test
    fun testValidEmailFormat() {
        assertTrue(emailPattern.matcher("founder@zerochat.ai").matches())
        assertTrue(emailPattern.matcher("user.test+tag@gmail.com").matches())
        assertFalse(emailPattern.matcher("invalid-email").matches())
        assertFalse(emailPattern.matcher("missing@domain").matches())
        assertFalse(emailPattern.matcher("@nodomain.com").matches())
    }

    @Test
    fun testSixDigitCodeValidation() {
        val codeRegex = Regex("^\\d{6}$")
        assertTrue(codeRegex.matches("123456"))
        assertTrue(codeRegex.matches("999999"))
        assertFalse(codeRegex.matches("12345"))  // 5 digits
        assertFalse(codeRegex.matches("1234567")) // 7 digits
        assertFalse(codeRegex.matches("12a456"))  // non-digit
    }

    @Test
    fun testPasswordStrengthLogic() {
        fun isStrong(pwd: String): Boolean {
            return pwd.length >= 8 &&
                    pwd.any { it.isUpperCase() } &&
                    pwd.any { it.isLowerCase() } &&
                    pwd.any { it.isDigit() }
        }

        assertTrue(isStrong("SuperSecure123!"))
        assertTrue(isStrong("Founder@2026"))
        assertFalse(isStrong("short1!"))      // < 8 chars
        assertFalse(isStrong("alllowercase1")) // missing uppercase
        assertFalse(isStrong("ALLUPPERCASE1")) // missing lowercase
        assertFalse(isStrong("NoDigitsHere!")) // missing digit
    }

    @Test
    fun testFounderAndSuperAdminRolePrivileges() {
        val founder = UserDto(
            id = 1,
            email = "founder@zerochat.ai",
            username = "ChiefFounder",
            role = "SUPER_ADMIN",
            isFounder = true,
            isVerifiedBadge = true,
            isActive = true,
            emailVerified = true,
            createdAt = "2026-10-28T00:00:00Z"
        )

        assertTrue("Founder must have isFounder=true", founder.isFounder)
        assertTrue("Founder must be verified", founder.isVerifiedBadge)
        assertTrue("Founder must be super admin", founder.isSuperAdmin)
        assertTrue("Founder must have admin privileges", founder.isAdmin)

        val regularUser = UserDto(
            id = 2,
            email = "regular@example.com",
            username = "RegularGuy",
            role = "USER",
            isFounder = false,
            isVerifiedBadge = false,
            isActive = true,
            emailVerified = true,
            createdAt = "2026-10-28T00:00:00Z"
        )

        assertFalse("Regular user must NOT be founder", regularUser.isFounder)
        assertFalse("Regular user must NOT have badge by default", regularUser.isVerifiedBadge)
        assertFalse("Regular user must NOT be super admin", regularUser.isSuperAdmin)
        assertFalse("Regular user must NOT be admin", regularUser.isAdmin)
    }

    @Test
    fun testGoogleAuthRequestAndUserDtoMapping() {
        val googleReq = com.zerochat.ai.data.model.GoogleAuthRequest(
            idToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.dummyGoogleIdToken12345"
        )
        assertEquals("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.dummyGoogleIdToken12345", googleReq.idToken)

        val googleUser = UserDto(
            id = 42,
            email = "bakirof4@gmail.com",
            username = "bakirof4",
            role = "SUPER_ADMIN",
            isFounder = true,
            isVerifiedBadge = true,
            isActive = true,
            emailVerified = true,
            createdAt = "2026-11-01T20:00:00Z",
            googleId = "google_id_1098234",
            fullName = "Umidjon Bakirov",
            avatarUrl = "https://lh3.googleusercontent.com/a/dummy"
        )

        assertEquals("google_id_1098234", googleUser.googleId)
        assertEquals("Umidjon Bakirov", googleUser.fullName)
        assertEquals("https://lh3.googleusercontent.com/a/dummy", googleUser.avatarUrl)
        assertTrue(googleUser.isSuperAdmin)
        assertTrue(googleUser.isFounder)
        assertTrue(googleUser.isVerifiedBadge)
    }

    @Test
    fun testPasswordlessLoginRequestModel() {
        val req = com.zerochat.ai.data.model.PasswordlessLoginRequest(
            email = "user@example.com",
            verificationToken = "sample_verification_token_12345"
        )
        assertEquals("user@example.com", req.email)
        assertEquals("sample_verification_token_12345", req.verificationToken)
    }
}

