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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrick.main.ui.FatigueMainScreen
import com.patrick.main.ui.FatigueScreenViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 設置疲勞檢測日誌配置 - 配置2：只看關鍵事件和觸發
        // 避免 EAR 數值日誌過多，重點看閉眼、打哈欠等事件
        com.patrick.core.FatigueDetectionLogger.setLogEnabled(
            sensitivity = false, // 關閉靈敏度日誌，避免 EAR 數值日誌過多
            trigger = true, // 保留觸發日誌，看疲勞級別變化
            calibration = false, // 關閉校正日誌，避免校正日誌干擾
            event = true, // 開啟事件日誌，重點：看閉眼、打哈欠等事件
            reset = false, // 關閉重置日誌，避免重置日誌干擾
        )

        setContent {
            MainApp()
        }
    }
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val requestPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasCameraPermission = isGranted
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraScreen()
    } else {
        PermissionRequestScreen(
            onRequestPermission = {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
        )
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp),
            ) {
                Text(
                    text = "需要相機權限",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                Text(
                    text = "為了進行疲勞偵測，此應用程式需要存取您的相機。請授權相機權限以繼續使用。",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp),
                )

                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text("授權相機權限")
                }
            }
        }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val fatigueScreenViewModel: FatigueScreenViewModel =
        viewModel(
            factory =
                object : ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FatigueScreenViewModel(context as android.app.Application) as T
                    }
                },
        )
    val fatigueLevel by fatigueScreenViewModel.fatigueLevel.collectAsState()
    val calibrationProgress by fatigueScreenViewModel.calibrationProgress.collectAsState()
    val isCalibrating by fatigueScreenViewModel.isCalibrating.collectAsState()
    val showFatigueDialog by fatigueScreenViewModel.showFatigueDialog.collectAsState()
    val statusText by fatigueScreenViewModel.statusText.collectAsState()
    val previewView = remember { PreviewView(context) }

    // 添加調試日誌
    LaunchedEffect(fatigueLevel, isCalibrating, showFatigueDialog, statusText) {
        android.util.Log.d(
            "MainActivity",
            "UI狀態更新: fatigueLevel=$fatigueLevel, isCalibrating=$isCalibrating, showFatigueDialog=$showFatigueDialog, statusText=$statusText",
        )
    }

    LaunchedEffect(previewView, lifecycleOwner) {
        android.util.Log.d("MainActivity", "初始化疲勞偵測")
        fatigueScreenViewModel.initializeFatigueDetection(previewView, lifecycleOwner)
    }

    android.util.Log.d("MainActivity", "渲染 FatigueMainScreen")
    FatigueMainScreen(
        fatigueLevel = fatigueLevel,
        calibrationProgress = calibrationProgress,
        isCalibrating = isCalibrating,
        showFatigueDialog = showFatigueDialog,
        previewView = previewView,
        statusText = statusText,
        onUserAcknowledged = {
            android.util.Log.d("MainActivity", "用戶確認已清醒")
            fatigueScreenViewModel.onUserAcknowledged()
        },
        onUserRequestedRest = {
            android.util.Log.d("MainActivity", "用戶要求休息")
            fatigueScreenViewModel.onUserRequestedRest()
        },
    )
}
