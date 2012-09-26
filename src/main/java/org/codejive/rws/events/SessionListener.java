
package org.codejive.rws.events;

import java.util.EventListener;

/**
 *
 * @author tako
 */
public interface SessionListener extends EventListener {

    void connect(SessionEvent event);

    void disconnect(SessionEvent event);

    void change(SessionEvent event);
}
