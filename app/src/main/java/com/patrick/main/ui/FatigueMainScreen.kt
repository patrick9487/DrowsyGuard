package com.patrick.main.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FatigueMainScreen(
    fatigueLevel: com.patrick.core.FatigueLevel,
    calibrationProgress: Int,
    isCalibrating: Boolean,
    showFatigueDialog: Boolean,
    previewView: androidx.camera.view.PreviewView,
    statusText: String = "持續偵測中…",
    onUserAcknowledged: () -> Unit = {},
    onUserRequestedRest: () -> Unit = {},
    uiEvent: kotlinx.coroutines.flow.SharedFlow<com.patrick.ui.fatigue.FatigueViewModel.FatigueUiEvent>? = null,
    // 新增的數據參數
    blinkFrequency: Int = 0,
    yawnCount: Int = 0,
    eyeClosureDuration: Long = 0L,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }
    var dialogType by remember { mutableStateOf<com.patrick.ui.fatigue.FatigueViewModel.FatigueUiEvent?>(null) }

    // 收集一次性事件流，決定是否顯示 Dialog
    if (uiEvent != null) {
        LaunchedEffect(uiEvent) {
            uiEvent.collectLatest { event ->
                when (event) {
                    is com.patrick.ui.fatigue.FatigueViewModel.FatigueUiEvent.ShowWarningDialog -> {
                        dialogType = event
                        showDialog = true
                    }
                    is com.patrick.ui.fatigue.FatigueViewModel.FatigueUiEvent.ShowNoticeDialog -> {
                        dialogType = event
                        showDialog = true
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FatigueDrawerContent(
                selectedItem = selectedItem,
                onItemSelected = { item ->
                    selectedItem = item
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                FatigueTopAppBar(
                    statusText = statusText,
                    fatigueLevel = fatigueLevel,
                    isCalibrating = isCalibrating,
                    onMenuClick = { scope.launch { drawerState.open() } },
                )
            },
        ) { paddingValues ->
            FatigueMainContent(
                paddingValues = paddingValues,
                previewView = previewView,
                fatigueLevel = fatigueLevel,
                calibrationProgress = calibrationProgress,
                isCalibrating = isCalibrating,
                showFatigueDialog = showFatigueDialog,
                onUserAcknowledged = onUserAcknowledged,
                onUserRequestedRest = onUserRequestedRest,
                blinkFrequency = blinkFrequency,
                yawnCount = yawnCount,
                eyeClosureDuration = eyeClosureDuration,
            )
            // 疲勞警告對話框
            if (showFatigueDialog) {
                com.patrick.ui.fatigue.FatigueAlertDialog(
                    fatigueLevel = fatigueLevel,
                    onAcknowledged = onUserAcknowledged,
                    onRequestRest = onUserRequestedRest,
                )
            }
        }
    }
}

@Composable
private fun FatigueDrawerContent(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
) {
    ModalDrawerSheet {
        Spacer(modifier = Modifier.padding(12.dp))
        Text(
            "DrowsyGuard",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.padding(12.dp))

        val drawerItems =
            listOf(
                "📷" to "疲勞偵測",
                "📁" to "歷史記錄",
                "⚙️" to "設定",
                "👤" to "帳號",
            )

        drawerItems.forEachIndexed { index, (icon, label) ->
            NavigationDrawerItem(
                icon = { Text(icon) },
                label = { Text(label) },
                selected = selectedItem == index,
                onClick = { onItemSelected(index) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FatigueTopAppBar(
    statusText: String,
    fatigueLevel: com.patrick.core.FatigueLevel,
    isCalibrating: Boolean,
    onMenuClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = getStatusTextColor(fatigueLevel, isCalibrating),
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Text("☰", style = MaterialTheme.typography.titleLarge)
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
            ),
    )
}

@Composable
private fun getStatusTextColor(
    fatigueLevel: com.patrick.core.FatigueLevel,
    isCalibrating: Boolean,
): Color {
    return when {
        isCalibrating -> MaterialTheme.colorScheme.primary
        fatigueLevel == com.patrick.core.FatigueLevel.NORMAL -> MaterialTheme.colorScheme.onSurface
        fatigueLevel == com.patrick.core.FatigueLevel.NOTICE -> Color(0xFFFF9800) // 橙色提醒
        fatigueLevel == com.patrick.core.FatigueLevel.WARNING -> Color(0xFFF44336) // 紅色警告
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
private fun FatigueMainContent(
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    previewView: androidx.camera.view.PreviewView,
    fatigueLevel: com.patrick.core.FatigueLevel,
    calibrationProgress: Int,
    isCalibrating: Boolean,
    showFatigueDialog: Boolean,
    onUserAcknowledged: () -> Unit,
    onUserRequestedRest: () -> Unit,
    blinkFrequency: Int,
    yawnCount: Int,
    eyeClosureDuration: Long,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues),
    ) {
        // 主要內容佈局
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 頂部標題
            Text(
                text = "疲勞偵測中...",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )

            // 相機預覽區域 - 佔據上半部分，去掉黑色背景
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // 讓相機畫面佔據剩餘空間的主要部分
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 下半部分數據顯示區域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 疲勞等級指示器
                FatigueLevelIndicator(fatigueLevel)

                Spacer(modifier = Modifier.height(24.dp))

                // 統計數據
                DetectionStats(
                    blinkFrequency = blinkFrequency,
                    yawnCount = yawnCount,
                    eyeClosureDuration = eyeClosureDuration,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 底部按鈕
                Button(
                    onClick = { /* TODO: 實現儲存記錄功能 */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "儲存記錄",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 校正進度條
        if (isCalibrating) {
            CalibrationProgressOverlay(calibrationProgress)
        }

        // 疲勞提醒覆蓋層
        if (fatigueLevel != com.patrick.core.FatigueLevel.NORMAL && !isCalibrating) {
            FatigueAlertOverlay(fatigueLevel)
        }
    }
}

@Composable
private fun FatigueLevelIndicator(fatigueLevel: com.patrick.core.FatigueLevel) {
    val (levelNumber, levelText, backgroundColor) = when (fatigueLevel) {
        com.patrick.core.FatigueLevel.NORMAL -> Triple(0, "正常", Color.Green)
        com.patrick.core.FatigueLevel.NOTICE -> Triple(2, "輕度疲勞", Color(0xFFFF9800))
        com.patrick.core.FatigueLevel.WARNING -> Triple(3, "重度疲勞", Color(0xFFF44336))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 圓形指示器
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = levelNumber.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 等級文字
        Text(
            text = levelText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DetectionStats(
    blinkFrequency: Int,
    yawnCount: Int,
    eyeClosureDuration: Long,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // 眨眼頻率
        StatItem(
            value = "${blinkFrequency}次",
            label = "眨眼/分鐘",
        )

        // 打哈欠次數
        StatItem(
            value = "${yawnCount}次",
            label = "哈欠/分鐘",
        )

        // 閉眼時間
        StatItem(
            value = "${String.format("%.1f", eyeClosureDuration / 1000.0)}秒",
            label = "閉眼時間",
        )
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FatigueAlertOverlay(fatigueLevel: com.patrick.core.FatigueLevel) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val backgroundColor =
            when (fatigueLevel) {
                com.patrick.core.FatigueLevel.NOTICE -> Color(0xFFFFA500).copy(alpha = 0.3f) // 橙色半透明
                com.patrick.core.FatigueLevel.WARNING -> Color(0xFFFF0000).copy(alpha = 0.4f) // 紅色半透明
                else -> Color.Transparent
            }

        val borderColor =
            when (fatigueLevel) {
                com.patrick.core.FatigueLevel.NOTICE -> Color(0xFFFFA500) // 橙色
                com.patrick.core.FatigueLevel.WARNING -> Color(0xFFFF0000) // 紅色
                else -> Color.Transparent
            }

        val alertText =
            when (fatigueLevel) {
                com.patrick.core.FatigueLevel.NOTICE -> "⚠️ 提醒：偵測到疲勞行為"
                com.patrick.core.FatigueLevel.WARNING -> "🚨 警告：請確認您的狀態"
                else -> ""
            }

        if (alertText.isNotEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(16.dp),
            ) {
                Text(
                    text = alertText,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier =
                        Modifier
                            .background(
                                borderColor.copy(alpha = 0.8f),
                                shape = MaterialTheme.shapes.medium,
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CalibrationProgressOverlay(calibrationProgress: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .padding(bottom = 120.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.medium,
                    )
                    .padding(24.dp),
        ) {
            Text(
                text = "校正中… $calibrationProgress%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.padding(16.dp))
            LinearProgressIndicator(
                progress = calibrationProgress / 100f,
                modifier =
                    Modifier
                        .padding(horizontal = 32.dp)
                        .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
private fun FatigueAlertDialog(
    fatigueLevel: com.patrick.core.FatigueLevel,
    onUserAcknowledged: () -> Unit,
    onUserRequestedRest: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = getDialogTitle(fatigueLevel),
                    style = MaterialTheme.typography.headlineSmall,
                    color = getDialogTitleColor(fatigueLevel),
                )
            },
            text = {
                Text(
                    text = getDialogMessage(fatigueLevel),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = onUserAcknowledged,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Text("我已清醒")
                }
            },
            dismissButton = {
                Button(
                    onClick = onUserRequestedRest,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                        ),
                ) {
                    Text("我會找地方休息")
                }
            },
        )
    }
}

private fun getDialogTitle(fatigueLevel: com.patrick.core.FatigueLevel): String {
    return when (fatigueLevel) {
        com.patrick.core.FatigueLevel.NOTICE -> "疲勞提醒"
        com.patrick.core.FatigueLevel.WARNING -> "疲勞警告"
        else -> "疲勞偵測"
    }
}

@Composable
private fun getDialogTitleColor(fatigueLevel: com.patrick.core.FatigueLevel): Color {
    return when (fatigueLevel) {
        com.patrick.core.FatigueLevel.NOTICE -> Color(0xFFFF9800)
        com.patrick.core.FatigueLevel.WARNING -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurface
    }
}

private fun getDialogMessage(fatigueLevel: com.patrick.core.FatigueLevel): String {
    return when (fatigueLevel) {
        com.patrick.core.FatigueLevel.NOTICE -> "系統偵測到您可能處於疲勞狀態，請注意安全！"
        com.patrick.core.FatigueLevel.WARNING -> "系統偵測到您處於警告狀態，請立即確認！"
        else -> "系統偵測中…"
    }
}
