package com.example.landmarkremark

import android.app.Application
import com.example.landmarkremark.utils.SharedReferencesHelper
import com.example.landmarkremark.utils.StringProvider

class LandmarkRemarkApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        SharedReferencesHelper.init(applicationContext)
        StringProvider.init(applicationContext)
    }
}