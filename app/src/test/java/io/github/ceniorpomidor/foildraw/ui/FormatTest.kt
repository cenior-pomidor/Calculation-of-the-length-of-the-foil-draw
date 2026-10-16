package io.github.ceniorpomidor.foildraw.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    private val forms = arrayOf("ролик", "ролика", "роликов")

    @Test
    fun `russian plural forms`() {
        val expected = mapOf(
            1L to "1 ролик", 2L to "2 ролика", 4L to "4 ролика", 5L to "5 роликов",
            11L to "11 роликов", 12L to "12 роликов", 14L to "14 роликов", 21L to "21 ролик",
            22L to "22 ролика", 25L to "25 роликов", 101L to "101 ролик", 111L to "111 роликов",
        )
        for ((count, text) in expected) assertEquals(text, Format.plural(count, forms))
    }

    @Test
    fun `numbers use decimal comma`() {
        assertEquals("29,00", Format.mm(29.0))
        assertEquals("2,42", Format.rolls(290.0 / 120))
        assertEquals("348,0", Format.meters(348.0))
        assertEquals(10.5, Format.parse(" 10,5 ")!!, 0.0)
    }
}
