package ru.antispam.app

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.antispam.app.security.AntiTamperHelper
import ru.antispam.app.security.SignatureStatus
import ru.antispam.app.ui.calllog.CallLogScreen
import ru.antispam.app.ui.calllog.CallLogViewModel
import ru.antispam.app.ui.privacy.PrivacyShieldScreen
import ru.antispam.app.ui.privacy.PrivacyShieldViewModel
import ru.antispam.app.ui.settings.SettingsScreen
import ru.antispam.app.ui.settings.SettingsViewModel
import ru.antispam.app.ui.theme.AntiSpamTheme
import ru.antispam.app.ui.theme.AppleBlue
import ru.antispam.app.ui.theme.AppleRed
import ru.antispam.app.ui.whitelist.WhitelistScreen
import ru.antispam.app.ui.whitelist.WhitelistViewModel

class MainActivity : ComponentActivity() {

    private val callLogViewModel: CallLogViewModel by viewModels()
    private val whitelistViewModel: WhitelistViewModel by viewModels()
    private val privacyViewModel: PrivacyShieldViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        callLogViewModel.refreshData()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Phase 7.1: Enforce FLAG_SECURE on Activity window to block screen-scraping & screenshots
        AntiTamperHelper.enforceSecureWindow(this)

        // Phase 13.3: Handle incoming P2P .aspb file opened via Quick Share / File Manager
        handleIncomingAspbIntent(intent)

        setContent {
            AntiSpamTheme {
                MainAppScaffold(
                    callLogViewModel = callLogViewModel,
                    whitelistViewModel = whitelistViewModel,
                    privacyViewModel = privacyViewModel,
                    settingsViewModel = settingsViewModel,
                    onRequestRole = { requestCallScreeningRole() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        callLogViewModel.refreshData()
        privacyViewModel.checkPendingRevocationOnResume()
    }

    private fun requestCallScreeningRole() {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager ?: return
        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            roleRequestLauncher.launch(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingAspbIntent(intent)
    }

    private fun handleIncomingAspbIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            settingsViewModel.importDatabaseFromUri(intent.data!!)
        }
    }
}

data class NavigationTab(
    val title: String,
    val icon: ImageVector
)

@Composable
fun MainAppScaffold(
    callLogViewModel: CallLogViewModel,
    whitelistViewModel: WhitelistViewModel,
    privacyViewModel: PrivacyShieldViewModel,
    settingsViewModel: SettingsViewModel,
    onRequestRole: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    val tabs = listOf(
        NavigationTab("Журнал", Icons.Rounded.Call),
        NavigationTab("Белые списки", Icons.Rounded.Shield),
        NavigationTab("Приватность", Icons.Rounded.Lock),
        NavigationTab("Настройки", Icons.Rounded.Settings)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (selectedTab != index) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTab = index
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppleBlue,
                            selectedTextColor = AppleBlue,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding())

        Column(modifier = modifier) {
            val tamperState by AntiTamperHelper.verificationFlow.collectAsStateWithLifecycle()
            if (tamperState?.status == SignatureStatus.TAMPERED) {
                TamperedWarningBanner(
                    message = tamperState?.message ?: "Обнаружена модифицированная версия приложения!"
                )
            }

            val screenModifier = Modifier.fillMaxSize()
            when (selectedTab) {
                0 -> CallLogScreen(viewModel = callLogViewModel, onRequestRole = onRequestRole, modifier = screenModifier)
                1 -> WhitelistScreen(viewModel = whitelistViewModel, modifier = screenModifier)
                2 -> PrivacyShieldScreen(viewModel = privacyViewModel, modifier = screenModifier)
                3 -> SettingsScreen(viewModel = settingsViewModel, modifier = screenModifier)
            }
        }
    }
}

@Composable
fun TamperedWarningBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppleRed.copy(alpha = 0.15f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = "Предупреждение безопасности",
            tint = AppleRed,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Неофициальная модификация!",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = AppleRed
            )
            Text(
                text = message,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                lineHeight = 15.sp
            )
        }
    }
}
