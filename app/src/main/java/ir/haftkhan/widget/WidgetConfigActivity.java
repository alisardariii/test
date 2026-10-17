package ir.haftkhan.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** موقع گذاشتن ویجت: انتخاب مجموعه‌ای که ویجت نشان می‌دهد */
public class WidgetConfigActivity extends Activity {
    int wid = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        Bundle ex = getIntent().getExtras();
        if (ex != null) wid = ex.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (wid == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        List<Sites.Site> l = Sites.all(this);
        if (l.size() <= 1) { done(0); return; }

        Typeface reg, bold;
        try { reg = Typeface.createFromAsset(getAssets(), "fonts/samim.ttf"); bold = Typeface.createFromAsset(getAssets(), "fonts/samim-bold.ttf"); }
        catch (Exception e) { reg = Typeface.DEFAULT; bold = Typeface.DEFAULT_BOLD; }
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setBackgroundColor(Color.parseColor("#F7F3EA"));
        box.setPadding((int) (20 * d), (int) (40 * d), (int) (20 * d), (int) (20 * d));
        TextView t = new TextView(this);
        t.setText("این ویجت کدام مجموعه را نشان دهد؟"); t.setTypeface(bold); t.setTextSize(18);
        t.setTextColor(Color.parseColor("#0B3F36")); t.setPadding(0, 0, 0, (int) (12 * d));
        box.addView(t);
        for (int i = 0; i < l.size(); i++) {
            final int idx = i;
            TextView row = new TextView(this);
            row.setText(l.get(i).name); row.setTypeface(bold); row.setTextSize(16); row.setGravity(Gravity.CENTER);
            row.setTextColor(Color.parseColor("#0F5C4D")); row.setBackgroundResource(R.drawable.btn_ghost);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (54 * d));
            lp.topMargin = (int) (10 * d);
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { done(idx); } });
            box.addView(row, lp);
        }
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(Color.parseColor("#F7F3EA")); sv.addView(box);
        setContentView(sv);
    }

    void done(int idx) {
        Sites.setForWidget(this, wid, idx);
        LiveWidget.render(getApplicationContext());
        new Thread(new Runnable() { public void run() { LiveWidget.refreshNow(getApplicationContext()); } }).start();
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid));
        finish();
    }
}
