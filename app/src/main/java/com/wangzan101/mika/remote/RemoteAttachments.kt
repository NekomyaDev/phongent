package com.wangzan101.mika.remote

import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.cap.sandbox.SandboxDirs
import com.wangzan101.mika.ui.AttachmentMessage
import android.util.Base64
import android.graphics.BitmapFactory
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.UUID

class RemoteAttachments(private val app:MikaApp) {
    fun download(body:JSONObject):JSONObject {
        val conversation=body.getString("conversationId")
        val exchange=body.getString("exchangeId")
        val rows=app.chat.remoteExchanges(conversation)
        val item=rows.mapIndexed{index,ex->(ex.runId ?: "$conversation-$index") to ex}.firstOrNull{it.first==exchange}?.second ?: error("exchange_not_found")
        val index=body.getInt("index");require(index in item.files.indices)
        val file=item.files[index];require(file.size in 1..8*1024*1024){"file_too_large"}
        val uri=android.net.Uri.parse(file.uri);require(uri.scheme=="content")
        val bytes=app.contentResolver.openInputStream(uri)?.use{it.readLimited(8*1024*1024)} ?: error("file_unavailable")
        require(bytes.size<=8*1024*1024)
        return JSONObject().put("name",file.name).put("data",Base64.encodeToString(bytes,Base64.NO_WRAP))
    }
    fun upload(body:JSONObject):JSONObject {
        val conversation=body.getString("conversationId")
        require(conversation.isNotBlank() && conversation==app.chat.ui.value.conversationId){"conversation_changed"}
        val encoded=body.getString("data");require(encoded.length<=12*1024*1024){"image_too_large"}
        val bytes=Base64.decode(encoded,Base64.DEFAULT);require(bytes.size in 1..8*1024*1024)
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        require(options.outWidth in 1..16384 && options.outHeight in 1..16384){"invalid_image"}
        val extension=when(options.outMimeType){"image/jpeg"->"jpg";"image/png"->"png";"image/webp"->"webp";else->error("unsupported_image")}
        // Retrying the same image does not create duplicate workspace files.
        val digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        val name="remote-$digest.$extension"
        val dirs=SandboxDirs(app)
        runBlocking{dirs.tree.shared{val workspace=dirs.workspace(conversation);workspace.mkdirs();require(workspace.usableSpace>bytes.size+64L*1024*1024){"storage_full"};dirs.gate.replace(workspace,"inbox/$name",bytes)}}
        return JSONObject().put("path","/workspace/inbox/$name").put("conversationId",conversation)
            .put("line",AttachmentMessage.line("/workspace/inbox/$name","${bytes.size} B"))
    }
}
