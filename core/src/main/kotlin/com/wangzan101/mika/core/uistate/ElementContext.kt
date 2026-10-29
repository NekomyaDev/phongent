package com.wangzan101.mika.core.uistate

/**
 * 一个元素自己的上下文,判它做的是什么时只看这些,不看整屏:它自己的名字、同一行上的文字(附着的标签、行里的说明),
 * 以及它上方最近的一个小节标题(不可点、不可编辑的文字)。屏上别处的字(页脚的隐私政策、别的设置行)不算。
 */
object ElementContext {
    /** 同一行:中心落在元素的上下范围里,或元素的中心落在它的上下范围里。 */
    fun row(e: UiElement, s: ScreenSnapshot): List<UiElement> {
        if (e.bounds.size != 4) return emptyList()
        val cy = (e.bounds[1] + e.bounds[3]) / 2
        return s.elements.filter { o ->
            o !== e && o.bounds.size == 4 && ((o.bounds[1] + o.bounds[3]) / 2 in e.bounds[1]..e.bounds[3] || cy in o.bounds[1]..o.bounds[3])
        }
    }

    /** 上方最近的小节标题:整个在元素上面、不可点、不可编辑、有字;没有为 null。 */
    fun heading(e: UiElement, s: ScreenSnapshot): UiElement? {
        if (e.bounds.size != 4) return null
        return s.elements.filter { o ->
            o !== e && o.bounds.size == 4 && o.bounds[3] <= e.bounds[1] && !o.clickable && !o.editable && o.name.isNotBlank()
        }.maxByOrNull { it.bounds[3] }
    }

    /** 元素自己和同一行的文字(附着的标签),小写。 */
    fun label(e: UiElement, s: ScreenSnapshot?): String =
        (listOf(e) + (s?.let { row(e, it) }.orEmpty().filter { !it.editable })).joinToString(" ") { it.name }.lowercase()
}
