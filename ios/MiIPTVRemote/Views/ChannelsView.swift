import SwiftUI

/// The TV's channel list on the phone: search with the iPhone keyboard, tap to watch on the TV.
struct ChannelsView: View {
    @Environment(AppModel.self) private var model
    @State private var query = ""
    @State private var filter: ChannelFilter = .all
    @State private var didPickDefault = false

    var body: some View {
        NavigationStack {
            let results = model.filteredChannels(query: query, filter: filter)
            VStack(spacing: 0) {
                if !model.channels.isEmpty {
                    FilterChips(filter: $filter)
                }
                List {
                    ForEach(results) { channel in
                        ChannelRow(channel: channel)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .overlay {
                    if model.channels.isEmpty {
                        if model.loadingChannels {
                            ProgressView("Loading channels…")
                        } else {
                            ContentUnavailableView("No channels yet",
                                                   systemImage: "tv",
                                                   description: Text("When the TV has loaded your playlist, the channels appear here."))
                        }
                    } else if results.isEmpty {
                        if query.isEmpty, filter == .favorites {
                            ContentUnavailableView("No favorites yet",
                                                   systemImage: "star",
                                                   description: Text("Swipe a channel to the left, or hold OK on the TV, to add it."))
                        } else {
                            ContentUnavailableView.search(text: query)
                        }
                    }
                }
                .refreshable { await model.loadChannels() }
            }
            .background(AppBackground())
            .navigationTitle("Channels")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always),
                        prompt: Text("Name or number"))
            .toolbar {
                if !query.isEmpty {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            Haptics.tap()
                            model.searchOnTV(query)
                        } label: {
                            Label("Show on TV", systemImage: "tv")
                        }
                    }
                }
            }
            .onChange(of: model.channels.count, initial: true) { _, _ in
                // Start on Favorites when there are some, like the TV does
                if !didPickDefault, !model.channels.isEmpty {
                    didPickDefault = true
                    filter = model.favorites.isEmpty ? .all : .favorites
                }
            }
        }
    }
}

/// Favorites · All · categories, scrolling sideways.
private struct FilterChips: View {
    @Environment(AppModel.self) private var model
    @Binding var filter: ChannelFilter

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    chip(.favorites, systemImage: "star.fill") { Text("Favorites") }
                    chip(.all, systemImage: nil) { Text("All") }
                    ForEach(Array(model.groups.enumerated()), id: \.offset) { index, name in
                        chip(.group(index), systemImage: nil) { Text(verbatim: name) }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
            }
            .scrollIndicators(.hidden)
            .onChange(of: filter) { _, value in
                withAnimation { proxy.scrollTo(value, anchor: .center) }
            }
        }
    }

    private func chip<L: View>(_ value: ChannelFilter, systemImage: String?, @ViewBuilder label: () -> L) -> some View {
        let selected = filter == value
        return Button {
            Haptics.select()
            filter = value
        } label: {
            HStack(spacing: 6) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.caption)
                        .foregroundStyle(selected ? Color.white : Theme.gold)
                }
                label()
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
            }
            .foregroundStyle(selected ? Color.white : Theme.text2)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(
                Capsule().fill(selected ? AnyShapeStyle(Theme.gradient) : AnyShapeStyle(Theme.surface2))
            )
            .overlay(Capsule().strokeBorder(selected ? Color.clear : Theme.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .id(value)
    }
}

struct ChannelRow: View {
    @Environment(AppModel.self) private var model
    let channel: TVChannel
    var compact = false
    var onPlay: (() -> Void)?

    var body: some View {
        let fav = model.isFavorite(channel.name)
        let playing = model.isPlaying(channel)
        Button {
            model.play(channel)
            onPlay?()
        } label: {
            HStack(spacing: 12) {
                Text(verbatim: String(channel.n))
                    .font(.subheadline.monospacedDigit().weight(.semibold))
                    .foregroundStyle(Theme.accent)
                    .frame(minWidth: 34, alignment: .trailing)
                ChannelLogo(number: channel.n, logo: channel.logo, name: channel.name, size: compact ? 36 : 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: channel.name)
                        .font(.body.weight(.medium))
                        .foregroundStyle(Theme.text)
                        .lineLimit(1)
                    if !compact {
                        Text(verbatim: model.groupName(channel))
                            .font(.caption)
                            .foregroundStyle(Theme.muted)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 4)
                if playing {
                    LivePill(text: "ON TV")
                }
                if fav {
                    Image(systemName: "star.fill")
                        .font(.caption)
                        .foregroundStyle(Theme.gold)
                }
            }
            .padding(.vertical, compact ? 2 : 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .listRowBackground(playing ? Theme.accent.opacity(0.12) : Color.clear)
        .listRowSeparatorTint(Theme.line)
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button {
                model.toggleFavorite(channel)
            } label: {
                if fav {
                    Label("Remove", systemImage: "star.slash")
                } else {
                    Label("Favorite", systemImage: "star")
                }
            }
            .tint(fav ? Theme.muted : Theme.gold)
        }
        .contextMenu {
            Button {
                model.play(channel)
            } label: {
                Label("Watch on the TV", systemImage: "play.tv")
            }
            Button {
                model.toggleFavorite(channel)
            } label: {
                if fav {
                    Label("Remove from favorites", systemImage: "star.slash")
                } else {
                    Label("Add to favorites", systemImage: "star")
                }
            }
        }
    }
}
