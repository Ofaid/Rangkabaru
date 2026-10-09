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
        
        // Hitung level rata-rata (sama seperti sebelumnya)
        int total = 0;
        for (byte b : mData) {
            total += Math.abs(b);
        }
        float nilai = (total / (float)mData.length) / 128f;
        if (nilai > 1f) nilai = 1f;
        
        float panjangBatang = lebarTotal * nilai;
        if (panjangBatang < 2f) panjangBatang = 2f;

        // === LOGIKA BLOK TEGAS (PENGHAPUS GRADASI) ===
        int batasHijau = (int)(lebarTotal * 0.45f);
        int batasKuning = (int)(lebarTotal * 0.70f);

        // 1. GAMBAR BLOK HIJAU (Dasar)
        mPaint.setColor(WARNA_BAWAH); // #00FF00
        canvas.drawRect(0, 0, Math.min(panjangBatang, batasHijau), tinggiTotal, mPaint);

        // 2. GAMBAR BLOK KUNING (Hanya jika level tembus 45%)
        if (panjangBatang > batasHijau) {
            mPaint.setColor(WARNA_TENGAH); // #FFFF00
            int akhirKuning = Math.min((int)panjangBatang, batasKuning);
            canvas.drawRect(batasHijau, 0, akhirKuning, tinggiTotal, mPaint);
        }

        // 3. GAMBAR BLOK MERAH (Hanya jika level tembus 70%)
        if (panjangBatang > batasKuning) {
            mPaint.setColor(WARNA_ATAS); // #FF0000
            canvas.drawRect(batasKuning, 0, (int)panjangBatang, tinggiTotal, mPaint);
        }
    }