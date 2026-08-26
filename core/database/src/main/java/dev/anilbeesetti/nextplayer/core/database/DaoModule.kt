package dev.anilbeesetti.nextplayer.core.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun provideMediumStateDao(db: MediaDatabase) = db.mediumStateDao()

    @Provides
    fun provideHiddenVideoDao(db: MediaDatabase) = db.hiddenVideoDao()

    @Provides
    fun provideNetworkConnectionDao(db: MediaDatabase) = db.networkConnectionDao()

    @Provides
    fun providePlaylistDao(db: MediaDatabase) = db.playlistDao()
}
