package com.tcc.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tcc.TermuxBootstrap
import com.tcc.api.LarkClient
import java.io.File

// 系统状态视图
class SystemStatusView(context: Context) : FrameLayout(context) {

    companion object {
        private const val BG = 0xFF0A0A0B.toInt()
        private const val SURFACE = 0xFF141416.toInt()
        private const val SURFACE_ELEVATED = 0xFF1C1C1F.toInt()
        private const val ACCENT = 0xFFFF8C00.toInt()
        private const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val TEXT_SECONDARY = 0xFF8B8B93.toInt()
        private const val TEXT_TERTIARY = 0xFF5E5E66.toInt()
        private const val SUCCESS = 0xFF00C853.toInt()
        private const val ERROR = 0xFFFF5252.toInt()
    }

    var onClose: (() -> Unit)? = null

    private var nodeStatusText: TextView? = null
    private var nodeStatusDot: View? = null
    private var glibcStatusText: TextView? = null
    private var glibcStatusDot: View? = null
    private var larkStatusText: TextView? = null
    private var larkStatusDot: View? = null
    private var claudeStatusText: TextView? = null
    private var claudeStatusDot: View? = null
    private var storageFreeText: TextView? = null
    private var storageTotalText: TextView? = null
    private var larkAuthText: TextView? = null
    private var installButton: Button? = null

    init {
        setBackgroundColor(BG)
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // Top bar
        val topBar = createTopBar()
        rootLayout.addView(topBar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))

        // Scrollable content
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            isVerticalScrollBarEnabled = true
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }

        // Environment Detection Section
        contentLayout.addView(createSectionTitle("环境检测"))
        contentLayout.addView(createEnvironmentSection())

        // Storage Section
        contentLayout.addView(createSectionTitle("存储空间"))
        contentLayout.addView(createStorageSection())

        // lark-cli Auth Status Section
        contentLayout.addView(createSectionTitle("lark-cli 认证状态"))
        contentLayout.addView(createLarkAuthSection())

        // Refresh button
        contentLayout.addView(createRefreshButton())

        scrollView.addView(contentLayout)
        rootLayout.addView(scrollView)

        addView(rootLayout)
    }

    // 创建顶部栏
    private fun createTopBar(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(0), dp(16), dp(0))
            setBackgroundColor(SURFACE)

            val titleText = TextView(context).apply {
                text = "系统状态"
                setTextColor(TEXT_PRIMARY)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(titleText)

            val closeBtn = TextView(context).apply {
                text = "✕"
                setTextColor(TEXT_SECONDARY)
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                setOnClickListener {
                    onClose?.invoke()
                }
            }
            addView(closeBtn)
        }
    }

    // 创建分区标题
    private fun createSectionTitle(title: String): TextView {
        return TextView(context).apply {
            text = title.toUpperCase(java.util.Locale.US)
            setTextColor(TEXT_SECONDARY)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
                bottomMargin = dp(8)
                leftMargin = dp(4)
            }
        }
    }

    // 创建状态行（名称 + 圆点 + 文本）
    private fun createStatusRow(name: String, dot: View, statusText: TextView): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
            // Status dot
            addView(dot)
            // Name label
            addView(TextView(context).apply {
                text = name
                setTextColor(TEXT_PRIMARY)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = dp(10)
                    rightMargin = dp(8)
                }
            })
            // Status text (takes remaining space)
            addView(statusText)
        }
    }

    // 创建状态指示圆点
    private fun createStatusDot(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10))
            setBackgroundDrawable(createRoundedDrawable(TEXT_TERTIARY, dp(5)))
        }
    }

    // 创建环境检测区域
    private fun createEnvironmentSection(): View {
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundDrawable(createRoundedDrawable(SURFACE, dp(12)))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

        // Node.js
        nodeStatusDot = createStatusDot()
        nodeStatusText = createStatusLabel("检查中…")
        section.addView(createStatusRow("Node.js", nodeStatusDot!!, nodeStatusText!!))

        // glibc
        glibcStatusDot = createStatusDot()
        glibcStatusText = createStatusLabel("检查中…")
        section.addView(createStatusRow("glibc", glibcStatusDot!!, glibcStatusText!!))

        // lark-cli
        larkStatusDot = createStatusDot()
        larkStatusText = createStatusLabel("检查中…")
        section.addView(createStatusRow("lark-cli", larkStatusDot!!, larkStatusText!!))

        // Claude Code
        claudeStatusDot = createStatusDot()
        claudeStatusText = createStatusLabel("检查中…")
        section.addView(createStatusRow("Claude Code", claudeStatusDot!!, claudeStatusText!!))

        installButton = Button(context).apply {
            text = "安装可选 CLI 环境"
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            gravity = Gravity.CENTER
            setBackgroundDrawable(createRoundedDrawable(ACCENT, dp(10)))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
            setOnClickListener { installClaudeRuntime() }
        }
        section.addView(installButton)

        return section
    }

    // 创建状态标签
    private fun createStatusLabel(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(TEXT_TERTIARY)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.END
        }
    }

    // 创建存储空间区域
    private fun createStorageSection(): View {
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundDrawable(createRoundedDrawable(SURFACE, dp(12)))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

        // Free space
        val freeRow = createStorageRow("可用空间")
        storageFreeText = freeRow.second
        section.addView(freeRow.first)

        // Total space
        val totalRow = createStorageRow("总空间")
        storageTotalText = totalRow.second
        section.addView(totalRow.first)

        return section
    }

    // 创建存储信息行
    private fun createStorageRow(label: String): Pair<View, TextView> {
        val valueText = TextView(context).apply {
            text = "计算中…"
            setTextColor(TEXT_SECONDARY)
            textSize = 14f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(16)
            }
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
            addView(TextView(context).apply {
                text = label
                setTextColor(TEXT_PRIMARY)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            })
            addView(valueText)
        }

        return Pair(row, valueText)
    }

    // 创建 Lark 认证状态区域
    private fun createLarkAuthSection(): View {
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundDrawable(createRoundedDrawable(SURFACE, dp(12)))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

        larkAuthText = TextView(context).apply {
            text = "检查中…"
            setTextColor(TEXT_TERTIARY)
            textSize = 13f
            typeface = Typeface.MONOSPACE
        }
        section.addView(larkAuthText)

        return section
    }

    // 创建刷新按钮
    private fun createRefreshButton(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(24)
            }
        }

        val btn = Button(context).apply {
            text = "刷新"
            setTextColor(TEXT_PRIMARY)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundDrawable(createRoundedDrawable(ACCENT, dp(12)))
            setPadding(dp(32), dp(12), dp(32), dp(12))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                refresh()
            }
        }
        container.addView(btn)

        return container
    }

    // 刷新所有状态
    fun refresh() {
        checkEnvironment()
        checkStorage()
        checkLarkAuth()
    }

    private fun installClaudeRuntime() {
        installButton?.isEnabled = false
        installButton?.text = "准备安装 CLI…"
        Thread {
            val progress: (String) -> Unit = { msg ->
                postOnUi { installButton?.text = msg }
            }
            val baseOk = if (TermuxBootstrap.isInstalled(context)) {
                true
            } else {
                TermuxBootstrap.install(context, progress)
            }
            val ok = baseOk && TermuxBootstrap.installClaudeRuntime(context, progress)
            postOnUi {
                installButton?.isEnabled = true
                installButton?.text = if (ok) "CLI 环境已就绪" else "CLI 安装失败，可重试"
                refresh()
            }
        }.start()
    }

    // 附加到窗口时自动刷新
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refresh()
    }

    // 检查环境（Node.js/glibc/lark-cli/Claude Code）
    private fun checkEnvironment() {
        Thread {
            // Node.js
            checkCommand("node", "--version", nodeStatusDot, nodeStatusText)

            // glibc
            checkLibcFile(glibcStatusDot, glibcStatusText)

            // lark-cli
            checkLarkCliAvailable()

            // Claude Code
            checkCommand("claude", "--version", claudeStatusDot, claudeStatusText)
        }.start()
    }

    // 检查命令是否可用
    private fun checkCommand(command: String, arg: String, dot: View?, statusText: TextView?) {
        try {
            val prefix = TermuxBootstrap.getPrefixDir(context)
            val binFile = File(prefix, "bin/$command")
            val proc = if (command == "claude") {
                val claudeExe = File(prefix, "bin/claude.exe")
                val claudeScript = File(prefix, "bin/claude")
                val claudeFile = if (claudeExe.isFile) claudeExe else claudeScript
                if (!claudeFile.isFile) {
                    postOptionalStatus("HTTP模式可用", dot, statusText, true)
                    return
                }
                if (claudeExe.isFile) {
                    TermuxBootstrap.execGlibcBinary(context, claudeExe.absolutePath, arg)
                } else {
                    TermuxBootstrap.execBash(context, "${shellQuote(claudeFile.absolutePath)} $arg")
                }
            } else {
                if (!binFile.isFile) {
                    if (command == "node") {
                        postOptionalStatus("HTTP模式无需Node", dot, statusText)
                        return
                    }
                    postCommandMissing(command, dot, statusText)
                    return
                }
                TermuxBootstrap.execInTermux(context, binFile.absolutePath, arg)
            }
            val output = proc.inputStream.bufferedReader().readText().trim()
            val exitCode = proc.waitFor()
            val firstLine = output.lines().firstOrNull()?.take(60)?.ifEmpty { null }
            postOnUi {
                if (exitCode == 0) {
                    dot?.setBackgroundDrawable(createRoundedDrawable(SUCCESS, dp(5)))
                    statusText?.text = firstLine ?: "可用"
                    statusText?.setTextColor(SUCCESS)
                } else {
                    dot?.setBackgroundDrawable(createRoundedDrawable(ERROR, dp(5)))
                    statusText?.text = firstLine ?: "$command: exit $exitCode"
                    statusText?.setTextColor(ERROR)
                }
            }
        } catch (e: Exception) {
            postOnUi {
                dot?.setBackgroundDrawable(createRoundedDrawable(ERROR, dp(5)))
                statusText?.text = "${command}: 未找到"
                statusText?.setTextColor(ERROR)
            }
        }
    }

    // 检查 libc 库是否存在
    private fun postCommandMissing(command: String, dot: View?, statusText: TextView?) {
        postOnUi {
            dot?.setBackgroundDrawable(createRoundedDrawable(ERROR, dp(5)))
            statusText?.text = "${command}: 未找到"
            statusText?.setTextColor(ERROR)
        }
    }

    private fun postOptionalStatus(text: String, dot: View?, statusText: TextView?, successDot: Boolean = false) {
        postOnUi {
            dot?.setBackgroundDrawable(createRoundedDrawable(if (successDot) SUCCESS else TEXT_TERTIARY, dp(5)))
            statusText?.text = text
            statusText?.setTextColor(if (successDot) SUCCESS else TEXT_SECONDARY)
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun checkLibcFile(dot: View?, statusText: TextView?) {
        val prefix = TermuxBootstrap.getPrefixDir(context)
        val linker = File(prefix, "glibc/lib/ld-linux-aarch64.so.1")
        val libc = File(prefix, "glibc/lib/libc.so.6")
        val found = linker.isFile && libc.isFile
        postOnUi {
            if (found) {
                dot?.setBackgroundDrawable(createRoundedDrawable(SUCCESS, dp(5)))
                statusText?.text = "已安装"
                statusText?.setTextColor(SUCCESS)
            } else {
                dot?.setBackgroundDrawable(createRoundedDrawable(TEXT_TERTIARY, dp(5)))
                statusText?.text = "CLI可选"
                statusText?.setTextColor(TEXT_SECONDARY)
            }
        }
    }

    // 检查 lark-cli 是否可用
    private fun checkLarkCliAvailable() {
        val available = LarkClient.isAvailable(context)
        postOnUi {
            if (available) {
                larkStatusDot?.setBackgroundDrawable(createRoundedDrawable(SUCCESS, dp(5)))
                larkStatusText?.text = "可用"
                larkStatusText?.setTextColor(SUCCESS)
            } else {
                larkStatusDot?.setBackgroundDrawable(createRoundedDrawable(TEXT_TERTIARY, dp(5)))
                larkStatusText?.text = "可选未安装"
                larkStatusText?.setTextColor(TEXT_SECONDARY)
            }
        }
    }

    // 检查存储空间
    private fun checkStorage() {
        try {
            val dataDir = context.filesDir.parentFile ?: File("/")
            val freeBytes = dataDir.freeSpace
            val totalBytes = dataDir.totalSpace
            val freeGb = String.format("%.1f GB", freeBytes / (1024.0 * 1024.0 * 1024.0))
            val totalGb = String.format("%.1f GB", totalBytes / (1024.0 * 1024.0 * 1024.0))
            postOnUi {
                storageFreeText?.text = freeGb
                storageTotalText?.text = totalGb
            }
        } catch (e: Exception) {
            postOnUi {
                storageFreeText?.text = "获取失败"
                storageFreeText?.setTextColor(ERROR)
                storageTotalText?.text = "获取失败"
                storageTotalText?.setTextColor(ERROR)
            }
        }
    }

    // 检查 Lark 认证状态
    private fun checkLarkAuth() {
        Thread {
            if (!LarkClient.hasNode(context)) {
                postOnUi {
                    larkAuthText?.text = "当前使用 HTTP 对话模式；lark-cli 未安装，飞书工具暂不可用"
                    larkAuthText?.setTextColor(TEXT_SECONDARY)
                }
                return@Thread
            }
            if (!LarkClient.isInstalled(context)) {
                postOnUi {
                    larkAuthText?.text = "lark-cli 未安装，飞书工具暂不可用"
                    larkAuthText?.setTextColor(TEXT_SECONDARY)
                }
                return@Thread
            }
            val status = LarkClient.authStatus(context)
            postOnUi {
                larkAuthText?.text = status
                val isError = status.contains("Error", ignoreCase = true) ||
                        status.contains("not logged", ignoreCase = true) ||
                        status.contains("not authenticated", ignoreCase = true) ||
                        status.contains("login required", ignoreCase = true)
                larkAuthText?.setTextColor(if (isError) ERROR else SUCCESS)
            }
        }.start()
    }

    // 在主线程执行操作
    private fun postOnUi(action: () -> Unit) {
        android.os.Handler(context.mainLooper).post(action)
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
}
