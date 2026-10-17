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
    }

    static PendingIntent refreshIntent(Context ctx) {
        Intent i = new Intent(ctx, LiveWidget.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, refreshIntent(ctx));
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
        v.setTextViewText(R.id.title, "‏" + name);
        if (site == null) v.setTextViewText(R.id.info, "برای تنظیم، اپ «رادرس» را باز کنید");
        if (last != null) {
            try {
                JSONObject x = new JSONObject(last);
                if (site != null && (site.name == null || site.name.isEmpty())) name = x.optString("site_name", name);
                v.setTextViewText(R.id.title, "‏" + name + "‏ · ‏" + x.optString("now_t", ""));
                v.setTextViewText(R.id.amount, x.optString("today_t", "—"));
                v.setTextViewText(R.id.pct, "‏" + x.optString("pct_t", ""));
                if (!off) v.setInt(R.id.bgfill, "setColorFilter", tableColor(x.optInt("open_now", 0)));
                String R2 = "‏";
                v.setTextViewText(R.id.info, R2 + "فیش " + x.optString("orders_t", "") + R2 + "  ·  " + R2 + "میز باز " + x.optString("open_t", "") + R2);
                JSONObject best = null;
                JSONArray open = x.optJSONArray("open");
                if (open != null) for (int k = 0; k < open.length(); k++) {
                    JSONObject t = open.optJSONObject(k);
                    if (t == null || !t.optBoolean("indoor") || t.optInt("minutes") > 360) continue;
                    if (best == null || t.optInt("minutes") > best.optInt("minutes")) best = t;
                }
                if (best != null) {
                    v.setTextViewText(R.id.t_place, "‏" + fa(best.optString("place", "")));
                    v.setTextViewText(R.id.t_min, "‏⏱ " + fa(String.valueOf(best.optInt("minutes"))) + " دقیقه");
                    v.setTextViewText(R.id.t_amt, "‏" + sep(Math.round(best.optDouble("amount", 0) / 10)) + " تومان");
                } else {
                    v.setTextViewText(R.id.t_place, "میز بازی نیست");
                    v.setTextViewText(R.id.t_min, ""); v.setTextViewText(R.id.t_amt, "");
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
        v.setTextViewText(R.id.status, off && last != null ? "قطع — آخرین داده " + fa(at) : (at.isEmpty() ? "" : "به‌روزرسانی " + fa(at)));

        String u = site == null ? "" : Sites.jsonUrl(site.url);
        Intent openI = u.isEmpty() ? new Intent(ctx, MainActivity.class) : new Intent(Intent.ACTION_VIEW, Uri.parse(u.substring(0, u.length() - 5)));
        openI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean tapRefresh = "refresh".equals(p.getString("tap", "open")) && !u.isEmpty();
        v.setOnClickPendingIntent(R.id.root, tapRefresh ? refreshIntent(ctx) : PendingIntent.getActivity(ctx, 100 + widgetId, openI,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        v.setOnClickPendingIntent(R.id.refresh, refreshIntent(ctx));

        // اندازه‌ها
        float mw = shape == 1 ? w * 0.55f : w, mh = shape == 2 ? h * 0.58f : h;
        float s = Math.max(0.8f, Math.min(5f, Math.min(mw / 175f, mh / 185f)));
        int U = TypedValue.COMPLEX_UNIT_DIP;
        v.setTextViewTextSize(R.id.refresh, U, 15f * s);
        v.setTextViewTextSize(R.id.title, U, 11f * s);
        v.setTextViewTextSize(R.id.amount, U, Math.min(24f * s, (mw - 36f * Math.min(s, 3f)) / 6.8f));
        v.setTextViewTextSize(R.id.unit, U, 10f * s);
        v.setTextViewTextSize(R.id.pct, U, 11f * s);
        v.setTextViewTextSize(R.id.info, U, 11f * s);
        v.setTextViewTextSize(R.id.status, U, 9f * s);
        v.setTextViewTextSize(R.id.offline, U, 10f * s);
        float tw = shape == 1 ? w * 0.42f : w * 0.5f, th = shape == 1 ? h * 0.5f : h * 0.38f;
        float ts = Math.max(0.7f, Math.min(4f, Math.min(tw / 150f, th / 125f)));
        v.setTextViewTextSize(R.id.t_title, U, 10f * ts);
        v.setTextViewTextSize(R.id.t_place, U, 18f * ts);
        v.setTextViewTextSize(R.id.t_min, U, 13f * ts);
        v.setTextViewTextSize(R.id.t_amt, U, 10f * ts);
        int pad = (int) (6f * Math.min(s, 3f) * d), bp = (int) (8f * Math.min(s, 3f) * d);
        v.setViewPadding(R.id.content, pad, pad, pad, pad);
        v.setViewPadding(R.id.blk_main, bp, bp, bp, bp);
        v.setViewPadding(R.id.blk_tbl, bp, bp, bp, bp);
        return v;
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
