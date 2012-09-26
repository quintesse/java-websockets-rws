
package org.codejive.rws;

import java.io.PrintWriter;

/**
 *
 * @author tako
 */
public interface RwsConverter<T> {
    
    Object toJSON(RwsObject obj, T value) throws RwsException;

    T fromJSON(RwsObject obj, Object value) throws RwsException;

    void generateTypeScript(RwsObject obj, PrintWriter out) throws RwsException;
}
