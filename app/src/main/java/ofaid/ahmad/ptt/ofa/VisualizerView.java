package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class VisualizerView extends View {

    private byte[] mData;
    private Paint mPaint;
    
    // WARNA ASLI OFAID (Hijau -> Kuning -> Merah)
    private static final int WARNA_BAWAH = 0xFF00FF00;  
    private static final int WARNA_TENGAH = 0xFFFFFF00; 
    private static final int WARNA_ATAS = 0xFFFF0000;   

    public VisualizerView(Context context) {
        super(context);
        init();
    }

    public VisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mData = null;
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
    }

    public void updateVisualizer(byte[] data) {
        mData = data;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (mData == null || mData.length == 0) return;

        int lebarTotal = getWidth();
        int tinggiTotal = getHeight();
        
        // AMBIL NILAI DARI DATA PERTAMA (KARENA SEKARANG CUMA 1 BATANG)
        // Atau rata-rata jika data lebih dari 1 elemen
        int total = 0;
        for (byte b : mData) {
            total += Math.abs(b);
        }
        float nilai = (total / (float)mData.length) / 128f;
        if (nilai > 1f) nilai = 1f;
        
        // PANJANG BATANG MENDATAR BERDASARKAN LEVEL
        float panjangBatang = lebarTotal * nilai;
        if (panjangBatang < 2f) panjangBatang = 2f; // Minimal terlihat
        
        // WARNA DINAMIS ASLI OFAID (Hijau -> Kuning -> Merah)
        int warna;
        if (nilai < 0.5f) {
            // Hijau ke Kuning
            float f = nilai / 0.5f;
            int r = (int)(0xFF * f);
            int g = 0xFF;
            int b = 0;
            warna = Color.rgb(r, g, b);
        } else {
            // Kuning ke Merah
            float f = (nilai - 0.5f) / 0.5f;
            int r = 0xFF;
            int g = (int)(0xFF * (1f - f));
            int b = 0;
            warna = Color.rgb(r, g, b);
        }
        
        mPaint.setColor(warna);
        
        // GAMBAR MENDATAR: Dari kiri (0) ke kanan (panjangBatang)
        // Posisi Y di tengah-tengah tinggi view agar rapi
        float yAtas = (tinggiTotal - tinggiTotal) / 2f; // Mulai dari atas
        float yBawah = tinggiTotal; // Sampai bawah (atau bisa diatur tebalnya)
        
        // OPSI A: PENUH TINGGI (Solid Bar)
        canvas.drawRect(0, 0, panjangBatang, tinggiTotal, mPaint);
        
        // OPSI B: GARIS TIPIS DI TENGAH (Uncomment jika mau gaya neon tipis)
        // float tebalGaris = tinggiTotal * 0.6f;
        // float yTengah = tinggiTotal / 2f;
        // canvas.drawRect(0, yTengah - tebalGaris/2, panjangBatang, yTengah + tebalGaris/2, mPaint);
    }
}