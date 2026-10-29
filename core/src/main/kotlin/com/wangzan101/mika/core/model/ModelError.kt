package com.wangzan101.mika.core.model

sealed class ModelError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Auth : ModelError("API Key 无效或没有权限")
    class QuotaExceeded : ModelError("额度已用完")
    class RateLimited(val retryAfterMs: Long?) : ModelError("请求太频繁,请稍后再试")
    class ContextTooLong : ModelError("对话内容超出模型上下文长度")
    class ModelNotFound : ModelError("找不到该模型")
    class Network(cause: Throwable? = null) : ModelError("网络连接失败", cause)
    class Server(val status: Int) : ModelError("模型服务出错(HTTP $status)")
    class ContentRefused : ModelError("模型拒绝处理该内容")
    class BadResponse(detail: String = "", cause: Throwable? = null) :
        ModelError(if (detail.isEmpty()) "模型返回了无法解析的内容" else "模型返回了无法解析的内容: $detail", cause)
}
