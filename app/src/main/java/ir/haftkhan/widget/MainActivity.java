package ir.haftkhan.widget;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class MainActivity extends Activity {
    Typeface reg, bold;
    SharedPreferences p;
    int editing = -1;

    int page = R.id.pg_home;
    final int[] PAGES = {R.id.pg_home, R.id.pg_sites, R.id.pg_alerts, R.id.pg_look, R.id.pg_update, R.id.pg_guide};

    /** تم برنامه: روشن، تیره یا مثل گوشی (pref "theme": light / dark / auto) */
    @Override
    protected void attachBaseContext(Context base) {
        String t = base.getSharedPreferences(LiveWidget.PREFS, 0).getString("theme", "auto");
        if ("auto".equals(t)) { super.attachBaseContext(base); return; }
        android.content.res.Configuration c = new android.content.res.Configuration(base.getResources().getConfiguration());
        c.uiMode = (c.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                | ("dark".equals(t) ? android.content.res.Configuration.UI_MODE_NIGHT_YES : android.content.res.Configuration.UI_MODE_NIGHT_NO);
        super.attachBaseContext(base.createConfigurationContext(c));
    }

    boolean night() {
        return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    int col(int id) { return getResources().getColor(id, null); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean dk = night();
        getWindow().setBackgroundDrawable(new GlassBg(dk));
        getWindow().setStatusBarColor(col(R.color.c_bar));
        getWindow().setNavigationBarColor(col(R.color.c_bar));
        getWindow().getDecorView().setSystemUiVisibility(dk ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_main);
        try {
            reg = Typeface.createFromAsset(getAssets(), "fonts/samim.ttf");
            bold = Typeface.createFromAsset(getAssets(), "fonts/samim-bold.ttf");
        } catch (Exception e) { reg = Typeface.DEFAULT; bold = Typeface.DEFAULT_BOLD; }
        p = getSharedPreferences(LiveWidget.PREFS, 0);

        // سرصفحه‌ی زیرصفحه‌ها: «→ عنوان»
        for (int id : PAGES) headers(findViewById(id));

        // راهنما
        String[] steps = {
                "در «مجموعه‌ها» دکمه‌ی «+ افزودن مجموعه» را بزنید، اسم و لینک «داده‌ی خام» صفحه‌ی ‎/widget‎ داشبورد را بگذارید و «ذخیره» را بزنید.",
                "روی صفحه‌ی اصلی گوشی، جای خالی را نگه دارید ← ابزارک‌ها (Widgets).",
                "زیر «رادرس»، ویجت «گزارش زنده» را روی صفحه بکشید (اگر چند مجموعه دارید، مجموعه را انتخاب کنید).",
                "ویجت را مربعی، عریض یا بلند کنید؛ در حالت عریض و بلند، قدیمی‌ترین میز و نمودار هم می‌آید.",
                "در «هشدارها» موارد دلخواه را روشن کنید؛ همان لحظه ذخیره می‌شود."};
        LinearLayout box = (LinearLayout) findViewById(R.id.steps);
        for (int i = 0; i < steps.length; i++) box.addView(stepRow(i + 1, steps[i]));

        // صفحه‌ی اصلی
        findViewById(R.id.refresh_now).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { refreshAll(true); }
        });
        findViewById(R.id.hero_open).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                List<Sites.Site> l = Sites.all(MainActivity.this);
                if (l.isEmpty()) { go(R.id.pg_sites); return; }
                String u = Sites.jsonUrl(l.get(0).url);
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u.substring(0, u.length() - 5)))); } catch (Exception ignored) {}
            }
        });
        findViewById(R.id.row_log).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { go(R.id.pg_alerts); }
        });

        icon((TextView) findViewById(R.id.refresh_now), R.drawable.ic_refresh, 0xFF1A1405);
        icon((TextView) findViewById(R.id.hero_open), R.drawable.ic_ext, Color.WHITE);

        // مجموعه‌ها
        findViewById(R.id.add_site).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openForm(-1); }
        });
        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeForm(); }
        });
        findViewById(R.id.paste).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData c = cm == null ? null : cm.getPrimaryClip();
                if (c != null && c.getItemCount() > 0) {
                    CharSequence t = c.getItemAt(0).coerceToText(MainActivity.this);
                    if (t != null) ((EditText) findViewById(R.id.url)).setText(t.toString().trim());
                }
            }
        });
        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { saveSite(); }
        });
        fillSites();

        // هشدارها — هر تغییر همان لحظه ذخیره می‌شود
        alertSwitch(R.id.al_tables, "al_tables", "میز بیش از حد مجاز نشسته", "وقتی میزی در سالن بیشتر از این بماند");
        alertSwitch(R.id.al_watch, "al_watch", "نگهبان صندوق", "حذف فیش، تخفیف بعد از چاپ، پرداخت تکراری");
        alertSwitch(R.id.al_taraz, "al_taraz", "تراز صفر نشده", "آخر شب، اگر حساب فیش‌ها باز مانده باشد");
        alertSwitch(R.id.al_sum, "al_sum", "خلاصه‌ی فروش امشب", "آخر شب: فروش، تعداد فیش و سهم هر روش پرداخت");
        setupMinutes();
        findViewById(R.id.al_test).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!notifOk()) { askNotif(); return; }
                Alerts.notify(MainActivity.this, "test:" + System.currentTimeMillis(), "رادرس — هشدار آزمایشی",
                        "اگر این پیغام را می‌بینید، هشدارها درست کار می‌کنند.", "");
                show((TextView) findViewById(R.id.al_res), "✓ پیغام آزمایشی فرستاده شد؛ بالای صفحه را نگاه کنید.", 1);
            }
        });
        findViewById(R.id.perm_warn).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!notifOk()) askNotif();
                else try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) {}
            }
        });
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);

        // ظاهر ویجت
        View.OnClickListener seg = new View.OnClickListener() {
            public void onClick(View v) {
                p.edit().putString("tap", v.getId() == R.id.tapRefresh ? "refresh" : "open").apply();
                fillSeg();
                renderWidget();
            }
        };
        findViewById(R.id.tapOpen).setOnClickListener(seg);
        findViewById(R.id.tapRefresh).setOnClickListener(seg);
        final Switch pv = (Switch) findViewById(R.id.privacy);
        tint(pv);
        twoLine(pv, "حالت مخفی", "مبلغ‌ها پنهان؛ با زدن روی ویجت ۳۰ ثانیه دیده می‌شوند");
        pv.setChecked(p.getBoolean("privacy", false));
        pv.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) {
                p.edit().putBoolean("privacy", v).putLong("reveal_until", 0).apply();
                preview();
                new Thread(new Runnable() { public void run() { LiveWidget.render(getApplicationContext()); LiveWidget.tile(getApplicationContext()); } }).start();
            }
        });
        findViewById(R.id.font_dd).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setFontList(findViewById(R.id.font_list).getVisibility() != View.VISIBLE); }
        });

        setupUpdate();
        applyFont(findViewById(R.id.page));
        fillSeg();
        fillTheme();
        fillFonts();
        fillMenu();
        refreshAll(false);
        if (b != null) go(b.getInt("page", R.id.pg_home));
    }

    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); o.putInt("page", page); }

    /** آیکون خطی کنار متن دکمه */
    void icon(TextView t, int res, int color) {
        android.graphics.drawable.Drawable d = getDrawable(res).mutate();
        d.setTint(color); d.setBounds(0, 0, dp(19), dp(19));
        t.setCompoundDrawablesRelative(d, null, null, null);
        t.setCompoundDrawablePadding(dp(8));
    }

    // ───── تم ─────
    void fillTheme() {
        LinearLayout g = (LinearLayout) findViewById(R.id.theme_seg);
        g.removeAllViews();
        String cur = p.getString("theme", "auto");
        String[][] o = {{"light", "روشن"}, {"dark", "تیره"}, {"auto", "مثل گوشی"}};
        int[] ic = {R.drawable.ic_sun, R.drawable.ic_moon, R.drawable.ic_phone};
        for (int i = 0; i < o.length; i++) {
            final String key = o[i][0];
            boolean on = key.equals(cur);
            TextView t = new TextView(this);
            t.setText(o[i][1]); t.setGravity(Gravity.CENTER); t.setTextSize(13.5f);
            t.setTypeface(on ? bold : reg);
            int c = col(on ? R.color.c_head : R.color.c_mut);
            t.setTextColor(c);
            android.graphics.drawable.Drawable d = getDrawable(ic[i]).mutate();
            d.setTint(c); d.setBounds(0, 0, dp(22), dp(22));
            t.setCompoundDrawables(null, d, null, null); t.setCompoundDrawablePadding(dp(4));
            t.setPadding(0, dp(10), 0, dp(9));
            if (on) t.setBackgroundResource(R.drawable.seg_on);
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (key.equals(p.getString("theme", "auto"))) return;
                    p.edit().putString("theme", key).commit();
                    recreate();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) lp.setMarginStart(dp(4));
            g.addView(t, lp);
        }
    }

    // ───── حد مجاز میز: نوار کشویی ۳۰ تا ۱۲۰ دقیقه، هر پله ۵ دقیقه ─────
    static final int MIN_LO = 30, MIN_HI = 120, MIN_STEP = 5;

    void setupMinutes() {
        final android.widget.SeekBar sb = (android.widget.SeekBar) findViewById(R.id.al_min);
        sb.setMax((MIN_HI - MIN_LO) / MIN_STEP);
        sb.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#F2C46B")));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(col(R.color.c_swoff)));
        int m = Math.max(MIN_LO, Math.min(MIN_HI, p.getInt("al_min", 60)));
        sb.setProgress((m - MIN_LO) / MIN_STEP);
        minTitle(m);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s2, int v, boolean user) {
                int mm = MIN_LO + v * MIN_STEP;
                minTitle(mm);
                if (user) p.edit().putInt("al_min", mm).apply();
            }
            public void onStartTrackingTouch(android.widget.SeekBar s2) {}
            public void onStopTrackingTouch(android.widget.SeekBar s2) { fillMenu(); }
        });
        LinearLayout tk = (LinearLayout) findViewById(R.id.ticks);
        tk.removeAllViews();
        String[] lab = {"۳۰", "۶۰", "۹۰", "۱۲۰"};
        for (int i = 0; i < lab.length; i++) {
            if (i > 0) tk.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            TextView t = new TextView(this);
            t.setText(lab[i]); t.setTextSize(11.5f); t.setTextColor(col(R.color.c_mut)); t.setTypeface(reg);
            tk.addView(t);
        }
        tk.setPadding(dp(12), 0, dp(12), 0);
    }

    void minTitle(int m) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("میز بیش از ");
        int a = sb.length(); sb.append("\u00A0").append(LiveWidget.fa(String.valueOf(m))).append("\u00A0");
        sb.setSpan(new Pill(col(R.color.c_acc), night() ? 0xFF10201B : Color.WHITE, bold), a, sb.length(), 0);
        sb.append(" دقیقه");
        int s0 = sb.length();
        sb.append("\n").append("وقتی میزی در سالن بیشتر از این بماند");
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.8f), s0, sb.length(), 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(col(R.color.c_mut)), s0, sb.length(), 0);
        ((Switch) findViewById(R.id.al_tables)).setText(sb);
    }

    /** عدد داخل یک قرص رنگی */
    static class Pill extends android.text.style.ReplacementSpan {
        final int bg, fg; final Typeface tf;
        Pill(int bg, int fg, Typeface tf) { this.bg = bg; this.fg = fg; this.tf = tf; }
        @Override public int getSize(android.graphics.Paint pt, CharSequence t, int s, int e, android.graphics.Paint.FontMetricsInt fm) {
            Typeface o = pt.getTypeface(); pt.setTypeface(tf);
            int w = Math.round(pt.measureText(t, s, e)); pt.setTypeface(o); return w;
        }
        @Override public void draw(android.graphics.Canvas c, CharSequence t, int s, int e, float x, int top, int y, int bottom, android.graphics.Paint pt) {
            Typeface o = pt.getTypeface(); int oc = pt.getColor();
            pt.setTypeface(tf);
            float w = pt.measureText(t, s, e);
            android.graphics.Paint.FontMetrics fm = pt.getFontMetrics();
            pt.setColor(bg);
            float r = (fm.descent - fm.ascent) * 0.3f;
            c.drawRoundRect(new android.graphics.RectF(x, y + fm.ascent * 0.92f, x + w, y + fm.descent * 0.9f), r, r, pt);
            pt.setColor(fg); c.drawText(t, s, e, x, y, pt);
            pt.setTypeface(o); pt.setColor(oc);
        }
    }

    @Override protected void onResume() { super.onResume(); permState(); fillHome(); fillStatus(); fillLog(); fillMenu(); }

    // ───── جابه‌جایی بین صفحه‌ها ─────
    void go(int id) {
        if (id == R.id.pg_look) { setFontList(false); preview(); }
        if (id == R.id.pg_home) { fillHome(); fillMenu(); }
        for (int x : PAGES) findViewById(x).setVisibility(x == id ? View.VISIBLE : View.GONE);
        ((android.widget.ScrollView) findViewById(id)).scrollTo(0, 0);
        View f = getCurrentFocus();
        if (f != null) {
            f.clearFocus();
            android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(f.getWindowToken(), 0);
        }
        page = id;
    }

    @Override public void onBackPressed() {
        if (page != R.id.pg_home) go(R.id.pg_home); else super.onBackPressed();
    }

    void headers(View v) {
        if (v instanceof TextView && "hdr".equals(v.getTag())) {
            TextView t = (TextView) v;
            t.setTag("b");
            t.setTextColor(col(R.color.c_head)); t.setTextSize(21);
            android.graphics.drawable.Drawable bk = getDrawable(R.drawable.ic_back).mutate();
            bk.setTint(col(R.color.c_head)); bk.setBounds(0, 0, dp(24), dp(24));
            t.setCompoundDrawablesRelative(bk, null, null, null); t.setCompoundDrawablePadding(dp(10));
            t.setPadding(dp(4), dp(22), dp(4), dp(16));
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { go(R.id.pg_home); } });
            return;
        }
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) headers(g.getChildAt(i)); }
    }

    // ───── منوی تنظیمات ─────
    void fillMenu() {
        LinearLayout m = (LinearLayout) findViewById(R.id.menu);
        m.removeAllViews();
        int ns = Sites.all(this).size();
        int on = 0;
        for (String k : new String[]{"al_tables", "al_watch", "al_taraz", "al_sum"}) if (p.getBoolean(k, true)) on++;
        String fn = "";
        for (String[] f : Fonts.LIST) if (f[0].equals(Fonts.current(this))) fn = f[1];
        menuRow(m, R.drawable.ic_store, "مجموعه‌ها", ns == 0 ? "هنوز نیست" : LiveWidget.fa(String.valueOf(ns)) + " مجموعه", null, R.id.pg_sites);
        menuRow(m, R.drawable.ic_bell, "هشدارها", LiveWidget.fa(String.valueOf(on)) + " از ۴ روشن", null, R.id.pg_alerts);
        String th = p.getString("theme", "auto");
        menuRow(m, R.drawable.ic_widget, "ظاهر", fn + " · " + ("dark".equals(th) ? "تیره" : "light".equals(th) ? "روشن" : "مثل گوشی"), null, R.id.pg_look);
        menuRow(m, R.drawable.ic_down, "به‌روزرسانی", newApk == null ? LiveWidget.fa(myName()) : null, newApk == null ? null : "نسخه‌ی " + LiveWidget.fa(newName), R.id.pg_update);
        menuRow(m, R.drawable.ic_help, "راهنمای نصب ویجت", null, null, R.id.pg_guide);
    }

    void menuRow(LinearLayout m, int ico, String title, String val, String badge, final int target) {
        if (m.getChildCount() > 0) {
            View d = new View(this); d.setBackgroundColor(col(R.color.c_line));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            dl.setMarginStart(dp(62)); m.addView(d, dl);
        }
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(13), dp(16), dp(13));
        r.setBackgroundResource(R.drawable.rowsel); r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { go(target); } });
        android.widget.ImageView ic = new android.widget.ImageView(this);
        ic.setImageResource(ico); ic.setColorFilter(col(R.color.c_acc));
        ic.setBackgroundResource(R.drawable.ico_bg); ic.setPadding(dp(7), dp(7), dp(7), dp(7));
        r.addView(ic, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView t = new TextView(this);
        t.setText(title); t.setTextSize(16); t.setTextColor(col(R.color.c_text)); t.setTypeface(reg);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.setMarginStart(dp(12)); r.addView(t, tl);
        if (val != null && !val.isEmpty()) {
            TextView v = new TextView(this);
            v.setText(val); v.setTextSize(14); v.setTextColor(col(R.color.c_mut)); v.setTypeface(reg);
            r.addView(v);
        }
        if (badge != null) {
            TextView v = new TextView(this);
            v.setText(badge); v.setTextSize(12); v.setTextColor(Color.WHITE); v.setTypeface(bold);
            v.setBackgroundResource(R.drawable.badge); v.setPadding(dp(9), dp(2), dp(9), dp(2));
            r.addView(v);
        }
        android.widget.ImageView ch = new android.widget.ImageView(this);
        ch.setImageResource(R.drawable.ic_chev); ch.setColorFilter(col(R.color.c_chev));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(16), dp(16));
        cl.setMarginStart(dp(10)); r.addView(ch, cl);
        m.addView(r);
    }

    // ───── کارت سبز صفحه‌ی اصلی ─────
    void fillHome() {
        List<Sites.Site> l = Sites.all(this);
        TextView nm = (TextView) findViewById(R.id.hero_name), st = (TextView) findViewById(R.id.hero_state),
                am = (TextView) findViewById(R.id.hero_amt), sub = (TextView) findViewById(R.id.hero_sub),
                op = (TextView) findViewById(R.id.hero_open);
        if (l.isEmpty()) {
            nm.setText("هنوز مجموعه‌ای اضافه نشده"); st.setVisibility(View.GONE);
            am.setText("—"); sub.setText("برای شروع، یک مجموعه اضافه کنید."); sub.setTextColor(Color.parseColor("#CFE3DA"));
            op.setText("+ افزودن مجموعه");
        } else {
            op.setText("باز کردن گزارش");
            nm.setText(l.get(0).name);
            boolean off = p.getBoolean("offline_0", false);
            String at = p.getString("last_at_0", "");
            st.setVisibility(View.VISIBLE);
            st.setText((off ? "✕ قطع" : "● وصل") + (at.isEmpty() ? "" : " · " + LiveWidget.fa(at)));
            st.setTextColor(Color.parseColor(off ? "#FFC2B8" : "#FFFFFF"));
            JSONObject x = null;
            try { x = new JSONObject(p.getString("last_0", "")); } catch (Exception ignored) {}
            if (x == null) { am.setText("—"); sub.setText("هنوز اطلاعاتی نیامده؛ «تازه کن» را بزنید."); sub.setTextColor(Color.parseColor("#CFE3DA")); }
            else {
                android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(x.optString("today_t", "—"));
                int s0 = sb.length(); sb.append("  تومان");
                sb.setSpan(new android.text.style.RelativeSizeSpan(0.45f), s0, sb.length(), 0);
                sb.setSpan(new CustomFont(reg), s0, sb.length(), 0);
                am.setText(sb);
                StringBuilder s = new StringBuilder();
                int col = Color.parseColor("#CFE3DA");
                if (!x.isNull("pct")) {
                    double pc = x.optDouble("pct", 0);
                    s.append(pc >= 0 ? "▲ " : "▼ ").append(LiveWidget.fa(String.valueOf(Math.round(Math.abs(pc))))).append("٪ از هفته‌ی قبل");
                    col = Color.parseColor(pc >= 0 ? "#C8FFD9" : "#FFC2B8");
                }
                String o = x.optString("orders_t", "");
                if (!o.isEmpty()) s.append(s.length() > 0 ? "  |  " : "").append(o).append(" فیش");
                int n = 0;
                JSONArray open = x.optJSONArray("open");
                if (open != null) for (int k = 0; k < open.length(); k++) {
                    JSONObject t = open.optJSONObject(k);
                    if (t != null && t.optBoolean("indoor") && t.optInt("minutes") <= 360) n++;
                }
                s.append(s.length() > 0 ? "  |  " : "").append(n == 0 ? "میز بازی نیست" : LiveWidget.fa(String.valueOf(n)) + " میز باز");
                sub.setText(s.toString()); sub.setTextColor(col);
            }
        }
        // آخرین هشدار
        LinearLayout la = (LinearLayout) findViewById(R.id.last_alert);
        la.removeAllViews();
        JSONArray a;
        try { a = new JSONArray(p.getString("log", "[]")); } catch (Exception e) { a = new JSONArray(); }
        if (a.length() == 0) {
            TextView t = line("هنوز هشداری نیامده.", R.color.c_hint, false); t.setPadding(dp(16), dp(14), dp(16), dp(14)); la.addView(t);
        } else la.addView(alertRow(a.optJSONObject(a.length() - 1)));
        ((TextView) findViewById(R.id.row_log_t)).setText("همه‌ی هشدارها" + (a.length() > 0 ? " (" + LiveWidget.fa(String.valueOf(a.length())) + ")" : ""));
    }

    static class CustomFont extends android.text.style.MetricAffectingSpan {
        final Typeface tf;
        CustomFont(Typeface t) { tf = t; }
        @Override public void updateDrawState(android.text.TextPaint tp) { tp.setTypeface(tf); }
        @Override public void updateMeasureState(android.text.TextPaint tp) { tp.setTypeface(tf); }
    }

    void refreshAll(final boolean loud) {
        if (loud) Toast.makeText(this, "در حال به‌روزرسانی…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                LiveWidget.refreshNow(getApplicationContext());
                runOnUiThread(new Runnable() { public void run() { fillHome(); fillStatus(); fillLog(); if (page == R.id.pg_look) preview(); } });
            }
        }).start();
    }

    void fillStatus() {
        LinearLayout box = (LinearLayout) findViewById(R.id.status_list);
        box.removeAllViews();
        List<Sites.Site> l = Sites.all(this);
        if (l.isEmpty()) { box.addView(line("هنوز مجموعه‌ای اضافه نشده.", R.color.c_hint, false)); return; }
        for (int i = 0; i < l.size(); i++) {
            boolean off = p.getBoolean("offline_" + i, false);
            String at = p.getString("last_at_" + i, "");
            String t = (off ? "✕ " : "✓ ") + l.get(i).name + " — " + (off ? "قطع" : "وصل") + (at.isEmpty() ? "" : " · " + LiveWidget.fa(at));
            box.addView(line(t, off ? "#B03A2E" : "#0F5C4D", true));
        }
    }

    TextView line(String s, String color, boolean b) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.parseColor(color)); t.setTextSize(14);
        t.setTypeface(b ? bold : reg);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    TextView line(String s, int colorRes, boolean b) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(col(colorRes)); t.setTextSize(14); t.setTypeface(b ? bold : reg);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    // ───── مجموعه‌ها ─────
    void openForm(int idx) {
        editing = idx;
        List<Sites.Site> l = Sites.all(this);
        boolean ed = idx >= 0 && idx < l.size();
        ((TextView) findViewById(R.id.form_title)).setText(ed ? "ویرایش «" + l.get(idx).name + "»" : "مجموعه‌ی جدید");
        ((EditText) findViewById(R.id.name)).setText(ed ? l.get(idx).name : "");
        ((EditText) findViewById(R.id.url)).setText(ed ? l.get(idx).url : "");
        findViewById(R.id.result).setVisibility(View.GONE);
        findViewById(R.id.form).setVisibility(View.VISIBLE);
        findViewById(R.id.add_site).setVisibility(View.GONE);
    }

    void closeForm() {
        editing = -1;
        findViewById(R.id.form).setVisibility(View.GONE);
        findViewById(R.id.add_site).setVisibility(View.VISIBLE);
    }

    void saveSite() {
        final TextView res = (TextView) findViewById(R.id.result);
        final String n = ((EditText) findViewById(R.id.name)).getText().toString().trim();
        final String u = ((EditText) findViewById(R.id.url)).getText().toString().trim();
        if (u.isEmpty()) { show(res, "لینک را وارد کنید.", -1); return; }
        show(res, "در حال امتحان لینک…", 0);
        new Thread(new Runnable() {
            public void run() {
                String msg; int st; String nm = n;
                try {
                    JSONObject x = new JSONObject(LiveWidget.fetch(Sites.jsonUrl(u)));
                    if (nm.isEmpty()) nm = x.optString("site_name", "مجموعه");
                    List<Sites.Site> l = Sites.all(MainActivity.this);
                    if (editing >= 0 && editing < l.size()) { l.get(editing).name = nm; l.get(editing).url = u; }
                    else l.add(new Sites.Site(nm, u));
                    Sites.save(MainActivity.this, l);
                    msg = "✓ «" + nm + "» ذخیره شد و وصل است."; st = 1;
                } catch (Exception ex) { msg = "✕ وصل نشد؛ ذخیره نشد. لینک یا اینترنت را چک کنید."; st = -1; }
                final String m = msg; final int s2 = st;
                runOnUiThread(new Runnable() { public void run() {
                    if (s2 > 0) { closeForm(); fillSites(); Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show(); refreshAll(false); }
                    else show(res, m, s2);
                } });
            }
        }).start();
    }

    void fillSites() {
        LinearLayout box = (LinearLayout) findViewById(R.id.sites_list);
        box.removeAllViews();
        final List<Sites.Site> l = Sites.all(this);
        for (int i = 0; i < l.size(); i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.field); row.setPadding(dp(14), dp(10), dp(10), dp(10));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = dp(8); row.setLayoutParams(rl);
            LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
            TextView nm = new TextView(this); nm.setText(l.get(i).name); nm.setTextColor(col(R.color.c_head)); nm.setTextSize(15); nm.setTypeface(bold);
            TextView ul = new TextView(this); ul.setText(l.get(i).url); ul.setTextColor(col(R.color.c_hint)); ul.setTextSize(11);
            ul.setTextDirection(View.TEXT_DIRECTION_LTR); ul.setSingleLine(true); ul.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            col.addView(nm); col.addView(ul);
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(chip("ویرایش", R.color.c_acc, new View.OnClickListener() { public void onClick(View v) { openForm(idx); } }));
            row.addView(chip("حذف", "#B03A2E", new View.OnClickListener() { public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this).setMessage("«" + l.get(idx).name + "» حذف شود؟")
                        .setPositiveButton("حذف", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                            List<Sites.Site> x = Sites.all(MainActivity.this); if (idx < x.size()) x.remove(idx);
                            Sites.save(MainActivity.this, x); closeForm(); fillSites(); fillStatus(); LiveWidget.render(getApplicationContext());
                        } }).setNegativeButton("انصراف", null).show();
            } }));
            box.addView(row);
        }
        findViewById(R.id.add_site).setVisibility(findViewById(R.id.form).getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    TextView chip(String text, String color, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextColor("#B03A2E".equals(color) ? col(R.color.c_badfg) : col(R.color.c_acc)); t.setTextSize(13); t.setTypeface(bold);
        t.setBackgroundResource(R.drawable.btn_small); t.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(6)); t.setLayoutParams(lp); t.setOnClickListener(l);
        return t;
    }

    // ───── هشدارها ─────
    void tint(Switch sw) {
        int[][] st = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(st, new int[]{Color.parseColor("#E3A12F"), Color.parseColor("#FFFFFF")}));
        sw.setTrackTintList(new ColorStateList(st, new int[]{Color.parseColor("#F2C46B"), col(R.color.c_swoff)}));
    }

    void twoLine(Switch sw, String title, String sub) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(title);
        int s0 = sb.length(); sb.append("\n").append(sub);
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.8f), s0, sb.length(), 0);
        sb.setSpan(new android.text.style.ForegroundColorSpan(col(R.color.c_mut)), s0, sb.length(), 0);
        sw.setText(sb);
        sw.setLineSpacing(dp(2), 1f);
    }

    void alertSwitch(final int id, final String key, String title, String sub) {
        Switch sw = (Switch) findViewById(id);
        tint(sw); twoLine(sw, title, sub);
        sw.setChecked(p.getBoolean(key, true));
        if (id == R.id.al_tables) findViewById(R.id.min_row).setAlpha(sw.isChecked() ? 1f : 0.4f);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) {
                p.edit().putBoolean(key, v).apply();
                if (id == R.id.al_tables) findViewById(R.id.min_row).setAlpha(v ? 1f : 0.4f);
                if (v && !notifOk()) askNotif();
            }
        });
    }

    static int parseNum(String s) {
        StringBuilder en = new StringBuilder();
        for (char c : s.trim().toCharArray()) {
            if (c >= '۰' && c <= '۹') en.append((char) ('0' + (c - '۰')));
            else if (c >= '0' && c <= '9') en.append(c);
        }
        try { return Integer.parseInt(en.toString()); } catch (Exception e) { return -1; }
    }

    boolean notifOk() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    void askNotif() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        else try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (Exception ignored) {}
    }

    void permState() {
        TextView w = (TextView) findViewById(R.id.perm_warn);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean battery = pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());
        if (!notifOk()) { w.setText("⚠ اجازه‌ی نوتیفیکیشن خاموش است؛ برای روشن کردن اینجا را بزنید."); w.setVisibility(View.VISIBLE); }
        else if (!battery) { w.setText("⚠ برای اینکه هشدارها سر وقت بیایند، «رادرس» را از بهینه‌سازی باتری خارج کنید (اینجا را بزنید ← All apps ← رادرس ← Don't optimize / Unrestricted)."); w.setVisibility(View.VISIBLE); }
        else w.setVisibility(View.GONE);
        if (reg != null) w.setTypeface(bold);
    }

    void fillLog() {
        LinearLayout box = (LinearLayout) findViewById(R.id.log_list);
        box.removeAllViews();
        JSONArray a;
        try { a = new JSONArray(p.getString("log", "[]")); } catch (Exception e) { a = new JSONArray(); }
        if (a.length() == 0) { TextView t = line("هنوز هشداری نیامده.", R.color.c_hint, false); t.setPadding(dp(16), dp(14), dp(16), dp(14)); box.addView(t); return; }
        for (int i = a.length() - 1; i >= 0 && i >= a.length() - 20; i--) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (box.getChildCount() > 0) {
                View d = new View(this); d.setBackgroundColor(col(R.color.c_line));
                box.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
            box.addView(alertRow(o));
        }
    }

    /** یک ردیف هشدار: نقطه‌ی رنگی، عنوان و متن، ساعت سمت چپ */
    View alertRow(JSONObject o) {
        String k = o.optString("k", ""), title = o.optString("title", "");
        int dot;
        if ("summary".equals(k) || title.contains("خلاصه")) dot = col(R.color.c_acc);
        else if ("tbl".equals(k) || title.contains("میز بیش")) dot = Color.parseColor("#B03A2E");
        else if (k.isEmpty() && title.contains("آزمایشی")) dot = col(R.color.c_hint);
        else dot = Color.parseColor("#E3A12F");
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(dp(16), dp(12), dp(16), dp(12));
        View d = new View(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL); g.setColor(dot); d.setBackground(g);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(10), dp(10)); dl.topMargin = dp(8);
        r.addView(d, dl);
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
        int dash = title.indexOf(" — ");
        String head = dash > 0 && Sites.all(this).size() <= 1 ? title.substring(dash + 3) : title;
        TextView h = line(head, R.color.c_text, true); h.setTextSize(15); h.setPadding(0, 0, 0, dp(2));
        TextView x = line(o.optString("text"), R.color.c_mut, false); x.setTextSize(13); x.setPadding(0, 0, 0, 0); x.setLineSpacing(dp(2), 1f);
        col.addView(h); col.addView(x);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        cl.setMarginStart(dp(12)); r.addView(col, cl);
        String t = o.optString("t", "");
        String today = new java.text.SimpleDateFormat("MM/dd", java.util.Locale.US).format(new java.util.Date());
        String tm = t.startsWith(today + " ") ? t.substring(t.indexOf(' ') + 1) : t;
        TextView tv = line(LiveWidget.fa(tm), R.color.c_mut, false); tv.setTextSize(12); tv.setPadding(0, dp(2), 0, 0);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.setMarginStart(dp(10)); r.addView(tv, tl);
        return r;
    }

    // ───── به‌روزرسانی از خود داشبورد (‎/static/app/version.json و radres.apk) ─────
    int myCode() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; } catch (Exception e) { return 0; }
    }
    String myName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }
    String base() {
        List<Sites.Site> l = Sites.all(this);
        String u = l.isEmpty() ? "" : l.get(0).url.trim();
        try {
            URL x = new URL(u);
            return x.getProtocol() + "://" + x.getHost() + (x.getPort() > 0 ? ":" + x.getPort() : "");
        } catch (Exception e) { return ""; }
    }

    String newApk = null, newName = ""; int newCode = 0;

    void setupUpdate() {
        ((TextView) findViewById(R.id.ver)).setText("رادرس · نسخه‌ی " + LiveWidget.fa(myName()));
        ((TextView) findViewById(R.id.ver2)).setText("نسخه‌ی نصب‌شده: " + LiveWidget.fa(myName()));
        final TextView btn = (TextView) findViewById(R.id.upd);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (newApk != null) download(); else check(true);
            }
        });
        check(false);
    }

    void check(final boolean loud) {
        final TextView r = (TextView) findViewById(R.id.updres);
        final TextView btn = (TextView) findViewById(R.id.upd);
        final String b = base();
        if (b.isEmpty()) { if (loud) show(r, "اول یک مجموعه در «مجموعه‌ها» اضافه کنید.", -1); return; }
        if (loud) show(r, "در حال بررسی…", 0);
        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONObject j = new JSONObject(LiveWidget.fetch(b + "/static/app/version.json"));
                    final int code = j.optInt("versionCode", 0);
                    final String name = j.optString("versionName", "");
                    final String notes = j.optString("notes", "");
                    final String apk = b + "/static/app/" + j.optString("file", "radres.apk");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (code > myCode()) {
                                newApk = apk; newCode = code; newName = name; fillMenu();
                                btn.setBackgroundResource(R.drawable.btn_primary);
                                btn.setTextColor(Color.parseColor("#2A1C00"));
                                btn.setText("دانلود و نصب نسخه‌ی " + LiveWidget.fa(name));
                                show(r, "نسخه‌ی جدید آماده است" + (notes.isEmpty() ? "" : ":\n" + notes), 1);
                            } else if (loud) {
                                show(r, "✓ برنامه به‌روز است.", 1);
                            }
                        }
                    });
                } catch (Exception e) {
                    if (loud) runOnUiThread(new Runnable() { public void run() { show(r, "✕ بررسی انجام نشد. اینترنت یا لینک را چک کنید.", -1); } });
                }
            }
        }).start();
    }

    void download() {
        final TextView r = (TextView) findViewById(R.id.updres);
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            show(r, "یک بار اجازه‌ی «نصب برنامه‌های ناشناس» را برای رادرس روشن کنید و برگردید، بعد دوباره دکمه را بزنید.", 0);
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        show(r, "در حال دانلود…", 0);
        final String apk = newApk;
        new Thread(new Runnable() {
            public void run() {
                try {
                    File f = new File(getCacheDir(), UpdateProvider.NAME);
                    HttpURLConnection c = (HttpURLConnection) new URL(apk).openConnection();
                    c.setConnectTimeout(15000); c.setReadTimeout(30000);
                    int total = c.getContentLength();
                    InputStream in = c.getInputStream();
                    FileOutputStream out = new FileOutputStream(f);
                    byte[] buf = new byte[16384]; int n; long got = 0; long lastUi = 0;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n); got += n;
                        if (total > 0 && System.currentTimeMillis() - lastUi > 300) {
                            lastUi = System.currentTimeMillis();
                            final int pct = (int) (got * 100 / total);
                            runOnUiThread(new Runnable() { public void run() { show(r, "در حال دانلود… " + LiveWidget.fa(String.valueOf(pct)) + "٪", 0); } });
                        }
                    }
                    out.close(); in.close(); c.disconnect();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            show(r, "دانلود شد. «نصب» یا «به‌روزرسانی» را بزنید.", 1);
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setDataAndType(UpdateProvider.uri(), "application/vnd.android.package-archive");
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() { public void run() { show(r, "✕ دانلود انجام نشد. دوباره امتحان کنید.", -1); } });
                }
            }
        }).start();
    }



    View stepRow(int n, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView num = new TextView(this);
        num.setText(LiveWidget.fa(String.valueOf(n)));
        num.setTag("b");
        num.setGravity(Gravity.CENTER);
        num.setTextColor(col(R.color.c_acc));
        num.setTextSize(14);
        num.setBackgroundResource(R.drawable.num);
        row.addView(num, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(col(R.color.c_text));
        t.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(dp(12));
        row.addView(t, lp);
        return row;
    }

    void show(TextView res, String m, int st) {
        res.setVisibility(View.VISIBLE);
        res.setText(m);
        if (bold != null) res.setTypeface(bold);
        if (st > 0) { res.setBackgroundResource(R.drawable.chip_ok); res.setTextColor(col(R.color.c_acc)); }
        else if (st < 0) { res.setBackgroundResource(R.drawable.chip_bad); res.setTextColor(col(R.color.c_badfg)); }
        else { res.setBackgroundResource(R.drawable.field); res.setTextColor(col(R.color.c_text2)); }
    }

    // ───── ظاهر ویجت ─────
    static final String SAMPLE = "۲۵۵٬۱۵۰٬۴۰۷";

    Typeface fontTf(String key, boolean b) {
        if ("system".equals(key)) return b ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        Typeface t = Fonts.get(this, key, b);
        return t == null ? Typeface.DEFAULT : t;
    }

    void fillFonts() {
        String cur = Fonts.current(this);
        String nm = "";
        for (String[] f : Fonts.LIST) if (f[0].equals(cur)) nm = f[1];
        TextView c = (TextView) findViewById(R.id.font_cur);
        c.setText(nm + "   " + "۲۵۵٬۱۵۰");
        c.setTypeface(fontTf(cur, false));
        LinearLayout box = (LinearLayout) findViewById(R.id.font_list);
        box.removeAllViews();
        for (int i = 0; i < Fonts.LIST.length; i++) {
            final String[] f = Fonts.LIST[i];
            boolean on = f[0].equals(cur);
            View d = new View(this); d.setBackgroundColor(col(R.color.c_line));
            box.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(16), dp(11), dp(16), dp(11));
            r.setBackgroundResource(on ? R.drawable.rowon : R.drawable.rowsel); r.setClickable(true);
            TextView rd = new TextView(this);
            rd.setText(on ? "◉" : "○"); rd.setTextSize(18); rd.setTextColor(on ? Color.parseColor("#E3A12F") : col(R.color.c_chev));
            r.addView(rd);
            TextView n = new TextView(this);
            n.setText(f[1]); n.setTextSize(15); n.setTextColor(col(R.color.c_text)); n.setTypeface(fontTf(f[0], on));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            nl.setMarginStart(dp(12)); r.addView(n, nl);
            TextView sm = new TextView(this);
            sm.setText(SAMPLE); sm.setTextSize(19); sm.setTextColor(col(R.color.c_head)); sm.setTypeface(fontTf(f[0], false));
            r.addView(sm);
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    p.edit().putString("font", f[0]).apply();
                    fillFonts(); setFontList(false); fillMenu(); preview(); renderWidget();
                }
            });
            box.addView(r);
        }
    }

    void setFontList(boolean open) {
        findViewById(R.id.font_list).setVisibility(open ? View.VISIBLE : View.GONE);
        findViewById(R.id.font_box).setBackgroundResource(open ? R.drawable.dd_open : R.drawable.grp);
        TextView ar = (TextView) findViewById(R.id.font_arrow);
        android.graphics.drawable.Drawable dd = getDrawable(R.drawable.ic_down_s).mutate();
        dd.setTint(col(R.color.c_mut)); dd.setBounds(0, 0, dp(18), dp(18));
        ar.setCompoundDrawablesRelative(dd, null, null, null);
        ar.setRotation(open ? 180 : 0);
    }

    void fillSeg() {
        boolean ref = "refresh".equals(p.getString("tap", "open"));
        TextView a = (TextView) findViewById(R.id.tapOpen), b = (TextView) findViewById(R.id.tapRefresh);
        for (TextView t : new TextView[]{a, b}) {
            boolean on = (t == b) == ref;
            t.setBackgroundResource(on ? R.drawable.seg_on : 0);
            t.setTextColor(col(on ? R.color.c_head : R.color.c_mut));
            t.setTypeface(on ? bold : reg);
        }
    }

    /** پیش‌نمایش زنده: همان تصویری که روی ویجت کشیده می‌شود */
    void preview() {
        final android.widget.ImageView iv = (android.widget.ImageView) findViewById(R.id.preview);
        iv.post(new Runnable() {
            public void run() {
                int W = iv.getWidth(), H = iv.getHeight();
                if (W <= 0 || H <= 0) return;
                List<Sites.Site> l = Sites.all(MainActivity.this);
                String name = l.isEmpty() ? "هفت‌خوان" : l.get(0).name;
                JSONObject x = null;
                try { x = new JSONObject(p.getString("last_0", "")); } catch (Exception ignored) {}
                if (x == null) try {
                    x = new JSONObject("{\"today\":255150407,\"today_t\":\"۲۵۵٬۱۵۰٬۴۰۷\",\"orders_t\":\"۹۷\",\"pct\":-11,\"lw_full\":288000000,\"open\":[]}");
                } catch (Exception ignored) {}
                float d = getResources().getDisplayMetrics().density;
                String font = Fonts.current(MainActivity.this);
                boolean hidden = p.getBoolean("privacy", false);
                iv.setImageBitmap(MainCard.draw(MainActivity.this, font, name, x, false, p.getString("last_at_0", ""), hidden, W, H, d));
            }
        });
    }

    void renderWidget() {
        new Thread(new Runnable() { public void run() { LiveWidget.render(getApplicationContext()); } }).start();
    }

    void applyFont(View v) {
        if (v instanceof TextView && !(v instanceof EditText)) ((TextView) v).setTypeface("b".equals(v.getTag()) ? bold : reg);
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) applyFont(g.getChildAt(i)); }
    }

    int dp(int x) { return (int) (x * getResources().getDisplayMetrics().density + 0.5f); }
}
