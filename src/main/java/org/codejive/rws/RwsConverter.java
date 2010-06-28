
package org.codejive.rws;

import java.io.PrintWriter;

/**
 *
 * @author tako
 */
public interface RwsConverter<T> {
    
    Object toJSON(T value) throws RwsException;

    T fromJSON(Object value, Class<T> targetType) throws RwsException;

    void generateTypeScript(Class<T> type, PrintWriter out) throws RwsException;
}
