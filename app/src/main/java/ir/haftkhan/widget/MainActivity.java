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

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0B3F36"));
        setContentView(R.layout.activity_main);
        try {
            reg = Typeface.createFromAsset(getAssets(), "fonts/samim.ttf");
            bold = Typeface.createFromAsset(getAssets(), "fonts/samim-bold.ttf");
        } catch (Exception e) { reg = Typeface.DEFAULT; bold = Typeface.DEFAULT_BOLD; }
        p = getSharedPreferences(LiveWidget.PREFS, 0);

        // راهنما (اگر مجموعه‌ای هست، جمع شده)
        String[] steps = {
                "در «مجموعه‌ها» دکمه‌ی «+ افزودن مجموعه» را بزنید، اسم و لینک «داده‌ی خام» صفحه‌ی ‎/widget‎ داشبورد را بگذارید و «ذخیره» را بزنید.",
                "روی صفحه‌ی اصلی گوشی، جای خالی را نگه دارید ← ابزارک‌ها (Widgets).",
                "زیر «رادرس»، ویجت «گزارش زنده» را روی صفحه بکشید (اگر چند مجموعه دارید، مجموعه را انتخاب کنید).",
                "ویجت را مربعی، عریض یا بلند کنید؛ در حالت عریض و بلند، قدیمی‌ترین میز و نمودار هم می‌آید.",
                "در «هشدارها» موارد دلخواه را روشن کنید و «ذخیره‌ی تنظیمات» را بزنید."};
        final LinearLayout box = (LinearLayout) findViewById(R.id.steps);
        for (int i = 0; i < steps.length; i++) box.addView(stepRow(i + 1, steps[i]));
        setGuide(Sites.all(this).isEmpty());
        findViewById(R.id.guide_head).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setGuide(box.getVisibility() != View.VISIBLE); }
        });

        // وضعیت
        findViewById(R.id.refresh_now).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { refreshAll(true); }
        });

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

        // هشدارها
        styleSwitch(R.id.al_tables, "al_tables");
        styleSwitch(R.id.al_watch, "al_watch");
        styleSwitch(R.id.al_taraz, "al_taraz");
        ((EditText) findViewById(R.id.al_min)).setText(LiveWidget.fa(String.valueOf(p.getInt("al_min", 60))));
        findViewById(R.id.al_save).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { saveAlerts(); }
        });
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

        // با زدن روی ویجت
        RadioGroup rg = (RadioGroup) findViewById(R.id.tap);
        ((RadioButton) findViewById("refresh".equals(p.getString("tap", "open")) ? R.id.tapRefresh : R.id.tapOpen)).setChecked(true);
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                p.edit().putString("tap", id == R.id.tapRefresh ? "refresh" : "open").apply();
                Toast.makeText(MainActivity.this, "ذخیره شد", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() { public void run() { LiveWidget.render(getApplicationContext()); } }).start();
            }
        });

        setupUpdate();
        applyFont(findViewById(R.id.page));
        refreshAll(false);
    }

    @Override protected void onResume() { super.onResume(); permState(); fillStatus(); fillLog(); }

    // ───── راهنما ─────
    void setGuide(boolean open) {
        findViewById(R.id.steps).setVisibility(open ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.guide_arrow)).setText(open ? "▴" : "▾");
    }

    // ───── وضعیت ─────
    void refreshAll(final boolean loud) {
        if (loud) Toast.makeText(this, "در حال به‌روزرسانی…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                LiveWidget.refreshNow(getApplicationContext());
                runOnUiThread(new Runnable() { public void run() { fillStatus(); fillLog(); } });
            }
        }).start();
    }

    void fillStatus() {
        LinearLayout box = (LinearLayout) findViewById(R.id.status_list);
        box.removeAllViews();
        List<Sites.Site> l = Sites.all(this);
        if (l.isEmpty()) { box.addView(line("هنوز مجموعه‌ای اضافه نشده.", "#93A099", false)); return; }
        for (int i = 0; i < l.size(); i++) {
            boolean off = p.getBoolean("offline_" + i, false);
            String at = p.getString("last_at_" + i, "");
            String today = "";
            try { today = new JSONObject(p.getString("last_" + i, "{}")).optString("today_t", ""); } catch (Exception ignored) {}
            String t = (off ? "✕ " : "✓ ") + l.get(i).name + " — " + (off ? "قطع" : "وصل")
                    + (at.isEmpty() ? "" : " · " + LiveWidget.fa(at)) + (today.isEmpty() || off ? "" : " · فروش امروز " + today + " تومان");
            box.addView(line(t, off ? "#B03A2E" : "#0F5C4D", true));
        }
    }

    TextView line(String s, String color, boolean b) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.parseColor(color)); t.setTextSize(14); t.setTypeface(b ? bold : reg);
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
            TextView nm = new TextView(this); nm.setText(l.get(i).name); nm.setTextColor(Color.parseColor("#0B3F36")); nm.setTextSize(15); nm.setTypeface(bold);
            TextView ul = new TextView(this); ul.setText(l.get(i).url); ul.setTextColor(Color.parseColor("#93A099")); ul.setTextSize(11);
            ul.setTextDirection(View.TEXT_DIRECTION_LTR); ul.setSingleLine(true); ul.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            col.addView(nm); col.addView(ul);
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(chip("ویرایش", "#0F5C4D", new View.OnClickListener() { public void onClick(View v) { openForm(idx); } }));
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
        t.setText(text); t.setTextColor(Color.parseColor(color)); t.setTextSize(13); t.setTypeface(bold);
        t.setBackgroundResource(R.drawable.btn_small); t.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(6)); t.setLayoutParams(lp); t.setOnClickListener(l);
        return t;
    }

    // ───── هشدارها ─────
    void styleSwitch(int id, String key) {
        Switch sw = (Switch) findViewById(id);
        int[][] st = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(st, new int[]{Color.parseColor("#E3A12F"), Color.parseColor("#FFFFFF")}));
        sw.setTrackTintList(new ColorStateList(st, new int[]{Color.parseColor("#F2C46B"), Color.parseColor("#B9C2BE")}));
        sw.setChecked(p.getBoolean(key, true));
        if (id == R.id.al_tables) sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) { findViewById(R.id.min_row).setAlpha(v ? 1f : 0.4f); }
        });
        if (id == R.id.al_tables) findViewById(R.id.min_row).setAlpha(sw.isChecked() ? 1f : 0.4f);
    }

    void saveAlerts() {
        int m = 60;
        try {
            String s = ((EditText) findViewById(R.id.al_min)).getText().toString().trim();
            StringBuilder en = new StringBuilder();
            for (char c : s.toCharArray()) en.append(c >= '۰' && c <= '۹' ? (char) ('0' + (c - '۰')) : c);
            m = Math.max(5, Integer.parseInt(en.toString()));
        } catch (Exception ignored) {}
        p.edit().putBoolean("al_tables", ((Switch) findViewById(R.id.al_tables)).isChecked())
                .putBoolean("al_watch", ((Switch) findViewById(R.id.al_watch)).isChecked())
                .putBoolean("al_taraz", ((Switch) findViewById(R.id.al_taraz)).isChecked())
                .putInt("al_min", m).apply();
        ((EditText) findViewById(R.id.al_min)).setText(LiveWidget.fa(String.valueOf(m)));
        show((TextView) findViewById(R.id.al_res), "✓ تنظیمات هشدار ذخیره شد.", 1);
        if (!notifOk()) askNotif();
        refreshAll(false);
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
        if (a.length() == 0) { box.addView(line("هنوز هشداری نیامده.", "#93A099", false)); return; }
        for (int i = a.length() - 1; i >= 0 && i >= a.length() - 8; i--) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            TextView t = line(LiveWidget.fa(o.optString("t")) + " · " + o.optString("title"), "#1D2B27", true);
            TextView d = line(o.optString("text"), "#5B6A64", false); d.setTextSize(13); d.setPadding(0, 0, 0, dp(6));
            box.addView(t); box.addView(d);
        }
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

    String newApk = null; int newCode = 0;

    void setupUpdate() {
        ((TextView) findViewById(R.id.ver)).setText("نسخه‌ی فعلی: " + LiveWidget.fa(myName()));
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
        if (b.isEmpty()) { if (loud) show(r, "اول یک مجموعه در کادر «مجموعه‌ها» اضافه کنید.", -1); return; }
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
                                newApk = apk; newCode = code;
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
        num.setTextColor(Color.parseColor("#0F5C4D"));
        num.setTextSize(14);
        num.setBackgroundResource(R.drawable.num);
        row.addView(num, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.parseColor("#1D2B27"));
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
        if (st > 0) { res.setBackgroundResource(R.drawable.chip_ok); res.setTextColor(Color.parseColor("#0F5C4D")); }
        else if (st < 0) { res.setBackgroundResource(R.drawable.chip_bad); res.setTextColor(Color.parseColor("#B03A2E")); }
        else { res.setBackgroundResource(R.drawable.field); res.setTextColor(Color.parseColor("#5B6A64")); }
    }

    void applyFont(View v) {
        if (v instanceof TextView && !(v instanceof EditText)) ((TextView) v).setTypeface("b".equals(v.getTag()) ? bold : reg);
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) applyFont(g.getChildAt(i)); }
    }

    int dp(int x) { return (int) (x * getResources().getDisplayMetrics().density + 0.5f); }
}
