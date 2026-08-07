package com.jasermohamed.bumpcompanion.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasermohamed.bumpcompanion.domain.model.CandidateDecision
import com.jasermohamed.bumpcompanion.domain.model.AppSettings
import com.jasermohamed.bumpcompanion.domain.model.CandidateEvent
import com.jasermohamed.bumpcompanion.domain.repository.BumpRepository
import com.jasermohamed.bumpcompanion.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val candidates: List<CandidateEvent> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: BumpRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<ReviewUiState> = combine(
        repository.observePendingCandidates(),
        settingsRepository.settings,
    ) { candidates, settings -> ReviewUiState(candidates, settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    fun decide(id: String, decision: CandidateDecision) = viewModelScope.launch {
        repository.decideCandidate(id, decision)
    }

    fun confirmProbable() = viewModelScope.launch {
        state.value.candidates.filter { it.confidence >= 0.70f && it.latitude != null && it.longitude != null }
            .forEach { repository.confirmCandidate(it.id) }
    }
}
