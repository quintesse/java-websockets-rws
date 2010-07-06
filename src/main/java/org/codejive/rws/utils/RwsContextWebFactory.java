/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.codejive.rws.utils;

import javax.servlet.ServletContext;
import org.codejive.rws.RwsContext;

/**
 *
 * @author tako
 */
public class RwsContextWebFactory implements RwsContextFactory {
    private ServletContext servletContext;

    private static final String ATTR_CONTEXT = "__rwscontext__";

    private RwsContextWebFactory(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public static RwsContextWebFactory getInstance(ServletContext servletContext) {
        return new RwsContextWebFactory(servletContext);
    }

    public void setContext(RwsContext rwsContext) {
        servletContext.setAttribute(ATTR_CONTEXT, rwsContext);
    }

    @Override
    public RwsContext getContext() {
        return (RwsContext) servletContext.getAttribute(ATTR_CONTEXT);
    }

}
