package com.patrick.camera

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.MutableState
import android.util.Log

@Composable
fun CameraPreviewComposable(
    modifier: Modifier = Modifier,
    previewViewState: MutableState<PreviewView?>,
    onPreviewConfigChanged: ((PreviewView.ScaleType, Float) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            Log.d("CameraPreviewComposable", "[factory] Created PreviewView: ${previewView.hashCode()}")
            previewViewState.value = previewView
            previewView.post {
                val zoom = 0.2f
                onPreviewConfigChanged?.invoke(PreviewView.ScaleType.FILL_CENTER, zoom)
            }
            previewView
        },
        modifier = modifier
    )
} 