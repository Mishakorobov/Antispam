package ru.antispam.app.ui.whitelist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.antispam.app.data.remote.dto.WhitelistGroupDto
import ru.antispam.app.ui.components.AppleGroupedCard
import ru.antispam.app.ui.components.AppleHairlineDivider
import ru.antispam.app.ui.components.AppleSegmentedControl
import ru.antispam.app.ui.theme.AppleBlue
import ru.antispam.app.ui.theme.AppleMintGreen
import ru.antispam.app.ui.theme.AppleOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(
    viewModel: WhitelistViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Белые списки",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Apple Segmented Control Tabs
            item {
                AppleSegmentedControl(
                    items = listOf("Банки и службы", "Мой белый список"),
                    selectedIndex = state.selectedTab,
                    onItemSelected = { viewModel.selectTab(it) }
                )
            }

            if (state.selectedTab == 0) {
                // Server Groups Section
                item {
                    Text(
                        text = "ОРГАНИЗАЦИИ С ИММУНИТЕТОМ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Официальные номера этих служб защищены от блокировок. Вы можете отключить любую из них (например, звонки Т-Банка) одним переключателем.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                }

                item {
                    AppleGroupedCard {
                        state.serverGroups.forEachIndexed { index, group ->
                            ServerGroupRow(
                                group = group,
                                onToggle = { enable ->
                                    viewModel.toggleServerGroup(group.groupKey, enable)
                                }
                            )
                            if (index < state.serverGroups.lastIndex) {
                                AppleHairlineDivider()
                            }
                        }
                    }
                }
            } else {
                // Local Whitelist Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ДОВЕРЕННЫЕ НОМЕРА",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        TextButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Добавить", color = AppleBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (state.localEntries.isEmpty()) {
                    item {
                        AppleGroupedCard {
                            Text(
                                text = "Список пуст. Вы можете добавить персональные доверенные номера, и они никогда не будут заблокированы.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    }
                } else {
                    item {
                        AppleGroupedCard {
                            state.localEntries.forEachIndexed { index, entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        tint = AppleMintGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "+${entry.phoneNumber}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = entry.label ?: "Доверенный номер",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeLocalNumber(entry.phoneNumber) }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                if (index < state.localEntries.lastIndex) {
                                    AppleHairlineDivider()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Local Number Dialog
        if (showAddDialog) {
            AddNumberDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { num, label ->
                    viewModel.addLocalNumber(num, label)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
private fun ServerGroupRow(
    group: WhitelistGroupDto,
    onToggle: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.groupKey,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = group.isEnabled,
                onCheckedChange = { isChecked ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle(isChecked)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleMintGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        AnimatedVisibility(visible = !group.isEnabled) {
            Text(
                text = "⚠️ Защита отключена. Номера могут блокироваться пользователями.",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AppleOrange,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun AddNumberDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var phoneInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Добавить в белый список", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Номер телефона") },
                    placeholder = { Text("+7 (900) 123-45-67") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text("Метка / Имя (опционально)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (phoneInput.isNotBlank()) onAdd(phoneInput, labelInput) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
            ) {
                Text("Сохранить", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
