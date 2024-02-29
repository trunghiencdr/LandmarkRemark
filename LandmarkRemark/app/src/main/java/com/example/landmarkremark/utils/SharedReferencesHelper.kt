package com.example.landmarkremark.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.landmarkremark.User
import com.google.gson.Gson

object SharedReferencesHelper {
    private var sharedPref: SharedPreferences? = null
    private const val USER_INFO_STORAGE_KEY = "user_info"
    fun init(applicationContext: Context?) {
        sharedPref = applicationContext?.getSharedPreferences("MyApp", Context.MODE_PRIVATE)
    }

    private fun putString(key: String, value: String) {
        sharedPref?.edit()?.putString(key, value)?.apply()
    }

    private fun getString(key: String, default: String) =
        sharedPref?.getString(key, default)

    fun putUser(userInfo: User) {
        putString(USER_INFO_STORAGE_KEY, Gson().toJson(userInfo))
    }

    fun getUser(): User? = try {
        getString(USER_INFO_STORAGE_KEY, "")?.let {
            Gson().fromJson(it, User::class.java)
        }
    } catch (ex: Exception) {
        null
    }

}