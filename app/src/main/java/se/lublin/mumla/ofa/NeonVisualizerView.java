package se.lublin.mumla.ofa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class NeonVisualizerView extends View {

    private final Paint neonPaint = new Paint();
    private float mLevel = 0f;
    private float mSensitivitas = 0.7f; 

    // Parameter penghalus gerakan
    private static final float ATTACK_SPEED = 0.9f; 
    private static final float DECAY_SPEED = 0.12f; 

    public NeonVisualizerView(Context context) { super(context); init(); }
    public NeonVisualizerView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public NeonVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        neonPaint.setStyle(Paint.Style.FILL);
        neonPaint.setAntiAlias(true);
        
        // WAJIB ADA agar garis halus & tidak crash di semua HP
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        
        // PASTIKAN TIDAK ADA setShadowLayer() DI SINI
        // Agar warna pelangi tetap tajam dan tidak ngeblur
    }

    public void setAudioLevel(float level) {
        float target = Math.max(0f, Math.min(1f, level * mSensitivitas));
        
        if (target > mLevel) {
            mLevel += (target - mLevel) * ATTACK_SPEED;
        } else {
            mLevel = Math.max(0f, mLevel - DECAY_SPEED);
        }
        invalidate();
    }

    public void setSensitivitas(float faktor) { this.mSensitivitas = faktor; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int lebar = getWidth();
        int tinggi = getHeight();
        
        // 6 ZONA WARNA PELANGI
        int z1 = (int)(lebar * 0.25f); 
        int z2 = (int)(lebar * 0.40f); 
        int z3 = (int)(lebar * 0.55f); 
        int z4 = (int)(lebar * 0.70f); 
        int z5 = (int)(lebar * 0.85f); 

        int panjang = (int)(lebar * mLevel);
        float tebal = tinggi * 0.7f; 
        float yTengah = tinggi / 2f;

        // Zona 1: HIJAU
        if (panjang > 0) {
            neonPaint.setColor(Color.parseColor("#00FF00"));
            canvas.drawRect(0, yTengah - tebal/2, Math.min(panjang, z1), yTengah + tebal/2, neonPaint);
        }
        // Zona 2: CYAN
        if (panjang > z1) {
            neonPaint.setColor(Color.parseColor("#00FFFF"));
            canvas.drawRect(z1, yTengah - tebal/2, Math.min(panjang, z2), yTengah + tebal/2, neonPaint);
        }
        // Zona 3: JINGGA
        if (panjang > z2) {
            neonPaint.setColor(Color.parseColor("#FF8C00"));
            canvas.drawRect(z2, yTengah - tebal/2, Math.min(panjang, z3), yTengah + tebal/2, neonPaint);
        }
        // Zona 4: KUNING
        if (panjang > z3) {
            neonPaint.setColor(Color.parseColor("#FFFF00"));
            canvas.drawRect(z3, yTengah - tebal/2, Math.min(panjang, z4), yTengah + tebal/2, neonPaint);
        }
        // Zona 5: ORANYE TUA
        if (panjang > z4) {
            neonPaint.setColor(Color.parseColor("#FF4500"));
            canvas.drawRect(z4, yTengah - tebal/2, Math.min(panjang, z5), yTengah + tebal/2, neonPaint);
        }
        // Zona 6: MERAH
        if (panjang > z5) {
            neonPaint.setColor(Color.parseColor("#FF0000"));
            canvas.drawRect(z5, yTengah - tebal/2, panjang, yTengah + tebal/2, neonPaint);
        }
    }
}