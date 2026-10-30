package com.wangzan101.mika.ui

import com.wangzan101.mika.core.model.ListFailure
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.i18n.ChineseTexts
import com.wangzan101.mika.settings.AesGcmKeyCipher
import com.wangzan101.mika.settings.ProviderConfig
import com.wangzan101.mika.settings.ProviderPresets
import com.wangzan101.mika.settings.ProviderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

/**
 * 「测试并保存」走真的列模型接口和适配器:自定义的 OpenAI 兼容接口,对着一个按路径回话的本地服务。
 * 列表接口 404 时分得出「这家真不提供列表」(手填的模型调得通)和「地址、接口类型填错了」(调用也 404)。
 */
class ModelSetupNetworkTest {
    @get:Rule val texts = ChineseTexts()

    @get:Rule val tmp = TemporaryFolder()

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 路径 → 回什么;没写的路径回 404 网页。 */
    private val routes = mutableMapOf<String, () -> MockResponse>()

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                routes[request.path?.substringBefore('?')]?.invoke()
                    ?: MockResponse().setResponseCode(404).setHeader("Content-Type", "text/html").setBody("<html><body>Not Found</body></html>")
        }
        server.start()
    }

    @After fun stop() {
        scope.cancel()
        server.shutdown()
    }

    private val base get() = server.url("/").toString().trimEnd('/')

    private fun toolCall() = MockResponse().setHeader("Content-Type", "application/json").setBody(
        """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"c1","type":"function",""" +
            """"function":{"name":"get_time","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""",
    )

    private fun setUp(): Pair<ProviderStore, ModelSetupViewModel> {
        val store = ProviderStore(tmp.root, AesGcmKeyCipher { key }, userAgent = "Mika/1.2.3")
        val vm = ModelSetupViewModel(store, scope, io = Dispatchers.IO)
        vm.selectPreset(ProviderPresets.custom.label)
        vm.setKind(ProviderConfig.Kind.OpenAiCompat)
        vm.setBaseUrl(base)
        vm.setApiKey("sk")
        return store to vm
    }

    private fun ModelSetupViewModel.testAndWait() = runBlocking {
        testAndSave()
        withTimeout(15_000) { state.first { it.form.test != ListState.Running } }
    }

    @Test fun listAndChatBoth404MeansNothingIsSaved() {
        val (store, vm) = setUp()
        vm.testAndWait()
        assertTrue("先要一个模型名", vm.state.value.form.noList)
        assertTrue(store.providers.value.isEmpty())
        vm.setModel("gemini")
        vm.testAndWait()
        val failed = vm.state.value.form.test as ListState.Failed
        assertTrue(failed.message, failed.message.startsWith("列不出模型，用「gemini」试调用也说找不到：地址或接口类型可能填错了"))
        assertTrue(store.providers.value.isEmpty())
        assertTrue(store.list().isEmpty())
    }

    @Test fun list404ButChatWorksSavesANoListProvider() {
        routes["/chat/completions"] = ::toolCall
        val (store, vm) = setUp()
        vm.testAndWait()
        vm.setModel("my-model")
        vm.testAndWait()
        val p = store.providers.value.single()
        assertEquals(emptyList<Any>(), p.models)
        assertEquals(setOf("my-model"), p.verifiedModels)
        assertEquals("my-model", store.configFor(ModelRole.Main)?.model)
        assertFalse(vm.state.value.showForm)
        // 记下的「核实过」重新读入后还在
        assertEquals(setOf("my-model"), ProviderStore(tmp.root, AesGcmKeyCipher { key }).provider(p.id)?.verifiedModels)
        assertEquals(listOf("/models", "/models", "/chat/completions"), List(server.requestCount) { server.takeRequest().path })
    }

    @Test fun badKeyIsAKeyErrorAndNothingIsSaved() {
        routes["/models"] = { MockResponse().setResponseCode(401).setBody("""{"error":{"message":"invalid api key"}}""") }
        val (store, vm) = setUp()
        vm.testAndWait()
        assertEquals(ListState.Failed(ListFailure.Auth.text), vm.state.value.form.test)
        assertFalse("Key 不对不要模型名", vm.state.value.form.noList)
        assertTrue(store.providers.value.isEmpty())
    }
}
