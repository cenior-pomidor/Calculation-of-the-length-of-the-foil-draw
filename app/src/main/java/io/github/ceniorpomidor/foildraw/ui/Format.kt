package io.github.ceniorpomidor.foildraw.ui

import java.util.Locale

private val russian = Locale.forLanguageTag("ru-RU")

/** Числа в интерфейсе: десятичная запятая, как принято в русском тексте. */
object Format {
    fun mm(value: Double): String = String.format(russian, "%.2f", value)

    fun percent(fraction: Double): String = String.format(russian, "%.0f", fraction * 100)

    fun meters(value: Double): String = String.format(russian, "%.1f", value)

    fun parse(text: CharSequence): Double? = text.toString().trim().replace(',', '.').toDoubleOrNull()
}
