package com.tcc.ui

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tcc.data.ConfigManager
import com.tcc.data.PromptManager
import com.tcc.data.PromptTemplate
import com.tcc.data.SkillManager
import com.tcc.model.Provider

// 设置视图 - 多级菜单
class SettingsView(context: Context) : LinearLayout(context) {

    private val config = ConfigManager.getInstance(context)
    private val promptMgr = PromptManager(context)
    private val skillMgr = SkillManager(context)

    companion object {
        private const val BG = 0xFF0A0A0B.toInt()
        private const val SURFACE = 0xFF141416.toInt()
        private const val ELEVATED = 0xFF1C1C1F.toInt()
        private const val ACCENT = 0xFFFF8C00.toInt()
        private const val TXT_PRI = 0xFFFFFFFF.toInt()
        private const val TXT_SEC = 0xFF8B8B93.toInt()
        private const val TXT_TERTIARY = 0xFF5E5E66.toInt()
    }

    var onClose: (() -> Unit)? = null
    private val rootStack = mutableListOf<View>()  // 导航栈

    init {
        orientation = VERTICAL
        setBackgroundColor(BG)
        showMainMenu()
    }

    private fun showMainMenu() {
        removeAllViews(); rootStack.clear()
        addView(topBar("设置"))
        val scroll = ScrollView(context).apply { layoutParams = LayoutParams(-1, -1) }
        val content = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }

        content.addView(menuCard("供应商与模型") { showProviders() })
        content.addView(menuCard("提示词") { showPrompts() })
        content.addView(menuCard("Skills") { showSkills() })
        content.addView(menuCard("外观") { showAppearance() })
        content.addView(menuCard("WebDAV 备份") { showWebDav() })
        content.addView(menuCard("调试日志") { showDebugLog() })

        // 使用统计
        val stats = config.getUsageStats()
        if (stats.isNotEmpty()) {
            content.addView(sectionTitle("使用统计"))
            for (s in stats) {
                content.addView(textLine("${s.providerId.take(8)} / ${s.modelName}: \$${"%.4f".format(s.totalCostUsd)} (${s.totalInputTokens + s.totalOutputTokens} tokens)"))
            }
        }
        scroll.addView(content); addView(scroll)
    }

    private fun pushPage(title: String, page: View) {
        rootStack.add(getChildAt(childCount - 1))  // save current content
        removeViews(1, childCount - 1)  // keep topbar
        addView(page, LayoutParams(-1, -1))
        // Update topbar title
        (getChildAt(0) as? ViewGroup)?.let { tb ->
            val titleView = tb.findViewWithTag<TextView>("settings_title")
            titleView?.text = title
            val backBtn = tb.findViewWithTag<View>("settings_back")
            backBtn?.visibility = VISIBLE
        }
    }

    private fun popPage() {
        if (rootStack.isEmpty()) { onClose?.invoke(); return }
        removeViews(1, childCount - 1)
        addView(rootStack.removeAt(rootStack.size - 1), LayoutParams(-1, -1))
        (getChildAt(0) as? ViewGroup)?.let { tb ->
            val titleView = tb.findViewWithTag<TextView>("settings_title")
            titleView?.text = "设置"
            val backBtn = tb.findViewWithTag<View>("settings_back")
            backBtn?.visibility = if (rootStack.isEmpty()) GONE else VISIBLE
        }
    }

    private fun topBar(title: String): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(SURFACE); setPadding(dp(16), dp(0), dp(16), dp(0))
        layoutParams = LayoutParams(-1, dp(56))

        val backBtn = TextView(context).apply {
            text = "←"; setTextColor(ACCENT); textSize = 20f; visibility = GONE
            tag = "settings_back"
            setOnClickListener { popPage() }
            layoutParams = LayoutParams(dp(40), -1); gravity = Gravity.CENTER
        }
        addView(backBtn)

        addView(TextView(context).apply {
            text = title; setTextColor(TXT_PRI); textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            tag = "settings_title"
            layoutParams = LayoutParams(0, -2, 1f)
        })

        addView(TextView(context).apply {
            text = "✕"; setTextColor(TXT_SEC); textSize = 20f; gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8)); layoutParams = LayoutParams(dp(40), dp(40))
            setOnClickListener { onClose?.invoke() }
        })
    }

    // ---- Menu Cards ----
    private fun menuCard(title: String, onClick: () -> Unit): View = cardLayout {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
        addView(TextView(context).apply {
            text = title; setTextColor(TXT_PRI); textSize = 16f
            layoutParams = LayoutParams(0, -2, 1f)
        })
        addView(TextView(context).apply {
            text = "›"; setTextColor(TXT_TERTIARY); textSize = 24f
        })
    }

    // ---- Providers ----
    private fun showProviders() {
        val content = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }
        val activeId = config.getActiveProvider()?.id
        for (p in config.getProviders()) {
            val isActive = p.id == activeId
            val slot = p.models[config.getActiveModelKey()]
            content.addView(cardLayout {
                setOnClickListener { editProvider(p.id) }
                addView(TextView(context).apply {
                    text = if (isActive) "● ${p.name}" else "○ ${p.name}"
                    setTextColor(if (isActive) ACCENT else TXT_PRI); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(context).apply {
                    text = "活跃模型: ${slot?.displayName ?: "未设置"}"; setTextColor(TXT_SEC); textSize = 13f
                })
                if (!isActive) {
                    addView(button("设为活跃") {
                        config.switchProvider(p.id)
                        showToast("已切换到 ${p.name}")
                        showProviders()
                    })
                }
            })
        }
        content.addView(buttonBar {
            addView(button("+ 添加供应商") { addProvider() })
            addView(button("+ 添加预设") { addPreset() })
        })
        pushPage("供应商与模型", ScrollView(context).apply { addView(content) })
    }

    private fun editProvider(id: String) {
        val p = config.getProviders().find { it.id == id } ?: return
        val content = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }

        content.addView(fieldLabel("名称"))
        val nameInput = editText(p.name)
        content.addView(nameInput)

        content.addView(sectionDivider())

        // 环境变量编辑
        content.addView(sectionTitle("环境变量"))
        val envViews = mutableMapOf<String, android.widget.EditText>()
        for ((key, value) in p.env) {
            val label = when (key) {
                "ANTHROPIC_BASE_URL" -> "接口地址"
                "ANTHROPIC_AUTH_TOKEN" -> "API Key"
                "ANTHROPIC_API_KEY" -> "API Key"
                "ANTHROPIC_MODEL" -> "默认模型"
                "ANTHROPIC_DEFAULT_OPUS_MODEL" -> "Opus 模型"
                "ANTHROPIC_DEFAULT_SONNET_MODEL" -> "Sonnet 模型"
                "ANTHROPIC_DEFAULT_HAIKU_MODEL" -> "Haiku 模型"
                "CLAUDE_CODE_SUBAGENT_MODEL" -> "代理模型"
                "CLAUDE_CODE_EFFORT_LEVEL" -> "推理强度"
                else -> key
            }
            content.addView(fieldLabel(label))
            val et = editText(value)
            if (key == "ANTHROPIC_AUTH_TOKEN" || key == "ANTHROPIC_API_KEY") {
                et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            if (key == "ANTHROPIC_BASE_URL") {
                et.hint = "https://api.deepseek.com/anthropic"
            }
            envViews[key] = et
            content.addView(et)
        }

        content.addView(sectionDivider())

        // 显示当前状态
        val isActive = config.getActiveProvider()?.id == id
        if (isActive) {
            content.addView(textLine("● 当前活跃供应商"))
        }

        content.addView(buttonBar {
            addView(button("保存") {
                val updated = p.copy(
                    name = nameInput.text.toString(),
                    env = envViews.entries.associate { it.key to it.value.text.toString() }.toMutableMap()
                )
                config.saveProvider(updated)
                if (!isActive) config.switchProvider(updated.id)
                showToast("已保存")
                popPage(); showProviders()
            })
            if (!isActive) {
                addView(button("设为活跃") {
                    config.switchProvider(id)
                    showToast("已切换到 ${p.name}")
                    popPage(); showProviders()
                })
            }
            addView(button("删除", 0xFFFF5252.toInt()) {
                config.deleteProvider(id)
                showToast("已删除"); popPage(); showProviders()
            })
        })
        pushPage(p.name, ScrollView(context).apply { addView(content) })
    }

    private fun addProvider() {
        val p = Provider(name = "新供应商")
        config.saveProvider(p)
        editProvider(p.id)
    }

    private fun addPreset() {
        val content = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }
        for (preset in listOf(Provider.mimoPreset(), Provider.deepSeekPreset(), Provider.anthropicPreset())) {
            content.addView(cardLayout {
                setOnClickListener {
                    config.saveProvider(preset)
                    config.switchProvider(preset.id)
                    showToast("已添加 ${preset.name}")
                    popPage(); showProviders()
                }
                addView(TextView(context).apply {
                    text = preset.name; setTextColor(TXT_PRI); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
                })
                val slot = preset.models["ANTHROPIC_MODEL"]
                addView(TextView(context).apply {
                    text = "模型: ${slot?.model ?: ""}"; setTextColor(TXT_SEC); textSize = 13f
                })
            })
        }
        pushPage("选择预设", ScrollView(context).apply { addView(content) })
    }

    // ---- Prompts ----
    private fun showPrompts() {
        val content = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }

        content.addView(sectionTitle("全局提示词"))
        content.addView(textLine("当前: ${config.getSystemPrompt().take(50)}"))
        val et = android.widget.EditText(context).apply {
            setText(config.getSystemPrompt())
            setTextColor(TXT_PRI); setHintTextColor(TXT_TERTIARY); textSize = 14f
            gravity = Gravity.START or Gravity.TOP; minHeight = dp(80)
            setBackgroundColor(ELEVATED); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        content.addView(et)
        content.addView(button("保存系统提示词") {
            config.setSystemPrompt(et.text.toString())
            showToast("已保存")
        })

        for (t in promptMgr.getGlobalTemplates()) {
            content.addView(cardLayout {
                setOnClickListener {
                    config.setSystemPrompt(t.content)
                    et.setText(t.content)
                    showToast("已加载: ${t.name}")
                }
                addView(TextView(context).apply {
                    text = t.name; setTextColor(TXT_PRI); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                })
            })
        }

        content.addView(sectionTitle("项目提示词"))
        for (t in promptMgr.getProjectTemplates()) {
            content.addView(cardLayout {
                setOnClickListener {
                    config.setSystemPrompt(t.content); et.setText(t.content)
                    showToast("已加载: ${t.name}")
                }
                addView(TextView(context).apply {
                    text = t.name; setTextColor(TXT_PRI); textSize = 14f
                })
            })
        }

        pushPage("提示词", ScrollView(context).apply { addView(content) })
    }

    // ---- Skills ----
    private fun showSkills() {
        val content = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }
        for (s in skillMgr.getSkills()) {
            content.addView(cardLayout {
                setOnClickListener {
                    skillMgr.setSkillEnabled(s.name, !s.enabled)
                    showSkills()
                }
                addView(TextView(context).apply {
                    text = if (s.enabled) "[✓] ${s.name}" else "[ ] ${s.name}"
                    setTextColor(if (s.enabled) TXT_PRI else TXT_TERTIARY); textSize = 15f
                })
            })
        }
        content.addView(button("管理 MCP 服务器") { showMcp() })
        pushPage("Skills", ScrollView(context).apply { addView(content) })
    }

    private fun showMcp() {
        val content = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }
        for (m in skillMgr.getMcpServers()) {
            content.addView(cardLayout {
                setOnClickListener {
                    skillMgr.deleteMcpServer(m.name)
                    showMcp()
                }
                addView(TextView(context).apply {
                    text = "${m.name} [${m.command}]"; setTextColor(TXT_PRI); textSize = 14f
                })
            })
        }
        pushPage("MCP 服务器", ScrollView(context).apply { addView(content) })
    }

    // ---- Appearance ----
    private fun showAppearance() {
        val content = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }

        content.addView(fieldLabel("字体大小"))
        val seekBar = android.widget.SeekBar(context).apply {
            max = 8; progress = config.getFontSize() - 12
            layoutParams = LayoutParams(-1, -2)
        }
        content.addView(seekBar)
        content.addView(button("保存") {
            config.setFontSize(12 + seekBar.progress)
            showToast("已保存")
        })
        pushPage("外观", ScrollView(context).apply { addView(content) })
    }

    // ---- WebDAV ----
    private fun showWebDav() {
        val content = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }
        val urlInput = editText(config.getWebDavUrl()).apply { hint = "https://dav.jianguoyun.com/dav/" }
        val userInput = editText(config.getWebDavUser()).apply { hint = "用户名" }
        val passInput = editText(config.getWebDavPass()).apply { hint = "密码"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        content.addView(fieldLabel("服务器地址")); content.addView(urlInput)
        content.addView(fieldLabel("用户名")); content.addView(userInput)
        content.addView(fieldLabel("密码")); content.addView(passInput)
        content.addView(button("保存") {
            config.setWebDavUrl(urlInput.text.toString())
            config.setWebDavUser(userInput.text.toString())
            config.setWebDavPass(passInput.text.toString())
            showToast("已保存")
        })
        pushPage("WebDAV 备份", ScrollView(context).apply { addView(content) })
    }

    // ---- Debug Log ----
    private fun showDebugLog() {
        val content = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }

        val logText = TextView(context).apply {
            setTextColor(TXT_PRI); textSize = 11f; typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(ELEVATED)
            setTextIsSelectable(true)
            layoutParams = LayoutParams(-1, -1)
        }

        val refreshBtn = button("刷新日志") {
            val log = com.tcc.api.ClaudeCli.getLog()
            logText.text = if (log.isEmpty()) "暂无日志" else log
        }

        val exportBtn = button("导出日志") {
            try {
                val src = com.tcc.api.ClaudeCli.getLogFile()
                if (src == null || !src.exists()) {
                    showToast("暂无日志文件")
                    return@button
                }
                val dst = java.io.File("/sdcard/Download/tcc-log.txt")
                src.copyTo(dst, overwrite = true)
                showToast("已导出到 Download/tcc-log.txt")
            } catch (e: Exception) {
                showToast("导出失败: ${e.message}")
            }
        }

        content.addView(buttonBar {
            addView(refreshBtn)
            addView(exportBtn)
        })
        content.addView(logText, LayoutParams(-1, 0, 1f))

        pushPage("调试日志", content)
    }

    // ---- UI Helpers ----
    private fun sectionTitle(text: String) = TextView(context).apply {
        this.text = text.uppercase(); setTextColor(TXT_SEC); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
        layoutParams = LayoutParams(-1, -2).apply { topMargin = dp(20); bottomMargin = dp(8); leftMargin = dp(4) }
    }
    private fun fieldLabel(text: String) = TextView(context).apply {
        this.text = text; setTextColor(TXT_SEC); textSize = 13f
        layoutParams = LayoutParams(-1, -2).apply { bottomMargin = dp(4); topMargin = dp(8) }
    }
    private fun textLine(text: String) = TextView(context).apply {
        this.text = text; setTextColor(TXT_SEC); textSize = 13f
        layoutParams = LayoutParams(-1, -2).apply { bottomMargin = dp(4) }
    }
    private fun sectionDivider() = View(context).apply {
        setBackgroundColor(0xFF2A2A30.toInt())
        layoutParams = LayoutParams(-1, dp(1)).apply { topMargin = dp(4); bottomMargin = dp(12) }
    }
    private fun editText(text: String) = android.widget.EditText(context).apply {
        setText(text); setTextColor(TXT_PRI); setHintTextColor(TXT_TERTIARY); textSize = 14f
        setBackgroundDrawable(borderedDrawable(ELEVATED, 0xFF2A2A30.toInt(), dp(8), dp(1)))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }
    private fun cardLayout(init: LinearLayout.() -> Unit) = LinearLayout(context).apply {
        orientation = VERTICAL; setBackgroundColor(SURFACE); setPadding(dp(16), dp(12), dp(16), dp(12))
        setBackgroundDrawable(roundedDrawable(SURFACE, dp(12)))
        layoutParams = LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        init()
    }
    private fun buttonBar(init: LinearLayout.() -> Unit) = LinearLayout(context).apply {
        orientation = HORIZONTAL; layoutParams = LayoutParams(-1, -2).apply { topMargin = dp(16) }
        init()
    }
    private fun button(text: String, color: Int = ACCENT, onClick: () -> Unit = {}): android.widget.Button {
        return android.widget.Button(context).apply {
            this.text = text; setTextColor(0xFFFFFFFF.toInt()); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setBackgroundDrawable(roundedDrawable(color, dp(10)))
            setPadding(dp(24), dp(12), dp(24), dp(12))
            layoutParams = LayoutParams(-2, -2).apply { rightMargin = dp(10) }
            setOnClickListener { onClick() }
        }
    }

    fun loadConfig() = Unit  // handled on-the-fly

    private fun roundedDrawable(color: Int, radius: Int) = object : android.graphics.drawable.Drawable() {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        override fun draw(c: android.graphics.Canvas) { c.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), radius.toFloat(), radius.toFloat(), p) }
        override fun setAlpha(a: Int) { p.alpha = a }
        override fun setColorFilter(f: android.graphics.ColorFilter?) { p.colorFilter = f }
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun borderedDrawable(fillColor: Int, strokeColor: Int, radius: Int, strokeWidth: Int) = object : android.graphics.drawable.Drawable() {
        private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = fillColor }
        private val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = strokeColor; this.style = android.graphics.Paint.Style.STROKE; this.strokeWidth = strokeWidth.toFloat()
        }
        override fun draw(c: android.graphics.Canvas) {
            val r = radius.toFloat()
            c.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), r, r, fill)
            c.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), r, r, stroke)
        }
        override fun setAlpha(a: Int) { fill.alpha = a; stroke.alpha = a }
        override fun setColorFilter(f: android.graphics.ColorFilter?) { fill.colorFilter = f; stroke.colorFilter = f }
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun showToast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
