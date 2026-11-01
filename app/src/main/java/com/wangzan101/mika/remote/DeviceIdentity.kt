package com.wangzan101.mika.remote

import android.content.Context
import android.os.Build
import org.json.JSONObject

object DeviceIdentity {
    fun name(context:Context):String = context.getSharedPreferences("fleet-controller",0).getString("name:local",null) ?: Build.MODEL
    fun rename(context:Context,body:JSONObject):JSONObject {
        val name=body.getString("name").trim()
        require(name.isNotEmpty() && name.length<=40 && name.none{it.isISOControl()}){"invalid_device_name"}
        check(context.getSharedPreferences("fleet-controller",0).edit().putString("name:local",name).commit()){"name_save_failed"}
        BridgeEvents.signal()
        return JSONObject().put("name",name)
    }
}
