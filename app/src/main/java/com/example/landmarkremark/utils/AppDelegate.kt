package com.example.landmarkremark.utils

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

object AppDelegate {
    fun navigateAndFinish(currentActivity: AppCompatActivity, destinationActivityClass: Class<out AppCompatActivity>) {
        val intent = Intent(currentActivity, destinationActivityClass)
        currentActivity.startActivity(intent)
        currentActivity.finish()
    }
}