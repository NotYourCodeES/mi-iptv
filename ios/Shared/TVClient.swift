import Foundation

enum TVError: LocalizedError, Equatable {
    case unauthorized
    case notMiIPTV
    case disconnected
    case badResponse(Int)
    case message(String)

    var errorDescription: String? {
        switch self {
        case .unauthorized: return String(localized: "The TV no longer recognises this phone. Pair it again.")
        case .notMiIPTV: return String(localized: "That address doesn't answer like Mi IPTV. Check it on the TV screen.")
        case .disconnected: return String(localized: "Lost the connection with the TV.")
        case .badResponse: return String(localized: "The TV sent an unexpected answer. Update Mi IPTV on the TV.")
        case .message(let text): return text
        }
    }
}

/// Talks to the Mi IPTV app on the TV over the local network (see RemoteApi.java on the TV side).
final class TVClient: @unchecked Sendable {
    let host: String
    let port: Int
    let token: String?
    private let session: URLSession
    private let decoder = JSONDecoder()

    init(host: String, port: Int, token: String?) {
        self.host = host
        self.port = port
        self.token = token
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 6
        config.waitsForConnectivity = false
        config.httpMaximumConnectionsPerHost = 6
        session = URLSession(configuration: config)
    }

    deinit {
        session.invalidateAndCancel()
    }

    var baseURL: URL {
        let h = host.contains(":") && !host.hasPrefix("[") ? "[\(host)]" : host
        return URL(string: "http://\(h):\(port)")!
    }

    // MARK: - Pairing

    func info() async throws -> TVInfo {
        let info: TVInfo
        do {
            info = try await send(request("/api/remote/info"))
        } catch let error as TVError where error != .unauthorized {
            throw error == .disconnected ? error : TVError.notMiIPTV
        }
        guard info.app == "mi-iptv" else { throw TVError.notMiIPTV }
        return info
    }

    func startPairing(device: String) async throws {
        let _: OkResponse = try await send(request("/api/remote/pair/start", method: "POST", form: ["device": device]))
    }

    func pair(pin: String, device: String) async throws -> PairResponse {
        try await send(request("/api/remote/pair", method: "POST", form: ["pin": pin, "device": device]))
    }

    func unpair() async throws {
        let _: OkResponse = try await send(request("/api/remote/unpair", method: "POST"))
    }

    // MARK: - Reading

    func state() async throws -> TVState {
        try await send(request("/api/remote/state"))
    }

    func channels() async throws -> ChannelList {
        var req = request("/api/remote/channels")
        req.timeoutInterval = 30 // big playlists take a moment
        return try await send(req)
    }

    func logo(_ number: Int) async throws -> Data {
        var req = request("/api/remote/logo", query: ["n": String(number)])
        req.timeoutInterval = 15
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { throw TVError.badResponse(0) }
        return data
    }

    /// Live updates from the TV. Ends with an error when the connection drops.
    func events() -> AsyncThrowingStream<TVEvent, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    var req = self.request("/api/remote/events")
                    req.timeoutInterval = 45 // the TV sends a keep-alive every 15 s
                    let (bytes, response) = try await self.session.bytes(for: req)
                    guard let http = response as? HTTPURLResponse else { throw TVError.disconnected }
                    if http.statusCode == 401 { throw TVError.unauthorized }
                    guard http.statusCode == 200 else { throw TVError.badResponse(http.statusCode) }
                    for try await line in bytes.lines {
                        guard line.hasPrefix("data:") else { continue }
                        let json = line.dropFirst(5).trimmingCharacters(in: .whitespaces)
                        if let event = try? self.decoder.decode(TVEvent.self, from: Data(json.utf8)) {
                            continuation.yield(event)
                        }
                    }
                    continuation.finish(throwing: TVError.disconnected)
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    // MARK: - Commands

    @discardableResult
    func key(_ key: RemoteKey) async throws -> OkResponse {
        try await send(request("/api/remote/key", method: "POST", form: ["key": key.rawValue]))
    }

    /// Digits typed on the phone's keypad are sent as a channel number in one go.
    @discardableResult
    func play(_ number: Int) async throws -> OkResponse {
        try await send(request("/api/remote/play", method: "POST", form: ["n": String(number)]))
    }

    func favorite(_ number: Int, on: Bool?) async throws -> Bool {
        var form = ["n": String(number)]
        if let on { form["on"] = on ? "1" : "0" }
        let r: OkResponse = try await send(request("/api/remote/favorite", method: "POST", form: form))
        return r.favorite ?? false
    }

    func search(_ query: String) async throws {
        let _: OkResponse = try await send(request("/api/remote/search", method: "POST", form: ["q": query]))
    }

    /// Sends a link to the TV: played straight away, or loaded as the channel list.
    func share(url: String, title: String?, asPlaylist: Bool) async throws -> String? {
        var form = ["url": url, "as": asPlaylist ? "playlist" : "play"]
        if let title, !title.isEmpty { form["title"] = title }
        let r: OkResponse = try await send(request("/api/remote/share", method: "POST", form: form))
        if !r.ok { throw TVError.message(r.message ?? String(localized: "The TV couldn't open that link.")) }
        return r.message
    }

    func reload() async throws -> String? {
        let r: OkResponse = try await send(request("/api/remote/reload", method: "POST"))
        return r.message
    }

    // MARK: - Plumbing

    private func request(_ path: String, method: String = "GET", query: [String: String] = [:], form: [String: String] = [:]) -> URLRequest {
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty {
            components.queryItems = query.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        var req = URLRequest(url: components.url!)
        req.httpMethod = method
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if method == "POST" {
            req.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
            req.httpBody = Data(Self.formEncode(form).utf8)
        }
        return req
    }

    private func send<T: Decodable>(_ req: URLRequest) async throws -> T {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: req)
        } catch let error as URLError where error.code != .cancelled {
            throw TVError.disconnected
        }
        guard let http = response as? HTTPURLResponse else { throw TVError.disconnected }
        if http.statusCode == 401 { throw TVError.unauthorized }
        if http.statusCode >= 400 {
            if let r = try? decoder.decode(OkResponse.self, from: data), let message = r.message {
                throw TVError.message(message)
            }
            throw TVError.badResponse(http.statusCode)
        }
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw TVError.badResponse(http.statusCode)
        }
    }

    static func formEncode(_ form: [String: String]) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return form.sorted { $0.key < $1.key }.map { key, value in
            let k = key.addingPercentEncoding(withAllowedCharacters: allowed) ?? key
            let v = value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
            return "\(k)=\(v)"
        }.joined(separator: "&")
    }
}
