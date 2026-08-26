package com.snn.scichart

import android.app.Application
import com.scichart.charting.visuals.SciChartSurface
import com.snn.scichart.data.initializer.PointSourceDataInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Корень приложения, который активирует SciChart и запускает app-scoped операции data-слоя. */
@HiltAndroidApp
class SciChartApplication : Application() {

    @Inject
    lateinit var pointSourceDataInitializer: PointSourceDataInitializer

    override fun onCreate() {
        super.onCreate()

        val licenseKey = BuildConfig.SCICHART_LICENSE_KEY
        if (licenseKey.isNotBlank()) {
            SciChartSurface.setRuntimeLicenseKey(licenseKey)
        }

        pointSourceDataInitializer.initialize()
    }
}
