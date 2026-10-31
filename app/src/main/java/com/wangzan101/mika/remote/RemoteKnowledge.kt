package com.wangzan101.mika.remote

import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.core.memory.*
import com.wangzan101.mika.core.skills.*
import com.wangzan101.mika.core.assembly.knownToolNames
import com.wangzan101.mika.core.assembly.knownTools
import com.wangzan101.mika.core.tool.Verb
import org.json.JSONArray
import org.json.JSONObject

/** Uses the same stores and validation as Android's Memory & Skills screen. */
class RemoteKnowledge(private val app:MikaApp) {
    fun list():JSONObject {
        val rows=JSONArray()
        val store=app.memory.store()
        check(store.reloadIfNeeded()) { "unavailable" }
        store.items.value.forEach { rows.put(JSONObject().put("id",it.id).put("type","memory").put("title",it.kind.name).put("text",it.content).put("status",it.status.name)) }
        app.memory.skills().list().forEach { rows.put(JSONObject().put("id",it.name).put("type","skill").put("title",com.wangzan101.mika.core.knowledge.FrontMatter.parse(it.markdown).first["display"] as? String ?: it.name).put("text",it.markdown).put("status",it.status.name)) }
        return JSONObject().put("items",rows).put("memoryEnabled",app.memory.settings().switches.value.useMemory)
    }
    @Synchronized fun add(body:JSONObject):JSONObject {
        val id=body.getString("id");require(id.matches(Regex("[a-fA-F0-9-]{36}"))) { "invalid_id" }
        val text=body.getString("text").trim();require(text.isNotEmpty() && text.length<=8000) { "invalid_content" }
        return when(body.getString("type")) {
            "memory" -> {
                val store=app.memory.store();check(store.reloadIfNeeded()) { "unavailable" }
                val kind=MemoryKind.valueOf(body.optString("kind","fact"))
                val existing=store.items.value.firstOrNull { it.source.channel=="ios:$id" }
                if (existing!=null) {check(existing.content==text.replace(Regex("\\s+")," ") && existing.kind==kind) { "request_changed" };JSONObject().put("status",existing.status.name).put("id",existing.id)}
                else {
                    val added=store.add(kind,text,emptyList(),MemorySource("manual",atMs=System.currentTimeMillis(),channel="ios:$id")).getOrThrow()
                    JSONObject().put("status",added.item.status.name).put("id",added.item.id)
                }
            }
            "skill" -> {
                val title=body.getString("title").trim();val description=body.getString("description").trim()
                require(title.length in 1..100 && description.length in 1..300) { "invalid_description" }
                val name="ios-"+id.lowercase()
                val markdown=ManualSkill.markdown(name,title,description,text)
                val problem=SkillDrafter.validate(markdown,knownToolNames(),knownTools().nameOf(Verb.RunBundledScript))
                require(problem==null) {problem ?: "invalid_skill"}
                val skills=app.memory.skills();val existing=skills.get(name)
                if(existing!=null) check(existing.markdown==markdown) { "request_changed" }
                else skills.put(PersonalSkill(name,markdown,SkillStatus.trial,"ios",null,System.currentTimeMillis(),emptyList()))
                JSONObject().put("status",existing?.status?.name ?: "trial").put("id",name)
            }
            else -> error("invalid_type")
        }
    }
}
