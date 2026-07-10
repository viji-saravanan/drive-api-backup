package com.aryasubramani.vijibackup.auth.domain

import java.util.Locale

@ConsistentCopyVisibility
data class GoogleAccount private constructor(
    val subject: String,
    val email: String,
    val displayName: String?,
) {
    companion object {
        fun create(
            subject: String,
            email: String,
            displayName: String?,
        ): GoogleAccount? {
            val normalizedSubject = subject.trim()
            val normalizedEmail = normalizeEmail(email)
            if (normalizedSubject.isEmpty() || normalizedEmail == null) return null

            return GoogleAccount(
                subject = normalizedSubject,
                email = normalizedEmail,
                displayName = displayName?.trim()?.takeIf(String::isNotEmpty),
            )
        }

        internal fun normalizeEmail(email: String): String? =
            email
                .trim()
                .lowercase(Locale.ROOT)
                .takeIf { it.matches(GOOGLE_ACCOUNT_EMAIL_PATTERN) }
    }
}

private val GOOGLE_ACCOUNT_EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
