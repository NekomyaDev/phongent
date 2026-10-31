package com.wangzan101.mika.remote

import android.content.Context
import com.wangzan101.mika.settings.ProviderStore
import com.wangzan101.mika.settings.ProviderConfig
import com.wangzan101.mika.core.model.*
import com.wangzan101.mika.core.chatgpt.ChatGptAuth
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Explicit debug-only probe: never exports tokens, account identity or conversation content. */
object ModelDiagnostics {
    fun run(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val result=JSONObject().put("status","testing")
            val file=File(context.filesDir,"model-diagnostics.json")
            fun save(){file.writeText(result.toString())}
            save()
            try {
                val store=ProviderStore.get(context)
                val config=store.configFor(ModelRole.Main) ?: error("no_main_model")
                result.put("model",config.model).put("kind",config.kind.name)
                val provider=store.providers.value.first{it.id==config.providerId}
                if (provider.kind==ProviderConfig.Kind.ChatGpt) {
                    fun session(label:String) {
                        val s=store.chatGptSession(provider.id)
                        result.put(label,JSONObject().put("present",s!=null).put("signedIn",s?.signedIn==true)
                            .put("planScope",s?.planUsage==true).put("expiresInSeconds",((s?.expiresAtMs?:0)-System.currentTimeMillis())/1000)
                            .put("hasRefreshToken",!s?.refreshToken.isNullOrEmpty()))
                    }
                    session("before")
                    val listing=store.listModels(provider)
                    result.put("modelList",listing.javaClass.simpleName)
                    if (listing is ModelListResult.Failed) result.put("listFailure",listing.reason.name)
                    result.put("tokenRefresh",store.chatGptAuth.lastTokenDiagnostic)
                    session("after");save()
                    val s=store.chatGptSession(provider.id) ?: error("no_session")
                    val body=JSONObject().put("model",config.model).put("store",false).put("stream",true)
                        .put("instructions","Reply only OK.").put("input",org.json.JSONArray().put(JSONObject().put("role","user").put("content","ping")))
                    val http=OkHttpClient.Builder().readTimeout(40,TimeUnit.SECONDS).build()
                    http.newCall(Request.Builder().url(ChatGptAuth.API_BASE+"/responses").header("Authorization","Bearer "+s.accessToken)
                        .header("Accept","text/event-stream").post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { response ->
                        result.put("http",response.code)
                        val text=response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val error=runCatching{JSONObject(text).optJSONObject("error")}.getOrNull()
                            val code=error?.optString("code").orEmpty().ifBlank{error?.optString("type").orEmpty()}
                            result.put("errorCode",if(code.matches(Regex("[A-Za-z0-9_.-]{0,100}")))code else "redacted")
                        } else result.put("completed",text.contains("response.completed"))
                    }
                }
                try {
                    withTimeout(60_000) {store.modelFor(ModelRole.Main)!!.complete("Reply only OK.",listOf(Message(Role.User,listOf(Part.Text("ping")),Author.Mika)),emptyList())}
                    result.put("taskModelProbe","success")
                } catch(e:Exception){result.put("taskModelProbe",e.javaClass.simpleName)}
                result.put("status","done")
            }catch(e:Exception){result.put("status","failed").put("errorType",e.javaClass.simpleName)}
            finally {save()}
        }
    }
}
