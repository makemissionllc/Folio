package com.makemission.folio.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.makemission.folio.data.model.curatedSampleBooks
import com.makemission.folio.ui.library.LibraryScreen
import com.makemission.folio.ui.reader.ReadingScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class FolioRoute(val route: String) {
    data object Library : FolioRoute("library")
    data object Reader : FolioRoute("reader/{bookId}/{bookTitle}") {
        fun create(bookId: String, bookTitle: String): String {
            val encTitle = URLEncoder.encode(bookTitle, StandardCharsets.UTF_8.name())
            return "reader/$bookId/$encTitle"
        }
    }
}

@Composable
fun FolioNavHost() {
    val navController = rememberNavController()
    val books = remember { curatedSampleBooks() }

    NavHost(
        navController = navController,
        startDestination = FolioRoute.Library.route,
    ) {
        composable(FolioRoute.Library.route) {
            LibraryScreen(
                books = books,
                onBookClick = { book ->
                    navController.navigate(FolioRoute.Reader.create(book.id, book.title))
                },
            )
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
