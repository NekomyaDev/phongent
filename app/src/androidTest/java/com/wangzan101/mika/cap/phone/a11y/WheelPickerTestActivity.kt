package com.wangzan101.mika.cap.phone.a11y

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView

/**
 * 滚轮测试页:两个原版数字滚轮(0–59,两位数显示,循环),都从 0 开始。
 * 「分」可以直接输入;「锁住」挡住了里面的输入框(拿不到焦点,写进去的字不会被采用),只能一格一格滚。
 */
class WheelPickerTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun picker(label: String, block: Boolean) {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this).apply { text = label; textSize = 20f })
            col.addView(NumberPicker(this).apply {
                minValue = 0
                maxValue = 59
                value = 0
                setFormatter { "%02d".format(it) }
                contentDescription = label
                if (block) descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            })
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        picker(MINUTES, block = false)
        picker(LOCKED, block = true)
        setContentView(row)
    }

    companion object {
        const val MINUTES = "分"
        const val LOCKED = "锁住"
    }
}
