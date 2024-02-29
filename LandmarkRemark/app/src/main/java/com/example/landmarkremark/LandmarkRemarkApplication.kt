package com.example.landmarkremark

import android.app.Application
import com.example.landmarkremark.utils.SharedReferencesHelper

class LandmarkRemarkApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        SharedReferencesHelper.init(applicationContext)
    }
}