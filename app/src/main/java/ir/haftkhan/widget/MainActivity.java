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

        applyFont(findViewById(R.id.page));
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
        res.setTypeface(bold);
        if (st > 0) { res.setBackgroundResource(R.drawable.chip_ok); res.setTextColor(Color.parseColor("#0F5C4D")); }
        else if (st < 0) { res.setBackgroundResource(R.drawable.chip_bad); res.setTextColor(Color.parseColor("#B03A2E")); }
        else { res.setBackgroundResource(R.drawable.field); res.setTextColor(Color.parseColor("#5B6A64")); }
    }

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
