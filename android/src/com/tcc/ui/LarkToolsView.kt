package com.tcc.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.tcc.api.LarkClient

class LarkToolsView(context: Context) : FrameLayout(context) {

    companion object {
        private const val BG = 0xFF0A0A0B.toInt()
        private const val SURFACE = 0xFF141416.toInt()
        private const val SURFACE_ELEVATED = 0xFF1C1C1F.toInt()
        private const val ACCENT = 0xFF6C5CE7.toInt()
        private const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val TEXT_SECONDARY = 0xFF8B8B93.toInt()
        private const val TEXT_TERTIARY = 0xFF5E5E66.toInt()
    }

    var onClose: (() -> Unit)? = null

    private lateinit var outputText: TextView
    private lateinit var cmdInput: EditText

    init {
        setBackgroundColor(BG)
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // Top bar
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(16), dp(0), dp(16), dp(0))
        }
        val title = TextView(context).apply {
            text = "飞书 CLI"
            setTextColor(TEXT_PRIMARY)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(title)
        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(TEXT_SECONDARY)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onClose?.invoke() }
        }
        topBar.addView(closeBtn)
        rootLayout.addView(topBar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }

        // Auth status section
        content.addView(createSectionTitle("认证状态"))
        val statusRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(SURFACE_ELEVATED)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
        val checkBtn = Button(context).apply {
            text = "检查认证"
            setTextColor(TEXT_PRIMARY)
            textSize = 13f
            setBackgroundDrawable(createRoundedDrawable(ACCENT, dp(8)))
        }
        checkBtn.setOnClickListener {
            outputText.text = "检查中…"
            Thread {
                val result = LarkClient.authStatus()
                post { outputText.text = result }
            }.start()
        }
        statusRow.addView(checkBtn)
        content.addView(statusRow)

        // Quick actions
        content.addView(createSectionTitle("快捷操作"))
        val actions = listOf(
            "发送消息" to "chat send",
            "查看文档列表" to "doc list",
            "查看日历" to "calendar list"
        )
        for ((label, cmd) in actions) {
            val btn = Button(context).apply {
                text = label
                setTextColor(TEXT_PRIMARY)
                textSize = 14f
                gravity = Gravity.CENTER
                setBackgroundDrawable(createRoundedDrawable(SURFACE_ELEVATED, dp(8)))
                setPadding(dp(16), dp(12), dp(16), dp(12))
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
                setOnClickListener {
                    outputText.text = "执行中: lark-cli $cmd"
                    Thread {
                        val result = LarkClient.execute(*cmd.split(" ").toTypedArray())
                        post { outputText.text = result }
                    }.start()
                }
            }
            content.addView(btn)
        }

        // Custom command
        content.addView(createSectionTitle("自定义命令"))
        val cmdRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(8))
        }
        cmdInput = EditText(context).apply {
            hint = "lark-cli 参数…"
            setHintTextColor(TEXT_TERTIARY)
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            setBackgroundDrawable(createRoundedDrawable(SURFACE_ELEVATED, dp(8)))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) }
        }
        cmdRow.addView(cmdInput)
        val runBtn = Button(context).apply {
            text = "执行"
            setTextColor(TEXT_PRIMARY)
            setBackgroundDrawable(createRoundedDrawable(ACCENT, dp(8)))
            setOnClickListener {
                val args = cmdInput.text.toString().trim()
                if (args.isNotEmpty()) {
                    outputText.text = "执行中…"
                    Thread {
                        val result = LarkClient.execute(*args.split(" ").toTypedArray())
                        post { outputText.text = result }
                    }.start()
                }
            }
        }
        cmdRow.addView(runBtn)
        content.addView(cmdRow)

        // Output
        content.addView(createSectionTitle("输出"))
        outputText = TextView(context).apply {
            setTextColor(TEXT_PRIMARY)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setBackgroundColor(SURFACE_ELEVATED)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minHeight = dp(200)
            gravity = Gravity.TOP or Gravity.START
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            text = if (LarkClient.isAvailable()) "lark-cli 可用" else "lark-cli 未安装"
        }
        content.addView(outputText)

        scrollView.addView(content)
        rootLayout.addView(scrollView)
        addView(rootLayout)
    }

    private fun createSectionTitle(title: String): TextView {
        return TextView(context).apply {
            text = title.uppercase()
            setTextColor(TEXT_SECONDARY)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
                bottomMargin = dp(6)
                leftMargin = dp(4)
            }
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
                canvas.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), r, r, paint)
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    }
}
