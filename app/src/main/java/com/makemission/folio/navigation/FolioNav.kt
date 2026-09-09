package com.makemission.folio.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.makemission.folio.data.settings.SettingsRepository
import com.makemission.folio.ui.library.LibraryScreen
import com.makemission.folio.ui.onboarding.OnboardingScreen
import com.makemission.folio.ui.reader.ReadingScreen
import com.makemission.folio.ui.vocabulary.VocabularyScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

sealed class FolioRoute(val route: String) {
    data object Library : FolioRoute("library")
    data object Reader : FolioRoute("reader/{bookId}/{bookTitle}") {
        fun create(bookId: String, bookTitle: String): String {
            val encTitle = URLEncoder.encode(bookTitle, StandardCharsets.UTF_8.name())
            return "reader/$bookId/$encTitle"
        }
    }
    data object Vocabulary : FolioRoute("vocabulary")
    data object Settings : FolioRoute("settings")
    data object Insights : FolioRoute("insights")
    data object Onboarding : FolioRoute("onboarding")
}

@Composable
fun FolioNavHost() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val hasSeenOnboarding by repo.hasSeenOnboarding.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    if (hasSeenOnboarding == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (hasSeenOnboarding == false) {
        OnboardingScreen(
            onComplete = {
                scope.launch { repo.setHasSeenOnboarding(true) }
            },
        )
        return
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = FolioRoute.Library.route,
    ) {
        composable(FolioRoute.Library.route) {
            LibraryScreen(
                onBookClick = { book ->
                    navController.navigate(FolioRoute.Reader.create(book.id, book.title))
                },
                onVocabularyClick = {
                    navController.navigate(FolioRoute.Vocabulary.route)
                },
                onSettingsClick = {
                    navController.navigate(FolioRoute.Settings.route)
                },
                onInsightsClick = {
                    navController.navigate(FolioRoute.Insights.route)
                },
            )
        }
        composable(FolioRoute.Settings.route) {
            com.makemission.folio.ui.settings.SettingsScreen(
                onBack = { navController.popBackStack() },
                onInsightsClick = { navController.navigate(FolioRoute.Insights.route) },
            )
        }
        composable(FolioRoute.Insights.route) {
            com.makemission.folio.ui.insights.InsightsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(FolioRoute.Vocabulary.route) {
            VocabularyScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = FolioRoute.Reader.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("bookTitle") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
            val rawTitle = backStackEntry.arguments?.getString("bookTitle").orEmpty()
            val bookTitle = try {
                URLDecoder.decode(rawTitle, StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                rawTitle
            }
            ReadingScreen(
                bookId = bookId,
                bookTitle = bookTitle.ifBlank { "Reading" },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
