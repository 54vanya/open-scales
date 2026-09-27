package dev.openscales.data

import dev.openscales.recipe.Recipe
import dev.openscales.recipe.RecipeDraft
import dev.openscales.recipe.RecipeFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Свои рецепты: по файлу `<uuid>.json` на рецепт в [dir] ([RecipeFormat]). Список грузится в фоне при создании;
 * испорченный или непонятный файл пропускается и не мешает остальным. Запись — через временный файл
 * и переименование, чтобы сбой не оставил половину документа. [recipes] меняется в [scope] (главный поток).
 */
class RecipeStore(
    private val dir: File,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val log: (String) -> Unit = {},
) {
    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()

    private val mutex = Mutex()

    init {
        scope.launch {
            mutex.withLock {
                val loaded = withContext(io) { load() }
                // Пока грузились, сохранить ничего не могли: запись ждёт тот же замок.
                _recipes.value = loaded
            }
        }
    }

    /**
     * Рецепт по id. Если список ещё не загружен (экран варки восстановлен раньше загрузки), документ читается
     * с диска сразу — он маленький.
     */
    fun get(id: String): Recipe? =
        _recipes.value.firstOrNull { it.id == id } ?: fileOf(id)?.takeIf { it.isFile }?.let { read(it) }?.takeIf { it.id == id }

    suspend fun save(recipe: Recipe) = mutex.withLock {
        val file = checkNotNull(fileOf(recipe.id)) { "not a user recipe id: ${recipe.id}" }
        val text = RecipeFormat.encode(recipe)
        withContext(io) {
            dir.mkdirs()
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) throw IOException("rename failed: $tmp")
        }
        _recipes.update { list -> list.filterNot { it.id == recipe.id } + recipe }
    }

    suspend fun delete(id: String) = mutex.withLock {
        fileOf(id)?.let { file -> withContext(io) { file.delete() } }
        _recipes.update { list -> list.filterNot { it.id == id } }
    }

    private fun load(): List<Recipe> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) }.orEmpty().sortedBy { it.name }
        // Имя файла — id рецепта: иначе «Удалить» стёрло бы не тот файл и рецепт вернулся бы после перезапуска.
        return files.mapNotNull { file ->
            read(file)?.takeIf { fileOf(it.id)?.name == file.name }.also { if (it == null) log("recipe ${file.name}: skipped") }
        }
    }

    private fun read(file: File): Recipe? {
        val text = try {
            file.readText()
        } catch (e: IOException) {
            log("recipe ${file.name}: ${e.message}")
            return null
        }
        return RecipeFormat.decode(text)
    }

    /** Файл рецепта; `null` — id не свой или с недопустимыми символами (не даём выйти из каталога). */
    private fun fileOf(id: String): File? {
        val uuid = id.removePrefix(RecipeDraft.USER_ID_PREFIX).takeIf { id.startsWith(RecipeDraft.USER_ID_PREFIX) }
        return uuid?.takeIf { SAFE_NAME.matches(it) }?.let { File(dir, it + EXT) }
    }

    private companion object {
        const val EXT = ".json"
        val SAFE_NAME = Regex("[A-Za-z0-9-]{1,64}")
    }
}
