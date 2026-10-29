package com.wangzan101.mika.core.model

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 服务商列出的一个模型。能力字段为 null 表示接口没给,由调用方沿用自己的初值,这里不猜。
 */
data class ListedModel(
    val id: String,
    val displayName: String = "",
    val toolCalls: Boolean? = null,
    val vision: Boolean? = null,
    val contextTokens: Int? = null,
    val reasoning: Boolean? = null,
)

/** 列模型为什么失败;[text] 是给人看的原因。 */
enum class ListFailure(val text: String) {
    BadUrl("接口地址不对,要以 http:// 或 https:// 开头"),
    Auth("API Key 无效或没有权限"),
    Quota("账户额度已用完"),
    RateLimited("请求太频繁,请稍后再试"),
    Network("连不上这个地址,请检查网络和接口地址"),
    NotSupported("这个服务不提供模型列表"),
    Server("服务暂时出错,请稍后再试"),
    BadResponse("返回的内容看不懂,请检查接口地址和接口类型"),
}

sealed interface ModelListResult {
    data class Ok(val models: List<ListedModel>) : ModelListResult
    data class Failed(val reason: ListFailure, val detail: String = "") : ModelListResult {
        val message: String get() = if (detail.isEmpty()) reason.text else "${reason.text}($detail)"
    }
}

/**
 * 用各家公开的「列出模型」接口取可用模型:
 * - OpenAI 兼容:`GET {base}/models`(Bearer)。只给 id 的照实返回;带 `context_length`、
 *   `architecture.input_modalities`、`supported_parameters` 等字段的(部分聚合服务会给)才填能力。
 * - Anthropic:`GET {base}/v1/models`,按 `after_id` 翻页;`max_input_tokens` 是上下文,
 *   `capabilities.image_input` / `capabilities.thinking` 是看图和推理。不给工具调用信息。
 * - Gemini:`GET {base}/v1beta/models`,按 `pageToken` 翻页;只留能 `generateContent` 的,
 *   `inputTokenLimit` 是上下文,`thinking` 是推理。不给工具调用和看图信息。
 */
class ModelCatalog(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun openAiCompat(baseUrl: String, apiKey: String?): ModelListResult = run {
        val url = endpoint(baseUrl, "/models") ?: return@run badUrl()
        val req = Request.Builder().url(url).get()
            .apply { if (!apiKey.isNullOrEmpty()) header("Authorization", "Bearer $apiKey") }.build()
        val root = fetch(req).getOrElse { return@run it.asFailure() }
        val data = (root["data"] ?: root["models"]) as? JsonArray ?: return@run ModelListResult.Failed(ListFailure.BadResponse)
        ModelListResult.Ok(data.mapNotNull { (it as? JsonObject)?.let(::openAiModel) }.distinctBy { it.id })
    }

    suspend fun anthropic(baseUrl: String, apiKey: String): ModelListResult = run {
        val base = endpoint(baseUrl, "/v1/models") ?: return@run badUrl()
        val all = mutableListOf<ListedModel>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val url = base.newBuilder().addQueryParameter("limit", "1000")
                .apply { if (after != null) addQueryParameter("after_id", after) }.build()
            val req = Request.Builder().url(url).get()
                .header("x-api-key", apiKey).header("anthropic-version", "2023-06-01").build()
            val root = fetch(req).getOrElse { return@run it.asFailure() }
            val data = root["data"] as? JsonArray ?: return@run ModelListResult.Failed(ListFailure.BadResponse)
            all += data.mapNotNull { (it as? JsonObject)?.let(::anthropicModel) }
            after = root.str("last_id")
            if (root.bool("has_more") != true || after == null) return@run ModelListResult.Ok(all.distinctBy { it.id })
        }
        ModelListResult.Ok(all.distinctBy { it.id })
    }

    suspend fun gemini(baseUrl: String, apiKey: String): ModelListResult = run {
        val base = endpoint(baseUrl, "/v1beta/models") ?: return@run badUrl()
        val all = mutableListOf<ListedModel>()
        var token: String? = null
        repeat(MAX_PAGES) {
            val url = base.newBuilder().addQueryParameter("pageSize", "1000")
                .apply { if (token != null) addQueryParameter("pageToken", token) }.build()
            val req = Request.Builder().url(url).get().header("x-goog-api-key", apiKey).build()
            val root = fetch(req).getOrElse { return@run it.asFailure() }
            val data = root["models"] as? JsonArray ?: JsonArray(emptyList())
            all += data.mapNotNull { (it as? JsonObject)?.let(::geminiModel) }
            token = root.str("nextPageToken")?.takeIf { it.isNotEmpty() }
            if (token == null) return@run ModelListResult.Ok(all.distinctBy { it.id })
        }
        ModelListResult.Ok(all.distinctBy { it.id })
    }

    private fun openAiModel(o: JsonObject): ListedModel? {
        val id = o.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val arch = o["architecture"] as? JsonObject
        val inputs = (arch?.get("input_modalities") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val modality = arch?.str("modality")?.substringBefore("->")
        val params = (o["supported_parameters"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val caps = o["capabilities"] as? JsonObject
        return ListedModel(
            id = id,
            displayName = o.str("name").orEmpty(),
            toolCalls = params?.let { "tools" in it } ?: caps?.bool("function_calling") ?: caps?.bool("tool_calls"),
            vision = inputs?.let { "image" in it } ?: modality?.let { "image" in it } ?: o.bool("supports_image_in") ?: caps?.bool("vision"),
            contextTokens = o.int("context_length") ?: o.int("context_window") ?: o.int("max_context_length"),
            reasoning = params?.let { "reasoning" in it || "include_reasoning" in it } ?: o.bool("supports_reasoning"),
        )
    }

    private fun anthropicModel(o: JsonObject): ListedModel? {
        val id = o.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val caps = o["capabilities"] as? JsonObject
        return ListedModel(
            id = id,
            displayName = o.str("display_name").orEmpty(),
            vision = (caps?.get("image_input") as? JsonObject)?.bool("supported"),
            contextTokens = o.int("max_input_tokens"),
            reasoning = (caps?.get("thinking") as? JsonObject)?.bool("supported"),
        )
    }

    private fun geminiModel(o: JsonObject): ListedModel? {
        val id = o.str("name")?.removePrefix("models/")?.takeIf { it.isNotBlank() } ?: return null
        val methods = (o["supportedGenerationMethods"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        if (methods != null && "generateContent" !in methods) return null
        return ListedModel(
            id = id,
            displayName = o.str("displayName").orEmpty(),
            contextTokens = o.int("inputTokenLimit"),
            reasoning = o.bool("thinking"),
        )
    }

    private class Failure(val result: ModelListResult.Failed) : Exception(result.message)

    private fun Throwable.asFailure(): ModelListResult.Failed =
        (this as? Failure)?.result ?: ModelListResult.Failed(ListFailure.Network)

    private suspend fun fetch(req: Request): Result<JsonObject> {
        val raw = try {
            HttpSupport.execute(http, req)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(Failure(ModelListResult.Failed(ListFailure.Network)))
        }
        if (raw.status !in 200..299) return Result.failure(Failure(failureFor(raw.status, raw.body)))
        val root = try { json.parseToJsonElement(raw.body).jsonObject } catch (e: Exception) { null }
            ?: return Result.failure(Failure(ModelListResult.Failed(ListFailure.BadResponse)))
        return Result.success(root)
    }

    private fun failureFor(status: Int, body: String): ModelListResult.Failed = when {
        status == 401 || status == 403 -> ModelListResult.Failed(ListFailure.Auth)
        // Gemini 对无效 Key 回 400 INVALID_ARGUMENT,原因写在内容里
        status == 400 && (body.contains("API_KEY_INVALID") || body.contains("API key not valid")) -> ModelListResult.Failed(ListFailure.Auth)
        status == 402 || body.contains("insufficient_quota") -> ModelListResult.Failed(ListFailure.Quota)
        status == 429 -> ModelListResult.Failed(ListFailure.RateLimited)
        status == 404 || status == 405 || status == 501 -> ModelListResult.Failed(ListFailure.NotSupported)
        status in 500..599 -> ModelListResult.Failed(ListFailure.Server, "HTTP $status")
        else -> ModelListResult.Failed(ListFailure.BadResponse, "HTTP $status")
    }

    private fun badUrl() = ModelListResult.Failed(ListFailure.BadUrl)

    private fun endpoint(baseUrl: String, path: String) = (baseUrl.trim().trimEnd('/') + path).toHttpUrlOrNull()

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }

    private companion object {
        /** 翻页上限:防止服务端一直给下一页。 */
        const val MAX_PAGES = 10
    }
}
