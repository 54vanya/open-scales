package dev.openscales.recipe

import androidx.annotation.StringRes
import dev.openscales.R

object BuiltInRecipes {

    // У каждого шага без воды (подождать, взболтать, прожать) табло варки крупно показывает время до конца шага:
    // вода на этих шагах уже не важна.

    /** V60 по мотивам Джеймса Хоффмана. */
    val hoffmannV60 = Recipe(
        id = "builtin:hoffmann-v60",
        title = Text.Res(R.string.recipe_hoffmann_title),
        defaultDoseG = 15,
        description = Text.Res(R.string.recipe_hoffmann_description),
        items = listOf(
            RecipeItem.Step(Text.Res(R.string.recipe_step_bloom), 12, Text.Res(R.string.recipe_note_pour_slowly), 30),
            RecipeItem.Hint(Text.Res(R.string.recipe_hint_bloom_total)),
            RecipeItem.Step(Text.Res(R.string.recipe_step_swirl), 5),
            RecipeItem.Step(Text.Res(R.string.recipe_step_wait), 28),
            RecipeItem.Step(Text.Res(R.string.recipe_step_pour), 30, Text.Res(R.string.recipe_note_spiral), 150),
            RecipeItem.Step(Text.Res(R.string.recipe_step_pour), 30, Text.Res(R.string.recipe_note_center), 250),
            RecipeItem.Step(Text.Res(R.string.recipe_step_wait), 15),
            RecipeItem.Step(Text.Res(R.string.recipe_step_swirl), 3, Text.Res(R.string.recipe_note_gently)),
            RecipeItem.Step(Text.Res(R.string.recipe_step_wait), 87),
            RecipeItem.Hint(Text.Res(R.string.recipe_hint_drawdown)),
        ),
        difficulty = Difficulty.MEDIUM,
    )

    /**
     * 4:6 Тэцу Касуи, чемпиона мира по заварке 2016, по видео HARIO: пять проливов по 60 г каждые 45 секунд.
     * Сам пролив в видео не засечён — 10 с, остальное ожидание.
     */
    val kasuya46 = recipe(
        "builtin:kasuya-4-6", R.string.recipe_kasuya_title, 20, R.string.recipe_kasuya_description,
        step(R.string.recipe_step_pour, 10, 60, R.string.recipe_note_spiral_center),
        step(R.string.recipe_step_wait, 35),
        step(R.string.recipe_step_pour, 10, 120),
        step(R.string.recipe_step_wait, 35),
        step(R.string.recipe_step_pour, 10, 180),
        step(R.string.recipe_step_wait, 35),
        step(R.string.recipe_step_pour, 10, 240),
        step(R.string.recipe_step_wait, 35),
        step(R.string.recipe_step_pour, 10, 300),
        step(R.string.recipe_step_wait, 20),
        hint(R.string.recipe_hint_remove_dripper),
        difficulty = Difficulty.MEDIUM,
    )

    /**
     * V60 Скотта Рао по его видео: цветение с размешиванием ложкой, один пролив до конца, круг ложкой у стенок,
     * в 1:45 покрутить воронку. Основной пролив в видео смонтирован — 30 с.
     */
    val raoV60 = recipe(
        "builtin:rao-v60", R.string.recipe_rao_title, 22, R.string.recipe_rao_description,
        step(R.string.recipe_step_bloom, 10, 66, R.string.recipe_note_stir_spoon_gently),
        step(R.string.recipe_step_wait, 35),
        step(R.string.recipe_step_pour, 30, 360, R.string.recipe_note_circles),
        step(R.string.recipe_step_stir, 5, note = R.string.recipe_note_stir_one_circle),
        step(R.string.recipe_step_wait, 25),
        step(R.string.recipe_step_swirl, 5, note = R.string.recipe_note_spin_level),
        step(R.string.recipe_step_wait, 100),
        hint(R.string.recipe_hint_drawdown_330),
        difficulty = Difficulty.MEDIUM,
    )

    /**
     * V60 Ленса Хедрика «One and Done» по его видео: два цветения по 45 г без взбалтывания (около 7 мл/с — 6 с)
     * и один энергичный пролив, «заняло 12–13 секунд».
     */
    val hedrickV60 = recipe(
        "builtin:hedrick-one-and-done", R.string.recipe_hedrick_title, 15, R.string.recipe_hedrick_description,
        step(R.string.recipe_step_bloom, 6, 45, R.string.recipe_note_spiral_no_swirl),
        step(R.string.recipe_step_wait, 24),
        step(R.string.recipe_step_bloom_second, 6, 90, R.string.recipe_note_no_swirl),
        step(R.string.recipe_step_wait, 24),
        step(R.string.recipe_step_pour, 13, 225, R.string.recipe_note_energetic_coin),
        step(R.string.recipe_step_wait, 77),
        hint(R.string.recipe_hint_drawdown_230),
        difficulty = Difficulty.EASY,
    )

    val kalitaWave = recipe(
        "builtin:kalita-wave", R.string.recipe_kalita_title, 21, R.string.recipe_kalita_description,
        step(R.string.recipe_step_bloom, 10, 60),
        step(R.string.recipe_step_wait, 35),
        step(R.string.recipe_step_pour, 15, 200, R.string.recipe_note_spiral_slowly),
        step(R.string.recipe_step_pour, 60, 345, R.string.recipe_note_pulses),
        step(R.string.recipe_step_wait, 60),
        difficulty = Difficulty.EASY,
        category = RecipeCategory.FLAT_BOTTOM,
    )

    val chemex = recipe(
        "builtin:chemex", R.string.recipe_chemex_title, 42, R.string.recipe_chemex_description,
        step(R.string.recipe_step_bloom, 15, 150),
        step(R.string.recipe_step_stir, 10, note = R.string.recipe_note_stir_dry),
        step(R.string.recipe_step_wait, 20),
        step(R.string.recipe_step_pour, 60, 450, R.string.recipe_note_wiggle),
        step(R.string.recipe_step_pour, 45, 700, R.string.recipe_note_to_top),
        step(R.string.recipe_step_wait, 90),
        difficulty = Difficulty.EASY,
        category = RecipeCategory.CHEMEX,
    )

    /** «Ultimate AeroPress» Джеймса Хоффмана: без промывки и прогрева, настой под поршнем, медленный отжим. */
    val hoffmannAeropress = recipe(
        "builtin:hoffmann-aeropress", R.string.recipe_hoffmann_aeropress_title, 11,
        R.string.recipe_hoffmann_aeropress_description,
        step(R.string.recipe_step_pour, 10, 200, R.string.recipe_note_plunger_on),
        step(R.string.recipe_step_wait, 110),
        step(R.string.recipe_step_swirl, 5, note = R.string.recipe_note_hold_both),
        step(R.string.recipe_step_wait, 25),
        step(R.string.recipe_step_press, 30, note = R.string.recipe_note_press_slowly),
        category = RecipeCategory.AEROPRESS,
        difficulty = Difficulty.EASY,
    )

    /** Перевёрнутый аэропресс с цветением (по рецепту Blue Bottle): поршень снизу, переворот перед отжимом. */
    val invertedAeropress = recipe(
        "builtin:inverted-aeropress", R.string.recipe_inverted_aeropress_title, 15,
        R.string.recipe_inverted_aeropress_description,
        step(R.string.recipe_step_bloom, 10, 30),
        step(R.string.recipe_step_stir, 5, note = R.string.recipe_note_stir_three_four),
        step(R.string.recipe_step_wait, 30),
        step(R.string.recipe_step_pour, 10, 200),
        step(R.string.recipe_step_wait, 65),
        step(R.string.recipe_step_flip, 10, note = R.string.recipe_note_flip),
        step(R.string.recipe_step_press, 30, note = R.string.recipe_note_press_gently),
        category = RecipeCategory.AEROPRESS,
        difficulty = Difficulty.MEDIUM,
    )

    /** «A Better 1-Cup V60» Джеймса Хоффмана: небольшая чашка, пять одинаковых проливов по 50 г. */
    val hoffmannOneCup = recipe(
        "builtin:hoffmann-v60-one-cup", R.string.recipe_hoffmann_one_cup_title, 15,
        R.string.recipe_hoffmann_one_cup_description,
        step(R.string.recipe_step_bloom, 10, 50),
        step(R.string.recipe_step_swirl, 10),
        step(R.string.recipe_step_wait, 25),
        step(R.string.recipe_step_pour, 15, 100),
        step(R.string.recipe_step_wait, 10),
        step(R.string.recipe_step_pour, 10, 150),
        step(R.string.recipe_step_wait, 10),
        step(R.string.recipe_step_pour, 10, 200),
        step(R.string.recipe_step_wait, 10),
        step(R.string.recipe_step_pour, 10, 250),
        step(R.string.recipe_step_swirl, 10, note = R.string.recipe_note_gently),
        step(R.string.recipe_step_wait, 50),
        hint(R.string.recipe_hint_drawdown_300),
        difficulty = Difficulty.HARD,
    )

    /**
     * Hario Switch Тэцу Касуи (гибрид) по его видео: два пролива с открытым клапаном через 30 с, в 1:15 закрыть клапан
     * и долить водой около 75 °C, в 1:45 открыть.
     */
    val kasuyaSwitch = recipe(
        "builtin:kasuya-switch", R.string.recipe_kasuya_switch_title, 20,
        R.string.recipe_kasuya_switch_description,
        step(R.string.recipe_step_pour, 10, 60, R.string.recipe_note_valve_open_93),
        step(R.string.recipe_step_wait, 20),
        step(R.string.recipe_step_pour, 10, 120, R.string.recipe_note_spiral),
        step(R.string.recipe_step_wait, 35),
        step(R.string.recipe_step_pour, 15, 280, R.string.recipe_note_valve_close_75),
        step(R.string.recipe_step_wait, 15),
        step(R.string.recipe_step_open_valve, 75),
        hint(R.string.recipe_hint_drawdown_300),
        difficulty = Difficulty.HARD,
        category = RecipeCategory.SWITCH,
    )

    /** Френч-пресс Джеймса Хоффмана: долгий настой, корку снять, поршень не прожимать — только опустить к поверхности. */
    val hoffmannFrenchPress = recipe(
        "builtin:hoffmann-french-press", R.string.recipe_hoffmann_french_press_title, 30,
        R.string.recipe_hoffmann_french_press_description,
        step(R.string.recipe_step_pour, 10, 500, R.string.recipe_note_all_at_once),
        step(R.string.recipe_step_wait, 230),
        step(R.string.recipe_step_break_crust, 15, note = R.string.recipe_note_break_crust),
        step(R.string.recipe_step_skim, 30, note = R.string.recipe_note_skim),
        step(R.string.recipe_step_wait, 300, note = R.string.recipe_note_up_to_8_min),
        hint(R.string.recipe_hint_plunge_to_surface),
        category = RecipeCategory.FRENCH_PRESS,
        difficulty = Difficulty.EASY,
    )

    /**
     * Победитель World AeroPress Championship 2022 — Джибби Литтл (Австралия). Время размешивания и прожима
     * в источнике не заданы по секундам — взяты по описанию (35 мягких движений, прожим 1:40–2:10).
     */
    val littleAeropress = recipe(
        "builtin:wac-2022-little", R.string.recipe_wac2022_title, 18,
        R.string.recipe_wac2022_description,
        step(R.string.recipe_step_pour, 10, 94, R.string.recipe_note_inverted),
        step(R.string.recipe_step_stir, 20, note = R.string.recipe_note_stir_35),
        step(R.string.recipe_step_wait, 50),
        step(R.string.recipe_step_cap, 10, note = R.string.recipe_note_remove_air),
        step(R.string.recipe_step_flip, 10),
        step(R.string.recipe_step_press, 30, note = R.string.recipe_note_press_gently),
        hint(R.string.recipe_hint_dilute_150),
        category = RecipeCategory.AEROPRESS,
        difficulty = Difficulty.MEDIUM,
    )

    /**
     * Победитель World AeroPress Championship 2019 — Венделин ван Бюнник (Нидерланды): много кофе, короткий
     * настой, разбавление после прожима. Длительность прожима в источнике не задана — 30 с. Разбавление —
     * отдельная часть рецепта: аэропресс снимают, весы тарируют с чашкой концентрата и доливают 100 г.
     */
    val vanBunnikAeropress = recipe(
        "builtin:wac-2019-van-bunnik", R.string.recipe_wac2019_title, 30,
        R.string.recipe_wac2019_description,
        step(R.string.recipe_step_pour, 10, 100, R.string.recipe_note_inverted),
        step(R.string.recipe_step_stir, 10, note = R.string.recipe_note_stir_20_firm),
        step(R.string.recipe_step_cap, 20, note = R.string.recipe_note_remove_air),
        step(R.string.recipe_step_flip, 5),
        step(R.string.recipe_step_press, 30, note = R.string.recipe_note_press_all),
        tare(10, R.string.recipe_note_remove_aeropress),
        step(R.string.recipe_step_dilute, 15, 100, R.string.recipe_note_hot_water_to_taste),
        category = RecipeCategory.AEROPRESS,
        difficulty = Difficulty.MEDIUM,
    )

    /**
     * Победитель World Brewers Cup 2024 — Мартин Вёльфль (Австрия), по его видео: цветение 40 с, доливы в 0:40, 1:20
     * и 2:00, между ними ждать, пока протечёт, конец в 2:20–2:25.
     */
    val woelflPourOver = recipe(
        "builtin:wbrc-2024-woelfl", R.string.recipe_wbrc2024_title, 17,
        R.string.recipe_wbrc2024_description,
        step(R.string.recipe_step_bloom, 10, 60),
        step(R.string.recipe_step_wait, 30),
        step(R.string.recipe_step_pour, 10, 120),
        step(R.string.recipe_step_wait, 30),
        step(R.string.recipe_step_pour, 10, 170),
        step(R.string.recipe_step_wait, 30),
        step(R.string.recipe_step_pour, 15, 270),
        step(R.string.recipe_step_wait, 10, note = R.string.recipe_note_until_drained),
        difficulty = Difficulty.MEDIUM,
        category = RecipeCategory.FLAT_BOTTOM,
    )

    /**
     * Победитель World Brewers Cup 2022 — Шерри Хсу (Тайвань): четыре пролива по 50 г каждые 30 с, первый — водой
     * 70 °C. Конец в 2:30–3:00 — по стороннему разбору рецепта, в её выступлении время слива не названо.
     */
    val hsuPourOver = recipe(
        "builtin:wbrc-2022-hsu", R.string.recipe_wbrc2022_title, 14,
        R.string.recipe_wbrc2022_description,
        step(R.string.recipe_step_pour, 10, 50, R.string.recipe_note_water_70),
        step(R.string.recipe_step_wait, 20),
        step(R.string.recipe_step_pour, 10, 100, R.string.recipe_note_water_95),
        step(R.string.recipe_step_wait, 20),
        step(R.string.recipe_step_pour, 10, 150),
        step(R.string.recipe_step_wait, 20),
        step(R.string.recipe_step_pour, 10, 200),
        step(R.string.recipe_step_wait, 50, note = R.string.recipe_note_until_drained),
        difficulty = Difficulty.HARD,
        category = RecipeCategory.FLAT_BOTTOM,
    )

    /** Простой рецепт Hario Switch Эми Фукахори (чемпион World Brewers Cup 2018) из кофеен MAME. */
    val fukahoriSwitch = recipe(
        "builtin:fukahori-switch", R.string.recipe_fukahori_switch_title, 14,
        R.string.recipe_fukahori_switch_description,
        step(R.string.recipe_step_pour, 10, 50, R.string.recipe_note_valve_closed),
        step(R.string.recipe_step_wait, 20),
        step(R.string.recipe_step_pour, 40, 200, R.string.recipe_note_open_pour_center),
        step(R.string.recipe_step_wait, 70, note = R.string.recipe_note_until_drained),
        difficulty = Difficulty.EASY,
        category = RecipeCategory.SWITCH,
    )

    val all: List<Recipe> = listOf(
        hoffmannV60, hoffmannOneCup, kasuya46, raoV60, hedrickV60,
        kalitaWave, woelflPourOver, hsuPourOver,
        kasuyaSwitch, fukahoriSwitch,
        chemex,
        hoffmannAeropress, invertedAeropress, littleAeropress, vanBunnikAeropress,
        hoffmannFrenchPress,
    )

    fun byId(id: String?): Recipe? = all.firstOrNull { it.id == id }

    // Рецепты записаны рубежами в граммах при дозе по умолчанию — как в источниках.
    private fun step(
        @StringRes title: Int,
        durationS: Int,
        targetG: Int? = null,
        @StringRes note: Int? = null,
    ) = RecipeItem.Step(Text.Res(title), durationS, note?.let { Text.Res(it) }, targetG)

    private fun tare(durationS: Int, @StringRes note: Int) =
        RecipeItem.Step(Text.Res(R.string.recipe_step_tare), durationS, Text.Res(note), tare = true)

    private fun hint(@StringRes text: Int) = RecipeItem.Hint(Text.Res(text))

    private fun recipe(
        id: String,
        @StringRes title: Int,
        doseG: Int,
        @StringRes description: Int,
        vararg items: RecipeItem,
        category: RecipeCategory = RecipeCategory.V60,
        difficulty: Difficulty,
    ) = Recipe(id, Text.Res(title), doseG, Text.Res(description), items.toList(), category, difficulty)
}
