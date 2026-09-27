package dev.openscales.recipe

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Свой рецепт как самостоятельный JSON-документ: так он хранится на устройстве и так же его можно будет отдать
 * другому. Рубежи — целые граммы при дозе по умолчанию, как их ввёл человек. В документе id без префикса `user:`.
 */
object RecipeFormat {

    const val VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Только свой рецепт: тексты — [Text.Plain]. */
    fun encode(recipe: Recipe): String {
        require(recipe.id.startsWith(RecipeDraft.USER_ID_PREFIX)) { "only user recipes are stored" }
        val dto = RecipeDto(
            format = VERSION,
            id = recipe.id.removePrefix(RecipeDraft.USER_ID_PREFIX),
            title = recipe.title.plain(),
            category = recipe.category.name.lowercase(),
            doseG = recipe.defaultDoseG,
            difficulty = recipe.difficulty.name.lowercase(),
            description = recipe.description?.plain(),
            items = recipe.items.map { item ->
                when (item) {
                    is RecipeItem.Step -> ItemDto.Step(item.title.plain(), item.durationS, item.targetG, item.note?.plain(), item.showTime)
                    is RecipeItem.Hint -> ItemDto.Hint(item.text.plain())
                }
            },
        )
        return json.encodeToString(RecipeDto.serializer(), dto)
    }

    /** `null` — документ не разобрать, он из более новой версии формата или рецепт в нём неверный. */
    fun decode(text: String): Recipe? = try {
        val dto = json.decodeFromString(RecipeDto.serializer(), text)
        if (dto.format > VERSION || dto.id.isBlank()) {
            null
        } else {
            Recipe(
                id = RecipeDraft.USER_ID_PREFIX + dto.id,
                title = Text.Plain(dto.title),
                defaultDoseG = dto.doseG,
                description = dto.description?.let { Text.Plain(it) },
                items = dto.items.map { item ->
                    when (item) {
                        is ItemDto.Step -> RecipeItem.Step(
                            Text.Plain(item.title), item.durationS, item.note?.let { Text.Plain(it) }, item.targetG,
                            item.showTime,
                        )
                        is ItemDto.Hint -> RecipeItem.Hint(Text.Plain(item.text))
                    }
                },
                // До групп по оборудованию все пуроверы были `pour_over` — теперь это V60.
                category = RecipeCategory.entries.firstOrNull { it.name.lowercase() == dto.category } ?: RecipeCategory.V60,
                // Документы до появления сложности — «средне».
                difficulty = Difficulty.entries.firstOrNull { it.name.lowercase() == dto.difficulty } ?: Difficulty.MEDIUM,
            )
        }
    } catch (e: IllegalArgumentException) {
        // SerializationException — тоже IllegalArgumentException; сюда же неверный рецепт из init.
        null
    }

    private fun Text.plain(): String = (this as? Text.Plain)?.value ?: error("user recipe texts are plain")

    @Serializable
    private data class RecipeDto(
        val format: Int,
        val id: String,
        val title: String,
        val category: String,
        val doseG: Int,
        val difficulty: String? = null,
        val description: String? = null,
        val items: List<ItemDto>,
    )

    /** Вид элемента — поле `type`: `step` или `hint`. */
    @Serializable
    private sealed interface ItemDto {
        @Serializable
        @SerialName("step")
        data class Step(
            val title: String,
            val durationS: Int,
            val targetG: Int? = null,
            val note: String? = null,
            // Значение по умолчанию не пишется: поле появляется в документе, только когда признак включён.
            val showTime: Boolean = false,
        ) : ItemDto

        @Serializable
        @SerialName("hint")
        data class Hint(val text: String) : ItemDto
    }
}
