@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jasermohamed.bumpcompanion.ui.bumps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.model.*
import com.jasermohamed.bumpcompanion.ui.components.EmptyState
import java.text.DateFormat
import java.util.Date

@Composable
fun BumpsRoute(viewModel: BumpsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.bumps), fontWeight = FontWeight.SemiBold) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::setSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_bumps)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )
            ScrollableTabRow(selectedTabIndex = filterIndex(state.filter), edgePadding = 16.dp) {
                FilterTab(null, state.filter, stringResource(R.string.all), viewModel::setFilter)
                FilterTab(BumpStatus.CONFIRMED, state.filter, stringResource(R.string.confirmed), viewModel::setFilter)
                FilterTab(BumpStatus.PENDING, state.filter, stringResource(R.string.pending), viewModel::setFilter)
                FilterTab(BumpStatus.IMPORTED, state.filter, stringResource(R.string.imported), viewModel::setFilter)
                FilterTab(BumpStatus.ARCHIVED, state.filter, stringResource(R.string.archived), viewModel::setFilter)
                FilterTab(BumpStatus.REMOVED, state.filter, stringResource(R.string.removed), viewModel::setFilter)
            }
            if (state.items.isEmpty()) {
                EmptyState(stringResource(R.string.no_bumps), stringResource(R.string.privacy_local), Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        BumpItem(
                            item,
                            onEdit = { viewModel.select(item) },
                            onToggleWarning = { viewModel.toggleWarning(item) },
                            onArchive = { viewModel.archive(item) },
                            onRemoved = { viewModel.markRemoved(item) },
                            onDelete = { viewModel.delete(item) },
                            onExternal = { viewModel.openExternal(item) },
                        )
                    }
                }
            }
        }
    }
    state.selected?.let { EditBumpDialog(it, onDismiss = { viewModel.select(null) }, onSave = viewModel::save) }
}

@Composable
private fun FilterTab(value: BumpStatus?, selected: BumpStatus?, label: String, onSelect: (BumpStatus?) -> Unit) {
    Tab(selected = value == selected, onClick = { onSelect(value) }, text = { Text(label) })
}

private fun filterIndex(value: BumpStatus?): Int = when (value) {
    null -> 0
    BumpStatus.CONFIRMED -> 1
    BumpStatus.PENDING -> 2
    BumpStatus.IMPORTED -> 3
    BumpStatus.ARCHIVED -> 4
    BumpStatus.REMOVED -> 5
}

@Composable
private fun BumpItem(
    item: SpeedBump,
    onEdit: () -> Unit,
    onToggleWarning: () -> Unit,
    onArchive: () -> Unit,
    onRemoved: () -> Unit,
    onDelete: () -> Unit,
    onExternal: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.Speed,
                contentDescription = null,
                tint = when (item.status) {
                    BumpStatus.CONFIRMED -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
                    BumpStatus.REMOVED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.roadName ?: item.regionLabel ?: stringResource(R.string.unknown_location), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.coordinates_format, item.latitude, item.longitude), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pluralStringResource(R.plurals.seen_count, item.encounterCount, item.encounterCount), style = MaterialTheme.typography.bodySmall)
                Text("${stringResource(R.string.last_seen)}: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.lastDetectedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AssistChip(
                    onClick = onToggleWarning,
                    label = { Text(stringResource(if (item.warningEnabled) R.string.warning_enabled else R.string.warning_disabled)) },
                    leadingIcon = { Icon(if (item.warningEnabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.open_coordinate_external)) }, onClick = { menu = false; onExternal() }, leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) })
                    DropdownMenuItem(text = { Text(stringResource(if (item.archived) R.string.confirmed else R.string.archive)) }, onClick = { menu = false; onArchive() }, leadingIcon = { Icon(Icons.Outlined.Archive, null) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.mark_removed)) }, onClick = { menu = false; onRemoved() }, leadingIcon = { Icon(Icons.Outlined.RemoveRoad, null) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                }
            }
        }
    }
}

@Composable
private fun EditBumpDialog(item: SpeedBump, onDismiss: () -> Unit, onSave: (SpeedBump) -> Unit) {
    var roadName by remember(item.id) { mutableStateOf(item.roadName.orEmpty()) }
    var region by remember(item.id) { mutableStateOf(item.regionLabel.orEmpty()) }
    var notes by remember(item.id) { mutableStateOf(item.notes) }
    var latitude by remember(item.id) { mutableStateOf(item.latitude.toString()) }
    var longitude by remember(item.id) { mutableStateOf(item.longitude.toString()) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(roadName, { roadName = it }, label = { Text(stringResource(R.string.label)) }, singleLine = true)
                OutlinedTextField(region, { region = it }, label = { Text(stringResource(R.string.unknown_location)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(latitude, { latitude = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.latitude)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(longitude, { longitude = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.longitude)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
                OutlinedTextField(notes, { notes = it.take(2_000) }, label = { Text(stringResource(R.string.notes)) }, minLines = 2, maxLines = 4)
                if (invalid) Text(stringResource(R.string.invalid_coordinates), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val lat = latitude.toDoubleOrNull()
                val lon = longitude.toDoubleOrNull()
                if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) invalid = true
                else onSave(item.copy(latitude = lat, longitude = lon, roadName = roadName.ifBlank { null }, regionLabel = region.ifBlank { null }, notes = notes))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
