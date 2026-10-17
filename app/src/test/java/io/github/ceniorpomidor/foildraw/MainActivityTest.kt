package io.github.ceniorpomidor.foildraw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-xxhdpi")
class MainActivityTest {

    private fun launch(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun MainActivity.text(id: Int) = findViewById<TextView>(id).text.toString()

    /** Видимые поля схемы по порядку участков: элемент, промежуток, элемент, … */
    private fun MainActivity.visibleFields(): List<EditText> {
        val diagram = findViewById<ViewGroup>(R.id.diagram)
        return (0 until diagram.childCount)
            .map { diagram.getChildAt(it) }
            .filter { it.visibility == View.VISIBLE }
            .filterIsInstance<EditText>()
    }

    /** Снимок всего прокручиваемого экрана в app/build/outputs/screenshots. */
    private fun MainActivity.snapshot(name: String) {
        // Даём пройти кадрам, как на устройстве: схема дорисовывает анимацию, а поля,
        // ставшие видимыми, прокручивают текст на место в onPreDraw.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        val content = findViewById<ScrollView>(R.id.scroll).getChildAt(0)
        content.measure(
            View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(getColor(R.color.bg))
        content.draw(canvas)
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `defaults reproduce the sketch example`() {
        val activity = launch()

        assertEquals(3, activity.visibleFields().size)
        assertEquals("29,00 мм", activity.text(R.id.pull_value))
        assertEquals("Допустимо от 28,67 до 29,50 мм", activity.text(R.id.pull_window))
        assertEquals("2,00 мм", activity.text(R.id.clearance_value))
        assertEquals("66 %", activity.text(R.id.savings_value))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.usage_missing).visibility)
        activity.snapshot("2-elements")
    }

    @Test
    fun `rolls for the run and make-ready`() {
        val activity = launch()

        activity.findViewById<EditText>(R.id.run).setText("10000")
        activity.findViewById<EditText>(R.id.roll_length).setText("120")
        assertEquals("290,0 м · 2,42 ролика", activity.text(R.id.usage_run))
        assertEquals("На приладку (20 %)", activity.text(R.id.usage_make_ready_title))
        assertEquals("58,0 м · 0,48 ролика", activity.text(R.id.usage_make_ready))
        assertEquals("3 ролика", activity.text(R.id.usage_total))
        assertEquals("348,0 м вместе с приладкой", activity.text(R.id.usage_total_hint))

        activity.findViewById<EditText>(R.id.run).setText("2000")
        assertEquals("1 ролик", activity.text(R.id.usage_total))
        activity.findViewById<EditText>(R.id.run).setText("20000")
        assertEquals("6 роликов", activity.text(R.id.usage_total))
        activity.snapshot("foil-usage")
    }

    @Test
    fun `three and four elements`() {
        val activity = launch()

        activity.findViewById<RadioButton>(R.id.count_3).performClick()
        assertEquals(5, activity.visibleFields().size)
        assertEquals("100,00 мм", activity.text(R.id.pull_value))
        activity.snapshot("3-elements")

        activity.findViewById<RadioButton>(R.id.count_4).performClick()
        assertEquals(7, activity.visibleFields().size)
        assertEquals("150,00 мм", activity.text(R.id.pull_value))
        activity.snapshot("4-elements")
    }

    @Test
    fun `comma and dot are both decimal separators`() {
        val activity = launch()
        val (first, gap, second) = activity.visibleFields()

        first.setText("10,0")
        gap.setText("60.0")
        second.setText("15")
        assertEquals("29,00 мм", activity.text(R.id.pull_value))
    }

    @Test
    fun `empty field hides the result`() {
        val activity = launch()

        activity.visibleFields().first().setText("")
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.invalid_input).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.result).visibility)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `dark theme`() {
        launch().snapshot("2-elements-night")
    }

    @Test
    fun `done key hides the keyboard and leaves the field`() {
        val activity = launch()
        val field = activity.visibleFields()[1]
        val keyboard = activity.getSystemService(InputMethodManager::class.java)

        field.requestFocus()
        keyboard.showSoftInput(field, 0)
        assertTrue(shadowOf(keyboard).isSoftInputVisible)

        field.onEditorAction(EditorInfo.IME_ACTION_DONE)
        assertFalse(shadowOf(keyboard).isSoftInputVisible)
        assertFalse(field.hasFocus())
    }

    @Test
    fun `out of range value is outlined`() {
        val activity = launch()
        val field = activity.visibleFields().first()

        field.setText("5000")
        assertTrue(field.isActivated)
        field.setText("")
        assertFalse(field.isActivated)
        field.setText("12")
        assertFalse(field.isActivated)
    }

    @Test
    fun `diagram follows entered sizes`() {
        val activity = launch()
        val (_, gap, second) = activity.visibleFields()
        gap.setText("20")
        second.setText("40")
        gap.requestFocus()
        activity.snapshot("diagram-resized")
    }
}
