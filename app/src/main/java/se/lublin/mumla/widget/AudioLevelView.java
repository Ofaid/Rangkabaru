package se.lublin.mumla.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class AudioLevelView extends View {
    private static final float ATTACK = 0.6f;
    private static final float GREEN_MAX = 0.7f;
    private static final float RELEASE = 0.12f;
    private static final float YELLOW_MAX = 0.9f;
    
    private ValueAnimator mAnimator;
    private float mDisplayedLevel;
    private final Paint mFillPaint;
    private final RectF mRect;
    private float mTargetLevel;
    private final Paint mTrackPaint;
    
    private static final int COLOR_GREEN = Color.parseColor("#4CAF50");
    private static final int COLOR_YELLOW = Color.parseColor("#FFC107");
    private static final int COLOR_RED = Color.parseColor("#F44336");
    private static final int COLOR_TRACK = Color.parseColor("#332B2B2B");

    public AudioLevelView(Context context) {
        this(context, null);
    }

    public AudioLevelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AudioLevelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mRect = new RectF();
        this.mTargetLevel = 0.0f;
        this.mDisplayedLevel = 0.0f;
        
        this.mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.mTrackPaint.setColor(COLOR_TRACK);
        this.mTrackPaint.setStyle(Paint.Style.FILL);
        
        this.mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.mFillPaint.setStyle(Paint.Style.FILL);
        this.mFillPaint.setColor(COLOR_GREEN);
        
        startAnimationLoop();
    }

    public void setLevel(float level) {
        if (level < 0.0f) level = 0.0f;
        if (level > 1.0f) level = 1.0f;
        this.mTargetLevel = level;
    }

    public void reset() {
        this.mTargetLevel = 0.0f;
        this.mDisplayedLevel = 0.0f;
        invalidate();
    }

    private void startAnimationLoop() {
        this.mAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        this.mAnimator.setDuration(16L); // ~60fps
        this.mAnimator.setInterpolator(new DecelerateInterpolator());
        this.mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        
        // Menggunakan anonymous class standar agar kompatibel dengan semua versi Gradle
        this.mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float rate = mTargetLevel > mDisplayedLevel ? ATTACK : RELEASE;
                mDisplayedLevel += (mTargetLevel - mDisplayedLevel) * rate;
                
                if (Math.abs(mDisplayedLevel - mTargetLevel) < 0.002f) {
                    mDisplayedLevel = mTargetLevel;
                }
                invalidate();
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (this.mAnimator != null && !this.mAnimator.isStarted()) {
            this.mAnimator.start();
        }
    }

    // PERBAIKAN TOTAL: Method lifecycle Android yang BENAR adalah "FromWindow" bukan "ToWindow"
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow(); 
        if (this.mAnimator != null) {
            this.mAnimator.cancel();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float radius = height / 2.0f;
        this.mRect.set(0.0f, 0.0f, width, height);
        canvas.drawRoundRect(this.mRect, radius, radius, this.mTrackPaint);
        
        if (this.mDisplayedLevel <= 0.001f) return;

        this.mFillPaint.setColor(colorForLevel(this.mDisplayedLevel));
        float fillWidth = width * this.mDisplayedLevel;
        
        // Gambar bar level
        this.mRect.set(0.0f, 0.0f, Math.max(fillWidth, height), height);
        canvas.drawRoundRect(this.mRect, radius, radius, this.mFillPaint);
        
        // Fix visual glitch jika bar masih sangat pendek
        if (fillWidth < height) {
            this.mRect.set(fillWidth, 0.0f, width, height);
            canvas.drawRect(this.mRect, this.mTrackPaint);
            this.mRect.set(width - (2.0f * radius), 0.0f, width, height);
            canvas.drawRoundRect(this.mRect, radius, radius, this.mTrackPaint);
        }
    }

    private static int colorForLevel(float level) {
        if (level <= GREEN_MAX) return COLOR_GREEN;
        if (level <= YELLOW_MAX) return COLOR_YELLOW;
        return COLOR_RED;
    }
}