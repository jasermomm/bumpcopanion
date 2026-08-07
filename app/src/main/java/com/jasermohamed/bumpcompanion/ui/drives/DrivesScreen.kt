@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jasermohamed.bumpcompanion.ui.drives

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.model.DriveSession
import com.jasermohamed.bumpcompanion.ui.components.EmptyState
import com.jasermohamed.bumpcompanion.ui.components.StatusLine
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun DrivesRoute(viewModel: DrivesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drives = state.drives
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.drive_history), fontWeight = FontWeight.SemiBold) }) }) { padding ->
        if (drives.isEmpty()) {
            EmptyState(stringResource(R.string.no_drive_history), stringResource(R.string.privacy_local), Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(drives, key = { it.id }) { DriveCard(it, state.settings.metricUnits) }
            }
        }
    }
}

@Composable
private fun DriveCard(drive: DriveSession, metricUnits: Boolean) {
    var expanded by rememberSaveable(drive.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(drive.startedAt)), fontWeight = FontWeight.SemiBold)
                    Text("${formatDuration(drive.durationMillis)} • ${formatDistance(drive.distanceMetres, metricUnits)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill("${drive.candidateCount} ${stringResource(R.string.bumps_found)}", Modifier.weight(1f))
                InfoPill("${drive.warningCount} ${stringResource(R.string.warnings)}", Modifier.weight(1f))
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider()
                    StatusLine(stringResource(R.string.max_speed), formatSpeed(drive.maximumSpeedMetresPerSecond, metricUnits))
                    StatusLine(stringResource(R.string.average_speed), formatSpeed(drive.averageSpeedMetresPerSecond, metricUnits))
                    StatusLine(stringResource(R.string.known_bumps_passed), drive.knownBumpPasses.toString())
                    if (drive.incomplete) {
                        Text(stringResource(R.string.drive_ended_unexpectedly), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalMinutes = milliseconds / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun formatSpeed(metresPerSecond: Float, metric: Boolean): String = if (metric) {
    stringResource(R.string.speed_kmh_format, (metresPerSecond * 3.6f).roundToInt())
} else {
    stringResource(R.string.speed_mph_format, (metresPerSecond * 2.2369363f).roundToInt())
}

@Composable
private fun formatDistance(metres: Double, metric: Boolean): String = if (metric) {
    if (metres < 1_000) stringResource(R.string.metres_format, metres.roundToInt())
    else stringResource(R.string.kilometres_format, metres / 1_000.0)
} else {
    val feet = metres * 3.2808399
    if (feet < 5_280) stringResource(R.string.feet_format, feet.roundToInt())
    else stringResource(R.string.miles_format, feet / 5_280.0)
}
