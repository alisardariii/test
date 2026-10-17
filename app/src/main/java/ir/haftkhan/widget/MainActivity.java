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
        final EditText url = (EditText) findViewById(R.id.url);
        final TextView res = (TextView) findViewById(R.id.result);
        url.setText(p.getString("url", ""));

        String[] steps = {
                "لینک را بالا بگذارید و «ذخیره و امتحان» را بزنید تا «وصل شد» بیاید.",
                "روی صفحه‌ی اصلی، جای خالی را نگه دارید ← ابزارک‌ها (Widgets).",
                "زیر «رادرس»، ویجت «گزارش زنده» را پیدا کنید و روی صفحه بکشید.",
                "ویجت هر ۱۵ دقیقه خودش به‌روز می‌شود؛ برای به‌روزرسانی فوری ⟳ را بزنید."};
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
                p.edit().putString("url", url.getText().toString().trim()).apply();
                show(res, "در حال امتحان…", 0);
                final String u = LiveWidget.jsonUrl(MainActivity.this);
                new Thread(new Runnable() {
                    public void run() {
                        String msg; int st;
                        try {
                            JSONObject x = new JSONObject(LiveWidget.fetch(u));
                            msg = "✓ وصل شد — فروش امروز " + x.optString("today_t", "") + " تومان";
                            st = 1;
                        } catch (Exception ex) {
                            msg = "✕ وصل نشد. لینک یا اینترنت را چک کنید.";
                            st = -1;
                        }
                        final String m = msg; final int s = st;
                        runOnUiThread(new Runnable() { public void run() { show(res, m, s); } });
                        LiveWidget.refreshNow(getApplicationContext());
                    }
                }).start();
            }
        });

        RadioGroup rg = (RadioGroup) findViewById(R.id.tap);
        ((RadioButton) findViewById("refresh".equals(p.getString("tap", "open")) ? R.id.tapRefresh : R.id.tapOpen)).setChecked(true);
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup g, int id) {
                p.edit().putString("tap", id == R.id.tapRefresh ? "refresh" : "open").apply();
                new Thread(new Runnable() { public void run() { LiveWidget.render(getApplicationContext(), null); } }).start();
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
        String u = getSharedPreferences(LiveWidget.PREFS, 0).getString("url", "").trim();
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
        if (b.isEmpty()) { if (loud) show(r, "اول لینک داشبورد را در بالا ذخیره کنید.", -1); return; }
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
