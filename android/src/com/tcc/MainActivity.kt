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
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import com.tcc.api.AnthropicClient
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import com.tcc.api.AnthropicClient.StreamEvent
import com.tcc.data.ConfigManager
import com.tcc.data.ConversationManager
import com.tcc.model.Conversation
import com.tcc.model.Message
import com.tcc.ui.ChatListView
import com.tcc.ui.MessageInputView
import com.tcc.ui.SettingsView
import com.tcc.ui.SidebarView
import com.tcc.ui.SystemStatusView
import com.tcc.ui.LarkToolsView
import com.tcc.ui.ShellView

class MainActivity : Activity() {

    companion object {
        private const val TAG = "TCC"
        private const val BG = 0xFF0A0A0B.toInt()
        private const val SURFACE = 0xFF141416.toInt()
        private const val ACCENT = 0xFF6C5CE7.toInt()
        private const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val TEXT_SECONDARY = 0xFF8B8B93.toInt()
        private const val TEXT_TERTIARY = 0xFF5E5E66.toInt()
        private const val BORDER = 0xFF2A2A2E.toInt()
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
    private lateinit var sendStopButton: View

    private val config by lazy { ConfigManager.getInstance(this) }
    private val convManager by lazy { ConversationManager.getInstance(this) }
    private var currentConv: Conversation? = null
    private var anthropicClient: AnthropicClient? = null
    private var isStreaming = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isFirstMessage = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = BG

        val root = FrameLayout(this)
        rootView = root

        // Main content area
        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }

        // Top bar: hamburger + title + model
        val topBar = createTopBar()
        mainContent.addView(topBar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))

        // Chat list (flexible weight)
        chatList = ChatListView(this)
        mainContent.addView(chatList, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(4)
        })

        // Message input
        input = MessageInputView(this)
        mainContent.addView(input, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        root.addView(mainContent, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Sidebar
        sidebar = SidebarView(this)
        root.addView(sidebar, FrameLayout.LayoutParams(dp(280), LayoutParams.MATCH_PARENT))

        // Settings
        settings = SettingsView(this)
        settings.visibility = View.GONE
        root.addView(settings, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // System status
        systemStatus = SystemStatusView(this)
        systemStatus.visibility = View.GONE
        root.addView(systemStatus, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Lark tools
        larkTools = LarkToolsView(this)
        larkTools.visibility = View.GONE
        root.addView(larkTools, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Shell view
        shellView = ShellView(this)
        shellView.visibility = View.GONE
        root.addView(shellView, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        setContentView(root)

        setupCallbacks()
        applyFontSizeToViews()
        loadConversations()

        if (config.getApiKey().isEmpty()) {
            showToast("请先在设置中配置 API Key")
            settings.visibility = View.VISIBLE
            settings.loadConfig()
        }
    }

    private fun createTopBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(SURFACE)
            setPadding(dp(8), dp(0), dp(16), dp(0))
            elevation = 4f

            // Hamburger button (custom 3-line icon)
            val hamburger = object : View(this@MainActivity) {
                private val paint = android.graphics.Paint().apply {
                    color = TEXT_PRIMARY
                    strokeWidth = dp(2).toFloat()
                    strokeCap = android.graphics.Paint.Cap.ROUND
                }
                override fun onDraw(canvas: android.graphics.Canvas) {
                    val w = width.toFloat()
                    val h = height.toFloat()
                    val inset = w * 0.25f
                    val spacing = h * 0.22f
                    val cy = h / 2
                    for (i in -1..1) {
                        canvas.drawLine(inset, cy + i * spacing, w - inset, cy + i * spacing, paint)
                    }
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                setOnClickListener {
                    sidebar.toggle()
                }
            }
            addView(hamburger)

            // Title
            topBarTitle = TextView(this@MainActivity).apply {
                text = "TCC"
                setTextColor(TEXT_PRIMARY)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(topBarTitle)

            // Model name
            topBarModel = TextView(this@MainActivity).apply {
                text = config.getModel().ifBlank { "claude-sonnet-4-20250514" }
                    .let { if (it.length > 20) it.take(18) + "…" else it }
                setTextColor(TEXT_TERTIARY)
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
            }
            addView(topBarModel)
        }
    }

    private fun setupCallbacks() {
        // Sidebar conversation select
        sidebar.onConversationSelect = { id ->
            if (id.startsWith("__delete__")) {
                val convId = id.removePrefix("__delete__")
                deleteConversation(convId)
            } else {
                switchConversation(id)
                sidebar.close()
            }
        }

        sidebar.onNewChat = {
            newConversation()
            sidebar.close()
        }

        sidebar.onSettingsClick = {
            settings.loadConfig()
            settings.visibility = View.VISIBLE
            sidebar.close()
        }

        sidebar.onStatusClick = {
            systemStatus.refresh()
            systemStatus.visibility = View.VISIBLE
            sidebar.close()
        }

        sidebar.onLarkClick = {
            larkTools.visibility = View.VISIBLE
            sidebar.close()
        }

        sidebar.onShellClick = {
            shellView.visibility = View.VISIBLE
            sidebar.close()
        }

        // Input send
        input.setOnSendListener { text ->
            sendMessage(text)
        }

        // Input commands
        input.setOnCommandListener { cmd, args ->
            sendCommand(cmd, args)
        }

        // Settings close
        settings.onClose = {
            settings.visibility = View.GONE
            applyFontSizeToViews()
        }

        // System status close
        systemStatus.onClose = {
            systemStatus.visibility = View.GONE
        }

        // Lark tools close
        larkTools.onClose = {
            larkTools.visibility = View.GONE
        }

        // Shell view close
        shellView.onClose = {
            shellView.visibility = View.GONE
        }

        // Suggestion clicks
        chatList.setOnSuggestionClick { suggestion ->
            input.setText(suggestion)
        }
    }

    private fun loadConversations() {
        try {
            val convs = convManager.listConversations()
            sidebar.setConversations(convs)

            if (convs.isNotEmpty()) {
                switchConversation(convs.last().id)
            } else {
                newConversation()
            }
        } catch (e: Exception) {
            newConversation()
        }
    }

    private fun switchConversation(id: String) {
        // Save current conversation if needed
        saveCurrentConversation()

        try {
            val conv = convManager.getConversation(id)
            if (conv != null) {
                currentConv = conv
                chatList.setMessages(conv.messages)
                sidebar.setActiveId(conv.id)
                topBarTitle.text = if (conv.title.isBlank()) "新对话" else conv.title
                isFirstMessage = false

                // Check if streaming was interrupted
                if (conv.messages.any { it.isStreaming }) {
                    // Clean up streaming state
                    val cleanMsgs = conv.messages.filter { !it.isStreaming }
                    conv.messages.clear()
                    conv.messages.addAll(cleanMsgs)
                    convManager.saveConversation(conv)
                    chatList.setMessages(cleanMsgs)
                }
            }
        } catch (e: Exception) {
            newConversation()
        }
    }

    private fun saveCurrentConversation() {
        currentConv?.let { conv ->
            try {
                convManager.saveConversation(conv)
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }

    private fun newConversation() {
        saveCurrentConversation()

        try {
            val model = config.getModel().ifBlank { "claude-sonnet-4-20250514" }
            val newConv = convManager.createConversation(model)
            currentConv = newConv
            chatList.setMessages(emptyList())
            topBarTitle.text = "新对话"
            isFirstMessage = true
            sidebar.setActiveId(newConv.id)

            // Refresh sidebar list
            val convs = convManager.listConversations()
            sidebar.setConversations(convs)
        } catch (e: Exception) {
            showToast("创建对话失败: ${e.message}")
        }
    }

    private fun deleteConversation(id: String) {
        try {
            convManager.deleteConversation(id)
            val convs = convManager.listConversations()
            sidebar.setConversations(convs)
            if (currentConv?.id == id) {
                if (convs.isNotEmpty()) {
                    switchConversation(convs.last().id)
                } else {
                    newConversation()
                }
            }
        } catch (e: Exception) {
            showToast("删除失败: ${e.message}")
        }
    }

    private fun sendMessage(text: String) {
        try {
            sendMessageInternal(text)
        } catch (e: Exception) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stack = sw.toString()
            Log.e(TAG, "sendMessage crash: $stack")
            try {
                File("/sdcard/Download/mcc_error.txt").writeText(stack)
            } catch (_: Exception) {}
            showToast("错误: ${e.message}")
        }
    }

    private fun sendMessageInternal(text: String) {
        if (text.isBlank()) return

        if (config.getApiKey().isEmpty()) {
            Log.w(TAG, "API key is empty, showing settings")
            showToast("请先在设置中配置 API Key")
            settings.loadConfig()
            settings.visibility = View.VISIBLE
            return
        }

        if (currentConv == null) {
            newConversation()
        }

        val conv = currentConv ?: return
        val model = config.getModel().ifBlank { "claude-sonnet-4-20250514" }
        Log.d(TAG, "Sending message: model=$model, baseUrl=${config.getBaseUrl()}, keyLen=${config.getApiKey().length}")

        // Create user message
        val userMsg = Message(
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis()
        )

        conv.messages.add(userMsg)
        chatList.addMessage(userMsg)

        // Create empty assistant message for streaming
        val assistantMsg = Message(
            role = "assistant",
            content = "",
            timestamp = System.currentTimeMillis(),
            isStreaming = true
        )

        conv.messages.add(assistantMsg)
        chatList.addMessage(assistantMsg)

        // Update UI for streaming state
        input.isEnabled = false
        isStreaming = true

        // Build message list for API
        val systemPrompt = config.getSystemPrompt()

        anthropicClient = AnthropicClient()
        anthropicClient?.streamChat(
            messages = conv.messages.filter { !it.isStreaming },
            systemPrompt = systemPrompt,
            config = config,
            callback = object : AnthropicClient.StreamCallback {
                override fun onEvent(event: StreamEvent) {
                    mainHandler.post {
                        handleStreamEvent(event)
                    }
                }
            }
        )
    }

    private fun handleStreamEvent(event: StreamEvent) {
        when (event) {
            is StreamEvent.Chunk -> {
                val conv = currentConv ?: return
                val msgs = conv.messages
                val lastIndex = msgs.size - 1
                if (lastIndex >= 0 && msgs[lastIndex].role == "assistant") {
                    val lastMsg = msgs[lastIndex]
                    val updatedMsg = lastMsg.copy(
                        content = lastMsg.content + event.text,
                        isStreaming = true
                    )
                    msgs[lastIndex] = updatedMsg
                    chatList.updateLastMessage(updatedMsg)
                }
            }

            is StreamEvent.ToolUse -> {
                val conv = currentConv ?: return
                val msgs = conv.messages
                val lastIndex = msgs.size - 1
                if (lastIndex >= 0 && msgs[lastIndex].role == "assistant") {
                    val lastMsg = msgs[lastIndex]
                    val toolText = "\n\n[使用工具: ${event.name}]"
                    val updatedMsg = lastMsg.copy(
                        content = lastMsg.content + toolText,
                        isStreaming = true
                    )
                    msgs[lastIndex] = updatedMsg
                    chatList.updateLastMessage(updatedMsg)
                }
            }

            is StreamEvent.Done -> {
                isStreaming = false
                anthropicClient = null

                val conv = currentConv ?: return
                val msgs = conv.messages
                val lastIndex = msgs.size - 1
                if (lastIndex >= 0 && msgs[lastIndex].role == "assistant") {
                    val lastMsg = msgs[lastIndex]
                    val updatedMsg = lastMsg.copy(isStreaming = false)
                    msgs[lastIndex] = updatedMsg
                    chatList.updateLastMessage(updatedMsg)
                    convManager.updateMessage(conv.id, lastIndex, updatedMsg)
                }

                // Auto-title on first AI response
                if (isFirstMessage && conv.messages.size >= 2) {
                    autoTitleConversation(conv)
                }

                input.isEnabled = true
                convManager.saveConversation(conv)

                // Refresh sidebar
                val convs = convManager.listConversations()
                sidebar.setConversations(convs)
            }

            is StreamEvent.Error -> {
                handleStreamError(event.message)
            }
        }
    }

    private fun handleStreamError(error: String) {
        Log.e(TAG, "Stream error: $error")
        try { File("/sdcard/Download/mcc_error.txt").writeText("Stream error: $error") } catch (_: Exception) {}
        isStreaming = false
        anthropicClient?.abort()
        anthropicClient = null
        input.isEnabled = true
        showToast("错误: $error")

        val conv = currentConv ?: return
        val msgs = conv.messages
        val lastIndex = msgs.size - 1
        if (lastIndex >= 0 && msgs[lastIndex].role == "assistant") {
            val lastMsg = msgs[lastIndex]
            val errorContent = if (lastMsg.content.isNotEmpty()) {
                "${lastMsg.content}\n\n[错误: $error]"
            } else {
                "[错误: $error]"
            }
            val updatedMsg = lastMsg.copy(content = errorContent, isStreaming = false)
            msgs[lastIndex] = updatedMsg
            chatList.updateLastMessage(updatedMsg)
            convManager.updateMessage(conv.id, lastIndex, updatedMsg)
        }

        convManager.saveConversation(conv)
    }

    private fun autoTitleConversation(conv: Conversation) {
        // Generate a title from the first user message
        val firstUserMsg = conv.messages.find { it.role == "user" }
        val title = firstUserMsg?.content?.let { content ->
            val cleaned = content.replace("\n", " ").trim()
            if (cleaned.length > 30) cleaned.take(30) + "…" else cleaned
        } ?: "新对话"

        if (title.isNotBlank()) {
            conv.title = title
            topBarTitle.text = title
            isFirstMessage = false
            convManager.saveConversation(conv)

            val convs = convManager.listConversations()
            sidebar.setConversations(convs)
        }
    }

    private fun sendCommand(cmd: String, args: String?) {
        when {
            cmd == "/clear" -> {
                currentConv?.let { conv ->
                    conv.messages.clear()
                    chatList.setMessages(emptyList())
                    convManager.saveConversation(conv)
                    showToast("对话已清除")
                }
            }

            cmd == "/help" -> {
                val helpText = buildString {
                    appendLine("## MCC 命令帮助")
                    appendLine()
                    appendLine("/clear - 清除当前对话")
                    appendLine("/help - 显示此帮助信息")
                    appendLine("/model <模型名> - 切换模型")
                    appendLine("/temperature - 显示温度设置信息")
                    appendLine("/context - 显示上下文信息")
                    appendLine("/compact - 收起之前的对话")
                    appendLine()
                    appendLine("快捷键:")
                    appendLine("- Enter: 发送消息")
                    appendLine("- Shift+Enter: 换行")
                }

                val conv = currentConv ?: return
                val helpMsg = Message(
                    role = "assistant",
                    content = helpText,
                    timestamp = System.currentTimeMillis()
                )
                conv.messages.add(helpMsg)
                chatList.addMessage(helpMsg)
                convManager.saveConversation(conv)
            }

            cmd == "/model" -> {
                if (args != null && args.isNotBlank()) {
                    config.setModel(args)
                    topBarModel.text = if (args.length > 20) args.take(18) + "…" else args
                    showToast("模型已切换为: $args")
                } else {
                    showToast("当前模型: ${config.getModel().ifBlank { "claude-sonnet-4-20250514" }}")
                }
            }

            cmd == "/temperature" -> {
                showToast("温度设置请在代码中配置。当前使用默认温度参数。")
            }

            cmd == "/context" -> {
                val conv = currentConv
                val msgCount = conv?.messages?.size ?: 0
                val totalChars = conv?.messages?.sumOf { it.content.length } ?: 0
                showToast("对话消息数: $msgCount | 总字符数: $totalChars")
            }

            cmd == "/compact" -> {
                showToast("已收起之前的对话（仅保留最近的消息）")
                // Implementation: could truncate older messages
                currentConv?.let { conv ->
                    if (conv.messages.size > 10) {
                        val kept = conv.messages.takeLast(10).toMutableList()
                        conv.messages.clear()
                        conv.messages.addAll(kept)
                        chatList.setMessages(kept)
                        convManager.saveConversation(conv)
                        showToast("已保留最近10条消息")
                    }
                }
            }

            else -> {
                showToast("未知命令: $cmd。输入 /help 查看帮助")
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rootView.requestLayout()
    }

    override fun onBackPressed() {
        when {
            larkTools.visibility == View.VISIBLE -> {
                larkTools.visibility = View.GONE
            }
            shellView.visibility == View.VISIBLE -> {
                shellView.visibility = View.GONE
            }
            systemStatus.visibility == View.VISIBLE -> {
                systemStatus.visibility = View.GONE
            }
            settings.visibility == View.VISIBLE -> {
                settings.visibility = View.GONE
            }
            sidebar.isOpen -> {
                sidebar.close()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentConversation()
    }

    override fun onDestroy() {
        anthropicClient?.abort()
        anthropicClient = null
        saveCurrentConversation()
        super.onDestroy()
    }

    private fun applyFontSizeToViews() {
        val size = config.getFontSize()
        chatList.applyFontSize(size)
        sidebar.applyFontSize(size)
        input.applyFontSize(size)
        topBarTitle.textSize = (size + 4).toFloat()
        topBarModel.textSize = (size - 2).toFloat()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
