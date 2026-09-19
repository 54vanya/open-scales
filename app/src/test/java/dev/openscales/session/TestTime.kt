package dev.openscales.session

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/**
 * Сессия и репозиторий живут в `backgroundScope`, а `advanceUntilIdle()` фоновые задачи не ждёт.
 * Поэтому явно прокручиваем виртуальное время.
 */
fun TestScope.settle(ms: Long = 60_000) {
    advanceTimeBy(ms)
    runCurrent()
}
