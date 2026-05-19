package com.bartmuskala.mykaroohud.extension

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.bartmuskala.mykaroohud.extension.SparklineConfig
import com.bartmuskala.mykaroohud.extension.ZoneConfig

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mykaroohud")

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private inline fun <reified T> Context.streamConfig(
    key: Preferences.Key<String>,
    default: T,
): Flow<T> =
    dataStore.data
        .map { prefs ->
            prefs[key]?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() } ?: default
        }
        .distinctUntilChanged()

private suspend inline fun <reified T> Context.saveConfig(
    key: Preferences.Key<String>,
    config: T,
) {
    dataStore.edit { it[key] = json.encodeToString(config) }
}

@Serializable
data class MyKarooHudConfig(
    val cp: Int = 250,
    val wPrimeJoules: Int = 16000,
    val useSmoothedPower: Boolean = true
)

private val configKey = stringPreferencesKey("mykaroohud_config")

fun Context.streamMyKarooHudConfig(): Flow<MyKarooHudConfig> =
    streamConfig(configKey, MyKarooHudConfig())

suspend fun Context.saveMyKarooHudConfig(config: MyKarooHudConfig) =
    saveConfig(configKey, config)

fun Context.streamSparklineConfig(): Flow<SparklineConfig> = flowOf(SparklineConfig())
fun Context.streamZoneConfig(): Flow<ZoneConfig> = flowOf(ZoneConfig())
