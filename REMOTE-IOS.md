# Mika iOS 新版（2026-10-30）

## 本次完成

- iPhone 中心雷达，周围显示同一局域网中的 Mika 设备；每台设备使用 aora-bot 表情与动画。超过六台分页。
- 基于 UDP 的 mDNS/Bonjour 自动发现 `_mika-agent._tcp`，点设备发送申请。安卓允许后，将设备授权凭据保存在 iOS 钥匙串，后续自动重连，不填地址或六位码。
- 每台设备独立控制台、任务提交、停止、回答、消息预览。请求不自动重发，避免网络超时导致重复执行。
- 小号桌面组件显示选中设备，中号展示最多三个助手；不在组件中保存聊天正文。
- 灵动岛与锁屏实时活动按设备和任务分离，状态过期会显示等待更新，点按打开对应设备。
- “预览多设备效果”提供明确标注的模拟设备；演示不会提交真实指令。
- 界面移除许可证与署名文字；aora-bot 的原始许可证和说明保留在 App/Resources/Aora 中。

## 使用

iPhone 与安卓处于同一 Wi-Fi；允许 iOS 本地网络权限。新版安卓服务运行后，设备会自动出现，主界面不必保持打开。选设备申请连接，在安卓通知中允许即可。

安卓当前安装版本还需要后续更新和联调。按用户安排，本次先交付 iOS；没有发现安卓设备时可点击页面下方“预览多设备效果”。添加桌面组件需在 iOS 桌面的小组件选择器中选择 Mika。灵动岛在有进行中的任务或演示任务时出现。

## 验证与边界

iOS 真机、模拟器构建通过；签名版本已安装并启动于连接的 iPhone。安卓 LAN 改动已编译，但未安装到当前离线的安卓设备，尚未验证真实 LAN 配对、后台悬浮窗联通和微信/QQ回复。不能将演示状态视为连接成功。

应用内使用本地 aora-bot JavaScript 动画；桌面组件和灵动岛使用同源几何数据的原生 SwiftUI 表情和状态过渡。系统组件不能持续运行该 JavaScript 动画引擎。

局域网版不需要 Mac 中转。iOS 前台约每两秒读取状态；退到后台后连接可能被系统暂停。桌面组件由系统安排刷新，灵动岛在无新数据时标记过期。本版未启用 APNs 后台推送，不能承诺锁屏持续实时更新。

通知预览仅来自安卓允许访问的微信、QQ通知，最多保留100条于内存，不是完整聊天记录。回复仍需模型识别与核对会话；尚未做真实发信验证。

## 构建

使用 `ios/project.yml` 生成 Xcode 工程，部署目标 iOS 17。App Bundle ID 为 `com.kassen.mikaremote`，Widget 为 `com.kassen.mikaremote.widgets`，App Group 为 `group.com.kassen.mikaremote`。开发签名属于本机现有团队。

`relay/` 是先前 USB 原型的开发工具，当前局域网首页不依赖它。其证书、配对密钥和开发签名未放入交付源码。

## 通信方案调整

按用户要求移除传输加密：UDP mDNS/Bonjour 发现设备，普通 HTTP over TCP 传输申请、状态、通知和指令。无 TLS、证书或指纹校验。首次安卓确认后记住授权凭据；明文传输。该新版两端需要同步更新，旧版 TLS 客户端不能连接新版服务。

当前进度：安卓明文版构建与签名完成；待手机重新接线后覆盖安装并联调。消息计数、按应用/会话组织通知、单设备与多设备 agent 对话是后续待实现内容。

## 2026-10-31: LAN event stream

The default transport is an authenticated HTTP event stream over plain TCP (`GET /events?heartbeat=30`), not repeated long polling. The Android service sends a snapshot on every connection, then ordered `state`, `messages` (upserts/removals), and idle `heartbeat` frames. State changes and notification callbacks wake clients. iOS verifies per-connection sequence numbers, merges notifications by ID, stops the stream in background, and reconnects with exponential backoff up to 30 seconds. Reconnection resets the stream sequence and replaces local state with the retained snapshot; it does not replay commands. There is no exactly-once delivery claim or recovery beyond retained notification history.

Sync settings retain configurable legacy polling and long polling. Push mode supports a 15–120 second idle heartbeat and skips periodic `/state` requests once streaming. Older Android builds fall back to polling. Android stores the latest 1000 notification records privately across restarts; notification-provided MessagingStyle entries/text lines are captured when available. Missing WeChat history cannot be reconstructed from a count-only summary. Existing notification intents are runtime handles and may expire after restart.

Push mode is foreground LAN networking, not APNs. iOS suspension still prevents guaranteed background delivery. JEV is not integrated.

## Device and conversation workspace

The iPhone chat header opens a left drawer; regular-width layouts keep the drawer alongside the chat. The drawer groups saved conversations by device, supports search, explicit new-chat creation, device rename, unread markers, and waiting-state indicators. Message notifications remain a separate destination. Offline browsing uses private per-device/per-conversation caches.

Authenticated `GET /chats` and `GET /chat/<id>` expose Android conversation summaries and retained text history. `select_chat` selects an existing Android conversation without executing a task. A blocked switch is reported rather than stopping the running task. Task submissions include the expected selected conversation ID and reject mismatches before creating a conversation. This is not multi-device broadcast/orchestration.
