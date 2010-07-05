package org.codejive.rws;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
                generateTypeScript(response, rwsObject.getTargetClass());
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

    private void generateTypeScript(HttpServletResponse response, Class type) throws IOException, ServletException {
        assert(response != null);
        assert(type != null);

        response.setContentType("text/javascript; charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            out.println("if (!rws) var rws = {};");
            RwsRegistry.generateTypeScript(type, out);
        } catch (RwsException ex) {
            throw new ServletException("Could not generate object script for " + type.getSimpleName(), ex);
        } finally {
            out.close();
        }
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
