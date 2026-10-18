package ir.haftkhan.widget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** فهرست مجموعه‌ها (هر مجموعه = یک اسم و لینک داده‌ی داشبوردش) */
public class Sites {
    public static class Site { public String name, url; Site(String n, String u) { name = n; url = u; } }

    static SharedPreferences p(Context c) { return c.getSharedPreferences(LiveWidget.PREFS, 0); }

    public static List<Site> all(Context c) {
        List<Site> out = new ArrayList<>();
        SharedPreferences p = p(c);
        String raw = p.getString("sites", null);
        if (raw == null) {                       // انتقال تنظیم قدیمی (یک لینک)
            String u = p.getString("url", "").trim();
            if (!u.isEmpty()) { out.add(new Site("هفت‌خوان", u)); save(c, out); }
            return out;
        }
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Site(o.optString("name", ""), o.optString("url", "")));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void save(Context c, List<Site> l) {
        JSONArray a = new JSONArray();
        try { for (Site s : l) a.put(new JSONObject().put("name", s.name).put("url", s.url)); } catch (Exception ignored) {}
        p(c).edit().putString("sites", a.toString()).apply();
    }

    public static int forWidget(Context c, int widgetId) {
        int n = all(c).size();
        int i = p(c).getInt("w_" + widgetId, 0);
        return (i >= 0 && i < n) ? i : 0;
    }

    public static void setForWidget(Context c, int widgetId, int idx) { p(c).edit().putInt("w_" + widgetId, idx).apply(); }

    static final String[] KEYS = {"last_", "offline_", "last_at_", "seen_"};

    /** جابه‌جایی دو مجموعه همراه با داده‌های ذخیره‌شده و ویجت‌هایشان */
    static void swapState(Context c, int a, int b) {
        SharedPreferences p = p(c);
        SharedPreferences.Editor e = p.edit();
        java.util.Map<String, ?> all = p.getAll();
        for (String k : KEYS) {
            Object va = all.get(k + a), vb = all.get(k + b);
            put(e, k + a, vb); put(e, k + b, va);
        }
        for (String key : all.keySet()) if (key.startsWith("w_") && all.get(key) instanceof Integer) {
            int v = (Integer) all.get(key);
            if (v == a) e.putInt(key, b); else if (v == b) e.putInt(key, a);
        }
        e.apply();
    }

    static void put(SharedPreferences.Editor e, String k, Object v) {
        if (v == null) e.remove(k);
        else if (v instanceof String) e.putString(k, (String) v);
        else if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
        else if (v instanceof Integer) e.putInt(k, (Integer) v);
        else if (v instanceof Long) e.putLong(k, (Long) v);
    }

    public static void makeMain(Context c, int idx) {
        List<Site> l = all(c);
        if (idx <= 0 || idx >= l.size()) return;
        Site s = l.get(idx); l.set(idx, l.get(0)); l.set(0, s);
        save(c, l); swapState(c, 0, idx);
    }

    public static void remove(Context c, int idx) {
        List<Site> l = all(c);
        if (idx < 0 || idx >= l.size()) return;
        for (int i = idx; i < l.size() - 1; i++) swapState(c, i, i + 1);   // داده‌ها هم با فهرست یکی جلو می‌آیند
        l.remove(idx); save(c, l);
        SharedPreferences.Editor e = p(c).edit();
        for (String k : KEYS) e.remove(k + l.size());
        e.apply();
    }

    public static String jsonUrl(String u) {
        u = u == null ? "" : u.trim();
        if (u.isEmpty()) return "";
        if (u.endsWith("/mini")) u = u.substring(0, u.length() - 5);
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!u.endsWith(".json")) u = u + ".json";
        return u;
    }
}
