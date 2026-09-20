package com.example.yimei

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** 设置页：开启输入法引导、后端域名、AI 开关、试用输入框。 */
class MainActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = ReplyApi.prefs(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(24)) }

        fun text(s: String, size: Float = 15f, bold: Boolean = false, color: Int = Color.parseColor("#17363B")) =
            TextView(this).apply { text = s; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(6), 0, dp(6)) }

        box.addView(text("医美回复键盘", 24f, true))
        box.addView(text("开启键盘（只需一次）", 16f, true))
        box.addView(text("1. 点下面「启用输入法」，在列表里打开「医美回复」\n2. 回到微信输入框，点「切换输入法」选「医美回复」\n3. 复制客户消息 → 点「读取剪贴板并生成」→ 点候选回复输入 → 发送"))
        box.addView(Button(this).apply { text = "启用输入法"; setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } })
        box.addView(Button(this).apply { text = "切换输入法"; setOnClickListener { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() } })

        box.addView(text("后端设置", 16f, true))
        val api = EditText(this).apply {
            setText(ReplyApi.baseUrl(this@MainActivity)); hint = "https://你的域名"; setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { prefs.edit().putString("apiBase", s.toString().trim().trimEnd('/')).apply() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        box.addView(api)
        box.addView(Switch(this).apply {
            text = "AI 润色（关闭则只用本地话术库）"; isChecked = ReplyApi.aiEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("useAI", on).apply() }
        })

        box.addView(text("在这里试用键盘", 16f, true))
        box.addView(EditText(this).apply { hint = "点这里，切换到医美回复键盘试试"; minLines = 3; gravity = Gravity.TOP })
        box.addView(text("合规过滤只是兜底，发送前仍需顾问人工核对。", 12f, false, Color.GRAY))

        setContentView(ScrollView(this).apply { addView(box) })
    }
}
