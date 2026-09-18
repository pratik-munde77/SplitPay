package com.example.splitpay.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferences by preferencesDataStore(name = "splitpay_settings")

@Singleton
class PreferencesRepository @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val dark = booleanPreferencesKey("dark_theme")
    val darkTheme = context.preferences.data.catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }.map { it[dark] }
    suspend fun setDarkTheme(enabled: Boolean) { context.preferences.edit { it[dark] = enabled } }
}
