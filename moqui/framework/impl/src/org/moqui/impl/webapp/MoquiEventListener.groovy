/*
 * This Work is in the public domain and is provided on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE,
 * NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * You are solely responsible for determining the appropriateness of using
 * this Work and assume any risks associated with your use of this Work.
 *
 * This Work includes contributions authored by David E. Jones, not as a
 * "work for hire", who hereby disclaims any copyright to the same.
 */
package org.moqui.impl.webapp

import javax.servlet.http.HttpSessionListener
import javax.servlet.http.HttpSession
import javax.servlet.http.HttpSessionEvent

import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import java.sql.Timestamp

class MoquiEventListener implements HttpSessionListener {
    protected final static Logger logger = LoggerFactory.getLogger(MoquiEventListener.class)

    void sessionCreated(HttpSessionEvent event) {
        HttpSession session = event.getSession()
        ExecutionContextFactoryImpl ecfi = (ExecutionContextFactoryImpl) session.getServletContext().getAttribute("executionContextFactory")
        if (!ecfi) {
            logger.warn("Not creating visit for session [${session.id}], no executionContextFactory in ServletContext")
            return
        }

        // create and persist Visit
        String webappName = session.getServletContext().getContextPath().substring(1) ?: "ROOT"
        Map parameters = [sessionId:session.id, webappName:webappName, fromDate:new Timestamp(session.getCreationTime())]
        InetAddress address = InetAddress.getLocalHost();
        if (address) {
            parameters.serverIpAddress = address.getHostAddress()
            parameters.serverHostName = address.getHostName()
        }
        Map result = ecfi.serviceFacade.sync().name("create", "Visit").parameters((Map<String, Object>) parameters).call()

        // put visitId in session as "moqui.visitId"
        session.setAttribute("moqui.visitId", result.visitId)
    }

    void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession()
        ExecutionContextFactoryImpl ecfi = (ExecutionContextFactoryImpl) session.getServletContext().getAttribute("executionContextFactory")
        if (!ecfi) {
            logger.warn("Not updating (closing) visit for session [${session.id}], no executionContextFactory in ServletContext")
            return
        }
        String visitId = session.getAttribute("visitId")
        if (!visitId) {
            logger.warn("Not updating (closing) visit for session [${session.id}], no visitId attribute found")
            return
        }
        // set thruDate on Visit
        ecfi.serviceFacade.sync().name("update", "Visit")
                .parameters((Map<String, Object>) [visitId:visitId, thruDate:new Timestamp(System.currentTimeMillis())])
                .call()
    }
}