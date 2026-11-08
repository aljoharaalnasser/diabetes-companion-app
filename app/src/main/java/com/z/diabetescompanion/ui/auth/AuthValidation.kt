package com.z.diabetescompanion.ui.auth

/** Pure validation helpers (no Android deps) so they're trivially unit-testable. */
object AuthValidation {

    const val MIN_PASSWORD_LENGTH = 8

    private val emailRegex = Regex("^[A-Za-z0-9+_.'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Email is required."
        !emailRegex.matches(email.trim()) -> "Enter a valid email address."
        else -> null
    }

    /** For sign-in we only check presence; the server decides if it's correct. */
    fun passwordPresenceError(password: String): String? =
        if (password.isEmpty()) "Password is required." else null

    /** For sign-up we enforce a sensible minimum. */
    fun newPasswordError(password: String): String? = when {
        password.isEmpty() -> "Password is required."
        password.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters."
        !password.any { it.isDigit() } -> "Include at least one number."
        !password.any { it.isLetter() } -> "Include at least one letter."
        else -> null
    }

    fun confirmPasswordError(password: String, confirm: String): String? = when {
        confirm.isEmpty() -> "Please confirm your password."
        confirm != password -> "Passwords don't match."
        else -> null
    }

    fun nameError(name: String): String? = when {
        name.isBlank() -> "Name is required."
        name.trim().length < 2 -> "Name is too short."
        else -> null
    }

    /** 0..4 strength score for the sign-up meter. */
    fun passwordStrength(password: String): Int {
        if (password.isEmpty()) return 0
        var score = 0
        if (password.length >= MIN_PASSWORD_LENGTH) score++
        if (password.length >= 12) score++
        if (password.any { it.isDigit() } && password.any { it.isLetter() }) score++
        if (password.any { !it.isLetterOrDigit() } || (password.any { it.isUpperCase() } && password.any { it.isLowerCase() })) score++
        return score
    }
}
