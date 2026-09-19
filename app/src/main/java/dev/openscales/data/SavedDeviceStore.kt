package dev.openscales.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.openscales.protocol.ScaleModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SavedDevice(val address: String, val name: String, val model: ScaleModel)

interface SavedDeviceStore {
    val device: Flow<SavedDevice?>
    suspend fun save(device: SavedDevice)
    suspend fun clear()
}

/** Общий файл настроек приложения: запомненные весы и настройки звука. */
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore("scale")

class DataStoreSavedDeviceStore(context: Context) : SavedDeviceStore {
    private val store = context.applicationContext.dataStore

    override val device: Flow<SavedDevice?> = store.data.map { p ->
        val address = p[ADDRESS] ?: return@map null
        SavedDevice(address, p[NAME].orEmpty(), ScaleModel.fromCode(p[MODEL] ?: -1))
    }

    override suspend fun save(device: SavedDevice) {
        store.edit {
            it[ADDRESS] = device.address
            it[NAME] = device.name
            it[MODEL] = device.model.code
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val ADDRESS = stringPreferencesKey("address")
        val NAME = stringPreferencesKey("name")
        val MODEL = intPreferencesKey("model")
    }
}
