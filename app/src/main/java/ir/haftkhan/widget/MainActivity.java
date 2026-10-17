package ir.haftkhan.widget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(48, 96, 48, 48);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        l.setBackgroundColor(Color.parseColor("#F3EFE4"));

        TextView t = new TextView(this);
        t.setText("ویجت شب زنده هفت‌خوان");
        t.setTextSize(22);
        t.setTextColor(Color.parseColor("#0D1F1A"));
        t.setGravity(Gravity.RIGHT);
        l.addView(t);

        TextView h = new TextView(this);
        h.setText("\nلینک «داده‌ی خام» را از صفحه‌ی /widget داشبورد کپی کنید و اینجا بگذارید، بعد «ذخیره» را بزنید.\nسپس روی صفحه‌ی اصلی گوشی انگشت را نگه دارید ← ابزارک‌ها (Widgets) ← «شب زنده هفت‌خوان» را اضافه کنید.\n");
        h.setTextSize(15);
        h.setTextColor(Color.parseColor("#333333"));
        h.setGravity(Gravity.RIGHT);
        l.addView(h);

        final EditText e = new EditText(this);
        e.setHint("http://.../w/....json");
        e.setSingleLine(false);
        e.setTextDirection(View.TEXT_DIRECTION_LTR);
        e.setText(getSharedPreferences(LiveWidget.PREFS, 0).getString("url", ""));
        l.addView(e);

        Button s = new Button(this);
        s.setText("ذخیره و امتحان");
        l.addView(s);

        final TextView res = new TextView(this);
        res.setTextSize(15);
        res.setGravity(Gravity.RIGHT);
        res.setPadding(0, 24, 0, 0);
        l.addView(res);

        s.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                getSharedPreferences(LiveWidget.PREFS, 0).edit().putString("url", e.getText().toString().trim()).apply();
                res.setTextColor(Color.parseColor("#555555"));
                res.setText("در حال امتحان…");
                final String url = LiveWidget.jsonUrl(MainActivity.this);
                new Thread(new Runnable() {
                    public void run() {
                        String msg; boolean ok = false;
                        try {
                            JSONObject x = new JSONObject(LiveWidget.fetch(url));
                            msg = "وصل شد ✓\nفروش امروز: " + x.optString("today_t", "") + " تومان";
                            ok = true;
                        } catch (Exception ex) {
                            msg = "وصل نشد. لینک را چک کنید.\n" + ex.getMessage();
                        }
                        final String m = msg; final boolean okf = ok;
                        runOnUiThread(new Runnable() {
                            public void run() {
                                res.setTextColor(Color.parseColor(okf ? "#157A53" : "#B03A2E"));
                                res.setText(m);
                            }
                        });
                        LiveWidget.refreshNow(getApplicationContext());
                    }
                }).start();
                sendBroadcast(new Intent(MainActivity.this, LiveWidget.class).setAction(LiveWidget.ACTION_REFRESH));
            }
        });
        setContentView(l);
    }
}
