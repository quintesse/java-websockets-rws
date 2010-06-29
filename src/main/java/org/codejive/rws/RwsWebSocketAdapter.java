package org.codejive.rws;

import java.io.IOException;

/**
 *
 * @author tako
 */
public interface RwsWebSocketAdapter {

    public void onConnect();

    public void onMessage(String msg);

    public void onDisconnect();

    public boolean isConnected();

    public void disconnect();

    public void sendMessage(String msg) throws IOException;
}
