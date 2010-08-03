package org.codejive.rws;

import java.beans.MethodDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.codejive.rws.utils.RwsContextWebFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author tako
 */
public class RwsServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(RwsServlet.class);

    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        log.debug("Incoming request: {}", path);
        if (path == null) {
            log.debug("Return overview page");
            RwsContext context = RwsContextWebFactory.getInstance(getServletContext()).getContext();
            generateOverview(context, response);
        } else if ("/rws.js".equals(path)) {
            log.debug("Return main script");
            // TODO return global JS page needed for all object
        } else if (path.startsWith("/object/") && path.endsWith(".js")) {
            String objName = path.substring(8, path.length() - 3);
            log.debug("Requesting object script for '{}'", objName);
            RwsContext context = RwsContextWebFactory.getInstance(getServletContext()).getContext();
            RwsObject rwsObject = context.getRegistry().getObject(objName);
            if (rwsObject != null) {
                response.setContentType("text/javascript; charset=UTF-8");
                PrintWriter out = response.getWriter();
                try {
                    out.println("if (!rws) var rws = {};");
                    context.getRegistry().generateTypeScript(rwsObject.getTargetClass(), out);
                } catch (RwsException ex) {
                    throw new ServletException("Could not generate object script for " + rwsObject.getTargetClass().getSimpleName(), ex);
                } finally {
                    out.close();
                }
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown RWS object '" + objName + "'");
            }
        } else if (path.startsWith("/test/") && path.endsWith(".html")) {
            String objName = path.substring(6, path.length() - 5);
            log.debug("Requesting test page for object '{}'", objName);
            RwsContext context = RwsContextWebFactory.getInstance(getServletContext()).getContext();
            RwsObject rwsObject = context.getRegistry().getObject(objName);
            if (rwsObject != null) {
                Set<String> instances = context.getRegistry().listInstanceNames(objName);
                if (instances != null && instances.size() > 0) {
                    generateTestPage(rwsObject, instances, response);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "No instances exist for RWS object '" + objName + "'");
                }
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

    private void generateOverview(RwsContext context, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            out.println("<html>");
            out.println("<head>");
            out.println("<title>RWS Objects</title>");
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>RWS Objects</h1>");
            out.println("<ul>");
            Set<String> names = context.getRegistry().listObjectNames();
            for (String name : names) {
                out.println("<li>");
                Set<String> instances = context.getRegistry().listInstanceNames(name);
                if (instances != null && instances.size() > 0) {
                    // TODO Fix URL
                    out.println("<a href=\"/rws/test/" + name + ".html\">");
                    out.println(name);
                    out.println("</a>");
                } else {
                    out.println(name);
                }
                out.println("</li>");
            }
            out.println("</ul>");
            out.println("</body>");
            out.println("</html>");
        } finally {
            out.close();
        }
    }

    private void generateTestPage(RwsObject rwsObject, Set<String> instances, HttpServletResponse response) throws IOException {
        response.setContentType("text/html; charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            out.println("<html><head><title>");
            out.println(rwsObject.scriptName());
            out.println("</title>");
            // TODO Fix URL
            out.println("<script type='text/javascript' src=\"../../js/rws.js\"></script>");
            out.println("<script type='text/javascript' src=\"../object/" + rwsObject.scriptName() + ".js\"></script>");
            out.println("<script type='text/javascript'>");
            out.println("if (!window.WebSocket) {");
            out.println("    alert('Sorry, you need a browser that supports WebSockets');");
            out.println("}");
            out.println("function connect() {");
            out.println("    var location = document.location.toString().replace('http://','ws://').replace('https://','wss://').replace('.html','');");
            // TODO Fix URL
            out.println("    rws.connect('ws://localhost:8080/dangerzone');");
            out.println("}");
            out.println("function call(methodname, pstart, pcnt) {");
            out.println("    var p = document.forms[0].elements;");
            out.println("    var obj = window[p[0].value];");
            out.println("    var fn = obj[methodname];");
            out.println("    var out = document.getElementById('result_' + methodname);");
            out.println("    var params = [];");
            out.println("    for (i = 0; i < pcnt; i++) {");
            out.println("        params[i] = p[pstart + i].value;");
            out.println("    };");
            out.println("    params[pcnt] = function(info) {");
            out.println("        out.innerHTML = info;");
            out.println("    };");
            out.println("    params[pcnt + 1] = function(info) {");
            out.println("        out.innerHTML = info;");
            out.println("    };");
            out.println("    fn.apply(obj, params);");
            out.println("}");
            out.println("</script>");
            out.println("</head><body onload=\"connect()\">");
            out.println("<h1>" + rwsObject.scriptName() + "</h1>");
            out.println("<form>");
            if (instances.size() > 1) {
                out.println("Instance: <select name=instance>");
                for (String instance : instances) {
                    out.println("<option>");
                    out.println(instance);
                    out.println("</option>");
                }
                out.println("</select><br><br>");
            } else {
                out.println("<input type=hidden name=instance value=\"" + instances.toArray()[0] + "\">");
                out.println("Instance: <b>");
                out.println(instances.toArray()[0]);
                out.println("</b><br><br>");
            }
            out.println("<ul>");
            int paramCount = 1;
            Set<String> methodNames = rwsObject.listMethodNames();
            for (String methodName : methodNames) {
                out.println("<li><b>");
                out.println(methodName);
                out.println("</b>(");
                MethodDescriptor m = rwsObject.getTargetMethod(methodName);
                Class[] params = m.getMethod().getParameterTypes();
                int startParam = paramCount;
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        if (i > 0) {
                            out.println(" , ");
                        }
                        out.println("<input type=text name=param>");
                        paramCount++;
                    }
                }
                paramCount++;
                out.println(")<input type=button value=Call onClick=\"call('" + methodName + "', " + startParam + ", " + params.length + ")\">");
                out.println("<span id=\"result_" + methodName + "\"></span></li>");
            }
            out.println("</ul>");
            out.println("</form>");
            out.println("</body></html>");
            //                } catch (RwsException ex) {
            //                    throw new ServletException("Could not generate object script for " + rwsObject.getTargetClass().getSimpleName(), ex);
        } finally {
            out.close();
        }
    }

}
