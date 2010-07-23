
package org.codejive.rws.events;

import java.util.EventObject;
import org.codejive.rws.RwsSession;

/**
 *
 * @author tako
 */
public class MulticastEvent extends EventObject {

    private String group;

    public MulticastEvent(String group, RwsSession session) {
        super(session);
        this.group = group;
    }

    public String getGroup() {
        return group;
    }
}
