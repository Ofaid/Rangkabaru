package ofaid.ahmad.ptt.ofa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class VisualizerView extends View {

    private byte[] mData;
    private Paint mPaint;
    
    // SAMA PERSIS DENGAN NEON MIC
    private static final int WARNA_BAWAH = 0xFF00FF00;  // Hijau
    private static final int WARNA_TENGAH = 0xFFFFFF00; // Kuning
    private static final int WARNA_ATAS = 0xFFFF0000;   // Merah
    private static final int JUMLAH_BATANG = 32;
    private static final float JARAK_ANTAR = 1.5f;

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
        
        float lebarBatang = (lebarTotal - (JUMLAH_BATANG - 1) * JARAK_ANTAR) / JUMLAH_BATANG;
        int langkah = mData.length / JUMLAH_BATANG;

        for (int i = 0; i < JUMLAH_BATANG; i++) {
            // Ambil nilai rata-rata
            int mulai = i * langkah;
            int akhir = Math.min(mulai + langkah, mData.length);
            
            int total = 0;
            for (int j = mulai; j < akhir; j++) {
                total += Math.abs(mData[j]);
            }
            float nilai = total / (akhir - mulai) / 128f;
            if (nilai > 1f) nilai = 1f;
            
            // TINGGI BATANG — sama persis skala dengan mic
            float tinggiBatang = nilai * tinggiTotal;
            if (tinggiBatang < 2f) tinggiBatang = 2f;
            
            // WARNA — hijau → kuning → merah sama persis dengan Neon
            int warna;
            if (nilai < 0.5f) {
                // Hijau → Kuning
                float f = nilai / 0.5f;
                int r = (int)(0xFF * f);
                int g = 0xFF;
                int b = 0;
                warna = Color.rgb(r, g, b);
            } else {
                // Kuning → Merah
                float f = (nilai - 0.5f) / 0.5f;
                int r = 0xFF;
                int g = (int)(0xFF * (1f - f));
                int b = 0;
                warna = Color.rgb(r, g, b);
            }
            mPaint.setColor(warna);
            
            // Gambar batang — tumbuh dari bawah ke atas
            float kiri = i * (lebarBatang + JARAK_ANTAR);
            float kanan = kiri + lebarBatang;
            float atas = tinggiTotal - tinggiBatang;
            float bawah = tinggiTotal;
            
            canvas.drawRect(kiri, atas, kanan, bawah, mPaint);
        }
    }
}
