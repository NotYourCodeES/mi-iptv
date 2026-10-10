import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// "Share → Mi IPTV": sends the shared link to the paired TV.
final class ShareViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        Task { @MainActor in
            let (url, title) = await sharedLink()
            let root = ShareView(url: url, title: title, tv: PairedTVStore.load()) { [weak self] in
                self?.extensionContext?.completeRequest(returningItems: nil)
            }
            let host = UIHostingController(rootView: root)
            host.view.backgroundColor = .clear
            addChild(host)
            host.view.frame = view.bounds
            host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            view.addSubview(host.view)
            host.didMove(toParent: self)
        }
    }

    /// The first web link among the shared items (a URL, or a URL inside shared text).
    private func sharedLink() async -> (URL?, String?) {
        let items = extensionContext?.inputItems as? [NSExtensionItem] ?? []
        let title = items.first?.attributedTitle?.string ?? items.first?.attributedContentText?.string
        for item in items {
            for provider in item.attachments ?? [] {
                if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier),
                   let value = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier),
                   let url = (value as? URL) ?? (value as? String).flatMap({ URL(string: $0) }),
                   isWebLink(url) {
                    return (url, title)
                }
                if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier),
                   let value = try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier),
                   let text = value as? String,
                   let url = firstLink(in: text) {
                    return (url, title)
                }
            }
        }
        return (nil, title)
    }

    private func isWebLink(_ url: URL) -> Bool {
        let scheme = url.scheme?.lowercased()
        return scheme == "http" || scheme == "https"
    }

    private func firstLink(in text: String) -> URL? {
        guard let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue) else { return nil }
        let range = NSRange(text.startIndex..., in: text)
        return detector.matches(in: text, range: range).compactMap(\.url).first(where: { self.isWebLink($0) })
    }
}

struct ShareView: View {
    let url: URL?
    let title: String?
    let tv: PairedTV?
    let close: () -> Void

    enum Phase: Equatable {
        case ready, sending, done(String), failed(String)
    }

    @State private var phase: Phase = .ready

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.4)
                .ignoresSafeArea()
                .onTapGesture(perform: close)

            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Theme.gradient)
                        Image(systemName: "tv").font(.system(size: 16, weight: .semibold)).foregroundStyle(.white)
                    }
                    .frame(width: 36, height: 36)
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Send to the TV")
                            .font(.headline)
                            .foregroundStyle(Theme.text)
                        if let tv {
                            Text(verbatim: tv.name)
                                .font(.footnote)
                                .foregroundStyle(Theme.muted)
                        }
                    }
                    Spacer()
                    Button(action: close) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .foregroundStyle(Theme.muted)
                    }
                    .accessibilityLabel(Text("Close"))
                }

                if tv == nil {
                    message("Open Mi IPTV Remote and connect it to your TV first.", icon: "exclamationmark.triangle")
                } else if let url {
                    Text(verbatim: url.absoluteString)
                        .font(.footnote.monospaced())
                        .foregroundStyle(Theme.text2)
                        .lineLimit(3)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.surface2))

                    switch phase {
                    case .ready, .failed:
                        VStack(spacing: 10) {
                            actionButton("Play on the TV", icon: "play.fill", primary: true) { send(asPlaylist: false) }
                            actionButton("Use as the channel list", icon: "list.bullet.rectangle", primary: false) { send(asPlaylist: true) }
                        }
                        if case .failed(let error) = phase {
                            Text(verbatim: error)
                                .font(.footnote)
                                .foregroundStyle(Theme.live)
                        }
                    case .sending:
                        HStack {
                            Spacer()
                            ProgressView()
                            Spacer()
                        }
                        .frame(height: 104)
                    case .done(let text):
                        HStack(spacing: 10) {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.green)
                            Text(verbatim: text).foregroundStyle(Theme.text)
                        }
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 104)
                    }
                } else {
                    message("There's no web link to send.", icon: "link")
                }
            }
            .padding(20)
            .background(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(Theme.bg2)
                    .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(Theme.line, lineWidth: 1))
            )
            .padding(12)
        }
        .preferredColorScheme(.dark)
        .tint(Theme.accent)
    }

    private func message(_ text: LocalizedStringKey, icon: String) -> some View {
        Label(text, systemImage: icon)
            .font(.subheadline)
            .foregroundStyle(Theme.text2)
            .padding(.vertical, 8)
    }

    private func actionButton(_ title: LocalizedStringKey, icon: String, primary: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: icon)
                .font(.body.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .foregroundStyle(primary ? Color.white : Theme.text)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(primary ? AnyShapeStyle(Theme.gradient) : AnyShapeStyle(Theme.surface2))
                )
        }
        .buttonStyle(.plain)
    }

    private func send(asPlaylist: Bool) {
        guard let tv, let url else { return }
        phase = .sending
        Task {
            let client = TVClient(host: tv.host, port: tv.port, token: tv.token)
            do {
                let message = try await client.share(url: url.absoluteString, title: title, asPlaylist: asPlaylist)
                phase = .done(message ?? String(localized: "Sent to the TV"))
                Haptics.success()
                try? await Task.sleep(nanoseconds: 900_000_000)
                close()
            } catch {
                phase = .failed(error.localizedDescription)
                Haptics.error()
            }
        }
    }
}
