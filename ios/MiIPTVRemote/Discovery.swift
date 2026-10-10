import Foundation
import Network

/// A Mi IPTV TV found on the Wi-Fi with Bonjour.
struct DiscoveredTV: Identifiable, Equatable {
    let id: String          // the TV's own id (stable), or the service name for older versions
    let name: String
    let host: String?       // from the TXT record; resolved on demand otherwise
    let port: Int?
    let endpoint: NWEndpoint
}

/// Browses for `_miiptv._tcp` services (the TV announces itself with Android's NsdManager).
@MainActor
final class Discovery {
    static let serviceType = "_miiptv._tcp"

    var onChange: (([DiscoveredTV]) -> Void)?
    /// Set when browsing can't work (usually because Local Network access was denied).
    var onProblem: ((String?) -> Void)?

    private var browser: NWBrowser?

    func start() {
        guard browser == nil else { return }
        let params = NWParameters()
        params.includePeerToPeer = false
        let browser = NWBrowser(for: .bonjourWithTXTRecord(type: Self.serviceType, domain: nil), using: params)
        browser.browseResultsChangedHandler = { [weak self] results, _ in
            let tvs = results.compactMap(Self.makeTV).sorted { $0.name.localizedCompare($1.name) == .orderedAscending }
            Task { @MainActor in self?.onChange?(tvs) }
        }
        browser.stateUpdateHandler = { [weak self] state in
            Task { @MainActor in self?.handle(state) }
        }
        browser.start(queue: .main)
        self.browser = browser
    }

    func stop() {
        browser?.cancel()
        browser = nil
    }

    func restart() {
        stop()
        start()
    }

    private func handle(_ state: NWBrowser.State) {
        switch state {
        case .ready:
            onProblem?(nil)
        case .waiting(let error), .failed(let error):
            // -65570 is kDNSServiceErr_PolicyDenied: the user hasn't allowed Local Network access
            if case .dns(let code) = error, code == -65570 {
                onProblem?(String(localized: "Allow Local Network access for Mi IPTV Remote in Settings to find your TV."))
            } else {
                onProblem?(error.localizedDescription)
            }
            if case .failed = state {
                browser = nil
            }
        default:
            break
        }
    }

    private nonisolated static func makeTV(_ result: NWBrowser.Result) -> DiscoveredTV? {
        guard case let .service(name, _, _, _) = result.endpoint else { return nil }
        var id = name
        var host: String?
        var port: Int?
        if case let .bonjour(txt) = result.metadata {
            if let value = txt["id"], !value.isEmpty { id = value }
            host = txt["ip"]
            port = txt["port"].flatMap { Int($0) }
        }
        return DiscoveredTV(id: id, name: name, host: host, port: port, endpoint: result.endpoint)
    }

    /// Finds the IPv4 address and port of a service when its TXT record doesn't carry them.
    nonisolated static func resolve(_ endpoint: NWEndpoint) async -> (host: String, port: Int)? {
        await withCheckedContinuation { continuation in
            let queue = DispatchQueue(label: "miiptv.resolve")
            let params = NWParameters.tcp
            if let ip = params.defaultProtocolStack.internetProtocol as? NWProtocolIP.Options {
                ip.version = .v4
            }
            let connection = NWConnection(to: endpoint, using: params)
            let once = ResumeOnce()
            let finish: @Sendable ((host: String, port: Int)?) -> Void = { value in
                guard once.claim() else { return }
                connection.cancel()
                continuation.resume(returning: value)
            }
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    if case let .hostPort(host, port) = connection.currentPath?.remoteEndpoint {
                        var text = "\(host)"
                        if let percent = text.firstIndex(of: "%") { text = String(text[..<percent]) }
                        finish((text, Int(port.rawValue)))
                    } else {
                        finish(nil)
                    }
                case .failed, .cancelled:
                    finish(nil)
                default:
                    break
                }
            }
            connection.start(queue: queue)
            queue.asyncAfter(deadline: .now() + 5) { finish(nil) }
        }
    }
}

/// Makes sure a continuation is resumed only once, whichever callback gets there first.
private final class ResumeOnce: @unchecked Sendable {
    private let lock = NSLock()
    private var done = false

    func claim() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if done { return false }
        done = true
        return true
    }
}
