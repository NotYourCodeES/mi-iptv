package es.mitv.iptv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import es.mitv.iptv.core.Channel;
import es.mitv.iptv.core.ChannelSearch;
import es.mitv.iptv.core.ConfigServer;
import es.mitv.iptv.core.IptvConfig;
import es.mitv.iptv.core.LogoCache;
import es.mitv.iptv.core.M3uParser;
import es.mitv.iptv.core.Pairing;
import es.mitv.iptv.core.RemoteApi;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends Activity implements ConfigServer.Listener {

    static final String APP_VERSION = "1.4.0";
    static final String USER_AGENT = "MiIPTV/" + APP_VERSION + " (Android TV)";
    private static final String CACHE_FILE = "playlist.m3u";
    private static final long PREVIEW_DELAY_MS = 650;
    private static final long EXIT_WINDOW_MS = 2500;
    private static final long OSD_MS = 5000;
    private static final long NUMBER_ENTRY_MS = 1800;
    private static final String SEARCH_GROUP = "\u0000search";

    private static final int TYPE_FAV = 0, TYPE_ALL = 1, TYPE_GROUP = 2, TYPE_SEARCH = 3;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private SharedPreferences prefs;
    private ExoPlayer player;
    private ConfigServer server;
    private RemoteApi remote;
    private RemoteDiscovery discovery;
    private AudioManager audio;
    private ImageLoader images;

    // Views
    private PlayerView playerFull, playerPreview;
    private View panel, welcome, osd, welcomeProgress, previewHint;
    private ListView groupList, channelList;
    private TextView statusText, clockText, footerUrl, channelsTitle, toastText;
    private TextView previewName, previewMeta, previewFav;
    private TextView osdNumber, osdName, osdGroup, osdClock, osdFav, osdInitial;
    private ImageView osdLogo, welcomeQr;
    private TextView welcomeTitle, welcomeText, welcomeUrl, welcomeStatus;
    private TextView channelsEmpty, numberEntry;
    private View pairingOverlay;
    private TextView pairingCode, pairingText;

    private GroupAdapter groupAdapter;
    private ChannelAdapter channelAdapter;

    // State
    private volatile IptvConfig config;
    private volatile String status = "";
    private boolean loading;
    private volatile List<Channel> allChannels = Collections.emptyList();
    private final List<GroupItem> groups = new ArrayList<>();
    private int currentGroup = -1;
    private List<Channel> shownChannels = new ArrayList<>();
    private List<Channel> zapList = new ArrayList<>();
    private int zapIndex = -1;
    private Channel committed;      // channel currently being watched
    private Channel previous;       // the one before it (Last channel button)
    private boolean paused;
    private boolean started;
    private String searchQuery;     // search typed from the phone, shown as a category
    private int channelsVersion;
    private final StringBuilder typedNumber = new StringBuilder();
    private String loadedUrl;       // what the player has loaded right now (may be a preview)
    private Set<String> favs = new HashSet<>();
    private boolean retried;
    private boolean longPressHandled;
    private long lastBackTime;
    private Channel pendingPreview;

    private final Runnable hideOsd = () -> osd.setVisibility(View.GONE);
    private final Runnable hideToast = () -> toastText.setVisibility(View.GONE);
    private final Runnable hidePairing = () -> pairingOverlay.setVisibility(View.GONE);
    private final Runnable goToTypedNumber = this::goToTypedNumber;
    private final Runnable previewTask = () -> {
        if (pendingPreview != null && panel.getVisibility() == View.VISIBLE) load(pendingPreview.url);
    };
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            String now = clockFormat.format(new Date());
            clockText.setText(now);
            osdClock.setText(now);
            ui.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        status = getString(R.string.status_waiting);
        prefs = getSharedPreferences("iptv", MODE_PRIVATE);
        config = loadConfig();
        favs = new HashSet<>(prefs.getStringSet("favs", Collections.<String>emptySet()));
        images = new ImageLoader(dp(96), USER_AGENT);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);

        bindViews();
        setupPlayer();
        setupLists();
        startServer();
        ui.post(clockTick);

        refreshTexts();
        if (config.isComplete()) loadPlaylist(true);
    }

    private void bindViews() {
        playerFull = findViewById(R.id.playerFull);
        playerPreview = findViewById(R.id.playerPreview);
        View previewFrame = findViewById(R.id.previewFrame);
        previewFrame.setClipToOutline(true);

        panel = findViewById(R.id.panel);
        welcome = findViewById(R.id.welcome);
        osd = findViewById(R.id.osd);
        groupList = findViewById(R.id.groupList);
        channelList = findViewById(R.id.channelList);
        channelsEmpty = findViewById(R.id.channelsEmpty);
        channelList.setEmptyView(channelsEmpty);
        numberEntry = findViewById(R.id.numberEntry);
        pairingOverlay = findViewById(R.id.pairingOverlay);
        pairingCode = findViewById(R.id.pairingCode);
        pairingText = findViewById(R.id.pairingText);
        statusText = findViewById(R.id.statusText);
        clockText = findViewById(R.id.clockText);
        footerUrl = findViewById(R.id.footerUrl);
        channelsTitle = findViewById(R.id.channelsTitle);
        toastText = findViewById(R.id.toastText);

        previewName = findViewById(R.id.previewName);
        previewMeta = findViewById(R.id.previewMeta);
        previewFav = findViewById(R.id.previewFav);
        previewHint = findViewById(R.id.previewHint);

        osdNumber = findViewById(R.id.osdNumber);
        osdName = findViewById(R.id.osdName);
        osdGroup = findViewById(R.id.osdGroup);
        osdClock = findViewById(R.id.osdClock);
        osdFav = findViewById(R.id.osdFav);
        osdLogo = findViewById(R.id.osdLogo);
        osdInitial = findViewById(R.id.osdInitial);

        welcomeTitle = findViewById(R.id.welcomeTitle);
        welcomeText = findViewById(R.id.welcomeText);
        welcomeUrl = findViewById(R.id.welcomeUrl);
        welcomeStatus = findViewById(R.id.welcomeStatus);
        welcomeProgress = findViewById(R.id.welcomeProgress);
        welcomeQr = findViewById(R.id.welcomeQr);
    }

    // ---------------------------------------------------------------- Player

    private void setupPlayer() {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000);
        // Prefer the bundled FFmpeg decoder so Dolby (AC-3 / E-AC-3) audio always plays
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true);
        player = new ExoPlayer.Builder(this, renderers)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .build();
        playerFull.setUseController(false);
        playerPreview.setUseController(false);
        playerFull.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                if (!retried && loadedUrl != null) {
                    retried = true;
                    ui.postDelayed(() -> { player.prepare(); player.play(); }, 2000);
                } else {
                    toast(getString(R.string.toast_cannot_play));
                }
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) retried = false;
            }
        });
    }

    /** Loads a stream URL into the player (no-op if it is already playing). */
    private void load(String url) {
        if (url == null) return;
        if (url.equals(loadedUrl)) {
            if (player.getPlaybackState() == Player.STATE_IDLE) { player.prepare(); player.play(); }
            return;
        }
        loadedUrl = url;
        retried = false;
        paused = false;
        MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(url));
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains(".m3u8") || lower.contains("output=hls")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(item.build());
        player.prepare();
        player.play();
    }

    /** Switches to actually watching a channel (not just previewing it). */
    private void commit(List<Channel> list, int index) {
        if (list.isEmpty()) return;
        zapList = list;
        zapIndex = ((index % list.size()) + list.size()) % list.size();
        Channel next = zapList.get(zapIndex);
        if (committed != null && committed != next) previous = committed;
        committed = next;
        load(committed.url);
        String groupName = (currentGroup >= 0 && currentGroup < groups.size()) ? groups.get(currentGroup).name : null;
        prefs.edit().putString("lastUrl", committed.url).putString("lastGroup", groupName).apply();
        channelAdapter.notifyDataSetChanged();
        notifyState();
    }

    private void zap(int delta) {
        if (zapList.isEmpty()) return;
        commit(zapList, zapIndex + delta);
        showOsd();
    }

    // ---------------------------------------------------------------- Panel and lists

    private void setupLists() {
        groupAdapter = new GroupAdapter();
        channelAdapter = new ChannelAdapter();
        groupList.setAdapter(groupAdapter);
        channelList.setAdapter(channelAdapter);

        groupList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (groupList.hasFocus()) selectGroup(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        groupList.setOnItemClickListener((p, v, pos, id) -> {
            selectGroup(pos);
            if (!shownChannels.isEmpty()) channelList.requestFocus();
        });

        channelList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (channelList.hasFocus()) onChannelHighlighted(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        channelList.setOnFocusChangeListener((v, has) -> {
            if (has) onChannelHighlighted(channelList.getSelectedItemPosition());
        });
        // Mouse click (emulator); the remote is handled in dispatchKeyEvent
        channelList.setOnItemClickListener((p, v, pos, id) -> openChannel(pos));
    }

    private void selectGroup(int pos) {
        if (pos < 0 || pos >= groups.size()) return;
        currentGroup = pos;
        groupList.setItemChecked(pos, true);
        GroupItem g = groups.get(pos);
        List<Channel> list = new ArrayList<>();
        if (g.type == TYPE_FAV) {
            Set<String> seen = new HashSet<>();
            for (Channel c : allChannels) if (favs.contains(c.name) && seen.add(c.name)) list.add(c);
            channelsEmpty.setText(R.string.favorites_empty);
        } else if (g.type == TYPE_SEARCH) {
            list.addAll(ChannelSearch.find(allChannels, searchQuery));
            channelsEmpty.setText(getString(R.string.search_empty, searchQuery));
        } else if (g.type == TYPE_ALL) {
            list.addAll(allChannels);
        } else {
            for (Channel c : allChannels) if (c.group.equals(g.name)) list.add(c);
        }
        shownChannels = list;
        channelsTitle.setText(getString(R.string.channels_title, g.label(this), list.size()));
        channelAdapter.notifyDataSetChanged();
        int playing = indexOfUrl(list, committed != null ? committed.url : null);
        channelList.setSelection(Math.max(0, playing));
    }

    private void onChannelHighlighted(int pos) {
        if (pos < 0 || pos >= shownChannels.size()) return;
        Channel c = shownChannels.get(pos);
        showPreviewInfo(c);
        pendingPreview = c;
        ui.removeCallbacks(previewTask);
        ui.postDelayed(previewTask, PREVIEW_DELAY_MS);
    }

    private void openChannel(int pos) {
        if (pos < 0 || pos >= shownChannels.size()) return;
        commit(new ArrayList<>(shownChannels), pos);
        hidePanel();
        showOsd();
    }

    private void showPreviewInfo(Channel c) {
        if (c == null) {
            previewName.setText(R.string.preview_none_title);
            previewMeta.setText(R.string.preview_none_text);
            previewFav.setVisibility(View.GONE);
            return;
        }
        previewName.setText(c.name);
        previewMeta.setText(c.number + "   ·   " + groupLabel(this, c.group));
        previewFav.setVisibility(favs.contains(c.name) ? View.VISIBLE : View.GONE);
    }

    private void showPanel() {
        if (allChannels.isEmpty()) return;
        ui.removeCallbacks(hideOsd);
        osd.setVisibility(View.GONE);
        panel.setVisibility(View.VISIBLE);
        PlayerView.switchTargetView(player, playerFull, playerPreview);
        if (currentGroup < 0) selectGroup(defaultGroup());
        groupList.setSelection(currentGroup);
        groupList.setItemChecked(currentGroup, true);
        int playing = indexOfUrl(shownChannels, committed != null ? committed.url : null);
        if (playing >= 0) channelList.setSelection(playing);
        showPreviewInfo(committed);
        if (!shownChannels.isEmpty()) channelList.requestFocus();
        else groupList.requestFocus();
        notifyState();
    }

    /** Opens the list straight on Favorites (Guide button on the remote). */
    private void showFavorites() {
        if (allChannels.isEmpty()) return;
        if (!panelOpen()) showPanel();
        int fav = indexOfType(TYPE_FAV);
        selectGroup(fav);
        groupList.setSelection(fav);
        if (!shownChannels.isEmpty()) {
            channelList.requestFocus();
            onChannelHighlighted(channelList.getSelectedItemPosition());
        } else {
            groupList.requestFocus();
            toast(getString(R.string.toast_no_favorites));
        }
    }

    private void hidePanel() {
        if (committed == null) return;
        ui.removeCallbacks(previewTask);
        pendingPreview = null;
        panel.setVisibility(View.GONE);
        PlayerView.switchTargetView(player, playerPreview, playerFull);
        load(committed.url); // if another channel was being previewed, go back to the one being watched
        // A search from the phone is temporary: it goes away once you are watching something
        if (searchQuery != null) {
            searchQuery = null;
            rebuildGroups();
            if (currentGroup < 0) selectGroup(defaultGroup());
        }
        notifyState();
    }

    private boolean panelOpen() { return panel.getVisibility() == View.VISIBLE; }

    private boolean fullscreen() {
        return !panelOpen() && welcome.getVisibility() != View.VISIBLE && committed != null;
    }

    private int defaultGroup() {
        String last = prefs.getString("lastGroup", null);
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).name.equals(last)) return i;
        int fav = indexOfType(TYPE_FAV);
        return groups.get(fav).count > 0 ? fav : indexOfType(TYPE_ALL); // Favorites if any, otherwise All channels
    }

    private int indexOfType(int type) {
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).type == type) return i;
        return 0;
    }

    private void setChannels(List<Channel> channels) {
        allChannels = channels;
        searchQuery = null;
        channelsVersion++;
        rebuildGroups();
        currentGroup = -1;
        selectGroup(defaultGroup());

        String lastUrl = prefs.getString("lastUrl", null);
        int idx = indexOfUrl(shownChannels, lastUrl);
        updateWelcome();
        if (idx >= 0) {
            commit(new ArrayList<>(shownChannels), idx);
            panel.setVisibility(View.GONE);
            showOsd();
        } else {
            showPanel();
        }
        if (remote != null) remote.publishChannelsChanged(channelsVersion);
    }

    private void rebuildGroups() {
        // Keep the same category selected even if the positions move (search added or removed)
        GroupItem selected = (currentGroup >= 0 && currentGroup < groups.size()) ? groups.get(currentGroup) : null;
        Map<String, Integer> counts = new HashMap<>();
        Set<String> favNames = new HashSet<>();
        for (Channel c : allChannels) {
            Integer n = counts.get(c.group);
            counts.put(c.group, n == null ? 1 : n + 1);
            if (favs.contains(c.name)) favNames.add(c.name);
        }
        groups.clear();
        if (searchQuery != null) {
            groups.add(new GroupItem(SEARCH_GROUP, ChannelSearch.find(allChannels, searchQuery).size(), TYPE_SEARCH));
        }
        groups.add(new GroupItem(getString(R.string.group_favorites), favNames.size(), TYPE_FAV));
        groups.add(new GroupItem(getString(R.string.group_all), allChannels.size(), TYPE_ALL));
        for (String g : M3uParser.groups(allChannels)) groups.add(new GroupItem(g, counts.get(g), TYPE_GROUP));
        if (selected != null) {
            currentGroup = -1;
            for (int i = 0; i < groups.size(); i++) {
                GroupItem g = groups.get(i);
                if (g.type == selected.type && (g.type != TYPE_GROUP || g.name.equals(selected.name))) currentGroup = i;
            }
        }
        groupAdapter.notifyDataSetChanged();
    }

    private static int indexOfUrl(List<Channel> list, String url) {
        if (url == null) return -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).url.equals(url)) return i;
        return -1;
    }

    // ---------------------------------------------------------------- Favorites

    private void toggleFavorite(Channel c) {
        if (c == null) return;
        boolean added;
        if (favs.contains(c.name)) { favs.remove(c.name); added = false; }
        else { favs.add(c.name); added = true; }
        prefs.edit().putStringSet("favs", new HashSet<>(favs)).apply();
        toast(getString(added ? R.string.toast_fav_added : R.string.toast_fav_removed, c.name));

        int selected = channelList.getSelectedItemPosition();
        rebuildGroups();
        if (currentGroup >= 0) groupList.setItemChecked(currentGroup, true);
        if (currentGroup >= 0 && groups.get(currentGroup).type == TYPE_FAV) {
            selectGroup(currentGroup);
            if (!shownChannels.isEmpty()) {
                channelList.setSelection(Math.min(Math.max(0, selected), shownChannels.size() - 1));
            } else if (panelOpen()) {
                groupList.requestFocus();
            }
        } else {
            channelAdapter.notifyDataSetChanged();
        }
        if (panelOpen()) showPreviewInfo(c);
        if (osd.getVisibility() == View.VISIBLE) showOsd();
        if (remote != null) remote.publishFavorites(new ArrayList<>(favs));
        notifyState();
    }

    private Channel highlightedChannel() {
        int pos = channelList.getSelectedItemPosition();
        return (pos >= 0 && pos < shownChannels.size()) ? shownChannels.get(pos) : null;
    }

    // ---------------------------------------------------------------- Remote control

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = e.getAction() == KeyEvent.ACTION_UP;

        boolean backKey = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        boolean volumeKey = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE;

        // Pairing code on screen: Back cancels it, everything else waits
        if (pairingOverlay.getVisibility() == View.VISIBLE && !volumeKey) {
            if (backKey && up && !e.isCanceled()) {
                if (remote != null) remote.pairing().cancel();
                hidePairingOverlay();
            }
            return true;
        }

        if (backKey) {
            if (up && !e.isCanceled()) handleBack();
            return true;
        }

        // Number keys: type a channel number (it switches after a short pause)
        int digit = digitOf(code);
        if (digit >= 0 && !allChannels.isEmpty()) {
            if (down && e.getRepeatCount() == 0) typeDigit(digit);
            return true;
        }

        if (code == KeyEvent.KEYCODE_LAST_CHANNEL) {
            if (down && e.getRepeatCount() == 0) goToPrevious();
            return true;
        }

        boolean playKey = code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY
                || code == KeyEvent.KEYCODE_MEDIA_PAUSE;
        if (playKey && committed != null) {
            if (down && e.getRepeatCount() == 0) {
                setPaused(code == KeyEvent.KEYCODE_MEDIA_PAUSE || (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && !paused));
            }
            return true;
        }

        boolean center = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean onChannels = panelOpen() && channelList.hasFocus() && !shownChannels.isEmpty();

        // Short OK = watch / open list. Long-press OK = toggle favorite.
        if (center && (fullscreen() || onChannels)) {
            if (down) {
                if (e.getRepeatCount() == 0) {
                    longPressHandled = false;
                } else if (!longPressHandled) {
                    longPressHandled = true;
                    toggleFavorite(onChannels ? highlightedChannel() : committed);
                }
            } else if (up) {
                if (!longPressHandled) {
                    if (onChannels) openChannel(channelList.getSelectedItemPosition());
                    else showPanel();
                }
                longPressHandled = false;
            }
            return true;
        }

        boolean favKey = code == KeyEvent.KEYCODE_PROG_YELLOW || code == KeyEvent.KEYCODE_BOOKMARK
                || code == KeyEvent.KEYCODE_PROG_RED;
        if (favKey) {
            if (down && e.getRepeatCount() == 0) toggleFavorite(onChannels ? highlightedChannel() : committed);
            return true;
        }

        // Guide (or blue) button: jump straight to Favorites
        if (code == KeyEvent.KEYCODE_GUIDE || code == KeyEvent.KEYCODE_PROG_BLUE) {
            if (down && e.getRepeatCount() == 0) showFavorites();
            return true;
        }

        if (fullscreen() && down) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_CHANNEL_UP:
                    zap(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                    zap(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MENU:
                    showPanel();
                    return true;
                case KeyEvent.KEYCODE_INFO:
                    showOsd();
                    return true;
            }
        }

        if (panelOpen() && down && code == KeyEvent.KEYCODE_MENU && onChannels) {
            toggleFavorite(highlightedChannel());
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void handleBack() {
        if (panelOpen() && committed != null) {
            hidePanel();
            showOsd();
            return;
        }
        if (fullscreen()) {
            if (!allChannels.isEmpty()) showPanel();
            else stopSharedStream(); // a link sent from the phone before any playlist was set up
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackTime < EXIT_WINDOW_MS) {
            finish();
        } else {
            lastBackTime = now;
            toast(getString(R.string.toast_press_back_again));
        }
    }

    // ---------------------------------------------------------------- Channel banner and toasts

    private void showOsd() {
        if (committed == null) return;
        Channel c = committed;
        osdNumber.setText(c.number > 0 ? String.valueOf(c.number) : "");
        osdName.setText(c.name);
        osdGroup.setText(groupLabel(this, c.group));
        osdFav.setVisibility(favs.contains(c.name) ? View.VISIBLE : View.GONE);
        osdInitial.setText(initial(c.name));
        images.load(c.logo, osdLogo, osdInitial);
        osd.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideOsd);
        ui.postDelayed(hideOsd, OSD_MS);
    }

    private void toast(String text) {
        if (remote != null) remote.publishToast(text);
        toastText.setText(text);
        toastText.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideToast);
        ui.postDelayed(hideToast, 2500);
    }

    /** Group name as shown on screen (the parser's "no group" key is translated). */
    static String groupLabel(Activity a, String group) {
        return M3uParser.NO_GROUP.equals(group) ? a.getString(R.string.group_uncategorized) : group;
    }

    static String initial(String name) {
        if (name == null) return "";
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isLetterOrDigit(ch)) return String.valueOf(Character.toUpperCase(ch));
        }
        return "";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- Playlist download

    private void loadPlaylist(boolean allowCache) {
        final IptvConfig cfg = config;
        loading = true;
        setStatus(getString(R.string.status_loading));
        io.execute(() -> {
            List<Channel> channels = null;
            String error = null;
            try {
                byte[] data = download(cfg.playlistUrl());
                channels = M3uParser.parse(new StringReader(new String(data, StandardCharsets.UTF_8)));
                if (channels.isEmpty()) {
                    error = getString(R.string.error_empty_playlist);
                    channels = null;
                } else {
                    try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), CACHE_FILE))) {
                        out.write(data);
                    }
                }
            } catch (Exception e) {
                error = getString(R.string.error_download, e.getMessage());
            }
            if (channels == null && allowCache) {
                File cache = new File(getFilesDir(), CACHE_FILE);
                if (cache.exists()) {
                    try (InputStreamReader r = new InputStreamReader(new FileInputStream(cache), StandardCharsets.UTF_8)) {
                        channels = M3uParser.parse(r);
                        error = getString(R.string.error_using_cache, error);
                    } catch (IOException ignored) {}
                }
            }
            final List<Channel> result = channels;
            final String err = error;
            ui.post(() -> {
                loading = false;
                if (result != null) {
                    setStatus(err != null ? err : getString(R.string.status_channels, result.size()));
                    setChannels(result);
                } else {
                    setStatus(err);
                    updateWelcome();
                }
            });
        });
    }

    private static byte[] download(String address) throws IOException {
        String current = address;
        for (int redirects = 0; redirects < 6; redirects++) {
            HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", USER_AGENT);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("redirect without location");
                current = new URL(new URL(current), loc).toString();
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0) buf.write(b, 0, n);
                return buf.toByteArray();
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }

    // ---------------------------------------------------------------- Setup from a computer or phone

    private void startServer() {
        if (server != null) return;
        try {
            ConfigServer s = new ConfigServer(ConfigServer.DEFAULT_PORT, readAsset("config.html"), this);
            if (remote == null) {
                remote = new RemoteApi(new PhoneRemote(), new Pairing(new PrefsDeviceStore()),
                        new LogoCache(8 * 1024 * 1024, USER_AGENT));
            }
            s.setRemote(remote);
            s.start();
            server = s;
            // Let the phone app find the TV without typing its address
            if (discovery == null) discovery = new RemoteDiscovery(this);
            Map<String, String> txt = new HashMap<>();
            txt.put("id", tvId());
            txt.put("api", String.valueOf(RemoteApi.API_VERSION));
            txt.put("port", String.valueOf(s.getPort()));
            String ip = localIp();
            if (ip != null) txt.put("ip", ip);
            discovery.register(tvName(), s.getPort(), txt);
        } catch (IOException e) {
            server = null;
        }
    }

    private String readAsset(String name) throws IOException {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return buf.toString("UTF-8");
        }
    }

    @Override public IptvConfig currentConfig() { return config; }

    @Override public String status() { return status; }

    @Override public String missingFieldsMessage() { return getString(R.string.cfg_missing_fields); }

    @Override
    public String onConfigSaved(IptvConfig cfg) {
        config = cfg;
        prefs.edit()
                .putString("mode", cfg.mode).putString("m3uUrl", cfg.m3uUrl)
                .putString("server", cfg.server).putString("user", cfg.user).putString("pass", cfg.pass)
                .apply();
        ui.post(() -> loadPlaylist(false));
        return getString(R.string.cfg_saved);
    }

    @Override
    public String onReload() {
        if (!config.isComplete()) return getString(R.string.cfg_missing_first);
        ui.post(() -> loadPlaylist(true));
        return getString(R.string.cfg_reloading);
    }

    private IptvConfig loadConfig() {
        IptvConfig c = new IptvConfig();
        c.mode = prefs.getString("mode", IptvConfig.MODE_M3U);
        c.m3uUrl = prefs.getString("m3uUrl", "");
        c.server = prefs.getString("server", "");
        c.user = prefs.getString("user", "");
        c.pass = prefs.getString("pass", "");
        return c;
    }

    private void setStatus(String s) {
        status = s;
        refreshTexts();
        notifyState();
    }

    private String configUrl() {
        String ip = localIp();
        if (server == null || ip == null) return null;
        return "http://" + ip + ":" + server.getPort();
    }

    private void refreshTexts() {
        statusText.setText(status);
        String url = configUrl();
        footerUrl.setText(url != null ? getString(R.string.footer_setup, url) : "");
        updateWelcome();
    }

    private Bitmap qrBitmap;
    private String qrFor;

    private void updateWelcome() {
        boolean show = allChannels.isEmpty() && committed == null;
        welcome.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        String url = configUrl();
        welcomeProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            welcomeTitle.setText(R.string.welcome_loading_title);
            welcomeText.setText(R.string.welcome_loading_text);
            welcomeStatus.setText("");
        } else if (!config.isComplete()) {
            welcomeTitle.setText(R.string.welcome_title);
            welcomeText.setText(R.string.welcome_text);
            welcomeStatus.setText("");
        } else {
            welcomeTitle.setText(R.string.welcome_error_title);
            welcomeText.setText(R.string.welcome_error_text);
            welcomeStatus.setText(status);
        }
        if (url == null) {
            welcomeUrl.setText(server == null ? R.string.welcome_port_busy : R.string.welcome_no_network);
            welcomeQr.setVisibility(View.GONE);
        } else {
            welcomeUrl.setText(url);
            if (!url.equals(qrFor)) {
                qrBitmap = QrCode.render(url, dp(180), 0xFF0A0E15, 0xFFFFFFFF);
                qrFor = url;
            }
            welcomeQr.setImageBitmap(qrBitmap);
            welcomeQr.setVisibility(qrBitmap != null ? View.VISIBLE : View.GONE);
        }
    }

    private static String localIp() {
        String fallback = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        if (a.isSiteLocalAddress()) return a.getHostAddress();
                        if (fallback == null) fallback = a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    // ---------------------------------------------------------------- Lifecycle

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        startServer();
        if (loadedUrl != null) { paused = false; player.prepare(); player.play(); }
        refreshTexts();
        notifyState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        if (player != null) player.stop();
        notifyState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discovery != null) discovery.unregister();
        if (remote != null) remote.close();
        if (server != null) server.stop();
        if (player != null) player.release();
        images.shutdown();
        io.shutdownNow();
        ui.removeCallbacksAndMessages(null);
    }

    // ---------------------------------------------------------------- Extra controls (remote and phone)

    private static int digitOf(int code) {
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) return code - KeyEvent.KEYCODE_0;
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) return code - KeyEvent.KEYCODE_NUMPAD_0;
        return -1;
    }

    /** Builds a channel number digit by digit, like on a regular TV. */
    private void typeDigit(int digit) {
        if (typedNumber.length() == 0 && digit == 0) return;
        if (typedNumber.length() >= 5) return;
        typedNumber.append(digit);
        numberEntry.setText(typedNumber);
        numberEntry.setVisibility(View.VISIBLE);
        ui.removeCallbacks(goToTypedNumber);
        // Switch at once when no longer number is possible, otherwise wait for more digits
        int maxDigits = String.valueOf(allChannels.size()).length();
        if (typedNumber.length() >= maxDigits) goToTypedNumber();
        else ui.postDelayed(goToTypedNumber, NUMBER_ENTRY_MS);
    }

    private void goToTypedNumber() {
        ui.removeCallbacks(goToTypedNumber);
        numberEntry.setVisibility(View.GONE);
        if (typedNumber.length() == 0) return;
        int n = Integer.parseInt(typedNumber.toString());
        typedNumber.setLength(0);
        Channel c = channelByNumber(n);
        if (c == null) toast(getString(R.string.toast_no_channel_number, n));
        else playChannel(c);
    }

    private Channel channelByNumber(int n) {
        List<Channel> list = allChannels;
        if (n >= 1 && n <= list.size() && list.get(n - 1).number == n) return list.get(n - 1);
        for (Channel c : list) if (c.number == n) return c;
        return null;
    }

    /** Watches a channel, zapping within the current list if it is there, otherwise within All channels. */
    private void playChannel(Channel c) {
        int idx = zapList.indexOf(c);
        if (idx >= 0) {
            commit(zapList, idx);
        } else {
            selectGroup(indexOfType(TYPE_ALL));
            commit(allChannels, allChannels.indexOf(c));
        }
        if (panelOpen()) hidePanel();
        showOsd();
    }

    private void goToPrevious() {
        Channel p = previous;
        if (p == null) {
            toast(getString(R.string.toast_no_previous));
            return;
        }
        if (p.number > 0 && allChannels.contains(p)) playChannel(p);
        else if (p.number == 0) playShared(p.url, p.name);
    }

    /**
     * Pauses a live channel. Resuming goes back to the live picture, which is what people
     * expect from TV (and the stream may not keep a buffer anyway).
     */
    private void setPaused(boolean pause) {
        if (committed == null || pause == paused) return;
        paused = pause;
        if (pause) {
            player.pause();
            toast(getString(R.string.toast_paused));
        } else {
            player.stop();
            player.prepare();
            player.play();
            showOsd();
        }
        notifyState();
    }

    private void adjustVolume(int direction) {
        if (audio != null) audio.adjustSuggestedStreamVolume(direction, AudioManager.STREAM_MUSIC, AudioManager.FLAG_SHOW_UI);
    }

    /** Plays a link sent from the phone without touching the playlist. */
    private void playShared(String url, String title) {
        String name = title != null && !title.trim().isEmpty() ? title.trim() : Uri.parse(url).getHost();
        if (name == null || name.isEmpty()) name = getString(R.string.shared_default_name);
        Channel c = new Channel(name, getString(R.string.group_shared), null, url, 0);
        if (committed != null && committed != c) previous = committed;
        committed = c;
        load(url);
        if (panelOpen()) hidePanel();
        updateWelcome();
        channelAdapter.notifyDataSetChanged();
        showOsd();
        notifyState();
    }

    private void stopSharedStream() {
        committed = null;
        loadedUrl = null;
        player.stop();
        player.clearMediaItems();
        osd.setVisibility(View.GONE);
        updateWelcome();
        notifyState();
    }

    /** Shows the channels matching what is typed on the phone, as a category at the top of the list. */
    private void applySearch(String q) {
        String query = q == null ? "" : q.trim();
        if (allChannels.isEmpty()) return;
        if (query.isEmpty()) {
            if (searchQuery == null) return;
            searchQuery = null;
            rebuildGroups();
            if (currentGroup < 0) selectGroup(defaultGroup());
            if (panelOpen()) {
                groupList.setSelection(currentGroup);
                groupList.setItemChecked(currentGroup, true);
            }
            notifyState();
            return;
        }
        searchQuery = query;
        rebuildGroups();
        if (!panelOpen()) showPanel();
        int idx = indexOfType(TYPE_SEARCH);
        selectGroup(idx);
        groupList.setSelection(idx);
        if (!shownChannels.isEmpty()) {
            channelList.setSelection(0);
            channelList.requestFocus();
            onChannelHighlighted(0);
        } else {
            groupList.requestFocus();
        }
        notifyState();
    }

    // ---------------------------------------------------------------- Pairing a phone

    private void showPairingOverlay(String pin, String device) {
        StringBuilder spaced = new StringBuilder();
        for (int i = 0; i < pin.length(); i++) {
            if (i > 0) spaced.append(' ');
            spaced.append(pin.charAt(i));
        }
        pairingCode.setText(spaced);
        pairingText.setText(getString(R.string.pair_text, device));
        pairingOverlay.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hidePairing);
        ui.postDelayed(hidePairing, Pairing.PIN_VALID_MS);
    }

    private void hidePairingOverlay() {
        ui.removeCallbacks(hidePairing);
        pairingOverlay.setVisibility(View.GONE);
    }

    /** Remembers paired phones in the app preferences ("token TAB name" per line, oldest first). */
    private final class PrefsDeviceStore implements Pairing.Store {
        @Override
        public Map<String, String> load() {
            Map<String, String> m = new LinkedHashMap<>();
            String raw = prefs.getString("remoteDevices", "");
            for (String line : raw.split("\n")) {
                int tab = line.indexOf('\t');
                if (tab > 0) m.put(line.substring(0, tab), line.substring(tab + 1));
            }
            return m;
        }

        @Override
        public void save(Map<String, String> devices) {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, String> e : devices.entrySet()) {
                if (b.length() > 0) b.append('\n');
                b.append(e.getKey()).append('\t').append(e.getValue());
            }
            prefs.edit().putString("remoteDevices", b.toString()).apply();
        }
    }

    private String tvId() {
        String id = prefs.getString("tvId", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString("tvId", id).apply();
        }
        return id;
    }

    private String tvName() {
        String name = null;
        if (Build.VERSION.SDK_INT >= 25) name = Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
        if (name == null || name.trim().isEmpty()) name = Build.MODEL;
        return name == null || name.trim().isEmpty() ? "Android TV" : name.trim();
    }

    // ---------------------------------------------------------------- What the phone sees

    /** Tells connected phones what is on screen. Call on the UI thread after anything visible changes. */
    private void notifyState() {
        if (remote != null && remote.connectedPhones() > 0) remote.publishState(stateJson());
    }

    private String stateJson() {
        String screen = !started ? "background"
                : welcome.getVisibility() == View.VISIBLE ? "welcome"
                : panelOpen() ? "list" : "full";
        String url = configUrl();
        return "{\"screen\":" + ConfigServer.json(screen)
                + ",\"channel\":" + (committed != null ? channelJson(committed) : "null")
                + ",\"paused\":" + paused
                + ",\"loading\":" + loading
                + ",\"configured\":" + config.isComplete()
                + ",\"status\":" + ConfigServer.json(status)
                + ",\"channelCount\":" + allChannels.size()
                + ",\"version\":" + channelsVersion
                + ",\"search\":" + (searchQuery != null ? ConfigServer.json(searchQuery) : "null")
                + ",\"setupUrl\":" + (url != null ? ConfigServer.json(url) : "null") + "}";
    }

    private String channelJson(Channel c) {
        return "{\"n\":" + c.number
                + ",\"name\":" + ConfigServer.json(c.name)
                + ",\"group\":" + ConfigServer.json(groupLabel(this, c.group))
                + ",\"logo\":" + (c.logo != null && !c.logo.isEmpty() ? ConfigServer.json(c.logo) : "null")
                + ",\"fav\":" + favs.contains(c.name) + "}";
    }

    /** Runs on the UI thread and waits for the result (used by the server threads). */
    private <T> T onUi(Callable<T> task, T fallback) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try { return task.call(); } catch (Exception e) { return fallback; }
        }
        FutureTask<T> f = new FutureTask<>(task);
        ui.post(f);
        try {
            return f.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Bridge between the phone API (server threads) and the TV screen (UI thread). */
    private final class PhoneRemote implements RemoteApi.Tv {

        @Override public String tvId() { return MainActivity.this.tvId(); }
        @Override public String tvName() { return MainActivity.this.tvName(); }
        @Override public String appVersion() { return APP_VERSION; }

        @Override
        public void showPairingCode(String pin, String deviceName) {
            ui.post(() -> showPairingOverlay(pin, deviceName));
        }

        @Override
        public void hidePairingCode() {
            ui.post(MainActivity.this::hidePairingOverlay);
        }

        @Override
        public void onDevicePaired(String deviceName) {
            ui.post(() -> toast(getString(R.string.toast_phone_paired, deviceName)));
        }

        @Override
        public String key(String key) {
            return onUi(() -> {
                switch (key) {
                    case "volup": adjustVolume(AudioManager.ADJUST_RAISE); return null;
                    case "voldown": adjustVolume(AudioManager.ADJUST_LOWER); return null;
                    case "mute":
                        if (Build.VERSION.SDK_INT >= 23) adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE);
                        return null;
                    default:
                        break;
                }
                if (!started) return getString(R.string.remote_open_app);
                int code = keyCode(key);
                if (code == KeyEvent.KEYCODE_UNKNOWN) return null;
                long t = SystemClock.uptimeMillis();
                dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
                dispatchKeyEvent(new KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0));
                return null;
            }, getString(R.string.remote_busy));
        }

        @Override
        public String play(int number) {
            return onUi(() -> {
                if (!started) return getString(R.string.remote_open_app);
                Channel c = channelByNumber(number);
                if (c == null) return getString(R.string.toast_no_channel_number, number);
                playChannel(c);
                return null;
            }, getString(R.string.remote_busy));
        }

        @Override
        public Boolean favorite(int number, Boolean on) {
            return onUi(() -> {
                Channel c = channelByNumber(number);
                if (c == null) return null;
                boolean is = favs.contains(c.name);
                if (on == null || on != is) toggleFavorite(c);
                return favs.contains(c.name);
            }, null);
        }

        @Override
        public void search(String query) {
            ui.post(() -> { if (started) applySearch(query); });
        }

        @Override
        public String share(String url, String title, boolean asPlaylist) {
            return onUi(() -> {
                if (asPlaylist) {
                    IptvConfig cfg = new IptvConfig();
                    cfg.mode = IptvConfig.MODE_M3U;
                    cfg.m3uUrl = url;
                    return onConfigSaved(cfg);
                }
                if (!started) return getString(R.string.remote_open_app);
                playShared(url, title);
                return getString(R.string.remote_shared_playing);
            }, getString(R.string.remote_busy));
        }

        @Override
        public String reload() {
            return onReload();
        }

        @Override
        public String stateJson() {
            return onUi(MainActivity.this::stateJson, "{}");
        }

        @Override
        public String channelsJson() {
            // Take a consistent snapshot on the UI thread, build the (possibly large) JSON here
            Object[] snap = onUi(() -> new Object[]{allChannels, new HashSet<>(favs), channelsVersion}, null);
            if (snap == null) return "{\"version\":0,\"groups\":[],\"channels\":[],\"favorites\":[]}";
            @SuppressWarnings("unchecked") List<Channel> list = (List<Channel>) snap[0];
            @SuppressWarnings("unchecked") Set<String> favNames = (Set<String>) snap[1];
            int version = (Integer) snap[2];

            List<String> groupNames = M3uParser.groups(list);
            Map<String, Integer> groupIndex = new HashMap<>();
            List<String> labels = new ArrayList<>();
            for (String g : groupNames) {
                groupIndex.put(g, labels.size());
                labels.add(groupLabel(MainActivity.this, g));
            }
            StringBuilder b = new StringBuilder(list.size() * 96 + 256);
            b.append("{\"version\":").append(version);
            b.append(",\"groups\":").append(RemoteApi.jsonArray(labels));
            b.append(",\"channels\":[");
            for (int i = 0; i < list.size(); i++) {
                Channel c = list.get(i);
                if (i > 0) b.append(',');
                b.append("{\"n\":").append(c.number)
                        .append(",\"name\":").append(ConfigServer.json(c.name))
                        .append(",\"g\":").append(groupIndex.get(c.group));
                if (c.logo != null && !c.logo.isEmpty()) b.append(",\"logo\":").append(ConfigServer.json(c.logo));
                b.append('}');
            }
            b.append("],\"favorites\":").append(RemoteApi.jsonArray(new ArrayList<>(favNames))).append('}');
            return b.toString();
        }

        @Override
        public String logoUrl(int number) {
            Channel c = channelByNumber(number);
            return c != null && c.logo != null && !c.logo.isEmpty() ? c.logo : null;
        }
    }

    private static int keyCode(String key) {
        switch (key) {
            case "up": return KeyEvent.KEYCODE_DPAD_UP;
            case "down": return KeyEvent.KEYCODE_DPAD_DOWN;
            case "left": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "right": return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "ok": return KeyEvent.KEYCODE_DPAD_CENTER;
            case "back": return KeyEvent.KEYCODE_BACK;
            case "menu": return KeyEvent.KEYCODE_MENU;
            case "info": return KeyEvent.KEYCODE_INFO;
            case "guide": return KeyEvent.KEYCODE_GUIDE;
            case "fav": return KeyEvent.KEYCODE_PROG_YELLOW;
            case "chup": return KeyEvent.KEYCODE_CHANNEL_UP;
            case "chdown": return KeyEvent.KEYCODE_CHANNEL_DOWN;
            case "last": return KeyEvent.KEYCODE_LAST_CHANNEL;
            case "playpause": return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            default:
                if (key.length() == 1 && Character.isDigit(key.charAt(0))) return KeyEvent.KEYCODE_0 + (key.charAt(0) - '0');
                return KeyEvent.KEYCODE_UNKNOWN;
        }
    }

    // ---------------------------------------------------------------- Adapters

    private static final class GroupItem {
        final String name;
        final int count;
        final int type;

        GroupItem(String name, Integer count, int type) {
            this.name = name;
            this.count = count == null ? 0 : count;
            this.type = type;
        }

        String label(MainActivity a) {
            if (type == TYPE_FAV) return "★  " + name;
            if (type == TYPE_SEARCH) return a.getString(R.string.group_search, a.searchQuery);
            return groupLabel(a, name);
        }
    }

    private final class GroupAdapter extends BaseAdapter {
        @Override public int getCount() { return groups.size(); }
        @Override public Object getItem(int p) { return groups.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            View v = convert != null ? convert
                    : LayoutInflater.from(MainActivity.this).inflate(R.layout.item_group, parent, false);
            GroupItem g = groups.get(pos);
            ((TextView) v.findViewById(R.id.groupName)).setText(g.label(MainActivity.this));
            ((TextView) v.findViewById(R.id.groupCount)).setText(String.valueOf(g.count));
            return v;
        }
    }

    private final class ChannelAdapter extends BaseAdapter {
        @Override public int getCount() { return shownChannels.size(); }
        @Override public Object getItem(int p) { return shownChannels.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            View v = convert;
            Holder h;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_channel, parent, false);
                h = new Holder();
                h.number = v.findViewById(R.id.channelNumber);
                h.name = v.findViewById(R.id.channelName);
                h.initial = v.findViewById(R.id.channelInitial);
                h.logo = v.findViewById(R.id.channelLogo);
                h.star = v.findViewById(R.id.channelStar);
                h.live = v.findViewById(R.id.channelLive);
                v.setTag(h);
            } else {
                h = (Holder) v.getTag();
            }
            Channel c = shownChannels.get(pos);
            h.number.setText(String.valueOf(c.number));
            h.name.setText(c.name);
            h.initial.setText(initial(c.name));
            h.star.setVisibility(favs.contains(c.name) ? View.VISIBLE : View.GONE);
            h.live.setVisibility(committed != null && committed.url.equals(c.url) ? View.VISIBLE : View.GONE);
            images.load(c.logo, h.logo, h.initial);
            return v;
        }
    }

    private static final class Holder {
        TextView number, name, initial, star;
        ImageView logo;
        View live;
    }
}
