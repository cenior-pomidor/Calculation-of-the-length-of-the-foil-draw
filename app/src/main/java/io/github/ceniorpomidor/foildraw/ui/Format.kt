package io.github.ceniorpomidor.foildraw.ui

import java.util.Locale

private val russian = Locale.forLanguageTag("ru-RU")

/** Числа в интерфейсе: десятичная запятая, как принято в русском тексте. */
object Format {
    fun mm(value: Double): String = String.format(russian, "%.2f", value)

    fun percent(fraction: Double): String = String.format(russian, "%.0f", fraction * 100)

    fun meters(value: Double): String = String.format(russian, "%.1f", value)

    /** Целое число с пробелами между разрядами: 20 000. */
    fun count(value: Long): String = String.format(russian, "%,d", value).replace('\u00A0', ' ').replace('\u202F', ' ')

    fun rolls(value: Double): String = String.format(russian, "%.2f", value)

    /** Форма слова для числа [count] из [forms] — для 1, 2–4 и 5+ по правилам русского языка. */
    fun plural(count: Long, forms: Array<String>): String {
        val lastTwo = count % 100
        val last = count % 10
        val form = when {
            lastTwo in 11..14 -> forms[2]
            last == 1L -> forms[0]
            last in 2..4 -> forms[1]
            else -> forms[2]
        }
        return "$count $form"
    }

    fun parse(text: CharSequence): Double? = text.toString().trim().replace(',', '.').toDoubleOrNull()
}
