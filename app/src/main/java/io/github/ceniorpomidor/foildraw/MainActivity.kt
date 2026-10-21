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
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import io.github.ceniorpomidor.foildraw.calc.FoilCalculator
import io.github.ceniorpomidor.foildraw.calc.FoilConsumption
import io.github.ceniorpomidor.foildraw.calc.MAKE_READY_SHARE
import io.github.ceniorpomidor.foildraw.calc.FoilPattern
import io.github.ceniorpomidor.foildraw.ui.FoilInputView
import io.github.ceniorpomidor.foildraw.ui.FoilSchemeView
import io.github.ceniorpomidor.foildraw.ui.Format

class MainActivity : Activity() {

    private val heightFields = mutableListOf<EditText>()
    private val gapFields = mutableListOf<EditText>()
    private lateinit var diagram: FoilInputView
    private lateinit var minGapField: EditText
    private lateinit var runField: EditText
    private lateinit var rollField: EditText
    private lateinit var prefs: SharedPreferences
    private var count = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("input", MODE_PRIVATE)
        setUpEdgeToEdge()

        diagram = find(R.id.diagram)
        buildDiagram()
        minGapField = find(R.id.min_gap)
        setUpNumberField(minGapField, getString(R.string.min_gap_description))
        runField = find(R.id.run)
        setUpNumberField(runField, getString(R.string.run_description), decimal = false)
        rollField = find(R.id.roll_length)
        setUpNumberField(rollField, getString(R.string.roll_description))
        text(R.id.usage_make_ready_title, getString(R.string.usage_make_ready_title, Format.percent(MAKE_READY_SHARE)))

        heightFields.forEachIndexed { i, field -> field.setText(prefs.getString("height$i", DEFAULT_HEIGHTS[i])) }
        gapFields.forEachIndexed { i, field -> field.setText(prefs.getString("gap$i", DEFAULT_GAPS[i])) }
        minGapField.setText(prefs.getString("minGap", DEFAULT_MIN_GAP))
        runField.setText(prefs.getString("run", ""))
        rollField.setText(prefs.getString("roll", DEFAULT_ROLL_LENGTH))
        count = prefs.getInt("count", 2).coerceIn(2, MAX_ELEMENTS)

        val countGroup = find<RadioGroup>(R.id.count_group)
        countGroup.check(COUNT_BUTTONS.getValue(count))
        countGroup.setOnCheckedChangeListener { _, id ->
            count = COUNT_BUTTONS.entries.first { it.value == id }.key
            prefs.edit().putInt("count", count).apply()
            recalculate()
        }

        heightFields.forEachIndexed { i, field -> field.saveOnChange("height$i") }
        gapFields.forEachIndexed { i, field -> field.saveOnChange("gap$i") }
        minGapField.saveOnChange("minGap")
        runField.saveOnChange("run")
        rollField.saveOnChange("roll")

        recalculate()
    }

    /** Поля схемы по порядку участков: элемент 1, промежуток 1, элемент 2, … */
    private fun buildDiagram() {
        for (i in 0 until MAX_ELEMENTS) {
            val height = numberField(getString(R.string.element_description, i + 1))
            heightFields += height
            diagram.addDimension(fieldBox(height), height, DEFAULT_HEIGHTS[i].toDouble())
            if (i < MAX_ELEMENTS - 1) {
                val gap = numberField(getString(R.string.gap_description, i + 1))
                gapFields += gap
                diagram.addDimension(fieldBox(gap), gap, DEFAULT_GAPS[i].toDouble())
            }
        }
    }

    private fun numberField(description: String) = EditText(this, null, 0, R.style.Field_Bare).apply {
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        setUpNumberField(this, description)
    }

    /** Рамка поля со «мм» внутри; фокус и ошибку поля рамка берёт себе через addStatesFromChildren. */
    private fun fieldBox(field: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_field)
        setAddStatesFromChildren(true)
        addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(TextView(context, null, 0, R.style.BoxUnit))
        setOnClickListener {
            field.requestFocus()
            getSystemService(InputMethodManager::class.java).showSoftInput(field, 0)
        }
    }

    private fun setUpNumberField(field: EditText, description: String, decimal: Boolean = true) {
        if (decimal) {
            // Цифровая клавиатура, но с запятой и точкой: на русской раскладке дробь пишут через запятую.
            field.keyListener = DigitsKeyListener.getInstance("0123456789.,")
            field.setRawInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        } else {
            field.keyListener = DigitsKeyListener.getInstance("0123456789")
            field.setRawInputType(InputType.TYPE_CLASS_NUMBER)
        }
        // Enter («Готово») сворачивает клавиатуру и снимает фокус с поля.
        field.imeOptions = EditorInfo.IME_ACTION_DONE
        field.setOnEditorActionListener { view, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
                true
            } else {
                false
            }
        }
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

    private fun recalculate() {
        val heights = heightFields.take(count).map { Format.parse(it.text) }
        val gaps = gapFields.take(count - 1).map { Format.parse(it.text) }
        val minGap = if (minGapField.text.isBlank()) 0.0 else Format.parse(minGapField.text)

        val heightsOk = heights.map { it != null && it >= MIN_HEIGHT && it <= MAX_SIZE }
        val gapsOk = gaps.map { it != null && it >= 0.0 && it <= MAX_SIZE }
        val minGapOk = minGap != null && minGap >= 0.0 && minGap <= MAX_MIN_GAP
        heightsOk.forEachIndexed { i, ok -> heightFields[i].markInvalid(!ok) }
        gapsOk.forEachIndexed { i, ok -> gapFields[i].markInvalid(!ok) }
        minGapField.markInvalid(!minGapOk)
        diagram.show(2 * count - 1, List(2 * count - 1) { if (it % 2 == 0) heights[it / 2] else gaps[it / 2] })

        val valid = heightsOk.all { it } && gapsOk.all { it } && minGapOk

        find<View>(R.id.invalid_input).visibility = if (valid) View.GONE else View.VISIBLE
        find<View>(R.id.result).visibility = if (valid) View.VISIBLE else View.GONE
        if (!valid) {
            renderUsage(null)
            return
        }

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
        find<FoilSchemeView>(R.id.scheme).show(pattern, result.pull)
        renderUsage(result.pull)
    }

    /** Ролики фольги на тираж и приладку при протяжке [pull]; null — протяжка не посчитана. */
    private fun renderUsage(pull: Double?) {
        val run = runField.text.toString().trim().toLongOrNull()
        val roll = Format.parse(rollField.text)
        val runOk = run != null && run in 1..MAX_RUN
        val rollOk = roll != null && roll >= 1.0 && roll <= MAX_ROLL_LENGTH
        runField.markInvalid(!runOk)
        rollField.markInvalid(!rollOk)
        val usage = if (pull != null && runOk && rollOk) {
            FoilConsumption(pull, run!!, roll!!)
        } else {
            null
        }

        val message = when {
            usage != null -> null
            pull == null -> getString(R.string.usage_no_pull)
            runField.text.isBlank() || rollField.text.isBlank() -> getString(R.string.usage_missing)
            else -> getString(R.string.usage_invalid)
        }
        find<TextView>(R.id.usage_missing).apply {
            visibility = if (message == null) View.GONE else View.VISIBLE
            text = message
        }
        find<View>(R.id.usage).visibility = if (usage == null) View.GONE else View.VISIBLE
        if (usage == null) return

        text(R.id.usage_run, getString(R.string.usage_length_rolls, Format.meters(usage.runLength), Format.rolls(usage.runRolls)))
        text(R.id.usage_make_ready, getString(R.string.usage_length_rolls, Format.meters(usage.makeReadyLength), Format.rolls(usage.makeReadyRolls)))
        text(R.id.usage_total, Format.plural(usage.rollsNeeded.toLong(), resources.getStringArray(R.array.roll_forms)))
        text(R.id.usage_total_hint, getString(R.string.usage_total_hint, Format.meters(usage.totalLength)))
    }

    /** Красная рамка у поля с недопустимым значением; пустое поле не подсвечиваем, пока его заполняют. */
    private fun EditText.markInvalid(invalid: Boolean) {
        isActivated = invalid && text.isNotBlank()
    }

    private fun text(id: Int, value: String) {
        find<TextView>(id).text = value
    }

    private fun <T : View> find(id: Int): T = checkNotNull(findViewById(id)) { "Нет view ${resources.getResourceEntryName(id)}" }

    /**
     * С targetSdk 35+ приложение рисуется под системными панелями. Сверху и по бокам
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
        const val MAX_ELEMENTS = 7
        const val MIN_HEIGHT = 0.5
        const val MAX_SIZE = 1000.0
        const val MAX_MIN_GAP = 100.0
        const val MAX_RUN = 9_999_999L
        const val MAX_ROLL_LENGTH = 10_000.0
        const val DEFAULT_ROLL_LENGTH = "120"
        const val DEFAULT_MIN_GAP = "1"
        val DEFAULT_HEIGHTS = listOf("10", "15", "10", "10", "10", "10", "10")
        val DEFAULT_GAPS = listOf("60", "40", "40", "40", "40", "40")
        val COUNT_BUTTONS = mapOf(
            2 to R.id.count_2, 3 to R.id.count_3, 4 to R.id.count_4,
            5 to R.id.count_5, 6 to R.id.count_6, 7 to R.id.count_7,
        )
    }
}
