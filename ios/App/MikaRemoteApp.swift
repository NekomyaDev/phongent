import SwiftUI

@main struct MikaRemoteApp: App {
    @State private var didModelProbe=false
    @State private var selectedTab=0
    @StateObject var hub = NearbyHub()
    @Environment(\.scenePhase) var phase
    var body: some Scene {
        WindowGroup {
            TabView(selection: $selectedTab) {
                NearbyRadar(hub: hub).tag(0).tabItem { Label("设备", systemImage: "iphone.gen3.radiowaves.left.and.right") }
                AgentHome(hub:hub,onDevices:{selectedTab=0}).tag(1).tabItem { Label("agent", systemImage: "bubble.left.and.bubble.right") }
            }.preferredColorScheme(.dark)
                .task { await MessageAlerts.shared.prepare() }
                .onReceive(NotificationCenter.default.publisher(for:.agentMessageOpened)) { event in
                    selectedTab=0
                    hub.requestedDevice=event.userInfo?["deviceId"] as? String
                }
                .onOpenURL { url in
                    if url.scheme == "mikaremote", url.host == "device" { hub.requestedDevice = url.lastPathComponent }
                }
                .task(id: phase) {
                    guard phase == .active else { hub.stopDiscovery(); return }
                    await MessageAlerts.shared.prepare()
                    hub.start()
                    #if DEBUG
                    if !didModelProbe && ProcessInfo.processInfo.environment["AGENT_MODEL_PROBE"] == "1" {
                        didModelProbe=true
                        Task { await ModelCatalog.shared.diagnosticProbe() }
                    }
                    #endif
                    #if DEBUG && targetEnvironment(simulator)
                    if ProcessInfo.processInfo.environment["MIKA_PREVIEW_NEARBY"] == "1" { await hub.showDemo() }
                    #endif
                    while !Task.isCancelled { await hub.refresh(); try? await Task.sleep(for: .seconds(hub.refreshInterval)) }
                }
        }
    }
}
