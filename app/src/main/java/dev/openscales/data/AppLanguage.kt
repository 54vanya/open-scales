package dev.openscales.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.RequiresApi
import java.util.Locale

/** Язык интерфейса: как в системе или явно выбранный. [tag] — тег языка, для «как в системе» пустой. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    RUSSIAN("ru"),
    ENGLISH("en");

    companion object {
        /** По тегу первого выбранного языка (`ru`, `ru-RU`, `en-US`); незнакомый или пустой — как в системе. */
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.isNotEmpty() && tag?.substringBefore('-') == it.tag } ?: SYSTEM
    }
}

/** Где хранится выбор языка. */
interface AppLanguageStore {
    fun current(): AppLanguage
    fun set(language: AppLanguage)
}

/**
 * Android 13+: язык приложения хранит сама система — это та же настройка, что в системных
 * «Приложения → Open Scales → Язык». При смене система сама пересоздаёт открытые экраны.
 */
@RequiresApi(33)
class SystemAppLanguageStore(context: Context) : AppLanguageStore {
    private val manager = context.getSystemService(LocaleManager::class.java)

    override fun current(): AppLanguage =
        manager.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.let { AppLanguage.fromTag(it.toLanguageTag()) }
            ?: AppLanguage.SYSTEM

    override fun set(language: AppLanguage) {
        manager.applicationLocales =
            if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
    }
}

/**
 * Android 8–12: системного языка приложения нет. Выбор хранится у приложения (читается синхронно —
 * он нужен в `attachBaseContext`), а применяется обёрткой контекста каждого экрана ([withAppLanguage]).
 */
class LocalAppLanguageStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) : AppLanguageStore {
    override fun current(): AppLanguage = AppLanguage.fromTag(read())

    override fun set(language: AppLanguage) = write(language.tag)
}

/**
 * Контекст экрана с выбранными языком и темой, если их не применяет система: язык — на Android < 13,
 * тема — на Android < 12. «Как в системе» конфигурацию не меняет.
 */
fun Context.withAppConfiguration(language: AppLanguage?, theme: ThemeMode?): Context {
    val base = resources.configuration
    val config = Configuration(base)
    if (language != null && language != AppLanguage.SYSTEM) config.setLocale(Locale.forLanguageTag(language.tag))
    if (theme != null) config.uiMode = theme.applyTo(base.uiMode)
    return if (config == base) this else createConfigurationContext(config)
}
