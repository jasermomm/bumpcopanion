@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jasermohamed.bumpcompanion.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.model.*
import com.jasermohamed.bumpcompanion.ui.components.*
import kotlin.math.roundToInt

private enum class PermissionDialog { LOCATION, NOTIFICATIONS, MOUNT }

@Composable
fun HomeRoute(
    onReviewCandidates: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<PermissionDialog?>(null) }

    fun hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        dialog = PermissionDialog.MOUNT
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val precise = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasFineLocation()
        if (precise) {
            dialog = if (hasNotificationPermission()) PermissionDialog.MOUNT else PermissionDialog.NOTIFICATIONS
        } else {
            dialog = null
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    DisposableEffect(Unit) {
        viewModel.refreshReadiness()
        onDispose { }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
                },
            )
        },
    ) { padding ->
        if (state.runtime.isRunning) {
            ActiveDriveScreen(
                state = state,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onStop = viewModel::stop,
                onMark = viewModel::markBump,
                onMute = viewModel::toggleMute,
                onOpenNavigation = viewModel::openNavigation,
                modifier = Modifier.padding(padding),
            )
        } else {
            IdleHomeScreen(
                state = state,
                onStartDrive = {
                    viewModel.refreshReadiness()
                    dialog = when {
                        !hasFineLocation() -> PermissionDialog.LOCATION
                        !hasNotificationPermission() -> PermissionDialog.NOTIFICATIONS
                        else -> PermissionDialog.MOUNT
                    }
                },
                onOpenNavigation = viewModel::openNavigation,
                onReviewCandidates = onReviewCandidates,
                modifier = Modifier.padding(padding),
            )
        }
    }

    when (dialog) {
        PermissionDialog.LOCATION -> ExplanationDialog(
            title = stringResource(R.string.permission_location_title),
            body = stringResource(R.string.permission_location_body),
            confirm = stringResource(R.string.continue_label),
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
        )
        PermissionDialog.NOTIFICATIONS -> ExplanationDialog(
            title = stringResource(R.string.permission_notifications_title),
            body = stringResource(R.string.permission_notifications_body),
            confirm = stringResource(R.string.continue_label),
            dismiss = stringResource(R.string.not_now),
            onDismiss = { dialog = PermissionDialog.MOUNT },
            onConfirm = {
                dialog = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else dialog = PermissionDialog.MOUNT
            },
        )
        PermissionDialog.MOUNT -> AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null) },
            title = { Text(stringResource(R.string.mount_confirmation_title)) },
            text = { Text(stringResource(R.string.mount_confirmation_body)) },
            confirmButton = {
                Button(onClick = {
                    dialog = null
                    viewModel.startDrive()
                }) { Text(stringResource(R.string.phone_is_mounted)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun ExplanationDialog(
    title: String,
    body: String,
    confirm: String,
    dismiss: String = stringResource(R.string.cancel),
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

@Composable
private fun IdleHomeScreen(
    state: HomeUiState,
    onStartDrive: () -> Unit,
    onOpenNavigation: () -> Unit,
    onReviewCandidates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val supported = state.sensorCapabilities.quality != DetectionQuality.UNSUPPORTED
        val ready = supported && state.locationEnabled
        StatusLine(
            label = stringResource(R.string.bump_detection),
            value = stringResource(
                when {
                    !supported -> R.string.detection_unavailable
                    !state.locationEnabled -> R.string.location_off
                    else -> R.string.ready
                }
            ),
            positive = ready,
        )
        StatusLine(stringResource(R.string.phone_placement), stringResource(R.string.mount_required))
        Button(
            onClick = onStartDrive,
            enabled = supported,
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Outlined.DirectionsCar, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.start_drive), style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onOpenNavigation, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Navigation, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.open_navigation_app))
            }
            OutlinedButton(onClick = onReviewCandidates, modifier = Modifier.weight(1f), enabled = state.pendingCandidates > 0) {
                Icon(Icons.Outlined.FactCheck, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.review_candidates))
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricBlock(stringResource(R.string.confirmed_bumps), state.confirmedBumps.toString(), Modifier.weight(1f))
            MetricBlock(stringResource(R.string.pending_candidates), state.pendingCandidates.toString(), Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle(stringResource(R.string.last_drive))
            val latest = state.latestDrive
            if (latest == null) Text(stringResource(R.string.no_drives_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text("${formatDuration(latest.durationMillis)} • ${formatDistance(latest.distanceMetres, state.settings.metricUnits)} • ${latest.candidateCount} ${stringResource(R.string.bumps_found)}")
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.privacy_local), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(stringResource(R.string.safety_statement), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ActiveDriveScreen(
    state: HomeUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onMark: () -> Unit,
    onMute: () -> Unit,
    onOpenNavigation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val runtime = state.runtime
    val elapsed = (System.currentTimeMillis() - (runtime.startedAt ?: System.currentTimeMillis())).coerceAtLeast(0)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (runtime.serviceState == DriveServiceState.PAUSED) R.string.drive_paused else R.string.active_drive),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        MetricBlock(
            stringResource(R.string.current_speed),
            runtime.currentSpeedMetresPerSecond?.let { formatSpeed(it, state.settings.metricUnits) } ?: stringResource(R.string.not_available),
            prominent = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricBlock(stringResource(R.string.drive_duration), formatDuration(elapsed), Modifier.weight(1f))
            MetricBlock(stringResource(R.string.distance_travelled), formatDistance(runtime.distanceMetres, state.settings.metricUnits), Modifier.weight(1f))
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (runtime.phonePlacementState == PhonePlacementState.UNSTABLE) {
                StatusLine(stringResource(R.string.phone_placement), stringResource(R.string.secure_phone), false)
            }
            StatusLine(stringResource(R.string.known_bumps_passed), runtime.knownBumpsPassed.toString())
            StatusLine(stringResource(R.string.next_bump), runtime.nextBumpDistanceMetres?.let { formatDistance(it.toDouble(), state.settings.metricUnits, compact = true) } ?: stringResource(R.string.none_nearby))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = if (runtime.serviceState == DriveServiceState.PAUSED) onResume else onPause,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Icon(if (runtime.serviceState == DriveServiceState.PAUSED) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (runtime.serviceState == DriveServiceState.PAUSED) R.string.resume_drive else R.string.pause_drive))
            }
            FilledTonalButton(onClick = onStop, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.stop_drive))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onMark, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) {
                Icon(Icons.Outlined.AddLocationAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mark_bump))
            }
            OutlinedButton(onClick = onMute, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) {
                Icon(if (runtime.warningsMuted) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (runtime.warningsMuted) R.string.unmute else R.string.mute))
            }
        }
        OutlinedButton(onClick = onOpenNavigation, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Icon(Icons.Outlined.Navigation, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.open_navigation_app))
        }
        Text(stringResource(R.string.safety_statement), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1_000L
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

@Composable
private fun formatSpeed(metresPerSecond: Float, metric: Boolean): String = if (metric) {
    stringResource(R.string.speed_kmh_format, (metresPerSecond * 3.6f).roundToInt())
} else {
    stringResource(R.string.speed_mph_format, (metresPerSecond * 2.2369363f).roundToInt())
}

@Composable
private fun formatDistance(metres: Double, metric: Boolean, compact: Boolean = false): String {
    return if (metric) {
        if (metres < 1_000 || compact) stringResource(R.string.metres_format, metres.roundToInt())
        else stringResource(R.string.kilometres_format, metres / 1_000.0)
    } else {
        val feet = metres * 3.2808399
        if (feet < 5_280 || compact) stringResource(R.string.feet_format, feet.roundToInt())
        else stringResource(R.string.miles_format, feet / 5_280.0)
    }
}
