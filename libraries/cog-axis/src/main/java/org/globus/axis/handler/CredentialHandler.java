/*
 * Copyright 1999-2006 University of Chicago
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.globus.axis.handler;

import javax.servlet.http.HttpServletRequest;
import javax.security.auth.Subject;

import org.apache.axis.AxisFault;
import org.apache.axis.MessageContext;
import org.apache.axis.handlers.BasicHandler;
import org.apache.axis.transport.http.HTTPConstants;

import org.globus.axis.gsi.GSIConstants;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.globus.gsi.gssapi.jaas.GlobusPrincipal;
import org.globus.gsi.gssapi.jaas.UserNamePrincipal;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;

/**
 * Axis server-side request handler. To be used only in when deployed in
 * Tomcat.
 */
public class CredentialHandler extends BasicHandler {

    private static Log log =
        LogFactory.getLog(CredentialHandler.class.getName());

    // must match the PEER_SUBJECT in security code
    private static final String CALLER_SUBJECT =
        "callerSubject";
    
    public void invoke(MessageContext msgContext) throws AxisFault {
        log.debug("Enter: invoke");

        Object tmp = msgContext.getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);

        if ((tmp == null) || !(tmp instanceof HttpServletRequest)) {
            return;
        }

        HttpServletRequest req = (HttpServletRequest)tmp;
        
        // if httpg is access protocol in servlet engine, axis
        // will not set the TRANS_URL property correctly.
        // this is a workaround for that problem
        String url = req.getRequestURL().toString();
        tmp = msgContext.getProperty(MessageContext.TRANS_URL);
        if (tmp == null && url != null) {
            msgContext.setProperty(MessageContext.TRANS_URL, url);
        }

        Subject subject = getSubject(msgContext);
        
        // USER_DN is set by both HTTPS/HTTPG valves
        tmp = req.getAttribute(GSIConstants.GSI_USER_DN);
        if (tmp != null) {
            msgContext.setProperty(GSIConstants.GSI_USER_DN, tmp);
            subject.getPrincipals().add(new GlobusPrincipal((String)tmp));
        }
        
        // GSI_CONTEXT is set by HTTPS valve only
        tmp = req.getAttribute(GSIConstants.GSI_CONTEXT);
        if (tmp != null) {
            msgContext.setProperty(GSIConstants.GSI_CONTEXT, tmp);
            GSSContext ctx = (GSSContext)tmp;
            try {
                if (ctx.getDelegCred() != null) {
                    subject.getPrivateCredentials().add(ctx.getDelegCred());
                }
            } catch (GSSException e) {
                log.warn("Unable to obtain delegated credentials", e);
            }
        }


        // GSI_CREDENTIALS is set only by HTTPG valve
        tmp = req.getAttribute(GSIConstants.GSI_CREDENTIALS);
        if (tmp != null) {
            log.debug("Delegation performed. Setting credentials property.");
            msgContext.setProperty(GSIConstants.GSI_CREDENTIALS, tmp);
            subject.getPrivateCredentials().add(tmp);
        } else {
            log.debug("Delegation not performed. Not setting credentials property.");
        }
        
        // GSI_AUTH_USERNAM is set only by HTTPG Valve
        tmp = req.getAttribute(GSIConstants.GSI_AUTH_USERNAME);
        if (tmp != null) {
            msgContext.setProperty(GSIConstants.GSI_AUTH_USERNAME, tmp);
            subject.getPrincipals().add(new UserNamePrincipal((String)tmp));
        }
        
        log.debug("Exit: invoke");
    }

    protected Subject getSubject(MessageContext msgCtx) {
        Subject subject = 
            (Subject)msgCtx.getProperty(CALLER_SUBJECT);
        if (subject == null) {
            subject = new Subject();
            msgCtx.setProperty(CALLER_SUBJECT, subject);
        }
        return subject;
    }

}
