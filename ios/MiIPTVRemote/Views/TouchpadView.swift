import SwiftUI

/// Apple TV-style touch surface: swipe to move (keep dragging to keep moving), tap for OK,
/// touch and hold to add the channel to favorites.
struct TouchpadView: View {
    let onKey: (RemoteKey) -> Void

    private let step: CGFloat = 52          // distance that counts as one arrow press
    private let tapSlop: CGFloat = 12

    @State private var anchor: CGPoint?
    @State private var touch: CGPoint?
    @State private var moved = false
    @State private var longPressed = false
    @State private var holdTimer: Task<Void, Never>?

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .fill(
                    LinearGradient(colors: [Theme.surface2, Theme.surface],
                                   startPoint: .top, endPoint: .bottom)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 30, style: .continuous)
                        .strokeBorder(Theme.line, lineWidth: 1)
                )

            VStack(spacing: 10) {
                Image(systemName: "hand.point.up.left")
                    .font(.system(size: 26, weight: .light))
                Text("Swipe to move · Tap for OK\nHold for favorite")
                    .font(.footnote)
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(Theme.muted.opacity(0.75))
            .opacity(touch == nil ? 1 : 0)
            .animation(.easeOut(duration: 0.2), value: touch == nil)

            if let touch {
                Circle()
                    .fill(RadialGradient(colors: [Theme.accent.opacity(longPressed ? 0.6 : 0.4), .clear],
                                         center: .center, startRadius: 0, endRadius: 80))
                    .frame(width: 160, height: 160)
                    .position(touch)
                    .allowsHitTesting(false)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
        .gesture(
            DragGesture(minimumDistance: 0, coordinateSpace: .local)
                .onChanged(changed)
                .onEnded(ended)
        )
        .accessibilityElement()
        .accessibilityLabel(Text("Touch surface"))
        .accessibilityHint(Text("Swipe to move, double-tap for OK"))
        .accessibilityAction { onKey(.ok) }
    }

    private func changed(_ value: DragGesture.Value) {
        if anchor == nil {
            anchor = value.startLocation
            moved = false
            longPressed = false
            startHoldTimer()
        }
        touch = value.location
        if hypot(value.translation.width, value.translation.height) > tapSlop {
            holdTimer?.cancel()
        }
        guard let a = anchor, !longPressed else { return }
        let dx = value.location.x - a.x
        let dy = value.location.y - a.y
        if max(abs(dx), abs(dy)) >= step {
            moved = true
            send(abs(dx) > abs(dy) ? (dx > 0 ? .right : .left) : (dy > 0 ? .down : .up))
            anchor = value.location
        }
    }

    private func ended(_ value: DragGesture.Value) {
        holdTimer?.cancel()
        defer {
            anchor = nil
            withAnimation(.easeOut(duration: 0.25)) { touch = nil }
            longPressed = false
        }
        guard !longPressed, !moved else { return }
        let distance = hypot(value.translation.width, value.translation.height)
        if distance < tapSlop {
            Haptics.tap()
            onKey(.ok)
            return
        }
        // A quick flick shorter than one step still counts as a move
        let t = value.predictedEndTranslation
        if max(abs(t.width), abs(t.height)) >= step * 0.8 {
            send(abs(t.width) > abs(t.height) ? (t.width > 0 ? .right : .left) : (t.height > 0 ? .down : .up))
        }
    }

    private func send(_ key: RemoteKey) {
        Haptics.tick()
        onKey(key)
    }

    private func startHoldTimer() {
        holdTimer?.cancel()
        holdTimer = Task {
            try? await Task.sleep(nanoseconds: 650_000_000)
            guard !Task.isCancelled, !moved else { return }
            longPressed = true
            Haptics.press()
            onKey(.fav)
        }
    }
}

/// Classic arrow ring with OK in the middle (touch and hold OK for favorite).
struct DPadView: View {
    let onKey: (RemoteKey) -> Void

    @State private var okPressed = false

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            ZStack {
                Circle()
                    .fill(LinearGradient(colors: [Theme.surface2, Theme.surface], startPoint: .top, endPoint: .bottom))
                    .overlay(Circle().strokeBorder(Theme.line, lineWidth: 1))

                arrow("chevron.up", .up).position(x: size / 2, y: size * 0.15)
                arrow("chevron.down", .down).position(x: size / 2, y: size * 0.85)
                arrow("chevron.left", .left).position(x: size * 0.15, y: size / 2)
                arrow("chevron.right", .right).position(x: size * 0.85, y: size / 2)

                Text("OK")
                    .font(.system(size: size * 0.09, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .frame(width: size * 0.38, height: size * 0.38)
                    .background(Circle().fill(Theme.gradient))
                    .shadow(color: Theme.accent.opacity(0.45), radius: 16)
                    .scaleEffect(okPressed ? 0.93 : 1)
                    .animation(.spring(duration: 0.18), value: okPressed)
                    .position(x: size / 2, y: size / 2)
                    .onTapGesture {
                        Haptics.tap()
                        onKey(.ok)
                    }
                    .onLongPressGesture(minimumDuration: 0.6, perform: {
                        Haptics.press()
                        onKey(.fav)
                    }, onPressingChanged: { okPressed = $0 })
                    .accessibilityAddTraits(.isButton)
                    .accessibilityHint(Text("Touch and hold to add to favorites"))
            }
            .frame(width: size, height: size)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func arrow(_ image: String, _ key: RemoteKey) -> some View {
        Button {
            Haptics.tick()
            onKey(key)
        } label: {
            Image(systemName: image)
                .font(.system(size: 24, weight: .semibold))
                .frame(width: 72, height: 72)
                .contentShape(Rectangle())
        }
        .buttonStyle(ArrowStyle())
    }
}

private struct ArrowStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(configuration.isPressed ? Theme.accent : Theme.text)
            .scaleEffect(configuration.isPressed ? 0.88 : 1)
            .animation(.spring(duration: 0.15), value: configuration.isPressed)
    }
}
