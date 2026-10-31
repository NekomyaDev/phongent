package com.wangzan101.mika.core.skills

import com.wangzan101.mika.core.knowledge.FrontMatter
import org.junit.Assert.*
import org.junit.Test

class ManualSkillTest {
    private val body=SkillDrafter.SECTIONS.joinToString("\n\n") { "## $it\n按照用户指定的目标操作，结果不确定时停止。" }
    @Test fun validManualSkillRetainsDisplayAndValidates() {
        val md=ManualSkill.markdown("ios-test","整理通知","整理当前应用的通知",body)
        assertEquals("整理通知",FrontMatter.parse(md).first["display"])
        assertNull(SkillDrafter.validate(md,emptySet(),null))
    }
    @Test fun titleCannotReplaceNameAndIncompleteSkillIsRejected() {
        val md=ManualSkill.markdown("ios-test","标题\nname: malicious","用途",body)
        assertEquals("ios-test",FrontMatter.parse(md).first["name"])
        assertEquals("missing_section",SkillDrafter.validate(ManualSkill.markdown("ios-test","标题","用途","随便做"),emptySet(),null))
    }
    @Test fun unavailableToolIsRejected() {
        val md=ManualSkill.markdown("ios-test","标题","用途",body+"\n`unknown_tool`")
        assertEquals("unknown_tool",SkillDrafter.validate(md,emptySet(),null))
    }
}
