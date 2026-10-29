package com.wangzan101.mika.core.record

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.wangzan101.mika.core.testing.verb

class RedactorTest {
    @Test fun passwordFieldTextMasked() {
        val out = Redactor.args(verb("phone_type_text"), """{"text":"hunter2","ref":"@e3","snapshotId":"s1"}""", secretField = true)
        val o = Json.parseToJsonElement(out).jsonObject
        assertEquals("***", o["text"]!!.jsonPrimitive.content)
        assertEquals("@e3", o["ref"]!!.jsonPrimitive.content)
        assertFalse(out.contains("hunter2"))
    }

    @Test fun apiKeyLikeStringsMasked() {
        val key = "sk-" + "a1B2c3D4e5F6g7H8i9J0k1L2"
        val t = Redactor.text("key is $key and header Authorization: Bearer abc.DEF-123_xyz done")
        assertFalse(t.contains(key))
        assertFalse(t.contains("abc.DEF-123_xyz"))
        assertTrue(t.contains("***"))
        assertTrue(t.endsWith(" done"))
        val google = "AIza" + "SyA1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6"
        val gh = "ghp_" + "abcdefghijklmnopqrstuvwxyz0123456789"
        val t2 = Redactor.text("g=$google h=$gh")
        assertFalse(t2.contains(google)); assertFalse(t2.contains(gh))
        // 不是密码框,但参数里有 Key 样子的串也要遮
        val a = Redactor.args(verb("phone_type_text"), """{"text":"$key"}""", secretField = false)
        assertFalse(a.contains(key))
    }

    @Test fun ordinaryTextKept() {
        val s = "打开设置,搜索 sky-blue 主题 task-1234 Bearer"
        assertEquals(s, Redactor.text(s))
        val args = """{"text":"你好","pressEnter":true}"""
        assertEquals("你好", Json.parseToJsonElement(Redactor.args(verb("phone_type_text"), args, secretField = false)).jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("""{"ref":"@e1","snapshotId":"s"}""", Redactor.args(verb("phone_tap"), """{"ref":"@e1","snapshotId":"s"}""", secretField = false))
    }

    private fun scriptCode(code: String): String {
        val args = """{"code":${kotlinx.serialization.json.JsonPrimitive(code)}}"""
        return Json.parseToJsonElement(Redactor.args(verb("web_script"), args, secretField = false)).jsonObject["code"]!!.jsonPrimitive.content
    }

    @Test fun scriptStringLiteralsAllMasked() {
        // 不管字怎么交出去:直接给 mika.type、先存变量、拼接、写进 DOM
        val c = scriptCode("const s = await mika.snapshot();\nawait mika.type(find(s, '密码'), 'hunter2');\nconst pw = \"abc\" + 'def';\n" +
            "document.querySelector('#p').value = pw;\nawait mika.type(s.elements[0], `x${'$'}{pw}y${'$'}{'zz'}`);")
        for (secret in listOf("hunter2", "abc", "def", "密码", "#p", "zz", "`x")) assertFalse(secret, secret in c)
        // 只留形状:引号种类、模板里表达式的位置、代码本身
        assertTrue(c, "mika.type(find(s, '***'), '***')" in c)
        assertTrue(c, "\"***\" + '***'" in c)
        assertTrue(c, "`***${'$'}{pw}***${'$'}{'***'}***`" in c)
        assertEquals("const a = '';", scriptCode("const a = '';"))
    }

    @Test fun scriptEscapedQuotesStayInsideLiteral() {
        val c = scriptCode("const a = 'it\\'s hunter2'; const b = \"say \\\"hi\\\"\";")
        assertEquals("const a = '***'; const b = \"***\";", c)
    }
}
