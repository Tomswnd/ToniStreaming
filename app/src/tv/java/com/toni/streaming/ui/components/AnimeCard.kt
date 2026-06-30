package com.toni.streaming.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.toni.streaming.data.model.Anime
import com.toni.streaming.ui.theme.AccentPurple
import com.toni.streaming.ui.theme.TextSecondary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progressRatio: Float? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(160.dp)
    ) {
        // Parent Box handles the outer focus borders and shadow.
        // Scale is removed to prevent ANY vertical page shaking/wobbling.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .shadow(
                    elevation = if (isFocused) 16.dp else 4.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = AccentPurple.copy(alpha = 0.4f)
                )
                .then(
                    // 3.dp AccentPurple border drawn on the parent bounds (always on top of child Card contents)
                    if (isFocused) Modifier.border(3.dp, AccentPurple, RoundedCornerShape(12.dp))
                    else Modifier
                )
                .padding(if (isFocused) 3.dp else 0.dp) // Spushes Card content inside the border to avoid subpixel crop
        ) {
            Card(
                onClick = onClick,
                scale = CardDefaults.scale(focusedScale = 1f), // Removed scale to eliminate vertical shaking
                border = CardDefaults.border(
                    border = androidx.tv.material3.Border.None,
                    focusedBorder = androidx.tv.material3.Border.None,
                    pressedBorder = androidx.tv.material3.Border.None
                ),
                colors = CardDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { isFocused = it.isFocused },
                shape = CardDefaults.shape(RoundedCornerShape(12.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = anime.imageUrl,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Progress Bar overlay
                    if (progressRatio != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .align(Alignment.BottomCenter)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressRatio.coerceIn(0f, 1f))
                                    .fillMaxSize()
                                    .background(AccentPurple)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = anime.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            color = if (isFocused) Color.White else TextSecondary
        )
    }
}
