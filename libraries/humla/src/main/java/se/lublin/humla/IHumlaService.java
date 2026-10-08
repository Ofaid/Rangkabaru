/*
 * Copyright (C) 2015 Andrew Comminos <andrew@comminos.com>
 * modify by Ofaid
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

    void setStatusDenganId(String idOFA, String statusTeks);

    void cancelReconnect();

    Server getTargetServer();

    IHumlaSession HumlaSession() throws HumlaDisconnectedException;

    short[] getRecordingBuffer();
}
