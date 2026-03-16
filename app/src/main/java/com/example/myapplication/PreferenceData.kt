package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import java.io.IOException

class PreferenceData private constructor() {

    private fun getSharedPreferences(context: Context?): SharedPreferences? {
        return context?.getSharedPreferences(SHP_KEY, Context.MODE_PRIVATE)
    }

    fun clearData(context: Context?) {
        val sharedPref = getSharedPreferences(context) ?: return
        val editor = sharedPref.edit()
        editor.clear()
        editor.apply()
    }

    fun putUser(context: Context?, user: User?) {
        val sharedPref = getSharedPreferences(context) ?: return
        val editor = sharedPref.edit()
        user?.toJson()?.let {
            editor.putString(USER_KEY, it)
        } ?: editor.remove(USER_KEY)
        editor.apply()
    }

    fun getUser(context: Context?): User? {
        val sharedPref = getSharedPreferences(context) ?: return null
        val json = sharedPref.getString(USER_KEY, null) ?: return null
        return User.fromJson(json)
    }
    
    fun putSharing(context: Context?, enabled: Boolean) {
        val sharedPref = getSharedPreferences(context) ?: return
        val editor = sharedPref.edit()
        editor.putBoolean(SHARING_KEY, enabled)
        editor.apply()
    }
    
    fun getSharing(context: Context?): Boolean {
        val sharedPref = getSharedPreferences(context) ?: return false
        return sharedPref.getBoolean(SHARING_KEY, false)
    }

    companion object {
        @Volatile
        private var INSTANCE: PreferenceData? = null
        private val lock = Any()

        fun getInstance(): PreferenceData =
            INSTANCE ?: synchronized(lock) {
                INSTANCE ?: PreferenceData().also { INSTANCE = it }
            }

        private const val SHP_KEY = "com.example.myapplication.preferences"
        private const val USER_KEY = "userKey"
        private const val SHARING_KEY = "sharingKey"
    }
}
