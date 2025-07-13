/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.patrick.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.camera.view.PreviewView
import com.patrick.camera.CameraViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import com.patrick.main.ui.FatigueMainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CameraScreen()
        }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraViewModel: CameraViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CameraViewModel(context as android.app.Application) as T
            }
        }
    )
    val fatigueLevel by cameraViewModel.fatigueLevel.collectAsState()
    val calibrationProgress by cameraViewModel.calibrationProgress.collectAsState()
    val isCalibrating by cameraViewModel.isCalibrating.collectAsState()
    val showFatigueDialog by cameraViewModel.showFatigueDialog.collectAsState()
    val previewView = remember { PreviewView(context) }
    LaunchedEffect(previewView, lifecycleOwner) {
        cameraViewModel.initializeCamera(previewView, lifecycleOwner)
    }
    FatigueMainScreen(
        fatigueLevel = fatigueLevel,
        calibrationProgress = calibrationProgress,
        isCalibrating = isCalibrating,
        showFatigueDialog = showFatigueDialog,
        previewView = previewView,
        onUserAcknowledged = {
            cameraViewModel.onUserAcknowledged()
        },
        onUserRequestedRest = {
            cameraViewModel.onUserRequestedRest()
        }
    )
}
