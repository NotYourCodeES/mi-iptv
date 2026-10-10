import Foundation

/// Remembers the paired TV. It lives in the App Group so the Share extension can send links too.
enum PairedTVStore {
    static let appGroup = "group.es.notyourcode.miiptv"
    private static let key = "pairedTV"

    private static var defaults: UserDefaults {
        UserDefaults(suiteName: appGroup) ?? .standard
    }

    static func load() -> PairedTV? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(PairedTV.self, from: data)
    }

    static func save(_ tv: PairedTV?) {
        if let tv, let data = try? JSONEncoder().encode(tv) {
            defaults.set(data, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }
}
