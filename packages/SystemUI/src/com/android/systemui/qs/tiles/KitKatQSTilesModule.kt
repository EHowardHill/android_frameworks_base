package com.android.systemui.qs.tiles

import com.android.systemui.qs.tileimpl.QSTileImpl
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

/**
 * KitKat UI Project (P20.8/P20.10): binds the donor 4.4 QS tiles into the
 * tile map - brightness (opens the donor brightness dialog, P20.8),
 * settings (opens Settings from the grid) and user (avatar tile,
 * quick-contact on tap) - so the donor tile set + order in
 * quick_settings_tiles_default resolves.
 */
@Module
object KitKatQSTilesModule {

    @Provides
    @IntoMap
    @StringKey(KitKatBrightnessTile.TILE_SPEC)
    fun provideBrightnessTile(tile: KitKatBrightnessTile): QSTileImpl<*> = tile

    @Provides
    @IntoMap
    @StringKey(KitKatSettingsTile.TILE_SPEC)
    fun provideSettingsTile(tile: KitKatSettingsTile): QSTileImpl<*> = tile

    @Provides
    @IntoMap
    @StringKey(KitKatUserTile.TILE_SPEC)
    fun provideUserTile(tile: KitKatUserTile): QSTileImpl<*> = tile
}