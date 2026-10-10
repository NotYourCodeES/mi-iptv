import SwiftUI
import UIKit

/// First screen: finds the TVs on the Wi-Fi and starts pairing.
struct ConnectView: View {
    @Environment(AppModel.self) private var model
    @State private var showManual = false
    @State private var manualAddress = ""

    var body: some View {
        @Bindable var model = model
        ScrollView {
            VStack(spacing: 28) {
                VStack(spacing: 14) {
                    RadarView()
                        .padding(.top, 24)
                    Text("Mi IPTV Remote")
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(Theme.text)
                    Text("Use your iPhone as the remote, keyboard and channel guide for Mi IPTV on your TV.")
                        .font(.callout)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(Theme.muted)
                        .padding(.horizontal, 12)
                }

                VStack(alignment: .leading, spacing: 12) {
                    Text("TVs on your Wi-Fi")
                        .font(.headline)
                        .foregroundStyle(Theme.text)

                    if model.discovered.isEmpty {
                        HStack(spacing: 14) {
                            ProgressView()
                            Text("Looking for Mi IPTV… Make sure the app is open on the TV.")
                                .font(.subheadline)
                                .foregroundStyle(Theme.text2)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                        .background(card)
                    }

                    ForEach(model.discovered) { tv in
                        Button {
                            Haptics.tap()
                            Task { await model.choose(tv) }
                        } label: {
                            TVRow(name: tv.name, address: tv.host, busy: model.connectingTo == tv.id)
                        }
                        .buttonStyle(.plain)
                        .disabled(model.connectingTo != nil)
                    }

                    if let problem = model.discoveryProblem {
                        VStack(alignment: .leading, spacing: 10) {
                            Label {
                                Text(verbatim: problem)
                            } icon: {
                                Image(systemName: "wifi.exclamationmark")
                            }
                            .font(.subheadline)
                            .foregroundStyle(Theme.gold)
                            Button("Open Settings") {
                                if let url = URL(string: UIApplication.openSettingsURLString) {
                                    UIApplication.shared.open(url)
                                }
                            }
                            .font(.subheadline.weight(.semibold))
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                        .background(card)
                    }
                }

                if let error = model.connectError {
                    Text(verbatim: error)
                        .font(.subheadline)
                        .foregroundStyle(Theme.live)
                        .multilineTextAlignment(.center)
                }

                VStack(spacing: 6) {
                    Button {
                        showManual = true
                    } label: {
                        Label("Enter the TV address", systemImage: "keyboard")
                            .font(.body.weight(.semibold))
                    }
                    Text("It's shown at the bottom of the TV screen, for example 192.168.1.50:8080.")
                        .font(.footnote)
                        .foregroundStyle(Theme.muted)
                        .multilineTextAlignment(.center)
                }
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 24)
        }
        .scrollIndicators(.hidden)
        .refreshable { model.retryDiscovery() }
        .alert("TV address", isPresented: $showManual) {
            TextField(text: $manualAddress, prompt: Text(verbatim: "192.168.1.50")) { EmptyView() }
                .keyboardType(.numbersAndPunctuation)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button("Connect") {
                let address = manualAddress
                Task { await model.chooseManually(address) }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Type the address shown on the TV.")
        }
        .sheet(item: $model.pairingTarget) { target in
            PairingView(target: target)
                .presentationDetents([.medium])
                .presentationDragIndicator(.visible)
                .presentationBackground(Theme.bg2)
        }
    }

    private var card: some View {
        RoundedRectangle(cornerRadius: 18, style: .continuous)
            .fill(Theme.surface)
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Theme.line, lineWidth: 1))
    }
}

private struct TVRow: View {
    let name: String
    let address: String?
    let busy: Bool

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Theme.gradient)
                Image(systemName: "tv")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.white)
            }
            .frame(width: 44, height: 44)

            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: name)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Theme.text)
                if let address {
                    Text(verbatim: address)
                        .font(.footnote.monospacedDigit())
                        .foregroundStyle(Theme.muted)
                }
            }
            Spacer()
            if busy {
                ProgressView()
            } else {
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.muted)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Theme.surface)
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Theme.line, lineWidth: 1))
        )
        .contentShape(Rectangle())
    }
}

/// Pulsing circles around a TV icon while we look for TVs.
struct RadarView: View {
    @State private var animate = false

    var body: some View {
        ZStack {
            ForEach(0..<3) { i in
                Circle()
                    .stroke(Theme.accent.opacity(0.45), lineWidth: 1.5)
                    .scaleEffect(animate ? 1 : 0.35)
                    .opacity(animate ? 0 : 0.9)
                    .animation(.easeOut(duration: 2.6).repeatForever(autoreverses: false).delay(Double(i) * 0.85),
                               value: animate)
            }
            Circle()
                .fill(Theme.gradient)
                .frame(width: 88, height: 88)
                .overlay(
                    Image(systemName: "av.remote.fill")
                        .font(.system(size: 34, weight: .semibold))
                        .foregroundStyle(.white)
                )
                .shadow(color: Theme.accent.opacity(0.55), radius: 26)
        }
        .frame(width: 190, height: 190)
        .onAppear { animate = true }
        .accessibilityHidden(true)
    }
}
