package com.iadv.dukaanlocker.ui.screens

import androidx.compose.ui.graphics.Color

enum class PasswordStrength(val label: String, val color: Color, val level: Int) {
    NONE("Enter a password", Color.Gray, 0),
    WEAK("Weak", Color(0xFFEF4444), 1),
    FAIR("Fair", Color(0xFFF59E0B), 2),
    GOOD("Good", Color(0xFF06B6D4), 3),
    STRONG("Strong", Color(0xFF22C55E), 4)
}

internal fun evaluatePasswordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) return PasswordStrength.NONE
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isLowerCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when {
        score <= 1 -> PasswordStrength.WEAK
        score <= 2 -> PasswordStrength.FAIR
        score <= 3 -> PasswordStrength.GOOD
        else -> PasswordStrength.STRONG
    }
}

internal fun isValidEmail(email: String): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

internal val msmeRegex = Regex("^UDYAM-[A-Z]{2}-\\d{2}-\\d{7}$", RegexOption.IGNORE_CASE)

internal fun isValidMsme(number: String): Boolean = msmeRegex.matches(number.trim())

private val captchaChars = ('A'..'Z') + ('0'..'9')

internal fun generateCaptcha(length: Int = 5): String {
    val random = java.util.Random()
    return (1..length).map { captchaChars[random.nextInt(captchaChars.size)] }.joinToString("")
}
