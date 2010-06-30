package org.codejive.rws;

import java.beans.EventSetDescriptor;
import java.beans.MethodDescriptor;
import java.beans.ParameterDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.codejive.rws.RwsObject.Scope;
import org.codejive.rws.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author tako
 */
public class RwsServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(RwsServlet.class);

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        try {
            RwsObject clt = new RwsObject("__this__", RwsSession.class, Scope.connection);
            RwsRegistry.register(clt);
        } catch (RwsException ex) {
            throw new ServletException("Could not initialize RwsRegistry", ex);
        }
    }
    
    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String path = request.getPathInfo();
        log.debug("Incoming request: {}", path);
        if (path == null) {
            log.debug("Return overview page");
            generateOverview(response);
        } else if ("/rws.js".equals(path)) {
            log.debug("Return main script");
            // TODO return global JS page needed for all object
        } else if (path.startsWith("/object/")) {
            String objName = path.substring(8, path.length() - 3);
            log.debug("Requesting object script for '{}'", objName);
            RwsObject rwsObject = RwsRegistry.getObject(objName);
            if (rwsObject != null) {
                generateObjectScript(response, rwsObject);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown RWS object '" + objName + "'");
            }
        }
    } 

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        processRequest(request, response);
    } 

    /** 
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "RWS object script generator";
    }// </editor-fold>

    private void generateObjectScript(HttpServletResponse response, RwsObject rwsObject) throws IOException, ServletException {
        assert(response != null);
        assert(rwsObject != null);

        response.setContentType("text/javascript; charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            out.println("if (!rws) var rws = {};");
            out.println("if (!" + rwsObject.getName() + ") var " + rwsObject.getName() + " = {};");

            Set<Class> paramTypes = new HashSet<Class>();
            for (String methodName : rwsObject.listMethodNames()) {
                MethodDescriptor m = rwsObject.getTargetMethod(methodName);
                paramTypes.addAll(Arrays.asList(m.getMethod().getParameterTypes()));
                String params = generateParameters(m.getMethod().getParameterTypes());
                if (params.length() > 0) {
                    out.println(rwsObject.getName() + "." + methodName + " = function(" + params + ", onsuccess, onfailure) {");
                    out.println("    rws.call('sys', '" + methodName + "', '" + rwsObject.getName() + "', onsuccess, onfailure, " + params + ")");
                    out.println("}");
                } else {
                    out.println(rwsObject.getName() + "." + methodName + " = function(onsuccess, onfailure) {");
                    out.println("    rws.call('sys', '" + methodName + "', '" + rwsObject.getName() + "', onsuccess, onfailure)");
                    out.println("}");
                }
            }
            
            for (String eventName : rwsObject.listEventNames()) {
                EventSetDescriptor es = rwsObject.getTargetEvent(eventName);
                String evnm = Strings.upperFirst(eventName);
                MethodDescriptor[] listenerMethods = es.getListenerMethodDescriptors();
                for (MethodDescriptor m : listenerMethods) {
                    String mnm = Strings.upperFirst(m.getName());
                    // Event subscribe
                    out.println(rwsObject.getName() + ".subscribe" + evnm + mnm + " = function(handler) {");
                    out.println("    return rws.subscribe('sys', '" + m.getName() + "', '" + eventName + "', '" + rwsObject.getName() + "', handler)");
                    out.println("}");
                    // Event unsubscribe
                    out.println(rwsObject.getName() + ".unsubscribe" + evnm + mnm + " = function(handlerid) {");
                    out.println("    rws.unsubscribe(handlerid)");
                    out.println("}");
                }
            }

            for (Class paramType : paramTypes) {
                RwsRegistry.generateTypeScript(paramType, out);
            }
        } catch (RwsException ex) {
            throw new ServletException("Could not generate object script for " + rwsObject.getName(), ex);
        } finally {
            out.close();
        }
    }

    private String generateParameters(Class[] types) throws RwsException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append("p");
            result.append(i);
        }
        return result.toString();
    }

    private void generateOverview(HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            out.println("<html>");
            out.println("<head>");
            out.println("<title>RWS Objects</title>");
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>RWS Objects</h1>");
            out.println("</body>");
            out.println("</html>");
        } finally {
            out.close();
        }
    }

}
