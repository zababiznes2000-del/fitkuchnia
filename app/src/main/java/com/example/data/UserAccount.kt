package com.example.data

data class UserAccount(
    val email: String,
    val name: String,
    val isAdmin: Boolean
) {
    companion object {
        const val SUPERADMIN_EMAIL = "zababiznes2000@gmail.com"
        const val ADMIN_EMAIL = SUPERADMIN_EMAIL
        const val ADMIN_PASSWORD_EXACT = "sonymhc-ex5"

        fun isAdministrator(email: String?, name: String? = ""): Boolean {
            val cleanEmail = (email ?: "").trim().lowercase()
            return cleanEmail == SUPERADMIN_EMAIL.lowercase()
        }

        fun fromEmailAndName(email: String, name: String = ""): UserAccount {
            val cleanEmail = email.trim()
            val isAdmin = isAdministrator(cleanEmail, name)
            val displayName = name.trim().ifBlank {
                if (isAdmin) "Super Admin (Właściciel)"
                else cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
            }
            return UserAccount(
                email = cleanEmail,
                name = displayName,
                isAdmin = isAdmin
            )
        }

        val DEFAULT_ADMIN = UserAccount(
            email = SUPERADMIN_EMAIL,
            name = "Administrator (Superadmin)",
            isAdmin = true
        )

        val SAMPLE_REGULAR_USER = UserAccount(
            email = "uzytkownik@gmail.com",
            name = "Użytkownik FitKuchnia",
            isAdmin = false
        )
        val DEFAULT_USER = SAMPLE_REGULAR_USER
    }
}
