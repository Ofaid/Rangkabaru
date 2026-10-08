/*
 * Copyright (C) 2015 Andrew Comminos <andrew@comminos.com>
 * modify by Ofaid & Rangkabaru ST12
 */
 
package se.lublin.humla;

import se.lublin.humla.model.Server;
import se.lublin.humla.util.HumlaDisconnectedException;
import se.lublin.humla.util.HumlaException;
import se.lublin.humla.util.IHumlaObserver;

public interface IHumlaService {
    void registerObserver(IHumlaObserver observer);
    void unregisterObserver(IHumlaObserver observer);
    boolean isConnected();
    void disconnect();
    HumlaService.ConnectionState getConnectionState();
    HumlaException getConnectionError();
    boolean isReconnecting();
    void cancelReconnect();
    Server getTargetServer();
    IHumlaSession HumlaSession() throws HumlaDisconnectedException;

    // ========== TAMBAHAN FITUR KUSTOM OFAID/ST12 ==========
    
    // Untuk Visualizer (mengembalikan clone buffer audio aman)
    short[] getRecordingBuffer(); 

    // Untuk kontrol PTT langsung dari UI Activity
    void setTalkingState(boolean talking);

    // Untuk fitur Avatar/Texture OFAID
    void setUserTexture(int session, byte[] data);

    // Untuk fitur Status Kustom RAPI
    void setStatusDenganId(String idOFA, String statusTeks);
    
    // ======================================================
}