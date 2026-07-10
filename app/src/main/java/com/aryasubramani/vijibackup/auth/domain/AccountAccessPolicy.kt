package com.aryasubramani.vijibackup.auth.domain

class AccountAccessPolicy(allowedEmails: Set<String>) {
    private val normalizedAllowedEmails = allowedEmails.map { email ->
        requireNotNull(GoogleAccount.normalizeEmail(email)) {
            "Allowed Google account email is invalid"
        }
    }.toSet()

    init {
        require(normalizedAllowedEmails.size == allowedEmails.size) {
            "Allowed Google account emails must be unique after normalization"
        }
    }

    fun evaluate(account: GoogleAccount): AccountAccess =
        if (account.email in normalizedAllowedEmails) {
            AccountAccess.Approved(account)
        } else {
            AccountAccess.Blocked(account)
        }
}

sealed interface AccountAccess {
    data class Approved(val account: GoogleAccount) : AccountAccess

    data class Blocked(val account: GoogleAccount) : AccountAccess
}
