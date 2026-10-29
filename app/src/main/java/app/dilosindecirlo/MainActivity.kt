package app.dilosindecirlo

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.dilosindecirlo.ui.AppViewModel
import app.dilosindecirlo.ui.Screen
import app.dilosindecirlo.ui.screens.CardEditorScreen
import app.dilosindecirlo.ui.screens.CategoriesScreen
import app.dilosindecirlo.ui.screens.CustomCardsScreen
import app.dilosindecirlo.ui.screens.GameRoot
import app.dilosindecirlo.ui.screens.HomeScreen
import app.dilosindecirlo.ui.screens.HowToScreen
import app.dilosindecirlo.ui.screens.SettingsScreen
import app.dilosindecirlo.ui.screens.SetupScreen
import app.dilosindecirlo.ui.screens.StatsScreen
import app.dilosindecirlo.ui.theme.DsdTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val cream = Color.rgb(0xFB, 0xF1, 0xDA)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(cream, cream),
            navigationBarStyle = SystemBarStyle.light(cream, cream),
        )
        super.onCreate(savedInstanceState)
        setContent { DsdTheme { AppRoot(vm) } }
    }

    override fun onStop() {
        super.onStop()
        vm.onAppBackground()
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current

    BackHandler(enabled = vm.screen != Screen.Home && vm.screen != Screen.Playing) {
        if (!vm.back()) vm.goHome()
    }

    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.toast = null
        }
    }

    val animations = vm.settings.animations
    Crossfade(
        targetState = vm.screen,
        animationSpec = tween(if (animations) 180 else 0),
        label = "screen",
    ) { screen ->
        when (screen) {
            Screen.Home -> HomeScreen(vm)
            is Screen.Setup -> SetupScreen(vm, screen.mode)
            Screen.Playing -> GameRoot(vm)
            Screen.Categories -> CategoriesScreen(vm)
            Screen.Stats -> StatsScreen(vm)
            Screen.Settings -> SettingsScreen(vm)
            Screen.HowTo -> HowToScreen(vm)
            Screen.CustomCards -> CustomCardsScreen(vm)
            is Screen.CardEditor -> CardEditorScreen(vm, screen.cardId)
        }
    }
}
