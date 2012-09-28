/**
 * Logging and Bookkeeping GetVersion() web service example based on axis.
 *
 * Examples uses default settings. Commented out is custom proxyFile.
 *
 * Build dependencies:
 *		axis.jar
 *		cog-axis.jar
 *		cog-jglobus.jar
 *		jaxrpc.jar
 *		log4j.jar[-1.2.8].jar
 *
 * Rutime dependencies:
 *		cog-url.jar
 *		commons-discovery.jar
 *		commons-logging.jar
 *		cryptix32.jar
 *		cryptix-asn1.jar
 *		jce-jdk13-131.jar
 *		puretls.jar
 *		saaj.jar
 *		wsdl4j.jar
 */
//package org.gridcc.wfms.lb.client;
package org.glite.lb;

import javax.xml.namespace.QName;

import org.apache.axis.configuration.SimpleProvider;
import org.glite.wsdl.services.lb.LoggingAndBookkeepingLocator;
import org.globus.axis.gsi.GSIConstants;
import org.globus.gsi.gssapi.auth.HostAuthorization;
import org.ietf.jgss.GSSCredential;

/**
 * Custom LoggingAndBookkeeping locator does some globus settings. Probably wouldn't be needed using some properties...
 * */
public class LoggingAndBookkeepingLocatorClient extends LoggingAndBookkeepingLocator {
	GSSCredential gssCred = null;

    public LoggingAndBookkeepingLocatorClient(SimpleProvider provider, GSSCredential gssCred) {
		super(provider);
		this.gssCred = gssCred;
	}

	public javax.xml.rpc.Call createCall() throws javax.xml.rpc.ServiceException {
		javax.xml.rpc.Call call = super.createCall();
		setGSI(call);
		return call;
	}

	public javax.xml.rpc.Call createCall(QName portName) throws javax.xml.rpc.ServiceException {
		javax.xml.rpc.Call call = super.createCall(portName);
		setGSI(call);
		return call;
	}

	public javax.xml.rpc.Call createCall(QName portName, String arg2)  throws javax.xml.rpc.ServiceException {
		javax.xml.rpc.Call call = super.createCall(portName, arg2);
		setGSI(call);
		return call;
	}

	public javax.xml.rpc.Call createCall(QName portName, QName arg2)  throws javax.xml.rpc.ServiceException {
		javax.xml.rpc.Call call = super.createCall(portName, arg2);
		setGSI(call);
		return call;
	}

	public void setGSI(javax.xml.rpc.Call call) {
		// specify loaded custom credential here
		call.setProperty(GSIConstants.GSI_CREDENTIALS, gssCred);

		//
		// Note: with 'host authorization', the host in the service URL
		// must agree with the host certificate DN of that service host.
		//
		call.setProperty(GSIConstants.GSI_AUTHORIZATION,
						 HostAuthorization.getInstance());

		//
		// If we are using httpg, we can use the transport level
		// delegation facilities of the CoG Kit to do full delegation
		// of the proxy credential.  If we are using https, then the
		// credential does NOT get delegated, but we can use the
		// poor man's method of delegation by storing the credential
		// to a MyProxy server with a random username and
		// passphrase, and a short lifetime.  (Note that the '-m'
		// must have been specified for this to occur here.)
		//
//		call.setProperty(GSIConstants.GSI_MODE, GSIConstants.GSI_MODE_FULL_DELEG);
	}
}