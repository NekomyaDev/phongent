package com.wangzan101.mika.remote

import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.cap.web.WebSites
import com.wangzan101.mika.cap.web.WebHandover
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray

class RemoteBrowser(private val app:MikaApp){
    fun read():JSONObject=JSONObject().put("sites",JSONArray(WebSites.registry(app).list().map{
        JSONObject().put("host",it.host).put("lastVisitMs",it.lastVisitMs).put("loggedIn",it.loggedInByUser).put("challenged",it.challenged(System.currentTimeMillis()))
    }))
    fun update(body:JSONObject):JSONObject=runBlocking{withContext(Dispatchers.Main){
        check(!app.taskState.busy.value){"agent_busy"}
        check(WebHandover.session.value==null){"browser_login_busy"}
        when(body.getString("action")){
            "open"->{val host=host(body);check(WebHandover.showLogin(app,host)){"browser_login_busy"}}
            "logout"->{val host=host(body);require(WebSites.registry(app).list().any{it.host==host}){"site_not_found"};WebSites.login(app).logout(host)}
            "clear"->WebSites.logoutAll(app)
            else->error("unknown_action")
        }
        read().put("status",if(body.getString("action")=="open")"已请求在设备打开登录页面" else "已清理网页数据，部分站点可能仍需手动退出")
    }}
    private fun host(body:JSONObject):String {
        val value=body.getString("host").trim().lowercase()
        val uri=java.net.URI("https://$value/")
        require(!uri.host.isNullOrBlank() && uri.host==value && uri.userInfo==null && uri.port == -1){"invalid_site"}
        return value
    }
}
