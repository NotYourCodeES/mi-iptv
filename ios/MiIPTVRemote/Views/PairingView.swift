import SwiftUI

/// Enter the 4-digit code the TV is showing.
struct PairingView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let target: PairingTarget

    @State private var pin = ""
    @State private var error: String?
    @State private var busy = false
    @State private var attempts: CGFloat = 0
    @FocusState private var focused: Bool

    private let digits = 4

    var body: some View {
        VStack(spacing: 20) {
            VStack(spacing: 8) {
                Text("Enter the code on the TV")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(Theme.text)
                Text("“\(target.name)” is showing a \(String(digits))-digit code.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
                    .multilineTextAlignment(.center)
            }
            .padding(.top, 28)

            ZStack {
                // The real text field is invisible; the boxes show what's typed
                TextField("", text: $pin)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .focused($focused)
                    .frame(width: 1, height: 1)
                    .opacity(0.02)
                    .accessibilityLabel(Text("Code"))

                HStack(spacing: 12) {
                    ForEach(0..<digits, id: \.self) { i in
                        DigitBox(character: character(at: i), active: focused && i == min(pin.count, digits - 1))
                    }
                }
                .modifier(Shake(animatableData: attempts))
                .contentShape(Rectangle())
                .onTapGesture { focused = true }
            }

            Group {
                if busy {
                    ProgressView()
                } else if let error {
                    Text(verbatim: error)
                        .foregroundStyle(Theme.live)
                } else {
                    Text("Paired phones work as a remote until you unpair them.")
                        .foregroundStyle(Theme.muted)
                }
            }
            .font(.footnote)
            .multilineTextAlignment(.center)
            .frame(minHeight: 36)

            Button("Show a new code on the TV") {
                error = nil
                pin = ""
                Task {
                    if let message = await model.requestNewCode(for: target) { error = message }
                }
            }
            .font(.subheadline.weight(.semibold))

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 28)
        .onAppear { focused = true }
        .onChange(of: pin) { _, value in
            let clean = String(value.filter(\.isNumber).prefix(digits))
            if clean != value {
                pin = clean
                return
            }
            if clean.count == digits { submit(clean) }
        }
    }

    private func character(at index: Int) -> String? {
        guard index < pin.count else { return nil }
        return String(pin[pin.index(pin.startIndex, offsetBy: index)])
    }

    private func submit(_ code: String) {
        guard !busy else { return }
        busy = true
        error = nil
        Task {
            let result = await model.submitPin(code, for: target)
            busy = false
            switch result {
            case .ok:
                Haptics.success()
                dismiss()
            case .wrong:
                fail(String(localized: "That code isn't right. Try again."))
            case .expired:
                if let message = await model.requestNewCode(for: target) {
                    fail(message)
                } else {
                    fail(String(localized: "The code expired. There's a new one on the TV."))
                }
            case .failed(let message):
                fail(message)
            }
        }
    }

    private func fail(_ message: String) {
        Haptics.error()
        error = message
        pin = ""
        withAnimation(.default) { attempts += 1 }
    }
}

private struct DigitBox: View {
    let character: String?
    let active: Bool

    var body: some View {
        Text(verbatim: character ?? "")
            .font(.system(size: 34, weight: .semibold, design: .rounded).monospacedDigit())
            .foregroundStyle(Theme.text)
            .frame(width: 58, height: 70)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Theme.surface2)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(active ? Theme.accent : Theme.line, lineWidth: active ? 2 : 1)
            )
            .animation(.easeOut(duration: 0.15), value: active)
    }
}

/// Horizontal shake for a wrong code.
private struct Shake: GeometryEffect {
    var animatableData: CGFloat

    func effectValue(size: CGSize) -> ProjectionTransform {
        ProjectionTransform(CGAffineTransform(translationX: 10 * sin(animatableData * .pi * 4), y: 0))
    }
}
