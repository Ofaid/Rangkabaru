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
    
    // SENSITIVITAS DEFAULT DITURUNKAN (dari 1.8f ke 0.7f)
    // Agar suara pelan tidak langsung membuat bar penuh/lempeng
    private float mSensitivitas = 0.7f; 

    // PARAMETER PENGHALUS GERAKAN (REAL-TIME FEEL)
    // Attack: Seberapa cepat naik saat ada suara (0.9 = responsif tapi tidak kaget)
    private static final float ATTACK_SPEED = 0.9f; 
    // Decay: Seberapa cepat turun saat hening (0.12 = turun perlahan & elegan)
    private static final float DECAY_SPEED = 0.12f; 

    public NeonVisualizerView(Context context) {
        super(context);
        init();
    }

    public NeonVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NeonVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        neonPaint.setStyle(Paint.Style.FILL);
        neonPaint.setAntiAlias(true);
        
        // Efek Glow/Pendaran Cahaya Neon
        neonPaint.setShadowLayer(10f, 0, 0, Color.WHITE);
        // Wajib pakai Software Layer agar shadow terlihat di semua HP Android
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /**
     * Dipanggil oleh ChannelListFragment setiap ~33ms
     */
    public void setAudioLevel(float level) {
        // 1. Terapkan Sensitivitas dulu
        float target = Math.max(0f, Math.min(1f, level * mSensitivitas));
        
        // 2. Logika Attack & Decay yang Diperhalus
        if (target > mLevel) {
            // NAIK: Gunakan interpolasi linear agar tidak "loncat" instan
            mLevel += (target - mLevel) * ATTACK_SPEED;
        } else {
            // TURUN: Kurangi secara konstan agar gerakannya natural seperti pegas
            mLevel = Math.max(0f, mLevel - DECAY_SPEED);
        }
        
        invalidate(); // Perintah gambar ulang
    }

    public void setSensitivitas(float faktor) {
        this.mSensitivitas = faktor;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int lebar = getWidth();
        int tinggi = getHeight();
        
        // BATAS WARNA 4 GRADASI
        int batasHijau = (int)(lebar * 0.35f);  // 0% - 35%
        int batasJingga = (int)(lebar * 0.55f); // 35% - 55%
        int batasKuning = (int)(lebar * 0.75f); // 55% - 75%
        // 75% - 100% adalah Merah

        int panjang = (int)(lebar * mLevel);
        float tebal = tinggi * 0.6f;
        float yTengah = tinggi / 2f;

        // ZONA 1: HIJAU (Aman / Pelan)
        if (panjang > 0) {
            neonPaint.setColor(Color.parseColor("#00FF00")); // Hijau Neon
            int akhir = Math.min(panjang, batasHijau);
            canvas.drawRect(0, yTengah - tebal/2, akhir, yTengah + tebal/2, neonPaint);
        }

        // ZONA 2: JINGGA (Sedang / Waspada)
        if (panjang > batasHijau) {
            neonPaint.setColor(Color.parseColor("#FF8C00")); // Jingga/DarkOrange Neon
            int awal = batasHijau;
            int akhir = Math.min(panjang, batasJingga);
            canvas.drawRect(awal, yTengah - tebal/2, akhir, yTengah + tebal/2, neonPaint);
        }

        // ZONA 3: KUNING (Keras / Perhatian)
        if (panjang > batasJingga) {
            neonPaint.setColor(Color.parseColor("#FFFF00")); // Kuning Neon
            int awal = batasJingga;
            int akhir = Math.min(panjang, batasKuning);
            canvas.drawRect(awal, yTengah - tebal/2, akhir, yTengah + tebal/2, neonPaint);
        }

        // ZONA 4: MERAH (Maksimal / Clipping Warning)
        if (panjang > batasKuning) {
            neonPaint.setColor(Color.parseColor("#FF0000")); // Merah Neon
            int awal = batasKuning;
            canvas.drawRect(awal, yTengah - tebal/2, panjang, yTengah + tebal/2, neonPaint);
        }
    }
}