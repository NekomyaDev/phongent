package com.wangzan101.mika.mcp

import android.content.Context
import android.content.Intent
import android.util.Log
import com.wangzan101.mika.BuildConfig
import com.wangzan101.mika.core.mcp.McpAccess
import com.wangzan101.mika.core.mcp.McpActivity
import com.wangzan101.mika.core.mcp.McpHost
import com.wangzan101.mika.core.mcp.RemoteTasks
import com.wangzan101.mika.settings.McpSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/** 电脑连接此刻的样子:关着、在监听哪个端口、或没能开起来(端口被占等)。 */
sealed interface McpServerState {
    data object Off : McpServerState
    data class Listening(val port: Int) : McpServerState
    data class Failed(val message: String) : McpServerState
}

/**
 * 电脑连接(MCP)的开关与服务:整个进程一份。开关打开时起 [McpService](前台服务,让进程在后台也活着),
 * 服务在的时候才监听端口;关掉开关就停服务、关端口。任务经 [tasks] 交给任务服务。
 */
class McpRuntime(private val context: Context, val settings: McpSettings, private val tasks: RemoteTasks) {
    private val _state = MutableStateFlow<McpServerState>(McpServerState.Off)
    val state: StateFlow<McpServerState> = _state.asStateFlow()
    @Volatile private var host: McpHost? = null

    /** 最近连过的客户端和最后一次请求;没在监听时为空。 */
    val activity: McpActivity get() = host?.activity?.value ?: McpActivity()

    /** 设置页的开关:存下来,打开就起前台服务,关掉就停。 */
    fun setEnabled(on: Boolean) {
        settings.setEnabled(on)
        if (on) ensureRunning() else {
            context.stopService(Intent(context, McpService::class.java))
            _state.value = McpServerState.Off
        }
    }

    /**
     * 开关开着就把服务起来(已经在跑没有影响)。从后台起前台服务可能被系统拒绝,这时记成没开起来,
     * 等下次打开 Mika 的界面再起。
     */
    fun ensureRunning() {
        if (!settings.enabled.value) return
        runCatching { context.startForegroundService(Intent(context, McpService::class.java)) }
            .onFailure { e ->
                Log.w(TAG, "could not start computer access service", e)
                _state.value = McpServerState.Failed(e.message ?: e::class.simpleName.orEmpty())
            }
    }

    /** 由 [McpService] 调:开始监听。开过了不再开;开不起来返回 false。 */
    @Synchronized internal fun startServer(): Boolean {
        if (host != null) return true
        val h = McpHost(tasks, settings::token, settings.port, allowAllInterfaces = true, serverVersion = BuildConfig.VERSION_NAME,
            onError = { Log.w(TAG, "computer access server error", it) })
        return try {
            h.start()
            host = h
            _state.value = McpServerState.Listening(h.boundPort)
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not listen on port ${settings.port}", e)
            _state.value = McpServerState.Failed(e.message ?: e::class.simpleName.orEmpty())
            false
        }
    }

    @Synchronized internal fun stopServer() {
        host?.stop()
        host = null
        if (_state.value is McpServerState.Listening) _state.value = McpServerState.Off
    }

    /** 换令牌:旧的当场失效。 */
    fun regenerateToken(): String = settings.regenerate()

    companion object {
        private const val TAG = "MikaMcp"

        /** 电脑能连的地址:开着的网卡上的局域网、私有组网 IPv4 地址。 */
        fun addresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress && McpAccess.privateAddress(it) }
                .mapNotNull { it.hostAddress }
                .distinct()
        }.getOrDefault(emptyList())

        fun url(address: String, port: Int) = "http://$address:$port${McpAccess.PATH}"

        /** 给电脑上的 MCP 客户端用的配置片段:地址加 Authorization 头。 */
        fun clientConfig(url: String, token: String): String = """
            {
              "mcpServers": {
                "mika": {
                  "type": "http",
                  "url": "$url",
                  "headers": { "Authorization": "Bearer $token" }
                }
              }
            }
        """.trimIndent()
    }
}
