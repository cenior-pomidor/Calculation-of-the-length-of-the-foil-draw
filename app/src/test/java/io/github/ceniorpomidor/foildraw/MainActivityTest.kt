package io.github.ceniorpomidor.foildraw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import io.github.ceniorpomidor.foildraw.ui.FoilSchemeView
import io.github.ceniorpomidor.foildraw.ui.SwipePager
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
            .map { diagram.getChildAt(it) as ViewGroup }
            .filter { it.visibility == View.VISIBLE }
            .map { box -> (0 until box.childCount).map { box.getChildAt(it) }.filterIsInstance<EditText>().single() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Экран целиком, как его видно на телефоне, — после перехода на вкладку [tab]. */
    private fun MainActivity.screen(name: String, tab: Int) {
        findViewById<RadioButton>(tab).performClick()
        idle()
        val decor = window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        save(bitmap, name)
    }

    /** Вся прокручиваемая лента экрана [page] целиком, без обрезки по высоте телефона. */
    private fun MainActivity.fullPage(name: String, page: Int) {
        idle()
        val content = findViewById<ScrollView>(page).getChildAt(0)
        content.measure(
            View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(getColor(R.color.bg))
        content.draw(canvas)
        save(bitmap, name)
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
        activity.screen("1-die", R.id.tab_die)
        activity.screen("2-pull", R.id.tab_pull)
        activity.screen("3-usage", R.id.tab_usage)
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
        activity.screen("3-usage-filled", R.id.tab_usage)
    }

    @Test
    fun `three and four elements`() {
        val activity = launch()

        activity.findViewById<RadioButton>(R.id.count_3).performClick()
        assertEquals(5, activity.visibleFields().size)
        assertEquals("100,00 мм", activity.text(R.id.pull_value))
        activity.screen("2-pull-3-elements", R.id.tab_pull)

        activity.findViewById<RadioButton>(R.id.count_4).performClick()
        assertEquals(7, activity.visibleFields().size)
        assertEquals("150,00 мм", activity.text(R.id.pull_value))
    }

    @Test
    fun `seven elements`() {
        val activity = launch()

        activity.findViewById<RadioButton>(R.id.count_7).performClick()
        assertEquals(13, activity.visibleFields().size)
        assertEquals("187,50 мм", activity.text(R.id.pull_value))
        activity.fullPage("1-die-7-elements", R.id.page_die)
        activity.screen("2-pull-7-elements", R.id.tab_pull)
    }

    @Test
    fun `impressions scheme fits the screen without scrolling`() {
        val activity = launch()
        activity.findViewById<RadioButton>(R.id.count_7).performClick()
        activity.screen("2-pull-7-elements-check", R.id.tab_pull)

        val scheme = activity.findViewById<View>(R.id.scheme)
        val location = IntArray(2).also(scheme::getLocationInWindow)
        assertTrue(scheme.height > 0)
        assertTrue(location[1] + scheme.height <= activity.window.decorView.height)
    }

    @Test
    fun `impressions tape scrolls endlessly`() {
        val activity = launch()
        activity.findViewById<RadioButton>(R.id.tab_pull).performClick()
        idle()
        val scheme = activity.findViewById<FoilSchemeView>(R.id.scheme)
        assertEquals(1, scheme.visibleStrikes().first)

        scheme.scrollBy(100_000f)
        assertTrue(scheme.visibleStrikes().first > 100)
        activity.screen("2-pull-scrolled", R.id.tab_pull)

        scheme.scrollBy(-1_000_000f)
        assertEquals(0f, scheme.offset)
    }

    @Test
    fun `tabs and swipe switch screens`() {
        val activity = launch()
        val pager = activity.findViewById<SwipePager>(R.id.pager)
        val tabs = activity.findViewById<RadioGroup>(R.id.tabs)

        activity.findViewById<RadioButton>(R.id.tab_usage).performClick()
        idle()
        assertEquals(2, pager.page)

        // Свайп вправо — на предыдущий экран.
        val y = pager.height / 2f
        val start = SystemClock.uptimeMillis()
        pager.dispatchTouchEvent(MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, pager.width * 0.2f, y, 0))
        for (step in 1..10) {
            val x = pager.width * (0.2f + 0.06f * step)
            pager.dispatchTouchEvent(MotionEvent.obtain(start, start + step * 10L, MotionEvent.ACTION_MOVE, x, y, 0))
        }
        pager.dispatchTouchEvent(MotionEvent.obtain(start, start + 110L, MotionEvent.ACTION_UP, pager.width * 0.8f, y, 0))
        idle()
        assertEquals(1, pager.page)
        assertEquals(R.id.tab_pull, tabs.checkedRadioButtonId)
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
        val activity = launch()
        activity.screen("1-die-night", R.id.tab_die)
        activity.screen("2-pull-night", R.id.tab_pull)
    }

    @Test
    fun `done key hides the keyboard and leaves the field`() {
        val activity = launch()
        val field = activity.visibleFields()[1]
        val keyboard = activity.getSystemService(InputMethodManager::class.java)

        field.requestFocus()
        idle()
        assertTrue(field.hasFocus())
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
        // Красную рамку рисует контейнер поля, он берёт состояние у поля.
        assertTrue((field.parent as View).drawableState.contains(android.R.attr.state_activated))
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
        activity.screen("1-die-resized", R.id.tab_die)
    }
}
