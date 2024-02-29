package com.example.landmarkremark.utils

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
object StringProvider {
    private var contextString: Context? = null
    fun init(context: Context) {
        contextString = context
    }

    fun getString(id: Int)=
        contextString?.getString(id) ?: "Unknown"

}