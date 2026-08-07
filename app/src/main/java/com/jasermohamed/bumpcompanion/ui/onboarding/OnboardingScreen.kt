package com.jasermohamed.bumpcompanion.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jasermohamed.bumpcompanion.R

private data class OnboardingPage(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val icon: ImageVector,
)

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel = hiltViewModel()) {
    OnboardingScreen(onComplete = viewModel::complete)
}

@Composable
internal fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage(R.string.onboarding_title_1, R.string.onboarding_body_1, Icons.Outlined.Navigation),
            OnboardingPage(R.string.onboarding_title_2, R.string.onboarding_body_2, Icons.Outlined.PhoneAndroid),
            OnboardingPage(R.string.onboarding_title_3, R.string.onboarding_body_3, Icons.Outlined.DirectionsCar),
            OnboardingPage(R.string.onboarding_title_4, R.string.onboarding_body_4, Icons.Outlined.Lock),
        )
    }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val current = pages[page]
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onComplete) { Text(stringResource(R.string.skip)) }
            }
            Spacer(Modifier.weight(0.8f))
            Icon(
                current.icon,
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(30.dp))
            Text(
                stringResource(current.title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(current.body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { index ->
                    Surface(
                        modifier = Modifier.size(if (index == page) 28.dp else 8.dp, 8.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ) {}
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page > 0) {
                    OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.back))
                    }
                }
                Button(
                    onClick = { if (page == pages.lastIndex) onComplete() else page++ },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(if (page == pages.lastIndex) R.string.get_started else R.string.next))
                }
            }
        }
    }
}
