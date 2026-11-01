package com.wangzan101.mika.remote

import android.content.Context
import com.wangzan101.mika.settings.FeatureFlags
import com.wangzan101.mika.core.cap.phone.OperatingPace
import com.wangzan101.mika.capsule.CapsuleMode
import org.json.JSONObject

/** Shared by local Android controls and remote controllers. Only explicit fields change. */
class RemoteSettings(context: Context) {
    private val app=context.applicationContext as com.wangzan101.mika.MikaApp
    private val flags=FeatureFlags.get(context)
    private val speedKeys=setOf("inlineScreen","batchSteps","replay","deepLinks","lightSteps","parallelTasks","structuredCompaction","adaptiveVision")
    fun read()=JSONObject().put("version",1).put("mcpLan",app.mcp.settings.enabled.value).put("mcpUsb",app.mcp.usb.enabled.value)
        .put("keepScreenOn",flags.keepScreenOn()).put("muteTarget",flags.muteTarget())
        .put("parallelMax",flags.parallelMax()).put("operatingPace",flags.operatingPace().name)
        .put("capsuleMode",flags.capsuleMode().name).put("notificationApps",NotificationPreferences.apps(app))
        .put("paceOptions",org.json.JSONArray(OperatingPace.entries.map{it.name}))
        .put("capsuleOptions",org.json.JSONArray(CapsuleMode.entries.map{it.name}))
        .also{result->val enabled=flags.speedFlags().describe().split(",").toSet();speedKeys.forEach{result.put(it,it in enabled)}}
    @Synchronized fun patch(body:JSONObject):JSONObject {
        val allowed=setOf("mcpLan","mcpUsb","keepScreenOn","muteTarget","parallelMax","operatingPace","capsuleMode","notificationApp")+speedKeys
        require(body.keys().asSequence().all{it in allowed}){"unknown_setting"}
        // Validate the complete patch before changing any setting.
        val lan=if(body.has("mcpLan"))body.get("mcpLan").also{require(it is Boolean)} as Boolean else null
        val usb=if(body.has("mcpUsb"))body.get("mcpUsb").also{require(it is Boolean)} as Boolean else null
        val keep=if(body.has("keepScreenOn"))body.get("keepScreenOn").also{require(it is Boolean)} as Boolean else null
        val mute=if(body.has("muteTarget"))body.get("muteTarget").also{require(it is Boolean)} as Boolean else null
        val parallel=if(body.has("parallelMax"))body.getInt("parallelMax").also{require(it in 1..3)} else null
        val pace=if(body.has("operatingPace"))OperatingPace.valueOf(body.getString("operatingPace")) else null
        val capsule=if(body.has("capsuleMode"))CapsuleMode.valueOf(body.getString("capsuleMode")) else null
        val notification=if(body.has("notificationApp"))body.getJSONObject("notificationApp").also{
            require(it.getString("package").matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))
            require(it.get("enabled") is Boolean)
        } else null
        val speedPatch=speedKeys.filter{body.has(it)}.associateWith{body.get(it).also{value->require(value is Boolean)} as Boolean}
        val speed=if(speedPatch.isEmpty())null else {
            val enabled=flags.speedFlags().describe().split(",").filter{it in speedKeys}.toMutableSet()
            speedPatch.forEach{(key,value)->if(value)enabled.add(key) else enabled.remove(key)}
            com.wangzan101.mika.core.agent.SpeedFlags.parse(enabled.joinToString(","))
        }
        notification?.let{NotificationPreferences.set(app,it.getString("package"),it.getBoolean("enabled"))}
        speed?.let(flags::setSpeed)
        keep?.let(flags::setKeepScreenOn);mute?.let(flags::setMuteTarget)
        parallel?.let(flags::setParallelMax);pace?.let(flags::setOperatingPace);capsule?.let(flags::setCapsuleMode)
        lan?.let(app.mcp::setEnabled);usb?.let(app.mcp::setUsbEnabled)
        return read()
    }
}
