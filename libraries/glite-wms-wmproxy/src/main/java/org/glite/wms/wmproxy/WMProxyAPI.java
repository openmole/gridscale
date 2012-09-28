
/*
 * Copyright (c) 2005 on behalf of the EU EGEE Project:
 * The European Organization for Nuclear Research (CERN),
 * Istituto Nazionale di Fisica Nucleare (INFN), Italy
 * Datamat Spa, Italy
 * Centre National de la Recherche Scientifique (CNRS), France
 * CS Systeme d'Information (CSSI), France
 * Royal Institute of Technology, Center for Parallel Computers (KTH-PDC), Sweden
 * Universiteit van Amsterdam (UvA), Netherlands
 * University of Helsinki (UH.HIP), Finland
 * University of Bergen (UiB), Norway
 * Council for the Central Laboratory of the Research Councils (CCLRC), United Kingdom
 * 
 * Authors: Marco Sottilaro (marco.sottilaro@datamat.it)
 *
 * Authors: Marco Sottilaro (egee@datamat.it)
 */


package org.glite.wms.wmproxy;


import java.net.URL;
import java.io.File ;
import java.io.FileNotFoundException ;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream ;
import java.io.IOException;

import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;

import java.util.Calendar ;
import java.util.Date ;

// JSDL imports
/*
import org.w3c.dom.Element;
import org.ggf.schemas.jsdl._2005._11.jsdl.JobDefinition_Type;
import org.apache.axis.MessageContext;
import org.apache.axis.client.AxisClient;
*/


import org.gridsite.www.namespaces.delegation_2.*;
import org.glite.security.delegation.GrDPX509Util;
import org.glite.security.delegation.GrDProxyGenerator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import org.apache.axis.client.Stub;
/**
* Allows sending requests to the Workload Manager Proxy (WMProxy) server
* <p>
* The class WMProxyAPI represents a client API to access to the WMProxy services provided as Web Services.<BR>
* It provides a set of methods that allow :
* <UL>
* <LI>delegating the credential ;
* <LI>registering and submitting jobs ;
* <LI>cancelling the job during its life-cycle ;
* <LI>retrieving information on the location where the job input sandbox files can be stored ;
* <LI>retrieving the output sandbox files list ;
* <LI>retrieving a list of possible matching Computer Elements ;
* <LI> getting JDL templates ;
* <LI>getting information on the user disk quota on the server .
* </UL>
* </p>
* <p>
* Job requirements are expressed by Job Description Language (JDL).
* The types of jobs supported by the WM service are:
* <UL>
* <LI>Normal - a simple application
* <LI>DAG - a direct acyclic graph of dependent jobs
* <LI>Collection - a set of independent jobs
* <LI>Parametric - jobs with JDL's containing some parameters
* </UL>
 *
 * @author Marco Sottilaro <marco.sottilaro@datamat.it>
 * @version $Id: WMProxyAPI.java,v 1.20 2008/04/02 10:55:40 lpetronz Exp $
 *
 */

public class WMProxyAPI{
        /**
        *  Constructor
        *  @param url WMProxy service URL
        *  @param proxyFile the path location of the user proxy file
        *  @throws  ServiceException If any error in calling the service
        *  @throws ServiceURLException malformed service URL specified as input
        *  @throws CredentialException in case of any error with the user proxy file
        */
	public WMProxyAPI (String url, String proxyFile) throws org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServiceURLException,
					org.glite.wms.wmproxy.CredentialException {
                FileInputStream proxyStream = null;
                      try {
                            proxyStream = new FileInputStream (proxyFile);
                            } catch (FileNotFoundException e) {
	                        System.out.println("Failed retrieving proxy from path:"+e.toString());
                            }
                certsPath = "";
                this.WMProxyAPIConstructor(url, proxyStream, certsPath);

	}
        /**
        *  Constructor that allows setting of the Log4j tool by configuration file
        *  @param url WMProxy service URL
        *  @param proxyFile the path location of the user proxy file
        *  @param logPropFille the path location of the log4j properties file
	*  @throws  ServiceException If any error in calling the service
        *  @throws CredentialException in case of any error with the user proxy file
        *  @throws ServiceURLException malformed service URL specified as input
        */
	public WMProxyAPI (String url, String proxyFile, String certsPath) throws org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServiceURLException,
					org.glite.wms.wmproxy.CredentialException {
                FileInputStream proxyStream = null;
                      try {
                            proxyStream = new FileInputStream (proxyFile);
                            } catch (FileNotFoundException e) {
                                System.out.println("Failed retrieving proxy from path:"+e.toString());
                            }
                this.WMProxyAPIConstructor(url, proxyStream, certsPath);
	}
        /**
        *  Constructor
        *  @param url WMProxy service URL
        *  @param proxyFile the proxy in input as a stream
	*  @throws  ServiceException If any error in calling the service
        *  @throws CredentialException in case of any error with the user proxy file
        *  @throws ServiceURLException malformed service URL specified as input
        */
	public WMProxyAPI (String url, InputStream proxyFile) throws org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServiceURLException,
					org.glite.wms.wmproxy.CredentialException {
		String certsPath = "";
		this.WMProxyAPIConstructor (url, proxyFile, certsPath);
	}
        /**
        *  Constructor that allows setting of the Log4j tool by configuration file
        *  @param url WMProxy service URL
        *  @param proxyFile the proxy in input as a stream
        *  @param logPropFille the path location of the log4j properties file
	*  @throws  ServiceException If any error in calling the service
        *  @throws CredentialException in case of any error with the user proxy file
        *  @throws ServiceURLException malformed service URL specified as input
        */
         public WMProxyAPI (String url, InputStream proxyFile, String certsPath) throws org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServiceURLException,
					org.glite.wms.wmproxy.CredentialException {
		this.WMProxyAPIConstructor (url, proxyFile, certsPath);
	}
        /**
        *  Method that serves the constructors of the class
        *  @param url WMProxy service URL
        *  @param proxyFile the proxy in input as a stream
        *  @param logPropFille the path location of the log4j properties file
	*  @throws  ServiceException If any error in calling the service
        *  @throws CredentialException in case of any error with the user proxy file
        *  @throws ServiceURLException malformed service URL specified as input
        */
	private void WMProxyAPIConstructor (String url, InputStream proxyFile, String certsPath) throws org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServiceURLException,
					org.glite.wms.wmproxy.CredentialException {

		logger = Logger.getLogger(WMProxyAPI.class);
		logger.debug ("INPUT: url=[" + url + "] - proxyFile = [" + proxyFile.toString() + "] - certsPath=[" + certsPath + "]");
		try {
			// service URL
			this.serviceURL = new URL (url);
		} catch (java.net.MalformedURLException exc) {
			// MalformedURLException -> ServiceURLException
			throw new org.glite.wms.wmproxy.ServiceURLException (exc.getMessage());
		}
		// proxyFile
		try {
                        BufferedReader in = new BufferedReader (new InputStreamReader (proxyFile));
			String s, s2 = new String();
			while ( (s = in.readLine()) != null)
			     s2 += s + "\n";
                        in.close();
                        String proxyString = s2;
			this.proxyFile = proxyString;
		} catch (FileNotFoundException e) {
	                     System.out.println("Failed retrieving proxy:"+e.toString());
                } catch (IOException ioe) {
	                     System.out.println("Failed reading proxy:"+ioe.toString());
                }
		// CAs path
		this.certsPath = certsPath;
		// Sets up the connection
		this.setUpService ( );
	}
	/**
	*  Gets a proxy identified by the delegationId string.
	*  This method remains to keep compatibility with the version 1.0.0 of WMProxy servers,
	*  but it will be soon deprecated.
	* @param delegationID the id to identify the delegation
	* @return the string representing the proxy
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws ServiceException a problem occurred during the remote call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public java.lang.String getProxyReq (java.lang.String delegationId)
		throws org.glite.wms.wmproxy.AuthenticationFaultException,
			org.glite.wms.wmproxy.AuthorizationFaultException,
			org.glite.wms.wmproxy.ServiceException,
			org.glite.wms.wmproxy.ServerOverloadedFaultException {
		logger.debug ("INPUT: delegationId=[" +delegationId + "]" );
		try {
			return this.serviceStub.getProxyReq(delegationId) ;
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.ServerOverloadedFaultType exc) {
			// ServerOverloadedFaultException
			throw new org.glite.wms.wmproxy.ServerOverloadedFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	*  Gets a proxy identified by the delegationId string.
	* This method can be only used invoking WMProxy servers with versions greater than or equal to 2.0.0;
	*  the version of the server can be retrieved by calling the getVersion service.
	*  @param delegationID the id to identify the delegation
	*  @return a string representing the proxy
	*  @throws CredentialException a problem occurred during the operations of retrieving the proxy
	* @throws ServiceException a problem occurred during the remote call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	* @since 1.2.0
	*/
	public java.lang.String grstGetProxyReq (java.lang.String delegationId)
		throws org.glite.wms.wmproxy.CredentialException,
			org.glite.wms.wmproxy.ServiceException,
			org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: delegationId=[" +delegationId + "]" );
		try {
			return this.grstStub.getProxyReq(delegationId) ;
		} catch ( org.gridsite.www.namespaces.delegation_2.DelegationException exc)  {
			// DelegationException -> CredentialException
			throw new org.glite.wms.wmproxy.CredentialException(exc.getMessage());
		} catch (java.rmi.RemoteException exc)  {
			// RemoteException ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
 		} catch (Exception exc )  {
			// Exception -> ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	*  Gets a proxy identified by the delegationId string.
	* This method can be only used invoking WMProxy servers with versions greater than or equal to 3.0.0;
	*  the version of the server can be retrieved by calling the getVersion service.
	*  @param delegationID the id to identify the delegation
	*  @return a NewProxyReq representing the proxy
	*  @throws CredentialException a problem occurred during the operations of retrieving the proxy
	* @throws ServiceException a problem occurred during the remote call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public org.gridsite.www.namespaces.delegation_2.NewProxyReq getNewProxyReq ( )
		throws org.glite.wms.wmproxy.CredentialException,
			org.glite.wms.wmproxy.ServiceException,
			org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		try {
			return this.grstStub.getNewProxyReq( ) ;
		} catch (org.gridsite.www.namespaces.delegation_2.DelegationException exc)  {
			// DelegationException -> CredentialException
			throw new org.glite.wms.wmproxy.CredentialException(exc.getMessage());
		} catch (java.rmi.RemoteException exc)  {
			// RemoteException ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
 		} catch (Exception exc )  {
			// Exception -> ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	*  Queries implementation specific meta information about the service
	* This method can be only used invoking WMProxy servers with versions greater than or equal to 3.0.0;
	*  the version of the server can be retrieved by calling the getVersion service.
	*  @param the key of the queried service metadata
	*  @return a string representing the information
	*  @throws CredentialException a problem occurred during the operations of retrieving the proxy
	* @throws ServiceException a problem occurred during the remote call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public java.lang.String getServiceMetadata ( java.lang.String key )
		throws org.glite.wms.wmproxy.CredentialException,
			org.glite.wms.wmproxy.ServiceException,
			org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		try {
			return this.grstStub.getServiceMetadata( key ) ;
		} catch (org.gridsite.www.namespaces.delegation_2.DelegationException exc)  {
			// DelegationException -> CredentialException
			throw new org.glite.wms.wmproxy.CredentialException(exc.getMessage());
		} catch (java.rmi.RemoteException exc)  {
			// RemoteException ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
 		} catch (Exception exc )  {
			// Exception -> ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	*  Queries the version of the supported interface.
	* This method can be only used invoking WMProxy servers with versions greater than or equal to 3.0.0;
	*  the version of the server can be retrieved by calling the getVersion service.
	*  @return a string representing the version number of the interface
	*  @throws CredentialException a problem occurred during the operations of retrieving the proxy
	* @throws ServiceException a problem occurred during the remote call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public java.lang.String getInterfaceVersion ( )
		throws org.glite.wms.wmproxy.CredentialException,
			org.glite.wms.wmproxy.ServiceException,
			org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		try {
			return this.grstStub.getInterfaceVersion( ) ;
		} catch (org.gridsite.www.namespaces.delegation_2.DelegationException exc)  {
			// DelegationException -> CredentialException
			throw new org.glite.wms.wmproxy.CredentialException(exc.getMessage());
		} catch (java.rmi.RemoteException exc)  {
			// RemoteException ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
 		} catch (Exception exc )  {
			// Exception -> ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	*  Returns the expiration date and time of the credential, associated with the given delegationId.
	* This method can be only used invoking WMProxy servers with versions greater than or equal to 3.0.0;
	*  the version of the server can be retrieved by calling the getVersion service.
	*  @param delegationID the id to identify the delegation
	*  @return a calendar with the date and time when the delegated credentials will expire
	*  @throws CredentialException a problem occurred during the operations of retrieving the proxy
	* @throws ServiceException a problem occurred during the remote call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public java.util.Calendar getTerminationTime ( java.lang.String delegationId )
		throws org.glite.wms.wmproxy.CredentialException,
			org.glite.wms.wmproxy.ServiceException,
			org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		try {
			return this.grstStub.getTerminationTime( delegationId ) ;
		} catch (org.gridsite.www.namespaces.delegation_2.DelegationException exc)  {
			// DelegationException -> CredentialException
			throw new org.glite.wms.wmproxy.CredentialException(exc.getMessage());
		} catch (java.rmi.RemoteException exc)  {
			// RemoteException ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
 		} catch (Exception exc )  {
			// Exception -> ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	*  Destroys the delegated credentials associated with the given delegationId.
	* This method can be only used invoking WMProxy servers with versions greater than or equal to 3.0.0;
	*  the version of the server can be retrieved by calling the getVersion service.
	*  @param delegationID the id to identify the delegation
	*  @throws CredentialException a problem occurred during the operations of retrieving the proxy
	* @throws ServiceException a problem occurred during the remote call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public void destroy ( java.lang.String delegationId )
		throws org.glite.wms.wmproxy.CredentialException,
			org.glite.wms.wmproxy.ServiceException,
			org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		try {
			this.grstStub.destroy( delegationId ) ;
		} catch (org.gridsite.www.namespaces.delegation_2.DelegationException exc)  {
			// DelegationException -> CredentialException
			throw new org.glite.wms.wmproxy.CredentialException(exc.getMessage());
		} catch (java.rmi.RemoteException exc)  {
			// RemoteException ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
 		} catch (Exception exc )  {
			// Exception -> ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	*  Restarts the delegation procedure by asking for a certificate signing request from the server for an already
	* existing delegationId.
	* This method can be only used invoking WMProxy servers with versions greater than or equal to 3.0.0;
	*  the version of the server can be retrieved by calling the getVersion service.
	*  @param delegationID the id to identify the delegation
	*  @return a string with the new proxy request, which is to replace the existing one
	*  @throws CredentialException a problem occurred during the operations of retrieving the proxy
	* @throws ServiceException a problem occurred during the remote call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public java.lang.String renewProxyReq ( java.lang.String delegationId )
		throws org.glite.wms.wmproxy.CredentialException,
			org.glite.wms.wmproxy.ServiceException,
			org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		try {
			return this.grstStub.renewProxyReq( delegationId ) ;
		} catch (org.gridsite.www.namespaces.delegation_2.DelegationException exc)  {
			// DelegationException -> CredentialException
			throw new org.glite.wms.wmproxy.CredentialException(exc.getMessage());
		} catch (java.rmi.RemoteException exc)  {
			// RemoteException ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
 		} catch (Exception exc )  {
			// Exception -> ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* This method allows delegating user credential to the WMProxy server:
	* it creates a proxy from the input certificate (got by a previous call to the createProxyfromCertReq server).
	* The created proxy is sent to the server and identified by a delegationId string.
	* This string can be used to call some services accepting a delegationId string as input parameter
	*  (like  jobRegister, jobSubmit, etc) until its expiration time.
	*  This method remains to keep compatibility with the version 1.0.0 of WMProxy servers,
	*  but it will be soon deprecated; the version of the server can be retrieved by calling the getVersion service.
	* @param delegationId the id used to identify the delegation
	* @param cert the input certificate
	* @throws CredentialException if any error occurs during the creation of the proxy from the input certificate
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	* @see #getVersion
	*
	*/
	public void putProxy(java.lang.String delegationId, java.lang.String cert)
		throws org.glite.wms.wmproxy.AuthenticationFaultException,
			org.glite.wms.wmproxy.AuthorizationFaultException,
			org.glite.wms.wmproxy.CredentialException,
			org.glite.wms.wmproxy.ServiceException,
			org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: cert=[" + cert+ "]");
		logger.debug ("INPUT: delegationId=[" +delegationId + "]");
		logger.debug ("Creating proxy from certificate (CreateProxyfromCertReq)");
		// Creates the proxy to be delegated from the input certificate
		String proxy = this.createProxyfromCertReq(cert);
		try {
			// Delegating user proxy
			logger.debug ("Delegating credential (putProxy)");
			this.serviceStub.putProxy(delegationId, proxy) ;
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* This method allows delegating user credential to the WMProxy server using the GridSite package:
	* it creates a proxy from the input certificate (got by a previous call to the createProxyfromCertReq server).
	* The created proxy is sent to the server and identified by a delegationId string.
	* This string can be used to call some services accepting a delegationId string as input parameter
	*  (like  jobRegister, jobSubmit, etc) until its expiration time.
	* This method can be only used invoking WMProxy servers with versions greater than or equal to 2.0.0
	*  @param delegationId the id used to identify the delegation
	*  @param cert the input certificate
	* @throws CredentialException a problem occurred during the operations of delegation
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	* @since 1.2.0
	* @see #getVersion
	*/
	public void grstPutProxy(java.lang.String delegationId, java.lang.String cert)
		throws org.glite.wms.wmproxy.CredentialException,
		org.glite.wms.wmproxy.ServiceException,
		org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: cert=[" + cert + "]");
		logger.debug ("INPUT: delegationId=[" +delegationId + "]");
		logger.debug ("Creating proxy from certificate (CreateProxyfromCertReq)");
		String proxy = this.createProxyfromCertReq(cert);
		logger.debug ("Delegating credential (putProxy)");
		try {
			this.serviceStub.putProxy(delegationId,proxy) ;
		} catch (org.gridsite.www.namespaces.delegation_2.DelegationException exc)  {
			// DelegationException -> CredentialException
			throw new org.glite.wms.wmproxy.CredentialException(exc.getMessage());
		} catch (java.rmi.RemoteException exc)  {
			// RemoteException ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
 		} catch (Exception exc )  {
			// Exception -> ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* Returns the Delegated Proxy information identified by the delegationId string
	* @param delegationId The id of the delegation created previously (by a getProxyReq call)
	* @param cfs Non-default configuration context (proxy file, endpoint URL and trusted cert location) ;  if NULL, the object is created with the default parameters
	* @return a struct with the information on the input proxy
	*  @throws AuthenticationFaultException a generic authentication problem occurred.
	*  @throws AuthorizationFaultException if the client is not authorized to perform this operation.
	*  @throws InvalidArgumentFaultException one or more of the given input parameters is not valid.
	*  @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	* @see #getProxyReq
	* @see #grstGetProxyReq
	* @see #putProxy
	* @see #grstPutProxy
	* @since 1.5.3
	*/
	public ProxyInfoStructType getDelegatedProxyInfo(java.lang.String delegationId)
		throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: delegationId=[" + delegationId + "]");
		try {
			return this.serviceStub.getDelegatedProxyInfo(delegationId);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}  catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* Returns the information related to the proxy used to submit a job that identified by its JobId.
	* This operation needs that a valid proxy (identified by an id string -delegationId string-) has been previously delegated to the endpoint.
	* @param jobId the identifier of the job
	* @param cfs Non-default configuration context (proxy file, endpoint URL and trusted cert location) ;  if NULL, the object is created with the default parameters
	* @return a struct with the information on the input proxy
	*  @throws AuthenticationFaultException a generic authentication problem occurred.
	*  @throws AuthorizationFaultException if the client is not authorized to perform this operation.
	*  @throws InvalidArgumentFaultException one or more of the given input parameters is not valid.
	*  @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	* @see #getProxyReq
	* @see #putProxy
	* @see BaseException
	*/
	public ProxyInfoStructType getJobProxyInfo(java.lang.String jobId)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: jobId=[" + jobId + "]");
		try {
			return this.serviceStub.getJobProxyInfo(jobId);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}  catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* Gets the version numbers of the WMProxy services
	* @return the string with the version numbers
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public java.lang.String getVersion()
			throws org.glite.wms.wmproxy.AuthenticationFaultException,
				org.glite.wms.wmproxy.ServiceException,
				org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		try {
			return this.serviceStub.getVersion() ;
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception -> ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* Returns the information related to the status of the job identified by its JobId.
	* This operation needs that a valid proxy (identified by an id string -delegationId string-) has been previously delegated to the 	* endpoint.
	* @param jobId the identifier of the job
	* @return a struct with the information on the status of the job
	* @throws AuthenticationFaultException a generic authentication problem occurred.
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation.
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public JobStatusStructType getJobStatus(java.lang.String jobId)
			throws org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException {
		try {
			return this.serviceStub.getJobStatus(jobId);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.ServerOverloadedFaultType exc) {
			// ServerOverloadedFaultException
			throw new org.glite.wms.wmproxy.ServerOverloadedFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}  catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* Registers a job for submission.The unique identifier assigned to the job is returned to the client.
	* This operation only registers the job and assigns it with an identifier.
	* The processing of the job (matchmaking, scheduling etc.) within the WM is not started.
	* The job is "held" by the system in the "Registered" status until the jobStart operation is called.
	* Between the two calls the client can perform all preparatory actions needed by the job to run
	* (e.g. the registration of input data, the upload of the job input sandbox files etc); especially
	* those actions requiring the specification of the job identifier.
	* The service supports registration (and consequently submission) of simple jobs, parametric jobs, partitionable jobs, DAGs and collections of jobs.
	* The description is always provided through a single JDL description.
	* When a clients requests for registration of a complex object, i.e. parametric and partitionable jobs, DAGs and collections of jobs (all those requests represent in fact a set of jobs),
	* the operations returns a structure containing the main identifier of the complex object and the identifiers of all related sub jobs.
	* @param jdl the job jdl representation.
	* @param delegationId the id used to identify the delegation
	* @return the structure containing the id of the registered job
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws InvalidArgumentFaultException the given JDL expression is not valid
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public org.glite.wms.wmproxy.JobIdStructType jobRegister(java.lang.String jdl, java.lang.String delegationId)
			throws org.glite.wms.wmproxy.AuthenticationFaultException,
				org.glite.wms.wmproxy.InvalidArgumentFaultException,
				org.glite.wms.wmproxy.ServiceException,
				org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: JDL=[" + jdl + "]");
		logger.debug ("INPUT: delegationId=[" +delegationId + "]");
		try {
			return this.serviceStub.jobRegister (jdl, delegationId) ;
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		}  catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception -> ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}

	/**
	* Triggers the submission a previously registered job. It starts the actual processing of the registered job within the Workload Manager.
	* It is assumed that when this operation is called, all the work preparatory to the job
	* (e.g. input sandbox upload, registration of input data to the Data Management service etc.) has been completed by the client.<BR>
	* Here follows an example of the sequence of operations to be performed for submitting a job:<BR>
	* <UL>
	* <LI> jobId = jobRegister(JDL)
	* <LI> destURI = getSandboxDestURI(jobID)
	* <LI> transfer of the job Input Sandbox file to the returned destURI (using GridFTP)
	* <LI> jobStart(jobId).Triggers the submission for a previously registered job
	* </UL>
	* @param jobId the id of the job
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws JobUnknownFaultException the given job has not been registered to the system
	* @throws InvalidArgumentFaultException the given job Id is not valid
	* @throws OperationNotAllowedFaultException the current job status does not allow requested operation.
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public void jobStart(java.lang.String jobId)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.OperationNotAllowedFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.JobUnknownFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: jobid=[" + jobId + "]");
		try {
			this.serviceStub.jobStart ( jobId ) ;
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.OperationNotAllowedFaultType exc) {
			// OperationNotAllowedFault
			throw new org.glite.wms.wmproxy.OperationNotAllowedFaultException(this.createExceptionMessage(exc));
		 } catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			//
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.JobUnknownFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.JobUnknownFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}

	/**
	* Submits a job: performs registration and triggers the submission
	* The JDL description of the job provided by the client is validated by the service,
	* registered to the LB and finally passed to the Workload Manager.
	* The unique identifier assigned to the job is returned to the client.
	* Usage of this operation (instead of jobRegister + jobStart) is indeed recommended when the job identifier is not needed prior to its submission
	* (e.g. jobs without input sandbox or with a sandbox entirely available on a GridFTP server managed by the client or her organisation).
	* The service supports submission of simple jobs, parametric jobs, partitionable jobs, DAGs and collections of jobs;
	* the description is always provided through a single JDL description.
	* When a clients requests for submission of a complex object, i.e. parametric and partitionable jobs, DAGs and collections of jobs
	* (all those requests represent in fact a set of jobs), the operations returns a structure containing the main identifier of the complex object
	* and the identifiers of all related sub jobs.
	* @param jobId the id of the job
	* @param delegationId the id used to identify the delegation
	* @return the id of the job
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws InvalidArgumentFaultException the given job Id is not valid
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public org.glite.wms.wmproxy.JobIdStructType jobSubmit(java.lang.String jdl, java.lang.String delegationId)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {

		logger.debug ("INPUT: JDL=[" + jdl + "]\n");
		logger.debug ("INPUT: delegationId=[" +delegationId + "]");
		try {
			return this.serviceStub.jobSubmit(jdl, delegationId) ;
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}

	/**
	* This operation cancels a previously submitted job identified by its JobId. If the job is still managed by the WM then it is removed from the WM tasks queue.
	* If the job has been already sent to the CE, the WM simply forwards the request to the CE.
	* For suspending/releasing and sending signals to a submitted job the user has to check that the job has been scheduled to a CE
	* and access directly the corresponding operations made available by the CE service.
	* @param jobId the id of the job
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws JobUnknownFaultException the given job has not been registered to the system
	* @throws InvalidArgumentFaultException the given job Id is not valid
	* @throws OperationNotAllowedFaultException the current job status does not allow requested operation.
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public void jobCancel(java.lang.String jobId)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.OperationNotAllowedFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.JobUnknownFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: jobid=[" + jobId + "]");
		try {
			this.serviceStub.jobCancel(jobId);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.OperationNotAllowedFaultType exc) {
			// OperationNotAllowedFault
			throw new org.glite.wms.wmproxy.OperationNotAllowedFaultException(this.createExceptionMessage(exc));
		 } catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
		 	// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.JobUnknownFaultType exc) {
			// JobUnknownFault
			throw new org.glite.wms.wmproxy.JobUnknownFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* Returns the maximum Input sandbox size (in bytes) a user can count on for a job submission if using the space managed by the WM.
	* This is a static value in the WM configuration (on a job-basis) set by the VO administrator
	*  @return the size of the InputSandbox (in bytes)
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public double getMaxInputSandboxSize()
		throws org.glite.wms.wmproxy.AuthenticationFaultException,
			org.glite.wms.wmproxy.ServiceException,
			org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		try {
			return this.serviceStub.getMaxInputSandboxSize();
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		 } catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* Returns the server available transfer protocols to be used for file transferring operations
	* @return a list with the protocol strings
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public org.glite.wms.wmproxy.StringList getTransferProtocols( )
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		try {
			return this.serviceStub.getTransferProtocols();
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		 } catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* Returns a list of destination URI's associated to the job, identified by the jobId provided as input,
	* where the job input sandbox files can be uploaded by the client.
	* The location is created in the storage managed by the WM (if needed) and
	* the corresponding URI is returned to the operation caller if no problems has been arised during creation.
	* Files of the job input sandbox that have been referenced in the JDL as relative or absolute paths
 	* are expected to be found in the returned location when the job lands on the CE.
	* File upload can be performed by the GridFTP/HTTPS server available on the WMS node(using file-transferring tools like curl and globus-url-copy).
	* The user can also specify in the JDL the complete URI of files that are stored on a GridFTP/HTTPS server (e.g. managed by her organisation);
	* those files will be directly downloaded (by the JobWrapper) on the WN where the job will run without transiting on the WMS machine.
	* The same applies to the output sandbox files list, i.e. the user can specify in the JDL the complete URIs for the files of the output sandbox;
	* those files will be directly uploaded (by the JobWrapper) from the WN to the specified GridFTP/HTTPS servers without transiting on the WMS machine.
	* @param jobId string containing the JobId
	* @param protocol string containing the protocol to use in the returned URI (Server available protocols are those returned by getTransferProtocols
* operation. Possible standard values are: "all" to get URIs with all available protocols; "default" to get URIs with the server default protocol
*  not mandatory, default value is "all")
	* @return the list of the Sandbox destionation URI's strings
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws JobUnknownFaultException the given job has not been registered to the system
	* @throws InvalidArgumentFaultException the given job Id is not valid
	* @throws OperationNotAllowedFaultException the current job status does not allow requested operation.
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public org.glite.wms.wmproxy.StringList getSandboxDestURI(java.lang.String jobId, java.lang.String protocol)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.OperationNotAllowedFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.JobUnknownFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: jobid=[" + jobId + "] - protocol [" + protocol +"]");
		try {
			return this.serviceStub.getSandboxDestURI(jobId, protocol);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.OperationNotAllowedFaultType exc) {
			// OperationNotAllowedFault
			throw new org.glite.wms.wmproxy.OperationNotAllowedFaultException(this.createExceptionMessage(exc));
		 } catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			//. InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}catch (org.glite.wms.wmproxy.JobUnknownFaultType exc) {
			// JobUnknownFault
			throw new org.glite.wms.wmproxy.JobUnknownFaultException(this.createExceptionMessage(exc));
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* Returns the list of destination URIs associated to a compound job (i.e. a DAG a Collection or a parametric jobs) and all of its sub-jobs in a complex structure containing:
	* <UL>
	* <LI> the job id
	* <LI> the corresponding list of destination URIs (can be more than one if different transfer protocols are supported, e.g. gsiftp, https etc.)
	* <UL>
	* The structure contains an element (structure above) for the compound job id provided (at first position) and one further element for any sub nodes.
	* It contains only one element if the job id provided as imnput is the identifier of a simple job.
	* The location is created in the storage managed by the WMS and the corresponding URI is returned to the operation caller if no problems has been arised during creation.
	* Files of the job input sandbox that have been referenced in the JDL as relative or absolute paths are expected to be found in the returned location when the job lands on the CE.	 	* File upload can be performed by the GridFTP/HTTPS server available on the WMS node(using file-transferring tools like curl and globus-url-copy).
	* The user can also specify in the JDL the complete URI of files that are stored on a GridFTP/HTTPS server (e.g. managed by her organisation);
	* those files will be directly downloaded (by the JobWrapper) on the WN where the job will run without transiting on the WMS machine.
	* The same applies to the output sandbox files list, i.e. the user can specify in the JDL the complete URIs for the files of the output sandbox;
	* those files will be directly uploaded (by the JobWrapper) from the WN to the specified GridFTP/HTTPS servers without transiting on the WMS machine.
	* @param jobId string containing the JobId
	* @param protocol string containing the protocol to use in the returned URI (Server available protocols are those returned by getTransferProtocols
	* @return the list of the Sandbox destionation URI's strings
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws JobUnknownFaultException the given job has not been registered to the system
	* @throws InvalidArgumentFaultException the given job Id is not valid
	* @throws OperationNotAllowedFaultException the current job status does not allow requested operation.
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public DestURIsStructType getSandboxBulkDestURI(java.lang.String jobId, java.lang.String protocol)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.OperationNotAllowedFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.JobUnknownFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: jobid=[" + jobId + "] - protocol [" + protocol + "]");
		try {
			return this.serviceStub.getSandboxBulkDestURI(jobId, protocol);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.OperationNotAllowedFaultType exc) {
			// OperationNotAllowedFault
			throw new org.glite.wms.wmproxy.OperationNotAllowedFaultException(this.createExceptionMessage(exc));
		 } catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.JobUnknownFaultType exc) {
			// JobUnknownFault
			throw new org.glite.wms.wmproxy.JobUnknownFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* Returns the total space quota assigned to the user on the storage managed by the WM
	* The fault GetQuotaManagementFault is returned if the quota management is not active on the WM.
	* @param softLimit the returned value of the soft limit free quota i.e. the difference between quota soft limit and user's disk usage.
	* @param hardLimit  the returned value of the hard limit quota (in bytes) i.e. the real quota limit that cannot be exceeded.
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws GetQuotaManagementFaultException quota management is not active on the WM.
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public void getTotalQuota (javax.xml.rpc.holders.LongHolder softLimit, javax.xml.rpc.holders.LongHolder hardLimit)
		throws org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.GetQuotaManagementFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		try {
			this.serviceStub.getTotalQuota(softLimit, hardLimit);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GetQuotaManagementFaultType exc) {
			// GetQuotaManagementFault
			throw new org.glite.wms.wmproxy.GetQuotaManagementFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}

	/**
	* Returns the remaining free part of available user disk quota (in bytes).
	* The fault GetQuotaManagementFault is returned if the quota management is not active.
	* @param softLimit the returned value of the soft limit free quota i.e. the difference between quota soft limit and user's disk usage.
	* @param hardLimit  the returned value of the hard limit quota (in bytes) i.e. the real quota limit that cannot be exceeded.
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws GetQuotaManagementFaultException quota management is not active on the WM.
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public void getFreeQuota (javax.xml.rpc.holders.LongHolder softLimit, javax.xml.rpc.holders.LongHolder hardLimit)
		throws org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.GetQuotaManagementFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		try {
			this.serviceStub. getFreeQuota(softLimit, hardLimit);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GetQuotaManagementFaultType exc) {
			// GetQuotaManagementFault
			throw new org.glite.wms.wmproxy.GetQuotaManagementFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}

	/**
	* Removes from the WM managed space all files related to the job identified by the jobId provided as input.
	* It can only be applied for job related files that are managed by the WM.
	* E.g. Input/Output sandbox files that have been specified in the JDL through a URI will be not subjected to this management.
	* @param jobId the identifier of the job
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws JobUnknownFaultException the given job has not been registered to the system
	* @throws InvalidArgumentFaultException the given job Id is not valid
	* @throws OperationNotAllowedFaultException the current job status does not allow requested operation.
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public void jobPurge(java.lang.String jobId)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.OperationNotAllowedFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.JobUnknownFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {

		logger.debug ("INPUT: jobid=[" + jobId + "]");
		try {
			this.serviceStub.jobPurge(jobId);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.OperationNotAllowedFaultType exc) {
			// OperationNotAllowedFault
			throw new org.glite.wms.wmproxy.OperationNotAllowedFaultException(this.createExceptionMessage(exc));
		 } catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		}  catch (org.glite.wms.wmproxy.JobUnknownFaultType exc) {
			// JobUnknownFault
			throw new org.glite.wms.wmproxy.JobUnknownFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* Returns the list of URIs where the output files created during job execution have been stored in the WM managed space and the corresponding sizes in bytes.
	* It can only be applied for files of the Output Sandbox that are managed by the WM (i.e. not specified as URI in the JDL).
	* @param jobId the identifier of the job
	* @param cfs Non-default configuration context (proxy file, endpoint URL and trusted cert location) ;  if NULL, the object is created with the default parameters
	* @return  the list of objects containing the file URI and the corresponding size in bytes
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws JobUnknownFaultException the given job has not been registered to the system
	* @throws InvalidArgumentFaultException the given job Id is not valid
	* @throws OperationNotAllowedFaultException the current job status does not allow requested operation.
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public org.glite.wms.wmproxy.StringAndLongList getOutputFileList (java.lang.String jobId, java.lang.String protocol)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.OperationNotAllowedFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.JobUnknownFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: jobid=[" + jobId + "] - protocol [" +  protocol + "]");
		try {
			return this.serviceStub.getOutputFileList (jobId, protocol);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.OperationNotAllowedFaultType exc) {
			// OperationNotAllowedFault
			throw new org.glite.wms.wmproxy.OperationNotAllowedFaultException(this.createExceptionMessage(exc));
		 } catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		}  catch (org.glite.wms.wmproxy.JobUnknownFaultType exc) {
			// JobUnknownFault
			throw new org.glite.wms.wmproxy.JobUnknownFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}

        /**
        * Returns the list of CE Ids satisfying the job Requirements specified in the JDL, ordered according to the decreasing Rank.
	* The fault NoSuitableResourcesFault is returned if there are no resources matching job constraints.
        * @param jdl the job description
        * @param delegationId the string used previously to delegate credential
        * @return the list of the found resources and their rank values
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws NoSuitableResourcesFaultException no suitable resources matching job requirements have been found.
	* @throws InvalidArgumentFaultException the given job JDL expression is not valid
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
        */
        public org.glite.wms.wmproxy.StringAndLongList jobListMatch (java.lang.String jdl, java.lang.String delegationId)
                			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.NoSuitableResourcesFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
                logger.debug ("INPUT: JDL=[" + jdl + "] - deleagtionId=[" + delegationId + "]");
               	try {
			return this.serviceStub.jobListMatch(jdl, delegationId);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
 		 } catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.NoSuitableResourcesFaultType exc) {
			// NoSuitableResourcesFault
			throw new org.glite.wms.wmproxy.NoSuitableResourcesFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
        }
	/**
	* Enables file perusal functionalities if not disabled with the specific jdl attribute during job
	* register operation.
	* Calling this operation, the user enables perusal for job identified by jobId, for files specified with fileList.
	* After this operation, the URIs of perusal files generated during job execution can be retrieved by calling the getPerusalFiles service
	* An empty fileList disables perusal.
	* This method can be only used invoking WMProxy servers with version greater than or equal to 2.0.0;
	*  the version of the server can be retrieved by calling the getVersion service.
	* @param jobid the string with the job identifier
	* @param fileList the list of filenames to be enabled
	* @throws AuthorizationFaultException if the client is not authorized to perform this operation
	* @throws AuthenticationFaultException a generic authentication problem occurred
	* @throws InvalidArgumentFaultException the given jobId is not valid
	* @throws OperationNotAllowedFaultException perusal was disabled with the specific jdl attribute.
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	* @see #getPerusalFiles
	* @see #getVersion
	*/
	public void enableFilePerusal (java.lang.String  jobId, org.glite.wms.wmproxy.StringList fileList)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.JobUnknownFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: jobId=[" +jobId + "]");
		try {
			this.serviceStub.enableFilePerusal(jobId, fileList);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		}  catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.JobUnknownFaultType exc) {
			// JobUnknownFault
			throw new org.glite.wms.wmproxy.JobUnknownFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		} catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}  catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	 }
	 /**
	* Gets the URIs of perusal files generated during job execution for the specified file file.
	* If allChunks is set to true all perusal URIs will be returned; also the URIs already requested with a
	* previous getPerusalFiles operation. Default value is false.
	* Perusal files have to be presiuosly enabled by calling the enableFilePerusal service
	* This method can be only used invoking WMProxy servers with version greater than or equal to 2.0.0;
	*  the version of the server can be retrieved by calling the getVersion service.
	* @param jobid the string with the job identifier
	* @param allchuncks boolean value to specify when to get all chuncks
	* @param jobid the string with the job identifier
	* @param file the name of the perusal file be enabled
	* @param jobid the string with the job identifier
	* @param file the name of the perusal file be enabled
	* @param protocol string containing the protocol to use in the returned URI (Server available protocols are those returned by getTransferProtocols
	* @throws AuthenticationFaultException An authentication problem occurred
	* @throws AuthorizationFaultException The user is not authorized to perform this operation
	* @throws InvalidArgumentFaultException If the given jobId is not valid
	* @throws JobUnknownFaultException The provided jobId has not been registered to the system
	* @throws OperationNotAllowedFaultexception perusal was disabled with the specific jdl attribute
	* @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	* @see #enableFilePerusal
	* @see #getVersion
	*/
	public  org.glite.wms.wmproxy.StringList getPerusalFiles (java.lang.String  jobId, java.lang.String file, boolean allchunks, java.lang.String protocol)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.JobUnknownFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException  {
		logger.debug ("INPUT: jobId=[" + jobId + "] - file=[" +file + "] - allchunck=[" + allchunks + "] - protocol [" +  protocol + "]");
		try {
			return this.serviceStub.getPerusalFiles(jobId, file, allchunks, protocol);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		}  catch (org.glite.wms.wmproxy.JobUnknownFaultType exc) {
			// JobUnknownFault
			throw new org.glite.wms.wmproxy.JobUnknownFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}  catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	 }
	/**
	*  Returns a JDL template for the requested job type.
	*  @param jobType the job type description
	*  @param executable the exacutable filename
	*  @param arguments the arguments of the executable
	*  @param requirements  a string representing the expression describing all the Job requirements (which is an attribute of boolean type)
	*  @param rank a string representing the expression for the rank (which is an attribute of double type) of the resources
	*  @return the jdl template of the job
	*  @throws AuthenticationFaultException a generic authentication problem occurred.
	*  @throws AuthorizationFaultException if the client is not authorized to perform this operation.
	*  @throws InvalidArgumentFaultException one or more of the given input parameters is not valid
	*  @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public java.lang.String getJobTemplate (org.glite.wms.wmproxy.JobTypeList jobType,
										java.lang.String executable,
										java.lang.String arguments,
										java.lang.String requirements,
										java.lang.String rank)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException {

		logger.debug ("INPUT: executable=[" + executable + "] - arguments=[" + arguments + "]");
		logger.debug ("INPUT: requirements=[" + requirements + "] - rank=[" + rank + "]");
		try {
			return this.serviceStub.getJobTemplate(jobType, executable, arguments, requirements, rank);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}  catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	*  Returns a JDL template for a DAG.
	*  @param dependencies the description of the DAG
	*  @param requirements a string representing the expression describing all the Job requirements (which is an attribute of boolean type)
	*  @param rank a string representing the expression for the rank (which is an attribute of double type) for all the nodes of the dag
	*  @return the jdl template of the DAG
	*  @throws AuthenticationFaultException a generic authentication problem occurred.
	*  @throws AuthorizationFaultException if the client is not authorized to perform this operation.
	*  @throws InvalidArgumentFaultException one or more of the given input parameters is not valid
	*  @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*
	*/
	public java.lang.String getDAGTemplate (org.glite.wms.wmproxy.GraphStructType dependencies,
										java.lang.String requirements,
										java.lang.String rank)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException {
		logger.debug ("INPUT: requirements=[" + requirements + "] - rank=[" + rank + "]");
		try {
			return this.serviceStub.getDAGTemplate(dependencies, requirements, rank);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}  catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}


	/**
	*  Returns a JDL template for a collection of jobs, that is a set of independent jobs that can be submitted, controlled and monitored as a single entity.
	*  @param jobNumber integer representing the number of jobs of the collection.
	*  @param requirements a string representing the expression describing all the Job requirements (which is an attribute of boolean type)
	*  @param rank a string representing the expression for the rank (which is an attribute of double type) of the resources
	*  @return the jdl template of the collection
	*  @throws AuthenticationFaultException a generic authentication problem occurred.
	*  @throws AuthorizationFaultException if the client is not authorized to perform this operation.
	*  @throws InvalidArgumentFaultException one or more of the given input parameters is not valid.
	*  @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public java.lang.String getCollectionTemplate (int jobNumber,
					java.lang.String requirements,
					java.lang.String rank)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException {
		logger.debug ("INPUT: requirements=[" + requirements + "] - rank=[" + rank + "]");
		try {
			return this.serviceStub.getCollectionTemplate( jobNumber, requirements, rank);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}  catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}


	/**
	*  Returns a JDL template for a parametric of job, which is a job having one or more parametric attributes in the JDL.
	* The parametric attributes vary their values according to the "Parameter" attribute specified in the JDL itself (in this case the parametere has to be an integer).
	* The submission of a Parametric job results in the submission  of a set of jobs having the same descritpion apart from the parametrised attribute. They can be however controlled and monitored as a single entity.
	*  @param attributes list of strings representing the parametric JDL attributes
	*  @param param integer representing the value of the Parameter attribute
	*  @param paramStart	 integer representing the start value for varying the parametric attributes
	*  @param paramStep intege representing the step of each variation
	*  @param requirements a string representing the expression describing all the Job requirements (which is an attribute of boolean type)
	*  @param rank a string representing the expression for the rank (which is an attribute of double type) of the resources
	*  @return the jdl template
	*  @throws AuthenticationFaultException a generic authentication problem occurred.
	*  @throws AuthorizationFaultException if the client is not authorized to perform this operation.
	*  @throws InvalidArgumentFaultException one or more of the given input parameters is not valid.
	*  @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public java.lang.String getIntParametricJobTemplate (org.glite.wms.wmproxy.StringList attributes,
													int param,
													int parameterStart,
													int parameterStep,
													java.lang.String requirements,
													java.lang.String rank)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException {
		logger.debug ("INPUT: param=[" + param + "] - parameterStart=[" + parameterStart + "] - parameterStep=[" + parameterStep + "]");
		logger.debug ("INPUT: requirements=[" + requirements + "] - rank=[" + rank + "]");
		try {
			return this.serviceStub.getIntParametricJobTemplate(attributes, param, parameterStart, parameterStep, requirements, rank);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}  catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	*  Returns a JDL template for a parametric of job, which is a job having one or more parametric attributes in the JDL.
	* The parametric attributes vary their values according to the "Parameter" attribute specified in the JDL itself (in this case the parametere has to be a list of strings).
	* The submission of a Parametric job results in the submission  of a set of jobs having the same descritpion apart from the value of the parametric attributes.
	* They can be however controlled and monitored as a single entity.
	*  @param attributes list of strings representing the parametric JDL attributes
	*  @param param list of strings representing the values of the Parameter attribute
	*  @param requirements a string representing the expression describing all the Job requirements (which is an attribute of boolean type)
	*  @param rank a string representing the expression for the rank (which is an attribute of double type) of the resources
	*  @return the jdl template
	*  @throws AuthenticationFaultException a generic authentication problem occurred.
	*  @throws AuthorizationFaultException if the client is not authorized to perform this operation.
	*  @throws InvalidArgumentFaultException one or more of the given input parameters is not valid
	*  @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	*/
	public java.lang.String getStringParametricJobTemplate (org.glite.wms.wmproxy.StringList attributes,
													 org.glite.wms.wmproxy.StringList param,
													 java.lang.String requirements,
													  java.lang.String rank)

			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException {
		try {
			return this.serviceStub.getStringParametricJobTemplate(attributes, param, requirements, rank);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}  catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
	/**
	* Returns the JDL string which identifier is the input JobId
	* @param jobid the identifier of the job
	* @param type the type of the JDL to be retrieved (either ORIGINAL or REGISTERED)
	* @return the string with the JDL
	*  @throws AuthenticationFaultException a generic authentication problem occurred.
	*  @throws AuthorizationFaultException if the client is not authorized to perform this operation.
	*  @throws InvalidArgumentFaultException one or more of the given input parameters is not valid.
	*  @throws ServiceException If any other error occurs during the execution of the remote method call to the WMProxy server
	* @throws ServerOverloadedFaultException the server is too much busy to attend the requested operation
	* @since 1.5.3
	*/
	public java.lang.String getJDL(java.lang.String jobId, JdlType type)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException {
		logger.debug ("INPUT: jobId=[" + jobId + "] - JdlType=[" + type + "]");
		try {
			return this.serviceStub.getJDL(jobId, type);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}  catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}



/*
	public org.glite.wms.wmproxy.JobIdStructType jobRegisterJSDL(
						JobDefinitionHandler jsdl_h,
						java.lang.String delegationId)
			throws org.glite.wms.wmproxy.AuthorizationFaultException,
					org.glite.wms.wmproxy.AuthenticationFaultException,
					org.glite.wms.wmproxy.InvalidArgumentFaultException,
					org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.ServerOverloadedFaultException {
		logger.debug ("INPUT: delegationId=[" + delegationId);
		try {
			return this.serviceStub.jobRegisterJSDL(jsdl_h.getJobDefinition(), delegationId);
		} catch (org.glite.wms.wmproxy.AuthenticationFaultType exc) {
			// AuthenticationFault
			throw new org.glite.wms.wmproxy.AuthenticationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.AuthorizationFaultType exc) {
			// AuthorizationFault
			throw new org.glite.wms.wmproxy.AuthorizationFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.InvalidArgumentFaultType exc) {
			// InvalidArgumentFault
			throw new org.glite.wms.wmproxy.InvalidArgumentFaultException(this.createExceptionMessage(exc));
		} catch (org.glite.wms.wmproxy.GenericFaultType exc) {
			// GenericFault ->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(this.createExceptionMessage(exc));
		}  catch ( java.rmi.RemoteException exc) {
			// RemoteException->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		} catch (Exception exc) {
			// Exception->ServiceException
			throw new org.glite.wms.wmproxy.ServiceException(exc.getMessage());
		}
	}
*/



	/**
	*  Generates a proxy from the input certificate and from the user proxy file on the user local machine
	* (which path has to be specified as input parameter in one of the class costructors)
	*  This service is called after the getProxyReq
	*  the input proxy string of this service is the string got by getProxyReq.
	*  @param certReq Service certificate request
	*  @return the generated proxy certificate
	*  @throws CredentialException in case of any error with the local user proxy
	*/

	private String createProxyfromCertReq(java.lang.String certReq)
				throws org.glite.wms.wmproxy.CredentialException {
		byte[ ] proxy = null;
		ByteArrayInputStream stream = null;
		CertificateFactory cf = null;
		X509Certificate cert = null ;
		long lifetime = 0;
		try {
			// generator object
			GrDProxyGenerator generator = new GrDProxyGenerator ( );
			// user proxy file
			String proxyStream = System.getProperty("gridProxyStream");
                        if (proxyStream == null) {
				throw new org.glite.wms.wmproxy.CredentialException  ( "proxy file not found at: "+ this.proxyFile );
			}
			try{
				// gets the local proxy as array of byte
				//proxy = GrDPX509Util.getFileBytes( File );
				proxy = proxyStream.getBytes();
				// reads the proxy time-left
				stream = new ByteArrayInputStream(proxy);
				cf = CertificateFactory.getInstance("X.509");
				cert = (X509Certificate)cf.generateCertificate(stream);
				stream.close();
				Date now = new Date ( );
				lifetime =( cert.getNotAfter().getTime ( ) - now.getTime() ) / 3600000  ; // in hour ! (TBC in secs)
			} catch (Exception exc){
				String errmsg = "an error occured while loading the local proxy  ("  + this.proxyFile +"): \n";
				errmsg += exc.toString();
				throw new  org.glite.wms.wmproxy.CredentialException (errmsg);
			}
			// checks if the proxy is still valid
			if (lifetime < 0 ){
				throw new org.glite.wms.wmproxy.CredentialException ("the local proxy has expired (" + this.proxyFile +")" );
			}
			// sets the lifetime
			generator.setLifetime ((int)lifetime);
			// creates the new proxy
			proxy =  generator.x509MakeProxyCert(certReq.getBytes( ) , proxy, "");
			// converts the proxy from byte[] to String
			return new String(proxy);
		} catch (Exception exc) {
 			throw new org.glite.wms.wmproxy.CredentialException (exc.getMessage());
		}
	}
	 /**
	* Sets up the service
	*/
	private void setUpService ( ) throws org.glite.wms.wmproxy.ServiceException,
					org.glite.wms.wmproxy.CredentialException {
		String protocol = "";
		try {
			// Gets a stub to the service
			this.serviceLocator = new WMProxyLocator( );
			// pointer to GliteWMProxy Stub object
			this.serviceStub = (WMProxyStub) serviceLocator.getWMProxy_PortType( this.serviceURL ) ;
			// pointer to Gridsite Stub object
			this.grstStub = (DelegationSoapBindingStub) serviceLocator.getWMProxyDelegation2_PortType(this.serviceURL) ;
		} catch (javax.xml.rpc.ServiceException exc) {
			throw new org.glite.wms.wmproxy.ServiceException (exc.getMessage());
		}
		// Checks for https protocol
		protocol = serviceURL.getProtocol( ).trim( );
		logger.debug(protocol);
		// Sets security properties for https connections
		if ( protocol.compareTo("https") == 0 ){
			System.setProperty("axis.socketSecureFactory","org.glite.security.trustmanager.axis.AXISSocketFactory");
		        System.setProperty("gridProxyStream", proxyFile);
			// Bug #26989 fix, turning off crlUpdate threads for memory heap issues
			System.setProperty("crlUpdateInterval", "0 seconds");

		}
		if (certsPath.length()>0){
			System.setProperty(org.glite.security.trustmanager.ContextWrapper.CA_FILES, certsPath);
		}
	}
	/*
	* Creates an exception message from the input exception object
	*/
	private String createExceptionMessage(BaseFaultType exc) {
		String message = "";
		String date = "";
		int hours = 0;
		String ec = exc.getErrorCode();
		String[] cause = (String[])exc.getFaultCause();
		// fault description
		String desc = exc.getDescription();
		if (desc.length()>0) { message = desc + "\n";}
		// method
		String meth = exc.getMethodName() ;
		if (meth.length()>0) { message += "Method: " + meth + "\n";}
		// time stamp
		Calendar  calendar = exc.getTimestamp();
		//java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
		hours = calendar.get(Calendar.HOUR_OF_DAY) - (calendar.get(Calendar.ZONE_OFFSET)/ (60*60*1000));
		/*
		calendar.set(Calendar.HOUR_OF_DAY, hours);
		df.setCalendar(calendar);
		calendar = df.getCalendar( );
		if (calendar != null){
		*/
			calendar.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
			date = dayStr[calendar.get(Calendar.DAY_OF_WEEK)] + " " +
				monthStr[calendar.get(Calendar.MONTH)] + " " +
				twodigits(calendar.get(Calendar.DAY_OF_MONTH)) + " "
				+ calendar.get(Calendar.YEAR) + " ";
			//hours =  - (calendar.get(Calendar.ZONE_OFFSET)/ (60*60*1000));
			date += twodigits(calendar.get(Calendar.HOUR_OF_DAY)) + ":" +
				twodigits(calendar.get(Calendar.MINUTE)) + ":" +
				twodigits(calendar.get(Calendar.SECOND)) ;
			date += " " + calendar.getTimeZone().getID( );
			if (date.length()>0) { message += "TimeStamp: " + date + "\n";}
		//}
		// error code
		if (ec.length()>0) { message += "ErrorCode: " + ec + "\n";}
		// fault cause(s)
		for (int i = 0; i < cause.length; i++) {
			if (i==0) { message += "Cause: " + cause[i] + "\n";}
			else { message += cause[i] + "\n" ;}
		}
		return message;
	}

	private String twodigits(int n) {
		String td = "";
		if (n>=0 && n<10) {
			td = "0" + n ;
		} else {
			td = "" + n;
		}
		return td;
	}

   	public void setTimeout(int timeout) {
                ((Stub) serviceStub).setTimeout(timeout);
                ((Stub) grstStub).setTimeout(timeout);
        }

	/** Service URL */
	private URL serviceURL = null;
	/** Proxy file location */
	private String proxyFile = null;
	/** Certificate Authorities files location */
	private String certsPath = null;
	/** Service location */
	private WMProxyLocator serviceLocator= null;
	/** Glite WMProxy Service Stub */
	private WMProxyStub serviceStub= null ;
	/** GridSite Service Stub */
	private DelegationSoapBindingStub grstStub = null;
	/** Log manager */
	private Logger logger= null ;

	private final static String[] monthStr  = {"Jan", "Feb", "March", "Apr", "May", "June" ,"July", "Aug", "Sept", "Oct", "Nov", "Dec"};
	private final static String[] dayStr = {"Sun", "Mon", "Tue", "Wedn", "Thu", "Fri" ,"Sat"};

}
