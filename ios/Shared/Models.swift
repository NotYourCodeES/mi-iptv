import Foundation

/// A channel as listed by the TV. `n` is its number in the playlist (1-based).
struct TVChannel: Codable, Hashable, Identifiable {
    let n: Int
    let name: String
    let g: Int?
    let logo: String?

    var id: Int { n }
}

/// The whole channel list (`GET /api/remote/channels`).
struct ChannelList: Codable {
    let version: Int
    let groups: [String]
    let channels: [TVChannel]
    let favorites: [String]
}

/// The channel the TV is showing. `n` is 0 for a link sent from the phone.
struct NowPlaying: Codable, Hashable {
    let n: Int
    let name: String
    let group: String
    let logo: String?
    let fav: Bool
}

/// What is on the TV screen right now.
struct TVState: Codable, Equatable {
    /// "welcome", "list", "full" or "background" (the app is not open on the TV)
    var screen: String = "full"
    var channel: NowPlaying?
    var paused = false
    var loading = false
    var configured = true
    var status = ""
    var channelCount = 0
    var version = 0
    var search: String?
    var setupUrl: String?

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        screen = try c.decodeIfPresent(String.self, forKey: .screen) ?? "full"
        channel = try c.decodeIfPresent(NowPlaying.self, forKey: .channel)
        paused = try c.decodeIfPresent(Bool.self, forKey: .paused) ?? false
        loading = try c.decodeIfPresent(Bool.self, forKey: .loading) ?? false
        configured = try c.decodeIfPresent(Bool.self, forKey: .configured) ?? true
        status = try c.decodeIfPresent(String.self, forKey: .status) ?? ""
        channelCount = try c.decodeIfPresent(Int.self, forKey: .channelCount) ?? 0
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 0
        search = try c.decodeIfPresent(String.self, forKey: .search)
        setupUrl = try c.decodeIfPresent(String.self, forKey: .setupUrl)
    }
}

/// One message of the TV's event stream.
struct TVEvent: Decodable {
    let type: String
    let state: TVState?
    let version: Int?
    let favorites: [String]?
    let text: String?
}

struct TVInfo: Decodable {
    let app: String
    let api: Int
    let id: String
    let name: String
    let version: String
    let paired: Bool
}

struct PairResponse: Decodable {
    let ok: Bool
    let token: String?
    let id: String?
    let name: String?
    let expired: Bool?
}

struct OkResponse: Decodable {
    let ok: Bool
    let message: String?
    let favorite: Bool?
}

/// The TV this phone is paired with (kept in the shared App Group so the Share extension can use it).
struct PairedTV: Codable, Equatable {
    var id: String
    var name: String
    var host: String
    var port: Int
    var token: String

    var address: String { port == 8080 ? host : "\(host):\(port)" }
}

/// Buttons the TV understands (the same as the physical remote).
enum RemoteKey: String {
    case up, down, left, right, ok, back, menu, info, guide, fav
    case channelUp = "chup", channelDown = "chdown", last, playPause = "playpause"
    case volumeUp = "volup", volumeDown = "voldown", mute
}
