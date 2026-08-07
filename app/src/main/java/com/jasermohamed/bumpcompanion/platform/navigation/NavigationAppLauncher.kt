package com.jasermohamed.bumpcompanion.platform.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

interface NavigationAppLauncher {
    suspend fun openChooser(): Result<Unit>
    fun openCoordinate(latitude: Double, longitude: Double, label: String? = null): Result<Unit>
}

@Singleton
class AndroidNavigationAppLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : NavigationAppLauncher {
    override suspend fun openChooser(): Result<Unit> = runCatching {
        val preferred = settingsRepository.settings.first().preferredNavigationPackage
        val mapsIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, CATEGORY_APP_MAPS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (preferred != null) {
            val targeted = Intent(mapsIntent).setPackage(preferred)
            if (targeted.resolveActivity(context.packageManager) != null) {
                context.startActivity(targeted)
                return@runCatching
            }
        }
        require(mapsIntent.resolveActivity(context.packageManager) != null) {
            context.getString(R.string.navigation_no_compatible_app)
        }
        context.startActivity(Intent.createChooser(mapsIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun openCoordinate(latitude: Double, longitude: Double, label: String?): Result<Unit> = runCatching {
        val encodedLabel = Uri.encode(label ?: context.getString(R.string.speed_bump_label))
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        require(intent.resolveActivity(context.packageManager) != null) {
            context.getString(R.string.map_no_compatible_app)
        }
        context.startActivity(intent)
    }

    private companion object {
        const val CATEGORY_APP_MAPS = "android.intent.category.APP_MAPS"
    }
}
