package com.makemission.folio.ui.library.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.makemission.folio.data.model.Book

@Composable
fun BookGrid(
    books: List<Book>,
    onBookClick: (Book) -> Unit = {},
    onLongClick: (Book) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(books, key = { it.id }) { book ->
            BookCoverCard(
                book = book,
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
