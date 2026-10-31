package com.wangzan101.mika.core.skills

import kotlinx.serialization.json.JsonPrimitive

/** Manual skill metadata is quoted so input cannot inject additional front-matter fields. */
object ManualSkill {
    fun markdown(name:String,title:String,description:String,body:String):String {
        require(PersonalSkills.NAME.matches(name))
        require(title.trim().length in 1..100 && description.trim().length in 1..300)
        return "---\nname: $name\ndisplay: ${JsonPrimitive(title.trim())}\ndescription: ${JsonPrimitive(description.trim())}\n---\n\n${body.trim()}"
    }
}
