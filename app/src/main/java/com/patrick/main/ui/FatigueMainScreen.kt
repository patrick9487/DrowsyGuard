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
            // 相機預覽區域 - 佔據主要空間，避免與導航欄重疊
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // 讓相機畫面佔據主要空間
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .padding(top = 16.dp), // 增加頂部間距，避免與導航欄重疊
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 數據顯示區域 - 緊湊排列在底部
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 疲勞等級指示器
                FatigueLevelIndicator(fatigueLevel)

                Spacer(modifier = Modifier.height(16.dp))

                // 統計數據
                DetectionStats(
                    blinkFrequency = blinkFrequency,
                    yawnCount = yawnCount,
                    eyeClosureDuration = eyeClosureDuration,
                    isCalibrating = isCalibrating,
                    calibrationProgress = calibrationProgress,
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
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
    isCalibrating: Boolean = false,
    calibrationProgress: Int = 0,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // 眨眼頻率或校正進度
        if (isCalibrating) {
            CalibrationStatItem(
                progress = calibrationProgress,
                label = "校正中",
            )
        } else {
            StatItem(
                value = "${blinkFrequency}次",
                label = "眨眼/分鐘",
            )
        }

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
private fun CalibrationStatItem(
    progress: Int,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 進度條和百分比組合顯示，佔據與 StatItem 的 value 相同的空間
        Box(
            modifier = Modifier.height(32.dp), // 與 StatItem 的 titleLarge 文字高度一致
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                // 進度條
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier
                        .height(6.dp)
                        .size(width = 40.dp, height = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                )
                
                Spacer(modifier = Modifier.size(width = 8.dp, height = 0.dp))
                
                // 進度百分比
                Text(
                    text = "${progress}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        
        // 標籤，與 StatItem 的 label 完全一致
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
