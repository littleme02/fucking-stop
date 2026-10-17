package dev.natmon.fuckingstop;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LogActivity extends Activity {
    private static class Event {
        long time;
        String pkg, kind, component;
        int count;
    }

    private final List<Event> events = new ArrayList<>();
    private final Map<String, Drawable> icons = new LinkedHashMap<>();
    private final Map<String, CharSequence> labels = new LinkedHashMap<>();
    private TextView summary;
    private View empty;
    private Adapter adapter;

    private final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            load();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_log);
        summary = findViewById(R.id.summary);
        empty = findViewById(R.id.empty);
        ListView list = findViewById(R.id.list);
        adapter = new Adapter();
        list.setAdapter(adapter);
        findViewById(R.id.clear).setOnClickListener(v ->
                getContentResolver().delete(LogProvider.URI, null, null));
    }

    @Override
    protected void onStart() {
        super.onStart();
        getContentResolver().registerContentObserver(LogProvider.URI, false, observer);
        load();
    }

    @Override
    protected void onStop() {
        getContentResolver().unregisterContentObserver(observer);
        super.onStop();
    }

    private void load() {
        events.clear();
        Map<String, Integer> blocked = new LinkedHashMap<>();
        try (Cursor c = getContentResolver().query(LogProvider.URI, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                Event e = new Event();
                e.time = c.getLong(c.getColumnIndexOrThrow("time"));
                e.pkg = c.getString(c.getColumnIndexOrThrow("pkg"));
                e.kind = c.getString(c.getColumnIndexOrThrow("kind"));
                e.component = c.getString(c.getColumnIndexOrThrow("component"));
                e.count = c.getInt(c.getColumnIndexOrThrow("count"));
                events.add(e);
                if (LogProvider.KIND_BLOCKED.equals(e.kind)) blocked.merge(e.pkg, 1, Integer::sum);
            }
        }

        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, Integer> b : blocked.entrySet()) {
            if (s.length() > 0) s.append('\n');
            s.append(label(b.getKey())).append(": ").append(b.getValue())
                    .append(b.getValue() == 1 ? " attempt blocked" : " attempts blocked");
        }
        summary.setText(s);
        summary.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
        empty.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private CharSequence label(String pkg) {
        return labels.computeIfAbsent(pkg, p -> {
            try {
                PackageManager pm = getPackageManager();
                return pm.getApplicationLabel(pm.getApplicationInfo(p, 0));
            } catch (PackageManager.NameNotFoundException e) {
                return p;
            }
        });
    }

    /** The icon the app wanted to switch to, or the app icon for resets. */
    private Drawable icon(Event e) {
        String key = e.component != null ? e.pkg + "/" + e.component : e.pkg;
        return icons.computeIfAbsent(key, k -> {
            PackageManager pm = getPackageManager();
            try {
                if (e.component != null) {
                    return pm.getActivityInfo(new ComponentName(e.pkg, e.component),
                            PackageManager.MATCH_DISABLED_COMPONENTS).loadIcon(pm);
                }
                return pm.getApplicationIcon(e.pkg);
            } catch (PackageManager.NameNotFoundException ex) {
                return pm.getDefaultActivityIcon();
            }
        });
    }

    private static String shortName(String pkg, String component) {
        if (component == null) return "";
        if (component.startsWith(pkg + ".")) return component.substring(pkg.length() + 1);
        return component;
    }

    private class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return events.size();
        }

        @Override
        public Object getItem(int i) {
            return events.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(LogActivity.this).inflate(R.layout.row, parent, false);
            Event e = events.get(i);
            String when = DateFormat.getDateFormat(LogActivity.this).format(e.time) + " "
                    + DateFormat.getTimeFormat(LogActivity.this).format(e.time);
            ((ImageView) v.findViewById(R.id.icon)).setImageDrawable(icon(e));
            TextView title = v.findViewById(R.id.title);
            TextView detail = v.findViewById(R.id.detail);
            if (LogProvider.KIND_RESET.equals(e.kind)) {
                title.setText(label(e.pkg) + ": reset " + e.count + (e.count == 1 ? " icon" : " icons"));
                detail.setText(when);
            } else {
                title.setText(label(e.pkg) + " tried " + shortName(e.pkg, e.component));
                detail.setText(when);
            }
            return v;
        }
    }
}
