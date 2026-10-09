// ✅ GAIN DINAIIKAN MENJADI 8.0x + HARD CAP (Tanpa Soft-Clipping)
    private static final float MIC_INPUT_GAIN = 8.0f; 

    // ==================================================
    // ✅ FUNGSI KHUSUS MIC DENGAN HARD-CAP & BLOCK MAPPING
    // ==================================================
    private void kirimLevelKeVisualMicOnly(short[] frame, int frameSize) {
        if (frame == null || frameSize <= 0 || mContext == null) return;

        double sum = 0;
        for (int i = 0; i < frameSize; i++) {
            sum += frame[i] * frame[i];
        }
        double rms = Math.sqrt(sum / frameSize);
        
        // 1. TERAPKAN GAIN TINGGI + HARD CAP LANGSUNG DI 1.0
        // Tidak pakai soft-clipping biar sinyal nggak "dipotong pelan-pelan"
        float rawLevel = (float) ((rms * MIC_INPUT_GAIN) / 32768.0f);
        float processedLevel = Math.min(rawLevel, 1.0f);

        // 2. LOW-PASS FILTER (Tetap dipertahankan biar transisi blok nggak glitchy)
        mFilteredMicLevel = (LPF_ALPHA * processedLevel) + ((1f - LPF_ALPHA) * mFilteredMicLevel);

        // 3. DISCRETE BLOCK MAPPING (Logika tetap sama, cuma sinyalnya sekarang lebih kuat)
        float discreteLevel;
        if (mFilteredMicLevel < THRESHOLD_GREEN_YELLOW) {
            // BLOK HIJAU
            discreteLevel = mFilteredMicLevel; 
        } else if (mFilteredMicLevel < THRESHOLD_YELLOW_RED) {
            // BLOK KUNING (Dipaksa ke 0.6 biar nyala penuh di tengah)
            discreteLevel = 0.6f; 
        } else {
            // BLOK MERAH (Dipaksa mentok 1.0)
            discreteLevel = 1.0f; 
        }

        Intent kirimNeon = new Intent("ofaid.ahmad.ptt.LEVEL_SUARA");
        kirimNeon.putExtra("level", discreteLevel);
        mContext.sendBroadcast(kirimNeon);
    }