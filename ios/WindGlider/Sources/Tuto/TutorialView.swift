import SwiftUI
import WindGliderShared

/// Visite guidée (Guide.kt d'Android, V19.1b « coach marks ») : la vraie page en capture, un voile à 50 % et un cadre
/// bleu lumineux qui glisse d'une zone à l'autre. Dernière page : « Votre avis ».
/// Nouveau design (JB, 29/10/2026) : 8 étapes iOS (Tutorial.stepsIos), captures en français ou en anglais selon le
/// bouton FR/EN, ouverte seulement depuis l'onglet Tuto (plus de tutoriel imposé au premier lancement).
struct TutorialView: View {
    @EnvironmentObject var account: AccountModel
    @AppStorage("guideLanguage") private var lang = Locale.current.language.languageCode?.identifier == "fr" ? "fr" : "en"
    let onClose: () -> Void
    @State private var index = TutorialView.launchStep ?? 0
    private let steps = Tutorial.shared.stepsIos

    /// Build de développement : `-tourStep N` ouvre le tutoriel à l'étape N (1 à 8), pour les captures au simulateur.
    static var launchStep: Int? {
        #if DEBUG
        let a = ProcessInfo.processInfo.arguments
        if let i = a.firstIndex(of: "-tourStep"), i + 1 < a.count, let n = Int(a[i + 1]) { return max(n - 1, 0) }
        #endif
        return nil
    }

    var body: some View {
        if index >= steps.count {
            FeedbackPage(lang: $lang, onBack: { index = steps.count - 1 }, onDone: onClose)
        } else {
            SpotlightPage(step: steps[index], position: index + 1, total: steps.count, lang: $lang,
                          onPrevious: { if index > 0 { index -= 1 } }, onNext: { index += 1 },
                          onSkip: onClose)
        }
    }
}

/// Capture de l'étape dans la langue choisie (`tour2_pilotage_fr.jpg`), sinon l'image commune (`tour_*.webp`).
private func tourImage(_ name: String, lang: String) -> Image? {
    Bundle.main.path(forResource: "\(name)_\(lang)", ofType: "jpg").flatMap(UIImage.init(contentsOfFile:)).map(Image.init(uiImage:))
        ?? wgImage(name)
}

private let glow = Color(red: 0x5A / 255, green: 0xC8 / 255, blue: 0xFA / 255)

private struct SpotlightPage: View {
    let step: TourStep
    let position: Int
    let total: Int
    @Binding var lang: String
    let onPrevious: () -> Void
    let onNext: () -> Void
    let onSkip: (() -> Void)?
    @State private var pulse = false

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            let scale = w / 1080
            let viewport = h / scale
            let bottomReserve = 150 / scale, topReserve = 40 / scale
            let rect = step.rect(lang: lang)
            let cy = CGFloat(rect.t + rect.b) / 2
            let maxScroll = CGFloat(step.imageHeight) - viewport + bottomReserve
            // capture plein écran (nouveau design) : posée exactement sur l'écran, comme la vraie page ;
            // capture plus haute que l'écran : défilement jusqu'à la zone
            let fitsScreen = CGFloat(step.imageHeight) <= viewport + 2
            let scroll = fitsScreen ? 0 : min(max(cy - viewport * 0.4, -topReserve), max(-topReserve, maxScroll))
            let pad: CGFloat = 6
            let hole = CGRect(x: CGFloat(rect.l) * scale - pad, y: (CGFloat(rect.t) - scroll) * scale - pad,
                              width: CGFloat(rect.r - rect.l) * scale + 2 * pad, height: CGFloat(rect.b - rect.t) * scale + 2 * pad)
            ZStack(alignment: .topLeading) {
                WG.page.frame(width: w, height: h)   // sous la capture : fond Godspeed, pas de bande blanche
                // la page, plein largeur, tenue dans l'écran (pas d'agrandissement de la vue)
                Color.clear.frame(width: w, height: h)
                    .overlay(alignment: .top) {
                        if let img = tourImage(step.image, lang: lang) {
                            img.resizable().frame(width: w, height: CGFloat(step.imageHeight) * scale)
                                .offset(y: -scroll * scale)
                                .id(step.image + lang)
                                .transition(.opacity)
                        }
                    }
                    .clipped()
                // voile 50 % avec découpe
                Path { p in
                    p.addRect(CGRect(x: 0, y: 0, width: w, height: h))
                    p.addRoundedRect(in: hole, cornerSize: CGSize(width: 14, height: 14))
                }
                .fill(Color.black.opacity(0.5), style: FillStyle(eoFill: true))
                // cadre lumineux
                ForEach(1...3, id: \.self) { k in
                    RoundedRectangle(cornerRadius: 14 + CGFloat(k) * 3)
                        .stroke(glow.opacity(0.16 * (pulse ? 1 : 0.55)), lineWidth: 4.2)
                        .frame(width: hole.width + CGFloat(k) * 6, height: hole.height + CGFloat(k) * 6)
                        .position(x: hole.midX, y: hole.midY)
                }
                RoundedRectangle(cornerRadius: 14).stroke(glow.opacity(pulse ? 1 : 0.89), lineWidth: 3)
                    .frame(width: hole.width, height: hole.height).position(x: hole.midX, y: hole.midY)
                // texte du côté où il reste le plus de place
                let below = (h - 120 - hole.maxY) >= hole.minY
                textBlock
                    .frame(width: w)
                    .position(x: w / 2, y: below ? hole.maxY + 18 + 80 : max(90, hole.minY - 18 - 80))
                // commandes
                VStack {
                    Spacer()
                    HStack(spacing: 8) {
                        if let onSkip { Button(Tutorial.shared.skip(lang: lang), action: onSkip).font(WG.subhead).foregroundStyle(.white.opacity(0.8)).lineLimit(1).fixedSize().padding(.vertical, 10) }
                        LanguageButton(lang: $lang)
                        Spacer()
                        if position > 1 { TourButton(text: Tutorial.shared.previous(lang: lang), filled: false, action: onPrevious) }
                        TourButton(text: Tutorial.shared.next(lang: lang), filled: true, action: onNext)
                    }
                    .padding(.horizontal, 16).padding(.vertical, 14)
                    .padding(.bottom, 30)   // au-dessus de la barre d'accueil (page en plein écran)
                    .background(LinearGradient(colors: [.clear, .black.opacity(0.8)], startPoint: .top, endPoint: .bottom))
                }
                .frame(width: w, height: h)
            }
            .animation(.spring(response: 0.6, dampingFraction: 0.85), value: position)
        }
        .ignoresSafeArea()
        .onAppear { withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { pulse = true } }
    }

    private var textBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(step.header(lang: lang, position: Int32(position), total: Int32(total))).font(WG.footnote.weight(.semibold)).foregroundStyle(glow)
            Text(step.title(lang: lang)).font(WG.title.weight(.bold)).foregroundStyle(.white)
            Text(step.text(lang: lang)).font(WG.body).foregroundStyle(.white.opacity(0.92)).fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 18).padding(.vertical, 16)
        .background(Color.black.opacity(0.78), in: RoundedRectangle(cornerRadius: 18))
        .padding(.horizontal, 16)
    }
}

private struct TourButton: View {
    let text: String
    let filled: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(text).font(WG.headline).foregroundStyle(filled ? Color(red: 0, green: 0x24 / 255, blue: 0x3A / 255) : .white)
                .lineLimit(1).fixedSize()   // « Précédent » sur une ligne, même sur un iPhone étroit
                .padding(.horizontal, 16).frame(height: 44)
                .background(filled ? glow : .clear, in: Capsule())
                .overlay(Capsule().stroke(filled ? .clear : .white.opacity(0.7)))
        }
    }
}

/// Bouton FR / EN (LanguageButton d'Android).
struct LanguageButton: View {
    @Binding var lang: String
    var dark = true
    var body: some View {
        Button { lang = lang == "fr" ? "en" : "fr" } label: {
            Text(lang == "fr" ? "EN" : "FR").font(WG.subhead.weight(.semibold))
                .foregroundStyle(dark ? .white : WG.brandInk)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(dark ? Color.black.opacity(0.55) : .clear, in: Capsule())   // lisible sur la capture
                .overlay(Capsule().stroke(dark ? .white.opacity(0.6) : WG.line))
        }
        .accessibilityLabel(lang == "fr" ? "English" : "Français")
    }
}

/// Dernière étape : commentaire libre (table feedback de Supabase, invité accepté).
struct FeedbackPage: View {
    @EnvironmentObject var account: AccountModel
    @Binding var lang: String
    let onBack: () -> Void
    let onDone: () -> Void
    @State private var text = ""
    @State private var state = 0 // 0 saisie, 1 envoi, 2 envoyé, 3 échec
    private var t: Tutorial { Tutorial.shared }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(t.thatsAll(lang: lang)).font(WG.largeTitle).foregroundStyle(WG.brandInk)
                    Spacer()
                    LanguageButton(lang: $lang, dark: false)
                }
                .padding(.top, 8)
                VStack(alignment: .leading, spacing: 10) {
                    Text(t.feedbackTitle(lang: lang)).font(WG.title.weight(.semibold)).foregroundStyle(WG.brandInk)
                    Text(t.feedbackIntro(lang: lang)).font(WG.subhead).foregroundStyle(WG.dim)
                    if state == 2 {
                        Text(t.sent(lang: lang)).font(WG.headline).foregroundStyle(WG.good)
                    } else {
                        TextField(t.feedbackPlaceholder(lang: lang), text: $text, axis: .vertical)
                            .lineLimit(5...12)
                            .padding(10)
                            .background(RoundedRectangle(cornerRadius: 12).stroke(WG.line))
                            .onChange(of: text) { _, v in if v.count > Int(Tutorial.shared.FEEDBACK_MAX) { text = String(v.prefix(Int(Tutorial.shared.FEEDBACK_MAX))) } }
                        Button {
                            state = 1
                            Task {
                                let ok = await account.sendFeedback(text, lang: lang)
                                state = ok ? 2 : 3
                                if ok { text = "" }
                            }
                        } label: {
                            Text(state == 1 ? t.sending(lang: lang) : t.send(lang: lang)).font(WG.headline)
                                .frame(maxWidth: .infinity, minHeight: 46)
                                .background(WG.sky, in: RoundedRectangle(cornerRadius: 12)).foregroundStyle(.white)
                        }
                        .disabled(state == 1 || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        if state == 3 { Text(t.sendFailed(lang: lang)).font(WG.footnote).foregroundStyle(WG.warn) }
                    }
                    Text(t.feedbackPrivacy(lang: lang)).font(WG.footnote).foregroundStyle(WG.dim)
                }
                .padding(16)
                .background(WG.card, in: RoundedRectangle(cornerRadius: 18))
                HStack(spacing: 10) {
                    Button(action: onBack) {
                        Text(t.review(lang: lang)).font(WG.headline).frame(maxWidth: .infinity, minHeight: 46)
                            .background(WG.sky.opacity(0.14), in: RoundedRectangle(cornerRadius: 12)).foregroundStyle(WG.sky)
                    }
                    Button(action: onDone) {
                        Text(t.done(lang: lang)).font(WG.headline).frame(maxWidth: .infinity, minHeight: 46)
                            .background(WG.sky, in: RoundedRectangle(cornerRadius: 12)).foregroundStyle(.white)
                    }
                }
            }
            .padding(16)
        }
        .background(WG.page.ignoresSafeArea())
    }
}

/// Onglet Tuto (TutoHomeScreen d'Android) : couverture animée, titre, deux boutons (tutoriel, avis).
struct TutoHomeView: View {
    @AppStorage("guideLanguage") private var lang = Locale.current.language.languageCode?.identifier == "fr" ? "fr" : "en"
    @State private var tour = TutorialView.launchStep != nil
    @State private var feedback = false
    @State private var shown = false

    var body: some View {
        ZStack {
            WG.coverSea.ignoresSafeArea()
            CoverAnimation().ignoresSafeArea()
            VStack(spacing: 0) {
                HStack { Spacer(); LanguageButton(lang: $lang) }.padding(.top, 12)
                BrandTitle().padding(.top, 12).opacity(shown ? 1 : 0)
                Text(Tutorial.shared.homeIntro(lang: lang)).font(WG.subhead).foregroundStyle(.white).multilineTextAlignment(.center)
                    .shadow(color: .black.opacity(0.4), radius: 4).padding(.top, 18).padding(.horizontal, 24).opacity(shown ? 1 : 0)
                Spacer()
                VStack(spacing: 16) {
                    coverButton(Tutorial.shared.tutorialButton(lang: lang)) { tour = true }
                    coverButton(Tutorial.shared.feedbackButton(lang: lang)) { feedback = true }
                }
                .opacity(shown ? 1 : 0)
                Spacer().frame(height: UIScreen.main.bounds.height * 0.2)
            }
            .padding(.horizontal, 24)
        }
        .onAppear { withAnimation(.easeOut(duration: 0.6).delay(0.5)) { shown = true } }
        .fullScreenCover(isPresented: $tour) { TutorialView { tour = false } }
        .fullScreenCover(isPresented: $feedback) { FeedbackPage(lang: $lang, onBack: { feedback = false }, onDone: { feedback = false }) }
    }

    private func coverButton(_ text: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(Font.system(size: 17).weight(.medium)).foregroundStyle(WG.brandInk)
                .frame(maxWidth: 300, minHeight: 56)
                .background(WG.card, in: RoundedRectangle(cornerRadius: 14))
                .shadow(color: .black.opacity(0.2), radius: 10, y: 3)
        }
        .buttonStyle(.plain)
    }
}
