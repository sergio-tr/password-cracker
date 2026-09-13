package com.wifiauditlab.android

import android.app.Application
import com.wifiauditlab.android.di.appModule
import com.wifiauditlab.android.i18n.AppLanguagePreferences
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class WifiAuditLabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLanguagePreferences.applyStored(this)
        startKoin {
            androidContext(this@WifiAuditLabApp)
            modules(appModule)
        }
    }
}
