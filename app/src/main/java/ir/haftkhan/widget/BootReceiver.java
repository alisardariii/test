package ir.haftkhan.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** بعد از روشن شدن تلویزیون، گزارش زنده خودش باز شود (اگر در نوار تلویزیون خاموش نشده باشد) */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            if (!LiveActivity.isTv(c)) return;
            if (!c.getSharedPreferences(LiveWidget.PREFS, 0).getBoolean("tv_boot", true)) return;
            if (Sites.all(c).isEmpty()) return;
            Intent o = LiveActivity.intent(c, null);
            o.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            c.startActivity(o);
        } catch (Exception ignored) {}
    }
}
