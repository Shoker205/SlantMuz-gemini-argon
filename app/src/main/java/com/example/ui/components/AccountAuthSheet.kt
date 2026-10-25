package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.CloudAccountState
import com.example.ui.theme.SlantCyan
import com.example.ui.theme.SlantEmerald
import com.example.ui.theme.SlantMagenta
import com.example.ui.theme.SlantViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountCloudSummaryCard(
    accountState: CloudAccountState,
    onOpenAuthSheet: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAuthSheet)
            .testTag("account_cloud_summary_card"),
        cornerRadius = 22.dp,
        tintColor = if (accountState.isSignedIn) SlantEmerald else SlantCyan
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = if (accountState.isSignedIn) {
                                        listOf(SlantEmerald, SlantCyan)
                                    } else {
                                        listOf(SlantCyan, SlantViolet)
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (accountState.photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = accountState.photoUrl,
                                contentDescription = accountState.displayName,
                                modifier = Modifier.size(48.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = if (accountState.isSignedIn) Icons.Default.CloudDone else Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (accountState.isSignedIn) {
                                    accountState.displayName.ifBlank { "SlantMuz Аккаунт" }
                                } else {
                                    "Аккаунт и облако SlantMuz"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = (if (accountState.isSignedIn) SlantEmerald else SlantCyan).copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = if (accountState.isSignedIn) "CLOUD SYNC" else "ВХОД",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (accountState.isSignedIn) SlantEmerald else SlantCyan,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (accountState.isSignedIn) {
                                accountState.email.ifBlank { accountState.statusMessage }
                            } else {
                                "Сохранение лайков, плейлистов, авторов и 56 поджанров ДНК в облаке"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (accountState.isSignedIn) {
                    IconButton(
                        onClick = onSyncNow,
                        enabled = !accountState.isSyncing,
                        modifier = Modifier.testTag("account_sync_now_btn")
                    ) {
                        if (accountState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = SlantCyan
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Синхронизировать с облаком",
                                tint = SlantCyan
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SlantCyan.copy(alpha = 0.18f),
                        modifier = Modifier.clickable(onClick = onOpenAuthSheet)
                    ) {
                        Text(
                            text = "Войти",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SlantCyan,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (accountState.isSignedIn) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CloudStatPill(
                        label = "Треки: ${accountState.syncedTracksCount}",
                        tint = SlantCyan
                    )
                    CloudStatPill(
                        label = "Авторы ДНК: ${accountState.syncedArtistsCount}",
                        tint = SlantViolet
                    )
                    CloudStatPill(
                        label = "Статус: Синхронизировано",
                        tint = SlantEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudStatPill(label: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAuthSheet(
    accountState: CloudAccountState,
    onSignInWithGoogle: () -> Unit,
    onSignInOrRegisterWithEmail: (email: String, password: String, displayName: String, isRegister: Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var authTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Вход, 1 = Регистрация
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("account_auth_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (accountState.isSignedIn) Icons.Default.CloudDone else Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = if (accountState.isSignedIn) SlantEmerald else SlantCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = if (accountState.isSignedIn) "Ваш облачный профиль" else "Вход и регистрация",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Облачное сохранение лайков, плейлистов и Музыкального ДНК",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
            }

            // Status / Error banner
            if (accountState.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SlantMagenta.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = accountState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlantMagenta,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else if (accountState.statusMessage.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SlantCyan.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (accountState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = SlantCyan
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (accountState.isSignedIn) SlantEmerald else SlantCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = accountState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (accountState.isSignedIn) {
                // Signed-In Dashboard
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    tintColor = SlantEmerald
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(SlantEmerald, SlantCyan))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (accountState.photoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = accountState.photoUrl,
                                        contentDescription = accountState.displayName,
                                        modifier = Modifier.size(54.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = accountState.displayName.take(1).uppercase().ifBlank { "S" },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = accountState.displayName.ifBlank { "Слушатель SlantMuz" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (accountState.email.isNotBlank()) {
                                    Text(
                                        text = accountState.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (accountState.lastSyncedAtMs > 0L) {
                                    val formattedTime = remember(accountState.lastSyncedAtMs) {
                                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                            .format(Date(accountState.lastSyncedAtMs))
                                    }
                                    Text(
                                        text = "Последняя синхронизация: $formattedTime",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SlantEmerald
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CloudStatPill(
                                label = "Лайков в облаке: ${accountState.syncedTracksCount}",
                                tint = SlantCyan
                            )
                            CloudStatPill(
                                label = "Авторов ДНК: ${accountState.syncedArtistsCount}",
                                tint = SlantViolet
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onSyncNow,
                                enabled = !accountState.isSyncing,
                                modifier = Modifier.weight(1f).testTag("sheet_sync_now_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SlantCyan)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (accountState.isSyncing) "Синхронизация…" else "Синхронизировать",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }

                            OutlinedButton(
                                onClick = onSignOut,
                                modifier = Modifier.testTag("sheet_sign_out_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp), tint = SlantMagenta)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Выйти", color = SlantMagenta, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                // 1. Primary Google Sign-In via CredentialManager
                Button(
                    onClick = onSignInWithGoogle,
                    enabled = !accountState.isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SlantCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Войти через Google Аккаунт (1 клик)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                }

                // 2. Email/Password Login & Registration Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Вход по Email", "Регистрация аккаунта").forEachIndexed { idx, label ->
                        val selected = authTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) SlantViolet.copy(alpha = 0.24f)
                                    else Color.Transparent
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) SlantViolet else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { authTab = idx }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected) SlantViolet else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = authTab == 1) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Ваше имя или никнейм") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("auth_name_input")
                    )
                }

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Email адрес") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("auth_email_input")
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Пароль (минимум 6 символов)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Показать пароль"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("auth_password_input")
                )

                Button(
                    onClick = {
                        onSignInOrRegisterWithEmail(
                            emailInput,
                            passwordInput,
                            nameInput,
                            authTab == 1
                        )
                    },
                    enabled = !accountState.isSyncing && emailInput.isNotBlank() && passwordInput.length >= 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("auth_submit_email_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SlantViolet)
                ) {
                    if (accountState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (authTab == 1) "Зарегистрироваться и сохранить ДНК" else "Войти и синхронизировать",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
