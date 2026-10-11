import SwiftUI
import WindGliderShared

/// Check-lists (ChecklistScreen d'Android) : contenu, plan de rupture de câble et progression viennent du métier commun
/// (core:domain, Checklists / CablePlan). Cases et briefing gardés sur l'iPhone, remis à zéro à la demande.
struct ChecklistView: View {
    @AppStorage("checklistChecked") private var checkedRaw = ""
    @AppStorage("checklistBrief") private var briefRaw = ""
    @State private var open: [String: Bool] = [:]
    @State private var resetDone = false

    private var checked: Set<String> { Set(checkedRaw.split(separator: "|").map(String.init)) }

    private var brief: CableBriefInput {
        let p = briefRaw.components(separatedBy: "|")
        guard p.count == 5 else { return CableBriefInput(qfu: "27", turn: "100", ahead: "80", field: "champ au nord de l’axe", threat: "vent NO et trafic remorqué") }
        return CableBriefInput(qfu: p[0], turn: p[1], ahead: p[2], field: p[3], threat: p[4])
    }

    var body: some View {
        let plan = CablePlan.companion.from(input: brief)
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top) {
                    Text(Checklists.shared.PAGE_TITLE).font(WG.largeTitle).foregroundStyle(WG.brandInk)
                    Spacer()
                    Text(Checklists.shared.PAGE_TAG).font(WG.caption2).foregroundStyle(WG.dim).multilineTextAlignment(.trailing)
                }
                Text(Checklists.shared.INTRO).font(WG.subhead).foregroundStyle(WG.dim)
                ForEach(Checklists.shared.all, id: \.id) { list in
                    card(list, plan: plan)
                }
                HStack {
                    Text(Checklists.shared.RESET_NOTE).font(WG.footnote).foregroundStyle(WG.dim)
                    Spacer()
                    Button(Checklists.shared.RESET_BUTTON) { checkedRaw = ""; resetDone = true }
                        .font(WG.subhead.weight(.semibold)).foregroundStyle(WG.bad)
                }
                if resetDone { Text(Checklists.shared.RESET_TOAST).font(WG.footnote).foregroundStyle(WG.good) }
                Text(Checklists.shared.SOURCE).font(WG.caption2).foregroundStyle(WG.dim)
            }
            .padding(16)
        }
        .background(WG.brandPaper)
    }

    private func isOpen(_ l: Checklist) -> Bool { open[l.id] ?? l.openByDefault }

    private func card(_ list: Checklist, plan: CablePlan) -> some View {
        let p = list.progress(checked: checked)
        return VStack(alignment: .leading, spacing: 10) {
            Button { open[list.id] = !isOpen(list) } label: {
                HStack(spacing: 12) {
                    Text(list.code).font(WG.caption.weight(.bold)).foregroundStyle(WG.sky)
                        .padding(.horizontal, 8).padding(.vertical, 4).background(WG.sky.opacity(0.12), in: Capsule())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(list.title).font(WG.headline).foregroundStyle(WG.brandInk)
                        Text(list.subtitle).font(WG.footnote).foregroundStyle(WG.dim)
                    }
                    Spacer()
                    Text(p.description()).font(WG.caption.weight(.semibold)).monospacedDigit()
                        .foregroundStyle(p.complete ? WG.good : WG.dim)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background((p.complete ? WG.good : WG.dim).opacity(0.12), in: Capsule())
                }
            }
            .buttonStyle(.plain)
            .accessibilityValue(isOpen(list) ? "déplié" : "replié")
            if isOpen(list) {
                ForEach(Array(list.blocks.enumerated()), id: \.offset) { _, block in
                    if let s = block as? BlockSubtitle {
                        Text(s.text).font(WG.footnote.weight(.semibold)).foregroundStyle(WG.dim).textCase(.uppercase).padding(.top, 4)
                    } else if let items = block as? BlockItems {
                        ForEach(items.items, id: \.id) { item in row(item, plan: plan) }
                    } else if block is BlockCableBrief {
                        briefFields(plan)
                    }
                }
            }
        }
        .padding(16)
        .background(WG.card, in: RoundedRectangle(cornerRadius: 18))
    }

    private func row(_ item: CheckItem, plan: CablePlan) -> some View {
        let on = checked.contains(item.id)
        let text = item.dynamic.map { plan.textFor(kind: $0) } ?? item.text
        return Button {
            var s = checked
            if on { s.remove(item.id) } else { s.insert(item.id) }
            checkedRaw = s.sorted().joined(separator: "|")
            resetDone = false
        } label: {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: on ? "checkmark.circle.fill" : "circle").font(.title3).foregroundStyle(on ? WG.good : WG.dim)
                VStack(alignment: .leading, spacing: 2) {
                    (Text(item.bold.map { $0 + " " } ?? "").fontWeight(.semibold) + Text(text))
                        .font(WG.body).foregroundStyle(WG.brandInk).strikethrough(on, color: WG.dim)
                    if let small = item.small { Text(small).font(WG.footnote).foregroundStyle(WG.dim) }
                }
                Spacer(minLength: 0)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel([item.bold, text].compactMap { $0 }.joined(separator: " "))
        .accessibilityValue(on ? "coché" : "non coché")
    }

    private func briefFields(_ plan: CablePlan) -> some View {
        let b = brief
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 8) {
                field("Piste / QFU", b.qfu, numeric: true) { save(b.doCopy(qfu: $0, turn: b.turn, ahead: b.ahead, field: b.field, threat: b.threat)) }
                field("Demi-tour dès", b.turn, numeric: true, unit: "m sol") { save(b.doCopy(qfu: b.qfu, turn: $0, ahead: b.ahead, field: b.field, threat: b.threat)) }
            }
            HStack(alignment: .top, spacing: 8) {
                field("Posé devant sous", b.ahead, numeric: true, unit: "m sol") { save(b.doCopy(qfu: b.qfu, turn: b.turn, ahead: $0, field: b.field, threat: b.threat)) }
                field("Dégagement", b.field) { save(b.doCopy(qfu: b.qfu, turn: b.turn, ahead: b.ahead, field: $0, threat: b.threat)) }
            }
            field("Menace", b.threat) { save(b.doCopy(qfu: b.qfu, turn: b.turn, ahead: b.ahead, field: b.field, threat: $0)) }
            VStack(alignment: .leading, spacing: 4) {
                ForEach(plan.rows, id: \.lead) { r in
                    (Text(r.lead).fontWeight(.semibold) + Text(" " + r.text)).font(WG.footnote).foregroundStyle(WG.brandInk)
                }
            }
            .padding(12)
            .background(WG.sky.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
        }
    }

    private func field(_ label: String, _ value: String, numeric: Bool = false, unit: String? = nil, set: @escaping (String) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(WG.caption).foregroundStyle(WG.dim).lineLimit(1)
            TextField(label, text: Binding(get: { value }, set: set))
                .keyboardType(numeric ? .numberPad : .default)
                .font(WG.callout.weight(.medium)).monospacedDigit()
                .padding(.vertical, 6)
                .overlay(alignment: .bottom) { Rectangle().fill(WG.line).frame(height: 1) }
            if let unit { Text(unit).font(WG.caption).foregroundStyle(WG.dim) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func save(_ b: CableBriefInput) {
        briefRaw = [b.qfu, b.turn, b.ahead, b.field, b.threat].map { $0.replacingOccurrences(of: "|", with: "/") }.joined(separator: "|")
    }
}
