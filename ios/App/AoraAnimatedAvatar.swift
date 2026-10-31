import SwiftUI
import WebKit

struct AoraAnimatedAvatar: UIViewRepresentable {
    var phase: String
    var active: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration(); config.websiteDataStore = .nonPersistent()
        let view = WKWebView(frame: .zero, configuration: config)
        view.isOpaque = false; view.backgroundColor = .clear; view.scrollView.isScrollEnabled = false
        view.isUserInteractionEnabled = false; view.navigationDelegate = context.coordinator
        if let url = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "Aora") { view.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent()) }
        return view
    }
    func updateUIView(_ view: WKWebView, context: Context) {
        context.coordinator.id = AoraEmotion.id(for: phase)
        context.coordinator.active = active && !reduceMotion
        context.coordinator.apply(view)
    }
    static func dismantleUIView(_ view: WKWebView, coordinator: Coordinator) { view.evaluateJavaScript("window.destroyMika()", completionHandler: nil); view.stopLoading() }
    final class Coordinator: NSObject, WKNavigationDelegate {
        var id = "02"; var active = true; var ready = false; var last = ""
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { ready = true; apply(webView) }
        func apply(_ view: WKWebView) {
            let signature = id + String(active); guard ready, signature != last else { return }; last = signature
            view.evaluateJavaScript("window.setMikaState('\(id)',\(active ? "true" : "false"))", completionHandler: nil)
        }
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) { decisionHandler(navigationAction.request.url?.isFileURL == true ? .allow : .cancel) }
    }
}
