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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DoneAll
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import ru.antispam.app.privacy.model.AdIdPrivacyProfile
import ru.antispam.app.privacy.model.CallForwardingReport
import ru.antispam.app.privacy.model.CallForwardingType
import ru.antispam.app.privacy.model.CellularGeneration
import ru.antispam.app.privacy.model.CellularRiskLevel
import ru.antispam.app.privacy.model.CellularSecurityReport
import ru.antispam.app.privacy.model.CertificateRiskLevel
import ru.antispam.app.privacy.model.ForwardingRiskLevel
import ru.antispam.app.privacy.model.ListenerRiskLevel
import ru.antispam.app.privacy.model.MaskingTargetApp
import ru.antispam.app.privacy.model.NotificationListenerApp
import ru.antispam.app.privacy.model.PackagePrivacyProfile
import ru.antispam.app.privacy.model.RussianAppRegistry
import ru.antispam.app.privacy.model.SecurityIssue
import ru.antispam.app.privacy.model.SecurityScoreReport
import ru.antispam.app.privacy.model.SuperAppRiskLevel
import ru.antispam.app.privacy.model.TriangulationRiskApp
import ru.antispam.app.privacy.model.TriangulationRiskLevel
import ru.antispam.app.privacy.model.UserInstalledCertificate
import ru.antispam.app.ui.components.AppleGroupedCard
import ru.antispam.app.ui.components.AppleHairlineDivider
import ru.antispam.app.ui.privacy.components.RemediationCelebrationDialog
import ru.antispam.app.ui.privacy.components.RemediationWizardBottomSheet
import ru.antispam.app.ui.privacy.components.SecurityRingWidget
import ru.antispam.app.ui.theme.AppleBlue
import ru.antispam.app.ui.theme.AppleMintGreen
import ru.antispam.app.ui.theme.AppleOrange
import ru.antispam.app.ui.theme.ApplePurple
import ru.antispam.app.ui.theme.AppleRed

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PrivacyShieldScreen(
    viewModel: PrivacyShieldViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var coachmarkApp by remember { mutableStateOf<AppPrivacyRisk?>(null) }

    // Instant verification upon returning from system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

        // Phase 12.4: Phishing Link Detected in Clipboard Alert Banner
        if (state.phishingLinkAlert != null) {
            item {
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppleRed.copy(alpha = 0.15f))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Phishing Alert",
                                tint = AppleRed,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Фишинговая ссылка в буфере!",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleRed
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = state.phishingLinkAlert!!,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { viewModel.clearPhishingAlert() }) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Dismiss",
                                    tint = AppleRed
                                )
                            }
                        }
                    }
                }
            }
        }

        // Phase 10.1: Unified Security & Protection Index Ring Widget
        state.securityScoreReport?.let { report ->
            item {
                SecurityRingWidget(
                    report = report,
                    onResolveIssue = { issue ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.resolveSecurityIssue(issue)
                    },
                    onOneClickRemediate = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.startOneClickRemediation()
                    },
                    isRemediating = state.isRemediationActive,
                    remediationStatus = state.remediationStatusText
                )
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
                            text = "Обнуление аудиопотока (Zero-Buffer)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.isJammerActive) "Zero-Buffer активен: сторонним супераппам отдаются пустые буферы" else "Перехват и обнуление аудиопотока микрофона при угрозе",
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Шредер 2FA в буфере",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (state.shreddedOtpCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AppleMintGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${state.shreddedOtpCount}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppleMintGreen
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (state.isClipboardGuardEnabled) {
                                if (state.shreddedOtpCount > 0)
                                    "Активен: очищено временных паролей: ${state.shreddedOtpCount}"
                                else
                                    "Активен: самоочистка 2FA-кодов из буфера через 30 сек"
                            } else {
                                "Очистка кодов из буфера отключена"
                            },
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

                AppleHairlineDivider()

                // Phase 12.4: Network Link Sanitizer in Clipboard
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
                                if (state.isLinkSanitizerEnabled) AppleBlue.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = "Link Sanitizer",
                            tint = if (state.isLinkSanitizerEnabled) AppleBlue else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Санитайзер ссылок",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (state.sanitizedLinksCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AppleBlue.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${state.sanitizedLinksCount}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppleBlue
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (state.isLinkSanitizerEnabled) {
                                if (state.sanitizedLinksCount > 0)
                                    "Активен: очищено ссылок от слежки: ${state.sanitizedLinksCount}"
                                else
                                    "Активен: удаление utm-меток, yclid и проверка фишинга"
                            } else {
                                "Очистка ссылок от трекеров слежки отключена"
                            },
                            fontSize = 13.sp,
                            color = if (state.isLinkSanitizerEnabled) AppleBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = state.isLinkSanitizerEnabled,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleLinkSanitizer(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppleBlue
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

        // Risk Category Count Pills (Unified Threat Groups)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RiskPill(
                    label = "🔴 Критические",
                    count = state.criticalAppsCount,
                    color = AppleRed
                )
                RiskPill(
                    label = "🟠 Высокий риск",
                    count = state.highAppsCount,
                    color = AppleOrange
                )
                RiskPill(
                    label = "🔵 Умеренные",
                    count = state.scannedApps.count { it.riskLevel == PrivacyRiskLevel.MEDIUM },
                    color = AppleBlue
                )
            }
        }

        // Scanned Apps List (All threats audited: SMS/Notification, A11y, SuperApp, Phone/Mic, Geo, Overlay)
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
                            text = "Уязвимостей в сторонних приложениях не обнаружено. Спящие и доверенные приложения скрыты.",
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
                    onOpenAction = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.handleAppThreatAction(appRisk, context)
                    },
                    onTrustApp = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.trustSuperApp(appRisk.packageName)
                    }
                )
            }
        }

        // Phase 8.1 & 11.3: Notification Listener Spy Auditor (SMS & 2FA Protection & Whitelist)
        item {
            NotificationListenerAuditCard(
                listeners = state.notificationListeners,
                highRiskCount = state.highRiskListenersCount,
                onOpenSettings = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openNotificationListenerSettings()
                },
                onToggleTrust = { pkg ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleTrustNotificationListener(pkg)
                }
            )
        }

        // Phase 8.3 & 11.3: Accessibility Services Abuse Auditor (Screen Scrape & Whitelist)
        item {
            AccessibilityAuditCard(
                services = state.accessibilityServices,
                criticalCount = state.criticalAccessibilityCount,
                onOpenSettings = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openAccessibilitySettings()
                },
                onToggleTrust = { pkg ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleTrustAccessibilityService(pkg)
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
                },
                onOpenDeepSleep = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openDeepSleepSettings()
                },
                onTrustApp = { pkg ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.trustSuperApp(pkg)
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
                isRoutingConfigured = state.isVpnRoutingConfigured,
                copiedCount = state.copiedAppCount,
                onSelectFormat = { viewModel.selectVpnFormat(it) },
                onCopyConfig = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.copyVpnMaskingConfig(it)
                },
                onToggleRoutingConfigured = { configured ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleVpnRoutingConfigured(configured)
                },
                onShowGuide = { viewModel.showVpnGuide() },
                onLaunchVpn = { viewModel.launchVpnClient(it) },
                onOpenVpnSettings = { viewModel.openVpnSettings() }
            )
        }

        // Phase 9.3: Stealth Geo-Triangulation without GPS Auditor
        item {
            TriangulationAuditCard(
                apps = state.triangulationApps,
                criticalCount = state.triangulationCriticalCount,
                highCount = state.triangulationHighCount,
                onOpenLocationSettings = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openLocationSettings()
                },
                onOpenAppDetails = { pkg ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openAppDetails(pkg)
                }
            )
        }

        // Phase 9.4: Root Certificate Authority & MITM Inspector
        item {
            CertificateAuditCard(
                certificates = state.userCertificates,
                criticalCount = state.certMitmCriticalCount,
                warningCount = state.certWarningCount,
                onOpenSecuritySettings = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openSecuritySettings()
                }
            )
        }

        // Phase 9.5: Advertising Identifier Reset & Tracking Shield
        item {
            AdIdCard(
                profile = state.adIdProfile,
                onOpenAdvertisingSettings = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openAdvertisingSettings()
                }
            )
        }

        // Phase 12.2: Call Forwarding & Covert Diversion Auditor
        item {
            CallForwardingAuditCard(
                report = state.callForwardingReport,
                onResetAll = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openResetCallForwardingDialer()
                },
                onQueryForwarding = { type ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.openQueryCallForwardingDialer(type)
                },
                onConfirmClean = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.markCallForwardingCleared()
                }
            )
        }

        // Phase 12.3: Cellular Downgrade Shield (Anti-IMSI-Catcher)
        item {
            CellularSecurityCard(
                report = state.cellularSecurityReport,
                onOpenCellularSettings = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.openCellularSettings()
                }
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

    // One-Click Guided Remediation Wizard Bottom Sheet
    if (state.isWizardBottomSheetVisible) {
        RemediationWizardBottomSheet(
            plan = state.remediationPlan,
            currentStepIndex = state.currentRemediationStepIndex,
            onResolveStep = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.resolveCurrentWizardStep(context)
            },
            onTrustApp = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.trustCurrentWizardApp()
            },
            onDismiss = {
                viewModel.dismissWizard()
            }
        )
    }

    // 100% Security Protection Index Celebration Dialog
    if (state.showRemediationCelebration) {
        RemediationCelebrationDialog(
            score = state.securityScoreReport?.score ?: 100,
            onDismiss = { viewModel.dismissWizard() }
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
    onOpenAction: () -> Unit,
    onTrustApp: (() -> Unit)? = null
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

                // Risk Badge
                val (badgeColor, badgeText) = when (app.riskLevel) {
                    PrivacyRiskLevel.CRITICAL -> AppleRed to "Критический"
                    PrivacyRiskLevel.HIGH -> AppleOrange to "Высокий"
                    PrivacyRiskLevel.MEDIUM -> AppleBlue to "Умеренный"
                    PrivacyRiskLevel.SAFE -> AppleMintGreen to "Безопасно"
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

            // Threat Badges
            if (app.threatBadges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    app.threatBadges.forEach { badge ->
                        val bColor = when {
                            badge.contains("СМС") || badge.contains("Спецвозможности") || badge.contains("Критич") -> AppleRed
                            badge.contains("Инвентарь") || badge.contains("Оверлей") || badge.contains("Звонки") -> AppleOrange
                            else -> AppleBlue
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(bColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = bColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.riskDescription,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            AppleHairlineDivider()
            Spacer(modifier = Modifier.height(8.dp))

            val actionText = when (app.primaryActionType) {
                ru.antispam.app.privacy.AppThreatActionType.REVOKE_NOTIFICATION_LISTENER -> "Отключить доступ к СМС/шторке"
                ru.antispam.app.privacy.AppThreatActionType.MANAGE_ACCESSIBILITY -> "Отключить спецвозможности"
                ru.antispam.app.privacy.AppThreatActionType.PUT_IN_DEEP_SLEEP -> "В глубокий сон (заморозить)"
                ru.antispam.app.privacy.AppThreatActionType.TRUST_APP -> "Добавить в доверенные"
                ru.antispam.app.privacy.AppThreatActionType.MANAGE_PERMISSIONS -> "Отозвать разрешения"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenAction,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = actionText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleBlue,
                        maxLines = 1
                    )
                }

                if (onTrustApp != null && (app.hasQueryAllPackages || app.riskLevel == PrivacyRiskLevel.MEDIUM)) {
                    OutlinedButton(
                        onClick = onTrustApp,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Доверять",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
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
    onOpenSettings: () -> Unit,
    onToggleTrust: (String) -> Unit
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
                val badgeColor = when {
                    listener.isTrusted -> AppleMintGreen
                    listener.isDeepSleeping -> AppleMintGreen
                    listener.riskLevel == ListenerRiskLevel.HIGH -> AppleRed
                    listener.riskLevel == ListenerRiskLevel.MEDIUM -> AppleOrange
                    else -> AppleMintGreen
                }
                val badgeText = when {
                    listener.isTrusted -> "Доверено"
                    listener.isDeepSleeping -> "Глубокий сон"
                    listener.riskLevel == ListenerRiskLevel.HIGH -> "ОПАСНО: СМС/2FA"
                    listener.riskLevel == ListenerRiskLevel.MEDIUM -> "Носимое устройство"
                    else -> "Система"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = listener.appName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (listener.isTrusted) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Trusted",
                                    tint = AppleMintGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = if (listener.isTrusted) "Приложение добавлено в белый список доверенных сервисов. Штраф отключен."
                                   else listener.riskDescription,
                            fontSize = 11.sp,
                            color = if (listener.isTrusted) AppleMintGreen.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(horizontalAlignment = Alignment.End) {
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

                        // Phase 11.3: 1-click whitelist button
                        if (!listener.isSystemApp) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (listener.isTrusted) "Отозвать" else "Доверять",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (listener.isTrusted) AppleOrange else AppleBlue,
                                modifier = Modifier
                                    .clickable { onToggleTrust(listener.packageName) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
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
    onOpenSettings: () -> Unit,
    onToggleTrust: (String) -> Unit
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
                val badgeColor = when {
                    service.isTrusted -> AppleMintGreen
                    service.isDeepSleeping -> AppleMintGreen
                    service.riskLevel == AccessibilityRiskLevel.CRITICAL -> AppleRed
                    service.riskLevel == AccessibilityRiskLevel.MEDIUM -> AppleOrange
                    else -> AppleMintGreen
                }
                val badgeText = when {
                    service.isTrusted -> "Доверено"
                    service.isDeepSleeping -> "Глубокий сон"
                    service.riskLevel == AccessibilityRiskLevel.CRITICAL -> "КРИТИЧНО"
                    service.riskLevel == AccessibilityRiskLevel.MEDIUM -> "OEM Система"
                    else -> "Доверенный AOSP"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = service.appName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (service.isTrusted) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Trusted",
                                    tint = AppleMintGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = if (service.isTrusted) "Приложение (менеджер паролей/сервис) добавлено в белый список. Штраф снят."
                                   else service.riskDescription,
                            fontSize = 11.sp,
                            color = if (service.isTrusted) AppleMintGreen.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(horizontalAlignment = Alignment.End) {
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

                        // Phase 11.3: 1-click whitelist button for non-system apps
                        if (!service.isSystemApp) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (service.isTrusted) "Отозвать" else "Доверять",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (service.isTrusted) AppleOrange else AppleBlue,
                                modifier = Modifier
                                    .clickable { onToggleTrust(service.packageName) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
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
    onOpenAppDetails: (String) -> Unit,
    onOpenDeepSleep: () -> Unit = {},
    onTrustApp: (String) -> Unit = {}
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

            // Explanation about QUERY_ALL_PACKAGES
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(10.dp)
            ) {
                Text(
                    text = "ℹ️ Разрешение QUERY_ALL_PACKAGES выдается навсегда при установке и не отображается в списке обычных разрешений. Чтобы приложение перестало сканировать устройство, добавьте его в «Глубокий сон» (заморозка) или пометьте как доверенное.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                val badgeColor = when {
                    profile.isDeepSleeping -> AppleMintGreen
                    profile.riskLevel == SuperAppRiskLevel.CRITICAL -> AppleRed
                    profile.riskLevel == SuperAppRiskLevel.HIGH -> AppleOrange
                    profile.riskLevel == SuperAppRiskLevel.MEDIUM -> AppleBlue
                    else -> AppleMintGreen
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

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onOpenDeepSleep
                        ) {
                            Text(
                                text = "В сон",
                                fontSize = 12.sp,
                                color = AppleOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(
                            onClick = { onTrustApp(profile.packageName) }
                        ) {
                            Text(
                                text = "Доверять",
                                fontSize = 12.sp,
                                color = AppleBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = { onOpenAppDetails(profile.packageName) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = "Инфо",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
    isRoutingConfigured: Boolean,
    copiedCount: Int,
    onSelectFormat: (VpnClientFormat) -> Unit,
    onCopyConfig: (VpnClientFormat) -> Unit,
    onToggleRoutingConfigured: (Boolean) -> Unit,
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

            // Routing Configured Status Banner
            if (isRoutingConfigured) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleMintGreen.copy(alpha = 0.15f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = AppleMintGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Раздельное проксирование активно",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppleMintGreen
                            )
                            Text(
                                text = "Российские сервисы идут в обход VPN. Угроза сканирования туннеля снята.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        TextButton(onClick = { onToggleRoutingConfigured(false) }) {
                            Text(text = "Сброс", fontSize = 12.sp, color = AppleBlue)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

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
                                VpnClientFormat.V2RAYTUN -> "v2RayTun"
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

            if (!isRoutingConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onToggleRoutingConfigured(true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = AppleMintGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Я настроил маршрутизацию в VPN",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
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

@Composable
private fun TriangulationAuditCard(
    apps: List<TriangulationRiskApp>,
    criticalCount: Int,
    highCount: Int,
    onOpenLocationSettings: () -> Unit,
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
                        imageVector = if (criticalCount > 0) Icons.Rounded.Warning else Icons.Rounded.Public,
                        contentDescription = "Triangulation Icon",
                        tint = if (criticalCount > 0) AppleRed else if (highCount > 0) AppleOrange else AppleMintGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Скрытая геолокация (Без GPS)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (criticalCount > 0) "Триангуляция до квартиры без спутников!"
                        else if (highCount > 0) "Обнаружен сбор данных Wi-Fi или вышек"
                        else "Защита от скрытого отслеживания координат",
                        fontSize = 13.sp,
                        color = if (criticalCount > 0) AppleRed else if (highCount > 0) AppleOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                        text = "🚨 КРИТИЧЕСКАЯ УГРОЗА: $criticalCount приложений способны вычислять ваш точный домашний адрес без включения GPS через триангуляцию по BSSID Wi-Fi роутеров и Cell ID сотовых вышек!",
                        fontSize = 12.sp,
                        color = AppleRed,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else if (highCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleOrange.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "⚠️ ВНИМАНИЕ: $highCount приложений запрашивают доступ к сканированию сетей Wi-Fi или сотовым вышкам в связке с геолокацией.",
                        fontSize = 12.sp,
                        color = AppleOrange,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else if (apps.isEmpty()) {
                Text(
                    text = "Скрытая мультивекторная триангуляция не обнаружена. Ваши координаты защищены от негласного сбора при отключенном GPS.",
                    fontSize = 13.sp,
                    color = AppleMintGreen
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Top apps display
            apps.take(5).forEach { app ->
                val badgeColor = when {
                    app.isDeepSleeping -> AppleMintGreen
                    app.riskLevel == TriangulationRiskLevel.CRITICAL -> AppleRed
                    app.riskLevel == TriangulationRiskLevel.HIGH -> AppleOrange
                    app.riskLevel == TriangulationRiskLevel.MEDIUM -> AppleBlue
                    else -> AppleMintGreen
                }
                val badgeText = when {
                    app.isDeepSleeping -> "Глубокий сон"
                    app.riskLevel == TriangulationRiskLevel.CRITICAL -> "ТРИАНГУЛЯЦИЯ"
                    app.riskLevel == TriangulationRiskLevel.HIGH -> "СБОР BSSID/ВЫШЕК"
                    app.riskLevel == TriangulationRiskLevel.MEDIUM -> "НАВИГАТОР/ГЕО"
                    else -> "СИСТЕМА"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = app.riskDescription,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                        if (app.warningBadges.isNotEmpty()) {
                            Text(
                                text = "Векторы: " + app.warningBadges.joinToString(" • "),
                                fontSize = 10.sp,
                                color = badgeColor,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = { onOpenAppDetails(app.packageName) }
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

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onOpenLocationSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (criticalCount > 0) AppleRed else AppleBlue
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Настройки геолокации системы",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CertificateAuditCard(
    certificates: List<UserInstalledCertificate>,
    criticalCount: Int,
    warningCount: Int,
    onOpenSecuritySettings: () -> Unit
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
                            else if (warningCount > 0) AppleOrange.copy(alpha = 0.15f)
                            else AppleMintGreen.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (criticalCount > 0) Icons.Rounded.Warning else Icons.Rounded.Lock,
                        contentDescription = "Certificate Icon",
                        tint = if (criticalCount > 0) AppleRed else if (warningCount > 0) AppleOrange else AppleMintGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Корневые сертификаты (Root CA)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (criticalCount > 0) "Критический риск MITM-перехвата HTTPS!"
                        else if (warningCount > 0) "Установлен государственный сертификат РФ"
                        else "Инспекция пользовательского хранилища Android",
                        fontSize = 13.sp,
                        color = if (criticalCount > 0) AppleRed else if (warningCount > 0) AppleOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                        text = "🚨 КРИТИЧЕСКАЯ УГРОЗА MITM: Обнаружено $criticalCount неизвестных пользовательских Root CA. Весь ваш зашифрованный HTTPS-трафик (пароли, личные сообщения, банки) может перехватываться и расшифровываться!",
                        fontSize = 12.sp,
                        color = AppleRed,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else if (warningCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleOrange.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "⚠️ ВНИМАНИЕ: Установлен сертификат Минцифры РФ. Он необходим для сайтов с российскими SSL-сертификатами, однако дает операторам связи потенциальную возможность расшифровки трафика.",
                        fontSize = 12.sp,
                        color = AppleOrange,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else if (certificates.isEmpty()) {
                Text(
                    text = "Пользовательские Root CA не установлены. Ваш зашифрованный HTTPS-трафик защищен от негласного MITM-перехвата.",
                    fontSize = 13.sp,
                    color = AppleMintGreen
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Display user certificates
            certificates.take(5).forEach { cert ->
                val badgeColor = when (cert.riskLevel) {
                    CertificateRiskLevel.CRITICAL -> AppleRed
                    CertificateRiskLevel.WARNING -> AppleOrange
                    CertificateRiskLevel.MEDIUM -> AppleBlue
                }
                val badgeText = when (cert.riskLevel) {
                    CertificateRiskLevel.CRITICAL -> "УГРОЗА MITM"
                    CertificateRiskLevel.WARNING -> "МИНЦИФРЫ РФ"
                    CertificateRiskLevel.MEDIUM -> "ФИЛЬТР ТРАФИКА"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cert.commonName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (cert.organization.isNotBlank()) {
                            Text(
                                text = "Организация: ${cert.organization}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = cert.riskDescription,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                        Text(
                            text = "SHA-256: " + cert.sha256Fingerprint.take(23) + "...",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
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
                onClick = onOpenSecuritySettings,
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
                    text = if (criticalCount > 0) "Удалить опасные сертификаты" else "Хранилище сертификатов",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AdIdCard(
    profile: AdIdPrivacyProfile?,
    onOpenAdvertisingSettings: () -> Unit
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
                        .background(AppleBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = "Ad ID Icon",
                        tint = AppleBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Сквозной рекламный трекер",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = profile?.trackingPlatformName ?: "GAID / OAID профилирование",
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
                    .background(AppleBlue.copy(alpha = 0.10f))
                    .padding(12.dp)
            ) {
                Text(
                    text = "🛡️ Рекламные сети (MyTracker, AppMetrica, Google, Яндекс) используют рекламный ID для связывания всех ваших действий в разных приложениях в один цифровой профиль.",
                    fontSize = 12.sp,
                    color = AppleBlue,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            profile?.resetSteps?.forEach { step ->
                Text(
                    text = step,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onOpenAdvertisingSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppleBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Открыть настройки рекламы (Сброс ID)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Phase 12.2: CallForwardingAuditCard
 * Visual inspection of active call forwardings and 1-tap USSD reset (##002#)
 */
@Composable
private fun CallForwardingAuditCard(
    report: CallForwardingReport,
    onResetAll: () -> Unit,
    onQueryForwarding: (CallForwardingType) -> Unit,
    onConfirmClean: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val isDanger = report.hasActiveForwarding || report.riskLevel == ForwardingRiskLevel.CRITICAL
    val isWarning = report.riskLevel == ForwardingRiskLevel.WARNING
    val statusColor = when {
        isDanger -> AppleRed
        isWarning -> AppleOrange
        else -> AppleMintGreen
    }

    AppleGroupedCard {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDanger) Icons.Rounded.Warning else Icons.Rounded.Call,
                        contentDescription = "Call Forwarding Icon",
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Переадресация вызовов",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = when {
                                    isDanger -> "ОПАСНОСТЬ"
                                    isWarning -> "ВНИМАНИЕ"
                                    else -> "БЕЗОПАСНО"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isDanger) "Обнаружена активная переадресация!" else "Защита от скрытого перехвата 2FA и звонков",
                        fontSize = 13.sp,
                        color = if (isDanger) AppleRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Explanation / Status summary
            Text(
                text = report.summary.ifEmpty {
                    "Шпионские трояны или посторонние лица могут скрытно включить переадресацию (*21*), перехватывая звонки банков и голосовые 2FA-коды."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                lineHeight = 18.sp
            )

            if (isDanger) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppleRed.copy(alpha = 0.12f))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = AppleRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Внимание: вызовы перенаправляются на другой номер! Немедленно выполните сброс.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppleRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary 1-Click Reset Button ##002#
            Button(
                onClick = onResetAll,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDanger) AppleRed else AppleBlue
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Сбросить все переадресации (##002#)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Expandable details for checking specific rules (*#21#, *#004#, etc.)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Скрыть детальные проверки" else "Проверить отдельные переадресации (*#21#, *#004#)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppleBlue
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 12.sp,
                    color = AppleBlue
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppleHairlineDivider()

                    // Unconditional CFU (*#21#)
                    ForwardingQueryRow(
                        title = "Безусловная (*#21#)",
                        subtitle = "Перенаправление всех звонков без исключения",
                        code = "*#21#",
                        onQuery = { onQueryForwarding(CallForwardingType.UNCONDITIONAL) }
                    )

                    AppleHairlineDivider()

                    // All conditional (*#004#)
                    ForwardingQueryRow(
                        title = "Все условные (*#004#)",
                        subtitle = "При недоступности, занятости и неответе",
                        code = "*#004#",
                        onQuery = { onQueryForwarding(CallForwardingType.ALL_CONDITIONAL) }
                    )

                    AppleHairlineDivider()

                    // Unreachable (*#62#)
                    ForwardingQueryRow(
                        title = "При недоступности (*#62#)",
                        subtitle = "Когда телефон выключен или вне зоны сети",
                        code = "*#62#",
                        onQuery = { onQueryForwarding(CallForwardingType.UNREACHABLE) }
                    )

                    AppleHairlineDivider()

                    // Confirm Clean Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onConfirmClean) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = AppleMintGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Отметить как проверенное",
                                fontSize = 12.sp,
                                color = AppleMintGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardingQueryRow(
    title: String,
    subtitle: String,
    code: String,
    onQuery: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Button(
            onClick = onQuery,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = code,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AppleBlue
            )
        }
    }
}

/**
 * Phase 12.3: CellularSecurityCard
 * Displays radio network generation, IMSI-catcher downgrade risks,
 * and 1-tap navigation to system SIM settings with instruction to disable 2G.
 */
@Composable
private fun CellularSecurityCard(
    report: CellularSecurityReport,
    onOpenCellularSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val isDanger = report.is2GActive || report.riskLevel == CellularRiskLevel.CRITICAL
    val isWarning = report.riskLevel == CellularRiskLevel.WARNING
    val statusColor = when {
        isDanger -> AppleRed
        isWarning -> AppleOrange
        else -> AppleMintGreen
    }

    AppleGroupedCard {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDanger) Icons.Rounded.Warning else Icons.Rounded.Public,
                        contentDescription = "Cellular Shield Icon",
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Защита радиоэфира",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = when {
                                    isDanger -> "УГРОЗА 2G"
                                    isWarning -> "СЕТЬ 3G"
                                    else -> "ЗАЩИЩЕНО (${report.generation.code})"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Текущий стандарт: ${report.networkTypeName}",
                        fontSize = 13.sp,
                        color = if (isDanger) AppleRed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Explanation / Summary
            Text(
                text = report.summary.ifEmpty {
                    "Ложные базовые станции (IMSI-Catcher) глушат 4G/LTE, принуждая телефон переключиться в 2G (GSM), где шифрование A5/1 взламывается на лету или отсутствует вовсе (A5/0)."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                lineHeight = 18.sp
            )

            if (isDanger) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppleRed.copy(alpha = 0.12f))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = AppleRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Внимание: радиоэфир 2G уязвим к перехвату разговоров и СМС. Отключите 2G в настройках сети прямо сейчас!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppleRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Button to open Cellular Settings
            Button(
                onClick = onOpenCellularSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDanger) AppleRed else AppleBlue
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Настройки сотовой сети (Отключить 2G)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Expandable Instructions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Скрыть инструкцию защиты от 2G" else "Как отключить 2G в Android (Инструкция)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppleBlue
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 12.sp,
                    color = AppleBlue
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppleHairlineDivider()

                    Text(
                        text = "Пошаговая инструкция защиты от IMSI-Catcher:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val steps = listOf(
                        "1. Нажмите кнопку «Настройки сотовой сети» выше.",
                        "2. Выберите вашу активную SIM-карту (например, SIM 1 или Т-Банк / МТС).",
                        "3. Найдите пункт «Разрешить 2G» (Allow 2G).",
                        "4. Переведите переключатель в положение ВЫКЛ. Смартфон больше никогда не подключится к уязвимым поддельным вышкам."
                    )

                    steps.forEach { step ->
                        Text(
                            text = step,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = 16.sp
                        )
                    }

                    if (report.isNullCipherAlertSupported) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppleMintGreen.copy(alpha = 0.12f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "🛡️ Android 14+: Уведомления об отсутствии шифрования (Null Cipher) ${if (report.isNullCipherAlertEnabled == true) "активны" else "поддерживаются системой"}.",
                                fontSize = 11.sp,
                                color = AppleMintGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
