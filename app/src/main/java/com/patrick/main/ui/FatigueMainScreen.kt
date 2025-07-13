package com.patrick.main.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.patrick.camera.CameraPreviewComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import android.Manifest
import androidx.compose.runtime.LaunchedEffect
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FatigueMainScreen(
    fatigueLevel: com.patrick.core.FatigueLevel,
    calibrationProgress: Int,
    isCalibrating: Boolean,
    showFatigueDialog: Boolean,
    previewView: androidx.camera.view.PreviewView,
    onUserAcknowledged: () -> Unit = {},
    onUserRequestedRest: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf(0) }
    
    // 根據狀態決定 TopAppBar 的標題文字
    val statusText = when {
        isCalibrating -> "正在校正中..."
        fatigueLevel == com.patrick.core.FatigueLevel.NORMAL -> "偵測中"
        fatigueLevel == com.patrick.core.FatigueLevel.MODERATE -> "警告：請注意安全"
        fatigueLevel == com.patrick.core.FatigueLevel.SEVERE -> "請盡快找地方休息"
        else -> "DrowsyGuard"
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.padding(12.dp))
                Text(
                    "DrowsyGuard",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.padding(12.dp))
                NavigationDrawerItem(
                    icon = { Text("📷") },
                    label = { Text("疲勞偵測") },
                    selected = selectedItem == 0,
                    onClick = {
                        selectedItem = 0
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Text("📁") },
                    label = { Text("歷史記錄") },
                    selected = selectedItem == 1,
                    onClick = {
                        selectedItem = 1
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Text("⚙️") },
                    label = { Text("設定") },
                    selected = selectedItem == 2,
                    onClick = {
                        selectedItem = 2
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    icon = { Text("👤") },
                    label = { Text("帳號") },
                    selected = selectedItem == 3,
                    onClick = {
                        selectedItem = 3
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                isCalibrating -> MaterialTheme.colorScheme.primary
                                fatigueLevel == com.patrick.core.FatigueLevel.NORMAL -> MaterialTheme.colorScheme.onSurface
                                fatigueLevel == com.patrick.core.FatigueLevel.MODERATE -> Color(0xFFFF9800) // 橙色警告
                                fatigueLevel == com.patrick.core.FatigueLevel.SEVERE -> Color(0xFFF44336) // 紅色危險
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 🔹 B. Camera 預覽區 - 主要內容區域
                AndroidView(
                    factory = { previewView }, 
                    modifier = Modifier.fillMaxSize()
                )
                
                // 其他 UI 元素疊加在相機預覽上方
                
                // 初始化進度條（畫面中下）
                if (isCalibrating) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(bottom = 120.dp) // 避免被底部導航擋住
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    shape = MaterialTheme.shapes.medium
                                )
                                .padding(24.dp)
                        ) {
                            Text(
                                text = "校正中… $calibrationProgress%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.padding(16.dp))
                            LinearProgressIndicator(
                                progress = calibrationProgress / 100f,
                                modifier = Modifier
                                    .padding(horizontal = 32.dp)
                                    .height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
                
                // 警告視窗（中央）
                if (showFatigueDialog) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { 
                                Text(
                                    "疲勞警示",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = when (fatigueLevel) {
                                        com.patrick.core.FatigueLevel.MODERATE -> Color(0xFFFF9800)
                                        com.patrick.core.FatigueLevel.SEVERE -> Color(0xFFF44336)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            text = { 
                                Text(
                                    when (fatigueLevel) {
                                        com.patrick.core.FatigueLevel.MODERATE -> "系統偵測到您可能處於疲勞狀態，請注意安全！"
                                        com.patrick.core.FatigueLevel.SEVERE -> "系統偵測到您處於嚴重疲勞狀態，請立即找地方休息！"
                                        else -> "系統偵測到您可能處於疲勞狀態，請注意安全！"
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = onUserAcknowledged,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("我已清醒")
                                }
                            },
                            dismissButton = {
                                Button(
                                    onClick = onUserRequestedRest,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Text("我會找地方休息")
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.large
                        )
                    }
                }
            }
        }
    }
} 