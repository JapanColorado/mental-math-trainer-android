package dev.russell.mmtrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.russell.mmtrainer.audio.Speaker
import dev.russell.mmtrainer.notify.Reminders
import dev.russell.mmtrainer.ui.AppRoot
import dev.russell.mmtrainer.ui.AppViewModel

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        AppViewModel.factory(
            (application as MmtApp).store,
            { Speaker(application) },
            { enabled, minute -> Reminders.schedule(application, enabled, minute) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !vm.ready }  // holds until the first screen has its data
        enableEdgeToEdge()  // bar icons follow the system light/dark theme, as the app does
        setContent { AppRoot(vm) }
    }
}
