package ir.haftkhan.widget;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.Manifest;
import java.util.List;

import org.json.JSONObject;

import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    Typeface reg, bold;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window win = getWindow();
        win.setStatusBarColor(Color.parseColor("#0B3F36"));
        setContentView(R.layout.activity_main);
        try {
            reg = Typeface.createFromAsset(getAssets(), "fonts/samim.ttf");
            bold = Typeface.createFromAsset(getAssets(), "fonts/samim-bold.ttf");
        } catch (Exception e) { reg = Typeface.DEFAULT; bold = Typeface.DEFAULT_BOLD; }

        final SharedPreferences p = getSharedPreferences(LiveWidget.PREFS, 0);
        final EditText name = (EditText) findViewById(R.id.name);
        final EditText url = (EditText) findViewById(R.id.url);
        final TextView res = (TextView) findViewById(R.id.result);

        String[] steps = {
                "در کادر «مجموعه‌ها» اسم و لینک هر مجموعه را بگذارید و «ذخیره و امتحان» را بزنید.",
                "روی صفحه‌ی اصلی، جای خالی را نگه دارید ← ابزارک‌ها (Widgets).",
                "زیر «رادرس»، ویجت «گزارش زنده» را روی صفحه بکشید و مجموعه را انتخاب کنید.",
                "ویجت را مربعی، عریض یا بلند کنید؛ در حالت عریض و بلند، قدیمی‌ترین میز و نمودار هم می‌آید."};
        LinearLayout box = (LinearLayout) findViewById(R.id.steps);
        for (int i = 0; i < steps.length; i++) box.addView(stepRow(i + 1, steps[i]));

        findViewById(R.id.paste).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData c = cm == null ? null : cm.getPrimaryClip();
                if (c != null && c.getItemCount() > 0) {
                    CharSequence t = c.getItemAt(0).coerceToText(MainActivity.this);
                    if (t != null) url.setText(t.toString().trim());
                }
            }
        });

        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String n = name.getText().toString().trim(), u = url.getText().toString().trim();
                if (u.isEmpty()) { show(res, "لینک را وارد کنید.", -1); return; }
                show(res, "در حال امتحان…", 0);
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
                            msg = "✓ «" + nm + "» وصل شد — فروش امروز " + x.optString("today_t", "") + " تومان"; st = 1;
                        } catch (Exception ex) {
                            msg = "✕ وصل نشد. لینک یا اینترنت را چک کنید."; st = -1;
                        }
                        final String m = msg; final int s2 = st;
                        runOnUiThread(new Runnable() { public void run() {
                            show(res, m, s2);
                            if (s2 > 0) { editing = -1; name.setText(""); url.setText(""); fillSites(); }
                        } });
                        if (st > 0) LiveWidget.refreshNow(getApplicationContext());
                    }
                }).start();
            }
        });
        fillSites();

        // هشدارها
        bindSwitch(R.id.al_tables, "al_tables");
        bindSwitch(R.id.al_watch, "al_watch");
        bindSwitch(R.id.al_taraz, "al_taraz");
        final EditText mins = (EditText) findViewById(R.id.al_min);
        mins.setText(String.valueOf(p.getInt("al_min", 60)));
        mins.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean f) {
                if (!f) { try { p.edit().putInt("al_min", Math.max(5, Integer.parseInt(mins.getText().toString().trim()))).apply(); } catch (Exception ignored) {} }
            }
        });
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);

        RadioGroup rg = (RadioGroup) findViewById(R.id.tap);
        ((RadioButton) findViewById("refresh".equals(p.getString("tap", "open")) ? R.id.tapRefresh : R.id.tapOpen)).setChecked(true);
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                p.edit().putString("tap", id == R.id.tapRefresh ? "refresh" : "open").apply();
                new Thread(new Runnable() { public void run() { LiveWidget.render(getApplicationContext()); } }).start();
            }
        });

        setupUpdate();
        applyFont(findViewById(R.id.page));
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

    int editing = -1;

    @Override protected void onPause() {
        super.onPause();
        try { getSharedPreferences(LiveWidget.PREFS, 0).edit().putInt("al_min",
                Math.max(5, Integer.parseInt(((EditText) findViewById(R.id.al_min)).getText().toString().trim()))).apply(); } catch (Exception ignored) {}
    }

    void bindSwitch(int id, final String key) {
        Switch sw = (Switch) findViewById(id);
        final SharedPreferences p = getSharedPreferences(LiveWidget.PREFS, 0);
        sw.setChecked(p.getBoolean(key, true));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) { p.edit().putBoolean(key, v).apply(); }
        });
    }

    void fillSites() {
        LinearLayout box = (LinearLayout) findViewById(R.id.sites_list);
        box.removeAllViews();
        final List<Sites.Site> l = Sites.all(this);
        if (l.isEmpty()) {
            TextView t = new TextView(this); t.setText("هنوز مجموعه‌ای اضافه نشده."); t.setTextColor(Color.parseColor("#93A099")); t.setTextSize(14);
            t.setTypeface(reg); box.addView(t);
        }
        for (int i = 0; i < l.size(); i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.field); row.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = dp(8); row.setLayoutParams(rl);
            LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
            TextView nm = new TextView(this); nm.setText(l.get(i).name); nm.setTextColor(Color.parseColor("#0B3F36")); nm.setTextSize(15); nm.setTypeface(bold);
            TextView ul = new TextView(this); ul.setText(l.get(i).url); ul.setTextColor(Color.parseColor("#93A099")); ul.setTextSize(11);
            ul.setTextDirection(View.TEXT_DIRECTION_LTR); ul.setSingleLine(true); ul.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            col.addView(nm); col.addView(ul);
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView del = new TextView(this); del.setText("حذف"); del.setTextColor(Color.parseColor("#B03A2E")); del.setTextSize(14);
            del.setTypeface(bold); del.setPadding(dp(12), dp(6), dp(4), dp(6));
            del.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                List<Sites.Site> x = Sites.all(MainActivity.this); if (idx < x.size()) x.remove(idx);
                Sites.save(MainActivity.this, x); editing = -1; fillSites(); LiveWidget.render(getApplicationContext());
            } });
            row.addView(del);
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                editing = idx;
                ((EditText) findViewById(R.id.name)).setText(l.get(idx).name);
                ((EditText) findViewById(R.id.url)).setText(l.get(idx).url);
                ((TextView) findViewById(R.id.form_title)).setText("ویرایش «" + l.get(idx).name + "»");
            } });
            box.addView(row);
        }
        ((TextView) findViewById(R.id.form_title)).setText("افزودن مجموعه");
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
        if (reg != null) applyFontOne(res);
        res.setText(m);
        res.setTypeface(bold);
        if (st > 0) { res.setBackgroundResource(R.drawable.chip_ok); res.setTextColor(Color.parseColor("#0F5C4D")); }
        else if (st < 0) { res.setBackgroundResource(R.drawable.chip_bad); res.setTextColor(Color.parseColor("#B03A2E")); }
        else { res.setBackgroundResource(R.drawable.field); res.setTextColor(Color.parseColor("#5B6A64")); }
    }

    void applyFontOne(TextView t) { t.setTypeface(bold); }

    void applyFont(View v) {
        if (v instanceof TextView && !(v instanceof EditText)) {
            ((TextView) v).setTypeface("b".equals(v.getTag()) ? bold : reg);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyFont(g.getChildAt(i));
        }
    }

    int dp(int x) { return (int) (x * getResources().getDisplayMetrics().density + 0.5f); }
}
