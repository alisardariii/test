package ir.haftkhan.widget;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.json.JSONObject;

/** کاشی منوی کشویی بالای گوشی: فروش امشب؛ با یک ضربه تازه می‌شود (در حالت مخفی، اول ۳۰ ثانیه نشان می‌دهد) */
public class QsTile extends TileService {

    @Override public void onStartListening() { paint(false); }
    @Override public void onTileAdded() { paint(false); }

    @Override public void onClick() {
        final Context app = getApplicationContext();
        if (LiveWidget.hidden(app)) { LiveWidget.reveal(app); LiveWidget.render(app); }
        paint(true);
        new Thread(new Runnable() { public void run() {
            LiveWidget.refreshNow(app);
            LiveWidget.tile(app);
        } }).start();
    }

    void paint(boolean busy) {
        Tile t = getQsTile();
        if (t == null) return;
        SharedPreferences p = getSharedPreferences(LiveWidget.PREFS, 0);
        String amount = "—", sub = "برای تنظیم، اپ را باز کنید";
        boolean off = p.getBoolean("offline_0", false);
        String last = p.getString("last_0", null);
        if (last != null) {
            try {
                JSONObject x = new JSONObject(last);
                amount = LiveWidget.hidden(this) ? LiveWidget.DOTS : x.optString("today_t", "—");
                String at = p.getString("last_at_0", "");
                sub = busy ? "در حال به‌روزرسانی…" : (off ? "قطع — " : "تومان · ") + LiveWidget.fa(at);
            } catch (Exception ignored) {}
        }
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_notif));
        if (Build.VERSION.SDK_INT >= 29) {
            t.setLabel(amount);
            t.setSubtitle(sub);
        } else {
            t.setLabel(amount + (last != null && !off ? " تومان" : ""));
        }
        t.setContentDescription("فروش امشب " + amount);
        t.setState(off || last == null ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        t.updateTile();
    }
}
