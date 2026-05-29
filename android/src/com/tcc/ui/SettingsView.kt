package com.tcc.ui
import android.view.ViewGroup.LayoutParams

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import com.tcc.data.ConfigManager

class SettingsView(context: Context) : FrameLayout(context) {

    companion object {
        private const val BG = 0xFF0A0A0B.toInt()
        private const val SURFACE = 0xFF141416.toInt()
        private const val SURFACE_ELEVATED = 0xFF1C1C1F.toInt()
        private const val ACCENT = 0xFF6C5CE7.toInt()
        private const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val TEXT_SECONDARY = 0xFF8B8B93.toInt()
        private const val TEXT_TERTIARY = 0xFF5E5E66.toInt()
        private const val BORDER = 0xFF2A2A2E.toInt()
        private const val SUCCESS = 0xFF00C853.toInt()
        private const val ERROR = 0xFFFF5252.toInt()
    }

    var onClose: (() -> Unit)? = null

    private var apiKeyInput: EditText? = null
    private var baseUrlInput: EditText? = null
    private var modelSpinner: Spinner? = null
    private var systemPromptInput: EditText? = null
    private var fontSizeSeekBar: SeekBar? = null
    private var fontSizeLabel: TextView? = null
    private var apiKeyVisible = false
    private var toastView: TextView? = null
    private var templateSpinner: Spinner? = null

    private val modelOptions = listOf(
        "deepseek-v4-flash",
        "deepseek-v4",
        "claude-sonnet-4-20250514",
        "claude-opus-4-20250514",
        "claude-haiku-4-20250514",
        "gemini-2.5-flash"
    )

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

        // Model Config Section
        contentLayout.addView(createSectionTitle("模型配置"))
        contentLayout.addView(createModelConfigSection())

        // System Prompt Section
        contentLayout.addView(createSectionTitle("系统提示词"))
        contentLayout.addView(createSystemPromptSection())

        // Appearance Section
        contentLayout.addView(createSectionTitle("外观"))
        contentLayout.addView(createAppearanceSection())

        // WebDAV Backup Section
        contentLayout.addView(createSectionTitle("WebDAV 备份"))
        contentLayout.addView(createWebDavSection())

        contentLayout.addView(createConfigButtons())

        scrollView.addView(contentLayout)
        rootLayout.addView(scrollView)

        addView(rootLayout)
    }

    private fun createTopBar(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(0), dp(16), dp(0))
            setBackgroundColor(SURFACE)

            val titleText = TextView(context).apply {
                text = "设置"
                setTextColor(TEXT_PRIMARY)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(titleText)

            val saveBtn = TextView(context).apply {
                text = "保存"
                setTextColor(ACCENT)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(16)
                }
                setOnClickListener {
                    saveConfig()
                }
            }
            addView(saveBtn)

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

    private fun createSectionTitle(title: String): TextView {
        return TextView(context).apply {
            text = title.uppercase()
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

    private fun createModelConfigSection(): View {
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundDrawable(createRoundedDrawable(SURFACE, dp(12)))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

        // API Key
        val apiKeyLabel = createFieldLabel("API Key")
        section.addView(apiKeyLabel)

        val apiKeyRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        }

        apiKeyInput = EditText(context).apply {
            hint = "sk-ant-..."
            setHintTextColor(TEXT_TERTIARY)
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            typeface = Typeface.MONOSPACE
            setBackgroundColor(SURFACE_ELEVATED)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        apiKeyRow.addView(apiKeyInput!!)

        val toggleEye = TextView(context).apply {
            text = "👁"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener {
                apiKeyVisible = !apiKeyVisible
                apiKeyInput?.inputType = if (apiKeyVisible) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                apiKeyInput?.setSelection(apiKeyInput?.text?.length ?: 0)
            }
        }
        apiKeyRow.addView(toggleEye)
        section.addView(apiKeyRow)

        // Base URL
        val baseUrlLabel = createFieldLabel("Base URL")
        section.addView(baseUrlLabel)

        baseUrlInput = EditText(context).apply {
            hint = "https://api.anthropic.com"
            setHintTextColor(TEXT_TERTIARY)
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT
            typeface = Typeface.MONOSPACE
            setBackgroundColor(SURFACE_ELEVATED)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        }
        section.addView(baseUrlInput)

        // Model
        val modelLabel = createFieldLabel("Model")
        section.addView(modelLabel)

        modelSpinner = Spinner(context).apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
            adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, modelOptions) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(TEXT_PRIMARY)
                    view.textSize = 14f
                    view.setPadding(dp(12), dp(12), dp(12), dp(12))
                    view.setBackgroundColor(SURFACE_ELEVATED)
                    return view
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    if (view is TextView) {
                        view.setTextColor(TEXT_PRIMARY)
                        view.textSize = 14f
                        view.setPadding(dp(12), dp(12), dp(12), dp(12))
                        view.setBackgroundColor(SURFACE_ELEVATED)
                    }
                    view?.setBackgroundColor(if (position % 2 == 0) SURFACE else SURFACE_ELEVATED)
                    return view
                }
            }
            setBackgroundColor(SURFACE_ELEVATED)
        }
        section.addView(modelSpinner)

        return section
    }

    private fun createSystemPromptSection(): View {
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundDrawable(createRoundedDrawable(SURFACE, dp(12)))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

        systemPromptInput = EditText(context).apply {
            hint = "输入系统提示词…"
            setHintTextColor(TEXT_TERTIARY)
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            gravity = Gravity.START or Gravity.TOP
            minHeight = dp(100)
            setBackgroundColor(SURFACE_ELEVATED)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        section.addView(systemPromptInput)

        // Template save row
        val templateRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
        }

        val templateInput = EditText(context).apply {
            hint = "模板名称"
            setHintTextColor(TEXT_TERTIARY)
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            setBackgroundColor(SURFACE_ELEVATED)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
            }
        }
        templateRow.addView(templateInput)

        val saveTemplateBtn = Button(context).apply {
            text = "保存模板"
            setTextColor(TEXT_PRIMARY)
            textSize = 13f
            setBackgroundDrawable(createRoundedDrawable(ACCENT, dp(8)))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                val name = templateInput.text.toString().trim()
                val prompt = systemPromptInput?.text?.toString()?.trim()
                if (name.isNotEmpty() && prompt != null && prompt.isNotEmpty()) {
                    ConfigManager.getInstance(context).savePromptTemplate(name, prompt)
                    showToast("模板「$name」已保存")
                    templateInput.setText("")
                    refreshTemplateSpinner()
                } else {
                    showToast("请输入模板名称和提示词")
                }
            }
        }
        templateRow.addView(saveTemplateBtn)
        section.addView(templateRow)

        // Template load row
        val loadRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }

        val loadLabel = TextView(context).apply {
            text = "加载模板"
            setTextColor(TEXT_SECONDARY)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(8)
            }
        }
        loadRow.addView(loadLabel)

        templateSpinner = Spinner(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
            }
            setBackgroundColor(SURFACE_ELEVATED)
        }
        refreshTemplateSpinner()
        loadRow.addView(templateSpinner!!)

        val loadBtn = Button(context).apply {
            text = "加载"
            setTextColor(TEXT_PRIMARY)
            textSize = 13f
            setBackgroundDrawable(createRoundedDrawable(0xFF2A2A2E.toInt(), dp(8)))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                val tmpl = templateSpinner?.selectedItem as? String
                if (tmpl != null && tmpl.isNotEmpty()) {
                    val templates = ConfigManager.getInstance(context).getPromptTemplates()
                    val found = templates.find { it.first == tmpl }
                    if (found != null) {
                        systemPromptInput?.setText(found.second)
                        showToast("模板「$tmpl」已加载")
                    }
                }
            }
        }
        loadRow.addView(loadBtn)

        val deleteBtn = Button(context).apply {
            text = "删除"
            setTextColor(0xFFFF5252.toInt())
            textSize = 13f
            setBackgroundDrawable(createRoundedDrawable(0xFF2A2A2E.toInt(), dp(8)))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(4)
            }
            setOnClickListener {
                val tmpl = templateSpinner?.selectedItem as? String
                if (tmpl != null && tmpl.isNotEmpty()) {
                    ConfigManager.getInstance(context).deletePromptTemplate(tmpl)
                    showToast("模板「$tmpl」已删除")
                    refreshTemplateSpinner()
                }
            }
        }
        loadRow.addView(deleteBtn)
        section.addView(loadRow)

        return section
    }

    private fun createAppearanceSection(): View {
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundDrawable(createRoundedDrawable(SURFACE, dp(12)))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

        val fontSizeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val fontSizeTitle = TextView(context).apply {
            text = "字体大小"
            setTextColor(TEXT_PRIMARY)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(16)
            }
        }
        fontSizeRow.addView(fontSizeTitle)

        fontSizeSeekBar = SeekBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            max = 8  // 12 to 20
            progress = 2 // default 14
        }
        fontSizeRow.addView(fontSizeSeekBar)

        fontSizeLabel = TextView(context).apply {
            text = "14"
            setTextColor(TEXT_PRIMARY)
            textSize = 15f
            minWidth = dp(32)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(8)
            }
        }
        fontSizeRow.addView(fontSizeLabel)

        section.addView(fontSizeRow)

        fontSizeSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = 12 + progress
                fontSizeLabel?.text = size.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        return section
    }

    private var webdavUrlInput: EditText? = null
    private var webdavUserInput: EditText? = null
    private var webdavPassInput: EditText? = null
    private var webdavVisible = false
    private var statusText: TextView? = null

    private fun createWebDavSection(): View {
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundDrawable(createRoundedDrawable(SURFACE, dp(12)))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

        section.addView(TextView(context).apply {
            text = "支持坚果云等 WebDAV 服务，备份对话记录"
            setTextColor(TEXT_TERTIARY)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        })

        // Server URL
        section.addView(createFieldLabel("服务器地址"))
        webdavUrlInput = EditText(context).apply {
            setHint("https://dav.jianguoyun.com/dav/")
            setHintTextColor(TEXT_TERTIARY)
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            setBackgroundDrawable(createRoundedDrawable(SURFACE_ELEVATED, dp(8)))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        section.addView(webdavUrlInput)

        // Username
        section.addView(createFieldLabel("用户名"))
        webdavUserInput = EditText(context).apply {
            setHint("坚果云邮箱")
            setHintTextColor(TEXT_TERTIARY)
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            setBackgroundDrawable(createRoundedDrawable(SURFACE_ELEVATED, dp(8)))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        section.addView(webdavUserInput)

        // Password
        section.addView(createFieldLabel("密码"))
        val passRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }
        webdavPassInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setHint("坚果云应用密码")
            setHintTextColor(TEXT_TERTIARY)
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            setBackgroundDrawable(createRoundedDrawable(SURFACE_ELEVATED, dp(8)))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        passRow.addView(webdavPassInput)

        val toggleBtn = TextView(context).apply {
            text = "👁"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                webdavVisible = !webdavVisible
                webdavPassInput?.inputType = if (webdavVisible) {
                    InputType.TYPE_CLASS_TEXT
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }
        }
        passRow.addView(toggleBtn)
        section.addView(passRow)

        // Action buttons
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val testBtn = Button(context).apply {
            text = "测试连接"
            setTextColor(TEXT_PRIMARY)
            textSize = 13f
            setBackgroundDrawable(createRoundedDrawable(0xFF2A2A2E.toInt(), dp(8)))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(4)
                weight = 1f
            }
            setOnClickListener {
                saveConfig()
                testWebDavConnection()
            }
        }
        btnRow.addView(testBtn)

        val exportBtn = Button(context).apply {
            text = "导出"
            setTextColor(TEXT_PRIMARY)
            textSize = 13f
            setBackgroundDrawable(createRoundedDrawable(0xFF2A2A2E.toInt(), dp(8)))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
                weight = 1f
            }
            setOnClickListener {
                saveConfig()
                exportLocal()
            }
        }
        btnRow.addView(exportBtn)

        val backupBtn = Button(context).apply {
            text = "备份"
            setTextColor(TEXT_PRIMARY)
            textSize = 13f
            setBackgroundDrawable(createRoundedDrawable(ACCENT, dp(8)))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
                weight = 1f
            }
            setOnClickListener {
                saveConfig()
                backupToWebDav()
            }
        }
        btnRow.addView(backupBtn)

        val restoreBtn = Button(context).apply {
            text = "恢复"
            setTextColor(TEXT_PRIMARY)
            textSize = 13f
            setBackgroundDrawable(createRoundedDrawable(0xFF2A2A2E.toInt(), dp(8)))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(8)
                weight = 1f
            }
            setOnClickListener {
                saveConfig()
                restoreFromWebDav()
            }
        }
        btnRow.addView(restoreBtn)

        section.addView(btnRow)

        statusText = TextView(context).apply {
            text = ""
            setTextColor(TEXT_TERTIARY)
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        section.addView(statusText)

        return section
    }

    private fun testWebDavConnection() {
        statusText?.text = "测试中…"
        val backupMgr = com.tcc.data.BackupManager(context)
        backupMgr.testConnection { ok, msg ->
            android.os.Handler(context.mainLooper).post {
                statusText?.text = msg
                statusText?.setTextColor(if (ok) 0xFF00C853.toInt() else 0xFFFF5252.toInt())
            }
        }
    }

    private fun backupToWebDav() {
        statusText?.text = "备份中…"
        val backupMgr = com.tcc.data.BackupManager(context)
        backupMgr.backupToWebDav { ok, msg ->
            android.os.Handler(context.mainLooper).post {
                statusText?.text = msg
                statusText?.setTextColor(if (ok) 0xFF00C853.toInt() else 0xFFFF5252.toInt())
                if (ok) showToast("备份成功")
            }
        }
    }

    private fun restoreFromWebDav() {
        statusText?.text = "恢复中…"
        val backupMgr = com.tcc.data.BackupManager(context)
        backupMgr.restoreFromWebDav { ok, msg ->
            android.os.Handler(context.mainLooper).post {
                statusText?.text = msg
                statusText?.setTextColor(if (ok) 0xFF00C853.toInt() else 0xFFFF5252.toInt())
                if (ok) showToast("恢复成功，请重启应用")
            }
        }
    }

    private fun exportLocal() {
        statusText?.text = "导出中…"
        val backupMgr = com.tcc.data.BackupManager(context)
        backupMgr.exportToLocal { ok, msg ->
            android.os.Handler(context.mainLooper).post {
                statusText?.text = msg
                statusText?.setTextColor(if (ok) 0xFF00C853.toInt() else 0xFFFF5252.toInt())
            }
        }
    }

    private fun createConfigButtons(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(24)
            }
        }

        val btn = Button(context).apply {
            text = "保存配置"
            setTextColor(TEXT_PRIMARY)
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundDrawable(createRoundedDrawable(ACCENT, dp(12)))
            setPadding(dp(32), dp(12), dp(32), dp(12))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                saveConfig()
            }
        }
        container.addView(btn)

        return container
    }

    private fun createFieldLabel(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(TEXT_SECONDARY)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
                topMargin = dp(4)
            }
        }
    }

    fun loadConfig() {
        val config = ConfigManager.getInstance(context)
        apiKeyInput?.setText(config.getApiKey())
        baseUrlInput?.setText(config.getBaseUrl())

        val model = config.getModel()
        val modelIndex = modelOptions.indexOfFirst { it.equals(model, ignoreCase = true) }
        if (modelIndex >= 0) {
            modelSpinner?.setSelection(modelIndex)
        } else {
            // Add custom model to list
            val allModels = modelOptions.toMutableList()
            if (model.isNotEmpty() && !allModels.contains(model)) {
                allModels.add(0, model)
                modelSpinner?.adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, allModels) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        view.setTextColor(TEXT_PRIMARY)
                        view.textSize = 14f
                        view.setPadding(dp(12), dp(12), dp(12), dp(12))
                        view.setBackgroundColor(SURFACE_ELEVATED)
                        return view
                    }

                    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getDropDownView(position, convertView, parent)
                        if (view is TextView) {
                            view.setTextColor(TEXT_PRIMARY)
                            view.textSize = 14f
                            view.setPadding(dp(12), dp(12), dp(12), dp(12))
                            view.setBackgroundColor(SURFACE_ELEVATED)
                        }
                        view?.setBackgroundColor(if (position % 2 == 0) SURFACE else SURFACE_ELEVATED)
                        return view
                    }
                }
                modelSpinner?.setSelection(0)
            }
        }

        systemPromptInput?.setText(config.getSystemPrompt())

        val fontSize = config.getFontSize()
        val progress = (fontSize - 12).coerceIn(0, 8)
        fontSizeSeekBar?.progress = progress
        fontSizeLabel?.text = fontSize.toString()

        webdavUrlInput?.setText(config.getWebDavUrl())
        webdavUserInput?.setText(config.getWebDavUser())
        webdavPassInput?.setText(config.getWebDavPass())
    }

    fun saveConfig() {
        try {
            val config = ConfigManager.getInstance(context)
            val apiKey = apiKeyInput?.text?.toString()?.trim() ?: ""
            val baseUrl = baseUrlInput?.text?.toString()?.trim() ?: ""
            val model = if (modelSpinner?.selectedItem != null) {
                modelSpinner?.selectedItem.toString()
            } else {
                ""
            }
            val systemPrompt = systemPromptInput?.text?.toString()?.trim() ?: ""
            val fontSize = (fontSizeSeekBar?.progress ?: 2) + 12
            val webdavUrl = webdavUrlInput?.text?.toString()?.trim() ?: ""
            val webdavUser = webdavUserInput?.text?.toString()?.trim() ?: ""
            val webdavPass = webdavPassInput?.text?.toString()?.trim() ?: ""

            config.setApiKey(apiKey)
            config.setBaseUrl(baseUrl)
            config.setModel(model)
            config.setSystemPrompt(systemPrompt)
            config.setFontSize(fontSize)
            config.setWebDavUrl(webdavUrl)
            config.setWebDavUser(webdavUser)
            config.setWebDavPass(webdavPass)

            showToast("配置已保存")
        } catch (e: Exception) {
            showToast("保存失败: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        toastView?.let { removeView(it) }

        toastView = TextView(context).apply {
            text = message
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            gravity = Gravity.CENTER
            setBackgroundDrawable(createRoundedDrawable(0xCC141416.toInt(), dp(8)))
            setPadding(dp(24), dp(12), dp(24), dp(12))
            elevation = 8f
            layoutParams = FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                bottomMargin = dp(32)
            }
            postDelayed({
                if (toastView != null && toastView?.parent != null) {
                    removeView(toastView)
                    toastView = null
                }
            }, 2500)
        }
        addView(toastView)
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

    private fun refreshTemplateSpinner() {
        val templates = ConfigManager.getInstance(context).getPromptTemplates()
        val names = templates.map { it.first }
        val displayNames = if (names.isEmpty()) listOf("(无模板)") else names
        templateSpinner?.adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, displayNames) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(TEXT_PRIMARY)
                view.textSize = 13f
                view.setPadding(dp(8), dp(8), dp(8), dp(8))
                view.setBackgroundColor(SURFACE_ELEVATED)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                if (view is TextView) {
                    view.setTextColor(TEXT_PRIMARY)
                    view.textSize = 13f
                    view.setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                return view
            }
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
