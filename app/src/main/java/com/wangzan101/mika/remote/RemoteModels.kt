package com.wangzan101.mika.remote

import android.content.Context
import com.wangzan101.mika.settings.ProviderStore
import com.wangzan101.mika.settings.ProviderConfig
import com.wangzan101.mika.settings.RoleChoice
import com.wangzan101.mika.core.model.*
import org.json.JSONObject
import org.json.JSONArray

class RemoteModels(context:Context){
    private val store=ProviderStore.get(context)
    fun read():JSONObject=JSONObject().put("roles",JSONObject().apply{ModelRole.entries.forEach{role->val config=store.configFor(role);put(role.name,config?.let{JSONObject().put("provider",it.name).put("model",it.model).put("reasoning",store.roles.value.reasoningEffort(role) ?: "")} ?: JSONObject.NULL)}})
        .put("providers",JSONArray(store.providers.value.map{p->JSONObject().put("id",p.id).put("name",p.name).put("kind",p.kind.name).put("baseURL",p.baseUrl).put("hasKey",store.hasKey(p.id)).put("models",JSONArray(p.models?.map{it.id} ?: emptyList<String>()))}))
    private fun connectProvider(body:JSONObject):JSONObject {
        val name=body.getString("name").trim();require(name.isNotEmpty() && name.length<=80)
        val kind=ProviderConfig.Kind.valueOf(body.getString("kind"))
        require(kind!=ProviderConfig.Kind.ChatGpt){"请在设备上完成 ChatGPT 登录"}
        val url=body.getString("baseURL").trim().trimEnd('/')
        val uri=java.net.URI(url);require(uri.scheme in setOf("http","https") && !uri.host.isNullOrBlank() && uri.userInfo==null){"服务地址无效"}
        val model=body.getString("model").trim();require(model.isNotEmpty() && model.length<=200)
        val key=body.optString("apiKey").trim().takeIf{it.isNotEmpty()}
        val existing=store.providers.value.firstOrNull{it.name==name && it.kind==kind && it.baseUrl.trimEnd('/')==url}
        val effectiveKey=key ?: existing?.let{store.providerKey(it.id)}
        val result=kotlinx.coroutines.runBlocking{kotlinx.coroutines.withTimeout(20000){store.probe(kind,url,effectiveKey,null,model,null,Capabilities(true,false,128000,false))}}
        when(result){
            is com.wangzan101.mika.settings.ProbeResult.Failed->error(result.error.message ?: "模型测试失败")
            com.wangzan101.mika.settings.ProbeResult.NoToolCall->error("模型未通过工具调用测试，未保存")
            else->{}
        }
        val listed=(existing?.models.orEmpty().filter{it.id!=model}+ListedModel(model,toolCalls=true))
        store.connect(name,kind,url,key,listed,verified=setOf(model))
        return read()
    }
    @Synchronized fun update(body:JSONObject):JSONObject {
        if(body.optString("action")=="connect_provider")return connectProvider(body)
        val role=ModelRole.valueOf(body.getString("role"))
        when(body.getString("action")){
            "inherit"->check(store.choose(role,RoleChoice.SameAsMain)){"invalid_role"}
            "off"->check(store.choose(role,RoleChoice.Off)){"invalid_role"}
            "reasoning"->check(store.setReasoningEffort(role,body.optString("effort").ifEmpty{null})){"unsupported_effort"}
            "select"->{
                val model=body.getString("model").trim();require(model.isNotEmpty())
                val provider=store.providers.value.firstOrNull{it.id==body.optString("providerId") || (it.name==body.optString("providerName") && it.baseUrl==body.optString("baseURL"))} ?: error("provider_not_found")
                val listed=provider.models?.firstOrNull{it.id==model}
                val caps=Capabilities(true,role==ModelRole.Vision,listed?.contextTokens ?: 128000,listed?.reasoning ?: false)
                check(store.use(role,provider.id,model,caps)){"model_selection_failed"}
            }
            else->error("unknown_action")
        }
        return read()
    }
}
