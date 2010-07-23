
package org.codejive.rws.events;

import java.util.EventObject;
import org.codejive.rws.RwsSession;

/**
 *
 * @author tako
 */
public class SessionEvent extends EventObject {

    public SessionEvent(RwsSession session) {
        super(session);
    }
}
