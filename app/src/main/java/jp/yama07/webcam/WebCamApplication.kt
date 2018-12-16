package jp.yama07.webcam

import android.app.Application
import timber.log.Timber

class WebCamApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}