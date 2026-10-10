import Foundation
import Observation
import UIKit

/// A TV we are about to pair with (shown in the PIN sheet).
struct PairingTarget: Identifiable, Equatable {
    let id: String
    let name: String
    let host: String
    let port: Int
}

enum PinResult: Equatable {
    case ok, wrong, expired
    case failed(String)
}

/// Which channels the Channels tab shows.
enum ChannelFilter: Hashable {
    case favorites, all
    case group(Int)
}

struct Toast: Identifiable, Equatable {
    let id = UUID()
    let text: String
}

/// Everything the app knows about the TV, and every action it can take on it.
@MainActor
@Observable
final class AppModel {
    enum Connection: Equatable { case idle, connecting, online, offline }

    // Finding and pairing
    var discovered: [DiscoveredTV] = []
    var discoveryProblem: String?
    var pairingTarget: PairingTarget?
    var connectError: String?
    var connectingTo: String?

    // The paired TV
    private(set) var paired: PairedTV?
    private(set) var connection: Connection = .idle
    private(set) var state: TVState?

    // Channel list
    private(set) var channels: [TVChannel] = []
    private(set) var groups: [String] = []
    private(set) var favorites: Set<String> = []
    private(set) var loadingChannels = false
    private var channelsVersion = -1
    private var folded: [String] = []           // channel names without case/accents, same order as `channels`
    private var byNumber: [Int: Int] = [:]      // channel number → index

    var toast: Toast?

    private(set) var client: TVClient?
    @ObservationIgnored private let discovery = Discovery()
    @ObservationIgnored private var eventsTask: Task<Void, Never>?
    @ObservationIgnored private var lastCommand: Task<Void, Never>?

    init() {
        paired = PairedTVStore.load()
        discovery.onChange = { [weak self] tvs in self?.discoveryChanged(tvs) }
        discovery.onProblem = { [weak self] problem in self?.discoveryProblem = problem }
    }

    // MARK: - Lifecycle

    func appBecameActive() {
        discovery.start()
        if paired != nil, connection != .online || eventsTask == nil { connect() }
    }

    func appWentToBackground() {
        eventsTask?.cancel()
        eventsTask = nil
        connection = .idle
        discovery.stop()
    }

    func retryDiscovery() {
        discovery.restart()
    }

    // MARK: - Pairing

    var deviceName: String { UIDevice.current.name }

    func choose(_ tv: DiscoveredTV) async {
        var host = tv.host
        var port = tv.port
        if host == nil || port == nil, let resolved = await Discovery.resolve(tv.endpoint) {
            host = host ?? resolved.host
            port = port ?? resolved.port
        }
        guard let host else {
            connectError = String(localized: "Couldn't find the TV's address. Try entering it by hand.")
            return
        }
        await beginPairing(host: host, port: port ?? 8080, fallbackName: tv.name, key: tv.id)
    }

    /// Accepts "192.168.1.50", "192.168.1.50:8080" or the full "http://192.168.1.50:8080" shown on the TV.
    func chooseManually(_ text: String) async {
        var t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if let range = t.range(of: "://") { t = String(t[range.upperBound...]) }
        if let slash = t.firstIndex(of: "/") { t = String(t[..<slash]) }
        var host = t
        var port = 8080
        if let colon = t.lastIndex(of: ":"), !t.hasPrefix("["), let p = Int(t[t.index(after: colon)...]) {
            host = String(t[..<colon])
            port = p
        }
        guard !host.isEmpty else { return }
        await beginPairing(host: host, port: port, fallbackName: host, key: host)
    }

    private func beginPairing(host: String, port: Int, fallbackName: String, key: String) async {
        connectError = nil
        connectingTo = key
        defer { connectingTo = nil }
        let client = TVClient(host: host, port: port, token: nil)
        do {
            let info = try await client.info()
            try await client.startPairing(device: deviceName)
            pairingTarget = PairingTarget(id: info.id, name: info.name.isEmpty ? fallbackName : info.name, host: host, port: port)
        } catch {
            connectError = error.localizedDescription
        }
    }

    /// Asks the TV to show the code again (or a new one if it expired).
    func requestNewCode(for target: PairingTarget) async {
        let client = TVClient(host: target.host, port: target.port, token: nil)
        try? await client.startPairing(device: deviceName)
    }

    func submitPin(_ pin: String, for target: PairingTarget) async -> PinResult {
        let client = TVClient(host: target.host, port: target.port, token: nil)
        do {
            let r = try await client.pair(pin: pin, device: deviceName)
            guard r.ok, let token = r.token else { return r.expired == true ? .expired : .wrong }
            let tv = PairedTV(id: r.id ?? target.id, name: r.name ?? target.name, host: target.host, port: target.port, token: token)
            PairedTVStore.save(tv)
            paired = tv
            pairingTarget = nil
            connect()
            return .ok
        } catch {
            return .failed(error.localizedDescription)
        }
    }

    /// Unpairs on the TV (if it's reachable) and goes back to the connect screen.
    func disconnect() async {
        if let client { try? await client.unpair() }
        forgetTV(message: nil)
    }

    private func forgetTV(message: String?) {
        eventsTask?.cancel()
        eventsTask = nil
        PairedTVStore.save(nil)
        paired = nil
        client = nil
        state = nil
        channels = []
        groups = []
        favorites = []
        folded = []
        byNumber = [:]
        channelsVersion = -1
        connection = .idle
        connectError = message
        discovery.start()
    }

    // MARK: - Connection

    func connect() {
        guard let tv = paired else { return }
        eventsTask?.cancel()
        let client = TVClient(host: tv.host, port: tv.port, token: tv.token)
        self.client = client
        connection = .connecting
        eventsTask = Task { [weak self] in await self?.listen(to: client) }
    }

    /// Keeps the event stream open, reconnecting with a growing delay when the TV goes away.
    private func listen(to client: TVClient) async {
        var delay: UInt64 = 1
        while !Task.isCancelled {
            do {
                for try await event in client.events() {
                    if connection != .online {
                        connection = .online
                        delay = 1
                    }
                    apply(event)
                }
            } catch TVError.unauthorized {
                forgetTV(message: TVError.unauthorized.errorDescription)
                return
            } catch {
                // Dropped or unreachable: try again below
            }
            if Task.isCancelled { return }
            connection = .offline
            try? await Task.sleep(nanoseconds: delay * 1_000_000_000)
            delay = min(delay * 2, 8)
        }
    }

    private func apply(_ event: TVEvent) {
        switch event.type {
        case "state":
            if let s = event.state {
                state = s
                if let fav = s.channel?.fav, let name = s.channel?.name {
                    if fav { favorites.insert(name) } else { favorites.remove(name) }
                }
                if s.version != channelsVersion { Task { await loadChannels() } }
            }
        case "channels":
            Task { await loadChannels() }
        case "favorites":
            if let f = event.favorites { favorites = Set(f) }
        case "toast":
            if let text = event.text { show(text) }
        default:
            break
        }
    }

    /// The TV moved to another IP (DHCP): follow it using its id.
    private func discoveryChanged(_ tvs: [DiscoveredTV]) {
        discovered = tvs
        guard var tv = paired, let found = tvs.first(where: { $0.id == tv.id }),
              let host = found.host, let port = found.port else { return }
        if host != tv.host || port != tv.port {
            tv.host = host
            tv.port = port
            paired = tv
            PairedTVStore.save(tv)
            connect()
        }
    }

    // MARK: - Channels

    func loadChannels() async {
        guard let client, !loadingChannels else { return }
        loadingChannels = true
        defer { loadingChannels = false }
        do {
            let list = try await client.channels()
            channels = list.channels
            groups = list.groups
            favorites = Set(list.favorites)
            channelsVersion = list.version
            folded = list.channels.map { Self.fold($0.name) }
            var index: [Int: Int] = [:]
            for (i, c) in list.channels.enumerated() { index[c.n] = i }
            byNumber = index
        } catch TVError.unauthorized {
            forgetTV(message: TVError.unauthorized.errorDescription)
        } catch {
            // Keep the previous list; it will be fetched again on the next state change
        }
    }

    func channel(number: Int) -> TVChannel? {
        byNumber[number].map { channels[$0] }
    }

    func groupName(_ channel: TVChannel) -> String {
        guard let g = channel.g, groups.indices.contains(g) else { return "" }
        return groups[g]
    }

    func isFavorite(_ name: String) -> Bool { favorites.contains(name) }

    func isPlaying(_ channel: TVChannel) -> Bool { state?.channel?.n == channel.n }

    func filteredChannels(query: String, filter: ChannelFilter) -> [TVChannel] {
        let q = Self.fold(query)
        let number = Int(q)
        var out: [TVChannel] = []
        for (i, c) in channels.enumerated() {
            switch filter {
            case .favorites: if !favorites.contains(c.name) { continue }
            case .group(let g): if c.g != g { continue }
            case .all: break
            }
            if q.isEmpty || c.n == number || folded[i].contains(q) { out.append(c) }
        }
        if let number, let i = out.firstIndex(where: { $0.n == number }), i > 0 {
            out.insert(out.remove(at: i), at: 0)
        }
        return out
    }

    nonisolated static func fold(_ s: String) -> String {
        s.folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: nil)
            .trimmingCharacters(in: .whitespaces)
    }

    // MARK: - Commands (sent one after another so fast taps arrive in order)

    func press(_ key: RemoteKey) {
        run { try await $0.key(key) }
    }

    func play(_ channel: TVChannel) {
        Haptics.select()
        run { try await $0.play(channel.n) }
    }

    func play(number: Int) {
        run { try await $0.play(number) }
    }

    func toggleFavorite(_ channel: TVChannel) {
        toggleFavorite(number: channel.n, name: channel.name)
    }

    func toggleFavorite(number: Int, name: String) {
        let on = !favorites.contains(name)
        if on { favorites.insert(name) } else { favorites.remove(name) } // instant feedback
        Haptics.press()
        run { client in
            _ = try await client.favorite(number, on: on)
            return nil
        }
    }

    func searchOnTV(_ query: String) {
        run { client in
            try await client.search(query)
            return nil
        }
    }

    func reloadOnTV() {
        run { client in
            if let message = try await client.reload() { await MainActor.run { self.show(message) } }
            return nil
        }
    }

    func share(_ url: String, asPlaylist: Bool) async -> String? {
        guard let client else { return String(localized: "Not connected to the TV.") }
        do {
            return try await client.share(url: url, title: nil, asPlaylist: asPlaylist) ?? String(localized: "Sent to the TV")
        } catch {
            return error.localizedDescription
        }
    }

    private func run(_ operation: @escaping (TVClient) async throws -> OkResponse?) {
        guard let client else { return }
        let previous = lastCommand
        lastCommand = Task { [weak self] in
            await previous?.value
            do {
                if let r = try await operation(client), !r.ok, let message = r.message {
                    self?.show(message)
                }
            } catch TVError.unauthorized {
                self?.forgetTV(message: TVError.unauthorized.errorDescription)
            } catch {
                self?.show(error.localizedDescription)
            }
        }
    }

    func show(_ text: String) {
        toast = Toast(text: text)
    }
}
