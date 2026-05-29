package com.tcc.ui
import android.view.ViewGroup.LayoutParams

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListPopupWindow
import android.widget.TextView
import android.graphics.Color

class MessageInputView(context: Context) : FrameLayout(context) {

    companion object {
        private const val SURFACE = 0xFF141416.toInt()
        private const val ACCENT = 0xFF6C5CE7.toInt()
        private const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val TEXT_TERTIARY = 0xFF5E5E66.toInt()
        private const val BORDER = 0xFF2A2A2E.toInt()
        private const val SURFACE_ELEVATED = 0xFF1C1C1F.toInt()
    }

    private val editText = EditText(context)
    private val sendButton = Button(context)
    private var onSendListener: ((String) -> Unit)? = null
    private var onCommandListener: ((String, String) -> Unit)? = null
    private var commandPopup: ListPopupWindow? = null

    private val commands = listOf(
        "/context" to "显示上下文信息",
        "/compact" to "收起之前的对话",
        "/clear" to "清除当前对话",
        "/help" to "显示帮助信息",
        "/model" to "切换模型",
        "/temperature" to "设置温度参数"
    )

    init {
        setBackgroundColor(SURFACE)

        // Top border line
        val borderLine = View(context).apply {
            setBackgroundColor(BORDER)
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply {
                gravity = Gravity.TOP
            }
        }
        addView(borderLine)

        // Send button (right-aligned, circle)
        sendButton.apply {
            text = "→"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundDrawable(createCircleDrawable(ACCENT))
            isEnabled = false

            layoutParams = FrameLayout.LayoutParams(dp(48), dp(48)).apply {
                gravity = Gravity.END or Gravity.BOTTOM
                rightMargin = dp(4)
                bottomMargin = dp(8)
            }

            setOnClickListener {
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    if (text.startsWith("/")) {
                        val spaceIndex = text.indexOf(' ')
                        val cmd = if (spaceIndex == -1) text else text.substring(0, spaceIndex)
                        val args = if (spaceIndex == -1) null else text.substring(spaceIndex + 1).trim()
                        onCommandListener?.invoke(cmd, args ?: "")
                    } else {
                        onSendListener?.invoke(text)
                        editText.setText("")
                    }
                }
            }
        }
        addView(sendButton)

        // EditText
        editText.apply {
            hint = "输入消息…"
            setHintTextColor(TEXT_TERTIARY)
            setTextColor(TEXT_PRIMARY)
            textSize = 15f
            maxLines = 6
            isVerticalScrollBarEnabled = true
            gravity = Gravity.START or Gravity.TOP
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(12), dp(12), dp(56), dp(12))
            setLineSpacing(4f, 1.3f)

            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            }

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val text = s?.toString() ?: ""
                    sendButton.isEnabled = text.isNotBlank()
                    if (text.startsWith("/")) {
                        showCommandPopup()
                    } else {
                        commandPopup?.dismiss()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                    if (event.isShiftPressed) {
                        // Shift+Enter: new line
                        val cursor = selectionStart
                        text?.insert(cursor, "\n")
                        true
                    } else {
                        // Enter: send
                        val text = text.toString().trim()
                        if (text.isNotEmpty()) {
                            if (text.startsWith("/")) {
                                val spaceIndex = text.indexOf(' ')
                                val cmd = if (spaceIndex == -1) text else text.substring(0, spaceIndex)
                                val args = if (spaceIndex == -1) null else text.substring(spaceIndex + 1).trim()
                                onCommandListener?.invoke(cmd, args ?: "")
                            } else {
                                onSendListener?.invoke(text)
                                setText("")
                            }
                        }
                        true
                    }
                } else {
                    false
                }
            }
        }
        addView(editText)
    }

    fun setOnSendListener(callback: (String) -> Unit) {
        onSendListener = callback
    }

    fun setOnCommandListener(callback: (String, String) -> Unit) {
        onCommandListener = callback
    }

    override fun setEnabled(enabled: Boolean) {
        editText.isEnabled = enabled
        editText.isFocusable = enabled
        editText.isFocusableInTouchMode = enabled
        sendButton.isEnabled = enabled && editText.text.toString().trim().isNotEmpty()
        if (!enabled) {
            // Replace send with stop style
            sendButton.text = "■"
        } else {
            sendButton.text = "→"
        }
    }

    fun setText(text: String) {
        editText.setText(text)
        editText.setSelection(text.length)
    }

    fun getText(): String = editText.text.toString()

    fun applyFontSize(size: Int) {
        editText.textSize = size.toFloat()
    }

    private fun showCommandPopup() {
        if (commandPopup == null) {
            commandPopup = ListPopupWindow(context).apply {
                setAdapter(object : ArrayAdapter<String>(
                    context,
                    android.R.layout.simple_list_item_1,
                    commands.map { "${it.first}  -  ${it.second}" }.toTypedArray()
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        view.setTextColor(TEXT_PRIMARY)
                        view.textSize = 14f
                        view.setPadding(dp(16), dp(12), dp(16), dp(12))
                        view.setBackgroundColor(SURFACE_ELEVATED)
                        view.typeface = Typeface.MONOSPACE
                        return view
                    }
                })
                anchorView = this@MessageInputView
                width = dp(280)
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                isModal = false
                setBackgroundDrawable(createRoundedDrawable(SURFACE_ELEVATED, dp(8)))

                setOnItemClickListener(AdapterView.OnItemClickListener { _, _, position, _ ->
                    val cmd = commands[position].first
                    editText.setText("$cmd ")
                    editText.setSelection(editText.text.length)
                    dismiss()
                })
            }
        }

        if (commandPopup?.isShowing == false) {
            try {
                commandPopup?.show()
            } catch (e: Exception) {
                // Window not ready
            }
        }
    }

    private fun createCircleDrawable(color: Int): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = android.graphics.Paint.Style.FILL
            }

            override fun draw(canvas: android.graphics.Canvas) {
                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val radius = minOf(bounds.width(), bounds.height()) / 2f
                canvas.drawCircle(cx, cy, radius, paint)
            }

            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
    }

    private fun createRoundedDrawable(color: Int, radius: Int): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = android.graphics.Paint.Style.FILL
            }

            override fun draw(canvas: android.graphics.Canvas) {
                val r = radius.toFloat()
                canvas.drawRoundRect(
                    bounds.left.toFloat(), bounds.top.toFloat(),
                    bounds.right.toFloat(), bounds.bottom.toFloat(),
                    r, r, paint
                )
            }

            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        commandPopup?.dismiss()
    }
}
