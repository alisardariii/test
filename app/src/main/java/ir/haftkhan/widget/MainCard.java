package ir.haftkhan.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * بلوک اصلی ویجت به شکل یک تصویر:
 * هر خط به اندازه‌ای کشیده می‌شود که از لبه‌ی راست تا لبه‌ی چپ را پر کند (با سقف، تا عددهای کوتاه غول‌آسا نشوند)
 * و اگر روی هم از ارتفاع بلوک بیشتر شدند، همه به یک نسبت کوچک می‌شوند.
 */
public class MainCard {
    static final int GOLD = 0xFFFFE3A0, WHITE = 0xFFFFFFFF, SOFT = 0xFFE6EEEA, DOWN = 0xFFFFB3A8, UP = 0xFFC8FFD9;

    static class Line {
        String a, b;          // متن (b فقط برای ردیف دوتکه: a راست، b چپ)
        int ca, cb;           // رنگ‌ها
        boolean bold, boldB, badge, bar;
        float ratio;          // برای نوار پیشرفت
        String cap;           // متن مرجع برای سقف اندازه
        float px;             // اندازه‌ی نهایی
    }

    static TextPaint paint(Context ctx, String font, boolean bold, int color, float px) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Typeface tf = Fonts.get(ctx, font, bold);
        if (tf == null) tf = bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        p.setTypeface(tf);
        if (bold && !"system".equals(font) && !Fonts.hasBold(font)) p.setFakeBoldText(true);
        p.setColor(color);
        p.setTextSize(px);
        return p;
    }

    static StaticLayout layout(String s, TextPaint p, int w) {
        return StaticLayout.Builder.obtain(s, 0, s.length(), p, Math.max(1, w))
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false).setMaxLines(1).build();
    }

    static String fa(long n) { return LiveWidget.sep(n); }

    /** W و H به پیکسل */
    static Bitmap draw(Context ctx, String font, String name, JSONObject x, boolean off, String at, boolean hidden, int W, int H, float d) {
        List<Line> L = new ArrayList<>();
        String orders = x == null ? "" : x.optString("orders_t", "");
        Line t = new Line(); t.a = name + (orders.isEmpty() ? "" : "  |  " + orders + " فیش"); t.ca = GOLD; t.bold = true;
        t.cap = name + "  |  ۱۰۰ فیش"; L.add(t);
        if (x == null) {
            Line m = new Line(); m.a = "برای تنظیم، اپ «رادرس» را باز کنید"; m.ca = SOFT; L.add(m);
        } else {
            Line am = new Line(); am.a = hidden ? LiveWidget.DOTS : x.optString("today_t", "—"); am.ca = WHITE; am.bold = true;
            am.cap = "۰۰۰٬۰۰۰٬۰۰۰"; L.add(am);
            Line row = new Line(); row.a = "تومان"; row.ca = SOFT;
            if (!x.isNull("pct")) {
                double pc = x.optDouble("pct", 0);
                row.b = (pc >= 0 ? "▲ " : "▼ ") + LiveWidget.fa(String.valueOf(Math.round(Math.abs(pc)))) + "٪ از هفته‌ی قبل";
                row.cb = pc >= 0 ? UP : DOWN; row.boldB = true;
            } else { row.b = "هفته‌ی قبل اطلاعاتی نیست"; row.cb = SOFT; }
            row.cap = "تومان      ▼ ۱۰۰٪ از هفته‌ی قبل"; L.add(row);
            double lwf = x.optDouble("lw_full", 0), td = x.optDouble("today", 0);
            if (lwf > 0) {
                Line bar = new Line(); bar.bar = true; bar.ratio = (float) Math.max(0, Math.min(1, td / lwf)); L.add(bar);
                Line cp = new Line(); cp.ca = SOFT;
                String p = LiveWidget.fa(String.valueOf(Math.round(td * 100 / lwf))) + "٪ از کل شب هفته‌ی قبل";
                cp.a = hidden ? p : p + " (" + LiveWidget.fa(String.valueOf(Math.round(lwf / 1e7))) + " میلیون)";
                cp.cap = "۱۰۰٪ از کل شب هفته‌ی قبل (۱۰۰۰ میلیون)"; L.add(cp);
            }
            Line inf = new Line(); inf.ca = SOFT;
            if (off) {
                inf.a = "⚠ قطع — آخرین اطلاعات " + LiveWidget.fa(at); inf.ca = WHITE; inf.bold = true; inf.badge = true;
                inf.cap = "⚠ قطع — آخرین اطلاعات ۰۰:۰۰";
            } else {
                int n = 0, oldest = -1;
                JSONArray open = x.optJSONArray("open");
                if (open != null) for (int k = 0; k < open.length(); k++) {
                    JSONObject o = open.optJSONObject(k);
                    if (o == null || !o.optBoolean("indoor") || o.optInt("minutes") > 360) continue;
                    n++; oldest = Math.max(oldest, o.optInt("minutes"));
                }
                inf.a = n == 0 ? "میز بازی در سالن نیست"
                        : LiveWidget.fa(String.valueOf(n)) + " میز باز  |  قدیمی‌ترین " + LiveWidget.fa(String.valueOf(oldest)) + " دقیقه";
                inf.cap = "۴ میز باز  |  قدیمی‌ترین ۴۰ دقیقه";
            }
            L.add(inf);
        }

        // ۱) اندازه‌ی هر خط: پر کردن عرض، با سقف متن مرجع
        float pad = 0.06f * W;   // فاصله‌ی داخلی نشان قطع
        for (Line l : L) {
            if (l.bar) continue;
            float base = 100f;
            TextPaint p = paint(ctx, font, l.bold, l.ca, base);
            float w;
            if (l.b != null) {
                TextPaint pb = paint(ctx, font, l.boldB, l.cb, base);
                w = p.measureText(l.a) + pb.measureText(l.b) + base * 1.2f;
            } else w = p.measureText(l.a);
            float avail = l.badge ? W - 2 * pad : W;
            float px = base * avail / Math.max(1f, w);
            if (l.cap != null) {
                TextPaint pc = paint(ctx, font, l.bold, l.ca, base);
                float cpx = base * avail / Math.max(1f, pc.measureText(l.cap));
                px = Math.min(px, cpx);
            }
            l.px = px;
        }
        // ۲) جا شدن در ارتفاع
        float gapK = 0.42f;
        float total = 0;
        for (int i = 0; i < L.size(); i++) total += h(L, i) + (i > 0 ? gap(L, i, gapK) : 0);
        float k = total > H ? H / total : 1f;
        for (Line l : L) l.px *= k;
        total *= k;

        Bitmap bm = Bitmap.createBitmap(Math.max(1, W), Math.max(1, H), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        float y = (H - total) / 2f;
        for (int i = 0; i < L.size(); i++) {
            Line l = L.get(i);
            if (i > 0) y += gap(L, i, gapK);
            float lh = h(L, i);
            if (l.bar) {
                float bh = lh, r = bh / 2f;
                Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG); bg.setColor(0x2EFFFFFF);
                c.drawRoundRect(new RectF(0, y, W, y + bh), r, r, bg);
                Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG); fg.setColor(0xFFF2C46B);
                float fw = Math.max(bh, W * l.ratio);
                c.drawRoundRect(new RectF(W - fw, y, W, y + bh), r, r, fg);   // از راست پر می‌شود
            } else if (l.b != null) {
                TextPaint pa = paint(ctx, font, l.bold, l.ca, l.px), pb = paint(ctx, font, l.boldB, l.cb, l.px);
                StaticLayout la = layout(l.a, pa, (int) Math.ceil(pa.measureText(l.a)) + 2);
                StaticLayout lb = layout(l.b, pb, (int) Math.ceil(pb.measureText(l.b)) + 2);
                c.save(); c.translate(W - la.getWidth(), y + (lh - la.getHeight()) / 2f); la.draw(c); c.restore();
                c.save(); c.translate(0, y + (lh - lb.getHeight()) / 2f); lb.draw(c); c.restore();
            } else {
                TextPaint p = paint(ctx, font, l.bold, l.ca, l.px);
                if (l.badge) {
                    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG); bg.setColor(0xC0B03A2E);
                    c.drawRoundRect(new RectF(0, y, W, y + lh), lh * 0.25f, lh * 0.25f, bg);
                }
                StaticLayout sl = layout(l.a, p, W);
                c.save(); c.translate(0, y + (lh - sl.getHeight()) / 2f); sl.draw(c); c.restore();
            }
            y += lh;
        }
        return bm;
    }

    static float h(List<Line> L, int i) {
        Line l = L.get(i);
        if (l.bar) {   // نوار هم‌اندازه با متن زیرش
            float ref = i + 1 < L.size() ? L.get(i + 1).px : 20f;
            return Math.max(4f, ref * 0.55f);
        }
        return l.px * (l.badge ? 1.75f : 1.32f);
    }

    static float gap(List<Line> L, int i, float k) {
        Line l = L.get(i), prev = L.get(i - 1);
        float ref = Math.min(l.bar ? h(L, i) * 2 : l.px, prev.bar ? h(L, i - 1) * 2 : prev.px);
        if (l.bar || prev.bar) return ref * 0.45f;   // نوار و متن زیرش نزدیک به هم
        return ref * k * 1.4f;
    }
}
