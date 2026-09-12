package com.makemission.folio.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
    data object Reader : FolioRoute("reader/{bookId}/{bookTitle}?ch={ch}&para={para}") {
        fun create(bookId: String, bookTitle: String, chapterIndex: Int = -1, paragraphIndex: Int = -1): String {
            val encTitle = URLEncoder.encode(bookTitle, StandardCharsets.UTF_8.name())
            return if (chapterIndex >= 0 && paragraphIndex >= 0) "reader/$bookId/$encTitle?ch=$chapterIndex&para=$paragraphIndex"
            else "reader/$bookId/$encTitle"
        }
    }
    data object Vocabulary : FolioRoute("vocabulary")
    data object Settings : FolioRoute("settings")
    data object Insights : FolioRoute("insights")
    data object SmartFeatures : FolioRoute("smart_features")
    data object Logs : FolioRoute("logs")
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
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(340, easing = FastOutSlowInEasing),
                initialOffsetX = { it / 5 }
            ) + fadeIn(animationSpec = tween(260, easing = LinearOutSlowInEasing))
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                targetOffsetX = { -it / 5 }
            ) + fadeOut(animationSpec = tween(200, easing = LinearOutSlowInEasing))
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(340, easing = FastOutSlowInEasing),
                initialOffsetX = { -it / 5 }
            ) + fadeIn(animationSpec = tween(260, easing = LinearOutSlowInEasing))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                targetOffsetX = { it / 5 }
            ) + fadeOut(animationSpec = tween(200, easing = LinearOutSlowInEasing))
        },
    ) {
        composable(FolioRoute.Library.route) {
            LibraryScreen(
                onBookClick = { book ->
                    navController.navigate(FolioRoute.Reader.create(book.id, book.title))
                },
                onVocabularyClick = {
                    navController.navigate(FolioRoute.Vocabulary.route) {
                        launchSingleTop = true
                    }
                },
                onSettingsClick = {
                    navController.navigate(FolioRoute.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onInsightsClick = {
                    navController.navigate(FolioRoute.Insights.route) {
                        launchSingleTop = true
                    }
                },
                onSearchResultClick = { result ->
                    navController.navigate(
                        FolioRoute.Reader.create(result.bookId, result.bookTitle, result.chapterIndex, result.paragraphIndex)
                    )
                },
            )
        }
        composable(
            route = FolioRoute.Settings.route,
            // Swipe right on Library → Settings should slide in from the RIGHT (consistent with gesture)
            // Previously this used the default popEnter (from LEFT), which felt backwards for a right-swipe
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(340, easing = FastOutSlowInEasing),
                    initialOffsetX = { it } // from right (positive X)
                ) + fadeIn(animationSpec = tween(260, easing = LinearOutSlowInEasing))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    targetOffsetX = { -it / 5 }
                ) + fadeOut(animationSpec = tween(200, easing = LinearOutSlowInEasing))
            },
            popEnterTransition = {
                slideInHorizontally(
                    animationSpec = tween(340, easing = FastOutSlowInEasing),
                    initialOffsetX = { -it / 5 }
                ) + fadeIn(animationSpec = tween(260, easing = LinearOutSlowInEasing))
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    targetOffsetX = { it } // to right when popping back to Library
                ) + fadeOut(animationSpec = tween(200, easing = LinearOutSlowInEasing))
            },
        ) {
            com.makemission.folio.ui.settings.SettingsScreen(
                onBack = { navController.popBackStack() },
                onInsightsClick = { navController.navigate(FolioRoute.Insights.route) { launchSingleTop = true } },
                onSmartFeaturesClick = { navController.navigate(FolioRoute.SmartFeatures.route) { launchSingleTop = true } },
                onLogsClick = { navController.navigate(FolioRoute.Logs.route) { launchSingleTop = true } },
            )
        }
        composable(FolioRoute.Insights.route) {
            com.makemission.folio.ui.insights.InsightsScreen(
                onBack = { navController.popBackStack() },
                onVocabularyClick = { navController.navigate(FolioRoute.Vocabulary.route) { launchSingleTop = true } },
            )
        }
        composable(FolioRoute.SmartFeatures.route) {
            com.makemission.folio.ui.settings.SmartFeaturesScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(FolioRoute.Logs.route) {
            com.makemission.folio.ui.settings.LogsScreen(
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
                navArgument("ch") { type = NavType.IntType; defaultValue = -1 },
                navArgument("para") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
            val rawTitle = backStackEntry.arguments?.getString("bookTitle").orEmpty()
            val bookTitle = try {
                URLDecoder.decode(rawTitle, StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                rawTitle
            }
            val ch = backStackEntry.arguments?.getInt("ch") ?: -1
            val para = backStackEntry.arguments?.getInt("para") ?: -1
            ReadingScreen(
                bookId = bookId,
                bookTitle = bookTitle.ifBlank { "Reading" },
                onBack = { navController.popBackStack() },
                initialChapterIndex = ch.takeIf { it >= 0 },
                initialParagraphIndex = para.takeIf { it >= 0 },
            )
        }
    }
}
