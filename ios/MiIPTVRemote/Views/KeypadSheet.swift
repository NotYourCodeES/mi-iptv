import SwiftUI

/// Number pad: type a channel number and send it to the TV in one go.
struct KeypadSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var digits = ""

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 18), count: 3)

    var body: some View {
        VStack(spacing: 18) {
            VStack(spacing: 6) {
                Text(verbatim: digits.isEmpty ? "–" : digits)
                    .font(.system(size: 56, weight: .semibold, design: .rounded).monospacedDigit())
                    .foregroundStyle(digits.isEmpty ? Theme.muted : Theme.text)
                    .contentTransition(.numericText())
                    .animation(.snappy, value: digits)
                Group {
                    if let channel = target {
                        Text(verbatim: channel.name).foregroundStyle(Theme.accent)
                    } else if digits.isEmpty {
                        Text("Type a channel number").foregroundStyle(Theme.muted)
                    } else {
                        Text("There's no channel with that number").foregroundStyle(Theme.muted)
                    }
                }
                .font(.subheadline.weight(.medium))
                .lineLimit(1)
            }
            .padding(.top, 26)

            LazyVGrid(columns: columns, spacing: 14) {
                ForEach(1...9, id: \.self) { n in key(String(n)) }
                Button {
                    Haptics.tap()
                    if !digits.isEmpty { digits.removeLast() }
                } label: {
                    Image(systemName: "delete.left")
                }
                .buttonStyle(PadKeyStyle(kind: .secondary))
                .accessibilityLabel(Text("Delete"))
                key("0")
                Button {
                    go()
                } label: {
                    Image(systemName: "play.fill")
                }
                .buttonStyle(PadKeyStyle(kind: .primary))
                .disabled(Int(digits) == nil)
                .accessibilityLabel(Text("Watch"))
            }
            .padding(.horizontal, 28)

            Spacer(minLength: 0)
        }
    }

    private var target: TVChannel? {
        Int(digits).flatMap { model.channel(number: $0) }
    }

    private func key(_ d: String) -> some View {
        Button {
            Haptics.tap()
            if digits.count < 5 { digits.append(d) }
        } label: {
            Text(verbatim: d)
        }
        .buttonStyle(PadKeyStyle(kind: .digit))
    }

    private func go() {
        guard let n = Int(digits), n > 0 else { return }
        Haptics.success()
        model.play(number: n)
        dismiss()
    }
}

private struct PadKeyStyle: ButtonStyle {
    enum Kind { case digit, secondary, primary }
    let kind: Kind
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: kind == .digit ? 30 : 22, weight: .semibold, design: .rounded))
            .foregroundStyle(kind == .primary ? Color.white : Theme.text)
            .frame(maxWidth: .infinity)
            .frame(height: 62)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(kind == .primary ? AnyShapeStyle(Theme.gradient)
                          : AnyShapeStyle(kind == .digit ? Theme.surface2 : Theme.surface))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(kind == .primary ? Color.clear : Theme.line, lineWidth: 1)
            )
            .opacity(isEnabled ? 1 : 0.4)
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.spring(duration: 0.15), value: configuration.isPressed)
    }
}

/// Type on the phone, see the search on the TV (and pick a result from either).
struct TypeOnTVSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Type on the TV")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(Theme.text)
                Text("What you type shows up on the TV as a search. Pick a result here or with the remote.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
            }
            .padding(.top, 24)

            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(Theme.muted)
                TextField("Channel name or number", text: $text)
                    .focused($focused)
                    .submitLabel(.search)
                    .autocorrectionDisabled()
                    .onSubmit { model.searchOnTV(text) }
                if !text.isEmpty {
                    Button {
                        text = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(Theme.muted)
                    }
                    .accessibilityLabel(Text("Clear"))
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Theme.surface2))

            if !text.isEmpty {
                List {
                    ForEach(model.filteredChannels(query: text, filter: .all).prefix(40)) { channel in
                        ChannelRow(channel: channel, compact: true) { dismiss() }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .onAppear { focused = true }
        .task(id: text) {
            // Send after a short pause so the TV isn't searching on every keystroke
            guard !text.isEmpty else { return }
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard !Task.isCancelled else { return }
            model.searchOnTV(text)
        }
    }
}
