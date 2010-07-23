
package org.codejive.rws.events;

import java.util.EventListener;

/**
 *
 * @author tako
 */
public interface MulticastListener extends EventListener {

    void join(MulticastEvent event);

    void leave(MulticastEvent event);
}
