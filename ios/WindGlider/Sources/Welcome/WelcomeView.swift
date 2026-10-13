import GoogleSignIn
import SwiftUI
import WebKit

/// Couverture Wind Glider (même page animée qu'Android : app/src/main/assets/cover) et les trois entrées :
/// Continuer avec Google, Se connecter avec Apple, Continuer en invité (WelcomeScreen.kt).
/// Compte déjà connecté : la page sert d'écran de lancement (1,6 s) puis l'app s'ouvre.
struct WelcomeView: View {
    @EnvironmentObject var account: AccountModel
    let onContinue: () -> Void
    @State private var signedInAtStart: Bool?
    @State private var leaving = false
    @State private var shown = false

    var body: some View {
        ZStack {
            WG.coverSea.ignoresSafeArea()
            CoverAnimation().ignoresSafeArea()
            VStack(spacing: 0) {
                BrandTitle()
                    .padding(.top, 56)
                    .opacity(shown ? 1 : 0).offset(y: shown ? 0 : 12)
                Spacer()
                if signedInAtStart == false {
                    if let msg = account.message, !account.signedIn {
                        Text(msg).font(WG.footnote).foregroundStyle(WG.brandInk).multilineTextAlignment(.center)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(Color.white.opacity(0.85), in: RoundedRectangle(cornerRadius: 12))
                            .padding(.bottom, 12)
                    }
                    VStack(spacing: 16) {
                        if account.googleAvailable {
                            WelcomeButton(text: "Continuer avec Google", enabled: !account.busy && !leaving) {
                                GoogleMark()
                            } action: { account.signInWithGoogle() }
                        }
                        if account.appleAvailable {
                            WelcomeButton(text: "Se connecter avec Apple", enabled: !account.busy && !leaving) {
                                Image(systemName: "apple.logo").font(.system(size: 20, weight: .medium)).foregroundStyle(.black)
                            } action: { account.signInWithApple() }
                        }
                        WelcomeButton(text: "Continuer en invité", enabled: !leaving) { EmptyView() } action: {
                            leaving = true
                            onContinue()
                        }
                    }
                    .opacity(shown ? 1 : 0).offset(y: shown ? 0 : 16)
                    if account.busy { ProgressView().tint(.white).padding(.top, 12) }
                    HStack(spacing: 4) {
                        Link("Conditions", destination: Legal.terms)
                        Text("·")
                        Link("Confidentialité", destination: Legal.privacy)
                    }
                    .font(Font.custom("Inter", size: 12).weight(.medium)).foregroundStyle(.white).tint(.white)
                    .shadow(color: .black.opacity(0.6), radius: 4)
                    .padding(.top, 14)
                    Spacer().frame(height: UIScreen.main.bounds.height * 0.16)
                }
            }
            .padding(.horizontal, 24)
        }
        .preferredColorScheme(.light)
        .onAppear {
            if signedInAtStart == nil { signedInAtStart = account.signedIn }
            withAnimation(.easeOut(duration: 0.6).delay(0.5)) { shown = true }
        }
        .task {
            if account.signedIn && signedInAtStart == true {
                try? await Task.sleep(nanoseconds: 1_600_000_000)
                onContinue()
            }
        }
        .onChange(of: account.email) { _, mail in
            // connexion réussie depuis cette page : on entre dans l'app
            if signedInAtStart == false, mail != nil, !leaving { leaving = true; onContinue() }
        }
        .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
    }
}

/// Bouton de la couverture : carte blanche aux coins doux, texte bleu nuit (WelcomeButton d'Android).
private struct WelcomeButton<Leading: View>: View {
    let text: String
    let enabled: Bool
    @ViewBuilder let leading: () -> Leading
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                leading()
                Text(text).font(Font.custom("Inter", size: 17).weight(.medium)).tracking(0.2)
                    .foregroundStyle(enabled ? WG.brandInk : WG.brandInk.opacity(0.4))
            }
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(Color.white.opacity(0.95), in: RoundedRectangle(cornerRadius: 14))
            .shadow(color: Color(red: 0.06, green: 0.12, blue: 0.19).opacity(0.2), radius: 10, y: 3)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .frame(maxWidth: 300)
    }
}

/// Logo « G » officiel livré avec le SDK GoogleSignIn (charte « Sign in with Google »).
private struct GoogleMark: View {
    var body: some View {
        if let img = Self.logo { Image(uiImage: img).resizable().frame(width: 22, height: 22) }
    }
    private static let logo: UIImage? = {
        let bundles = [Bundle.main] + Bundle.allBundles + Bundle.allFrameworks
        for b in bundles {
            for name in ["GoogleSignIn_GoogleSignIn", "GoogleSignIn"] {
                if let url = b.url(forResource: name, withExtension: "bundle"), let rb = Bundle(url: url),
                   let img = UIImage(named: "google", in: rb, compatibleWith: nil) { return img }
            }
        }
        return nil
    }()
}

/// Titre « WIND GLIDER » en Inter capitales espacées (BrandTitle de WgArt.kt).
struct BrandTitle: View {
    var size: CGFloat = 46
    var body: some View {
        VStack(spacing: size * 0.16) {
            Text("WIND")
            Text("GLIDER")
        }
        .font(Font.custom("Inter", size: size).weight(.medium))
        .tracking(size * 0.07)
        .foregroundStyle(.white)
        .shadow(color: Color(red: 0, green: 0.1, blue: 0.16).opacity(0.25), radius: 8)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Wind Glider")
    }
}

/// Couverture animée de JB (océan, nuage, planeurs peints), page HTML partagée avec Android.
/// Couverture : image peinte native (toujours visible) sous la page animée d'Android. Si la page ne s'affiche pas
/// (vu sur iPad / iPadOS 27 : fond bleu vide), l'image native reste : plus jamais d'écran bleu sans dessin.
struct CoverAnimation: View {
    var body: some View {
        ZStack {
            NativeCover()
            CoverWeb()
        }
    }
}

/// Même cadrage que la page (viewBox 0 194 872 1841, « slice ») : océan, nuage, planeurs.
struct NativeCover: View {
    private static func img(_ name: String) -> UIImage? {
        Bundle.main.url(forResource: name, withExtension: "webp", subdirectory: "cover").flatMap { UIImage(contentsOfFile: $0.path) }
    }
    private static let layers: [(UIImage?, CGRect)] = [
        (img("ocean-sans-nuage"), CGRect(x: -24, y: 210, width: 928, height: 1918)),
        (img("nuage-transparent"), CGRect(x: 0, y: 233, width: 872, height: 1802)),
        (img("planeurs-transparents"), CGRect(x: 0, y: 231, width: 872, height: 1804)),
    ]

    var body: some View {
        GeometryReader { g in
            let k = max(g.size.width / 872, g.size.height / 1841)
            let dx = (g.size.width - 872 * k) / 2, dy = (g.size.height - 1841 * k) / 2 - 194 * k
            ZStack(alignment: .topLeading) {
                WG.coverSea
                ForEach(0..<Self.layers.count, id: \.self) { i in
                    if let im = Self.layers[i].0 {
                        let r = Self.layers[i].1
                        Image(uiImage: im).resizable()
                            .frame(width: r.width * k, height: r.height * k)
                            .offset(x: dx + r.minX * k, y: dy + r.minY * k)
                    }
                }
            }
            .frame(width: g.size.width, height: g.size.height, alignment: .topLeading)
            .clipped()
        }
    }
}

struct CoverWeb: UIViewRepresentable {
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        // fond de page transparent : tant que (ou si) la page n'a rien dessiné, l'image native reste visible
        config.userContentController.addUserScript(WKUserScript(
            source: "var s=document.createElement('style');s.textContent='html,body{background:transparent!important} #scene>rect:first-of-type{fill:transparent}';document.documentElement.appendChild(s);",
            injectionTime: .atDocumentStart, forMainFrameOnly: true))
        config.setURLSchemeHandler(AssetSchemeHandler(), forURLScheme: "wgassets")
        let web = WKWebView(frame: .zero, configuration: config)
        web.isOpaque = false
        web.backgroundColor = .clear
        web.scrollView.backgroundColor = .clear
        web.scrollView.isScrollEnabled = false
        web.isUserInteractionEnabled = false
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.load(URLRequest(url: URL(string: "wgassets://app/cover/index.html")!))
        return web
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
