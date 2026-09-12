package com.makemission.folio.ui.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.makemission.folio.data.model.Book
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCoverCard(
    book: Book,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(book.coverColor)
                .let { m ->
                    when {
                        onClick != null || onLongClick != null -> m.combinedClickable(
                            onClick = { onClick?.invoke() },
                            onLongClick = onLongClick,
                        )
                        else -> m
                    }
                },
        ) {
            // Cover image when available (imported EPUB), otherwise palette color
            // Performance: downsample to display size (~148dp → ~440px at 3x) via Coil size(),
            // reuse memory/disk cache and avoid re-decoding full-res bitmap on every recomposition.
            if (book.coverImagePath != null) {
                val context = LocalContext.current
                val coverRequest = remember(book.coverImagePath) {
                    ImageRequest.Builder(context)
                        .data(File(book.coverImagePath))
                        .size(Size(440, 660))
                        .crossfade(true)
                        .memoryCacheKey(book.coverImagePath)
                        .diskCacheKey(book.coverImagePath)
                        .build()
                }
                AsyncImage(
                    model = coverRequest,
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // Subtle scrim for legibility of overlay text on photographic covers
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f)),
                )
            }
            // Subtle spine edge.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.10f)),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                // Editorial chip — flat, small-caps feel.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "FOLIO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = book.coverColor,
                        ),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = 0.88f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Tiny amber rule at the bottom of the cover — focal point.
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.75f)),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Text(
            text = book.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
