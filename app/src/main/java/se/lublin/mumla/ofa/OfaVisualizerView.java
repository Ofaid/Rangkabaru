package se.lublin.mumla.ofa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class OfaVisualizerView extends View {

    private final Paint ofaPaint = new Paint();
    private float mLevel = 0f;
    private float mSensitivitas = 1.8f;
    private static final float LENYAP_CEPAT = 0.35f; // sama cepatnya

    public OfaVisualizerView(Context context) {
        super(context);
        init();
    }

    public OfaVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OfaVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ofaPaint.setStyle(Paint.Style.FILL);
        ofaPaint.setAntiAlias(true); // jernih, tanpa blur
    }

    public void setAudioLevel(float level) {
        float target = Math.max(0f, Math.min(1f, level * mSensitivitas));
        
        if (target >= mLevel) {
            mLevel = target;
        } else {
            mLevel = Math.max(0f, mLevel - LENYAP_CEPAT);
        }
        invalidate();
    }

    public void setSensitivitas(float faktor) {
        mSensitivitas = faktor;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int lebar = getWidth();
        int tinggi = getHeight();
        
        // Warna beda dari Mic — Biru → Ungu → Putih
        int batasBiru = (int)(lebar * 0.45f);
        int batasUngu = (int)(lebar * 0.70f);

        int panjang = (int)(lebar * mLevel);
        float tebal = tinggi * 0.6f;
        float yTengah = tinggi / 2f;

        // Biru — lembut
        if (panjang > 0) {
            ofaPaint.setColor(Color.parseColor("#2196F3"));
            if (panjang <= batasBiru) {
                canvas.drawRect(0, yTengah - tebal/2, panjang, yTengah + tebal/2, ofaPaint);
            } else {
                canvas.drawRect(0, yTengah - tebal/2, batasBiru, yTengah + tebal/2, ofaPaint);
            }
        }

        // Ungu — sedang
        if (panjang > batasBiru) {
            ofaPaint.setColor(Color.parseColor("#9C27B0"));
            int akhir = Math.min(panjang, batasUngu);
            canvas.drawRect(batasBiru, yTengah - tebal/2, akhir, yTengah + tebal/2, ofaPaint);
        }

        // Putih — kuat
        if (panjang > batasUngu) {
            ofaPaint.setColor(Color.parseColor("#FFFFFF"));
            canvas.drawRect(batasUngu, yTengah - tebal/2, panjang, yTengah + tebal/2, ofaPaint);
        }
    }
}
