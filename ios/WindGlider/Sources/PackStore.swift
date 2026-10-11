import CryptoKit
import Foundation
import WindGliderShared

/// Cartes hors ligne (relief + openAIP) : catalogue des packs régionaux d'Android, téléchargement vérifié par SHA-256.
/// Règles d'Android (PackDownloader) : fichier en « .part », empreinte vérifiée, renommage, manifeste écrit en dernier
/// (un pack sans manifeste n'est jamais considéré installé), anciens fichiers retirés.
@MainActor
final class PackStore: ObservableObject {
    @Published var available: PackInfo?
    @Published var installed: PackInfo?
    @Published var active: ActivePack?
    @Published var progress: Double?
    @Published var error: String?
    @Published var catalogError: String?
    private var catalog: [PackInfo] = []
    private var task: Task<Void, Never>?

    static let root: String = {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("packs")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        // les cartes se retéléchargent : pas de sauvegarde iCloud
        var url = dir; var v = URLResourceValues(); v.isExcludedFromBackup = true; try? url.setResourceValues(v)
        return dir.path
    }()

    /// Pack de la région du club : celui du catalogue, sinon un pack déjà installé qui couvre le terrain.
    func refresh(for club: Club?) async {
        guard let pos = club?.position else { return }
        if catalog.isEmpty { await loadCatalog() }
        available = IosPacks.shared.packFor(catalog: catalog, lat: pos.lat, lon: pos.lon)
        let inst = available.flatMap { IosPacks.shared.installed(root: Self.root, id: $0.id) }
            ?? IosPacks.shared.installedFor(root: Self.root, lat: pos.lat, lon: pos.lon)
        installed = inst
        if let inst, active?.info != inst { active = IosPacks.shared.load(root: Self.root, info: inst) }
        if inst == nil { active = nil }
    }

    private func loadCatalog() async {
        let cacheURL = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("packs-catalog.json")
        do {
            let (data, resp) = try await URLSession.shared.data(from: URL(string: IosPacks.shared.CATALOG_URL)!)
            guard (resp as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
            try? data.write(to: cacheURL)
            catalog = IosPacks.shared.parseCatalog(text: String(decoding: data, as: UTF8.self))
            catalogError = nil
        } catch {
            if let cached = try? String(contentsOf: cacheURL, encoding: .utf8) { catalog = IosPacks.shared.parseCatalog(text: cached) }
            catalogError = catalog.isEmpty ? "Catalogue des cartes injoignable" : nil
        }
    }

    var neededMB: Int { available.map { Int(IosPacks.shared.neededBytes(pack: $0) / 1_000_000) } ?? 0 }
    var updateAvailable: Bool { installed != nil && available != nil && available!.version != installed!.version }

    func download(club: Club?) {
        guard let pack = available, task == nil else { return }
        error = nil; progress = 0
        task = Task {
            do {
                try await install(pack)
                progress = nil
            } catch is CancellationError {
                progress = nil
            } catch {
                progress = nil
                self.error = error.localizedDescription
            }
            task = nil
            await refresh(for: club)
        }
    }

    func cancel() { task?.cancel() }

    private func install(_ pack: PackInfo) async throws {
        let (mdata, mresp) = try await URLSession.shared.data(from: URL(string: IosPacks.shared.manifestUrl(pack: pack))!)
        guard (mresp as? HTTPURLResponse)?.statusCode == 200 else { throw PackError("manifeste introuvable") }
        let manifestText = String(decoding: mdata, as: UTF8.self)
        guard let manifest = IosPacks.shared.parseManifest(text: manifestText) else { throw PackError("manifeste illisible") }
        let dir = URL(fileURLWithPath: Self.root).appendingPathComponent(manifest.id)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let files = IosPacks.shared.neededFiles(pack: manifest)
        let total = Double(files.reduce(Int64(0)) { $0 + $1.size })
        var done = 0.0
        for f in files {
            try Task.checkCancellation()
            let dest = dir.appendingPathComponent(f.name)
            if size(dest) == f.size, try sha256(dest) == f.sha256.lowercased() { done += Double(f.size); progress = done / total; continue }
            var attempt = 0
            while true {
                do {
                    let (tmp, resp) = try await URLSession.shared.download(from: URL(string: f.url)!, delegate: nil)
                    guard (resp as? HTTPURLResponse)?.statusCode == 200 else { throw PackError("HTTP \((resp as? HTTPURLResponse)?.statusCode ?? 0)") }
                    let part = dir.appendingPathComponent(f.name + ".part")
                    try? FileManager.default.removeItem(at: part)
                    try FileManager.default.moveItem(at: tmp, to: part)
                    guard size(part) == f.size else { throw PackError("fichier incomplet \(f.name)") }
                    guard try sha256(part) == f.sha256.lowercased() else { try? FileManager.default.removeItem(at: part); throw PackError("empreinte invalide pour \(f.name)") }
                    try? FileManager.default.removeItem(at: dest)
                    try FileManager.default.moveItem(at: part, to: dest)
                    break
                } catch {
                    attempt += 1
                    if error is CancellationError || attempt >= 3 { throw error }
                    try await Task.sleep(nanoseconds: UInt64(2_000_000_000 * attempt))
                }
            }
            done += Double(f.size); progress = done / total
        }
        let tmp = dir.appendingPathComponent("manifest.json.tmp")
        try manifestText.write(to: tmp, atomically: true, encoding: .utf8)
        let manifestURL = dir.appendingPathComponent("manifest.json")
        try? FileManager.default.removeItem(at: manifestURL)
        try FileManager.default.moveItem(at: tmp, to: manifestURL)
        let keep = Set(files.map(\.name) + ["manifest.json"])
        for name in (try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? [] where !keep.contains(name) {
            try? FileManager.default.removeItem(at: dir.appendingPathComponent(name))
        }
    }

    private func size(_ url: URL) -> Int64 { ((try? FileManager.default.attributesOfItem(atPath: url.path)[.size]) as? NSNumber)?.int64Value ?? -1 }

    private func sha256(_ url: URL) throws -> String {
        let h = try FileHandle(forReadingFrom: url)
        defer { try? h.close() }
        var hasher = SHA256()
        while let chunk = try h.read(upToCount: 1 << 20), !chunk.isEmpty { hasher.update(data: chunk) }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }
}

struct PackError: LocalizedError {
    let message: String
    init(_ m: String) { message = m }
    var errorDescription: String? { message }
}
