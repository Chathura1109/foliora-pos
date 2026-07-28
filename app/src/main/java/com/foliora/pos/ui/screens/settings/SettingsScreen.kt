package com.foliora.pos.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliora.pos.data.local.entity.SettingEntity
import com.foliora.pos.ui.components.FolioraTopAppBar
import kotlinx.coroutines.launch

/**
 * Stateful screen composable for Shop Settings management and Auth Logout in Foliora POS.
 * Collects state from [SettingsViewModel] and handles save/logout callbacks.
 *
 * @param onLogoutSuccess Callback invoked when user successfully logs out to clear backstack and navigate to Login screen.
 * @param onBackClick Optional callback invoked when top app bar back navigation icon is clicked.
 * @param viewModel Hilt-injected ViewModel instance.
 */
@Composable
fun SettingsScreen(
    onLogoutSuccess: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    SettingsScreenContent(
        settings = settings,
        onBackClick = onBackClick,
        onSaveSettings = { shopName, address, phone, receiptMessage ->
            viewModel.updateSettings(shopName, address, phone, receiptMessage)
        },
        onLogout = {
            viewModel.logout()
            onLogoutSuccess()
        },
        onSyncNow = { viewModel.syncNow() },
        syncStatus = syncStatus,
        isSyncing = isSyncing
    )
}

/**
 * Stateless content composable for Settings Screen.
 * Displays interactive text fields for shop configuration and a red Logout action button.
 *
 * @param settings Current [SettingEntity] state from database.
 * @param onBackClick Navigation callback for back button press.
 * @param onSaveSettings Callback triggered to update shop configuration settings.
 * @param onLogout Callback triggered to initiate user sign-out action.
 * @param modifier Optional [Modifier] for screen container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    settings: SettingEntity?,
    onBackClick: (() -> Unit)?,
    onSaveSettings: (shopName: String, address: String, phone: String, receiptMessage: String) -> Unit,
    onLogout: () -> Unit,
    onSyncNow: () -> Unit = {},
    syncStatus: String = "",
    isSyncing: Boolean = false,
    modifier: Modifier = Modifier
) {
    var shopName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var receiptMessage by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Populate local input states when settings entity is initialized or loaded
    LaunchedEffect(settings) {
        settings?.let {
            shopName = it.shopName
            address = it.address
            phone = it.phoneNumber
            receiptMessage = it.receiptMessage
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FolioraTopAppBar(
                title = "Settings",
                onBackClick = onBackClick
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Shop Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // 1. Shop Name
                        OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = { Text("Shop Name") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = "Shop Name Icon"
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 2. Address
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Address Icon"
                                )
                            },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 3. Phone Number
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Phone Number Icon"
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 4. Receipt Message
                        OutlinedTextField(
                            value = receiptMessage,
                            onValueChange = { receiptMessage = it },
                            label = { Text("Receipt Message") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = "Receipt Message Icon"
                                )
                            },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Save Settings Button
                        Button(
                            onClick = {
                                onSaveSettings(shopName, address, phone, receiptMessage)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Settings saved successfully!")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save Settings",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Save Settings",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Direct Sync to Cloud Button
                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    if (isSyncing) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .then(Modifier.height(20.dp).width(20.dp)),
                            color = MaterialTheme.colorScheme.onSecondary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Icon",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = if (isSyncing) "Syncing..." else "Sync Data to Cloud",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Show sync status text
                if (syncStatus.isNotEmpty()) {
                    Text(
                        text = syncStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (syncStatus.contains("FAILED") || syncStatus.contains("errors"))
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Red colored Logout Button at the bottom
                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout Icon",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Logout",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
