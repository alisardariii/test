package ir.haftkhan.widget;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

/**
 * نمایش زنده‌ی تمام‌صفحه (گوشی و تلویزیون اندروید).
 * محتوا همان صفحه‌ی سایت است (گزارش زنده یا آشپزخانه) تا با هر تغییر سایت، اینجا هم عوض شود.
 * چرخش ۰/۹۰/۱۸۰/۲۷۰ درجه را خود برنامه انجام می‌دهد (برای تلویزیون‌هایی که عمودی نصب شده‌اند).
 */
public class LiveActivity extends Activity {
    static final String DEFAULT_BASE = "http://85.185.66.73:48500";

    SharedPreferences p;
    Typeface reg, bold;
    FrameLayout root, rot;
    WebView web;
    LinearLayout bar, loginBox;
    TextView msg, rotBtn, modeBtn, bootBtn, menuBtn, updBtn;
    String newApk = null, newName = "";
    final java.util.List<TextView> btns = new java.util.ArrayList<>();
    int barRows = 0;

    /** دکمه‌ها را در یک ردیف (افقی) یا دو ردیف (عمودی) می‌چیند */
    void layoutBar(boolean side) {
        int rows = side ? 2 : 1;
        if (rows == barRows) return;
        barRows = rows;
        bar.removeAllViews();
        int per = (btns.size() + rows - 1) / rows;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
            for (int i = r * per; i < Math.min(btns.size(), (r + 1) * per); i++) {
                TextView b = btns.get(i);
                if (b.getParent() != null) ((ViewGroup) b.getParent()).removeView(b);
                row.addView(b);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (r > 0) rl.topMargin = dp(8);
            bar.addView(row, rl);
        }
    }
    boolean downloading = false;
    final Handler h = new Handler(Looper.getMainLooper());
    String siteUrl;
    boolean tv, loginOnly, pageError;

    public static Intent intent(Context c, String siteUrl) {
        Intent i = new Intent(c, LiveActivity.class);
        if (siteUrl != null) i.putExtra("url", siteUrl);
        return i;
    }

    public static boolean isTv(Context c) {
        try {
            UiModeManager um = (UiModeManager) c.getSystemService(Context.UI_MODE_SERVICE);
            if (um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) return true;
        } catch (Exception ignored) {}
        PackageManager pm = c.getPackageManager();
        return pm.hasSystemFeature("android.software.leanback") || pm.hasSystemFeature("android.hardware.type.television");
    }

    /** آدرس پایه‌ی داشبورد از لینک مجموعه: http://…:port */
    static String base(String u) {
        if (u == null) return "";
        u = u.trim();
        int i = u.indexOf("/a/"); if (i < 0) i = u.indexOf("/w/");
        return i > 0 ? u.substring(0, i) : u.replaceAll("/+$", "");
    }

    /** کلید (توکن) داخل لینک مجموعه */
    static String token(String u) {
        String j = Sites.jsonUrl(u);
        int i = j.lastIndexOf('/');
        String t = i >= 0 ? j.substring(i + 1) : j;
        return t.endsWith(".json") ? t.substring(0, t.length() - 5) : t;
    }

    static boolean isAccount(String u) { return u != null && u.contains("/a/"); }

    int dp(float x) { return (int) (x * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences(LiveWidget.PREFS, 0);
        tv = isTv(this);
        loginOnly = getIntent().getBooleanExtra("login", false);
        try {
            reg = Typeface.createFromAsset(getAssets(), "fonts/samim.ttf");
            bold = Typeface.createFromAsset(getAssets(), "fonts/samim-bold.ttf");
        } catch (Exception e) { reg = Typeface.DEFAULT; bold = Typeface.DEFAULT_BOLD; }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0d1f1a"));
        rot = new FrameLayout(this);
        root.addView(rot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setBackgroundColor(Color.parseColor("#0d1f1a"));
        web.setFocusable(!tv);   // روی تلویزیون، ریموت فقط نوار پایین را کنترل کند
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return false; }
            @Override public void onPageFinished(WebView v, String url) {
                if (url != null && url.contains("/login") && isAccount(siteUrl)) {
                    // کلید این دستگاه باطل شده (مدیر خارجش کرده)
                    showLogin("این دستگاه از حساب خارج شده است؛ دوباره وارد شوید.");
                    return;
                }
                if (!pageError) msg.setVisibility(View.GONE);
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r != null && r.isForMainFrame()) offline();
            }
        });
        rot.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        msg = new TextView(this);
        msg.setTypeface(reg); msg.setTextColor(Color.parseColor("#E9EFEC")); msg.setTextSize(tv ? 22 : 16);
        msg.setGravity(Gravity.CENTER); msg.setPadding(dp(24), dp(16), dp(24), dp(16));
        msg.setBackground(round(Color.parseColor("#E6141E1B"), dp(16)));
        msg.setVisibility(View.GONE);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        rot.addView(msg, ml);

        buildBar();
        if (!tv) {
            // روی گوشی: یک دکمه‌ی کوچک گوشه برای باز کردن نوار
            menuBtn = text("⋯", 22, true);
            menuBtn.setGravity(Gravity.CENTER); menuBtn.setTextColor(Color.WHITE);
            menuBtn.setBackground(round(Color.parseColor("#80141E1B"), dp(20)));
            menuBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { toggleBar(); } });
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM | Gravity.LEFT);
            bl.setMargins(dp(14), 0, 0, dp(14));
            rot.addView(menuBtn, bl);
        }

        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int bt, int ol, int ot, int or, int ob) {
                if (r - l != or - ol || bt - t != ob - ot) applyRot();
            }
        });

        String u = getIntent().getStringExtra("url");
        if (u == null || u.isEmpty()) {
            List<Sites.Site> l = Sites.all(this);
            if (!l.isEmpty()) u = l.get(0).url;
        }
        siteUrl = u;
        if (loginOnly || siteUrl == null || siteUrl.isEmpty()) showLogin(null);
        else load();
    }

    @Override protected void onResume() { super.onResume(); immersive(); }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); }

    void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        try { web.destroy(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    // ───── بارگذاری صفحه‌ی سایت ─────
    // ───── صفحه‌ها (گزارش زنده، آشپزخانه و هر صفحه‌ای که بعداً در سایت اضافه شود) ─────
    // فهرست از خود داشبورد می‌آید (/api/app/me)، پس برای صفحه‌ی تازه لازم نیست اپ دوباره ساخته شود.
    org.json.JSONArray pages() {
        try { org.json.JSONArray a = new org.json.JSONArray(p.getString("tv_pages", "")); if (a.length() > 0) return a; } catch (Exception ignored) {}
        org.json.JSONArray a = new org.json.JSONArray();
        try { a.put(new JSONObject().put("key", "live").put("title", "گزارش زنده")); } catch (Exception ignored) {}
        return a;
    }

    int pageIdx() {
        org.json.JSONArray a = pages(); String k = p.getString("tv_page", "live");
        for (int i = 0; i < a.length(); i++) if (k.equals(a.optJSONObject(i).optString("key"))) return i;
        return 0;
    }

    String pageKey() { return pages().optJSONObject(pageIdx()).optString("key", "live"); }

    void switchPage(int d) {
        if (!isAccount(siteUrl)) { toast("جابه‌جایی بین صفحه‌ها فقط با ورود حساب کاربری در دسترس است"); return; }
        org.json.JSONArray a = pages();
        if (a.length() < 2) { toast("صفحه‌ی دیگری برای این کاربر نیست"); return; }
        int i = ((pageIdx() + d) % a.length() + a.length()) % a.length();
        JSONObject o = a.optJSONObject(i);
        p.edit().putString("tv_page", o.optString("key")).apply();
        toast(o.optString("title"));
        load();
    }

    /** فهرست صفحه‌های مجاز این کاربر را از داشبورد می‌گیرد */
    void refreshPages() {
        if (!isAccount(siteUrl)) return;
        final String b = base(siteUrl), t = token(siteUrl);
        new Thread(new Runnable() { public void run() {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(b + "/api/app/me").openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(10000);
                c.setRequestProperty("X-Radres-Token", t);
                if (c.getResponseCode() != 200) return;
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                org.json.JSONArray a = new JSONObject(bo.toString("UTF-8")).optJSONArray("pages");
                if (a != null && a.length() > 0) {
                    p.edit().putString("tv_pages", a.toString()).apply();
                    runOnUiThread(new Runnable() { public void run() { fillBar(); } });
                }
            } catch (Exception ignored) {}
        } }).start();
    }

    void load() {
        pageError = false;
        h.removeCallbacks(retry);
        if (loginBox != null) { rot.removeView(loginBox); loginBox = null; }
        web.setVisibility(View.VISIBLE);
        String b = base(siteUrl), t = token(siteUrl);
        try {
            if (isAccount(siteUrl)) {
                String body = "t=" + URLEncoder.encode(t, "UTF-8") + "&go=" + URLEncoder.encode(pageKey(), "UTF-8");
                web.postUrl(b + "/app/enter", body.getBytes("UTF-8"));
            } else {
                web.loadUrl(b + "/w/" + t + "?app=1");   // لینک قدیمی ویجت: همان صفحه‌ی گزارش زنده‌ی عمومی
            }
        } catch (Exception e) { offline(); }
        refreshPages();
        if (newApk == null && !downloading) checkUpdate(false);
        h.removeCallbacks(dailyReload);
        h.postDelayed(dailyReload, 6 * 3600 * 1000L);
        fillBar();
    }

    final Runnable retry = new Runnable() { public void run() { load(); } };
    final Runnable dailyReload = new Runnable() { public void run() { load(); } };

    void offline() {
        pageError = true;
        msg.setText("ارتباط با سیستم رستوران برقرار نیست…\nهر ۱۵ ثانیه دوباره امتحان می‌شود.");
        msg.setVisibility(View.VISIBLE); msg.bringToFront();
        if (bar != null) bar.bringToFront();
        h.removeCallbacks(retry);
        h.postDelayed(retry, 15000);
    }

    // ───── نوار پایین (ریموت: OK برای باز و بسته کردن) ─────
    void buildBar() {
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);   // یک یا دو ردیف دکمه (در حالت عمودی جا نمی‌شوند)
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(10), dp(10), dp(10), dp(10));
        bar.setBackground(round(Color.parseColor("#E00F1916"), dp(20)));
        rotBtn = barBtn("⟳  چرخش صفحه", new View.OnClickListener() { public void onClick(View v) { rotate(); } });
        barBtn("↻  تازه‌سازی", new View.OnClickListener() { public void onClick(View v) { load(); hideBarSoon(); } });
        barBtn("◐  تیره / روشن", new View.OnClickListener() { public void onClick(View v) { toggleTheme(); hideBarSoon(); } });
        modeBtn = barBtn("", new View.OnClickListener() { public void onClick(View v) {
            switchPage(1);
        } });
        if (tv) bootBtn = barBtn("", new View.OnClickListener() { public void onClick(View v) { bootToggle(); } });
        updBtn = barBtn("⭳  به‌روزرسانی", new View.OnClickListener() { public void onClick(View v) { updateTap(); } });
        barBtn("⏻  خروج", new View.OnClickListener() { public void onClick(View v) { finish(); } });
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = dp(tv ? 28 : 18);
        rot.addView(bar, bl);
        layoutBar(false);
        bar.setVisibility(View.GONE);
        fillBar();
    }

    TextView barBtn(String t, View.OnClickListener l) {
        TextView b = text(t, tv ? 17 : 14, false);
        b.setTextColor(Color.parseColor("#E9EFEC"));
        b.setPadding(dp(tv ? 16 : 12), dp(tv ? 11 : 9), dp(tv ? 16 : 12), dp(tv ? 11 : 9));
        StateListDrawable sd = new StateListDrawable();
        sd.addState(new int[]{android.R.attr.state_focused}, round(Color.parseColor("#F2C46B"), dp(14)));
        sd.addState(new int[]{android.R.attr.state_pressed}, round(Color.parseColor("#F2C46B"), dp(14)));
        sd.addState(new int[]{}, round(Color.parseColor("#1AFFFFFF"), dp(14)));
        b.setBackground(sd);
        b.setFocusable(true); b.setClickable(true);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean f) {
            ((TextView) v).setTextColor(Color.parseColor(f ? "#14302A" : "#E9EFEC")); hideBarSoon();
        } });
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(5), 0, dp(5), 0);
        b.setLayoutParams(lp);
        btns.add(b);
        return b;
    }

    void fillBar() {
        if (modeBtn != null) {
            org.json.JSONArray a = pages();
            JSONObject nx = a.optJSONObject((pageIdx() + 1) % Math.max(1, a.length()));
            modeBtn.setText(a.length() > 1 && nx != null ? "⇄  " + nx.optString("title") : "⇄  صفحه‌ها");
        }
        if (rotBtn != null) rotBtn.setText("⟳  چرخش (" + LiveWidget.fa(String.valueOf(p.getInt("tv_rot", 0))) + "°)");
        if (bootBtn != null) bootBtn.setText(p.getBoolean("tv_boot", true) ? "✓  باز شدن خودکار" : "○  باز شدن خودکار");
    }

    void toggleBar() { if (bar.getVisibility() == View.VISIBLE) hideBar(); else showBar(); }

    void showBar() {
        if (loginBox != null) return;
        fillBar();
        bar.setVisibility(View.VISIBLE); bar.setAlpha(0f); bar.animate().alpha(1f).setDuration(180).start();
        bar.bringToFront();
        if (tv && !btns.isEmpty()) btns.get(0).requestFocus();
        hideBarSoon();
    }

    void hideBar() { bar.animate().alpha(0f).setDuration(160).withEndAction(new Runnable() { public void run() { bar.setVisibility(View.GONE); } }).start(); }

    final Runnable autoHide = new Runnable() { public void run() { if (bar.getVisibility() == View.VISIBLE) hideBar(); } };
    void hideBarSoon() { h.removeCallbacks(autoHide); h.postDelayed(autoHide, 10000); }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (loginBox != null) return super.onKeyDown(code, e);
        boolean open = bar.getVisibility() == View.VISIBLE;
        if (code == KeyEvent.KEYCODE_BACK) {
            if (open) { hideBar(); return true; }
            if (tv) { showBar(); toast("برای بیرون رفتن «خروج» را بزنید"); return true; }
            return super.onKeyDown(code, e);
        }
        // چپ و راست ریموت (وقتی نوار بسته است): صفحه‌ی قبلی / بعدی
        if (!open && (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_LEFT)) {
            switchPage(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1); return true;
        }
        if (!open && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_MENU
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
            if (tv || code == KeyEvent.KEYCODE_MENU) { showBar(); return true; }
        }
        if (open) hideBarSoon();
        return super.onKeyDown(code, e);
    }

    // ───── به‌روزرسانی از همان نوار (روی تلویزیون صفحه‌ی تنظیمات برنامه در دسترس نیست) ─────
    int myCode() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; } catch (Exception e) { return 0; }
    }

    /** بی‌صدا بررسی می‌کند نسخه‌ی تازه روی داشبورد هست یا نه؛ اگر بود، روی دکمه نوشته می‌شود */
    void checkUpdate(final boolean loud) {
        final String b = base(siteUrl);
        if (b.isEmpty()) return;
        new Thread(new Runnable() { public void run() {
            try {
                JSONObject j = new JSONObject(LiveWidget.fetch(b + "/static/app/version.json"));
                final int code = j.optInt("versionCode", 0);
                final String nm = j.optString("versionName", "");
                final String apk = b + "/static/app/" + j.optString("file", "radres.apk");
                runOnUiThread(new Runnable() { public void run() {
                    if (code > myCode()) {
                        newApk = apk; newName = nm;
                        updBtn.setText("⭳  نسخه‌ی " + LiveWidget.fa(nm).replace('.', '٫') + " آماده است");
                        if (loud) download();
                    } else {
                        newApk = null; updBtn.setText("⭳  به‌روزرسانی");
                        if (loud) toast("برنامه به‌روز است");
                    }
                } });
            } catch (Exception e) {
                if (loud) runOnUiThread(new Runnable() { public void run() { toast("بررسی انجام نشد؛ ارتباط با داشبورد را چک کنید"); } });
            }
        } }).start();
    }

    void updateTap() {
        if (downloading) return;
        if (newApk == null) { toast("در حال بررسی…"); checkUpdate(true); } else download();
    }

    void download() {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            toast("یک بار اجازه‌ی «نصب برنامه‌های ناشناس» را برای رادرس روشن کنید و برگردید.");
            try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {}
            return;
        }
        downloading = true;
        final String apk = newApk;
        updBtn.setText("⭳  در حال دانلود…");
        new Thread(new Runnable() { public void run() {
            try {
                java.io.File f = new java.io.File(getCacheDir(), UpdateProvider.NAME);
                HttpURLConnection c = (HttpURLConnection) new URL(apk).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(30000);
                final int total = c.getContentLength();
                java.io.InputStream in = c.getInputStream();
                java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                byte[] buf = new byte[16384]; int n; long got = 0, lastUi = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n); got += n;
                    if (total > 0 && System.currentTimeMillis() - lastUi > 300) {
                        lastUi = System.currentTimeMillis();
                        final int pct = (int) (got * 100 / total);
                        runOnUiThread(new Runnable() { public void run() { updBtn.setText("⭳  دانلود " + LiveWidget.fa(String.valueOf(pct)) + "٪"); } });
                    }
                }
                out.close(); in.close(); c.disconnect();
                runOnUiThread(new Runnable() { public void run() {
                    downloading = false; updBtn.setText("⭳  نصب نسخه‌ی تازه");
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(UpdateProvider.uri(), "application/vnd.android.package-archive");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(i); } catch (Exception e) { toast("نصب‌کننده‌ی تلویزیون باز نشد"); }
                } });
            } catch (Exception e) {
                runOnUiThread(new Runnable() { public void run() { downloading = false; updBtn.setText("⭳  به‌روزرسانی"); toast("دانلود انجام نشد؛ دوباره امتحان کنید"); } });
            }
        } }).start();
    }

    /** تم صفحه (همان تنظیم «ظاهر» سایت) بین روشن و تیره عوض می‌شود */
    void toggleTheme() {
        String js = "(function(){var K='hk_glass',s={};try{s=JSON.parse(localStorage.getItem(K)||'{}')||{};}catch(e){}"
                + "var d=document.documentElement.getAttribute('data-gt')==='dark';s.t=d?'light':'dark';"
                + "try{localStorage.setItem(K,JSON.stringify(s));}catch(e){}location.reload();})()";
        try { web.evaluateJavascript(js, null); } catch (Exception ignored) {}
    }

    // ───── چرخش ─────
    void rotate() {
        int r = (p.getInt("tv_rot", 0) + 90) % 360;
        p.edit().putInt("tv_rot", r).apply();
        applyRot(); fillBar(); hideBarSoon();
    }

    void applyRot() {
        int W = root.getWidth(), H = root.getHeight();
        if (W == 0 || H == 0) return;
        int r = p.getInt("tv_rot", 0);
        boolean side = r == 90 || r == 270;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) rot.getLayoutParams();
        lp.width = side ? H : W; lp.height = side ? W : H;
        lp.gravity = Gravity.TOP | Gravity.LEFT;   // برنامه راست‌به‌چپ است؛ بدون این، کادر چرخیده از سمت راست شروع می‌شد و از صفحه بیرون می‌زد
        // حاشیه‌ی امن تلویزیون (overscan): لبه‌های تصویر در بعضی تلویزیون‌ها بیرون از قاب می‌افتد
        int mx = 0, my = 0;   // فاصله از لبه‌ها را خود صفحه‌ی سایت می‌دهد (حالت اپ)، نه کوچک کردن کل تصویر
        FrameLayout.LayoutParams wl = (FrameLayout.LayoutParams) web.getLayoutParams();
        if (side) wl.setMargins(my, mx, my, mx); else wl.setMargins(mx, my, mx, my);
        web.setLayoutParams(wl);
        FrameLayout.LayoutParams bl = (FrameLayout.LayoutParams) bar.getLayoutParams();
        bl.bottomMargin = (side ? mx : my) + dp(tv ? 16 : 18);
        bar.setLayoutParams(bl);
        layoutBar(side);
        rot.setLayoutParams(lp);
        rot.setRotation(r);
        rot.setTranslationX(side ? (W - H) / 2f : 0);
        rot.setTranslationY(side ? (H - W) / 2f : 0);
        // مرورگر داخلی بعضی تلویزیون‌ها وقتی چرخانده شود فقط تکه‌ای از صفحه را می‌کشد؛
        // در حالت چرخیده، صفحه به‌صورت نرم‌افزاری و کامل کشیده می‌شود
        web.setLayerType(r == 0 ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_SOFTWARE, null);
        web.requestLayout(); web.invalidate();
    }

    // ───── باز شدن خودکار بعد از روشن شدن تلویزیون ─────
    void bootToggle() {
        boolean on = !p.getBoolean("tv_boot", true);
        p.edit().putBoolean("tv_boot", on).apply();
        fillBar();
        if (on && Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(this)) {
            // از اندروید ۱۰ به بعد، باز شدن خودکار به اجازه‌ی «نمایش روی برنامه‌های دیگر» نیاز دارد
            toast("برای باز شدن خودکار، در صفحه‌ی بعد به «رادرس» اجازه بدهید.");
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (Exception ex) { toast("این تلویزیون صفحه‌ی این اجازه را ندارد؛ شاید بدون آن هم باز شود."); }
        } else toast(on ? "بعد از روشن شدن تلویزیون، گزارش خودش باز می‌شود" : "باز شدن خودکار خاموش شد");
    }

    // ───── ورود با نام کاربری و رمز داشبورد ─────
    void showLogin(String note) {
        h.removeCallbacks(retry);
        if (bar != null) bar.setVisibility(View.GONE);
        msg.setVisibility(View.GONE);
        web.setVisibility(View.INVISIBLE);
        if (loginBox != null) rot.removeView(loginBox);
        loginBox = new LinearLayout(this);
        loginBox.setOrientation(LinearLayout.VERTICAL);
        loginBox.setPadding(dp(24), dp(22), dp(24), dp(22));
        loginBox.setBackground(round(Color.parseColor("#FBFCFB"), dp(24)));
        TextView t = text("ورود به رادرس", tv ? 28 : 22, true); t.setTextColor(Color.parseColor("#0B3F36"));
        loginBox.addView(t);
        TextView sub = text(note != null ? note : "با همان نام کاربری و رمزی که در داشبورد دارید وارد شوید.", tv ? 18 : 14, false);
        sub.setTextColor(Color.parseColor(note != null ? "#B03A2E" : "#5B6A64")); sub.setPadding(0, dp(4), 0, dp(14));
        loginBox.addView(sub);
        final EditText srv = field("آدرس داشبورد", false);
        srv.setText(p.getString("last_base", DEFAULT_BASE)); srv.setTextDirection(View.TEXT_DIRECTION_LTR);
        srv.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText un = field("نام کاربری", false);
        un.setText(p.getString("last_user", "")); un.setTextDirection(View.TEXT_DIRECTION_LTR);
        final EditText pw = field("رمز عبور", true);
        final TextView err = text("", tv ? 17 : 13.5f, false); err.setTextColor(Color.parseColor("#B03A2E")); err.setVisibility(View.GONE);
        loginBox.addView(err);
        final TextView go = text("ورود", tv ? 22 : 17, true);
        go.setGravity(Gravity.CENTER); go.setTextColor(Color.WHITE);
        StateListDrawable sd = new StateListDrawable();
        sd.addState(new int[]{android.R.attr.state_focused}, round(Color.parseColor("#0B3F36"), dp(16)));
        sd.addState(new int[]{}, round(Color.parseColor("#0F5C4D"), dp(16)));
        go.setBackground(sd); go.setFocusable(true); go.setClickable(true);
        go.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.topMargin = dp(10);
        loginBox.addView(go, gl);
        go.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            final String b = srv.getText().toString().trim().replaceAll("/+$", ""), u = un.getText().toString().trim(), pass = pw.getText().toString();
            if (b.isEmpty() || u.isEmpty() || pass.isEmpty()) { err.setText("همه‌ی خانه‌ها را پر کنید."); err.setVisibility(View.VISIBLE); return; }
            go.setText("در حال ورود…"); go.setEnabled(false); err.setVisibility(View.GONE);
            new Thread(new Runnable() { public void run() {
                String e2 = null; JSONObject x = null;
                try { x = login(b, u, pass, tv); if (!x.optBoolean("ok")) e2 = x.optString("error", "ورود انجام نشد"); }
                catch (Exception ex) { e2 = "به داشبورد وصل نشد؛ آدرس یا اینترنت را چک کنید."; }
                final String fe = e2; final JSONObject fx = x;
                runOnUiThread(new Runnable() { public void run() {
                    go.setText("ورود"); go.setEnabled(true);
                    if (fe != null) { err.setText(fe); err.setVisibility(View.VISIBLE); return; }
                    onLoggedIn(b, u, fx);
                } });
            } }).start();
        } });
        int w = Math.min(dp(tv ? 520 : 380), getResources().getDisplayMetrics().widthPixels - dp(32));
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        rot.addView(loginBox, ll);
        loginBox.bringToFront();
        (un.getText().length() > 0 ? pw : un).requestFocus();
    }

    EditText field(String hint, boolean pass) {
        TextView l = text(hint, tv ? 16 : 13, false); l.setTextColor(Color.parseColor("#5B6A64")); l.setPadding(dp(4), dp(6), 0, dp(4));
        loginBox.addView(l);
        EditText e = new EditText(this);
        e.setTypeface(reg); e.setTextSize(tv ? 20 : 16); e.setSingleLine(true);
        e.setTextColor(Color.parseColor("#1D2B27"));
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        StateListDrawable sd = new StateListDrawable();
        GradientDrawable f = round(Color.WHITE, dp(14)); f.setStroke(dp(2), Color.parseColor("#0F5C4D"));
        GradientDrawable n = round(Color.WHITE, dp(14)); n.setStroke(dp(1), Color.parseColor("#1F0F5C4D"));
        sd.addState(new int[]{android.R.attr.state_focused}, f); sd.addState(new int[]{}, n);
        e.setBackground(sd);
        if (pass) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        loginBox.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    static JSONObject login(String base, String user, String pass, boolean tv) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/api/app/login").openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(15000);
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        String dev = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        JSONObject body = new JSONObject().put("username", user).put("password", pass).put("device", dev).put("kind", tv ? "tv" : "phone");
        OutputStream os = c.getOutputStream(); os.write(body.toString().getBytes("UTF-8")); os.close();
        int code = c.getResponseCode();
        java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        if (in != null) { while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); in.close(); }
        String s = bo.toString("UTF-8");
        if (s.trim().isEmpty() || !s.trim().startsWith("{")) throw new Exception("bad");
        return new JSONObject(s);
    }

    void onLoggedIn(String b, String user, JSONObject x) {
        p.edit().putString("last_base", b).putString("last_user", user).remove("tv_pages").apply();
        String url = b + "/a/" + x.optString("token");
        String name = x.optString("site_name", "مجموعه");
        List<Sites.Site> l = Sites.all(this);
        int at = -1;
        String old = siteUrl == null ? "" : siteUrl;
        for (int i = 0; i < l.size(); i++) if (l.get(i).url.equals(old) && isAccount(old)) at = i;   // ورود دوباره‌ی همان مجموعه
        if (at >= 0) l.get(at).url = url; else l.add(new Sites.Site(name, url));
        Sites.save(this, l);
        p.edit().putBoolean("sites_changed", true).apply();
        siteUrl = url;
        toast("خوش آمدید، " + x.optString("name", user));
        new Thread(new Runnable() { public void run() { try { LiveWidget.render(getApplicationContext()); } catch (Exception ignored) {} } }).start();
        if (loginOnly) { finish(); return; }
        if (loginBox != null) { rot.removeView(loginBox); loginBox = null; }
        load();
        if (tv) h.postDelayed(new Runnable() { public void run() { showBar(); } }, 1500);
    }

    // ───── کمکی ─────
    TextView text(String s, float size, boolean b) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTypeface(b ? bold : reg);
        return t;
    }

    static GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(r); return g;
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
