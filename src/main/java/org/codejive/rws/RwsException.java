
package org.codejive.rws;

/**
 *
 * @author tako
 */
public class RwsException extends Exception {

    public RwsException(Throwable cause) {
        super(cause);
    }

    public RwsException(String message, Throwable cause) {
        super(message, cause);
    }

    public RwsException(String message) {
        super(message);
    }

    public RwsException() {
    }

}
