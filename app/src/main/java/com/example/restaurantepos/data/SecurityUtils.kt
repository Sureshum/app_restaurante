package com.example.restaurantepos.data

import org.mindrot.jbcrypt.BCrypt

object SecurityUtils {
    fun hashPin(pin: String): String {
        return BCrypt.hashpw(pin, BCrypt.gensalt(12))
    }

    fun verifyPin(pinInput: String, hashedPin: String): Boolean {
        return try {
            BCrypt.checkpw(pinInput, hashedPin)
        } catch (e: Exception) {
            false
        }
    }
}