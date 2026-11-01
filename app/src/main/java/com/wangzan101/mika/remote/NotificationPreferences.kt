package com.wangzan101.mika.remote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object NotificationPreferences {
    val defaults=setOf("com.tencent.mm","com.tencent.mobileqq","com.xingin.xhs")
    private fun prefs(context:Context)=context.getSharedPreferences("notification-sources",0)
    fun selected(context:Context):Set<String> = prefs(context).getStringSet("packages",defaults)?.toSet() ?: defaults
    @Synchronized fun set(context:Context,packageName:String,enabled:Boolean){
        val values=selected(context).toMutableSet()
        if(enabled)values.add(packageName) else values.remove(packageName)
        prefs(context).edit().putStringSet("packages",values).commit()
    }
    fun apps(context:Context):JSONArray {
        val selected=selected(context)
        val pm=context.packageManager
        val applications=pm.getInstalledApplications(0).associateBy{it.packageName}
        val names=mapOf("com.tencent.mm" to "微信","com.tencent.mobileqq" to "QQ","com.xingin.xhs" to "小红书")
        return JSONArray((applications.keys+selected+defaults).filter{it!=context.packageName}.map{key->
            JSONObject().put("package",key).put("name",names[key] ?: applications[key]?.let{pm.getApplicationLabel(it).toString()} ?: key).put("enabled",key in selected)
        }.sortedBy{it.getString("name")})
    }
}
