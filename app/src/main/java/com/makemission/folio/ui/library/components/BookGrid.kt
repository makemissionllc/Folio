package com.makemission.folio.ui.library.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.makemission.folio.data.model.Book
import com.makemission.folio.data.model.ShelfStatus

@Composable
fun BookGrid(
    books: List<Book>,
    onBookClick: (Book) -> Unit = {},
    onLongClick: (Book) -> Unit = {},
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    header: @Composable () -> Unit = {},
    readBookIds: Set<String> = emptySet(),
    compactTabletGrid: Boolean = false,
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    LazyVerticalGrid(
        columns = if (isTablet) GridCells.Adaptive(minSize = if (compactTabletGrid) 132.dp else 176.dp) else GridCells.Fixed(3),
        modifier = modifier,
        state = state,
        contentPadding = PaddingValues(
            start = if (isTablet) if (compactTabletGrid) 24.dp else 28.dp else 16.dp,
            top = 8.dp,
            end = if (isTablet) if (compactTabletGrid) 24.dp else 28.dp else 16.dp,
            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(if (compactTabletGrid && isTablet) 16.dp else 22.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isTablet) if (compactTabletGrid) 16.dp else 24.dp else 12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(books, key = { it.id }) { book ->
            BookCoverCard(
                book = book,
                status = when (book.shelfStatus) {
                    ShelfStatus.WANT -> "Want to Read"
                    ShelfStatus.FINISHED -> "Finished"
                    ShelfStatus.AUTO -> if (book.id in readBookIds) "${(book.progressFraction * 100).toInt().coerceAtLeast(1)}% read" else "New"
                },
                onClick = { onBookClick(book) },
                onLongClick = { onLongClick(book) },
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    fadeOutSpec = tween(durationMillis = 180),
                    placementSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                ),
            )
        }
    }
}
