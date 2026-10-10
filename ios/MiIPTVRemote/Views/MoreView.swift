import SafariServices
import SwiftUI
import UIKit

struct IdentifiedURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

/// TV details, provider setup, sending links and unpairing.
struct MoreView: View {
    @Environment(AppModel.self) private var model
    @State private var link = ""
    @State private var sending = false
    @State private var linkResult: String?
    @State private var safari: IdentifiedURL?
    @State private var confirmDisconnect = false

    var body: some View {
        NavigationStack {
            Form {
                Section("TV") {
                    if let tv = model.paired {
                        LabeledContent("Name") { Text(verbatim: tv.name) }
                        LabeledContent("Address") { Text(verbatim: tv.address).monospacedDigit() }
                    }
                    if let status = model.state?.status, !status.isEmpty {
                        LabeledContent("Status") { Text(verbatim: status) }
                    }
                    if let setup = setupURL {
                        Button {
                            safari = IdentifiedURL(url: setup)
                        } label: {
                            Label("Set up the provider", systemImage: "slider.horizontal.3")
                        }
                    }
                    Button {
                        Haptics.tap()
                        model.reloadOnTV()
                    } label: {
                        Label("Reload channels on the TV", systemImage: "arrow.clockwise")
                    }
                }

                Section {
                    TextField("https://…", text: $link, axis: .vertical)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .lineLimit(1...3)
                    if link.isEmpty {
                        Button {
                            if let pasted = UIPasteboard.general.string { link = pasted.trimmingCharacters(in: .whitespacesAndNewlines) }
                        } label: {
                            Label("Paste", systemImage: "doc.on.clipboard")
                        }
                    } else {
                        Button {
                            send(asPlaylist: false)
                        } label: {
                            Label("Play on the TV", systemImage: "play.tv")
                        }
                        .disabled(!isLink || sending)
                        Button {
                            send(asPlaylist: true)
                        } label: {
                            Label("Use as the channel list", systemImage: "list.bullet.rectangle")
                        }
                        .disabled(!isLink || sending)
                    }
                    if let linkResult {
                        Text(verbatim: linkResult)
                            .font(.footnote)
                            .foregroundStyle(Theme.muted)
                    }
                } header: {
                    Text("Send a link")
                } footer: {
                    Text("Tip: in Safari or any other app, tap Share and choose Mi IPTV to send a link straight to the TV.")
                }

                Section {
                    Button(role: .destructive) {
                        confirmDisconnect = true
                    } label: {
                        Label("Unpair this phone", systemImage: "xmark.circle")
                    }
                } footer: {
                    Text("You can also forget all phones from the setup page on your computer.")
                }

                Section("About") {
                    LabeledContent("Version") { Text(verbatim: appVersion) }
                    Link(destination: URL(string: "https://github.com/NotYourCodeES/mi-iptv")!) {
                        Label("Source code on GitHub", systemImage: "chevron.left.forwardslash.chevron.right")
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(AppBackground())
            .navigationTitle("More")
            .sheet(item: $safari) { item in
                SafariView(url: item.url).ignoresSafeArea()
            }
            .confirmationDialog("Unpair this phone?", isPresented: $confirmDisconnect, titleVisibility: .visible) {
                Button("Unpair", role: .destructive) {
                    Task { await model.disconnect() }
                }
            } message: {
                Text("You'll need a new code from the TV to use it as a remote again.")
            }
        }
    }

    private var setupURL: URL? {
        if let s = model.state?.setupUrl, let url = URL(string: s) { return url }
        guard let tv = model.paired else { return nil }
        return URL(string: "http://\(tv.host):\(tv.port)")
    }

    private var isLink: Bool {
        let l = link.lowercased()
        return (l.hasPrefix("http://") || l.hasPrefix("https://")) && URL(string: link) != nil
    }

    private var appVersion: String {
        let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let b = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(v) (\(b))"
    }

    private func send(asPlaylist: Bool) {
        let url = link.trimmingCharacters(in: .whitespacesAndNewlines)
        sending = true
        linkResult = nil
        Task {
            let message = await model.share(url, asPlaylist: asPlaylist)
            sending = false
            linkResult = message
            Haptics.success()
        }
    }
}

/// The TV's own setup page (it's served by the TV, so it's always in sync with it).
struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let vc = SFSafariViewController(url: url)
        vc.preferredControlTintColor = UIColor(Theme.accent)
        vc.preferredBarTintColor = UIColor(Theme.bg)
        return vc
    }

    func updateUIViewController(_ vc: SFSafariViewController, context: Context) {}
}
