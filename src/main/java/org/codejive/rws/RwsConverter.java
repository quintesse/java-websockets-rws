
package org.codejive.rws;

import java.io.PrintWriter;

/**
 *
 * @author tako
 */
public interface RwsConverter<T> {
    
    Object toJSON(T value, String name) throws RwsException;

    T fromJSON(Object value, Class<T> targetType) throws RwsException;

    void generateTypeScript(String name, Class<T> type, PrintWriter out) throws RwsException;
}
