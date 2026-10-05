# Design

## Context

Все Activity включают `enableEdgeToEdge()`, экраны — `Scaffold`. Отступы содержимого `Scaffold` (`padding`) уже включают системные панели и вырез (проверено на экране варки с эмуляцией выреза). Экран варки и вкладка «Весы» применяют `padding` целиком. Списки остальных экранов берут из него только верх и низ, а бока задают константой:

```kotlin
contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)
```

Так сделано в `SettingsScreen`, `ScaleDetailsScreen`, `ScanScreen.DeviceList`, `RecipesScreen`, `RecipeEditorScreen`, `JournalActivity` — боковая часть отступов теряется. Панель кнопок редактора использует `navigationBarsPadding()`, в котором выреза нет.

## Goals / Non-Goals

**Goals:**
- Одно место, где боковые отступы списка складываются с отступами `Scaffold`, чтобы новый экран не повторил ошибку.

**Non-Goals:**
- Альбомные раскладки этих экранов (две колонки, кнопки сбоку) — только отступы.
- Окна (`AlertDialog`, выбор рецепта): система сама держит их вне выреза.
- Экран варки и вкладка «Весы» — уже верно.

## Decisions

### Помощник `screenContentPadding(...)` в `ui/components`
Composable-функция `screenContentPadding(padding, horizontal = 16.dp, top = 0.dp, bottom = 0.dp): PaddingValues` — читает `LocalLayoutDirection` и возвращает `start = padding.calculateStartPadding(dir) + horizontal`, `end = …EndPadding(dir) + horizontal`, `top = …Top + top`, `bottom = …Bottom + bottom`. Все шесть списков переходят на неё, прежние добавки (`+ 24.dp`, `+ 96.dp`, `FAB_CLEARANCE`, `+ 8.dp`) передаются параметрами. Расчёт — чистая функция от `PaddingValues` и `LayoutDirection`, покрывается юнит-тестом без Compose.

*Альтернатива:* `Modifier.padding(padding)` на списке — содержимое перестало бы прокручиваться под заголовок и панель жестов (сейчас список уходит под сворачивающийся заголовок). `contentWindowInsets` у `Scaffold` менять не нужно — он уже верный.

### Редактор: низ списка и панель кнопок
Список редактора сжимается над кнопками и клавиатурой (`padding(bottom)` + `consumeWindowInsets` + `imePadding`) — это остаётся; `contentPadding` получает боковые отступы через помощник (верх — как сейчас, низ — 16 dp без системного, он уже учтён модификатором). Ряд кнопок в `bottomBar`: к `navigationBarsPadding()` добавляется `windowInsetsPadding(WindowInsets.displayCutout.only(Horizontal))`; `Surface` вокруг ряда остаётся на всю ширину. `safeDrawing` здесь не подходит: он включает клавиатуру, и кнопки поднимались бы над ней, закрывая поле ввода (поймано на эмуляторе), а по спецификации редактора кнопки остаются под клавиатурой.

### Вкладка «Рецепты» в широком окне
Слева стоит рейка, которая уже забрала левый отступ (`consumeWindowInsets(Start)` у `Scaffold`), поэтому помощник даст слева ноль, а справа — вырез/панель. Отдельной логики не нужно.

### Проверка — эмулятор с эмуляцией выреза
Robolectric оконных отступов не даёт: эталоны скриншотов меняться не должны (это и есть проверка «без выреза ничего не меняется»). Вырез проверяется на эмуляторе: `adb shell cmd overlay enable com.android.internal.display.cutout.emulation.tall`, обе альбомные ориентации (`cmd window user-rotation lock 1|3`), каждый экран из списка; панель навигации сбоку — `cmd overlay enable com.android.internal.systemui.navbar.threebutton`.

## Risks / Trade-offs

- [Двойной отступ, если внутри списка что-то само читает оконные отступы] → `Scaffold` отступы не «потребляет» для содержимого; после правки каждый экран просматривается в обеих ориентациях (на экране варки двойной отступ уже ловили).
- [Сворачивающийся заголовок (`MediumFlexibleTopAppBar`) и `nestedScroll`] → верхний отступ берётся как раньше из `padding`, поведение заголовка не затрагивается.
