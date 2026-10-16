package io.github.ceniorpomidor.foildraw

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import io.github.ceniorpomidor.foildraw.calc.FoilCalculator
import io.github.ceniorpomidor.foildraw.calc.FoilPattern
import io.github.ceniorpomidor.foildraw.ui.FoilInputSegmentView
import io.github.ceniorpomidor.foildraw.ui.FoilInputSegmentView.Kind
import io.github.ceniorpomidor.foildraw.ui.FoilSchemeView
import io.github.ceniorpomidor.foildraw.ui.Format

class MainActivity : Activity() {

    private val heightFields = mutableListOf<EditText>()
    private val gapFields = mutableListOf<EditText>()
    private val elementRows = mutableListOf<View>()
    private val gapRows = mutableListOf<View>()
    private lateinit var minGapField: EditText
    private lateinit var prefs: SharedPreferences
    private var count = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("input", MODE_PRIVATE)
        setUpEdgeToEdge()

        buildDiagram(find(R.id.diagram))
        minGapField = find(R.id.min_gap)
        setUpNumberField(minGapField, getString(R.string.min_gap_description))

        heightFields.forEachIndexed { i, field -> field.setText(prefs.getString("height$i", DEFAULT_HEIGHTS[i])) }
        gapFields.forEachIndexed { i, field -> field.setText(prefs.getString("gap$i", DEFAULT_GAPS[i])) }
        minGapField.setText(prefs.getString("minGap", DEFAULT_MIN_GAP))
        count = prefs.getInt("count", 2).coerceIn(2, MAX_ELEMENTS)

        val countGroup = find<RadioGroup>(R.id.count_group)
        countGroup.check(COUNT_BUTTONS.getValue(count))
        countGroup.setOnCheckedChangeListener { _, id ->
            count = COUNT_BUTTONS.entries.first { it.value == id }.key
            prefs.edit().putInt("count", count).apply()
            updateRows()
            recalculate()
        }

        heightFields.forEachIndexed { i, field -> field.saveOnChange("height$i") }
        gapFields.forEachIndexed { i, field -> field.saveOnChange("gap$i") }
        minGapField.saveOnChange("minGap")

        updateRows()
        recalculate()
    }

    private fun buildDiagram(container: LinearLayout) {
        container.addView(row(Kind.LEAD_IN, null, R.dimen.row_lead))
        for (i in 0 until MAX_ELEMENTS) {
            val height = numberField(getString(R.string.element_description, i + 1))
            heightFields += height
            elementRows += row(Kind.ELEMENT, height, R.dimen.row_element).also(container::addView)
            if (i < MAX_ELEMENTS - 1) {
                val gap = numberField(getString(R.string.gap_description, i + 1))
                gapFields += gap
                gapRows += row(Kind.GAP, gap, R.dimen.row_gap).also(container::addView)
            }
        }
        container.addView(row(Kind.LEAD_OUT, null, R.dimen.row_lead))
    }

    /** Строка схемы: участок фольги слева и поле ввода размера справа. */
    private fun row(kind: Kind, field: EditText?, heightRes: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(heightRes))
        }
        row.addView(FoilInputSegmentView(this, kind), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val fieldWidth = resources.getDimensionPixelSize(R.dimen.field_width)
        val unit = TextView(this, null, 0, R.style.Unit)
        unit.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val unitMargin = resources.getDimensionPixelSize(R.dimen.unit_margin)
        val fieldMargin = resources.getDimensionPixelSize(R.dimen.field_margin)
        if (field == null) {
            // Пустое место той же ширины, чтобы линии фольги шли без изломов.
            row.addView(View(this), LinearLayout.LayoutParams(fieldMargin + fieldWidth + unitMargin + unit.measuredWidth, 0))
        } else {
            row.addView(field, LinearLayout.LayoutParams(fieldWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = fieldMargin })
            row.addView(unit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = unitMargin })
        }
        return row
    }

    private fun numberField(description: String) = EditText(this, null, 0, R.style.Field).apply {
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        imeOptions = EditorInfo.IME_ACTION_NEXT
        setUpNumberField(this, description)
    }

    private fun setUpNumberField(field: EditText, description: String) {
        // Цифровая клавиатура, но с запятой и точкой: на русской раскладке дробь пишут через запятую.
        field.keyListener = DigitsKeyListener.getInstance("0123456789.,")
        field.setRawInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        // Видимых подписей у полей нет; contentDescription заглушил бы для TalkBack введённое значение.
        field.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.hintText = description
            }
        }
    }

    private fun EditText.saveOnChange(key: String) = addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable) {
            prefs.edit().putString(key, s.toString()).apply()
            recalculate()
        }
    })

    private fun updateRows() {
        elementRows.forEachIndexed { i, row -> row.visibility = if (i < count) View.VISIBLE else View.GONE }
        gapRows.forEachIndexed { i, row -> row.visibility = if (i < count - 1) View.VISIBLE else View.GONE }
    }

    private fun recalculate() {
        val heights = heightFields.take(count).map { Format.parse(it.text) }
        val gaps = gapFields.take(count - 1).map { Format.parse(it.text) }
        val minGap = if (minGapField.text.isBlank()) 0.0 else Format.parse(minGapField.text)

        val valid = heights.all { it != null && it >= MIN_HEIGHT && it <= MAX_SIZE } &&
            gaps.all { it != null && it >= 0.0 && it <= MAX_SIZE } &&
            minGap != null && minGap >= 0.0 && minGap <= MAX_MIN_GAP

        find<View>(R.id.invalid_input).visibility = if (valid) View.GONE else View.VISIBLE
        find<View>(R.id.result).visibility = if (valid) View.VISIBLE else View.GONE
        if (!valid) return

        val pattern = FoilPattern(heights.map { it!! }, gaps.map { it!! })
        val result = FoilCalculator.calculate(pattern, minGap!!)

        text(R.id.pull_value, getString(R.string.value_mm, Format.mm(result.pull)))
        text(
            R.id.pull_window,
            if (result.isNested) getString(R.string.pull_window, Format.mm(result.minPull), Format.mm(result.maxPull))
            else getString(R.string.pull_not_nested),
        )
        text(R.id.clearance_value, getString(R.string.value_mm, Format.mm(result.clearance)))
        text(R.id.savings_value, getString(R.string.savings_value, Format.percent(result.savings)))
        text(R.id.savings_hint, getString(R.string.savings_hint, Format.mm(result.plainPull)))
        // Протяжка в мм на удар — это ровно метры на тысячу ударов.
        text(R.id.consumption_value, getString(R.string.consumption_value, Format.meters(result.pull)))
        find<FoilSchemeView>(R.id.scheme).show(pattern, result.pull)
    }

    private fun text(id: Int, value: String) {
        find<TextView>(id).text = value
    }

    private fun <T : View> find(id: Int): T = checkNotNull(findViewById(id)) { "Нет view ${resources.getResourceEntryName(id)}" }

    /**
     * С targetSdk 35 приложение рисуется под системными панелями. Сверху и по бокам
     * отступаем на их размер, снизу даём контенту прокручиваться под панелью навигации,
     * а при открытой клавиатуре уменьшаем окно прокрутки, чтобы поле ввода оставалось видно.
     */
    @Suppress("DEPRECATION") // Цвета панелей нужны на Android 11–14, на 15+ они и так прозрачные.
    private fun setUpEdgeToEdge() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false

        val root = find<View>(R.id.root)
        val scroll = find<ScrollView>(R.id.scroll)
        val basePaddingBottom = scroll.paddingBottom
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            root.setPadding(bars.left, bars.top, bars.right, 0)
            scroll.setPadding(0, 0, 0, basePaddingBottom + if (ime.bottom > 0) 0 else bars.bottom)
            val params = scroll.layoutParams as ViewGroup.MarginLayoutParams
            if (params.bottomMargin != ime.bottom) {
                params.bottomMargin = ime.bottom
                scroll.layoutParams = params
            }
            WindowInsets.CONSUMED
        }
    }

    private companion object {
        const val MAX_ELEMENTS = 4
        const val MIN_HEIGHT = 0.5
        const val MAX_SIZE = 1000.0
        const val MAX_MIN_GAP = 100.0
        const val DEFAULT_MIN_GAP = "1"
        val DEFAULT_HEIGHTS = listOf("10", "15", "10", "10")
        val DEFAULT_GAPS = listOf("60", "40", "40")
        val COUNT_BUTTONS = mapOf(2 to R.id.count_2, 3 to R.id.count_3, 4 to R.id.count_4)
    }
}
