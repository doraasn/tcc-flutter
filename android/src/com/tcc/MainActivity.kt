package com.tcc

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import com.tcc.api.ClaudeCli
import com.tcc.api.ClaudeCli.StreamEvent
import com.tcc.api.CommandExecutor
import com.tcc.data.ConfigManager
import com.tcc.data.ConversationManager
import com.tcc.model.Conversation
import com.tcc.model.Message
import com.tcc.ui.ChatListView
import com.tcc.ui.MessageInputView
import com.tcc.ui.SettingsView
import com.tcc.ui.SidebarView
import com.tcc.ui.SystemStatusView
import com.tcc.TermuxBootstrap
import com.tcc.ui.LarkToolsView
import com.tcc.ui.ShellView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

// TCC 主界面 - 底部 Tab 导航
class MainActivity : Activity() {

    companion object {
        private const val TAG = "TCC"
        private const val BG = 0xFF0A0A0B.toInt()
        private const val SURFACE = 0xFF141416.toInt()
        private const val ACCENT = 0xFFFF8C00.toInt()
        private const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val TEXT_SECONDARY = 0xFF8B8B93.toInt()
        private const val TEXT_TERTIARY = 0xFF5E5E66.toInt()
        private const val TAB_INACTIVE = 0xFF5E5E66.toInt()
    }

    private lateinit var sidebar: SidebarView
    private lateinit var chatList: ChatListView
    private lateinit var input: MessageInputView
    private lateinit var settings: SettingsView
    private lateinit var systemStatus: SystemStatusView
    private lateinit var larkTools: LarkToolsView
    private lateinit var shellView: ShellView
    private lateinit var topBarTitle: TextView
    private lateinit var topBarModel: TextView
    private lateinit var rootView: FrameLayout
    private lateinit var mainContent: LinearLayout
    private lateinit var contentContainer: FrameLayout
    private lateinit var chatArea: LinearLayout
    private lateinit var bottomTab: LinearLayout
    private lateinit var setupView: LinearLayout
    private val config by lazy { ConfigManager.getInstance(this) }
    private val convManager by lazy { ConversationManager.getInstance(this) }
    private var currentConv: Conversation? = null
    private var claudeCli: ClaudeCli? = null
    private var isStreaming = false
    private var currentSessionId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var setupStatusText: TextView? = null
    private var isFirstMessage = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        window.statusBarColor = BG

        rootView = FrameLayout(this)

        // Main content area (always visible, contains top bar + content + bottom tab)
        mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }

        // Top bar
        mainContent.addView(createTopBar(), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))

        // Content container (switches between chat/settings/tools)
        contentContainer = FrameLayout(this)
        mainContent.addView(contentContainer, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // Chat area (inside content container)
        chatArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        chatList = ChatListView(this)
        chatArea.addView(chatList, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(4)
        })
        input = MessageInputView(this)
        chatArea.addView(input, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        contentContainer.addView(chatArea, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Settings view (inside content container)
        settings = SettingsView(this).apply { visibility = View.GONE }
        contentContainer.addView(settings, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // System status view (inside content container)
        systemStatus = SystemStatusView(this).apply { visibility = View.GONE }
        contentContainer.addView(systemStatus, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Lark tools view (inside content container)
        larkTools = LarkToolsView(this).apply { visibility = View.GONE }
        contentContainer.addView(larkTools, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Shell view (inside content container)
        shellView = ShellView(this).apply { visibility = View.GONE }
        contentContainer.addView(shellView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Bottom tab bar (always visible, below content)
        bottomTab = createBottomTab()
        mainContent.addView(bottomTab, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))

        // Setup view (install progress)
        setupView = createSetupView()
        rootView.addView(setupView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        rootView.addView(mainContent, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Sidebar (conversation list drawer)
        sidebar = SidebarView(this)
        rootView.addView(sidebar, FrameLayout.LayoutParams(dp(280), LayoutParams.MATCH_PARENT))

        setContentView(rootView)

        // 隐藏系统 ActionBar
        try { actionBar?.hide() } catch (_: Exception) {}

        // Init: 对话优先使用 HTTP 模式，Termux/Claude CLI 作为工具页的可选环境。
        if (!TermuxBootstrap.hasBundledEnvironment(this)) {
            Toast.makeText(this, "未内置 Termux 环境，CLI 工具不可用", Toast.LENGTH_LONG).show()
        }
        onReady()
    }

    private fun showSetup() {
        mainContent.visibility = View.GONE
        setupView.visibility = View.VISIBLE
        Thread {
            try {
                val ok = TermuxBootstrap.install(this) { msg ->
                    mainHandler.post { setupStatusText?.text = msg }
                }
                mainHandler.post {
                    if (ok) {
                        setupView.visibility = View.GONE
                        mainContent.visibility = View.VISIBLE
                        onReady()
                    } else {
                        // 显示详细错误信息
                        val detail = TermuxBootstrap.lastError
                        setupStatusText?.text = if (detail != null)
                            "环境安装失败\n\n$detail"
                        else
                            "环境安装失败，请重启应用重试"
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { setupStatusText?.text = "安装异常: $e\n${e.stackTraceToString().take(200)}" }
            }
        }.start()
    }

    private fun onReady() {
        setupCallbacks()
        applyFontSizeToViews()
        loadConversations()
        showTab(TAB_CHAT)
    }

    // ---- Tab Navigation ----
    private val TAB_CHAT = 0
    private val TAB_SETTINGS = 1
    private val TAB_TOOLS = 2
    private var currentTab = TAB_CHAT

    private fun createBottomTab(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(SURFACE)
        elevation = 8f

        val tabData = listOf(
            Triple("◉", "对话", TAB_CHAT),
            Triple("◎", "设置", TAB_SETTINGS),
            Triple("◆", "工具", TAB_TOOLS)
        )
        for ((icon, label, id) in tabData) {
            val tab = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener { showTab(id) }

                addView(TextView(this@MainActivity).apply {
                    text = icon; textSize = 18f
                    layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, 0, 1f)
                    gravity = Gravity.CENTER
                })
                addView(TextView(this@MainActivity).apply {
                    text = label; textSize = 10f
                    setTextColor(TAB_INACTIVE)
                    gravity = Gravity.CENTER
                    tag = "tab_label_$id"
                })
                tag = "tab_$id"
            }
            addView(tab)
        }
    }

    private fun showTab(tab: Int) {
        currentTab = tab
        // Reset all tab colors
        for (i in 0 until bottomTab.childCount) {
            val tabView = bottomTab.getChildAt(i)
            val label = tabView.findViewWithTag<TextView>("tab_label_$i")
            label?.setTextColor(TAB_INACTIVE)
        }
        // Highlight active tab
        val activeTab = bottomTab.findViewWithTag<View>("tab_$tab")
        val activeLabel = activeTab?.findViewWithTag<TextView>("tab_label_$tab")
        activeLabel?.setTextColor(ACCENT)

        // Toggle views inside contentContainer (bottomTab always stays visible)
        when (tab) {
            TAB_CHAT -> {
                chatArea.visibility = View.VISIBLE
                settings.visibility = View.GONE
                systemStatus.visibility = View.GONE
                larkTools.visibility = View.GONE
                shellView.visibility = View.GONE
            }
            TAB_SETTINGS -> {
                chatArea.visibility = View.GONE
                systemStatus.visibility = View.GONE
                larkTools.visibility = View.GONE
                shellView.visibility = View.GONE
                settings.loadConfig()
                settings.visibility = View.VISIBLE
            }
            TAB_TOOLS -> {
                chatArea.visibility = View.GONE
                settings.visibility = View.GONE
                larkTools.visibility = View.GONE
                shellView.visibility = View.GONE
                systemStatus.visibility = View.VISIBLE
                systemStatus.refresh()
            }
        }
    }

    // ---- Top Bar ----
    private fun createTopBar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(SURFACE)
        setPadding(dp(8), dp(0), dp(16), dp(0))
        elevation = 4f

        // Hamburger
        addView(object : View(this@MainActivity) {
            private val paint = android.graphics.Paint().apply {
                color = TEXT_PRIMARY; strokeWidth = dp(2).toFloat()
                strokeCap = android.graphics.Paint.Cap.ROUND
            }
            override fun onDraw(canvas: android.graphics.Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                val inset = w * 0.25f; val spacing = h * 0.22f; val cy = h / 2
                for (i in -1..1) canvas.drawLine(inset, cy + i * spacing, w - inset, cy + i * spacing, paint)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { sidebar.toggle() }
        })

        // Title
        topBarTitle = TextView(this@MainActivity).apply {
            text = "TCC"; setTextColor(TEXT_PRIMARY); textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        addView(topBarTitle)

        // Model indicator
        topBarModel = TextView(this@MainActivity).apply {
            val slot = config.getActiveModelSlot()
            val provider = config.getActiveProvider()
            text = if (provider != null && slot != null) "${provider.name}/${slot.displayName}" else "未配置"
            setTextColor(TEXT_TERTIARY); textSize = 12f
            gravity = Gravity.CENTER_VERTICAL; maxLines = 1
        }
        addView(topBarModel)
    }

    // ---- Setup View ----
    private fun createSetupView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setBackgroundColor(BG); setPadding(dp(32), dp(32), dp(32), dp(32))
        addView(TextView(this@MainActivity).apply {
            text = "TCC"; setTextColor(ACCENT); textSize = 48f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        })
        addView(TextView(this@MainActivity).apply {
            text = "Claude Code 智能客户端"; setTextColor(TEXT_TERTIARY); textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(48) }
        })
        addView(ProgressBar(this@MainActivity).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(dp(200), LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) }
        })
        setupStatusText = TextView(this@MainActivity).apply {
            setTextColor(TEXT_SECONDARY); textSize = 14f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(setupStatusText!!)
    }

    // ---- Callbacks ----
    private fun setupCallbacks() {
        sidebar.onConversationSelect = { id ->
            if (id.startsWith("__delete__")) {
                deleteConversation(id.removePrefix("__delete__"))
            } else {
                switchConversation(id); sidebar.close()
            }
        }
        sidebar.onNewChat = { newConversation(); sidebar.close() }
        input.setOnSendListener { text -> sendMessage(text) }
        input.setOnCommandListener { cmd, args -> sendCommand(cmd, args) }

        settings.onClose = { showTab(TAB_CHAT); applyFontSizeToViews() }
        systemStatus.onClose = { showTab(TAB_CHAT) }
        larkTools.onClose = { showTab(TAB_CHAT) }
        shellView.onClose = { showTab(TAB_CHAT) }
        chatList.setOnSuggestionClick { suggestion -> input.setText(suggestion) }
    }

    // ---- Conversation Management ----
    private fun loadConversations() {
        try {
            val convs = convManager.listConversations()
            sidebar.setConversations(convs)
            if (convs.isNotEmpty()) switchConversation(convs.last().id) else newConversation()
        } catch (_: Exception) { newConversation() }
    }

    private fun switchConversation(id: String) {
        saveCurrentConversation()
        try {
            val conv = convManager.getConversation(id) ?: return
            currentConv = conv
            chatList.setMessages(conv.messages)
            sidebar.setActiveId(conv.id)
            topBarTitle.text = if (conv.title.isBlank()) "新对话" else conv.title
            currentSessionId = conv.claudeSessionId
            isFirstMessage = false
            if (conv.messages.any { it.isStreaming }) {
                val cleanMsgs = conv.messages.filter { !it.isStreaming }
                conv.messages.clear(); conv.messages.addAll(cleanMsgs)
                convManager.saveConversation(conv); chatList.setMessages(cleanMsgs)
            }
        } catch (_: Exception) { newConversation() }
    }

    private fun saveCurrentConversation() {
        currentConv?.let { conv ->
            try {
                conv.claudeSessionId = currentSessionId
                convManager.saveConversation(conv)
            } catch (_: Exception) {}
        }
    }

    private fun newConversation() {
        saveCurrentConversation(); currentSessionId = null
        try {
            val slot = config.getActiveModelSlot()
            val model = slot?.model ?: "claude-sonnet-4-20250514"
            val newConv = convManager.createConversation(model)
            currentConv = newConv; chatList.setMessages(emptyList())
            topBarTitle.text = "新对话"; isFirstMessage = true
            sidebar.setActiveId(newConv.id)
            sidebar.setConversations(convManager.listConversations())
        } catch (e: Exception) { showToast("创建对话失败: ${e.message}") }
    }

    private fun deleteConversation(id: String) {
        try {
            convManager.deleteConversation(id)
            val convs = convManager.listConversations()
            sidebar.setConversations(convs)
            if (currentConv?.id == id) {
                if (convs.isNotEmpty()) switchConversation(convs.last().id) else newConversation()
            }
        } catch (e: Exception) { showToast("删除失败: ${e.message}") }
    }

    // ---- Send Message ----
    private fun sendMessage(text: String) {
        try { sendMessageInternal(text) } catch (e: Exception) {
            Log.e(TAG, "sendMessage: ${e.message}"); isStreaming = false; input.isEnabled = true
            showToast("错误: ${e.message}")
        }
    }

    private fun sendMessageInternal(text: String) {
        if (text.isBlank()) return

        // Check API key
        val provider = config.getActiveProvider()
        val apiKey = provider?.env?.get("ANTHROPIC_API_KEY") ?: ""
        val authToken = provider?.env?.get("ANTHROPIC_AUTH_TOKEN") ?: ""
        if (apiKey.isEmpty() && authToken.isEmpty()) {
            showToast("请先在设置中配置 API Key")
            showTab(TAB_SETTINGS)
            return
        }

        if (currentConv == null) newConversation()
        val conv = currentConv ?: return
        // Create messages
        val userMsg = Message(role = "user", content = text, timestamp = System.currentTimeMillis())
        conv.messages.add(userMsg); chatList.addMessage(userMsg)
        val assistantMsg = Message(role = "assistant", content = "", timestamp = System.currentTimeMillis(), isStreaming = true)
        conv.messages.add(assistantMsg); chatList.addMessage(assistantMsg)

        input.isEnabled = false; isStreaming = true

        val systemPrompt = config.getSystemPrompt()
        claudeCli = ClaudeCli(this)
        claudeCli?.sessionId = currentSessionId
        claudeCli?.streamChat(
            messages = conv.messages.filter { !it.isStreaming },
            systemPrompt = systemPrompt, config = config,
            callback = object : ClaudeCli.StreamCallback {
                override fun onEvent(event: StreamEvent) { mainHandler.post { handleStreamEvent(event) } }
            }
        )
    }

    private fun handleStreamEvent(event: StreamEvent) {
        when (event) {
            is StreamEvent.Chunk -> {
                val conv = currentConv ?: return; val msgs = conv.messages; val i = msgs.size - 1
                if (i >= 0 && msgs[i].role == "assistant") {
                    msgs[i] = msgs[i].copy(content = msgs[i].content + event.text, isStreaming = true)
                    chatList.updateLastMessage(msgs[i])
                }
            }
            is StreamEvent.Done -> {
                isStreaming = false; currentSessionId = claudeCli?.sessionId; claudeCli = null
                val conv = currentConv ?: return; val msgs = conv.messages; val i = msgs.size - 1
                if (i >= 0 && msgs[i].role == "assistant") {
                    msgs[i] = msgs[i].copy(isStreaming = false)
                    chatList.updateLastMessage(msgs[i])
                    convManager.updateMessage(conv.id, i, msgs[i])
                }
                if (isFirstMessage && conv.messages.size >= 2) autoTitleConversation(conv)
                input.isEnabled = true; convManager.saveConversation(conv)
                sidebar.setConversations(convManager.listConversations())
            }
            is StreamEvent.Error -> handleStreamError(event.message)
        }
    }

    private fun handleStreamError(error: String) {
        Log.e(TAG, "Stream error: $error"); isStreaming = false
        claudeCli?.abort(); claudeCli = null; input.isEnabled = true; showToast("错误: $error")
        val conv = currentConv ?: return; val msgs = conv.messages; val i = msgs.size - 1
        if (i >= 0 && msgs[i].role == "assistant") {
            val ec = if (msgs[i].content.isNotEmpty()) "${msgs[i].content}\n\n[错误: $error]" else "[错误: $error]"
            msgs[i] = msgs[i].copy(content = ec, isStreaming = false)
            chatList.updateLastMessage(msgs[i]); convManager.updateMessage(conv.id, i, msgs[i])
        }
        convManager.saveConversation(conv)
    }

    private fun autoTitleConversation(conv: Conversation) {
        val first = conv.messages.find { it.role == "user" }?.content?.replace("\n", " ")?.trim() ?: return
        conv.title = if (first.length > 30) "${first.take(30)}…" else first
        topBarTitle.text = conv.title; isFirstMessage = false
        convManager.saveConversation(conv)
        sidebar.setConversations(convManager.listConversations())
    }

    // ---- Commands ----
    private fun sendCommand(cmd: String, args: String?) {
        when (cmd) {
            "/clear" -> {
                currentConv?.let { c -> c.messages.clear(); chatList.setMessages(emptyList()); convManager.saveConversation(c) }
                showToast("对话已清除")
            }
            "/help" -> {
                val help = buildString {
                    appendLine("## TCC 命令帮助"); appendLine()
                    appendLine("/clear - 清除当前对话"); appendLine("/help - 显示此帮助信息")
                    appendLine("/model - 切换模型"); appendLine("/context - 显示统计信息")
                    appendLine("/run <命令> - 执行本机命令")
                    appendLine("/compact - 保留最近10条消息")
                }
                currentConv?.let { c ->
                    c.messages.add(Message(role = "assistant", content = help, timestamp = System.currentTimeMillis()))
                    chatList.addMessage(c.messages.last()); convManager.saveConversation(c)
                }
            }
            "/model" -> {
                val p = config.getActiveProvider(); val s = config.getActiveModelSlot()
                showToast("当前: ${p?.name ?: "?"} / ${s?.displayName ?: "?"}")
            }
            "/context" -> {
                val conv = currentConv
                showToast("消息: ${conv?.messages?.size ?: 0} | 字符: ${conv?.messages?.sumOf { it.content.length } ?: 0} | 费用: $${"%.4f".format(config.getTotalCost())}")
            }
            "/run" -> runDirectCommand(args)
            "/compact" -> {
                currentConv?.let { c ->
                    if (c.messages.size > 10) { val kept = c.messages.takeLast(10).toMutableList(); c.messages.clear(); c.messages.addAll(kept); chatList.setMessages(c.messages); convManager.saveConversation(c) }
                }
                showToast("已保留最近10条消息")
            }
            else -> showToast("未知命令: $cmd。输入 /help 查看帮助")
        }
    }

    private fun runDirectCommand(args: String?) {
        val command = args?.trim().orEmpty()
        if (command.isEmpty()) {
            showToast("用法: /run <命令>")
            return
        }
        if (currentConv == null) newConversation()
        val conv = currentConv ?: return
        val userMsg = Message(role = "user", content = "/run $command", timestamp = System.currentTimeMillis())
        conv.messages.add(userMsg)
        chatList.addMessage(userMsg)

        val assistantMsg = Message(
            role = "assistant",
            content = "执行中...\n\n`$ $command`",
            timestamp = System.currentTimeMillis(),
            isStreaming = true
        )
        conv.messages.add(assistantMsg)
        chatList.addMessage(assistantMsg)
        input.isEnabled = false
        isStreaming = true

        Thread {
            val result = CommandExecutor.run(this, command)
            val output = "```text\n${result.toToolText()}\n```"
            mainHandler.post {
                isStreaming = false
                input.isEnabled = true
                val idx = conv.messages.size - 1
                if (idx >= 0 && conv.messages[idx].role == "assistant") {
                    conv.messages[idx] = conv.messages[idx].copy(content = output, isStreaming = false)
                    chatList.updateLastMessage(conv.messages[idx])
                    convManager.updateMessage(conv.id, idx, conv.messages[idx])
                }
                convManager.saveConversation(conv)
                sidebar.setConversations(convManager.listConversations())
            }
        }.start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); rootView.requestLayout() }

    override fun onBackPressed() {
        when {
            currentTab != TAB_CHAT -> showTab(TAB_CHAT)
            sidebar.isOpen -> sidebar.close()
            else -> super.onBackPressed()
        }
    }

    override fun onPause() { super.onPause(); saveCurrentConversation() }
    override fun onDestroy() { claudeCli?.abort(); claudeCli = null; saveCurrentConversation(); super.onDestroy() }

    private fun applyFontSizeToViews() {
        val size = config.getFontSize()
        chatList.applyFontSize(size); sidebar.applyFontSize(size); input.applyFontSize(size)
        topBarTitle.textSize = (size + 4).toFloat(); topBarModel.textSize = (size - 2).toFloat()
    }

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
