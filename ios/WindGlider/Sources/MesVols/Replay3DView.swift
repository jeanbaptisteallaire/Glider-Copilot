import SwiftUI
import WebKit

/// Rejeu 3D d'un vol : même page que l'app Android (feature/replay3d/src/main/assets/replay3d), servie par un schéma
/// interne `wgassets://` (équivalent de WebViewAssetLoader). Le pont `window.AndroidReplay` est recréé pour la page.
/// Fonds et relief 3D chargés en ligne par la page (Sentinel-2, IGN, Terrain Tiles), attributions affichées par elle.
struct Replay3DView: UIViewRepresentable {
    let payloadJson: String
    var onStatus: (String) -> Void = { _ in }

    func makeCoordinator() -> Coordinator { Coordinator(payloadJson: payloadJson, onStatus: onStatus) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.setURLSchemeHandler(AssetSchemeHandler(), forURLScheme: "wgassets")
        config.allowsInlineMediaPlayback = true
        let bridge = """
        window.AndroidReplay = new Proxy({}, { get: (_, m) => (v) => window.webkit.messageHandlers.replay.postMessage({ method: String(m), value: String(v) }) });
        """
        config.userContentController.addUserScript(WKUserScript(source: bridge, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        config.userContentController.add(context.coordinator, name: "replay")
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = context.coordinator
        web.scrollView.isScrollEnabled = false
        web.isOpaque = false
        web.load(URLRequest(url: URL(string: "wgassets://app/replay3d/index.html")!))
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        let payloadJson: String
        let onStatus: (String) -> Void
        init(payloadJson: String, onStatus: @escaping (String) -> Void) { self.payloadJson = payloadJson; self.onStatus = onStatus }

        weak var web: WKWebView?
        private var sent = false

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { web = webView; send() }

        /// Une seule fois, quand la page et ses modules sont prêts (comme onPageFinished + onJavascriptReady d'Android).
        private func send() {
            guard !sent, let web else { return }
            let js = "(function(){ if (!window.GlidyReplay) return 'attente'; try { window.GlidyReplay.loadFlight(\(payloadJson)); return 'ok'; } catch (e) { return 'ERR ' + e + ' ' + (e.stack || ''); } })()"
            web.evaluateJavaScript(js) { r, err in
                let res = (r as? String) ?? err?.localizedDescription ?? "?"
                if res == "ok" { self.sent = true } else if res != "attente" { self.onStatus("Rejeu indisponible : \(res)") }
            }
        }

        func userContentController(_ c: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let body = message.body as? [String: String], let method = body["method"] else { return }
            switch method {
            case "onJavascriptReady": send()
            case "onReady": onStatus("")
            case "onError": onStatus(body["value"] ?? "Erreur du rejeu")
            default: break
            }
        }
    }
}

/// Sert les fichiers `replay3d/` du bundle (copiés depuis les assets Android) avec leur type MIME.
final class AssetSchemeHandler: NSObject, WKURLSchemeHandler {
    private static let types = ["html": "text/html", "mjs": "text/javascript", "js": "text/javascript", "css": "text/css",
                                "json": "application/json", "png": "image/png", "bin": "application/octet-stream",
                                "wasm": "application/wasm", "svg": "image/svg+xml"]

    func webView(_ webView: WKWebView, start task: WKURLSchemeTask) {
        guard let url = task.request.url, let base = Bundle.main.resourceURL else { task.didFailWithError(URLError(.badURL)); return }
        let file = base.appendingPathComponent(url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
        guard file.standardizedFileURL.path.hasPrefix(base.appendingPathComponent("replay3d").path),
              let data = try? Data(contentsOf: file) else {
            task.didReceive(HTTPURLResponse(url: url, statusCode: 404, httpVersion: "HTTP/1.1", headerFields: nil)!)
            task.didFinish(); return
        }
        let mime = Self.types[file.pathExtension.lowercased()] ?? "application/octet-stream"
        task.didReceive(HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1",
                                        headerFields: ["Content-Type": mime, "Content-Length": "\(data.count)", "Access-Control-Allow-Origin": "*"])!)
        task.didReceive(data)
        task.didFinish()
    }

    func webView(_ webView: WKWebView, stop task: WKURLSchemeTask) {}
}

/// Écran plein du rejeu, ouvert depuis le détail d'un vol.
struct ReplayScreen: View {
    let payload: String
    @State private var status = "Chargement du rejeu 3D…"
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .top) {
            GeometryReader { g in
                // comme Android : marges réservées en haut (bouton fermer, encoche) et en bas, transmises à la page
                Replay3DView(payloadJson: withInsets(top: g.safeAreaInsets.top + 56, bottom: g.safeAreaInsets.bottom)) { s in status = s }
                    .ignoresSafeArea()
            }
            HStack {
                Button { dismiss() } label: { Image(systemName: "xmark").font(.headline).padding(10).background(.white, in: Circle()) }
                    .accessibilityLabel("Fermer le rejeu")
                Spacer()
                if !status.isEmpty { Text(status).font(WG.caption).padding(8).background(.white.opacity(0.9), in: Capsule()) }
            }
            .padding()
        }
        .background(Color.black.ignoresSafeArea())
    }

    private func withInsets(top: CGFloat, bottom: CGFloat) -> String {
        guard payload.hasSuffix("}") else { return payload }
        return String(payload.dropLast()) + ",\"insets\":{\"top\":\(Int(top)),\"bottom\":\(Int(bottom))}}"
    }
}
