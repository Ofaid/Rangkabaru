package se.lublin.mumla.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class AudioLevelView extends View {
    // PARAMETER ANIMASI YANG LEBIH NATURAL
    private static final float ATTACK_RATE = 0.45f;   // Naik lebih smooth, tidak instan
    private static final float RELEASE_RATE = 0.08f;  // Turun perlahan, tidak langsung hilang
    private static final float MIN_DISPLAY_LEVEL = 0.02f; // Jangan reset ke 0 kalau masih ada sisa suara
    
    private ValueAnimator mAnimator;
    private float mDisplayedLevel;
    private float mTargetLevel;
    
    private final Paint mFillPaint;
    private final Paint mTrackPaint;
    private final RectF mRect;
    
    // WARNA GRADASI RADIO AMATIR
    private static final int COLOR_GREEN = Color.parseColor("#4CAF50");
    private static final int COLOR_YELLOW = Color.parseColor("#FFC107");
    private static final int COLOR_RED = Color.parseColor("#F44336");
    private static final int COLOR_TRACK = Color.parseColor("#2A2A2A");

    public AudioLevelView(Context context) { this(context, null); }
    public AudioLevelView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public AudioLevelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mRect = new RectF();
        
        mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTrackPaint.setColor(COLOR_TRACK);
        mTrackPaint.setStyle(Paint.Style.FILL);
        
        mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillPaint.setStyle(Paint.Style.FILL);
        
        startAnimationLoop();
    }

    public void setLevel(float level) {
        if (level < 0.0f) level = 0.0f;
        if (level > 1.0f) level = 1.0f;
        this.mTargetLevel = level;
    }

    public void reset() {
        this.mTargetLevel = 0.0f;
    }

    private void startAnimationLoop() {
        // Gunakan LinearInterpolator agar gerakan konsisten, tidak melambat di akhir
        mAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        mAnimator.setDuration(16L); 
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        
        mAnimator.addUpdateListener(animation -> {
            // LOGIKA INTERPOLASI MANUAL AGAR TIDAK KEDIP
            float rate = mTargetLevel > mDisplayedLevel ? ATTACK_RATE : RELEASE_RATE;
            
            // Rumus easing eksponensial agar transisi sangat halus
            mDisplayedLevel += (mTargetLevel - mDisplayedLevel) * rate;
            
            // Cegah floating point error saat level sangat kecil
            if (Math.abs(mDisplayedLevel - mTargetLevel) < 0.001f) {
                mDisplayedLevel = mTargetLevel;
            }
            
            // Jangan gambar kalau level benar-benar 0 untuk hemat GPU
            if (mDisplayedLevel > 0.001f || mTargetLevel > 0.001f) {
                invalidate();
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAnimator != null && !mAnimator.isStarted()) mAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow(); 
        if (mAnimator != null) mAnimator.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float radius = height / 2.0f;
        mRect.set(0.0f, 0.0f, width, height);
        canvas.drawRoundRect(mRect, radius, radius, mTrackPaint);
        
        if (mDisplayedLevel <= MIN_DISPLAY_LEVEL) return;

        // Tentukan warna berdasarkan level real-time
        mFillPaint.setColor(colorForLevel(mDisplayedLevel));
        
        float fillWidth = width * mDisplayedLevel;
        // Pastikan lebar minimal setinggi radius agar ujung bulat tetap terlihat
        float drawWidth = Math.max(fillWidth, height); 
        
        mRect.set(0.0f, 0.0f, drawWidth, height);
        canvas.drawRoundRect(mRect, radius, radius, mFillPaint);
    }

    private int colorForLevel(float level) {
        // Threshold warna disesuaikan agar transisi gradasi lebih natural
        if (level <= 0.65f) return COLOR_GREEN;
        if (level <= 0.85f) return COLOR_YELLOW;
        return COLOR_RED;
    }
}
