import SwiftUI

enum SyncPreferences {
    static var push:Bool {UserDefaults.standard.object(forKey:"syncPush") as? Bool ?? true}
    static var heartbeat:Double {min(120,max(15,UserDefaults.standard.object(forKey:"syncHeartbeat") as? Double ?? 30))}
    static var longPolling:Bool {UserDefaults.standard.object(forKey:"syncLongPolling") as? Bool ?? true}
    static var wait:Double {min(60,max(5,UserDefaults.standard.object(forKey:"syncWait") as? Double ?? 20))}
    static var interval:Double {min(60,max(0.5,UserDefaults.standard.object(forKey:"syncInterval") as? Double ?? 2))}
    static var stateInterval:Double {min(60,max(1,UserDefaults.standard.object(forKey:"syncStateInterval") as? Double ?? 2))}
    static var signature:String {"\(push)-\(heartbeat)-\(longPolling)-\(wait)-\(interval)"}
}
struct SyncSettings:View {
    @AppStorage("syncPush") private var push=true
    @AppStorage("syncHeartbeat") private var heartbeat=30.0
    @AppStorage("syncLongPolling") private var longPolling=true
    @AppStorage("syncWait") private var wait=20.0
    @AppStorage("syncInterval") private var interval=2.0
    @AppStorage("syncStateInterval") private var stateInterval=2.0
    var body:some View {
        Form {
            Section("连接方式") {
                Toggle("TCP 主动推送",isOn:$push)
                if push {
                    Stepper("空闲心跳：\(Int(heartbeat)) 秒",value:$heartbeat,in:15...120,step:15)
                    Text("通知与任务状态有变化立即推送。断线自动重连并补同步；旧版安卓暂用轮询。").font(.caption).foregroundStyle(.secondary)
                }
            }
            if !push { Section("消息同步") {
                Toggle("独立长轮询",isOn:$longPolling)
                if longPolling {
                    Stepper("最长等待：\(Int(wait)) 秒",value:$wait,in:5...60,step:5)
                    Text("收到通知立即返回；等待时间只决定没有新消息时多久重连。").font(.caption).foregroundStyle(.secondary)
                } else {
                    Stepper("轮询间隔：\(interval,specifier:"%.1f") 秒",value:$interval,in:0.5...60,step:0.5)
                }
            }
            Section("设备状态") {Stepper("刷新间隔：\(Int(stateInterval)) 秒",value:$stateInterval,in:1...60)}
            }
            Section {Text("设置自动保存，应用于所有设备。iPhone 在后台挂起时无法持续进行局域网同步。").font(.caption).foregroundStyle(.secondary)}
        }.navigationTitle("同步设置")
    }
}
