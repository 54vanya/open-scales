package dev.openscales.ui.scan

import dev.openscales.ui.scale.ScaleLink

/** Что делает нажатие на строку запомненных весов. */
enum class SavedRowAction { CONNECT, OPEN_DETAILS }

/**
 * Неподключённые весы — подключиться. Подключённые или подключающиеся — открыть карточку: повторное
 * подключение оборвало бы идущую попытку, а отключение нажатием на строку запрещено — только кнопкой в карточке.
 */
fun savedRowAction(link: ScaleLink): SavedRowAction =
    if (link == ScaleLink.DISCONNECTED) SavedRowAction.CONNECT else SavedRowAction.OPEN_DETAILS

/** Поиск в эфире замедляет обмен с подключёнными весами, поэтому сам он запускается, только пока их нет. */
fun shouldAutoScan(scaleReady: Boolean): Boolean = !scaleReady
