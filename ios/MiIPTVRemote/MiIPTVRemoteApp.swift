import SwiftUI

@main
struct MiIPTVRemoteApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .preferredColorScheme(.dark)
                .tint(Theme.accent)
        }
    }
}

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        @Bindable var model = model
        ZStack {
            AppBackground()
            if model.paired != nil {
                MainTabView()
                    .transition(.opacity)
            } else {
                ConnectView()
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: model.paired == nil)
        .overlay(alignment: .top) {
            if let toast = model.toast {
                ToastView(text: toast.text)
                    .id(toast.id)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .task(id: toast.id) {
                        try? await Task.sleep(nanoseconds: 2_600_000_000)
                        withAnimation { if model.toast?.id == toast.id { model.toast = nil } }
                    }
            }
        }
        .animation(.spring(duration: 0.35), value: model.toast)
        .onChange(of: scenePhase, initial: true) { _, phase in
            switch phase {
            case .active: model.appBecameActive()
            case .background: model.appWentToBackground()
            default: break
            }
        }
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            RemoteView()
                .tabItem { Label("Remote", systemImage: "av.remote") }
            ChannelsView()
                .tabItem { Label("Channels", systemImage: "list.bullet.rectangle") }
            MoreView()
                .tabItem { Label("More", systemImage: "ellipsis.circle") }
        }
    }
}

/// Notices from the TV ("Added to favorites", "This channel can't be played"...).
struct ToastView: View {
    let text: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "tv")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Theme.accent)
            Text(verbatim: text)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Theme.text)
                .lineLimit(2)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(Capsule().strokeBorder(Theme.line, lineWidth: 1))
        .shadow(color: .black.opacity(0.35), radius: 16, y: 6)
        .padding(.top, 6)
        .padding(.horizontal, 24)
    }
}
