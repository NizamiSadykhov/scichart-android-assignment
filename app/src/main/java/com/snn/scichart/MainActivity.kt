package com.snn.scichart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.snn.scichart.ui.chart.ChartRoute
import com.snn.scichart.ui.chart.ChartViewModel
import com.snn.scichart.ui.theme.ScichartTheme
import dagger.hilt.android.AndroidEntryPoint

/** Единственная Activity, содержащая Compose UI и подключённая к иерархии компонентов Hilt. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val chartViewModel: ChartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScichartTheme {
                ChartRoute(viewModel = chartViewModel)
            }
        }
    }
}
