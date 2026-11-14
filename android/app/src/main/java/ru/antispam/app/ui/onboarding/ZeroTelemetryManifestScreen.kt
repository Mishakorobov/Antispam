package ru.antispam.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.antispam.app.ui.components.AppleGroupedCard
import ru.antispam.app.ui.components.AppleHairlineDivider
import ru.antispam.app.ui.theme.AppleBlue
import ru.antispam.app.ui.theme.AppleMintGreen
import ru.antispam.app.ui.theme.AppleOrange
import ru.antispam.app.ui.theme.ApplePurple
import ru.antispam.app.ui.theme.AppleRed

/**
 * ZeroTelemetryManifestScreen:
 * Prominently presents the "Zero-Telemetry Manifesto" (Манифест Нулевой Телеметрии)
 * to reinforce AntiSpam Shield's core identity as a sovereign personal telecom firewall.
 *
 * Can be presented as a first-launch onboarding gate or inspected from Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroTelemetryManifestScreen(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    isModal: Boolean = false,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (isModal) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Манифест Приватности",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClose?.invoke()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Закрыть",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = if (isModal) 8.dp else 28.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Hero Header
            item {
                ManifestHeroHeader()
            }

            // 2. High-Impact Privacy Guarantee Card
            item {
                CoreGuaranteeCard()
            }

            // 3. Section Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "5 ПРИНЦИПОВ СУВЕРЕННОЙ ЗАЩИТЫ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // 4. Five Core Pillars Grouped Card
            item {
                AppleGroupedCard {
                    ManifestPillarRow(
                        icon = Icons.Rounded.CloudOff,
                        iconTint = AppleMintGreen,
                        title = "1. Нулевая выгрузка контактов",
                        subtitle = "Zero Cloud Upload",
                        description = "Ваша телефонная книга, история звонков и геопозиция физически никогда не покидают смартфон. Мы не храним и не собираем ваши данные в облаке."
                    )
                    AppleHairlineDivider()
                    ManifestPillarRow(
                        icon = Icons.Rounded.Memory,
                        iconTint = AppleBlue,
                        title = "2. Локальный поиск за 1 мс",
                        subtitle = "On-Device Local-First",
                        description = "Все 10 000 000+ спам-номеров и пулов проверяются локально прямо в процессоре смартфона через бинарный поиск O(log N). Работает в авиарежиме."
                    )
                    AppleHairlineDivider()
                    ManifestPillarRow(
                        icon = Icons.Rounded.Lock,
                        iconTint = ApplePurple,
                        title = "3. Криптография Ed25519",
                        subtitle = "Zero-Trust & Anti-Rollback",
                        description = "База номеров и флаги защищены математической асимметричной подписью. Злоумышленники не могут отравить базу или заблокировать службы 112."
                    )
                    AppleHairlineDivider()
                    ManifestPillarRow(
                        icon = Icons.Rounded.MicOff,
                        iconTint = AppleOrange,
                        title = "4. Анти-шпионский щит связи",
                        subtitle = "Call Privacy Shield",
                        description = "Защита микрофона от прослушки супераппами (MAX), аудит переадресации вызовов (*#21#), блокировка 2G/IMSI перехватчиков и очистка фишинговых ссылок."
                    )
                    AppleHairlineDivider()
                    ManifestPillarRow(
                        icon = Icons.Rounded.Share,
                        iconTint = AppleBlue,
                        title = "5. Офлайн P2P и GMS-Free",
                        subtitle = "Полная автономия",
                        description = "Никакой зависимости от сервисов Google Play и трекеров. Возможность передавать проверенную базу по Bluetooth или Quick Share без интернета."
                    )
                }
            }

            // 5. Open-Core / Transparency Badge
            item {
                TransparencyNotice()
            }

            // 6. Action Buttons
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            OnboardingManager.setManifestAccepted(context, true)
                            if (isModal) {
                                onClose?.invoke()
                            } else {
                                onAccept()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppleBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isModal) "Понятно, всё под защитой" else "Принять Манифест и продолжить",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            OnboardingManager.shareManifest(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AppleBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Поделиться Манифестом",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManifestHeroHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(AppleBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = AppleBlue,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "AntiSpam Shield",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Манифест Нулевой Телеметрии",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppleBlue
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(AppleMintGreen.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "🛡️ 100% On-Device • Никаких облачных баз",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppleMintGreen
            )
        }
    }
}

@Composable
private fun CoreGuaranteeCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = AppleBlue.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ПРИНЦИПИАЛЬНОЕ ОТЛИЧИЕ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleBlue,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "В отличие от коммерческих определителей номеров (GetContact, Яндекс с Алисой, Kaspersky WhoCalls, Truecaller, сервисов Сбера/МТС), мы НИКОГДА не выгружаем вашу телефонную книгу в облако.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Все 10 000 000+ спам-номеров проверяются прямо внутри процессора вашего смартфона за 1 мс без доступа к интернету.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = AppleMintGreen,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ManifestPillarRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = iconTint
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun TransparencyNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Открытый код • Математическая верификация Ed25519 • Без трекеров",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}
