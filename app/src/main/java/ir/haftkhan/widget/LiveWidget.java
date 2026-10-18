package ir.haftkhan.widget;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LiveWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "ir.haftkhan.widget.REFRESH";
    static final String PREFS = "cfg";
    static final String ACTION_REVEAL = "ir.haftkhan.widget.REVEAL";
    static final String ACTION_HIDE = "ir.haftkhan.widget.HIDE";
    static final String DOTS = "••••••";

    /** حالت مخفی روشن است و الان مبلغ‌ها نباید دیده شوند؟ */
    static boolean hidden(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
        return p.getBoolean("privacy", false) && System.currentTimeMillis() > p.getLong("reveal_until", 0);
    }

    /** کاشی منوی کشویی را هم تازه کن (فقط اندروید ۷ به بالا) */
    static void tile(Context ctx) {
        if (Build.VERSION.SDK_INT < 24) return;
        try { android.service.quicksettings.TileService.requestListeningState(ctx, new ComponentName(ctx.getPackageName(), "ir.haftkhan.widget.QsTile")); } catch (Throwable ignored) {}
    }

    /** ۳۰ ثانیه نشان دادن مبلغ‌ها، بعد دوباره مخفی */
    static void reveal(Context ctx) {
        ctx.getSharedPreferences(PREFS, 0).edit().putLong("reveal_until", System.currentTimeMillis() + 30000).apply();
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 30000,
                PendingIntent.getBroadcast(ctx, 2, new Intent(ctx, LiveWidget.class).setAction(ACTION_HIDE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }


    @Override public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) { schedule(ctx); refreshAsync(ctx); }
    @Override public void onEnabled(Context ctx) { schedule(ctx); }
    @Override public void onDisabled(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(refreshIntent(ctx));
    }
    @Override public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle opts) { render(ctx); }
    @Override public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) refreshAsync(ctx);
        else if (ACTION_REVEAL.equals(intent.getAction())) { reveal(ctx); render(ctx); tile(ctx); refreshAsync(ctx); }
        else if (ACTION_HIDE.equals(intent.getAction())) { render(ctx); tile(ctx); }
    }

    static PendingIntent refreshIntent(Context ctx) {
        Intent i = new Intent(ctx, LiveWidget.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        // هر ۱۵ دقیقه، حتی وقتی گوشی در حالت خواب است (بعد از هر به‌روزرسانی دوباره تنظیم می‌شود)
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, refreshIntent(ctx));
    }

    static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(15000); c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally { c.disconnect(); }
    }

    static String fa(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch >= '0' && ch <= '9' ? (char) ('۰' + (ch - '0')) : ch);
        return b.toString();
    }
    static String sep(long n) { return fa(String.format(Locale.US, "%,d", n)).replace(',', '٬'); }

    // رنگ ویجت بر اساس میزهای باز: آبی (خالی) ← سبز ← زرد ← قرمز (۱۵ میز و بیشتر)
    static int tableColor(int n) {
        float t = Math.max(0f, Math.min(1f, n / 15f));
        return Color.HSVToColor(new float[]{210f * (1f - t), 0.72f, 0.46f});
    }

    void refreshAsync(final Context ctx) {
        final PendingResult pr = goAsync();
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() { public void run() { try { refreshNow(app); } finally { if (pr != null) pr.finish(); } } }).start();
    }

    /** همه‌ی مجموعه‌ها را تازه می‌کند، هشدارها را می‌فرستد و ویجت‌ها را دوباره می‌کشد */
    static void refreshNow(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
        List<Sites.Site> sites = Sites.all(ctx);
        for (int i = 0; i < sites.size(); i++) {
            String url = Sites.jsonUrl(sites.get(i).url);
            if (url.isEmpty()) continue;
            try {
                String body = fetch(url);
                JSONObject x = new JSONObject(body);
                p.edit().putString("last_" + i, body).putBoolean("offline_" + i, false)
                        .putString("last_at_" + i, new SimpleDateFormat("HH:mm", Locale.US).format(new Date())).apply();
                Alerts.check(ctx, i, sites.get(i), x);
            } catch (Exception e) {
                p.edit().putBoolean("offline_" + i, true).apply();
            }
        }
        render(ctx);
        tile(ctx);
        schedule(ctx);
    }

    static void render(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        for (int id : m.getAppWidgetIds(new ComponentName(ctx, LiveWidget.class))) {
            try { m.updateAppWidget(id, build(ctx, id, m.getAppWidgetOptions(id))); } catch (Exception ignored) {}
        }
    }

    // شکل ویجت: ۰ = مربعی، ۱ = مستطیل افقی، ۲ = مستطیل عمودی
    static RemoteViews build(Context ctx, int widgetId, Bundle o) {
        int w = 0, h = 0;
        if (o != null) {
            w = Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0));
            h = Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0));
        }
        if (w <= 0) w = 150;
        if (h <= 0) h = 150;
        int shape = w >= h * 1.45f ? 1 : (h >= w * 1.35f ? 2 : 0);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), shape == 1 ? R.layout.widget_wide : R.layout.widget);
        java.util.HashMap<Integer, String> tx = new java.util.HashMap<>();
        java.util.HashMap<Integer, Float> sz = new java.util.HashMap<>();
        v.setViewVisibility(R.id.extra, shape == 0 ? View.GONE : View.VISIBLE);

        SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
        List<Sites.Site> sites = Sites.all(ctx);
        int si = Sites.forWidget(ctx, widgetId);
        Sites.Site site = sites.isEmpty() ? null : sites.get(si);
        String last = p.getString("last_" + si, null);
        boolean off = p.getBoolean("offline_" + si, false);
        float d = ctx.getResources().getDisplayMetrics().density;
        v.setInt(R.id.bgfill, "setColorFilter", tableColor(0));
        String name = site != null && site.name != null && !site.name.isEmpty() ? site.name : "رادرس";
        tt(v, tx, R.id.title, "‏" + name);
        if (site == null) tt(v, tx, R.id.info, "برای تنظیم، اپ «رادرس» را باز کنید");
        if (last != null) {
            try {
                JSONObject x = new JSONObject(last);
                if (site != null && (site.name == null || site.name.isEmpty())) name = x.optString("site_name", name);
                tt(v, tx, R.id.title, "‏" + name + "‏ · ‏" + x.optString("now_t", ""));
                boolean hid = hidden(ctx);
                tt(v, tx, R.id.amount, hid ? DOTS : x.optString("today_t", "—"));
                tt(v, tx, R.id.pct, "‏" + x.optString("pct_t", ""));
                if (!off) v.setInt(R.id.bgfill, "setColorFilter", tableColor(x.optInt("open_now", 0)));
                String R2 = "‏";
                tt(v, tx, R.id.info, R2 + "فیش " + x.optString("orders_t", "") + R2 + "  ·  " + R2 + "میز باز " + x.optString("open_t", "") + R2);
                JSONObject best = null;
                JSONArray open = x.optJSONArray("open");
                if (open != null) for (int k = 0; k < open.length(); k++) {
                    JSONObject t = open.optJSONObject(k);
                    if (t == null || !t.optBoolean("indoor") || t.optInt("minutes") > 360) continue;
                    if (best == null || t.optInt("minutes") > best.optInt("minutes")) best = t;
                }
                if (best != null) {
                    tt(v, tx, R.id.t_place, "‏" + fa(best.optString("place", "")));
                    tt(v, tx, R.id.t_min, "‏⏱ " + fa(String.valueOf(best.optInt("minutes"))) + " دقیقه");
                    tt(v, tx, R.id.t_amt, "‏" + (hid ? DOTS : sep(Math.round(best.optDouble("amount", 0) / 10))) + " تومان");
                } else {
                    tt(v, tx, R.id.t_place, "میز بازی نیست");
                    tt(v, tx, R.id.t_min, ""); tt(v, tx, R.id.t_amt, "");
                }
                if (shape != 0) {
                    int cw = (int) ((shape == 1 ? w * 0.40f : w * 0.48f) * d), ch = (int) ((shape == 1 ? h * 0.40f : h * 0.30f) * d);
                    Bitmap bm = chart(x, Math.max(120, Math.min(cw, 900)), Math.max(60, Math.min(ch, 500)), d);
                    if (bm != null) v.setImageViewBitmap(R.id.chart, bm);
                }
            } catch (Exception ignored) {}
        }
        if (off) v.setInt(R.id.bgfill, "setColorFilter", 0x40FFFFFF); // شیشه‌ای وقتی ارتباط قطع است
        v.setViewVisibility(R.id.offline, off ? View.VISIBLE : View.GONE);
        String at = p.getString("last_at_" + si, "");
        tt(v, tx, R.id.status, off && last != null ? "قطع — آخرین داده " + fa(at) : (at.isEmpty() ? "" : "به‌روزرسانی " + fa(at)));

        String u = site == null ? "" : Sites.jsonUrl(site.url);
        Intent openI = u.isEmpty() ? new Intent(ctx, MainActivity.class) : new Intent(Intent.ACTION_VIEW, Uri.parse(u.substring(0, u.length() - 5)));
        openI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean tapRefresh = "refresh".equals(p.getString("tap", "open")) && !u.isEmpty();
        PendingIntent tapI = tapRefresh ? refreshIntent(ctx) : PendingIntent.getActivity(ctx, 100 + widgetId, openI,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // در حالت مخفی، اولین ضربه مبلغ‌ها را ۳۰ ثانیه نشان می‌دهد؛ ضربه‌ی بعدی (وقتی پیداست) کار همیشگی را می‌کند
        if (hidden(ctx)) tapI = PendingIntent.getBroadcast(ctx, 3, new Intent(ctx, LiveWidget.class).setAction(ACTION_REVEAL),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.root, tapI);
        v.setOnClickPendingIntent(R.id.refresh, refreshIntent(ctx));

        // اندازه‌ها
        float mw = shape == 1 ? w * 0.55f : w, mh = shape == 2 ? h * 0.58f : h;
        float s = Math.max(0.8f, Math.min(5f, Math.min(mw / 175f, mh / 185f)));
        int U = TypedValue.COMPLEX_UNIT_DIP;
        v.setTextViewTextSize(R.id.refresh, U, 15f * s);
        ts(v, sz, R.id.title, 11f * s);
        ts(v, sz, R.id.amount, Math.min(24f * s, (mw - 36f * Math.min(s, 3f)) / 6.8f));
        ts(v, sz, R.id.unit, 10f * s);
        ts(v, sz, R.id.pct, 11f * s);
        ts(v, sz, R.id.info, 11f * s);
        ts(v, sz, R.id.status, 9f * s);
        v.setTextViewTextSize(R.id.offline, U, 10f * s);
        float tw = shape == 1 ? w * 0.42f : w * 0.5f, th = shape == 1 ? h * 0.5f : h * 0.38f;
        float ts = Math.max(0.7f, Math.min(4f, Math.min(tw / 150f, th / 125f)));
        ts(v, sz, R.id.t_title, 10f * ts);
        ts(v, sz, R.id.t_place, 18f * ts);
        ts(v, sz, R.id.t_min, 13f * ts);
        ts(v, sz, R.id.t_amt, 10f * ts);
        int pad = (int) (6f * Math.min(s, 3f) * d), bp = (int) (8f * Math.min(s, 3f) * d);
        v.setViewPadding(R.id.content, pad, pad, pad, pad);
        v.setViewPadding(R.id.blk_main, bp, bp, bp, bp);
        v.setViewPadding(R.id.blk_tbl, bp, bp, bp, bp);
        applyFont(ctx, v, tx, sz, shape, w, s, pad, bp, d);
        // بلوک اصلی: یک تصویر با خط‌های هم‌عرض (اندازه‌ی هر خط خودش با عرض ویجت تنظیم می‌شود)
        try {
            JSONObject xx = last == null ? null : new JSONObject(last);
            String nm = name;
            if (xx != null && site != null && (site.name == null || site.name.isEmpty())) nm = xx.optString("site_name", name);
            float cw = w, ch = h;
            if (o != null) {
                int mnW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0), mxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
                int mnH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0), mxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
                boolean port = ctx.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
                if (port && mnW > 0 && mxH > 0) { cw = mnW; ch = mxH; } else if (!port && mxW > 0 && mnH > 0) { cw = mxW; ch = mnH; }
            }
            float padDp = pad / d, bpDp = bp / d;
            float bw = shape == 1 ? (cw - 2 * padDp - 8f) * 1.35f / 2.35f : cw - 2 * padDp;
            float bh = shape == 2 ? (ch - 2 * padDp - 8f) * 1.5f / 2.5f : ch - 2 * padDp;
            int W = (int) ((bw - 2 * bpDp) * d), H = (int) ((bh - 2 * bpDp) * d);
            float lim = Math.max(W, H) > 1100 ? 1100f / Math.max(W, H) : 1f;
            W = Math.max(60, (int) (W * lim)); H = Math.max(60, (int) (H * lim));
            Bitmap card = MainCard.draw(ctx, Fonts.current(ctx), nm, xx, off && last != null, at, hidden(ctx), W, H, d);
            v.setImageViewBitmap(R.id.main_img, card);
            v.setViewVisibility(R.id.main_img, View.VISIBLE);
            for (int id : new int[]{R.id.hdr_row, R.id.amount, R.id.amount_img, R.id.unit, R.id.unit_img, R.id.pct, R.id.pct_img,
                    R.id.info, R.id.info_img, R.id.status, R.id.status_img})
                v.setViewVisibility(id, View.GONE);
        } catch (Throwable e) {
            v.setViewVisibility(R.id.main_img, View.GONE);
        }
        return v;
    }


    static void tt(RemoteViews v, java.util.Map<Integer, String> tx, int id, CharSequence t) { tx.put(id, t.toString()); v.setTextViewText(id, t); }
    static void ts(RemoteViews v, java.util.Map<Integer, Float> sz, int id, float dip) { sz.put(id, dip); v.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, dip); }

    // id متن، id تصویرش، رنگ، پررنگ، وسط‌چین، حداکثر خط، متن پیش‌فرض
    static final Object[][] TXT = {
            {R.id.title, R.id.title_img, 0xFFFFE3A0, true, false, 1, "هفت‌خوان"},
            {R.id.amount, R.id.amount_img, 0xFFFFFFFF, true, false, 1, "—"},
            {R.id.unit, R.id.unit_img, 0xFFE6EEEA, false, false, 1, "تومان"},
            {R.id.pct, R.id.pct_img, 0xFFFFFFFF, false, false, 2, ""},
            {R.id.info, R.id.info_img, 0xFFE6EEEA, false, false, 2, ""},
            {R.id.status, R.id.status_img, 0xFFC9D6D0, false, false, 1, ""},
            {R.id.t_title, R.id.t_title_img, 0xFFFFE3A0, true, true, 1, "قدیمی‌ترین میز باز"},
            {R.id.t_place, R.id.t_place_img, 0xFFFFFFFF, true, true, 1, "—"},
            {R.id.t_min, R.id.t_min_img, 0xFFFFFFFF, false, true, 1, ""},
            {R.id.t_amt, R.id.t_amt_img, 0xFFE6EEEA, false, true, 1, ""},
    };

    /** اگر فونتی غیر از پیش‌فرض گوشی انتخاب شده، متن‌ها را با همان فونت به تصویر تبدیل کن */
    static void applyFont(Context ctx, RemoteViews v, java.util.Map<Integer, String> tx, java.util.Map<Integer, Float> sz,
                          int shape, float w, float s, int pad, int bp, float d) {
        String key = Fonts.current(ctx);
        boolean custom = !"system".equals(key) && Fonts.get(ctx, key, false) != null;
        float padDp = pad / d, bpDp = bp / d;
        float mainW = (shape == 1 ? w * 1.35f / 2.35f - 4f : w) - 2 * padDp - 2 * bpDp;
        float tblW = (shape == 1 ? w / 2.35f - 4f - 2 * padDp - 2 * bpDp : (w - 2 * padDp - 8f) / 2f - 2 * bpDp);
        for (Object[] t : TXT) {
            int id = (Integer) t[0], img = (Integer) t[1];
            String text = tx.containsKey(id) ? tx.get(id) : (String) t[6];
            if (!custom || text == null || text.replace("\u200f", "").trim().isEmpty()) {
                v.setViewVisibility(img, View.GONE); v.setViewVisibility(id, View.VISIBLE); continue;
            }
            boolean inTbl = id == R.id.t_title || id == R.id.t_place || id == R.id.t_min || id == R.id.t_amt;
            float wd = inTbl ? tblW : (id == R.id.title ? mainW - 26f * Math.min(s, 3f) : mainW);
            Float size = sz.get(id);
            try {
                Bitmap bm = Fonts.text(ctx, key, text, size == null ? 12f : size, (Integer) t[2], (Boolean) t[3], (Boolean) t[4],
                        (Integer) t[5], Math.max(40f, wd), d);
                v.setImageViewBitmap(img, bm);
                v.setViewVisibility(img, View.VISIBLE); v.setViewVisibility(id, View.GONE);
            } catch (Throwable e) {
                v.setViewVisibility(img, View.GONE); v.setViewVisibility(id, View.VISIBLE);
            }
        }
    }

    /** نمودار کوچک فروش امروز (طلایی) در برابر هفته‌ی قبل (خط‌چین) */
    static Bitmap chart(JSONObject x, int W, int H, float d) {
        JSONArray a = x.optJSONArray("s_today"), b = x.optJSONArray("s_lw");
        if (a == null || b == null || b.length() < 2) return null;
        int n = Math.max(a.length(), b.length());
        double max = 1;
        for (int i = 0; i < n; i++) { max = Math.max(max, a.optDouble(i, 0)); max = Math.max(max, b.optDouble(i, 0)); }
        Bitmap bm = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        float pad = 4 * d, iw = W - 2 * pad, ih = H - 2 * pad;
        Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG); grid.setColor(0x26FFFFFF); grid.setStrokeWidth(1);
        for (int g = 1; g <= 3; g++) { float y = pad + ih * g / 4f; c.drawLine(pad, y, W - pad, y, grid); }
        Path lw = new Path(); boolean st = false;
        for (int i = 0; i < b.length(); i++) {
            if (b.isNull(i)) continue;
            float px = pad + iw * i / (n - 1f), py = pad + ih * (1f - (float) (b.optDouble(i) / max));
            if (!st) { lw.moveTo(px, py); st = true; } else lw.lineTo(px, py);
        }
        Paint pl = new Paint(Paint.ANTI_ALIAS_FLAG); pl.setStyle(Paint.Style.STROKE); pl.setStrokeWidth(1.6f * d);
        pl.setColor(0xB3FFFFFF); pl.setPathEffect(new DashPathEffect(new float[]{5 * d, 4 * d}, 0));
        c.drawPath(lw, pl);
        Path td = new Path(); st = false; float lastX = pad;
        for (int i = 0; i < a.length(); i++) {
            if (a.isNull(i)) break;
            float px = pad + iw * i / (n - 1f), py = pad + ih * (1f - (float) (a.optDouble(i) / max));
            if (!st) { td.moveTo(px, py); st = true; } else td.lineTo(px, py);
            lastX = px;
        }
        if (st) {
            Path fill = new Path(td); fill.lineTo(lastX, pad + ih); fill.lineTo(pad, pad + ih); fill.close();
            Paint pf = new Paint(Paint.ANTI_ALIAS_FLAG);
            pf.setShader(new LinearGradient(0, pad, 0, pad + ih, 0x80F2C46B, 0x00F2C46B, Shader.TileMode.CLAMP));
            c.drawPath(fill, pf);
            Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG); pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(2.4f * d);
            pt.setColor(0xFFF2C46B); pt.setStrokeJoin(Paint.Join.ROUND); pt.setStrokeCap(Paint.Cap.ROUND);
            c.drawPath(td, pt);
        }
        return bm;
    }
}
