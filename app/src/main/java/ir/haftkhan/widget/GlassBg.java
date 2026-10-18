package ir.haftkhan.widget;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** زمینه‌ی صفحه: یک‌رنگِ ملایم با یک هاله‌ی خیلی کم‌رنگ نعنایی در گوشه‌ی بالا. wall=true: والپیپر نمونه برای پیش‌نمایش ویجت */
public class GlassBg extends Drawable {
    final boolean dark, wall;
    final float radius;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    GlassBg(boolean dark) { this(dark, false, 0); }
    GlassBg(boolean dark, boolean wall, float radius) { this.dark = dark; this.wall = wall; this.radius = radius; }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        if (wall) {
            c.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(new RectF(b), radius, radius, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            p.setShader(new LinearGradient(0, 0, w, h, 0xFF2A1F5C, 0xFF14304A, Shader.TileMode.CLAMP));
            c.drawRect(b, p);
            blob(c, w * 0.15f, h * 0.2f, Math.max(w, h) * 0.45f, 0xFFFF9F6B);
            blob(c, w * 0.85f, h * 0.3f, Math.max(w, h) * 0.5f, 0xFF6B8BFF);
            blob(c, w * 0.5f, h * 1.05f, Math.max(w, h) * 0.5f, 0xFF3FD3A0);
            c.restore();
            return;
        }
        p.setShader(new LinearGradient(0, 0, 0, h, dark ? 0xFF171C1B : 0xFFF8FAF8, dark ? 0xFF121615 : 0xFFF1F4F2, Shader.TileMode.CLAMP));
        c.drawRect(b, p);
        blob(c, w, 0, Math.max(w, h) * 0.6f, dark ? 0x167FD6B4 : 0xFFE2F2EB);
    }

    void blob(Canvas c, float x, float y, float r, int color) {
        p.setShader(new RadialGradient(x, y, r, new int[]{color, color & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter f) {}
    @Override public int getOpacity() { return wall ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE; }
}
