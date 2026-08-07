package com.jasermohamed.bumpcompanion.di

import android.content.Context
import androidx.room.Room
import com.jasermohamed.bumpcompanion.data.calibration.*
import com.jasermohamed.bumpcompanion.data.exchange.*
import com.jasermohamed.bumpcompanion.data.local.*
import com.jasermohamed.bumpcompanion.data.preferences.SettingsRepositoryImpl
import com.jasermohamed.bumpcompanion.data.repository.*
import com.jasermohamed.bumpcompanion.domain.approach.ApproachPredictor
import com.jasermohamed.bumpcompanion.domain.detection.*
import com.jasermohamed.bumpcompanion.domain.repository.*
import com.jasermohamed.bumpcompanion.platform.location.*
import com.jasermohamed.bumpcompanion.platform.navigation.*
import com.jasermohamed.bumpcompanion.platform.sensors.*
import com.jasermohamed.bumpcompanion.platform.warnings.*
import com.jasermohamed.bumpcompanion.service.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "bump_companion.db")
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideSpeedBumpDao(database: AppDatabase): SpeedBumpDao = database.speedBumpDao()
    @Provides fun provideCandidateDao(database: AppDatabase): CandidateDao = database.candidateDao()
    @Provides fun provideDriveSessionDao(database: AppDatabase): DriveSessionDao = database.driveSessionDao()
    @Provides fun provideCalibrationDao(database: AppDatabase): CalibrationDao = database.calibrationDao()
    @Provides fun provideLocationTrackPointDao(database: AppDatabase): LocationTrackPointDao = database.locationTrackPointDao()
    @Provides fun provideDiagnosticFileDao(database: AppDatabase): DiagnosticFileDao = database.diagnosticFileDao()

    @Provides
    @Singleton
    fun provideRoadEventDetector(): RoadEventDetector = HeuristicRoadEventDetector()

    @Provides
    @Singleton
    fun provideEventLocationEstimator(): EventLocationEstimator = EventLocationEstimator()

    @Provides
    @Singleton
    fun provideApproachPredictor(): ApproachPredictor = ApproachPredictor()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {
    @Binds abstract fun bindBumpRepository(impl: BumpRepositoryImpl): BumpRepository
    @Binds abstract fun bindDriveRepository(impl: DriveRepositoryImpl): DriveRepository
    @Binds abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun bindMotionSensors(impl: AndroidMotionSensorProvider): MotionSensorProvider
    @Binds abstract fun bindLocationProvider(impl: AndroidLocationProvider): LocationProvider
    @Binds abstract fun bindWarningOutput(impl: AndroidWarningOutput): WarningOutput
    @Binds abstract fun bindNavigationLauncher(impl: AndroidNavigationAppLauncher): NavigationAppLauncher
    @Binds abstract fun bindExchangeManager(impl: BumpExchangeManagerImpl): BumpExchangeManager
    @Binds abstract fun bindServiceController(impl: AndroidDriveServiceController): DriveServiceController
    @Binds abstract fun bindCalibrationManager(impl: AndroidCalibrationManager): CalibrationManager
}
