package com.jasermohamed.bumpcompanion.ui.bumps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.model.*
import com.jasermohamed.bumpcompanion.domain.repository.BumpRepository
import com.jasermohamed.bumpcompanion.platform.navigation.NavigationAppLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BumpsUiState(
    val items: List<SpeedBump> = emptyList(),
    val filter: BumpStatus? = BumpStatus.CONFIRMED,
    val search: String = "",
    val selected: SpeedBump? = null,
    val message: String? = null,
)

@HiltViewModel
class BumpsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BumpRepository,
    private val navigationAppLauncher: NavigationAppLauncher,
) : ViewModel() {
    private val filter = MutableStateFlow<BumpStatus?>(BumpStatus.CONFIRMED)
    private val search = MutableStateFlow("")
    private val selected = MutableStateFlow<SpeedBump?>(null)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<BumpsUiState> = combine(
        repository.observeBumps(), filter, search, selected, message,
    ) { bumps, selectedFilter, query, selectedItem, text ->
        val filtered = bumps.filter { bump ->
            val statusMatches = selectedFilter == null || bump.status == selectedFilter
            val q = query.trim()
            val textMatches = q.isBlank() || listOfNotNull(
                bump.roadName,
                bump.regionLabel,
                bump.notes,
                bump.importedSource,
                "${bump.latitude},${bump.longitude}",
            ).any { it.contains(q, ignoreCase = true) }
            statusMatches && textMatches
        }
        BumpsUiState(filtered, selectedFilter, query, selectedItem, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BumpsUiState())

    fun setFilter(value: BumpStatus?) { filter.value = value }
    fun setSearch(value: String) { search.value = value }
    fun select(item: SpeedBump?) { selected.value = item }
    fun save(item: SpeedBump) = viewModelScope.launch { repository.updateBump(item); selected.value = null }
    fun toggleWarning(item: SpeedBump) = viewModelScope.launch { repository.setWarningEnabled(item.id, !item.warningEnabled) }
    fun archive(item: SpeedBump) = viewModelScope.launch { repository.setArchived(item.id, !item.archived) }
    fun markRemoved(item: SpeedBump) = viewModelScope.launch { repository.markRemoved(item.id) }
    fun delete(item: SpeedBump) = viewModelScope.launch { repository.deleteBump(item.id) }
    fun openExternal(item: SpeedBump) {
        navigationAppLauncher.openCoordinate(item.latitude, item.longitude, item.roadName)
            .onFailure { message.value = context.getString(R.string.map_no_compatible_app) }
    }
    fun consumeMessage() { message.value = null }
}
