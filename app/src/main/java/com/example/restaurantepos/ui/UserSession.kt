package com.example.restaurantepos.ui

import com.example.restaurantepos.data.UserRole

data class ActiveSession(
    val userId: Int,
    val userName: String,
    val role: UserRole
) {
    fun isAdmin(): Boolean = role == UserRole.ADMIN
}