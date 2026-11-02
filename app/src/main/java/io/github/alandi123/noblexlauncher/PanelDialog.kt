package io.github.alandi123.noblexlauncher

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Launcher-styled modal panel used for every menu, confirmation and form; replaces stock AlertDialog. */
class PanelDialog(private val context: Context) {
    private class Item(val title: String, val subtitle: String?, val onClick: () -> Unit)
    private class Action(val label: String, val onClick: () -> Unit)

    private var title: String? = null
    private var message: String? = null
    private var customView: View? = null
    private val items = ArrayList<Item>()
    private var positive: Action? = null
    private var neutral: Action? = null
    private var negative: Action? = Action("Cerrar") {}
    private var cancelable = true
    private var onDismiss: (() -> Unit)? = null
    private var widthDp = 640

    fun title(value: String) = apply { title = value }
    fun message(value: String) = apply { message = value }
    fun view(value: View) = apply { customView = value }
    fun item(title: String, subtitle: String? = null, onClick: () -> Unit) = apply { items += Item(title, subtitle, onClick) }
    fun positive(label: String, onClick: () -> Unit = {}) = apply { positive = Action(label, onClick) }
    fun neutral(label: String, onClick: () -> Unit = {}) = apply { neutral = Action(label, onClick) }
    fun negative(label: String, onClick: () -> Unit = {}) = apply { negative = Action(label, onClick) }
    fun noNegative() = apply { negative = null }
    fun cancelable(value: Boolean) = apply { cancelable = value }
    fun onDismiss(block: () -> Unit) = apply { onDismiss = block }
    fun width(dp: Int) = apply { widthDp = dp }

    fun create(): Dialog {
        val dialog = Dialog(context, R.style.PanelDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(cancelable)

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = context.getDrawable(R.drawable.dialog_background)
            setPadding(dp(32), dp(26), dp(32), dp(22))
        }
        title?.let {
            panel.addView(label(it, 26f, Color.WHITE, bold = true), LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(10)
            })
        }
        message?.let {
            panel.addView(label(it, 18f, Color.rgb(214, 226, 222)), LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(12)
            })
        }

        var firstFocus: View? = null
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        items.forEach { entry ->
            val row = itemRow(entry) { dialog.dismiss(); entry.onClick() }
            if (firstFocus == null) firstFocus = row
            body.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(8) })
        }
        customView?.let { body.addView(it, LinearLayout.LayoutParams(MATCH, WRAP)) }
        if (body.childCount > 0) {
            panel.addView(BoundedScrollView(context, dp(MAX_BODY_DP)).apply {
                isVerticalScrollBarEnabled = false
                clipToPadding = false
                addView(body)
            }, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        listOfNotNull(neutral, negative, positive).forEach { action ->
            val button = actionButton(action.label) { dialog.dismiss(); action.onClick() }
            button.id = View.generateViewId()
            if (action === positive) customView?.takeIf { it.isFocusable }?.nextFocusDownId = button.id
            if (action === positive && items.isEmpty() && customView == null) firstFocus = button
            else if (firstFocus == null && action === negative) firstFocus = button
            buttons.addView(button, LinearLayout.LayoutParams(WRAP, dp(48)).apply { marginStart = dp(12) })
        }
        if (buttons.childCount > 0) {
            panel.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
        }

        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(dp(widthDp), WindowManager.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.6f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
        onDismiss?.let { callback -> dialog.setOnDismissListener { callback() } }
        val entry = customView?.takeIf { it.isFocusable }
        dialog.setOnShowListener { (entry ?: firstFocus)?.requestFocus() }
        return dialog
    }

    fun show(): Dialog = create().also { it.show() }

    private fun itemRow(entry: Item, onClick: () -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            isFocusable = true
            isClickable = true
            background = context.getDrawable(R.drawable.panel_item)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            contentDescription = listOfNotNull(entry.title, entry.subtitle).joinToString(". ")
            setOnClickListener { onClick() }
        }
        val titleView = label(entry.title, 20f, Color.WHITE, bold = true)
        row.addView(titleView)
        val subtitleView = entry.subtitle?.let { label(it, 15f, Color.rgb(200, 213, 209)) }
        subtitleView?.let { row.addView(it) }
        row.setOnFocusChangeListener { _, focused ->
            titleView.setTextColor(if (focused) DARK else Color.WHITE)
            subtitleView?.setTextColor(if (focused) Color.rgb(48, 66, 64) else Color.rgb(200, 213, 209))
        }
        return row
    }

    private fun actionButton(text: String, onClick: () -> Unit) = Button(context).apply {
        this.text = text
        textSize = 17f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = context.getDrawable(R.drawable.focus_chip)
        minWidth = dp(120)
        setPadding(dp(22), 0, dp(22), 0)
        setOnClickListener { onClick() }
        setOnFocusChangeListener { view, focused -> (view as Button).setTextColor(if (focused) DARK else Color.WHITE) }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private class BoundedScrollView(context: Context, private val maxHeight: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
        }
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val MAX_BODY_DP = 280
        private val DARK = Color.rgb(24, 36, 37)
    }
}
