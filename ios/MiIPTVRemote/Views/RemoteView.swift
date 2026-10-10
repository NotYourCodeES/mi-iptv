import SwiftUI

/// The remote: what's on the TV, a touch surface (or D-pad) and the usual buttons.
struct RemoteView: View {
    @Environment(AppModel.self) private var model
    @AppStorage("useTouchpad") private var useTouchpad = true
    @State private var showKeypad = false
    @State private var showKeyboard = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                ConnectionBanner()
                NowPlayingCard()

                Group {
                    if useTouchpad {
                        TouchpadView { key in model.press(key) }
                    } else {
                        DPadView { key in model.press(key) }
                    }
                }
                .frame(minHeight: 150, maxHeight: 360)
                .layoutPriority(1)

                HStack {
                    RemoteKeyButton(systemImage: "arrow.uturn.backward", caption: "Back") { model.press(.back) }
                    Spacer()
                    RemoteKeyButton(systemImage: "star", caption: "Favorites") { model.press(.guide) }
                    Spacer()
                    RemoteKeyButton(systemImage: "info", caption: "Info") { model.press(.info) }
                }

                HStack(alignment: .center) {
                    Rocker(title: "CH", upImage: "chevron.up", downImage: "chevron.down",
                           up: { model.press(.channelUp) }, down: { model.press(.channelDown) })
                    Spacer()
                    VStack(spacing: 12) {
                        RemoteKeyButton(systemImage: model.state?.paused == true ? "play.fill" : "pause.fill",
                                        caption: nil, prominent: true) { model.press(.playPause) }
                        RemoteKeyButton(systemImage: "speaker.slash", caption: nil) { model.press(.mute) }
                    }
                    Spacer()
                    Rocker(title: "VOL", upImage: "plus", downImage: "minus",
                           up: { model.press(.volumeUp) }, down: { model.press(.volumeDown) })
                }

                HStack {
                    RemoteKeyButton(systemImage: "number", caption: "Number") { showKeypad = true }
                    Spacer()
                    RemoteKeyButton(systemImage: "keyboard", caption: "Type") { showKeyboard = true }
                    Spacer()
                    RemoteKeyButton(systemImage: "arrow.left.arrow.right", caption: "Previous") { model.press(.last) }
                }
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 10)
            .background(AppBackground())
            .navigationTitle(model.paired?.name ?? "")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Haptics.select()
                        withAnimation(.snappy) { useTouchpad.toggle() }
                    } label: {
                        Image(systemName: useTouchpad ? "dpad" : "hand.draw")
                    }
                    .accessibilityLabel(useTouchpad ? Text("Use arrow buttons") : Text("Use touch surface"))
                }
            }
            .sheet(isPresented: $showKeypad) {
                KeypadSheet()
                    .presentationDetents([.height(520), .large])
                    .presentationDragIndicator(.visible)
                    .presentationBackground(Theme.bg2)
            }
            .sheet(isPresented: $showKeyboard) {
                TypeOnTVSheet()
                    .presentationDetents([.medium, .large])
                    .presentationDragIndicator(.visible)
                    .presentationBackground(Theme.bg2)
            }
        }
    }
}

/// "Connecting…" / "No connection" strip above the remote.
struct ConnectionBanner: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        if model.connection != .online {
            HStack(spacing: 10) {
                if model.connection == .offline {
                    Image(systemName: "wifi.slash")
                        .foregroundStyle(Theme.gold)
                    Text("No connection with the TV. Retrying…")
                } else {
                    ProgressView()
                        .controlSize(.small)
                    Text("Connecting to the TV…")
                }
                Spacer()
            }
            .font(.footnote.weight(.medium))
            .foregroundStyle(Theme.text2)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.surface))
            .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}

/// What the TV is playing, with a favorite toggle.
struct NowPlayingCard: View {
    @Environment(AppModel.self) private var model
    @State private var setupURL: IdentifiedURL?

    var body: some View {
        HStack(spacing: 14) {
            content
        }
        .padding(14)
        .frame(maxWidth: .infinity, minHeight: 84, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Theme.surface)
                .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(Theme.line, lineWidth: 1))
        )
        .sheet(item: $setupURL) { item in
            SafariView(url: item.url).ignoresSafeArea()
        }
    }

    @ViewBuilder
    private var content: some View {
        let state = model.state
        if let channel = state?.channel, state?.screen != "background" {
            ChannelLogo(number: channel.n, logo: channel.logo, name: channel.name, size: 56)
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 8) {
                    if channel.n > 0 {
                        Text(verbatim: String(channel.n))
                            .font(.headline.monospacedDigit())
                            .foregroundStyle(Theme.accent)
                    }
                    Text(verbatim: channel.name)
                        .font(.headline)
                        .foregroundStyle(Theme.text)
                        .lineLimit(1)
                }
                HStack(spacing: 8) {
                    if state?.paused == true {
                        LivePill(text: "PAUSED", color: Theme.gold)
                    } else {
                        LivePill(text: "LIVE")
                    }
                    Text(verbatim: channel.group)
                        .font(.subheadline)
                        .foregroundStyle(Theme.muted)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            if channel.n > 0 {
                let fav = model.isFavorite(channel.name)
                Button {
                    model.toggleFavorite(number: channel.n, name: channel.name)
                } label: {
                    Image(systemName: fav ? "star.fill" : "star")
                        .font(.title3)
                        .foregroundStyle(fav ? Theme.gold : Theme.muted)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                        .symbolEffect(.bounce, value: fav)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(fav ? Text("Remove from favorites") : Text("Add to favorites"))
            }
        } else {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(Theme.accent)
                .frame(width: 56, height: 56)
                .background(RoundedRectangle(cornerRadius: 15, style: .continuous).fill(Theme.surface2))
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(Theme.text)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
            if state?.configured == false, let link = state?.setupUrl, let url = URL(string: link) {
                Button("Set up") { setupURL = IdentifiedURL(url: url) }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
            }
        }
    }

    private var icon: String {
        switch model.state?.screen {
        case "background": return "moon.zzz"
        case "welcome": return model.state?.loading == true ? "arrow.down.circle" : "slider.horizontal.3"
        default: return "tv"
        }
    }

    private var title: LocalizedStringKey {
        guard let state = model.state else { return "Mi IPTV" }
        if state.screen == "background" { return "Mi IPTV is closed on the TV" }
        if state.loading { return "Loading channels…" }
        if !state.configured { return "The TV needs your provider" }
        return "Pick a channel"
    }

    private var subtitle: LocalizedStringKey {
        guard let state = model.state else { return "Connecting…" }
        if state.screen == "background" { return "Open the app on the TV to use the remote." }
        if state.loading { return "This can take a few seconds." }
        if !state.configured { return "Add your M3U link or Xtream login." }
        return "Use the Channels tab or the remote."
    }
}

// MARK: - Buttons

/// Round remote button with an optional caption underneath.
struct RemoteKeyButton: View {
    let systemImage: String
    var caption: LocalizedStringKey?
    var prominent = false
    let action: () -> Void

    var body: some View {
        VStack(spacing: 6) {
            Button {
                Haptics.tap()
                action()
            } label: {
                Image(systemName: systemImage)
                    .font(.system(size: 19, weight: .semibold))
            }
            .buttonStyle(RoundKeyStyle(prominent: prominent))
            if let caption {
                Text(caption)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(Theme.muted)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

struct RoundKeyStyle: ButtonStyle {
    var prominent = false
    var diameter: CGFloat = 58

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(prominent ? Color.white : Theme.text)
            .frame(width: diameter, height: diameter)
            .background(
                Circle().fill(prominent ? AnyShapeStyle(Theme.gradient) : AnyShapeStyle(Theme.surface2))
            )
            .overlay(Circle().strokeBorder(prominent ? Color.clear : Theme.line, lineWidth: 1))
            .shadow(color: prominent ? Theme.accent.opacity(0.4) : .clear, radius: 12, y: 4)
            .scaleEffect(configuration.isPressed ? 0.9 : 1)
            .brightness(configuration.isPressed ? 0.08 : 0)
            .animation(.spring(duration: 0.18), value: configuration.isPressed)
            .contentShape(Circle())
    }
}

/// Tall up/down control for channels and volume. Holding a half repeats it.
struct Rocker: View {
    let title: LocalizedStringKey
    let upImage: String
    let downImage: String
    let up: () -> Void
    let down: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            RepeatButton(systemImage: upImage, action: up)
            Text(title)
                .font(.caption2.weight(.heavy))
                .tracking(1)
                .foregroundStyle(Theme.muted)
                .padding(.vertical, 2)
            RepeatButton(systemImage: downImage, action: down)
        }
        .frame(width: 66)
        .background(Capsule().fill(Theme.surface2))
        .overlay(Capsule().strokeBorder(Theme.line, lineWidth: 1))
    }
}

/// Fires once on touch, then repeats while held (for volume and channel zapping).
struct RepeatButton: View {
    let systemImage: String
    let action: () -> Void

    @State private var pressed = false
    @State private var repeater: Task<Void, Never>?

    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: 19, weight: .semibold))
            .foregroundStyle(Theme.text)
            .frame(width: 66, height: 58)
            .contentShape(Rectangle())
            .background(
                Capsule().fill(Theme.accent.opacity(pressed ? 0.22 : 0)).padding(4)
            )
            .scaleEffect(pressed ? 0.92 : 1)
            .animation(.spring(duration: 0.18), value: pressed)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        guard !pressed else { return }
                        pressed = true
                        fire()
                        repeater = Task {
                            try? await Task.sleep(nanoseconds: 450_000_000)
                            while !Task.isCancelled {
                                fire()
                                try? await Task.sleep(nanoseconds: 160_000_000)
                            }
                        }
                    }
                    .onEnded { _ in
                        pressed = false
                        repeater?.cancel()
                        repeater = nil
                    }
            )
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { action() }
    }

    private func fire() {
        Haptics.tick()
        action()
    }
}
