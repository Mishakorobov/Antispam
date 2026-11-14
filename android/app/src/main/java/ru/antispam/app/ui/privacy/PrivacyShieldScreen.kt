package ru.antispam.app.ui.privacy

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.antispam.app.data.local.entity.VaultCallRecordEntity
import ru.antispam.app.privacy.AppPrivacyRisk
import ru.antispam.app.privacy.PrivacyCategory
import ru.antispam.app.privacy.PrivacyRiskLevel
import ru.antispam.app.privacy.VpnClientFormat
import ru.antispam.app.privacy.VpnMaskingHelper
import ru.antispam.app.privacy.model.AccessibilityRiskLevel
import ru.antispam.app.privacy.model.AccessibilityServiceApp
import ru.antispam.app.privacy.model.ListenerRiskLevel
import ru.antispam.app.privacy.model.MaskingTargetApp
import ru.antispam.app.privacy.model.NotificationListenerApp
import ru.antispam.app.privacy.model.PackagePrivacyProfile
import ru.antispam.app.privacy.model.RussianAppRegistry
import ru.antispam.app.privacy.model.SuperAppRiskLevel
import ru.antispam.app.ui.components.AppleGroupedCard
import ru.antispam.app.ui.components.AppleHairlineDivider
import ru.antispam.app.ui.theme.AppleBlue
import ru.antispam.app.ui.theme.AppleMintGreen
import ru.antispam.app.ui.theme.AppleOrange
import ru.antispam.app.ui.theme.ApplePurple
import ru.antispam.app.ui.theme.AppleRed

@Composable
fun PrivacyShieldScreen(
    viewModel: PrivacyShieldViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var coachmarkApp by remember { mutableStateOf<AppPrivacyRisk?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Приватность линии",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Защита вызовов от фонового прослушивания и сбора метаданных",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }

        // Magnetic Onboarding Revocation Success Banner
        if (state.revocationSuccessMessage != null) {
            item {
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppleMintGreen.copy(alpha = 0.15f))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Success",
                                tint = AppleMintGreen,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Доступ ограничен!",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleMintGreen
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = state.revocationSuccessMessage!!,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { viewModel.clearRevocationBanner() }) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Dismiss",
                                    tint = AppleMintGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // Phase 8.2: 2FA Clipboard Shredded Notification Banner
        if (state.clipboardShreddedAlert != null) {
            item {
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppleMintGreen.copy(alpha = 0.15f))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = "Clipboard Shredded",
                                tint = AppleMintGreen,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = state.clipboardShreddedAlert!!,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearClipboardAlert() }) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Dismiss",
                                    tint = AppleMintGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. Live Mic Guard Hero Card
        item {
            AppleGroupedCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isMonitoringActive) AppleMintGreen.copy(alpha = 0.15f)
                                else if (state.isMicGuardEnabled) AppleBlue.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.isMonitoringActive) Icons.Rounded.Mic else Icons.Rounded.Lock,
                            contentDescription = "Shield Icon",
                            tint = if (state.isMonitoringActive) AppleMintGreen else if (state.isMicGuardEnabled) AppleBlue else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isMonitoringActive) "Разговор под защитой" else if (state.isMicGuardEnabled) "Защита линии активна" else "Защита отключена",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.isMonitoringActive) "Аудит микрофона в реальном времени" else "Инспекция захвата звука при звонке",
                            fontSize = 13.sp,
                            color = if (state.isMonitoringActive) AppleMintGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = state.isMicGuardEnabled,
                        onCheckedChange = { viewModel.toggleMicGuard(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppleBlue
                        )
                    )
                }

                AppleHairlineDivider()

                // Mic Jammer Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isJammerActive) AppleRed.copy(alpha = 0.15f)
                                else if (state.isJammerAutoEnabled) AppleOrange.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "Jammer Icon",
                            tint = if (state.isJammerActive) AppleRed else if (state.isJammerAutoEnabled) AppleOrange else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Глушилка микрофона (Jammer)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.isJammerActive) "Jammer активен: шпионам транслируется тишина" else "Монопольный аудиофокус при угрозе",
                            fontSize = 13.sp,
                            color = if (state.isJammerActive) AppleMintGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = state.isJammerAutoEnabled,
                        onCheckedChange = { viewModel.toggleJammerAutoEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppleOrange
                        )
                    )
                }

                AppleHairlineDivider()

                // VIP Mode ("Только контакты" / "Ночной купол")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isVipModeActive) ApplePurple.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NightsStay,
                            contentDescription = "VIP Mode",
                            tint = if (state.isVipModeActive) ApplePurple else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Режим «Только контакты»",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.isVipModeActive) "Активен: тихий режим для неизвестных номеров" else "Ночной купол: тишина от номеров не из книги",
                            fontSize = 13.sp,
                            color = if (state.isVipModeActive) ApplePurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = state.isVipModeActive,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleVipMode(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ApplePurple
                        )
                    )
                }

                AppleHairlineDivider()

                // Foreign +7 Filter (Kazakhstan / Abkhazia 6xx, 7xx)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isBlockForeignPlus7Active) AppleBlue.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = "Foreign +7 Filter",
                            tint = if (state.isBlockForeignPlus7Active) AppleBlue else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Фильтр зарубежного +7",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.isBlockForeignPlus7Active)
                                "Активен: сброс неизвестных +7 (6xx/7xx Казахстан)"
                            else
                                "Блокировать звонки с +7 (Казахстан), кроме контактов",
                            fontSize = 13.sp,
                            color = if (state.isBlockForeignPlus7Active) AppleBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = state.isBlockForeignPlus7Active,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleBlockForeignPlus7(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppleBlue
                        )
                    )
                }

                AppleHairlineDivider()

                // Phase 8.2: 2FA Clipboard Shredder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isClipboardGuardEnabled) AppleMintGreen.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = "Clipboard Guard",
                            tint = if (state.isClipboardGuardEnabled) AppleMintGreen else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Шредер 2FA в буфере",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.isClipboardGuardEnabled)
                                "Активен: самоочистка 2FA-кодов из буфера через 30 сек"
                            else
                                "Очистка кодов из буфера отключена",
                            fontSize = 13.sp,
                            color = if (state.isClipboardGuardEnabled) AppleMintGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = state.isClipboardGuardEnabled,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleClipboardGuard(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppleMintGreen
                        )
                    )
                }
            }
        }

        // Speakerphone Warning Banner
        if (state.isSpeakerphoneOn || state.speakerphoneWarning != null) {
            item {
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppleOrange.copy(alpha = 0.12f))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Speaker Warning",
                                tint = AppleOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Внимание: Громкая связь активна",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleOrange
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Голос собеседника звучит через динамик и физически доступен микрофонам сторонних приложений.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Alert Banner (if microphone capture by 3rd party was detected)
        if (state.lastAlertMessage != null) {
            item {
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    SurfaceAlertBanner(
                        alertText = state.lastAlertMessage!!,
                        onDismiss = { viewModel.clearAlert() }
                    )
                }
            }
        }

        // 3. Privacy Risk App Scanner
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Аудит установленных приложений",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Презумпция невиновности: разделение по уровням доверия",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                IconButton(
                    onClick = { viewModel.refreshScan() },
                    enabled = !state.isScanning
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        }

        // Risk Category Count Pills (3 groups)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RiskPill(
                    label = "🔴 Супераппы",
                    count = state.superappsCount,
                    color = AppleRed
                )
                RiskPill(
                    label = "🟡 Мессенджеры",
                    count = state.messengersCount,
                    color = AppleOrange
                )
                RiskPill(
                    label = "🟢 Системные",
                    count = state.systemAppsCount,
                    color = AppleMintGreen
                )
            }
        }

        // Scanned Apps List
        if (state.scannedApps.isEmpty() && !state.isScanning) {
            item {
                AppleGroupedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Clean",
                            tint = AppleMintGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Подозрительных приложений со связкой Телефон + Микрофон не обнаружено.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            items(state.scannedApps, key = { it.packageName }) { appRisk ->
                AppRiskCardItem(
                    app = appRisk,
                    onOpenSettings = {
                        coachmarkApp = appRisk
                    }
                )
            }
        }

        // Phase 8.1: Notification Listener Spy Auditor (SMS & 2FA Protection)
        item {
            NotificationListenerAuditCard(
                listeners = state.notificationListeners,
                highRiskCount = state.highRiskListenersCount,
                onOpenSettings = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openNotificationListenerSettings()
                }
            )
        }

        // Phase 8.3: Accessibility Services Abuse Auditor
        item {
            AccessibilityAuditCard(
                services = state.accessibilityServices,
                criticalCount = state.criticalAccessibilityCount,
                onOpenSettings = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openAccessibilitySettings()
                }
            )
        }

        // Phase 9.1: SuperApp Total Inspection Auditor (QUERY_ALL_PACKAGES & VPN Surveillance)
        item {
            SuperAppAuditCard(
                profiles = state.superAppProfiles,
                criticalCount = state.superAppCriticalCount,
                highCount = state.superAppHighCount,
                installedVpnClients = state.installedVpnClients,
                isVpnInventoryExposed = state.isVpnInventoryExposed,
                onOpenAppDetails = { pkg ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openAppDetails(pkg)
                }
            )
        }

        // Phase 9.2: VPN Masking & Split-Tunneling Master
        item {
            VpnMaskingCard(
                installedTargetApps = state.installedTargetApps,
                detectedVpnClient = state.detectedVpnClient,
                selectedFormat = state.selectedVpnFormat,
                isConfigCopied = state.isVpnConfigCopied,
                copiedCount = state.copiedAppCount,
                onSelectFormat = { viewModel.selectVpnFormat(it) },
                onCopyConfig = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.copyVpnMaskingConfig(it)
                },
                onShowGuide = { viewModel.showVpnGuide() },
                onLaunchVpn = { viewModel.launchVpnClient(it) },
                onOpenVpnSettings = { viewModel.openVpnSettings() }
            )
        }

        // 4. Call Log Vault (Incognito Mode) with Biometric Lock
        item {
            AppleGroupedCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isVaultEnabled) ApplePurple.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Vault Icon",
                            tint = if (state.isVaultEnabled) ApplePurple else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Сейф звонков (Инкогнито)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.isVaultEnabled) "Скрытие звонков из системного журнала" else "Защита метаданных отключена",
                            fontSize = 13.sp,
                            color = if (state.isVaultEnabled) ApplePurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = state.isVaultEnabled,
                        onCheckedChange = { viewModel.toggleCallVault(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ApplePurple
                        )
                    )
                }

                if (state.isVaultEnabled && state.vaultCalls.isNotEmpty()) {
                    AppleHairlineDivider()

                    if (!state.isVaultUnlocked) {
                        // Blurred Lock Overlay with Biometric Unlock Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Fingerprint,
                                    contentDescription = "Biometric Lock",
                                    tint = ApplePurple,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Сейф заблокирован биометрией",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "В сейфе ${state.vaultCalls.size} скрытых вызовов",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        // Trigger Biometric authentication
                                        viewModel.unlockVault()
                                        Toast.makeText(context, "Сейф разблокирован", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ApplePurple),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(text = "Показать скрытые звонки", fontSize = 13.sp, color = Color.White)
                                }
                            }
                        }
                    } else {
                        // Unlocked View
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "В сейфе: ${state.vaultCalls.size} вызовов",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.lockVault() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(text = "Заблокировать", fontSize = 11.sp, color = ApplePurple)
                                }
                                Button(
                                    onClick = { viewModel.clearVault() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(text = "Очистить", fontSize = 11.sp, color = AppleRed)
                                }
                            }
                        }

                        state.vaultCalls.take(5).forEach { call ->
                            VaultCallItemRow(call = call)
                            AppleHairlineDivider()
                        }
                    }
                }
            }
        }

        // 5. Security Tip Card
        item {
            AppleGroupedCard {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "💡 Как защититься от шпионажа мессенджеров?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "В настройках приложения (например, мессенджера MAX) отзовите доступ к «Телефону» (READ_PHONE_STATE). Без него мессенджер физически не узнает о начале сотового звонка и не сможет скрытно активировать микрофон.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Coachmark Dialog before redirecting to system settings
    coachmarkApp?.let { app ->
        AlertDialog(
            onDismissRequest = { coachmarkApp = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = AppleBlue,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Ограничение доступа к звонкам",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Для приложения «${app.appName}» откроется системное меню Android.",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "👉 Нажмите: Разрешения → Телефон → Запретить.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleBlue
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "После возврата AntiSpam автоматически проверит результат.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = app
                        coachmarkApp = null
                        viewModel.preparePermissionRevocation(target.appName, target.packageName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                ) {
                    Text(text = "Перейти в настройки", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { coachmarkApp = null }) {
                    Text(text = "Отмена")
                }
            }
        )
    }

    // Phase 9.2: VPN Masking Setup Guide Dialog
    if (state.showVpnGuideDialog) {
        VpnSetupGuideDialog(
            format = state.selectedVpnFormat,
            copiedCount = state.copiedAppCount,
            onDismiss = { viewModel.dismissVpnGuideDialog() },
            onLaunchVpn = { viewModel.launchVpnClient(state.selectedVpnFormat) }
        )
    }
}

@Composable
private fun SurfaceAlertBanner(
    alertText: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppleRed.copy(alpha = 0.12f))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = "Alert",
                    tint = AppleRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Попытка перехвата микрофона!",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleRed
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = alertText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AppleRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = "Понятно", fontSize = 12.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun RiskPill(
    label: String,
    count: Int,
    color: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label: $count",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun AppRiskCardItem(
    app: AppPrivacyRisk,
    onOpenSettings: () -> Unit
) {
    AppleGroupedCard {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = app.packageName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // Category & Risk Badge
                val (badgeColor, badgeText) = when (app.category) {
                    PrivacyCategory.SUPERAPP_SURVEILLANCE -> AppleRed to "Суперапп"
                    PrivacyCategory.TRUSTED_MESSENGER -> AppleOrange to "Мессенджер"
                    PrivacyCategory.SYSTEM_DIALER -> AppleMintGreen to "Системный"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.riskDescription,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 17.sp
            )

            if (app.category != PrivacyCategory.SYSTEM_DIALER) {
                Spacer(modifier = Modifier.height(10.dp))
                AppleHairlineDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ограничить доступ к звонкам",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultCallItemRow(call: VaultCallRecordEntity) {
    val typeLabel = when (call.callType) {
        1 -> "Входящий"
        2 -> "Исходящий"
        else -> "Пропущенный"
    }
    val typeColor = when (call.callType) {
        1 -> AppleMintGreen
        2 -> AppleBlue
        else -> AppleRed
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = call.callerName ?: call.formattedNumber,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (call.callerName != null) {
                Text(
                    text = call.formattedNumber,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Text(
                text = "$typeLabel • ${call.durationSeconds} сек • Скрыт из CallLog",
                fontSize = 11.sp,
                color = typeColor
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ApplePurple.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(text = "Vault", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ApplePurple)
        }
    }
}

@Composable
private fun NotificationListenerAuditCard(
    listeners: List<NotificationListenerApp>,
    highRiskCount: Int,
    onOpenSettings: () -> Unit
) {
    AppleGroupedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (highRiskCount > 0) AppleRed.copy(alpha = 0.15f)
                            else AppleMintGreen.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (highRiskCount > 0) Icons.Rounded.Warning else Icons.Rounded.Notifications,
                        contentDescription = "Notification Listener Icon",
                        tint = if (highRiskCount > 0) AppleRed else AppleMintGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Доступ к шторке и 2FA",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (highRiskCount > 0) "Обнаружен риск перехвата СМС-кодов!"
                        else "Аудит доступа к входящим уведомлениям",
                        fontSize = 13.sp,
                        color = if (highRiskCount > 0) AppleRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (highRiskCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleRed.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "⚠️ Внимание: $highRiskCount сторонних приложений имеют доступ к шторке и могут читать 2FA-коды банков и Госуслуг в обход разрешений СМС!",
                        fontSize = 12.sp,
                        color = AppleRed,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else if (listeners.isEmpty()) {
                Text(
                    text = "Шторка чиста: ни одно стороннее приложение не имеет доступа к перехвату уведомлений.",
                    fontSize = 13.sp,
                    color = AppleMintGreen
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Display listener apps
            listeners.take(5).forEach { listener ->
                val badgeColor = when (listener.riskLevel) {
                    ListenerRiskLevel.HIGH -> AppleRed
                    ListenerRiskLevel.MEDIUM -> AppleOrange
                    ListenerRiskLevel.SAFE -> AppleMintGreen
                }
                val badgeText = when (listener.riskLevel) {
                    ListenerRiskLevel.HIGH -> "ОПАСНО: СМС/2FA"
                    ListenerRiskLevel.MEDIUM -> "Носимое устройство"
                    ListenerRiskLevel.SAFE -> "Система"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = listener.appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = listener.riskDescription,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (highRiskCount > 0) AppleRed else AppleBlue
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (highRiskCount > 0) "Отозвать доступ в настройках" else "Настройки доступа к уведомлениям",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AccessibilityAuditCard(
    services: List<AccessibilityServiceApp>,
    criticalCount: Int,
    onOpenSettings: () -> Unit
) {
    AppleGroupedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (criticalCount > 0) AppleRed.copy(alpha = 0.15f)
                            else AppleMintGreen.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (criticalCount > 0) Icons.Rounded.Warning else Icons.Rounded.Security,
                        contentDescription = "Accessibility Icon",
                        tint = if (criticalCount > 0) AppleRed else AppleMintGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Спецвозможности (Accessibility)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (criticalCount > 0) "Критический риск: скрапинг экрана и перехват ввода!"
                        else "Аудит прав чтения экрана и эмуляции кликов",
                        fontSize = 13.sp,
                        color = if (criticalCount > 0) AppleRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (criticalCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleRed.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "🚨 КРИТИЧЕСКАЯ УГРОЗА: $criticalCount сторонних приложений имеют доступ к Спецвозможностям. Они могут читать экран внутри Telegram/банков, перехватывать нажатия клавиш и эмулировать действия!",
                        fontSize = 12.sp,
                        color = AppleRed,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else if (services.isEmpty()) {
                Text(
                    text = "Спецвозможности не используются сторонними приложениями. Экран и клавиатурный ввод защищены.",
                    fontSize = 13.sp,
                    color = AppleMintGreen
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Display active accessibility services
            services.take(5).forEach { service ->
                val badgeColor = when (service.riskLevel) {
                    AccessibilityRiskLevel.CRITICAL -> AppleRed
                    AccessibilityRiskLevel.MEDIUM -> AppleOrange
                    AccessibilityRiskLevel.SAFE -> AppleMintGreen
                }
                val badgeText = when (service.riskLevel) {
                    AccessibilityRiskLevel.CRITICAL -> "КРИТИЧНО"
                    AccessibilityRiskLevel.MEDIUM -> "OEM Система"
                    AccessibilityRiskLevel.SAFE -> "Доверенный AOSP"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = service.appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = service.riskDescription,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (criticalCount > 0) AppleRed else AppleBlue
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (criticalCount > 0) "Отозвать спецвозможности" else "Настройки спецвозможностей",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SuperAppAuditCard(
    profiles: List<PackagePrivacyProfile>,
    criticalCount: Int,
    highCount: Int,
    installedVpnClients: List<String>,
    isVpnInventoryExposed: Boolean,
    onOpenAppDetails: (String) -> Unit
) {
    AppleGroupedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (criticalCount > 0) AppleRed.copy(alpha = 0.15f)
                            else if (highCount > 0) AppleOrange.copy(alpha = 0.15f)
                            else AppleMintGreen.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (criticalCount > 0) Icons.Rounded.Warning else Icons.Rounded.Security,
                        contentDescription = "SuperApp Shield Icon",
                        tint = if (criticalCount > 0) AppleRed else if (highCount > 0) AppleOrange else AppleMintGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SuperApp Shield (Инспекция пакетов)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (criticalCount > 0) "Обнаружено тотальное сканирование программ!"
                        else if (highCount > 0) "Выявлен доступ к общему каталогу приложений"
                        else "Контроль разрешения QUERY_ALL_PACKAGES",
                        fontSize = 13.sp,
                        color = if (criticalCount > 0) AppleRed else if (highCount > 0) AppleOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // VPN Exposure Warning Banner
            if (isVpnInventoryExposed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleOrange.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "⚠️ ВНИМАНИЕ: На устройстве найдены VPN-клиенты (${installedVpnClients.joinToString()}). Супераппы с доступом к полному каталогу могут каталогизировать их и передавать на сервера профилирования!",
                        fontSize = 12.sp,
                        color = AppleOrange,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (criticalCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleRed.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "🚨 КРИТИЧЕСКИЙ РИСК: $criticalCount коммерческих супераппов (MAX/VK) используют разрешение QUERY_ALL_PACKAGES для слежки за установленным ПО и фингерпринтинга устройства.",
                        fontSize = 12.sp,
                        color = AppleRed,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else if (profiles.isEmpty()) {
                Text(
                    text = "Ни одно стороннее приложение не имеет доступа к полному каталогу установленных программ. Ваши VPN-клиенты и утилиты защищены от каталогизации.",
                    fontSize = 13.sp,
                    color = AppleMintGreen
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Display top apps with QUERY_ALL_PACKAGES
            profiles.take(5).forEach { profile ->
                val badgeColor = when (profile.riskLevel) {
                    SuperAppRiskLevel.CRITICAL -> AppleRed
                    SuperAppRiskLevel.HIGH -> AppleOrange
                    SuperAppRiskLevel.MEDIUM -> AppleBlue
                    SuperAppRiskLevel.SAFE -> AppleMintGreen
                }
                val badgeText = when (profile.riskLevel) {
                    SuperAppRiskLevel.CRITICAL -> "СУПЕРАПП"
                    SuperAppRiskLevel.HIGH -> "ТРЕКЕР"
                    SuperAppRiskLevel.MEDIUM -> "УТИЛИТА"
                    SuperAppRiskLevel.SAFE -> "СИСТЕМА"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = profile.riskDescription,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                        if (profile.detectedCapabilities.isNotEmpty()) {
                            Text(
                                text = "Возможности: " + profile.detectedCapabilities.joinToString(" • "),
                                fontSize = 10.sp,
                                color = badgeColor,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = { onOpenAppDetails(profile.packageName) }
                    ) {
                        Text(
                            text = "Ограничить",
                            fontSize = 12.sp,
                            color = AppleBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VpnMaskingCard(
    installedTargetApps: List<MaskingTargetApp>,
    detectedVpnClient: Pair<VpnClientFormat, String>?,
    selectedFormat: VpnClientFormat,
    isConfigCopied: Boolean,
    copiedCount: Int,
    onSelectFormat: (VpnClientFormat) -> Unit,
    onCopyConfig: (VpnClientFormat) -> Unit,
    onShowGuide: () -> Unit,
    onLaunchVpn: (VpnClientFormat) -> Unit,
    onOpenVpnSettings: () -> Unit
) {
    AppleGroupedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ApplePurple.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = "VPN Masking Icon",
                        tint = ApplePurple,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Маскировка VPN (Split-Tunneling)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Скрытие туннеля от банков, Госуслуг и сервисов РФ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Explanation Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ApplePurple.copy(alpha = 0.10f))
                    .padding(12.dp)
            ) {
                Text(
                    text = "🛡️ При включении раздельного туннелирования российские приложения работают напрямую через мобильную сеть (TRANSPORT_VPN = FALSE). Сбер, Т-Банк и Госуслуги открываются без ошибок, а остальной трафик защищен.",
                    fontSize = 12.sp,
                    color = ApplePurple,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Найдено целевых приложений:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "${installedTargetApps.size} из ${RussianAppRegistry.TARGET_SERVICES.size}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleBlue
                )
            }

            if (detectedVpnClient != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Обнаруженный VPN-клиент:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = detectedVpnClient.second,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleMintGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Format Selection Chips
            Text(
                text = "Формат экспорта правил:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VpnClientFormat.values().forEach { format ->
                    val isSelected = selectedFormat == format
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) ApplePurple.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { onSelectFormat(format) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (format) {
                                VpnClientFormat.AMNEZIA_VPN -> "Amnezia"
                                VpnClientFormat.V2RAYNG_NEKOBOX -> "v2ray / Neko"
                                VpnClientFormat.RAW_PACKAGE_LIST -> "Список"
                            },
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) ApplePurple else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Button(
                onClick = { onCopyConfig(selectedFormat) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ApplePurple),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isConfigCopied) Icons.Rounded.CheckCircle else Icons.Rounded.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isConfigCopied) "Скопировано ($copiedCount приложений)" else "Скопировать конфиг исключений",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onShowGuide,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Инструкция",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleBlue
                    )
                }

                if (detectedVpnClient != null) {
                    TextButton(
                        onClick = { onLaunchVpn(selectedFormat) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Открыть VPN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleMintGreen
                        )
                    }
                } else {
                    TextButton(
                        onClick = onOpenVpnSettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "VPN системы",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VpnSetupGuideDialog(
    format: VpnClientFormat,
    copiedCount: Int,
    onDismiss: () -> Unit,
    onLaunchVpn: () -> Unit
) {
    val steps = remember(format) { VpnMaskingHelper.getSetupGuide(format) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = ApplePurple,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Настройка ${format.displayName}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (copiedCount > 0) {
                    Text(
                        text = "✅ Список из $copiedCount целевых приложений скопирован в буфер обмена.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleMintGreen
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                steps.forEach { step ->
                    Text(
                        text = step,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onLaunchVpn()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ApplePurple)
            ) {
                Text(text = "Открыть клиент", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Понятно")
            }
        }
    )
}
