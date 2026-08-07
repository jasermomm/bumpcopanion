@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jasermohamed.bumpcompanion.ui.calibration

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jasermohamed.bumpcompanion.R
import java.text.DateFormat
import java.util.Date

@Composable
fun CalibrationRoute(
    onBack: () -> Unit,
    viewModel: CalibrationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calibration), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Icon(Icons.Outlined.Sensors, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.calibration_instructions), style = MaterialTheme.typography.bodyLarge)
            if (state.running) {
                CircularProgressIndicator()
                Text(stringResource(R.string.calibration_collecting))
            } else {
                Button(onClick = viewModel::start, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (state.latest == null) R.string.start_calibration else R.string.recalibrate))
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.latest?.let { profile ->
                HorizontalDivider()
                Text(stringResource(R.string.calibration_complete), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.calibration_date, DateFormat.getDateTimeInstance().format(Date(profile.createdAt))))
                TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.reset_calibration)) }
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.calibration_safety), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
