package com.jasermohamed.bumpcompanion.service

import com.jasermohamed.bumpcompanion.domain.model.DriveRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRuntimeStore @Inject constructor() {
    private val mutableState = MutableStateFlow(DriveRuntimeState())
    val state: StateFlow<DriveRuntimeState> = mutableState.asStateFlow()

    fun update(transform: (DriveRuntimeState) -> DriveRuntimeState) {
        mutableState.value = transform(mutableState.value)
    }

    fun reset() {
        mutableState.value = DriveRuntimeState()
    }
}
