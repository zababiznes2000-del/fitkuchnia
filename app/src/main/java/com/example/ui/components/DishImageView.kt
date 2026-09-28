package com.example.ui.components

import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.Recipe
import com.example.util.ImageStorageHelper
import java.io.File

@Composable
fun DishImageView(
    recipe: Recipe,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    backgroundColor: Color = Color(0xFF121212)
) {
    val context = LocalContext.current
    val fallbackDrawableRes = DishVisualResolver.getDrawableForRecipe(recipe)

    // Wyznacz docelowy model zdjęcia: oryginalny plik użytkownika, URL, URI lub zdekodowane bajty Base64
    val imageModel: Any? = remember(recipe.id, recipe.imagePath, recipe.imageBase64, recipe.imageUrl) {
        try {
            val path = recipe.safeImageUrl.takeIf { it.isNotBlank() } ?: recipe.imagePath?.trim()
            val b64 = recipe.imageBase64?.trim()

            if (!path.isNullOrBlank()) {
                if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("content://") || path.startsWith("file://")) {
                    path
                } else {
                    val file = File(path)
                    if (file.exists() && file.length() > 0) {
                        file
                    } else if (!b64.isNullOrBlank()) {
                        val restoredPath = ImageStorageHelper.decodeBase64ToFile(context, b64, recipe.id)
                        if (restoredPath != null && File(restoredPath).exists()) {
                            File(restoredPath)
                        } else {
                            try {
                                Base64.decode(b64, Base64.DEFAULT)
                            } catch (_: Throwable) {
                                null
                            }
                        }
                    } else {
                        null
                    }
                }
            } else if (!b64.isNullOrBlank()) {
                val restoredPath = ImageStorageHelper.decodeBase64ToFile(context, b64, recipe.id)
                if (restoredPath != null && File(restoredPath).exists()) {
                    File(restoredPath)
                } else {
                    try {
                        Base64.decode(b64, Base64.DEFAULT)
                    } catch (_: Throwable) {
                        null
                    }
                }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    Box(
        modifier = modifier.background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageModel ?: fallbackDrawableRes)
                .crossfade(true)
                .error(fallbackDrawableRes)
                .fallback(fallbackDrawableRes)
                .placeholder(fallbackDrawableRes)
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            alignment = Alignment.Center,
            error = painterResource(id = fallbackDrawableRes),
            fallback = painterResource(id = fallbackDrawableRes),
            placeholder = painterResource(id = fallbackDrawableRes)
        )
    }
}
