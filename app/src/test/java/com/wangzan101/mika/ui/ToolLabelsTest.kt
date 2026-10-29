package com.wangzan101.mika.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import com.wangzan101.mika.core.assembly.knownTools

class ToolLabelsTest {
    @Test
    fun toolLabelsHasBrowserTools() {
        assertEquals("打开网页", ToolLabels(knownTools()).label("browser_navigate"))
        assertEquals("读网页", ToolLabels(knownTools()).label("browser_read_page"))
        assertEquals("读取网页数据", ToolLabels(knownTools()).label("browser_evaluate_js"))
        assertEquals("切换网页标签", ToolLabels(knownTools()).label("browser_tabs"))
        assertEquals("查看下载", ToolLabels(knownTools()).label("browser_downloads"))
        assertEquals("上传文件", ToolLabels(knownTools()).label("browser_upload"))
    }
}
