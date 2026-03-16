package com.example.myapplication

import com.google.gson.Gson
import java.io.IOException

data class User(
    val username: String,
    val email: String,
    val uid: String,
    val access: String,
    val refresh: String,
    val photo: String = ""
) {
    fun toJson(): String? {
        return try {
            Gson().toJson(this)
        } catch (ex: IOException) {
            ex.printStackTrace()
            null
        }
    }

    companion object {
        fun fromJson(string: String): User? {
            return try {
                Gson().fromJson(string, User::class.java)
            } catch (ex: IOException) {
                ex.printStackTrace()
                null
            }
        }
    }
}
