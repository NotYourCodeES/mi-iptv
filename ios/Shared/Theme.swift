import SwiftUI
import UIKit

/// Same palette as the TV app, so both feel like one product.
enum Theme {
    static let bg = Color(hex: 0x0A0E15)
    static let bg2 = Color(hex: 0x111827)
    static let surface = Color(hex: 0x141A24)
    static let surface2 = Color(hex: 0x1C2432)
    static let line = Color(hex: 0x252E3F)
    static let text = Color(hex: 0xF2F4F8)
    static let text2 = Color(hex: 0xC9D0DC)
    static let muted = Color(hex: 0x8590A3)
    static let accent = Color(hex: 0x5B8CFF)
    static let accent2 = Color(hex: 0x8B6CFF)
    static let gold = Color(hex: 0xFFC94D)
    static let live = Color(hex: 0xFF4D6A)

    static let gradient = LinearGradient(colors: [accent, accent2], startPoint: .topLeading, endPoint: .bottomTrailing)
}

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: opacity)
    }
}

/// Dark background with a soft glow at the top, like the TV's welcome screen.
struct AppBackground: View {
    var body: some View {
        ZStack {
            Theme.bg
            RadialGradient(colors: [Theme.accent.opacity(0.18), .clear],
                           center: .top, startRadius: 0, endRadius: 420)
            RadialGradient(colors: [Theme.accent2.opacity(0.10), .clear],
                           center: .topTrailing, startRadius: 0, endRadius: 320)
        }
        .ignoresSafeArea()
    }
}

/// Small "LIVE" / "PAUSED" / "ON TV" capsule.
struct LivePill: View {
    let text: LocalizedStringKey
    var color: Color = Theme.live

    var body: some View {
        HStack(spacing: 5) {
            Circle().fill(color).frame(width: 6, height: 6)
            Text(text)
                .font(.caption2.weight(.bold))
                .tracking(0.8)
        }
        .foregroundStyle(color)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(Capsule().fill(color.opacity(0.14)))
    }
}

enum Haptics {
    static func tap() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    static func tick() { UIImpactFeedbackGenerator(style: .soft).impactOccurred(intensity: 0.7) }
    static func press() { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
    static func select() { UISelectionFeedbackGenerator().selectionChanged() }
    static func success() { UINotificationFeedbackGenerator().notificationOccurred(.success) }
    static func error() { UINotificationFeedbackGenerator().notificationOccurred(.error) }
}
