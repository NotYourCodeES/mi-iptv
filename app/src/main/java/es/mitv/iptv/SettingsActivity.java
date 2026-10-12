package es.mitv.iptv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import es.mitv.iptv.core.ConfigServer;
import es.mitv.iptv.core.IptvConfig;
import es.mitv.iptv.core.ParentalLock;
import es.mitv.iptv.core.Playlists;
import es.mitv.iptv.core.TvSettings;

/**
 * Settings on the TV itself, for when there's no phone or computer at hand: playlists, language,
 * hidden categories, parental control, the daily reload and some information.
 * The main screen picks the changes up when it comes back.
 */
public class SettingsActivity extends Activity {

    private static final int LISTS = 0, LANGUAGE = 1, CATEGORIES = 2, PARENTAL = 3, UPDATES = 4, ABOUT = 5;
    private static final int[] TITLES = {
            R.string.settings_section_lists, R.string.settings_section_language, R.string.settings_section_categories,
            R.string.settings_section_parental, R.string.settings_section_updates, R.string.settings_section_about};
    private static final String STATE_SECTION = "section";

    private LinearLayout content;
    private ScrollView scroll;
    private final List<TextView> sectionRows = new ArrayList<>();
    private int section = -1;
    /** The PIN was typed on this screen, so the parental settings can be changed. */
    private boolean parentalOpen;
    /** While a section is rebuilt, focus jumps around on its own: don't switch sections because of it. */
    private boolean rendering;

    private Playlists playlists;
    private TvSettings settings;
    private ParentalLock lock;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        AndroidStore store = new AndroidStore(this);
        playlists = Session.playlists(this);
        settings = new TvSettings(store);
        lock = new ParentalLock(store);
        content = findViewById(R.id.content);
        scroll = findViewById(R.id.contentScroll);

        LinearLayout sections = findViewById(R.id.sections);
        for (int i = 0; i < TITLES.length; i++) {
            final int index = i;
            TextView row = new TextView(this);
            row.setId(View.generateViewId());
            row.setText(TITLES[i]);
            row.setTextSize(17);
            row.setTextColor(getColor(R.color.text));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), 0, dp(16), 0);
            row.setBackgroundResource(R.drawable.bg_setting_row);
            row.setFocusable(true);
            row.setOnFocusChangeListener((v, has) -> { if (has && !rendering) select(index); });
            row.setOnClickListener(v -> focusContent());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            lp.bottomMargin = dp(4);
            sections.addView(row, lp);
            sectionRows.add(row);
        }
        int start = savedInstanceState != null ? savedInstanceState.getInt(STATE_SECTION, LISTS) : LISTS;
        select(start);
        sectionRows.get(start).requestFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_SECTION, section);
    }

    /** Back from the content goes to the sections first, then closes Settings. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        boolean back = e.getKeyCode() == KeyEvent.KEYCODE_BACK || e.getKeyCode() == KeyEvent.KEYCODE_ESCAPE;
        View focused = getCurrentFocus();
        if (back && focused != null && isInside(focused, content)) {
            if (e.getAction() == KeyEvent.ACTION_UP) sectionRows.get(section).requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private static boolean isInside(View v, ViewGroup parent) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == parent) return true;
        }
        return false;
    }

    private void select(int index) {
        if (index == section) return;
        section = index;
        for (int i = 0; i < sectionRows.size(); i++) sectionRows.get(i).setActivated(i == index);
        render();
    }

    private void focusContent() {
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (child.isFocusable()) {
                child.requestFocus();
                return;
            }
        }
    }

    /** Rebuilds the current section, keeping the focus on the same row when possible. */
    private void render() {
        View focused = getCurrentFocus();
        int focusedIndex = focused != null && focused.getParent() == content ? content.indexOfChild(focused) : -1;
        rendering = true;
        content.removeAllViews();
        switch (section) {
            case LISTS: renderLists(); break;
            case LANGUAGE: renderLanguage(); break;
            case CATEGORIES: renderCategories(); break;
            case PARENTAL: renderParental(); break;
            case UPDATES: renderUpdates(); break;
            default: renderAbout(); break;
        }
        int left = sectionRows.get(section).getId();
        View firstRow = null;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            child.setNextFocusLeftId(left);
            if (firstRow == null && child.isFocusable()) firstRow = child;
        }
        sectionRows.get(section).setNextFocusRightId(firstRow != null ? firstRow.getId() : View.NO_ID);
        if (focusedIndex >= 0) {
            View again = content.getChildAt(Math.min(focusedIndex, content.getChildCount() - 1));
            if (again != null && again.isFocusable()) again.requestFocus();
            else if (firstRow != null) firstRow.requestFocus();
            else sectionRows.get(section).requestFocus();
        } else {
            scroll.scrollTo(0, 0);
        }
        rendering = false;
    }

    // ---------------------------------------------------------------- Sections

    private void renderLists() {
        intro(getString(R.string.lists_intro));
        Playlists.Entry active = playlists.active();
        for (Playlists.Entry e : playlists.all()) {
            boolean inUse = active != null && active.id.equals(e.id);
            row(e.name, describe(e.config), inUse ? "✓  " + getString(R.string.lists_in_use) : null,
                    R.color.accent, v -> listActions(e));
        }
        row(getString(R.string.lists_add), getString(R.string.lists_add_desc), "+", R.color.accent, v -> editList(null));
    }

    private void renderLanguage() {
        String chosen = settings.language();
        String[][] options = {
                {TvSettings.LANGUAGE_AUTO, getString(R.string.language_auto)},
                {"es", "Español"},
                {"en", "English"}};
        for (String[] option : options) {
            boolean on = option[0].equals(chosen);
            row(option[1], null, on ? "✓" : null, R.color.accent, v -> {
                if (option[0].equals(settings.language())) return;
                settings.setLanguage(option[0]);
                recreate(); // the main screen follows when it comes back
            });
        }
        intro(getString(R.string.language_note));
    }

    private void renderCategories() {
        intro(getString(R.string.categories_intro));
        Playlists.Entry list = playlists.active();
        if (list == null || Session.groups.isEmpty()) {
            intro(getString(R.string.categories_none));
            return;
        }
        Set<String> hidden = settings.hiddenGroups(list.id);
        for (String g : Session.groups) {
            boolean isHidden = hidden.contains(g);
            row(MainActivity.groupLabel(this, g), null,
                    getString(isHidden ? R.string.categories_hidden : R.string.categories_shown),
                    isHidden ? R.color.muted : R.color.accent, v -> {
                        Set<String> now = settings.hiddenGroups(list.id);
                        if (!now.remove(g)) now.add(g);
                        settings.setHiddenGroups(list.id, now);
                        render();
                    });
        }
    }

    private void renderParental() {
        if (!lock.hasPin()) {
            intro(getString(R.string.parental_intro_off));
            row(getString(R.string.parental_turn_on), null, null, R.color.accent,
                    v -> PinDialog.choose(this, lock, () -> {
                        parentalOpen = true;
                        render();
                    }));
            return;
        }
        intro(getString(R.string.parental_intro_on));
        if (!parentalOpen) {
            row(getString(R.string.parental_unlock_settings), null, "🔒", R.color.muted,
                    v -> PinDialog.ask(this, lock, () -> {
                        parentalOpen = true;
                        render();
                    }));
            return;
        }
        row(getString(R.string.parental_change_pin), null, null, R.color.accent,
                v -> PinDialog.choose(this, lock, () -> toast(getString(R.string.done))));
        row(getString(R.string.parental_turn_off), null, null, R.color.live, v -> {
            lock.clearPin();
            for (Playlists.Entry e : playlists.all()) settings.setLockedGroups(e.id, Collections.<String>emptySet());
            parentalOpen = false;
            render();
        });
        Playlists.Entry list = playlists.active();
        if (list == null || Session.groups.isEmpty()) {
            intro(getString(R.string.categories_none));
            return;
        }
        Set<String> locked = settings.lockedGroups(list.id);
        for (String g : Session.groups) {
            boolean isLocked = locked.contains(g);
            row(MainActivity.groupLabel(this, g), null,
                    isLocked ? "🔒  " + getString(R.string.parental_locked) : getString(R.string.parental_open),
                    isLocked ? R.color.gold : R.color.muted, v -> {
                        Set<String> now = settings.lockedGroups(list.id);
                        if (!now.remove(g)) now.add(g);
                        settings.setLockedGroups(list.id, now);
                        render();
                    });
        }
    }

    private void renderUpdates() {
        boolean on = settings.autoRefresh();
        row(getString(R.string.updates_auto), getString(R.string.updates_auto_desc),
                getString(on ? R.string.on : R.string.off), on ? R.color.accent : R.color.muted, v -> {
                    settings.setAutoRefresh(!settings.autoRefresh());
                    render();
                });
        long last = settings.lastLoaded();
        String when = last > 0
                ? getString(R.string.updates_last, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(last)))
                : getString(R.string.updates_never);
        row(getString(R.string.updates_now), when, null, R.color.accent, v -> {
            Session.reloadRequested = true;
            finish();
        });
    }

    private void renderAbout() {
        row(getString(R.string.about_version), null, MainActivity.APP_VERSION, R.color.text2, null);
        row(getString(R.string.about_setup), null,
                Session.setupUrl != null ? Session.setupUrl : getString(R.string.about_no_network), R.color.text2, null);
        List<String> phones = Session.pairing != null ? Session.pairing.deviceNames() : Collections.<String>emptyList();
        row(getString(R.string.about_phones), phones.isEmpty() ? null : TextUtils.join(", ", phones),
                phones.isEmpty() ? getString(R.string.about_phones_none) : String.valueOf(phones.size()), R.color.text2, null);
        if (!phones.isEmpty()) {
            row(getString(R.string.about_forget_phones), null, null, R.color.live,
                    v -> confirm(getString(R.string.about_forget_phones_confirm), () -> {
                        if (Session.pairing != null) Session.pairing.forgetAll();
                        render();
                    }));
        }
        AndroidStore store = new AndroidStore(this);
        int favs = store.getStringSet("favs").size();
        if (favs > 0) {
            row(getString(R.string.about_clear_favs), null, String.valueOf(favs), R.color.live,
                    v -> confirm(getString(R.string.about_clear_favs_confirm, favs), () -> {
                        store.putStringSet("favs", Collections.<String>emptySet());
                        render();
                    }));
        }
    }

    // ---------------------------------------------------------------- Playlists

    private String describe(IptvConfig c) {
        boolean xtream = IptvConfig.MODE_XTREAM.equals(c.mode);
        String type = getString(xtream ? R.string.lists_type_xtream : R.string.lists_type_m3u);
        String where = xtream ? c.user + " @ " + Playlists.defaultName(c) : Playlists.defaultName(c);
        return type + "  ·  " + where;
    }

    private void listActions(Playlists.Entry e) {
        boolean inUse = playlists.active() != null && playlists.active().id.equals(e.id);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (!inUse) {
            labels.add(getString(R.string.lists_use));
            actions.add(() -> {
                playlists.setActive(e.id);
                render();
            });
        }
        labels.add(getString(R.string.lists_edit));
        actions.add(() -> editList(e));
        labels.add(getString(R.string.lists_delete));
        actions.add(() -> confirm(getString(R.string.lists_delete_confirm, e.name), () -> {
            playlists.remove(e.id);
            settings.forgetList(e.id);
            MainActivity.cacheFile(this, e.id).delete();
            render();
        }));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(e.name)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    /** New list (e == null) or changes to an existing one. */
    private void editList(Playlists.Entry e) {
        IptvConfig start = e != null ? e.config : new IptvConfig();
        final String[] mode = {start.mode};

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);

        EditText name = field(form, R.string.list_form_name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (e != null) name.setText(e.name);

        LinearLayout types = new LinearLayout(this);
        types.setOrientation(LinearLayout.HORIZONTAL);
        TextView m3uTab = tab(types, R.string.lists_type_m3u);
        TextView xtreamTab = tab(types, R.string.lists_type_xtream);
        form.addView(types);

        int uri = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
        EditText m3u = field(form, R.string.list_form_m3u, uri);
        EditText server = field(form, R.string.list_form_server, uri);
        EditText user = field(form, R.string.list_form_user, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText pass = field(form, e != null && !start.pass.isEmpty() ? R.string.list_form_pass_keep : R.string.list_form_pass,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        m3u.setText(start.m3uUrl);
        server.setText(start.server);
        user.setText(start.user);

        TextView error = new TextView(this);
        error.setTextColor(getColor(R.color.live));
        error.setVisibility(View.GONE);
        form.addView(error);

        Runnable showMode = () -> {
            boolean xtream = IptvConfig.MODE_XTREAM.equals(mode[0]);
            m3uTab.setActivated(!xtream);
            xtreamTab.setActivated(xtream);
            m3u.setVisibility(xtream ? View.GONE : View.VISIBLE);
            server.setVisibility(xtream ? View.VISIBLE : View.GONE);
            user.setVisibility(xtream ? View.VISIBLE : View.GONE);
            pass.setVisibility(xtream ? View.VISIBLE : View.GONE);
        };
        m3uTab.setOnClickListener(v -> { mode[0] = IptvConfig.MODE_M3U; showMode.run(); });
        xtreamTab.setOnClickListener(v -> { mode[0] = IptvConfig.MODE_XTREAM; showMode.run(); });
        showMode.run();

        ScrollView wrapper = new ScrollView(this);
        wrapper.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(e == null ? R.string.list_form_new : R.string.list_form_edit)
                .setView(wrapper)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            IptvConfig c = new IptvConfig();
            c.mode = mode[0];
            if (IptvConfig.MODE_XTREAM.equals(c.mode)) {
                c.server = server.getText().toString().trim();
                c.user = user.getText().toString().trim();
                c.pass = pass.getText().toString().trim();
                // Same rule as the setup page: a blank password keeps the saved one only for the same account
                if (c.pass.isEmpty() && e != null && ConfigServer.sameAccount(e.config, c)) c.pass = e.config.pass;
            } else {
                c.m3uUrl = m3u.getText().toString().trim();
            }
            if (!c.isComplete()) {
                error.setText(R.string.list_form_incomplete);
                error.setVisibility(View.VISIBLE);
                return;
            }
            String label = name.getText().toString();
            if (e == null) playlists.add(label, c);
            else playlists.update(e.id, label, c);
            dialog.dismiss();
            render();
        }));
        dialog.show();
    }

    private EditText field(LinearLayout form, int hint, int inputType) {
        EditText f = new EditText(this);
        f.setHint(hint);
        f.setInputType(inputType);
        f.setSingleLine(true);
        form.addView(f);
        return f;
    }

    private TextView tab(LinearLayout parent, int text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTextColor(getColor(R.color.text));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        t.setBackgroundResource(R.drawable.bg_setting_row);
        t.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(0, dp(8), dp(8), dp(8));
        parent.addView(t, lp);
        return t;
    }

    // ---------------------------------------------------------------- Rows

    /** A focusable setting: title and optional subtitle on the left, a value on the right. */
    private void row(String title, String subtitle, String value, int valueColor, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setId(View.generateViewId());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(60));
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.setBackgroundResource(R.drawable.bg_setting_row);
        row.setFocusable(true); // also rows without an action, so the list can be scrolled with the remote
        if (click != null) row.setOnClickListener(click);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(17);
        t.setTextColor(getColor(R.color.text));
        texts.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(subtitle);
            s.setTextSize(13);
            s.setTextColor(getColor(R.color.muted));
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(s);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (value != null) {
            TextView v = new TextView(this);
            v.setText(value);
            v.setTextSize(15);
            v.setTextColor(getColor(valueColor));
            v.setPadding(dp(16), 0, 0, 0);
            row.addView(v);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        content.addView(row, lp);
    }

    private void intro(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(getColor(R.color.muted));
        t.setPadding(dp(16), dp(4), dp(16), dp(14));
        content.addView(t);
    }

    private void confirm(String message, Runnable yes) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (d, w) -> yes.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
