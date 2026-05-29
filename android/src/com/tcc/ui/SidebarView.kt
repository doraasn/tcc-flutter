package com.tcc.ui
import android.view.ViewGroup.LayoutParams

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import com.tcc.model.Conversation

class SidebarView(context: Context) : FrameLayout(context) {

    companion object {
        private const val BG = 0xFF0A0A0B.toInt()
        private const val SURFACE = 0xFF141416.toInt()
        private const val SURFACE_HOVER = 0xFF252529.toInt()
        private const val ACCENT = 0xFF6C5CE7.toInt()
        private const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
        private const val TEXT_SECONDARY = 0xFF8B8B93.toInt()
        private const val TEXT_TERTIARY = 0xFF5E5E66.toInt()
        private const val SURFACE_ELEVATED = 0xFF1C1C1F.toInt()
        private const val BORDER = 0xFF2A2A2E.toInt()
        private const val SIDEBAR_WIDTH_DP = 280
    }

    private var baseFontSize = 14

    var onConversationSelect: ((String) -> Unit)? = null
    var onNewChat: (() -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null
    var onStatusClick: (() -> Unit)? = null
    var onLarkClick: (() -> Unit)? = null
    var onShellClick: (() -> Unit)? = null

    private val sidebarWidth: Int
    private val contentView = LinearLayout(context)
    private val overlayView = View(context)
    private val conversationContainer = LinearLayout(context)
    private val handler = Handler(Looper.getMainLooper())
    private val conversationItems = mutableListOf<ConversationItem>()

    private var _isOpen = false
    val isOpen: Boolean get() = _isOpen
    private var activeId: String? = null
    private var conversations = listOf<Conversation>()
    private val textViews = mutableListOf<TextView>()
    private var searchInput: EditText? = null
    private var allConversations = listOf<Conversation>()
    private var searchQuery = ""

    init {
        sidebarWidth = dp(SIDEBAR_WIDTH_DP)

        // Semi-transparent overlay
        overlayView.apply {
            setBackgroundColor(0x80000000.toInt())
            alpha = 0f
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setOnClickListener {
                close()
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    close()
                    true
                } else {
                    false
                }
            }
        }
        addView(overlayView)

        // Sidebar content panel
        contentView.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE)
            translationX = -sidebarWidth.toFloat()
            setPadding(dp(0), dp(24 + getStatusBarHeight()), dp(0), dp(0))
            layoutParams = FrameLayout.LayoutParams(sidebarWidth, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.START
            }
        }
        addView(contentView)

        buildContent()
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(24)
    }

    private fun buildContent() {
        // Title area
        val titleArea = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(20)
                rightMargin = dp(20)
                bottomMargin = dp(20)
            }
        }

        val titleText = TextView(context).apply {
            text = "TCC"
            setTextColor(ACCENT)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        titleArea.addView(titleText)

        val newChatBtn = createNewChatButton()
        titleArea.addView(newChatBtn)
        contentView.addView(titleArea)

        // Separator
        val separator = View(context).apply {
            setBackgroundColor(BORDER)
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1))
        }
        contentView.addView(separator)

        // Search input
        val searchInput = EditText(context).apply {
            setBackgroundColor(SURFACE_ELEVATED)
            setTextColor(TEXT_PRIMARY)
            hint = "搜索对话…"
            setHintTextColor(TEXT_TERTIARY)
            textSize = 13f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(16)
                rightMargin = dp(16)
                bottomMargin = dp(8)
            }
            maxLines = 1
            setBackgroundDrawable(createRoundedDrawable(SURFACE_ELEVATED, dp(8)))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {}
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterConversations(s?.toString() ?: "")
                }
            })
            this@SidebarView.searchInput = this
        }
        contentView.addView(searchInput)

        // Conversation list (scrollable, weight 1)
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            isVerticalScrollBarEnabled = true
        }

        conversationContainer.apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        scrollView.addView(conversationContainer)
        contentView.addView(scrollView)

        // Bottom: Settings button
        val bottomSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val bottomSeparator = View(context).apply {
            setBackgroundColor(BORDER)
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1))
        }
        bottomSection.addView(bottomSeparator)

        val settingsBtn = TextView(context).apply {
            text = "⚙  Settings"
            setTextColor(TEXT_SECONDARY)
            textSize = baseFontSize.toFloat()
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                onSettingsClick?.invoke()
            }
        }
        bottomSection.addView(settingsBtn)

        val statusBtn = TextView(context).apply {
            text = "📊  系统状态"
            setTextColor(TEXT_SECONDARY)
            textSize = baseFontSize.toFloat()
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                onStatusClick?.invoke()
            }
        }
        bottomSection.addView(statusBtn)

        val larkBtn = TextView(context).apply {
            text = "📨  飞书 CLI"
            setTextColor(TEXT_SECONDARY)
            textSize = baseFontSize.toFloat()
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                onLarkClick?.invoke()
            }
        }
        bottomSection.addView(larkBtn)

        val shellBtn = TextView(context).apply {
            text = ">_  Shell"
            setTextColor(TEXT_SECONDARY)
            textSize = baseFontSize.toFloat()
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                onShellClick?.invoke()
            }
        }
        bottomSection.addView(shellBtn)
        contentView.addView(bottomSection)
    }

    private fun createNewChatButton(): TextView {
        return TextView(context).apply {
            text = "新对话"
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            gravity = Gravity.CENTER
            setBackgroundDrawable(createRoundedDrawable(ACCENT, dp(8)))
            setPadding(dp(16), dp(8), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                onNewChat?.invoke()
            }
        }
    }

    fun setConversations(convs: List<Conversation>) {
        allConversations = convs
        conversations = convs
        val filtered = if (searchQuery.isEmpty()) {
            allConversations
        } else {
            allConversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        displayConversations(filtered)
    }

    private fun filterConversations(query: String) {
        searchQuery = query
        setConversations(allConversations)
    }

    private fun displayConversations(convs: List<Conversation>) {
        conversationContainer.removeAllViews()
        conversationItems.clear()
        textViews.clear()

        val now = System.currentTimeMillis()
        val sorted = convs.sortedByDescending { it.updatedAt }
        val sections = sorted.groupBy { getSectionKey(it.updatedAt, now) }

        val sectionOrder = listOf("今天", "昨天", "更早")
        for (section in sectionOrder) {
            val items = sections[section] ?: continue
            val header = TextView(context).apply {
                text = section
                setTextColor(TEXT_SECONDARY)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(16), dp(8), dp(4), dp(4))
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }
            conversationContainer.addView(header)
            textViews.add(header)

            for (conv in items) {
                val item = ConversationItem(context, conv)
                conversationContainer.addView(item)
                conversationItems.add(item)
            }
        }

        updateActiveState()
    }

    private fun getSectionKey(timestamp: Long, now: Long): String {
        val diff = now - timestamp
        return when {
            diff < 24 * 60 * 60 * 1000 -> "今天"
            diff < 48 * 60 * 60 * 1000 -> "昨天"
            else -> "更早"
        }
    }

    fun setActiveId(id: String?) {
        activeId = id
        updateActiveState()
    }

    private fun updateActiveState() {
        for (item in conversationItems) {
            item.setActive(item.conversation.id == activeId)
        }
    }

    fun open() {
        if (_isOpen) return
        _isOpen = true
        overlayView.visibility = View.VISIBLE

        val animator = ValueAnimator.ofFloat(-sidebarWidth.toFloat(), 0f).apply {
            duration = 250
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                contentView.translationX = value
                overlayView.alpha = value / -sidebarWidth.toFloat() + 1f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {}
            })
            start()
        }
    }

    fun close() {
        if (!_isOpen) return
        _isOpen = false

        val animator = ValueAnimator.ofFloat(0f, -sidebarWidth.toFloat()).apply {
            duration = 250
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                contentView.translationX = value
                overlayView.alpha = value / -sidebarWidth.toFloat() + 1f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlayView.visibility = View.GONE
                }
            })
            start()
        }
    }

    fun toggle() {
        if (_isOpen) close() else open()
    }

    fun applyFontSize(size: Int) {
        baseFontSize = size
        if (conversations.isNotEmpty()) {
            setConversations(conversations)
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

    // Conversation item view
    private inner class ConversationItem(context: Context, val conversation: Conversation) : LinearLayout(context) {
        private val titleText = TextView(context)
        private val timeText = TextView(context)
        private var isActive = false

        init {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(SURFACE)
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            minimumHeight = dp(56)

            // Title
            titleText.apply {
                val title = if (conversation.title.length > 20) {
                    conversation.title.take(20) + "…"
                } else {
                    conversation.title
                }
                text = if (title.isBlank()) "新对话" else title
                setTextColor(TEXT_PRIMARY)
                textSize = baseFontSize.toFloat()
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }
            addView(titleText)

            // Timestamp
            timeText.apply {
                text = formatRelativeTime(conversation.updatedAt)
                setTextColor(TEXT_TERTIARY)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                }
            }
            addView(timeText)

            // Separator
            val separator = View(context).apply {
                setBackgroundColor(BORDER)
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply {
                    topMargin = dp(12)
                }
            }
            addView(separator)

            // Click to select
            setOnClickListener {
                onConversationSelect?.invoke(conversation.id)
            }

            // Long press to delete
            setOnLongClickListener {
                showDeleteDialog(conversation)
                true
            }
        }

        fun setActive(active: Boolean) {
            isActive = active
            setBackgroundColor(if (active) SURFACE_HOVER else SURFACE)
        }

        private fun showDeleteDialog(conv: Conversation) {
            AlertDialog.Builder(context)
                .setTitle("删除对话")
                .setMessage("确定要删除「${conv.title}」吗？")
                .setPositiveButton("删除") { _, _ ->
                    // Notify parent to handle deletion
                    onConversationSelect?.invoke("__delete__${conv.id}")
                }
                .setNegativeButton("取消", null)
                .show()
        }

        private fun formatRelativeTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            val minutes = diff / 60000
            val hours = minutes / 60
            val days = hours / 24

            return when {
                minutes < 1 -> "刚刚"
                minutes < 60 -> "${minutes}分钟前"
                hours < 24 -> "${hours}小时前"
                days == 1L -> "昨天"
                else -> {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
                    val month = cal.get(java.util.Calendar.MONTH) + 1
                    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
                    "${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                }
            }
        }
    }
}
