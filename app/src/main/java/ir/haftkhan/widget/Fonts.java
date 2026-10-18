package ir.haftkhan.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

import java.util.HashMap;
import java.util.Map;

/** فونت‌های ویجت. ویجت‌های اندروید فونت دلخواه نمی‌پذیرند، برای همین متن‌ها با فونت انتخابی به تصویر تبدیل می‌شوند. */
public class Fonts {
    // کلید، نام فارسی، آیا نسخه‌ی پررنگ جدا دارد
    static final String[][] LIST = {
            {"system", "پیش‌فرض گوشی", "0"},
            {"samim", "سمیم", "1"},
            {"vazir", "وزیرمتن", "1"},
            {"shabnam", "شبنم", "1"},
            {"sahel", "ساحل", "1"},
            {"parastoo", "پرستو", "1"},
            {"tanha", "تنها", "0"},
            {"gandom", "گندم", "0"},
            {"lalezar", "لاله‌زار", "0"},
    };
    private static final Map<String, Typeface> cache = new HashMap<>();

    static String current(Context ctx) {
        return ctx.getSharedPreferences(LiveWidget.PREFS, 0).getString("font", "system");
    }

    /** null یعنی فونت پیش‌فرض گوشی */
    static Typeface get(Context ctx, String key, boolean bold) {
        if (key == null || "system".equals(key)) return null;
        String k = key + (bold ? "-bold" : "");
        if (cache.containsKey(k)) return cache.get(k);
        Typeface tf = null;
        try { tf = Typeface.createFromAsset(ctx.getAssets(), "fonts/" + k + ".ttf"); } catch (Exception ignored) {}
        if (tf == null && bold) tf = get(ctx, key, false);
        cache.put(k, tf);
        return tf;
    }

    static boolean hasBold(String key) {
        for (String[] f : LIST) if (f[0].equals(key)) return "1".equals(f[2]);
        return false;
    }

    /** متن را با فونت انتخابی به تصویری به عرض widthDp تبدیل می‌کند (راست‌چین یا وسط‌چین) */
    static Bitmap text(Context ctx, String key, String s, float sizeDp, int color, boolean bold, boolean center,
                       int maxLines, float widthDp, float d) {
        Typeface tf = get(ctx, key, bold);
        int W = Math.max(24, Math.min(1400, (int) (widthDp * d)));
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        if (tf != null) p.setTypeface(tf);
        if (bold && !hasBold(key)) p.setFakeBoldText(true);
        float px = Math.max(6f, sizeDp * d);
        StaticLayout l = null;
        for (int k = 0; k < 14; k++) {
            p.setTextSize(px);
            l = StaticLayout.Builder.obtain(s, 0, s.length(), p, W)
                    .setAlignment(center ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .setIncludePad(true).build();
            boolean fits = l.getLineCount() <= maxLines;
            if (fits && maxLines == 1 && p.measureText(s) > W) fits = false;
            if (fits) break;
            px *= 0.9f;
        }
        Bitmap bm = Bitmap.createBitmap(W, Math.max(1, l.getHeight()), Bitmap.Config.ARGB_8888);
        l.draw(new Canvas(bm));
        return bm;
    }
}
