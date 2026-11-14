package ru.antispam.app.ui.settings

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.antispam.app.privacy.FamilyLockManager
import ru.antispam.app.privacy.model.ProtectionPreset
import ru.antispam.app.ui.components.AppleGroupedCard
import ru.antispam.app.ui.components.AppleHairlineDivider
import ru.antispam.app.ui.settings.components.OemBatteryOptimizationBanner
import ru.antispam.app.ui.theme.AppleBlue
import ru.antispam.app.ui.theme.AppleMintGreen
import ru.antispam.app.ui.theme.AppleOrange
import ru.antispam.app.ui.theme.AppleRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showNoSecurityDialog by remember { mutableStateOf(false) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingAction?.invoke()
            pendingAction = null
        } else {
            pendingAction = null
            Toast.makeText(context, "Подтверждение отклонено: настройки защищены", Toast.LENGTH_SHORT).show()
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importDatabaseFromUri(it) }
    }

    val requestAuthAction: (String, () -> Unit) -> Unit = { title, action ->
        if (!state.isDeviceSecure) {
            pendingAction = action
            showNoSecurityDialog = true
        } else {
            val intent = FamilyLockManager.createConfirmDeviceCredentialIntent(
                context = context,
                title = "Семейный замок: $title",
                description = "Для изменения настроек защиты подтвердите личность PIN-кодом или отпечатком пальца"
            )
            if (intent != null) {
                pendingAction = action
                authLauncher.launch(intent)
            } else {
                pendingAction = action
                showNoSecurityDialog = true
            }
        }
    }

    if (showNoSecurityDialog) {
        AlertDialog(
            onDismissRequest = {
                pendingAction = null
                showNoSecurityDialog = false
            },
            title = {
                Text(text = "Семейный замок", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "На этом устройстве не установлена системная блокировка экрана (PIN/отпечаток). Рекомендуется включить её в настройках Android для полной защиты от случайного отключения фильтрации.\n\nПодтвердить изменение настроек?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction?.invoke()
                        pendingAction = null
                        showNoSecurityDialog = false
                    }
                ) {
                    Text("Разрешить", color = AppleBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingAction = null
                        showNoSecurityDialog = false
                    }
                ) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Настройки",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Section 0: Phase 10.3 Адаптивный OEM-баннер оптимизации батареи и автозапуска
            if (state.showOemBanner) {
                item {
                    SectionHeader("ЭНЕРГОСБЕРЕЖЕНИЕ И АВТОЗАПУСК")
                    OemBatteryOptimizationBanner(
                        vendor = state.oemVendor,
                        isIgnored = state.isBatteryOptimizationIgnored,
                        onOpenSettings = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.openOemAutostartSettings()
                        },
                        onDismiss = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.dismissOemBanner()
                        }
                    )
                }
            }

            // Section 1: Phase 10.2 Пресеты комплексной защиты в 1 клик
            item {
                SectionHeader("ПРОФИЛИ ЗАЩИТЫ В 1 КЛИК")
                AppleGroupedCard {
                    ProtectionPreset.entries.forEachIndexed { index, preset ->
                        PresetRow(
                            preset = preset,
                            isSelected = state.activePreset == preset,
                            onSelect = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (state.isFamilyLockEnabled) {
                                    requestAuthAction("Смена профиля защиты") {
                                        viewModel.applyProtectionPreset(preset)
                                    }
                                } else {
                                    viewModel.applyProtectionPreset(preset)
                                }
                            }
                        )
                        if (index < ProtectionPreset.entries.size - 1) {
                            AppleHairlineDivider()
                        }
                    }
                }
            }

            // Section 1.5: Phase 11.5 Режим «Семейный замок / Защита родителей»
            item {
                SectionHeader("СЕМЕЙНЫЙ ЗАМОК (ЗАЩИТА РОДИТЕЛЕЙ)")
                AppleGroupedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.isFamilyLockEnabled) AppleMintGreen.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Family Lock",
                                tint = if (state.isFamilyLockEnabled) AppleMintGreen else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Закрепить профиль защиты",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (state.isFamilyLockEnabled)
                                    "Активен: настройки защищены биометрией или системным PIN-кодом"
                                else
                                    "Защита от случайного отключения фильтрации пожилыми родственниками",
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                color = if (state.isFamilyLockEnabled) AppleMintGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Switch(
                            checked = state.isFamilyLockEnabled,
                            onCheckedChange = { willEnable ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (willEnable) {
                                    viewModel.toggleFamilyLock(true)
                                } else {
                                    requestAuthAction("Отключение Семейного замка") {
                                        viewModel.toggleFamilyLock(false)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppleMintGreen
                            )
                        )
                    }
                }
            }

            // Section 2: Категории блокировки
            item {
                SectionHeader("КАТЕГОРИИ ФИЛЬТРАЦИИ")
                AppleGroupedCard {
                    SettingsSwitchRow(
                        title = "Соцопросы и ВЦИОМ",
                        subtitle = "Блокировать звонки центров изучения общественного мнения",
                        checked = state.blockSurveys,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (state.isFamilyLockEnabled && !it) {
                                requestAuthAction("Отключение фильтра соцопросов") {
                                    viewModel.setSetting("block_surveys", false)
                                }
                            } else {
                                viewModel.setSetting("block_surveys", it)
                            }
                        }
                    )
                    AppleHairlineDivider()
                    SettingsSwitchRow(
                        title = "Финансовые услуги и займы",
                        subtitle = "Кредиты, службы безопасности, навязчивые страховки",
                        checked = state.blockFinancial,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (state.isFamilyLockEnabled && !it) {
                                requestAuthAction("Отключение финансового фильтра") {
                                    viewModel.setSetting("block_financial", false)
                                }
                            } else {
                                viewModel.setSetting("block_financial", it)
                            }
                        }
                    )
                    AppleHairlineDivider()
                    SettingsSwitchRow(
                        title = "Немые звонки и автопрозвоны",
                        subtitle = "Звонки от роботов-прозвонщиков со сбросом",
                        checked = state.blockSilentCalls,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (state.isFamilyLockEnabled && !it) {
                                requestAuthAction("Отключение фильтра немых звонков") {
                                    viewModel.setSetting("block_silent", false)
                                }
                            } else {
                                viewModel.setSetting("block_silent", it)
                            }
                        }
                    )
                }
            }

            // Section 3: Post-Call UX
            item {
                SectionHeader("ОБРАТНАЯ СВЯЗЬ (POST-CALL UX)")
                AppleGroupedCard {
                    SettingsSwitchRow(
                        title = "Уведомление после короткого вызова",
                        subtitle = "Показывать быстрые кнопки репорта в шторке после разговоров < 30 сек",
                        checked = state.postCallEnabled,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setSetting("post_call_enabled", it)
                        }
                    )
                    AppleHairlineDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Порог детекции спама",
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Вызовы длительностью до 29 секунд",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Text(
                            text = "< 30 сек",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppleBlue
                        )
                    }
                }
            }

            // Section 4: База данных и синхронизация
            item {
                SectionHeader("БАЗА ДАННЫХ И ОБНОВЛЕНИЯ")
                AppleGroupedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Локальная версия базы",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "v${state.currentDatabaseVersion}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    AppleHairlineDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Жалоб в очереди на отправку",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${state.pendingReportsCount}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.pendingReportsCount > 0) AppleBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    AppleHairlineDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openOemAutostartSettings()
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Автозапуск (${state.oemVendor.brandName})",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (state.isBatteryOptimizationIgnored) "Фоновая работа разрешена" else "Нажмите для настройки автозапуска",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                color = if (state.isBatteryOptimizationIgnored) AppleMintGreen else AppleOrange
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = AppleBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    AppleHairlineDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.triggerSyncNow()
                        },
                        enabled = !state.isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = AppleBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Синхронизация...", color = Color.White)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Обновить базу сейчас", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    AppleHairlineDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "ОФЛАЙН P2P-ОБМЕН БАЗОЙ (ZERO-TRUST)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val p2pMsg = state.p2pMessage
                    if (p2pMsg != null) {
                        val bannerBg = if (state.isP2pSuccess) AppleMintGreen.copy(alpha = 0.12f) else AppleRed.copy(alpha = 0.12f)
                        val bannerColor = if (state.isP2pSuccess) AppleMintGreen else AppleRed
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(bannerBg)
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = p2pMsg,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = bannerColor,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss",
                                    tint = bannerColor,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { viewModel.clearP2pMessage() }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val shareIntent = viewModel.prepareOfflineShareIntent()
                                if (shareIntent != null) {
                                    context.startActivity(shareIntent)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Файл базы еще не сформирован. Нажмите «Обновить базу сейчас» для первоначальной загрузки.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Поделиться", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                openDocumentLauncher.launch(arrayOf("*/*"))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Импорт файла", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: ProtectionPreset,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val (accentColor, icon) = when (preset) {
        ProtectionPreset.BASIC_COMFORT -> AppleMintGreen to Icons.Rounded.Shield
        ProtectionPreset.ANTI_EAVESDROPPING -> AppleBlue to Icons.Rounded.Security
        ProtectionPreset.MAX_BUNKER -> Color(0xFF8E24AA) to Icons.Rounded.Lock
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = if (isSelected) 0.22f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "АКТИВЕН",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            color = accentColor
                        )
                    }
                }
            }
            Text(
                text = preset.subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Icon(
            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppleMintGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
