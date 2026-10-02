package dev.openscales.recipe

import java.util.UUID

/**
 * Рецепт в редакторе. Поля — строками, как их ввёл человек: промежуточное состояние (пустое поле, «0:90») допустимо,
 * числа разбирает [validate]. [id] — id будущего рецепта (`user:<uuid>`), у копии и нового рецепта — свежий.
 */
data class RecipeDraft(
    val id: String,
    val title: String = "",
    val doseG: String = DEFAULT_DOSE_G.toString(),
    val category: RecipeCategory = RecipeCategory.V60,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val description: String = "",
    val items: List<DraftItem> = emptyList(),
) {

    /** Ключ для следующего нового элемента: не совпадает ни с одним из текущих. */
    val nextKey: Long get() = (items.maxOfOrNull { it.key } ?: -1) + 1

    fun indexOf(key: Long): Int = items.indexOfFirst { it.key == key }

    /** Вставить [item] на позицию [index] (0 — перед первым элементом, `items.size` — после последнего). */
    fun insert(index: Int, item: DraftItem): RecipeDraft =
        copy(items = items.toMutableList().apply { add(index.coerceIn(0, size), item) })

    /** Поменять элемент местами с соседом: [delta] −1 — выше, +1 — ниже. У крайних ничего не меняется. */
    fun move(key: Long, delta: Int): RecipeDraft {
        val from = indexOf(key)
        val to = from + delta
        if (from < 0 || to !in items.indices) return this
        return copy(items = items.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun remove(key: Long): RecipeDraft = copy(items = items.filterNot { it.key == key })

    fun update(key: Long, change: (DraftItem) -> DraftItem): RecipeDraft =
        copy(items = items.map { if (it.key == key) change(it) else it })

    /**
     * Время «начало–конец» каждого элемента, как на экране варки: шаг занимает свою длительность, подпись — ноль.
     * Длительность, которую не удалось разобрать, считается нулём.
     */
    fun times(): List<IntRange> {
        var t = 0
        return items.map { item ->
            val start = t
            if (item is DraftItem.Step) t += parseDuration(item.duration) ?: 0
            start..t
        }
    }

    /**
     * Прибавка каждого шага с разобранным рубежом: рубеж минус ближайший разобранный рубеж выше (у первого — минус
     * ноль). Шаги, чей рубеж меньше рубежа выше, не попадают: их рубеж — ошибка.
     */
    fun increments(): Map<Long, Int> = buildMap {
        var previous = 0
        for (item in items) {
            // После шага «Тара» рубежи считаются от нуля заново.
            if (item.isTare) previous = 0
            val target = (item as? DraftItem.Step)?.waterG ?: continue
            if (target >= previous) put(item.key, target - previous)
            previous = maxOf(previous, target)
        }
    }

    /**
     * Копия элемента [key] сразу после него под ключом [newKey]. Рубеж копии шага сдвигается на прибавку шага:
     * так серия одинаковых проливов набирается дублированием. Без прибавки поле рубежа копируется как есть.
     */
    fun duplicate(key: Long, newKey: Long): RecipeDraft {
        val index = indexOf(key)
        if (index < 0) return this
        val copy = when (val item = items[index]) {
            is DraftItem.Step -> {
                val target = item.waterG
                val increment = increments()[key]
                item.copy(
                    key = newKey,
                    targetG = if (target != null && increment != null) (target + increment).toString() else item.targetG,
                )
            }
            is DraftItem.Hint -> item.copy(key = newKey)
        }
        return insert(index + 1, copy)
    }

    /** Сводка для карточки рецепта; поля, которые не разобрать, не учитываются. */
    fun summary(): DraftSummary {
        // Вода — сумма последних рубежей всех частей: после шага «Тара» рубежи считаются заново.
        val lastByPart = mutableListOf<Int?>(null)
        for (item in items) {
            if (item.isTare) lastByPart += null
            (item as? DraftItem.Step)?.waterG?.let { lastByPart[lastByPart.lastIndex] = it }
        }
        val water = lastByPart.filterNotNull().takeIf { it.isNotEmpty() }?.sum()
        val dose = parseGrams(doseG)?.takeIf { it > 0 }
        return DraftSummary(
            waterG = water,
            ratio = if (water != null && dose != null) water.toDouble() / dose else null,
            totalS = times().lastOrNull()?.last ?: 0,
        )
    }

    /** Готовый рецепт; только для черновика без ошибок [validate]. */
    fun toRecipe(): Recipe {
        check(validate(this).isEmpty()) { "draft has errors" }
        return Recipe(
            id = id,
            title = Text.Plain(title.trim()),
            defaultDoseG = parseGrams(doseG)!!,
            description = description.trim().takeIf { it.isNotEmpty() }?.let { Text.Plain(it) },
            items = items.map { item ->
                when (item) {
                    is DraftItem.Step -> RecipeItem.Step(
                        title = Text.Plain(item.title.trim()),
                        durationS = parseDuration(item.duration)!!,
                        note = item.note.trim().takeIf { it.isNotEmpty() }?.let { Text.Plain(it) },
                        // Вода — только у пролива: у действия и тары поля нет, даже если в черновике что-то осталось.
                        targetG = item.waterG,
                        tare = item.kind == StepKind.TARE,
                    )
                    is DraftItem.Hint -> RecipeItem.Hint(Text.Plain(item.text.trim()))
                }
            },
            category = category,
            difficulty = difficulty,
        )
    }

    companion object {
        const val DEFAULT_DOSE_G = 15
        const val NEW_STEP_DURATION_S = 30
        const val TARE_DURATION_S = 15
        const val USER_ID_PREFIX = "user:"

        fun newId(): String = USER_ID_PREFIX + UUID.randomUUID()
    }
}

/** Элемент черновика. [key] — постоянный внутри редактора: по нему список помнит раскрытые карточки. */
sealed interface DraftItem {
    val key: Long

    data class Step(
        override val key: Long,
        val title: String = "",
        /** Цифры длительности как набраны; поле показывает их маской «м:сс» («130» — 1:30). */
        val duration: String = durationDigits(RecipeDraft.NEW_STEP_DURATION_S),
        val targetG: String = "",
        val note: String = "",
        /** Вид шага задаётся при добавлении и дальше не меняется. */
        val kind: StepKind = StepKind.POUR,
    ) : DraftItem {
        /** Вода шага, если это пролив и число разобрано. */
        val waterG: Int? get() = if (kind == StepKind.POUR) parseGrams(targetG) else null
    }

    data class Hint(override val key: Long, val text: String = "") : DraftItem

    val isTare: Boolean get() = this is Step && kind == StepKind.TARE
}

/**
 * Вид шага: [POUR] — пролив, вода обязательна, табло показывает воду; [ACTION] — без воды, табло показывает
 * обратный отсчёт (подождать, взболтать, прожать); [TARE] — шаг «Тара», рубежи после него считаются от нуля.
 */
enum class StepKind { POUR, ACTION, TARE }

/** Итог черновика: вода (последний рубеж), соотношение вода / доза и общая длительность, с. */
data class DraftSummary(val waterG: Int?, val ratio: Double?, val totalS: Int)

/** Заготовка нового элемента из меню «+». После вставки это обычный шаг или подпись. */
enum class ItemTemplate {
    POUR, WAIT, ACTION, TARE, HINT;

    /** Новый элемент; [title] — название шага на языке интерфейса (у «Действия» и подписи не нужно). */
    fun newItem(key: Long, title: String = ""): DraftItem = when (this) {
        POUR -> DraftItem.Step(key, title = title)
        WAIT -> DraftItem.Step(key, title = title, kind = StepKind.ACTION)
        ACTION -> DraftItem.Step(key, kind = StepKind.ACTION)
        TARE -> DraftItem.Step(key, title = title, duration = durationDigits(RecipeDraft.TARE_DURATION_S), kind = StepKind.TARE)
        HINT -> DraftItem.Hint(key)
    }
}

/**
 * Рецепт как черновик: числа переносятся без пересчёта, тексты — через [resolve] (у встроенного — на языке
 * интерфейса). [id] — id черновика: свой для «Изменить», новый для копии.
 */
fun Recipe.toDraft(resolve: (Text) -> String, id: String = this.id): RecipeDraft = RecipeDraft(
    id = id,
    title = resolve(title),
    doseG = defaultDoseG.toString(),
    category = category,
    difficulty = difficulty,
    description = description?.let(resolve).orEmpty(),
    items = items.mapIndexed { i, item ->
        when (item) {
            is RecipeItem.Step -> DraftItem.Step(
                key = i.toLong(),
                title = resolve(item.title),
                duration = durationDigits(item.durationS),
                targetG = item.targetG?.toString().orEmpty(),
                note = item.note?.let(resolve).orEmpty(),
                kind = when {
                    item.tare -> StepKind.TARE
                    item.targetG != null -> StepKind.POUR
                    else -> StepKind.ACTION
                },
            )
            is RecipeItem.Hint -> DraftItem.Hint(i.toLong(), resolve(item.text))
        }
    },
)

/** Длительность цифрами, как в поле с маской «м:сс»: 90 с → «130», 12 с → «012». */
fun durationDigits(seconds: Int): String = "%d%02d".format(seconds / 60, seconds % 60)

/**
 * Длительность из цифр поля с маской «м:сс»: последние две цифры — секунды, всё перед ними — минуты
 * («130» — 1:30, «045» — 0:45). `null` — пусто, не цифры, набрано меньше трёх цифр или секунд больше 59.
 */
fun parseDuration(digits: String): Int? {
    val d = digits.trim()
    if (d.length < 3 || !DIGITS.matches(d)) return null
    val sec = d.takeLast(2).toInt()
    return if (sec < 60) d.dropLast(2).toInt() * 60 + sec else null
}

/** Целые граммы; `null` — пусто или не число. */
fun parseGrams(text: String): Int? = text.trim().takeIf { DIGITS.matches(it) }?.toIntOrNull()

private val DIGITS = Regex("""\d{1,5}""")

// region проверка

enum class DraftField { TITLE, DOSE, STEP_TITLE, DURATION, TARGET, HINT_TEXT }

enum class DraftProblem {
    /** Пусто. */
    EMPTY,

    /** Не число или не в формате. */
    INVALID,

    /** Ноль. */
    ZERO,

    /** Рубеж меньше рубежа шага выше. */
    BELOW_PREVIOUS,
}

sealed interface DraftError {
    /** Ошибка поля. [key] — элемент, `null` — карточка рецепта. [previousG] — рубеж выше для [BELOW_PREVIOUS]. */
    data class Field(val key: Long?, val field: DraftField, val problem: DraftProblem, val previousG: Int? = null) : DraftError

    /** В рецепте нет ни одного шага. */
    data object NoSteps : DraftError
}

/** Все ошибки черновика сверху вниз; пусто — можно сохранять. */
fun validate(draft: RecipeDraft): List<DraftError> = buildList {
    if (draft.title.isBlank()) add(DraftError.Field(null, DraftField.TITLE, DraftProblem.EMPTY))
    number(draft.doseG, null, DraftField.DOSE, ::parseGrams)?.let(::add)
    if (draft.items.none { it is DraftItem.Step }) add(DraftError.NoSteps)
    var previous: Int? = null
    for (item in draft.items) {
        when (item) {
            is DraftItem.Step -> {
                if (item.title.isBlank()) add(DraftError.Field(item.key, DraftField.STEP_TITLE, DraftProblem.EMPTY))
                number(item.duration, item.key, DraftField.DURATION, ::parseDuration)?.let(::add)
                // После шага «Тара» рубежи считаются от его нуля: рубеж выше него не ограничивает.
                if (item.kind == StepKind.TARE) previous = null
                // Вода обязательна у пролива; у действия и тары поля нет.
                if (item.kind == StepKind.POUR && item.targetG.isBlank()) {
                    add(DraftError.Field(item.key, DraftField.TARGET, DraftProblem.EMPTY))
                }
                if (item.kind == StepKind.POUR && item.targetG.isNotBlank()) {
                    val error = number(item.targetG, item.key, DraftField.TARGET, ::parseGrams)
                    val target = parseGrams(item.targetG)
                    when {
                        error != null -> add(error)
                        target != null && previous != null && target < previous ->
                            add(DraftError.Field(item.key, DraftField.TARGET, DraftProblem.BELOW_PREVIOUS, previous))
                    }
                    if (error == null && target != null) previous = maxOf(previous ?: 0, target)
                }
            }
            is DraftItem.Hint ->
                if (item.text.isBlank()) add(DraftError.Field(item.key, DraftField.HINT_TEXT, DraftProblem.EMPTY))
        }
    }
}

/** Обязательное положительное число. */
private fun number(text: String, key: Long?, field: DraftField, parse: (String) -> Int?): DraftError.Field? {
    val value = parse(text)
    val problem = when {
        text.isBlank() -> DraftProblem.EMPTY
        value == null -> DraftProblem.INVALID
        value == 0 -> DraftProblem.ZERO
        else -> return null
    }
    return DraftError.Field(key, field, problem)
}

// endregion
