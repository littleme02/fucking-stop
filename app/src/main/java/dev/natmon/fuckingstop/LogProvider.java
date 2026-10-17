package dev.natmon.fuckingstop;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;

/**
 * Hooked apps insert a row here for every launcher change they attempt. The
 * package column comes from the caller's identity, not from the values.
 */
public class LogProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://dev.natmon.fuckingstop.log/events");
    public static final String KIND_BLOCKED = "blocked";
    public static final String KIND_RESET = "reset";

    private static final int KEEP = 2000;

    private SQLiteOpenHelper db;

    @Override
    public boolean onCreate() {
        db = new SQLiteOpenHelper(getContext(), "log.db", null, 1) {
            @Override
            public void onCreate(SQLiteDatabase d) {
                d.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY, time INTEGER, pkg TEXT,"
                        + " kind TEXT, component TEXT, state INTEGER, count INTEGER)");
            }

            @Override
            public void onUpgrade(SQLiteDatabase d, int from, int to) {
            }
        };
        return true;
    }

    @Override
    public Uri insert(Uri uri, ContentValues in) {
        String caller = getCallingPackage();
        if (caller == null || in == null) return null;
        ContentValues v = new ContentValues();
        v.put("time", System.currentTimeMillis());
        v.put("pkg", caller);
        v.put("kind", in.getAsString("kind"));
        v.put("component", in.getAsString("component"));
        v.put("state", in.getAsInteger("state"));
        v.put("count", in.getAsInteger("count"));
        SQLiteDatabase d = db.getWritableDatabase();
        long id = d.insert("events", null, v);
        d.execSQL("DELETE FROM events WHERE _id <= ?", new Object[]{id - KEEP});
        Context c = getContext();
        if (c != null) c.getContentResolver().notifyChange(URI, null);
        return Uri.withAppendedPath(URI, Long.toString(id));
    }

    /** Only the module's own UI reads the log. */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if (!isSelf()) return null;
        return db.getReadableDatabase().query("events", projection, selection, args, null, null,
                sort != null ? sort : "_id DESC");
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        if (!isSelf()) return 0;
        int n = db.getWritableDatabase().delete("events", selection, args);
        Context c = getContext();
        if (c != null) c.getContentResolver().notifyChange(URI, null);
        return n;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    private boolean isSelf() {
        return Binder.getCallingUid() == Process.myUid();
    }
}
