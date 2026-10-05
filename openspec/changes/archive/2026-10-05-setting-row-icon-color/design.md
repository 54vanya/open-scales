# Design

## Context

`SettingRow` передавал в `Icon` `tint = Color.Unspecified` для обычных строк. Для `Icon` это «не окрашивать»: векторный значок Material рисуется своим исходным чёрным. `ChoiceRow` и `StandbyRow` оттенок не задают и получают `LocalContentColor` — поэтому их значки светлые. В светлой теме разница почти незаметна (чёрный против тёмно-серого), в тёмной значки пропадают.

## Goals / Non-Goals

**Goals:** один цвет значков у всех строк настроек, взятый из темы.

**Non-Goals:** смена самих значков, цветов подписей и раскладки строк.

## Decisions

### `LocalContentColor.current` вместо `Color.Unspecified`
В `SettingRow`: `tint = if (destructive) colorScheme.error else LocalContentColor.current` — то же, что `Icon` берёт по умолчанию внутри `SegmentedListItem`, значит совпадает с `ChoiceRow`. Для подписи `Color.Unspecified` остаётся: у `Text` это «цвет из стиля и темы», там ошибки нет.

*Альтернатива:* убрать `tint` и строить `Icon` двумя ветками — то же поведение, больше кода.

### Скриншот-тест тёмных настроек
`SettingsScreenshotTest.settingsDark` рисует `SettingsScreen` в тёмной теме: в кадре строки всех видов. Экран настроек весов использует тот же `SettingRow`, отдельного эталона не заводим.

## Risks / Trade-offs

- [В светлой теме значки строк со значением станут чуть светлее (цвет содержимого вместо чистого чёрного)] → так и должно быть: теперь они совпадают со значками соседних строк.
