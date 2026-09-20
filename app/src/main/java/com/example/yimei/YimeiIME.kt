package com.example.yimei

import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 医美回复输入法：
 *  · 话术面板：读剪贴板里的客户消息 → 三条候选回复 → 点一下输入；常用话术一键输入。
 *  · 打字面板：全拼中文输入（整句连打、候选栏、拼音显示在输入框里）、中/英、数字符号页、长按连删。
 */
class YimeiIME : InputMethodService() {
    private val ink = Color.parseColor("#17363B")
    private val jade = Color.parseColor("#2F8F83")
    private val kbBg = Color.parseColor("#D3DADC")
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var phrasePanel: LinearLayout
    private lateinit var typePanel: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var replyBox: LinearLayout
    private lateinit var toneViews: List<TextView>
    private lateinit var bufferView: TextView
    private lateinit var candBox: LinearLayout
    private lateinit var candScroll: HorizontalScrollView
    private lateinit var rowsBox: LinearLayout

    private var tone = Tone.PRO
    private var message = ""
    private var generating = false

    private var engine: PinyinEngine? = null
    private var engineLoading = false
    private var buffer = ""
    private var cands: List<Cand> = emptyList()
    private var composing = false
    private var isChinese = true
    private var numberPage = false
    private var retKey: TextView? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        ReplyEngine.load(this)
    }

    override fun onCreateInputView(): View {
        val root = FrameLayout(this)
        root.setBackgroundColor(kbBg)
        buildPhrasePanel()
        buildTypePanel()
        root.addView(phrasePanel, FrameLayout.LayoutParams(-1, dp(310)))
        root.addView(typePanel, FrameLayout.LayoutParams(-1, dp(268)))
        showTyping(false)
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        buffer = ""; composing = false
        if (this::typePanel.isInitialized) refreshCandidates()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        buffer = ""; composing = false
    }

    private fun showTyping(on: Boolean) {
        phrasePanel.visibility = if (on) View.GONE else View.VISIBLE
        typePanel.visibility = if (on) View.VISIBLE else View.GONE
        if (on) { loadEngine(); rebuildRows() } else clearBuffer()
    }

    // ---------- 通用控件 ----------
    private fun shape(color: Int, r: Int = 6) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private fun pressable(color: Int, pressed: Int = Color.parseColor("#BFC8CA"), r: Int = 6) =
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(pressed, r))
            addState(intArrayOf(), shape(color, r))
        }

    private fun key(title: String, size: Float = 15f): TextView = TextView(this).apply {
        text = title; textSize = size; gravity = Gravity.CENTER; setTextColor(Color.BLACK)
        background = pressable(Color.WHITE); isClickable = true
    }

    private fun weight(v: View, w: Float = 1f, h: Int = -1, margin: Int = 2) {
        v.layoutParams = LinearLayout.LayoutParams(0, h, w).apply { setMargins(dp(margin), 0, dp(margin), 0) }
    }

    private fun onDownKey(v: TextView, action: () -> Unit) {
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { view.isPressed = true; action() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.isPressed = false
            }
            true
        }
    }

    private fun bindDelete(v: TextView) {
        val rep = object : Runnable { override fun run() { deleteOnce(); ui.postDelayed(this, 80) } }
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { view.isPressed = true; deleteOnce(); ui.postDelayed(rep, 400) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { view.isPressed = false; ui.removeCallbacks(rep) }
            }
            true
        }
    }

    private fun showPicker() =
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()

    // ---------- 话术面板 ----------
    private fun buildPhrasePanel() {
        phrasePanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(4)) }

        val gen = TextView(this).apply {
            text = "读取剪贴板并生成"; textSize = 14f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(dp(12), 0, dp(12), 0); background = pressable(ink, jade, 8)
            setOnClickListener { pasteAndGenerate() }
        }
        val seg = LinearLayout(this).apply { background = shape(Color.parseColor("#C3CBCD"), 8); setPadding(dp(2), dp(2), dp(2), dp(2)) }
        toneViews = Tone.values().map { t ->
            TextView(this).apply {
                text = t.title; textSize = 13f; gravity = Gravity.CENTER
                setOnClickListener { tone = t; updateToneUi(); if (message.isNotEmpty()) generate() }
            }
        }
        toneViews.forEach { weight(it, 1f, -1, 0); seg.addView(it) }
        updateToneUi()
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(gen, LinearLayout.LayoutParams(-2, dp(36)))
        top.addView(seg, LinearLayout.LayoutParams(0, dp(36), 1f).apply { leftMargin = dp(8) })
        phrasePanel.addView(top, LinearLayout.LayoutParams(-1, dp(36)))

        statusView = TextView(this).apply { text = "复制客户消息后点上方按钮"; textSize = 11f; setTextColor(Color.parseColor("#6B7F83")); setPadding(dp(4), dp(4), 0, dp(4)); maxLines = 1 }
        phrasePanel.addView(statusView)

        replyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this).apply { addView(replyBox) }
        phrasePanel.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))

        val quickRow = LinearLayout(this)
        ReplyEngine.quick.forEach { q ->
            val k = key(q.k).apply { setPadding(dp(14), 0, dp(14), 0); setOnClickListener { commit(q.text) } }
            quickRow.addView(k, LinearLayout.LayoutParams(-2, -1).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(quickRow) }
        phrasePanel.addView(hs, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(4) })

        val bottom = LinearLayout(this)
        val globe = key("🌐").apply { setOnClickListener { showPicker() } }
        val typeBtn = key("打字").apply { setOnClickListener { showTyping(true) } }
        val del = key("⌫"); bindDelete(del)
        val ret = key("换行").apply { setOnClickListener { enter() } }
        listOf(globe, typeBtn, del, ret).forEach { weight(it); bottom.addView(it) }
        phrasePanel.addView(bottom, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(4) })
    }

    private fun updateToneUi() {
        toneViews.forEachIndexed { i, v ->
            val on = Tone.values()[i] == tone
            v.background = if (on) shape(Color.WHITE, 6) else null
            v.setTextColor(if (on) Color.BLACK else Color.parseColor("#6B7F83"))
            v.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun pasteAndGenerate() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (text.isNullOrEmpty()) { statusView.text = "剪贴板是空的，先复制客户消息"; return }
        message = text.take(500)
        generate()
    }

    private fun generate() {
        if (generating) return
        generating = true
        val d = ReplyEngine.detect(message)
        val label = d.intent.label + (d.project?.let { " · " + it.name } ?: "")
        statusView.text = "$label  生成中…"
        fun show(list: List<String>, fromAI: Boolean) {
            replyBox.removeAllViews()
            val kinds = listOf("专业解析", "追问信息", "邀约面诊")
            list.forEachIndexed { i, t ->
                val c = Compliance.check(t)
                replyBox.addView(replyCard(kinds.getOrElse(i) { "备选" }, c.text, c.hits),
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            }
            statusView.text = "$label  ${if (fromAI) "AI 生成" else "话术库"}，发送前请核对"
            generating = false
        }
        val local = { ReplyEngine.buildReplies(message, d, tone) }
        if (ReplyApi.aiEnabled(this)) {
            ReplyApi.fetch(this, message, tone, d) { ai -> if (ai != null) show(ai, true) else show(local(), false) }
        } else show(local(), false)
    }

    private fun replyCard(kind: String, text: String, hits: List<String>): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = pressable(Color.WHITE, Color.parseColor("#EEF5F4"), 10)
            setPadding(dp(10), dp(8), dp(10), dp(8)); isClickable = true
            setOnClickListener { commit(text) }
        }
        card.addView(TextView(this).apply { this.text = "$kind  · 轻点输入"; textSize = 11f; setTextColor(jade); typeface = Typeface.DEFAULT_BOLD })
        card.addView(TextView(this).apply { this.text = text; textSize = 14f; setTextColor(Color.BLACK); maxLines = 5; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0) })
        if (hits.isNotEmpty()) card.addView(TextView(this).apply { this.text = "已替换违规表述：" + hits.joinToString("、"); textSize = 10f; setTextColor(Color.parseColor("#B5563F")) })
        return card
    }

    // ---------- 打字面板 ----------
    private fun buildTypePanel() {
        typePanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
        bufferView = TextView(this).apply { textSize = 12f; setTextColor(jade); typeface = Typeface.MONOSPACE; setPadding(dp(4), 0, 0, 0); text = " " }
        typePanel.addView(bufferView, LinearLayout.LayoutParams(-1, dp(16)))

        val candRow = LinearLayout(this)
        val back = key("话术").apply { setTextColor(jade); setPadding(dp(12), 0, dp(12), 0); setOnClickListener { showTyping(false) } }
        candRow.addView(back, LinearLayout.LayoutParams(-2, -1).apply { rightMargin = dp(6) })
        candBox = LinearLayout(this)
        candScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(candBox) }
        candRow.addView(candScroll, LinearLayout.LayoutParams(0, -1, 1f))
        typePanel.addView(candRow, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(2); bottomMargin = dp(4) })

        rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        typePanel.addView(rowsBox, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun rebuildRows() {
        rowsBox.removeAllViews()
        val r1: List<String>; val r2: List<String>; val r3: List<String>
        if (numberPage) {
            r1 = "1234567890".map { it.toString() }
            r2 = listOf("，", "。", "？", "！", "、", "：", "；", "“", "”", "（")
            r3 = listOf("）", "…", "—", "～", "@", "#", "&", "/")
        } else {
            r1 = "qwertyuiop".map { it.toString() }
            r2 = "asdfghjkl".map { it.toString() }
            r3 = "zxcvbnm".map { it.toString() }
        }
        addRow(letterRow(r1))
        addRow(letterRow(r2, if (numberPage) 0 else 16))
        val del = key("⌫", 18f); bindDelete(del)
        addRow(letterRow(r3, 0, del))

        val bottom = LinearLayout(this)
        val sw = key(if (numberPage) "ABC" else "123").apply { setOnClickListener { numberPage = !numberPage; rebuildRows() } }
        val globe = key("🌐").apply { setOnClickListener { showPicker() } }
        val lang = key(if (isChinese) "中" else "英").apply { setOnClickListener { isChinese = !isChinese; clearBuffer(); rebuildRows() } }
        val space = key(if (isChinese) "空格" else "space").apply { setOnClickListener { space() } }
        val ret = key(if (buffer.isEmpty()) "换行" else "确定").apply {
            background = pressable(ink, jade); setTextColor(Color.WHITE); setOnClickListener { enter() }
        }
        retKey = ret
        weight(sw, 1.3f); weight(globe, 1.3f); weight(lang, 1.3f); weight(space, 4.5f); weight(ret, 1.6f)
        listOf(sw, globe, lang, space, ret).forEach { bottom.addView(it) }
        addRow(bottom)
    }

    private fun addRow(row: LinearLayout) {
        rowsBox.addView(row, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(0, dp(3), 0, dp(3)) })
    }

    private fun letterRow(titles: List<String>, inset: Int = 0, extra: TextView? = null): LinearLayout {
        val row = LinearLayout(this).apply { setPadding(dp(inset), 0, dp(inset), 0) }
        titles.forEach { t ->
            val k = key(t, 20f)
            onDownKey(k) { charTapped(t) }
            weight(k, 1f, -1, 2); row.addView(k)
        }
        extra?.let { weight(it, 1f, -1, 2); row.addView(it) }
        return row
    }

    private fun loadEngine() {
        if (engine != null || engineLoading) return
        engineLoading = true
        Thread {
            val e = try { PinyinEngine(this) } catch (_: Exception) { null }
            ui.post { engine = e; engineLoading = false; refreshCandidates() }
        }.start()
    }

    // ---------- 输入逻辑 ----------
    private fun commit(text: String) { currentInputConnection?.commitText(text, 1) }

    private fun charTapped(t: String) {
        val isLetter = t.length == 1 && t[0] in 'a'..'z'
        if (isLetter && isChinese && !numberPage) {
            buffer += t
            refreshCandidates()
        } else {
            if (buffer.isNotEmpty()) commitAllBuffer()
            commit(t)
        }
    }

    private fun refreshCandidates() {
        cands = engine?.candidates(buffer) ?: emptyList()
        val ic = currentInputConnection
        if (buffer.isNotEmpty()) { ic?.setComposingText(buffer, 1); composing = true }
        else if (composing) { ic?.setComposingText("", 1); composing = false }
        if (!this::bufferView.isInitialized) return
        bufferView.text = if (buffer.isEmpty()) " " else if (engine == null) "$buffer  （词库加载中…）" else buffer
        candBox.removeAllViews()
        cands.take(40).forEachIndexed { i, c ->
            val v = TextView(this).apply {
                text = c.text; textSize = 18f; gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(10), 0)
                setTextColor(if (i == 0) jade else Color.BLACK)
                if (i == 0) typeface = Typeface.DEFAULT_BOLD
                background = pressable(Color.TRANSPARENT); isClickable = true
                setOnClickListener { pick(i) }
            }
            candBox.addView(v, LinearLayout.LayoutParams(-2, -1))
        }
        candScroll.scrollTo(0, 0)
        retKey?.text = if (buffer.isEmpty()) "换行" else "确定"
    }

    private fun pick(i: Int) {
        val c = cands.getOrNull(i) ?: return
        composing = false
        commit(c.text)
        buffer = buffer.drop(c.consumed)
        refreshCandidates()
    }

    private fun commitAllBuffer() {
        val first = cands.firstOrNull()
        composing = false
        commit(if (first != null && first.consumed == buffer.length) first.text else buffer)
        buffer = ""
        refreshCandidates()
    }

    private fun clearBuffer() {
        if (buffer.isNotEmpty() || composing) { currentInputConnection?.setComposingText("", 1); composing = false; buffer = "" }
        if (this::bufferView.isInitialized) refreshCandidates()
    }

    private fun space() {
        when {
            buffer.isEmpty() -> commit(" ")
            cands.isNotEmpty() -> pick(0)
            else -> { composing = false; commit(buffer); buffer = ""; refreshCandidates() }
        }
    }

    private fun enter() {
        if (buffer.isEmpty()) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        else { composing = false; commit(buffer); buffer = ""; refreshCandidates() }
    }

    private fun deleteOnce() {
        if (buffer.isNotEmpty()) { buffer = buffer.dropLast(1); refreshCandidates() }
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }
}
