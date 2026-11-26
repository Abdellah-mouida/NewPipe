package us.shandian.giga.ui.common;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

public class ProgressDrawable extends Drawable {
    private static final int MARQUEE_INTERVAL = 150;

    private float mProgress;
    private int mBackgroundColor, mForegroundColor;
    private Handler mMarqueeHandler;
    private float mMarqueeProgress;
    private Path mMarqueeLine;
    private int mMarqueeSize;
    private long mMarqueeNext;

    public ProgressDrawable() {
        mMarqueeLine = null;// marquee disabled
        mMarqueeProgress = 0.0f;
        mMarqueeSize = 0;
        mMarqueeNext = 0;
    }

    public void setColors(@ColorInt int background, @ColorInt int foreground) {
        mBackgroundColor = background;
        mForegroundColor = foreground;
    }

    public void setProgress(double progress) {
        mProgress = (float) progress;
        invalidateSelf();
    }

    public void setMarquee(boolean marquee) {
        if (marquee == (mMarqueeLine != null)) {
            return;
        }
        mMarqueeLine = marquee ? new Path() : null;
        mMarqueeHandler = marquee ? new Handler(Looper.getMainLooper()) : null;
        mMarqueeSize = 0;
        mMarqueeNext = 0;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int width = getBounds().width();
        int height = getBounds().height();

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float radius = height / 2.0f;
        paint.setColor(mBackgroundColor);
        canvas.drawRoundRect(0, 0, width, height, radius, radius, paint);
        paint.setColor(mForegroundColor);

        if (mMarqueeLine != null) {
            if (mMarqueeSize < 1) setupMarquee(width, height);

            int size = mMarqueeSize;
            Paint paint2 = new Paint();
            paint2.setColor(mForegroundColor);
            paint2.setStrokeWidth(size);
            paint2.setStyle(Paint.Style.STROKE);

            size *= 2;

            if (mMarqueeProgress >= size) {
                mMarqueeProgress = 1;
            } else {
                mMarqueeProgress++;
            }

            // render marquee
            width += size * 2;
            Path marquee = new Path();
            for (int i = -size; i < width; i += size) {
                marquee.addPath(mMarqueeLine, ((float)i + mMarqueeProgress), 0);
            }
            marquee.close();

            final Path clip = new Path();
            clip.addRoundRect(0, 0, getBounds().width(), height,
                    radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            canvas.drawPath(marquee, paint2);// draw marquee
            canvas.restore();

            if (System.currentTimeMillis() >= mMarqueeNext) {
                // program next update
                mMarqueeNext = System.currentTimeMillis() + MARQUEE_INTERVAL;
                mMarqueeHandler.postDelayed(this::invalidateSelf, MARQUEE_INTERVAL);
            }
            return;
        }

        final float progressWidth = Math.max(0, Math.min(width, mProgress * width));
        if (progressWidth > 0) {
            canvas.drawRoundRect(0, 0, progressWidth, height, radius, radius, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        // Unsupported
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        // Unsupported
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    @Override
    public void onBoundsChange(Rect rect) {
        if (mMarqueeLine != null) setupMarquee(rect.width(), rect.height());
    }

    private void setupMarquee(int width, int height) {
        mMarqueeSize = (int) ((width * 10.0f) / 100.0f);// the size is 10% of the width

        mMarqueeLine.rewind();
        mMarqueeLine.moveTo(-mMarqueeSize, -mMarqueeSize);
        mMarqueeLine.lineTo(-mMarqueeSize * 4, height + mMarqueeSize);
        mMarqueeLine.close();
    }
}
