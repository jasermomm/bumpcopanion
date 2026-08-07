@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jasermohamed.bumpcompanion.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.model.*
import com.jasermohamed.bumpcompanion.ui.components.SectionTitle

@Composable
fun SettingsRoute(
    onCalibration: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var deleteAllDialog by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::previewImport)
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.SemiBold) }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsSection(stringResource(R.string.detection_settings), Icons.Outlined.Tune) {
                Text(stringResource(R.string.sensitivity), style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Sensitivity.entries.forEachIndexed { index, sensitivity ->
                        SegmentedButton(
                            selected = state.settings.sensitivity == sensitivity,
                            onClick = { viewModel.setSensitivity(sensitivity) },
                            shape = SegmentedButtonDefaults.itemShape(index, Sensitivity.entries.size),
                            label = { Text(sensitivityLabel(sensitivity)) },
                        )
                    }
                }
                ActionRow(stringResource(R.string.calibration), Icons.Outlined.Sensors, onCalibration)
            }
            SettingsSection(stringResource(R.string.warning_settings), Icons.Outlined.NotificationsActive) {
                SwitchRow(stringResource(R.string.enable_warnings), state.settings.warningsEnabled, viewModel::setWarnings)
                SwitchRow(stringResource(R.string.voice_warnings), state.settings.voiceEnabled, viewModel::setVoice, state.settings.warningsEnabled)
                SwitchRow(stringResource(R.string.tone_warnings), state.settings.toneEnabled, viewModel::setTone, state.settings.warningsEnabled)
                SwitchRow(stringResource(R.string.vibration_warnings), state.settings.vibrationEnabled, viewModel::setVibration, state.settings.warningsEnabled)
                SwitchRow(stringResource(R.string.adaptive_distance), state.settings.adaptiveWarningDistance, viewModel::setAdaptiveDistance, state.settings.warningsEnabled)
            }
            SettingsSection(stringResource(R.string.drive_settings), Icons.Outlined.DirectionsCar) {
                SwitchRow(
                    stringResource(R.string.route_history),
                    state.settings.routeHistoryEnabled,
                    viewModel::setRouteHistory,
                    summary = stringResource(R.string.route_history_summary),
                )
            }
            SettingsSection(stringResource(R.string.units_settings), Icons.Outlined.Straighten) {
                SwitchRow(stringResource(R.string.metric_units), state.settings.metricUnits, viewModel::setMetric)
            }
            SettingsSection(stringResource(R.string.appearance_settings), Icons.Outlined.Palette) {
                Text(stringResource(R.string.dark_mode), style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.settings.themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            label = { Text(themeLabel(mode)) },
                        )
                    }
                }
                SwitchRow(stringResource(R.string.dynamic_colour), state.settings.dynamicColour, viewModel::setDynamicColour)
            }
            SettingsSection(stringResource(R.string.privacy_storage_settings), Icons.Outlined.Lock) {
                ActionRow(
                    label = stringResource(R.string.export_bumps),
                    icon = Icons.Outlined.UploadFile,
                    onClick = {
                        exportLauncher.launch(
                            "bump-companion-${System.currentTimeMillis()}.bumpcompanion"
                        )
                    },
                )

                ActionRow(
                    label = stringResource(R.string.import_bumps),
                    icon = Icons.Outlined.Download,
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/json",
                                "application/octet-stream",
                                "text/plain",
                            )
                        )
                    },
                )
                ActionRow(stringResource(R.string.delete_drive_history), Icons.Outlined.DeleteSweep, viewModel::deleteHistory)
                ActionRow(stringResource(R.string.delete_all_data), Icons.Outlined.DeleteForever, { deleteAllDialog = true }, destructive = true)
            }
            Text(stringResource(R.string.privacy_local), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.safety_statement), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
        }
    }
    state.pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text(stringResource(R.string.import_preview_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.import_preview_file, pending.preview.fileName))
                    Text(stringResource(R.string.import_preview_date, DateFormat.getDateTimeInstance().format(Date(pending.preview.exportedAt))))
                    Text(stringResource(R.string.import_preview_count, pending.preview.bumpCount))
                    Text(stringResource(R.string.import_preview_invalid, pending.preview.invalidCount))
                    pending.preview.sourceLabel?.let { Text(stringResource(R.string.import_preview_source, it)) }
                    Text(stringResource(R.string.import_preview_bounds, pending.preview.coordinateBounds))
                }
            },
            confirmButton = { Button(onClick = viewModel::confirmImport) { Text(stringResource(R.string.confirm_import)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleteAllDialog) {
        AlertDialog(
            onDismissRequest = { deleteAllDialog = false },
            title = { Text(stringResource(R.string.confirm_delete_all_title)) },
            text = { Text(stringResource(R.string.confirm_delete_all_body)) },
            confirmButton = {
                Button(
                    onClick = { deleteAllDialog = false; viewModel.deleteAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete_everything)) }
            },
            dismissButton = { TextButton(onClick = { deleteAllDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (state.busy) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            SectionTitle(title)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean = true, summary: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onChecked(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable
private fun ActionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, destructive: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun sensitivityLabel(value: Sensitivity): String = stringResource(
    when (value) {
        Sensitivity.CONSERVATIVE -> R.string.conservative
        Sensitivity.BALANCED -> R.string.balanced
        Sensitivity.SENSITIVE -> R.string.sensitive
    }
)

@Composable
private fun themeLabel(value: ThemeMode): String = stringResource(
    when (value) {
        ThemeMode.SYSTEM -> R.string.system_default
        ThemeMode.LIGHT -> R.string.light
        ThemeMode.DARK -> R.string.dark
    }
)
