package com.tcc.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.tcc.TermuxBootstrap
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

// Shell 终端视图
class ShellView(context: Context) : FrameLayout(context) {

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

    private lateinit var outputText: TextView
    private lateinit var cmdInput: EditText
    private lateinit var cwdText: TextView
    private lateinit var statusText: TextView
    private var currentDir: String
    private var termuxUsable = false

    // Termux environment paths
    private val termuxPrefix = "/data/data/com.termux/files/usr"
    private val termuxHome = "/data/data/com.termux/files/home"
    private val termuxBash = "$termuxPrefix/bin/bash"

    init {
        // Determine Termux availability
        val embedded = TermuxBootstrap.isInstalled(context)
        val systemTermux = java.io.File("/data/data/com.termux/files/usr/bin/bash").canExecute()
        termuxUsable = embedded || systemTermux
        currentDir = when {
            embedded -> TermuxBootstrap.getHomeDir(context).absolutePath
            systemTermux -> termuxHome
            else -> "/sdcard"
        }

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
            text = if (termuxUsable) "Termux Shell" else "Shell"
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

        // Status indicator
        statusText = TextView(context).apply {
            text = when {
                TermuxBootstrap.isInstalled(context) -> "Termux 环境就绪 (内置)"
                java.io.File("/data/data/com.termux/files/usr/bin/bash").canExecute() -> "Termux 已连接 (系统)"
                else -> "Termux 未安装 — 使用系统 sh"
            }
            setTextColor(if (termuxUsable) SUCCESS else TEXT_TERTIARY)
            textSize = 12f
            setBackgroundColor(SURFACE_ELEVATED)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        content.addView(statusText)

        // Current directory
        content.addView(TextView(context).apply {
            text = "工作目录"
            setTextColor(TEXT_SECONDARY)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4); leftMargin = dp(4) }
        })
        cwdText = TextView(context).apply {
            text = currentDir
            setTextColor(TEXT_TERTIARY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setBackgroundColor(SURFACE_ELEVATED)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
        content.addView(cwdText)

        // Quick dir buttons
        val dirRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
        val dirs = mutableListOf("/sdcard" to "SD卡")
        if (termuxUsable) {
            dirs.add(0, termuxHome to "~")
            dirs.add(1, "$termuxHome/mcc" to "mcc")
        }
        dirs.add("/" to "/")
        for ((path, label) in dirs) {
            dirRow.addView(Button(context).apply {
                text = label
                setTextColor(TEXT_PRIMARY)
                textSize = 12f
                setBackgroundDrawable(createRoundedDrawable(SURFACE_ELEVATED, dp(6)))
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(6) }
                setOnClickListener { cd(path) }
            })
        }
        content.addView(dirRow)

        // Quick actions
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        for ((label, cmd) in listOf(
            "ls" to "ls -lah",
            "pwd" to "pwd",
            "whoami" to "whoami",
            "uname" to "uname -a"
        )) {
            actionRow.addView(Button(context).apply {
                text = label
                setTextColor(TEXT_PRIMARY)
                textSize = 11f
                setBackgroundDrawable(createRoundedDrawable(SURFACE_ELEVATED, dp(6)))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(4) }
                setOnClickListener { runCommand(cmd) }
            })
        }
        content.addView(actionRow)

        // Command input
        content.addView(TextView(context).apply {
            text = "命令"
            setTextColor(TEXT_SECONDARY)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4); leftMargin = dp(4) }
        })
        val cmdRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        cmdInput = EditText(context).apply {
            hint = "输入命令…"
            setHintTextColor(TEXT_TERTIARY)
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            typeface = Typeface.MONOSPACE
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
                val cmd = cmdInput.text.toString().trim()
                if (cmd.isNotEmpty()) {
                    runCommand(cmd)
                    cmdInput.setText("")
                }
            }
        }
        cmdRow.addView(runBtn)
        content.addView(cmdRow)

        // Output
        content.addView(TextView(context).apply {
            text = "输出"
            setTextColor(TEXT_SECONDARY)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4); leftMargin = dp(4) }
        })
        outputText = TextView(context).apply {
            setTextColor(TEXT_PRIMARY)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setBackgroundColor(SURFACE_ELEVATED)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minHeight = dp(200)
            gravity = Gravity.TOP or Gravity.START
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            text = if (termuxUsable) "Termux Shell 就绪\n$termuxBash" else "系统 sh 就绪"
        }
        content.addView(outputText)

        scrollView.addView(content)
        rootLayout.addView(scrollView)
        addView(rootLayout)
    }

    // 切换工作目录
    private fun cd(path: String) {
        val dir = if (path.startsWith("/")) File(path) else File(currentDir, path)
        if (dir.isDirectory) {
            currentDir = dir.absolutePath
            cwdText.text = currentDir
            runCommand("ls -lah")
        } else {
            outputText.text = "不是目录: $path"
        }
    }

    // 执行 Shell 命令
    private fun runCommand(cmd: String) {
        outputText.text = "$ $cmd\n执行中…"
        Thread {
            try {
                val envMap: Map<String, String>
                val proc: Process

                if (TermuxBootstrap.isInstalled(context)) {
                    // 通过 linker + bash 执行（绕过 SELinux exec 限制）
                    val builtEnv = TermuxBootstrap.buildEnvironment(context)
                    proc = TermuxBootstrap.execBash(context, "cd \"$currentDir\" && $cmd", builtEnv)
                    envMap = builtEnv
                } else if (termuxUsable) {
                    // 系统 Termux（另一个 app），可以直接 exec
                    val env = mapOf(
                        "HOME" to termuxHome,
                        "PREFIX" to termuxPrefix,
                        "PATH" to "$termuxPrefix/bin:$termuxPrefix/bin/applets:/system/bin:/system/xbin",
                        "LD_LIBRARY_PATH" to "$termuxPrefix/lib",
                        "TMPDIR" to "$termuxPrefix/tmp",
                        "TERM" to "xterm-256color",
                        "LANG" to "en_US.UTF-8"
                    )
                    val pb = ProcessBuilder(termuxBash, "-c", "cd \"$currentDir\" && $cmd")
                    pb.environment().putAll(env)
                    pb.directory(File(currentDir))
                    proc = pb.start()
                    envMap = env
                } else {
                    proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cd \"$currentDir\" && $cmd"))
                    envMap = emptyMap()
                }

                val stdout = BufferedReader(InputStreamReader(proc.inputStream)).readText()
                val stderr = try { BufferedReader(InputStreamReader(proc.errorStream)).readText() } catch (_: Exception) { "" }
                val exitCode = proc.waitFor()

                val result = buildString {
                    append("$ $cmd\n")
                    if (stdout.isNotBlank()) append(stdout.trim())
                    if (stderr.isNotBlank()) {
                        if (isNotEmpty() && !stdout.isNotBlank()) append("\n")
                        if (stdout.isNotBlank()) append("\n")
                        append(stderr.trim())
                    }
                    append("\n[exit: $exitCode]")
                }
                post { outputText.text = result }
            } catch (e: Exception) {
                post { outputText.text = "$ $cmd\n错误: ${e.message}" }
            }
        }.start()
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
