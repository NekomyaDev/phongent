package com.wangzan101.mika.remote

import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.cap.sandbox.SandboxDirs
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import android.util.Base64

/** All file access uses Mika's descriptor-based sandbox gate, including directory traversal. */
class RemoteWorkspace(private val app:MikaApp){
    private val dirs=SandboxDirs(app)
    fun request(body:JSONObject):JSONObject=runBlocking{dirs.tree.shared{
        val conversation=body.getString("conversationId")
        require(conversation==app.chat.ui.value.conversationId || com.wangzan101.mika.chat.ConversationStore.shared(app).list().any{it.id==conversation}){"conversation_not_found"}
        val root=dirs.workspace(conversation);root.mkdirs()
        val path=body.optString("path").trim('/')
        require(path.length<=1024 && path.split('/').size<=16)
        when(body.getString("action")){
            "list"->{val names=dirs.gate.list(root,path).sorted();val rows=names.take(1000).mapNotNull{name->val relative=if(path.isEmpty())name else "$path/$name"
                runCatching{dirs.gate.openRead(root,relative).use{JSONObject().put("name",name).put("path",relative).put("directory",false).put("size",it.size)}}.getOrElse{runCatching{dirs.gate.list(root,relative);JSONObject().put("name",name).put("path",relative).put("directory",true).put("size",0)}.getOrNull()}
            };JSONObject().put("items",JSONArray(rows)).put("truncated",names.size>1000)}
            "read"->dirs.gate.openRead(root,path).use{file->require(file.size<=8*1024*1024){"file_too_large"};JSONObject().put("name",path.substringAfterLast('/')).put("data",Base64.encodeToString(file.input().readLimited(8*1024*1024),Base64.NO_WRAP))}
            "upload"->{
                val name=body.getString("name");require(name.isNotBlank() && name.length<=200 && name !in setOf(".","..") && !name.contains('/') && !name.contains('\\') && !name.contains('\u0000')){"invalid_name"}
                val id=body.getString("id");require(id.matches(Regex("[A-Za-z0-9-]{8,80}")))
                val prefs=app.getSharedPreferences("workspace-uploads",0);val key="$conversation:$id"
                val existing=prefs.getString(key,null)
                if(existing!=null)JSONObject().put("path",existing) else{
                    val encoded=body.getString("data");require(encoded.length<=12*1024*1024)
                    val bytes=Base64.decode(encoded,Base64.DEFAULT);require(bytes.size<=8*1024*1024);require(root.usableSpace>bytes.size+64L*1024*1024){"storage_full"}
                    val (actual,file)=dirs.gate.createNumbered(root,"inbox",name)
                    file.use{it.output().write(bytes)}
                    val relative="inbox/$actual";check(prefs.edit().putString(key,relative).commit());JSONObject().put("path",relative)
                }
            }
            "delete"->{dirs.gate.delete(root,path);JSONObject().put("status","deleted")}
            else->error("unknown_action")
        }
    }}
}
