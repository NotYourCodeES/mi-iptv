import SwiftUI
import UIKit

/// Loads channel logos once and keeps small thumbnails in memory.
/// HTTPS logos are fetched directly; plain-HTTP ones (blocked by iOS) come through the TV.
@MainActor
final class LogoStore {
    static let shared = LogoStore()

    private let cache = NSCache<NSString, UIImage>()
    private var inflight: [String: Task<UIImage?, Never>] = [:]

    init() {
        cache.countLimit = 600
    }

    func cached(_ url: String) -> UIImage? {
        cache.object(forKey: url as NSString)
    }

    func image(url: String, number: Int, client: TVClient?) async -> UIImage? {
        if let hit = cache.object(forKey: url as NSString) { return hit }
        if let running = inflight[url] { return await running.value }
        let task = Task<UIImage?, Never> {
            var data: Data?
            if url.lowercased().hasPrefix("https://"), let u = URL(string: url) {
                data = try? await URLSession.shared.data(from: u).0
            }
            if data == nil, number > 0, let client {
                data = try? await client.logo(number)
            }
            guard let data, let image = UIImage(data: data) else { return nil }
            return await image.byPreparingThumbnail(ofSize: CGSize(width: 128, height: 128)) ?? image
        }
        inflight[url] = task
        let image = await task.value
        inflight[url] = nil
        if let image { cache.setObject(image, forKey: url as NSString) }
        return image
    }
}

/// Square logo with the channel's initial as a placeholder (like the TV list).
struct ChannelLogo: View {
    @Environment(AppModel.self) private var model
    let number: Int
    let logo: String?
    let name: String
    var size: CGFloat = 44

    @State private var image: UIImage?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.26, style: .continuous)
                .fill(Theme.surface2)
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .padding(size * 0.14)
                    .transition(.opacity)
            } else {
                Text(verbatim: initial)
                    .font(.system(size: size * 0.4, weight: .semibold, design: .rounded))
                    .foregroundStyle(Theme.text2)
            }
        }
        .frame(width: size, height: size)
        .overlay(
            RoundedRectangle(cornerRadius: size * 0.26, style: .continuous)
                .strokeBorder(Theme.line, lineWidth: 1)
        )
        .task(id: "\(number)|\(logo ?? "")") {
            guard let logo, !logo.isEmpty else {
                image = nil
                return
            }
            if let hit = LogoStore.shared.cached(logo) {
                image = hit
                return
            }
            image = nil
            let loaded = await LogoStore.shared.image(url: logo, number: number, client: model.client)
            withAnimation(.easeOut(duration: 0.2)) { image = loaded }
        }
        .accessibilityHidden(true)
    }

    private var initial: String {
        name.first(where: { $0.isLetter || $0.isNumber }).map { String($0).uppercased() } ?? "·"
    }
}
