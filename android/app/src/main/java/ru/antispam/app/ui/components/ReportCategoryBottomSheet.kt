package ru.antispam.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.antispam.app.ui.theme.AppleBlue
import ru.antispam.app.ui.theme.AppleOrange
import ru.antispam.app.ui.theme.ApplePurple
import ru.antispam.app.ui.theme.AppleRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportCategoryBottomSheet(
    phoneNumberFormatted: String,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Пожаловаться на номер",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = phoneNumberFormatted,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(18.dp))

            AppleGroupedCard {
                ReportOptionItem(
                    title = "Опрос / ВЦИОМ / Статистика",
                    subtitle = "Автоматические и живые соцопросы",
                    icon = Icons.Rounded.Poll,
                    iconTint = AppleBlue,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCategorySelected("SURVEY")
                    }
                )
                AppleHairlineDivider()
                ReportOptionItem(
                    title = "Реклама и спам",
                    subtitle = "Навязчивые коммерческие предложения",
                    icon = Icons.Rounded.Campaign,
                    iconTint = AppleOrange,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCategorySelected("SPAM")
                    }
                )
                AppleHairlineDivider()
                ReportOptionItem(
                    title = "Финансовые услуги и кредиты",
                    subtitle = "Займы, службы безопасности, коллекторы",
                    icon = Icons.Rounded.AccountBalance,
                    iconTint = AppleRed,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCategorySelected("FINANCIAL")
                    }
                )
                AppleHairlineDivider()
                ReportOptionItem(
                    title = "Немой звонок / Сброс",
                    subtitle = "Робот сбрасывает трубку после ответа",
                    icon = Icons.Rounded.VolumeOff,
                    iconTint = ApplePurple,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCategorySelected("SILENT")
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReportOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
