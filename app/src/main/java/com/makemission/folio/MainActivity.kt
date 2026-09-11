package com.makemission.folio

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.makemission.folio.data.settings.SettingsRepository
import com.makemission.folio.navigation.FolioNavHost
import com.makemission.folio.ui.library.LibraryViewModel
import com.makemission.folio.ui.reader.ReaderPageTurnHandler
import com.makemission.folio.ui.theme.FolioPalette
import com.makemission.folio.ui.theme.FolioTheme
import com.makemission.folio.ui.theme.ThemeMode

/**
 * Single-activity Compose entry point — navigation host lives here.
 *
 * Library is the start destination; tapping a cover navigates to the
 * Reader (§3 phone/tablet layouts, §6 Room). Volume keys are forwarded
 * to the Reader for one-handed page turns when it is visible.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleEpubViewIntent(intent)
        enableEdgeToEdge()
        setContent {
            val repo = SettingsRepository.get(this)
            val palette by repo.darkPalette.collectAsState(initial = FolioPalette.SLATE)
            val themeMode by repo.themeMode.collectAsState(initial = ThemeMode.AUTO)
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.AUTO -> systemDark
            }
            // Premium crossfade for palette/theme changes — no jarring flash, subtle 360ms
            Crossfade(
                targetState = isDark to palette,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                label = "theme_crossfade",
            ) { (dark, pal) ->
                FolioTheme(darkTheme = dark, palette = pal) {
                    FolioNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEpubViewIntent(intent)
    }

    private fun handleEpubViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        // Only handle epub-like intents (mime or extension) — reuse existing import pipeline, no duplication
        val isEpub = intent.type?.contains("epub", ignoreCase = true) == true ||
            intent.type == "application/octet-stream" && uri.toString().contains(".epub", ignoreCase = true) ||
            uri.toString().contains(".epub", ignoreCase = true)
        if (!isEpub && intent.type != "*/*") return
        try {
            val vm = ViewModelProvider(this)[LibraryViewModel::class.java]
            vm.importEpub(uri, this)
        } catch (_: Exception) {}
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            ReaderPageTurnHandler.onVolumeKey?.let { handler ->
                handler(keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
