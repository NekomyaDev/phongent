package com.wangzan101.mika.remote

import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.run.Attendance
import com.wangzan101.mika.task.TaskService
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.json.JSONArray
import java.net.ServerSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Shared command handler. LAN transport is plain HTTP over TCP and requires explicit device approval. */
class RemoteBridge(private val app: MikaApp) {
    private val chatRevision = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
    private val attachments by lazy { RemoteAttachments(app) }
    private val automation by lazy { RemoteAutomation(app) }
    private val models by lazy { RemoteModels(app) }
    private val settings by lazy { RemoteSettings(app) }
    private val knowledge by lazy { RemoteKnowledge(app) }
    private val prefs = app.getSharedPreferences("remote-commands", 0)
    private val handler = Handler(Looper.getMainLooper())
    private val token: String = File(app.filesDir, "remote-token").let { f ->
        if (f.exists()) f.readText().trim() else ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }.also { f.writeText(it) }
    }
    fun start() = serve(ServerSocket(18765, 8, InetAddress.getByName("127.0.0.1")))
    fun serve(server: ServerSocket, lan: LanAccess? = null) {
        val stateObserver = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        stateObserver.launch { app.taskState.state.collect { BridgeEvents.signal() } }
        stateObserver.launch { app.chat.ui.collect { VirtualVideo.frame(app);BridgeEvents.signal() } }
        stateObserver.launch { app.taskState.frames.collect { VirtualVideo.frame(app) } }
        stateObserver.launch { com.wangzan101.mika.chat.ConversationStore.shared(app).changes.collect { chatRevision.incrementAndGet(); BridgeEvents.signal() } }
        val workers = java.util.concurrent.ThreadPoolExecutor(16, 16, 0L, TimeUnit.MILLISECONDS,
            java.util.concurrent.ArrayBlockingQueue<Runnable>(16))
        Thread({
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: java.io.IOException) { break }
                try { workers.execute { socket.use { s ->
                    s.soTimeout = 4000
                    s.keepAlive = true
                    s.tcpNoDelay = true
                    try {
                        val input = s.getInputStream().buffered()
                        fun line(): String {
                            val bytes = java.io.ByteArrayOutputStream()
                            while (true) { val b = input.read(); require(b >= 0); if (b == 10) break
                                require(bytes.size() < 8192); if (b != 13) bytes.write(b) }
                            return bytes.toString("UTF-8")
                        }
                        val request = line().split(" ")
                        val headers = mutableMapOf<String, String>()
                        var count = 0
                        while (true) { val l = line(); if (l.isEmpty()) break; require(++count < 40)
                            headers[l.substringBefore(':').lowercase()] = l.substringAfter(':').trim() }
                        fun respond(code: Int, value: JSONObject) {
                            val data = value.toString().toByteArray(Charsets.UTF_8)
                            s.getOutputStream().apply { write("HTTP/1.1 $code Response\r\nContent-Type: application/json; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: ${data.size}\r\n\r\n".toByteArray()); write(data); flush() }
                        }
                        fun body(): JSONObject {
                            require(headers["transfer-encoding"] == null)
                            val length = headers["content-length"]?.toInt() ?: 0
                            require(length in 1..(if(request[1] in listOf("/attachments","/workspace")) 13*1024*1024 else if (request[1] in listOf("/knowledge","/routes","/plans")) 1048576 else 16384))
                            val bytes = ByteArray(length); var n = 0
                            while (n < length) { val r = input.read(bytes, n, length - n); require(r > 0); n += r }
                            return JSONObject(String(bytes, Charsets.UTF_8))
                        }
                        require(request.size == 3)
                        if (lan != null && request[0] == "POST" && request[1] == "/pair") {
                            val result = lan.request(body()); respond(result.first, result.second); return@use
                        }
                        if (lan != null && request[0] == "GET" && request[1].startsWith("/pair/")) {
                            respond(200, lan.poll(request[1].removePrefix("/pair/"))); return@use
                        }
                        val authorized = if (lan != null) lan.authorized(headers["authorization"] ?: "") else
                            MessageDigest.isEqual((headers["authorization"] ?: "").toByteArray(), "Bearer $token".toByteArray())
                        if (!authorized) { respond(401, JSONObject().put("error", "unauthorized")); return@use }
                        when {
                            request[0] == "GET" && request[1] == "/models/settings" -> respond(200,models.read())
                            request[0] == "POST" && request[1] == "/models/settings" -> {try{respond(200,models.update(body()))}catch(e:Exception){respond(422,JSONObject().put("error",e.message ?: "invalid_model_setting"))}}

                            request[0] == "POST" && request[1] == "/files" -> {
                                try{respond(200,attachments.download(body()))}catch(e:Exception){respond(422,JSONObject().put("error",e.message ?: "file_unavailable"))}
                            }

                            request[0] == "POST" && request[1] == "/attachments" -> {
                                try{respond(200,attachments.upload(body()))}catch(e:Exception){respond(422,JSONObject().put("error",e.message ?: "image_upload_failed"))}
                            }

                            request[0] == "GET" && request[1] == "/plans" -> respond(200,automation.plans())
                            request[0] == "GET" && request[1] == "/routes" -> respond(200,automation.routes())
                            request[0] == "POST" && request[1] in listOf("/plans","/routes") -> {
                                try{respond(200,if(request[1]=="/plans")automation.plan(body()) else automation.route(body()))}
                                catch(e:Exception){respond(422,JSONObject().put("error",e.message?.take(160) ?: "invalid_automation"))}
                            }

                            request[0] == "GET" && request[1] == "/browser" -> respond(200,RemoteBrowser(app).read())
                            request[0] == "POST" && request[1] == "/browser" -> {try{respond(200,RemoteBrowser(app).update(body()))}catch(e:Exception){respond(422,JSONObject().put("error",e.message ?: "browser_unavailable"))}}
                            request[0] == "GET" && request[1] == "/settings" -> respond(200,settings.read())
                            request[0] == "POST" && request[1] == "/settings" -> {try{respond(200,settings.patch(body()))}catch(e:Exception){respond(422,JSONObject().put("error",e.message ?: "invalid_settings"))}}
                            request[0] == "POST" && request[1] == "/virtual/input" -> {
                                try{val data=body();check(runBlocking{withTimeout(4000){RemoteScreen.virtualInput(app,data,RemoteScreen.peer(headers["authorization"] ?: ""))}}){"input_rejected"};respond(200,JSONObject().put("status","accepted"))}
                                catch(e:Exception){respond(409,JSONObject().put("error",e.message ?: "input_rejected"))}
                            }
                            request[0] == "GET" && request[1] == "/virtual/status" -> respond(200,VirtualVideo.status(app,RemoteScreen.peer(headers["authorization"] ?: "")))
                            request[0] == "GET" && request[1] == "/virtual/video" -> {
                                if(VirtualVideo.frame(app)==null){respond(409,JSONObject().put("error","virtual_screen_closed"));return@use}
                                val out=java.io.DataOutputStream(s.getOutputStream())
                                out.writeBytes("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n");out.flush()
                                VirtualVideo.serve(app,out)
                            }
                            request[0] == "GET" && request[1] == "/video/status" -> respond(200,VideoStream.status())
                            request[0] == "POST" && request[1] == "/video/start" -> {VideoStream.request(app);respond(200,VideoStream.status())}
                            request[0] == "GET" && request[1] == "/video" -> {
                                if(!VideoStream.active || VideoStream.viewers.size>=2){respond(409,JSONObject().put("error","video_not_ready"));return@use}
                                val queue=java.util.concurrent.ArrayBlockingQueue<ByteArray>(12)
                                VideoStream.viewers.add(queue);VideoStream.lastViewer=android.os.SystemClock.elapsedRealtime()
                                try {
                                    val output=java.io.DataOutputStream(s.getOutputStream())
                                    output.writeBytes("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n")
                                    VideoStream.config?.let{output.writeInt(it.size);output.write(it)}
                                    VideoStream.requestKeyframe?.invoke();output.flush()
                                    while(VideoStream.active){val packet=queue.poll(3,TimeUnit.SECONDS) ?: continue;if(packet.isEmpty())break;output.writeInt(packet.size);output.write(packet);output.flush()}
                                }finally{VideoStream.viewers.remove(queue)}
                            }
                            request[0] == "POST" && request[1] == "/screen/input" -> {
                                try {
                                    val data=body()
                                    check(!app.taskState.busy.value) {"agent_busy"}
                                    val ok=runBlocking {withTimeout(4000) {RemoteScreen.input(data)}}
                                    check(ok) {"input_rejected"}
                                    respond(200,JSONObject().put("status","accepted"))
                                } catch(e:Exception) {respond(409,JSONObject().put("error",e.message ?: "input_rejected"))}
                            }
                            request[0] == "GET" && request[1] == "/knowledge" -> {
                                try { respond(200,knowledge.list()) } catch (_: Exception) {respond(503,JSONObject().put("error","knowledge_unavailable"))}
                            }
                            request[0] == "POST" && request[1] == "/knowledge" -> {
                                try {respond(200,knowledge.add(body()))}
                                catch (e: Exception) {respond(422,JSONObject().put("error",if(e is com.wangzan101.mika.core.memory.MemoryRejected) e.code else e.message?.take(100) ?: "knowledge_rejected"))}
                            }
                            request[0] == "GET" && request[1] == "/chats" -> respond(200, chats())
                            request[0] == "GET" && request[1].startsWith("/chat/") -> {
                                val conversation = request[1].removePrefix("/chat/")
                                require(conversation.matches(Regex("[A-Za-z0-9-]{8,80}")))
                                val turns = com.wangzan101.mika.chat.ConversationStore.shared(app).turns(conversation)
                                val messages = JSONArray()
                                turns.takeLast(100).forEachIndexed { index, turn ->
                                    val shown = app.taskState.shownRun?.takeIf { it.conversationId == conversation && turn.runId != null && it.runId.value == turn.runId }
                                    val key = shown?.key ?: turn.runId ?: "$conversation-$index"
                                    messages.put(JSONObject().put("id", "${shown?.mcpExternalId ?: key}-user").put("role", "user").put("text", turn.userText).put("status", "已接收").put("time", 0))
                                    turn.steers.forEachIndexed { n, text -> messages.put(JSONObject().put("id", "$key-steer-$n").put("role", "user").put("text", text).put("status", "已接收").put("time", 0)) }
                                    messages.put(JSONObject().put("id", "$key-agent").put("role", "agent").put("text", turn.outcome).put("status", when (turn.status) { "succeeded" -> "任务完成"; "failed" -> "执行失败"; "cancelled" -> "已停止"; else -> "待核实" }).put("time", 0))
                                }
                                respond(200, JSONObject().put("messages", messages).put("exchanges",NativeChat.exchanges(app,conversation)))
                            }
                            request[0] == "GET" && request[1].startsWith("/events?") -> {
                                val heartbeat = (android.net.Uri.parse(request[1]).getQueryParameter("heartbeat")?.toLong() ?: 30).coerceIn(15, 120)
                                val out = s.getOutputStream()
                                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n".toByteArray())
                                var sequence = 0L
                                fun event(kind: String, data: JSONObject) {
                                    data.put("kind", kind).put("sequence", ++sequence)
                                    out.write(("data: " + data.toString() + "\n\n").toByteArray(Charsets.UTF_8)); out.flush()
                                }
                                var revision = BridgeEvents.current()
                                val initial = snapshot()
                                event("snapshot", JSONObject().put("snapshot", initial))
                                fun stateKey(value: JSONObject): String {
                                    val copy = JSONObject(value.toString()); copy.remove("messages"); copy.remove("updatedAt")
                                    return copy.toString()
                                }
                                var previousState = stateKey(initial)
                                fun records(value: JSONObject): Map<String, String> {
                                    val list = value.getJSONArray("messages")
                                    return (0 until list.length()).associate { val m = list.getJSONObject(it); m.getString("id") to m.toString() }
                                }
                                var previousMessages = records(initial)
                                while (!server.isClosed && !Thread.currentThread().isInterrupted) {
                                    revision = BridgeEvents.await(revision, heartbeat * 1000)
                                    val current = snapshot(); val key = stateKey(current); val messages = records(current)
                                    var sent = false
                                    if (key != previousState) {
                                        val state = JSONObject(current.toString()).put("messages", JSONArray())
                                        event("state", JSONObject().put("snapshot", state)); previousState = key; sent = true
                                    }
                                    val changed = messages.filter { (id, value) -> previousMessages[id] != value }
                                    val removed = previousMessages.keys - messages.keys
                                    if (changed.isNotEmpty() || removed.isNotEmpty()) {
                                        event("messages", JSONObject().put("upserts", JSONArray(changed.values.map { JSONObject(it) })).put("removed", JSONArray(removed.toList())))
                                        previousMessages = messages; sent = true
                                    }
                                    if (!sent) event("heartbeat", JSONObject())
                                }
                            }
                            request[0] == "GET" && request[1].startsWith("/messages?after=") -> {
                                val query = android.net.Uri.parse(request[1])
                                val after = query.getQueryParameter("after")!!.toLong()
                                val wait = query.getQueryParameter("wait")?.toLong() ?: 20
                                respond(200, MessageListener.changes(after, wait))
                            }
                            request[0] == "POST" && request[1] == "/notification/open" -> {
                                val id = body().getString("id")
                                try { MessageListener.open(id); respond(200, JSONObject().put("status", "dispatched")) }
                                catch (_: Exception) { respond(409, JSONObject().put("error", "notification_expired_or_blocked")) }
                            }
                            request[0] == "POST" && request[1] == "/workspace" -> {try{respond(200,RemoteWorkspace(app).request(body()))}catch(e:Exception){respond(422,JSONObject().put("error",e.message ?: "file_operation_failed"))}}
                            request[0] == "GET" && request[1] == "/usage" -> respond(200,RemoteDiagnostics(app).usage())
                            request[0] == "GET" && request[1] == "/diagnostics" -> respond(200,RemoteDiagnostics(app).diagnostics())
                            request[0] == "GET" && request[1] == "/tasks" -> respond(200,RemoteTaskQueue.get(app).list())
                            request[0] == "POST" && request[1] == "/tasks" -> {try{val command=body();val queue=RemoteTaskQueue.get(app);respond(200,if(command.optString("action")=="cancel")queue.cancel(command.getString("id")) else if(command.optString("action")=="acknowledge")queue.acknowledge(command.getString("id")) else if(command.optString("action")=="retry")queue.retry(command.getString("id")) else queue.enqueue(command))}catch(e:Exception){respond(409,JSONObject().put("error",e.message ?: "queue_failed"))}}
                            request[0] == "POST" && request[1] == "/device/name" -> {try{respond(200,DeviceIdentity.rename(app,body()))}catch(e:Exception){respond(422,JSONObject().put("error",e.message))}}
                            request[0] == "GET" && request[1] == "/state" -> respond(200, snapshot())
                            request[0] == "POST" && request[1] == "/command" -> synchronized(prefs) {
                                val command = body()
                                val id = command.getString("id"); require(id.matches(Regex("[A-Za-z0-9-]{8,80}")))
                                val action = command.getString("action")
                                require(action in setOf("task", "stop", "answer", "new_chat", "select_chat", "delete_chat"))
                                val text = command.optString("text").trim(); require(text.length <= 8000)
                                if (action in setOf("task", "answer")) require(text.isNotEmpty())
                                if (prefs.contains(id)) respond(200, JSONObject().put("id", id).put("status", prefs.getString(id, "uncertain")).put("conversationId", prefs.getString("conversation:$id", "")))
                                else {
                                    // Persist before dispatch: process death must never replay a possibly sent reply.
                                    check(prefs.edit().putString(id, "uncertain").commit())
                                    var error: String? = null
                                    val done = CountDownLatch(1)
                                    handler.post {
                                        try {
                                            if (action == "delete_chat") {
                                                val conversation = command.getString("conversationId")
                                                require(conversation.matches(Regex("[A-Za-z0-9-]{8,80}")))
                                                check(app.taskState.runs.value.none { it.conversationId == conversation }) { "agent_busy" }
                                                app.chat.deleteConversation(conversation)
                                                prefs.edit().putBoolean("deleted:$conversation", true).commit()
                                                chatRevision.incrementAndGet()
                                                BridgeEvents.signal()
                                            } else if (action == "select_chat") {
                                                val conversation = command.getString("conversationId")
                                                require(conversation.matches(Regex("[A-Za-z0-9-]{8,80}")))
                                                val known = knownChats()
                                                check(conversation in known) { "conversation_not_found" }
                                                app.chat.openConversation(conversation)
                                                check(app.chat.ui.value.conversationId == conversation) { "conversation_switch_blocked_by_running_task" }
                                                prefs.edit().putString("conversation:$id", conversation).commit()
                                                BridgeEvents.signal()
                                            } else if (action == "new_chat") {
                                                check(!app.taskState.busy.value) { "agent_busy" }
                                                val conversation = com.wangzan101.mika.chat.ConversationStore.shared(app).newConversation()
                                                app.chat.openConversation(conversation)
                                                check(app.chat.ui.value.conversationId == conversation) { "conversation_not_selected" }
                                                prefs.edit().putString("conversation:$id", conversation).commit()
                                                BridgeEvents.signal()
                                            } else if (action == "task") {
                                                check(!app.taskState.busy.value) { "agent_busy" }
                                                val selected = app.chat.ui.value.conversationId
                                                val expected = command.optString("conversationId")
                                                check(expected.isEmpty() || expected == selected) { "conversation_changed" }
                                                val conversation = selected ?: com.wangzan101.mika.chat.ConversationStore.shared(app).newConversation().also { app.chat.openConversation(it) }
                                                check(app.chat.ui.value.conversationId == conversation) { "conversation_not_selected" }
                                                prefs.edit().putString("conversation:$id", conversation).commit()
                                                TaskService.startMcp(app, text, conversation, id, Attendance.Reachable)
                                            } else {
                                                val key = command.getString("runId")
                                                val run = app.taskState.runs.value.firstOrNull { it.key == key } ?: error("stale_run")
                                                if (action == "stop") TaskService.cancel(app, key)
                                                else { check(run.state.value is TaskState.WaitingInput) { "not_waiting_for_input" }; TaskService.answer(app, text, key) }
                                            }
                                            prefs.edit().putString(id, "accepted").commit()
                                        } catch (e: Exception) { error = e.message ?: "dispatch_failed"; prefs.edit().putString(id, "failed").commit() }
                                        finally { done.countDown() }
                                    }
                                    done.await(3, TimeUnit.SECONDS)
                                    respond(if (error == null) 202 else 409, JSONObject().put("id", id).put("status", prefs.getString(id, "uncertain")).put("error", error ?: JSONObject.NULL).put("conversationId", prefs.getString("conversation:$id", "")))
                                }
                            }
                            else -> respond(404, JSONObject().put("error", "not_found"))
                        }
                    } catch (_: Exception) { /* Close malformed or incomplete requests without logging their body. */ }
                } } } catch (_: java.util.concurrent.RejectedExecutionException) { socket.close() }
            }
            workers.shutdownNow(); stateObserver.cancel()
        }, "mika-usb-bridge").apply { isDaemon = true; start() }
    }
    private fun knownChats(): Set<String> = (
        com.wangzan101.mika.chat.ConversationStore.shared(app).list().map { it.id } +
        prefs.all.filterKeys { it.startsWith("conversation:") }.values.filterIsInstance<String>() +
        listOfNotNull(app.chat.ui.value.conversationId) + app.taskState.runs.value.map { it.conversationId }
    ).filter { it.isNotBlank() && !prefs.getBoolean("deleted:$it", false) && it !in app.chat.deleted.value }.toSet()
    private fun chats(): JSONObject {
        val store = com.wangzan101.mika.chat.ConversationStore.shared(app)
        val summaries = store.list().associateBy { it.id }
        val values = knownChats().map { id ->
            val summary = summaries[id]
            val run = app.taskState.runs.value.firstOrNull { it.conversationId == id }
            val last = store.turns(id).lastOrNull()
            JSONObject().put("id", id).put("title", (summary?.title ?: run?.goal ?: "新聊天").take(100))
                .put("preview", (last?.outcome ?: run?.goal ?: "").take(160))
                .put("updatedAt", (summary?.updatedAt ?: 0) / 1000.0)
                .put("busy", run != null).put("needsAttention", run?.state?.value.let { it is TaskState.WaitingInput || it is TaskState.WaitingTakeover || it is TaskState.WaitingConfirmation })
        }
        return JSONObject().put("chats", JSONArray(values.sortedByDescending { it.optDouble("updatedAt") }))
    }
    fun snapshot(): JSONObject {
        val state = app.taskState.state.value
        val phase: String
        val detail: String
        when (state) {
            is TaskState.Running -> { phase = "running"; detail = state.narration ?: state.reason ?: state.note }
            is TaskState.WaitingInput -> { phase = "waiting"; detail = state.prompt }
            is TaskState.WaitingTakeover -> { phase = "takeover"; detail = state.message }
            is TaskState.WaitingConfirmation -> { phase = "confirmation"; detail = "请在安卓端确认操作" }
            is TaskState.Held -> { phase = "waiting"; detail = state.reason.why }
            is TaskState.Succeeded -> { phase = "completed"; detail = state.answer }
            is TaskState.Failed -> { phase = "failed"; detail = state.reason }
            TaskState.Cancelled -> { phase = "cancelled"; detail = "任务已停止" }
            TaskState.Idle -> { phase = "idle"; detail = "等待你的下一项任务" }
        }
        return JSONObject().put("phase", phase).put("detail", detail.take(8000))
            .put("runId", app.taskState.shownRun?.key ?: "")
            .put("goal", app.taskState.shownRun?.goal ?: "")
            .put("commandId", app.taskState.shownRun?.mcpExternalId ?: "")
            .put("conversationId", app.taskState.shownRun?.conversationId ?: "")
            .put("currentConversationId", app.chat.ui.value.conversationId ?: "")
            .put("exchanges",NativeChat.exchanges(app,app.chat.ui.value.conversationId ?: ""))
            .put("taskQueueVersion",1).put("queuedTasks",RemoteTaskQueue.get(app).status())
            .put("knowledgeVersion", 1).put("chatRoutingVersion", 3).put("chatRevision", chatRevision.get())
            .put("busy", app.taskState.busy.value).put("device", DeviceIdentity.name(app))
            .put("updatedAt", System.currentTimeMillis() / 1000.0)
            .put("messages", MessageListener.snapshot())
            .put("messageWaitConfig", true).put("eventStream", true)
            .put("notificationAccess", app.getSystemService(android.app.NotificationManager::class.java)
                .isNotificationListenerAccessGranted(android.content.ComponentName(app, MessageListener::class.java)))
            .put("notificationListenerConnected", MessageListener.connected)
    }
}
