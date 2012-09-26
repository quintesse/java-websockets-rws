
package org.codejive.rws;

import java.io.IOException;

/**
 *
 * @author tako
 */
public interface RwsEventHandler {
    void handleEvent(Object args) throws IOException;
}
