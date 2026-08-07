@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jasermohamed.bumpcompanion.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.model.*
import com.jasermohamed.bumpcompanion.ui.components.EmptyState
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun ReviewRoute(
    onBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val candidates = state.candidates
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.candidate_review), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                actions = {
                    TextButton(onClick = viewModel::confirmProbable, enabled = candidates.any { it.confidence >= 0.70f && it.latitude != null }) {
                        Text(stringResource(R.string.batch_confirm))
                    }
                },
            )
        },
    ) { padding ->
        if (candidates.isEmpty()) {
            EmptyState(stringResource(R.string.no_candidates), stringResource(R.string.onboarding_body_3), Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(candidates, key = { it.id }) { candidate ->
                    CandidateCard(candidate, state.settings.metricUnits, onDecision = { viewModel.decide(candidate.id, it) })
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: CandidateEvent, metricUnits: Boolean, onDecision: (CandidateDecision) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(eventLabel(candidate.eventType), fontWeight = FontWeight.SemiBold)
                        Text(DateFormat.getDateTimeInstance().format(Date(candidate.detectedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(candidate.latitude?.let { lat -> candidate.longitude?.let { lon -> stringResource(R.string.coordinates_format, lat, lon) } } ?: stringResource(R.string.unknown_location))
            Text("${stringResource(R.string.speed_at_event)}: ${formatSpeed(candidate.speedMetresPerSecond, metricUnits)}", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onDecision(CandidateDecision.CONFIRMED) },
                    modifier = Modifier.weight(1f),
                    enabled = candidate.latitude != null && candidate.longitude != null,
                ) { Text(stringResource(R.string.confirm_speed_bump)) }
                OutlinedButton(onClick = { onDecision(CandidateDecision.REJECTED) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.reject))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { onDecision(CandidateDecision.NOT_SURE) }) { Text(stringResource(R.string.not_sure)) }
                TextButton(onClick = { onDecision(CandidateDecision.POTHOLE) }) { Text(stringResource(R.string.mark_pothole)) }
                TextButton(onClick = { onDecision(CandidateDecision.ROUGH_ROAD) }) { Text(stringResource(R.string.mark_rough_road)) }
            }
        }
    }
}

@Composable
private fun eventLabel(type: RoadEventType): String = stringResource(
    when (type) {
        RoadEventType.LIKELY_SPEED_BUMP -> R.string.event_likely_bump
        RoadEventType.POSSIBLE_SPEED_BUMP -> R.string.event_possible_bump
        RoadEventType.POTHOLE_LIKE -> R.string.event_pothole_like
        RoadEventType.ROUGH_ROAD -> R.string.event_rough_road
        RoadEventType.PHONE_MOVEMENT -> R.string.event_phone_movement
        RoadEventType.HARD_BRAKING -> R.string.event_hard_braking
        RoadEventType.UNKNOWN -> R.string.event_unknown
        RoadEventType.DISCARDED -> R.string.event_discarded
    }
)

@Composable
private fun formatSpeed(metresPerSecond: Float, metric: Boolean): String = if (metric) {
    stringResource(R.string.speed_kmh_format, (metresPerSecond * 3.6f).roundToInt())
} else {
    stringResource(R.string.speed_mph_format, (metresPerSecond * 2.2369363f).roundToInt())
}
