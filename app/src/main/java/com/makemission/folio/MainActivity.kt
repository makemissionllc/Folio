package com.makemission.folio

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.makemission.folio.widget.WidgetNavigation
import com.makemission.folio.widget.WidgetNavigationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-activity Compose entry point — navigation host lives here.
 *
 * Library is the start destination; tapping a cover navigates to the
 * Reader (§3 phone/tablet layouts, §6 Room). Volume keys are forwarded
 * to the Reader for one-handed page turns when it is visible.
 */
class MainActivity : ComponentActivity() {
    private val widgetNavigation = MutableStateFlow<WidgetNavigationRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Modern SplashScreen API — no hand-rolled delay; system shows
        // deep-green + centered amber wordmark + bottom MakeMission branding
        // until first frame is ready, then crossfades to Theme.Folio.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        handleEpubViewIntent(intent)
        handleWidgetIntent(intent)
        enableEdgeToEdge()
        setContent {
            val repo = SettingsRepository.get(this)
            val palette by repo.darkPalette.collectAsState(initial = FolioPalette.SLATE)
            val themeMode by repo.themeMode.collectAsState(initial = ThemeMode.AUTO)
            val widgetRequest by widgetNavigation.asStateFlow().collectAsState()
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
                    FolioNavHost(
                        widgetNavigationRequest = widgetRequest,
                        onWidgetNavigationHandled = { widgetNavigation.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEpubViewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        when (intent?.action) {
            WidgetNavigation.ACTION_OPEN_READER -> {
                val id = intent.getStringExtra(WidgetNavigation.EXTRA_BOOK_ID) ?: return
                val title = intent.getStringExtra(WidgetNavigation.EXTRA_BOOK_TITLE).orEmpty()
                widgetNavigation.value = WidgetNavigationRequest(id, title)
            }
            WidgetNavigation.ACTION_OPEN_LIBRARY -> {
                widgetNavigation.value = WidgetNavigationRequest()
            }
        }
    }

    private fun handleEpubViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val uriName = uri.lastPathSegment.orEmpty()
        val mimeType = intent.type.orEmpty()
        val isBook = intent.type?.contains("epub", ignoreCase = true) == true ||
            intent.type?.contains("mobipocket", ignoreCase = true) == true ||
            intent.type?.contains("fictionbook", ignoreCase = true) == true ||
            mimeType == "text/plain" ||
            uriName.endsWith(".epub", ignoreCase = true) ||
            uriName.endsWith(".mobi", ignoreCase = true) ||
            uriName.endsWith(".fb2", ignoreCase = true) ||
            uriName.endsWith(".txt", ignoreCase = true)
        if (!isBook && intent.type != "*/*" && intent.type != "application/octet-stream") return
        try {
            val vm = ViewModelProvider(this)[LibraryViewModel::class.java]
            vm.importEpub(uri, this)
        } catch (_: Exception) {}
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.isCtrlPressed == true && keyCode == KeyEvent.KEYCODE_K) {
            ReaderPageTurnHandler.onKeyboardLibrarySearch?.let { openLibrarySearch ->
                openLibrarySearch()
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            ReaderPageTurnHandler.onKeyboardLibraryEscape?.let { dismissLibrarySearch ->
                dismissLibrarySearch()
                return true
            }
        }
        if (event?.isCtrlPressed == true && keyCode == KeyEvent.KEYCODE_F) {
            ReaderPageTurnHandler.onKeyboardSearch?.let { openSearch ->
                openSearch()
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            ReaderPageTurnHandler.onKeyboardPageKey?.let { turnPage ->
                turnPage(keyCode == KeyEvent.KEYCODE_PAGE_DOWN)
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            ReaderPageTurnHandler.onVolumeKey?.let { handler ->
                handler(keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
