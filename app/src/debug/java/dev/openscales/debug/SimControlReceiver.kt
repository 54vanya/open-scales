package dev.openscales.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.openscales.OpenScalesApp

/**
 * Команды виртуальным весам из терминала (только debug):
 * `adb shell am broadcast -n dev.openscales/.debug.SimControlReceiver -a dev.openscales.SIM --es cmd "pour 250 30"`.
 * Ответ (`ok`, состояние или ошибка) возвращается в `data` результата — его печатает `am broadcast`.
 * Приёмник работает на главном потоке, там же, где всё состояние сессии.
 */
class SimControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val simulator = (context.applicationContext as OpenScalesApp).simulator
        resultData = simulator?.execute(intent.getStringExtra(EXTRA_COMMAND).orEmpty()) ?: "error: no simulator"
    }

    private companion object {
        const val EXTRA_COMMAND = "cmd"
    }
}
