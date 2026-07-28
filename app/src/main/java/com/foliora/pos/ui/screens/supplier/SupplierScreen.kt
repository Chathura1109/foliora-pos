package com.foliora.pos.ui.screens.supplier

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliora.pos.data.local.entity.SupplierEntity
import com.foliora.pos.ui.components.FolioraTopAppBar
import com.foliora.pos.utils.LocationHelper
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Stateful screen composable for Supplier management in Foliora POS.
 * Connects to [SupplierViewModel] and handles GPS location capture using [LocationHelper].
 *
 * @param onBackClick Optional navigation callback when back button is pressed.
 * @param viewModel Hilt-injected ViewModel for supplier state management.
 */
@Composable
fun SupplierScreen(
    onBackClick: (() -> Unit)? = null,
    viewModel: SupplierViewModel = hiltViewModel()
) {
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()

    SupplierScreenContent(
        suppliers = suppliers,
        onBackClick = onBackClick,
        onAddSupplier = { name, phone, address, notes, lat, lng ->
            viewModel.addSupplier(name, phone, address, notes, lat, lng)
        },
        onUpdateSupplier = { supplier ->
            viewModel.updateSupplier(supplier)
        },
        onDeleteSupplier = { supplier ->
            viewModel.deleteSupplier(supplier)
        }
    )
}

/**
 * Stateless content composable for the Supplier Screen.
 * Renders supplier list in a LazyColumn, FAB to add suppliers, and dialogs for CRUD actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierScreenContent(
    suppliers: List<SupplierEntity>,
    onBackClick: (() -> Unit)?,
    onAddSupplier: (name: String, phone: String, address: String, notes: String?, lat: Double?, lng: Double?) -> Unit,
    onUpdateSupplier: (SupplierEntity) -> Unit,
    onDeleteSupplier: (SupplierEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var isAddEditDialogVisible by remember { mutableStateOf(false) }
    var supplierToEdit by remember { mutableStateOf<SupplierEntity?>(null) }
    var supplierToDelete by remember { mutableStateOf<SupplierEntity?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            FolioraTopAppBar(
                title = "Suppliers",
                onBackClick = onBackClick
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    supplierToEdit = null
                    isAddEditDialogVisible = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Supplier"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (suppliers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Business,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No suppliers found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the + button to add a new supplier",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = suppliers,
                        key = { it.id }
                    ) { supplier ->
                        SupplierItem(
                            supplier = supplier,
                            onEditClick = {
                                supplierToEdit = supplier
                                isAddEditDialogVisible = true
                            },
                            onDeleteClick = {
                                supplierToDelete = supplier
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Supplier Dialog
    if (isAddEditDialogVisible) {
        SupplierAddEditDialog(
            supplier = supplierToEdit,
            onDismiss = {
                isAddEditDialogVisible = false
                supplierToEdit = null
            },
            onConfirm = { name, phone, address, notes, lat, lng ->
                if (supplierToEdit != null) {
                    onUpdateSupplier(
                        supplierToEdit!!.copy(
                            name = name,
                            phoneNumber = phone,
                            address = address,
                            notes = notes,
                            latitude = lat,
                            longitude = lng
                        )
                    )
                } else {
                    onAddSupplier(name, phone, address, notes, lat, lng)
                }
                isAddEditDialogVisible = false
                supplierToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (supplierToDelete != null) {
        SupplierDeleteConfirmationDialog(
            supplier = supplierToDelete!!,
            onDismiss = { supplierToDelete = null },
            onConfirm = {
                onDeleteSupplier(supplierToDelete!!)
                supplierToDelete = null
            }
        )
    }
}

/**
 * Card composable displaying supplier details (name, phone, address, optional notes, and GPS status badge).
 */
@Composable
fun SupplierItem(
    supplier: SupplierEntity,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasGps = supplier.latitude != null && supplier.longitude != null

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = supplier.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Row {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit ${supplier.name}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete ${supplier.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Phone Number
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Phone",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = supplier.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Address
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Address",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = supplier.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Notes if available
            if (!supplier.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notes,
                        contentDescription = "Notes",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = supplier.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // GPS location pin indicator
            if (hasGps) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "GPS Saved",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "GPS Saved",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Dialog for adding or editing a supplier.
 * Includes form fields for Name, Phone, Address, Notes, and a button to request permissions and capture GPS location.
 */
@Composable
fun SupplierAddEditDialog(
    supplier: SupplierEntity?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, address: String, notes: String?, lat: Double?, lng: Double?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val locationHelper = remember(context) { LocationHelper(context) }

    val isEditing = supplier != null
    var name by remember(supplier) { mutableStateOf(supplier?.name ?: "") }
    var phoneNumber by remember(supplier) { mutableStateOf(supplier?.phoneNumber ?: "") }
    var address by remember(supplier) { mutableStateOf(supplier?.address ?: "") }
    var notes by remember(supplier) { mutableStateOf(supplier?.notes ?: "") }
    var latitude by remember(supplier) { mutableStateOf(supplier?.latitude) }
    var longitude by remember(supplier) { mutableStateOf(supplier?.longitude) }

    var isCapturingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    // Coroutine function to request GPS coordinates
    val fetchLocation = {
        isCapturingLocation = true
        locationError = null
        coroutineScope.launch {
            val location = locationHelper.getCurrentLocation()
            isCapturingLocation = false
            if (location != null) {
                latitude = location.first
                longitude = location.second
            } else {
                locationError = "Unable to fetch location. Please ensure GPS is enabled."
            }
        }
    }

    // Permission launcher for ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            fetchLocation()
        } else {
            locationError = "Location permission was denied."
        }
    }

    val onCaptureLocationClick = {
        if (locationHelper.hasLocationPermission()) {
            fetchLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Supplier" else "Add Supplier",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Supplier Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // GPS Location Capture Button and status
                OutlinedButton(
                    onClick = { onCaptureLocationClick() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCapturingLocation
                ) {
                    if (isCapturingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetching GPS...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Capture Location",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (latitude != null && longitude != null) "Recapture Current Location" else "Capture Current Location"
                        )
                    }
                }

                // Show captured location coordinates if available
                if (latitude != null && longitude != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(Locale.US, "GPS: %.5f, %.5f", latitude, longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Show error message if location capture failed or permission denied
                if (locationError != null) {
                    Text(
                        text = locationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name.trim(),
                        phoneNumber.trim(),
                        address.trim(),
                        notes.trim().ifEmpty { null },
                        latitude,
                        longitude
                    )
                },
                enabled = name.isNotBlank() && phoneNumber.isNotBlank() && address.isNotBlank()
            ) {
                Text(text = if (isEditing) "Update" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

/**
 * Confirmation dialog before deleting a supplier.
 */
@Composable
fun SupplierDeleteConfirmationDialog(
    supplier: SupplierEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Delete Supplier")
        },
        text = {
            Text(text = "Are you sure you want to delete '${supplier.name}'? This action cannot be undone.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}
