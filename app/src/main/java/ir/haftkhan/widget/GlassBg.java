package ir.haftkhan.widget;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** زمینه‌ی صفحه: گرادیانت ملایم با سه هاله‌ی کم‌رنگ (هلویی، نعنایی، یاسی) */
public class GlassBg extends Drawable {
    final boolean dark;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    GlassBg(boolean dark) { this.dark = dark; }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        int[] base = dark ? new int[]{0xFF161B1A, 0xFF121716, 0xFF14171C} : new int[]{0xFFF7F3EC, 0xFFEEF3F0, 0xFFEEF0F6};
        p.setShader(new LinearGradient(0, 0, w * 0.1f, h, base, new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(b, p);
        blob(c, w * 0.95f, 0, Math.max(w, h) * 0.55f, dark ? 0x1AF2C46B : 0xFFFBE7C6);
        blob(c, 0, h * 0.40f, Math.max(w, h) * 0.55f, dark ? 0x1A5ABE96 : 0xFFD3EEE3);
        blob(c, w, h, Math.max(w, h) * 0.6f, dark ? 0x1A788CE6 : 0xFFDCE3F7);
    }

    void blob(Canvas c, float x, float y, float r, int color) {
        p.setShader(new RadialGradient(x, y, r, new int[]{color, color & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter f) {}
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
