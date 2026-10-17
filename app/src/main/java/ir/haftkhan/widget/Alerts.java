package ir.haftkhan.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** هشدارها: میز بیش از N دقیقه، موارد مشکوک نگهبان صندوق، تراز صفر نشده */
public class Alerts {
    static final String CH = "alerts";

    static void check(Context ctx, int si, Sites.Site site, JSONObject x) {
        SharedPreferences p = ctx.getSharedPreferences(LiveWidget.PREFS, 0);
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        int limit = p.getInt("al_min", 60);
        List<String[]> found = new ArrayList<>();   // key, title, text
        if (p.getBoolean("al_tables", true)) {
            JSONArray open = x.optJSONArray("open");
            if (open != null) for (int k = 0; k < open.length(); k++) {
                JSONObject t = open.optJSONObject(k);
                if (t == null || !t.optBoolean("indoor")) continue;
                int m = t.optInt("minutes");
                if (m >= limit && m <= 360)
                    found.add(new String[]{"tbl:" + today + ":" + t.optString("num"), "میز بیش از " + LiveWidget.fa(String.valueOf(limit)) + " دقیقه",
                            LiveWidget.fa(t.optString("place", "")) + " · " + LiveWidget.fa(String.valueOf(m)) + " دقیقه · فیش " + LiveWidget.fa(t.optString("num", ""))});
            }
        }
        JSONArray al = x.optJSONArray("alerts");
        if (al != null) for (int k = 0; k < al.length(); k++) {
            JSONObject a = al.optJSONObject(k);
            if (a == null) continue;
            String type = a.optString("type");
            if ("watch".equals(type) && !p.getBoolean("al_watch", true)) continue;
            if ("taraz".equals(type) && !p.getBoolean("al_taraz", true)) continue;
            found.add(new String[]{a.optString("key"), a.optString("title"), a.optString("text")});
        }
        String seenKey = "seen_" + si;
        String raw = p.getString(seenKey, null);
        JSONArray seen;
        try { seen = raw == null ? new JSONArray() : new JSONArray(raw); } catch (Exception e) { seen = new JSONArray(); }
        boolean first = raw == null;   // بار اول: موارد قدیمی امروز را بی‌صدا ثبت کن
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < seen.length(); i++) keys.add(seen.optString(i));
        String name = site.name == null || site.name.isEmpty() ? x.optString("site_name", "رادرس") : site.name;
        for (String[] f : found) {
            if (keys.contains(f[0])) continue;
            keys.add(f[0]);
            if (!first) notify(ctx, f[0], name + " — " + f[1], f[2], site.url);
        }
        while (keys.size() > 300) keys.remove(0);
        p.edit().putString(seenKey, new JSONArray(keys).toString()).apply();
    }

    static void notify(Context ctx, String key, String title, String text, String url) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(CH) == null)
                nm.createNotificationChannel(new NotificationChannel(CH, "هشدارهای رستوران", NotificationManager.IMPORTANCE_HIGH));
            b = new Notification.Builder(ctx, CH);
        } else {
            b = new Notification.Builder(ctx).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        }
        String u = Sites.jsonUrl(url);
        Intent open = u.isEmpty() ? new Intent(ctx, MainActivity.class) : new Intent(Intent.ACTION_VIEW, Uri.parse(u.substring(0, u.length() - 5)));
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        b.setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true).setColor(0xFF0F5C4D)
                .setContentIntent(PendingIntent.getActivity(ctx, key.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        nm.notify(key.hashCode(), b.build());
    }
}
