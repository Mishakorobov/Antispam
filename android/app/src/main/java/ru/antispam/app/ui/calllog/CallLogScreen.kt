package ru.antispam.app.ui.calllog

import android.provider.CallLog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.antispam.app.ui.components.AppleGroupedCard
import ru.antispam.app.ui.components.AppleHairlineDivider
import ru.antispam.app.ui.components.HeroShieldWidget
import ru.antispam.app.ui.components.ReportCategoryBottomSheet
import ru.antispam.app.ui.theme.AppleBlue
import ru.antispam.app.ui.theme.AppleMintGreen
import ru.antispam.app.ui.theme.AppleOrange
import ru.antispam.app.ui.theme.AppleRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogScreen(
    viewModel: CallLogViewModel,
    onRequestRole: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCallForReport by remember { mutableStateOf<CallUiItem?>(null) }
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AntiSpam Shield",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Блокировка спама и защита от прослушки",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Status Shield Widget
            item {
                HeroShieldWidget(
                    isProtected = state.isRoleHeld,
                    blockedCount = state.blockedCount,
                    poolsCount = state.totalPoolsCount,
                    lastSyncTime = state.lastSyncTime,
                    onRequestRole = onRequestRole
                )
            }

            // Delivery Mode Card (Waiting for Courier / Taxi)
            item {
                DeliveryModeCard(
                    isActive = state.isDeliveryModeActive,
                    remainingMinutes = state.deliveryRemainingMinutes,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleDeliveryMode()
                    }
                )
            }

            // System Blocked Audit Log Section (if any blocked calls recorded)
            if (state.blockedAuditRecords.isNotEmpty()) {
                item {
                    Text(
                        text = "ЗАБЛОКИРОВАНО СИСТЕМОЙ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = AppleRed.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }

                item {
                    AppleGroupedCard {
                        state.blockedAuditRecords.forEachIndexed { index, record ->
                            AuditRecordRow(record = record)
                            if (index < state.blockedAuditRecords.lastIndex) {
                                AppleHairlineDivider()
                            }
                        }
                    }
                }
            }

            // Call Log Section Header
            item {
                Text(
                    text = "НЕДАВНИЕ ВЫЗОВЫ",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
            }

            if (state.calls.isEmpty()) {
                item {
                    AppleGroupedCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Журнал звонков пуст или нет разрешения",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            } else {
                item {
                    AppleGroupedCard {
                        state.calls.forEachIndexed { index, item ->
                            CallItemRow(
                                item = item,
                                onReportClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedCallForReport = item
                                },
                                onWhitelistClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.addToWhitelist(item)
                                }
                            )
                            if (index < state.calls.lastIndex) {
                                AppleHairlineDivider()
                            }
                        }
                    }
                }
            }
        }

        // Report Bottom Sheet
        selectedCallForReport?.let { item ->
            ReportCategoryBottomSheet(
                phoneNumberFormatted = item.formattedNumber,
                onCategorySelected = { category ->
                    viewModel.submitReport(item, category)
                    selectedCallForReport = null
                },
                onDismiss = {
                    selectedCallForReport = null
                }
            )
        }
    }
}

@Composable
private fun CallItemRow(
    item: CallUiItem,
    onReportClick: () -> Unit,
    onWhitelistClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Badge
            val (icon, tint) = when {
                item.isCancelledByApp -> Icons.Rounded.Block to AppleRed
                item.isBlockedPool && item.callType != CallLog.Calls.OUTGOING_TYPE -> Icons.Rounded.ReportProblem to AppleOrange
                item.isSuspiciousShort -> Icons.Rounded.ReportProblem to AppleOrange
                else -> Icons.Rounded.Call to AppleMintGreen
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.formattedNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.isCancelledByApp) {
                    Spacer(modifier = Modifier.height(3.dp))
                    val badgeLabel = if (item.isCancelledByDatabase) {
                        "Отменён приложением • номер в базе"
                    } else {
                        when (item.cancelReasonCode) {
                            "SPOOFED_CALLER_ID" -> "Отменён приложением • подмена номера"
                            "BLOCKED_FOREIGN_PLUS7" -> "Отменён приложением • зарубежный +7"
                            else -> "Отменён приложением"
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppleRed.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleRed
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
                val categoryText = when (item.cancelCategory?.uppercase()) {
                    "SURVEY" -> "Опросы / Колл-центр"
                    "FINANCIAL" -> "Финансовый спам"
                    "SILENT" -> "Автодозвон / Немой вызов"
                    "SPOOFING" -> "ЕПВВ Антифрод"
                    "FOREIGN_PLUS7" -> "Казахстан / зарубежный +7"
                    else -> "Спам-база"
                }
                val subtitleText = when {
                    item.isCancelledByDatabase -> "$categoryText • ${item.operatorName ?: "Оператор не указан"}"
                    item.isCancelledByApp -> item.operatorName ?: categoryText
                    item.cancelReasonCode == "SILENCED_DELIVERY" -> "Заглушен (Режим доставки) • ${item.operatorName ?: categoryText}"
                    item.cancelReasonCode == "SILENCED_VIP_MODE" -> "Заглушен (VIP-режим)"
                    item.isBlockedPool && item.callType != CallLog.Calls.OUTGOING_TYPE ->
                        "Номер в базе спама (${item.operatorName ?: "Колл-центр"})"
                    item.callType == CallLog.Calls.OUTGOING_TYPE ->
                        if (item.durationSeconds > 0) "Исходящий вызов (${item.durationSeconds} сек)" else "Исходящий вызов"
                    item.callType == CallLog.Calls.MISSED_TYPE -> "Пропущенный вызов"
                    item.callType == CallLog.Calls.REJECTED_TYPE -> "Отклонён вручную"
                    item.isSuspiciousShort -> "Короткий вызов (${item.durationSeconds} сек)"
                    else -> "Входящий вызов (${item.durationSeconds} сек)"
                }
                val subtitleColor = when {
                    item.isCancelledByApp -> AppleRed
                    item.isBlockedPool && item.callType != CallLog.Calls.OUTGOING_TYPE -> AppleOrange
                    item.cancelReasonCode == "SILENCED_DELIVERY" -> AppleBlue
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = subtitleColor
                )
            }

            Text(
                text = item.dateFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }

        // Quick Actions
        if (!item.isWhitelisted && (item.isCancelledByApp || !item.isBlockedPool)) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onWhitelistClick,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "В доверенные",
                        fontSize = 12.sp,
                        color = AppleBlue
                    )
                }
                if (!item.isCancelledByApp) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = onReportClick,
                        colors = ButtonDefaults.buttonColors(containerColor = AppleOrange.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Пожаловаться",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleOrange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryModeCard(
    isActive: Boolean,
    remainingMinutes: Int,
    onToggle: () -> Unit
) {
    AppleGroupedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isActive) AppleOrange.copy(alpha = 0.15f) else AppleBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isActive) "🛵" else "📦",
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Режим «Жду доставку»",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isActive) "Активен: тихий звонок вместо сброса (${remainingMinutes} мин)"
                           else "Разрешить курьерам и такси дозвониться (на 2 часа)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) AppleOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) AppleOrange else AppleBlue
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isActive) "Отключить" else "Включить",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun AuditRecordRow(
    record: ru.antispam.app.data.local.entity.BlockedCallRecordEntity
) {
    val timeFormat = java.text.SimpleDateFormat("HH:mm, d MMM", java.util.Locale.getDefault())
    val dateStr = timeFormat.format(java.util.Date(record.timestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint, desc) = when {
            record.blockReason == "SPOOFED_CALLER_ID" ->
                Triple(Icons.Rounded.ReportProblem, AppleOrange, "Отменён • Подмена номера (ЕПВВ Антифрод)")
            record.blockReason == "BLOCKED_FOREIGN_PLUS7" ->
                Triple(Icons.Rounded.Block, AppleRed, "Отменён • Зарубежный +7 (${record.operatorName ?: "Казахстан"})")
            record.blockReason == "SILENCED_DELIVERY" ->
                Triple(Icons.Rounded.Call, AppleBlue, "Тихий вызов (Режим ожидания доставки)")
            record.blockReason == "SILENCED_VIP_MODE" ->
                Triple(Icons.Rounded.Call, AppleBlue, "Тихий вызов (VIP-режим)")
            record.blockReason.contains("MICRO_100") ->
                Triple(Icons.Rounded.Block, AppleRed, "Отменён (база, пул 100 номеров) • Опросы (${record.operatorName ?: "Виртуальная АТС"})")
            record.blockReason.contains("SUBPOOL_1000") ->
                Triple(Icons.Rounded.Block, AppleRed, "Отменён (база, 1000 номеров) • ${record.operatorName ?: "Виртуальная АТС"}")
            record.blockReason.contains("SINGLE_NUMBER") ->
                Triple(Icons.Rounded.Block, AppleRed, "Отменён (база) • Точечный спам-номер (${record.operatorName ?: "Мобильный"})")
            record.blockReason.contains("SURVEY_VCIOM") ->
                Triple(Icons.Rounded.Block, AppleRed, "Отменён (база) • Опросы ВЦИОМ")
            else ->
                Triple(Icons.Rounded.Block, AppleRed, "Отменён (в базе спам-пулов) • ${record.operatorName ?: "Виртуальная АТС"}")
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.formattedNumber,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = tint
            )
        }

        Text(
            text = dateStr,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}
