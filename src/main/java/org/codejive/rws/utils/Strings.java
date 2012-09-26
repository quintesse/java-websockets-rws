/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.codejive.rws.utils;

/**
 *
 * @author tako
 */
public class Strings {

    private Strings() {
    }

    public static String upperFirst(String value) {
        if (value != null && value.length() > 0) {
            if (value.length() > 1) {
                value = value.substring(0, 1).toUpperCase() + value.substring(1);
            } else {
                value = value.toUpperCase();
            }
        }
        return value;
    }
}
