package com.iadv.dukaanlocker.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import com.iadv.dukaanlocker.R

@Composable
fun LauncherLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = "DukaanLocker Logo"
) {
    val context = LocalContext.current
    val bitmap = remember {
        val density = context.resources.displayMetrics.density
        val size = (108 * density).toInt()
        val drawable = ResourcesCompat.getDrawable(context.resources, R.mipmap.ic_launcher, context.theme)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable?.setBounds(0, 0, size, size)
        drawable?.draw(canvas)
        bmp.asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier.clip(CircleShape),
        contentScale = ContentScale.Fit
    )
}
