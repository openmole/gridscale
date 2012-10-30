I. Configure HTTPG Connector as described in TOMCAT_INSTALL.TXT

II. Deploy Axis on Tomcat

    Please look at the Axis Installation Guide for details.

    For normal use please install the CredentialHandler as described
    below. For testing purposes of the MyService example this step 
    is not necessary.

    Modify the server-config.wsdd and add CredentialHandler handler to 
    the <requestFlow> section of the <globalConfiguration> block, e.g.:

    <globalConfiguration>

     <requestFlow>
      ... 
      <handler type="java:org.globus.axis.handler.CredentialHandler"/>
      ... 
     <requestFlow/>

    </globalConfiguration>

III. Deploy MyService to Axis
 
    Copy MyService.jws file to Tomcat's webapps/axis/ directory.

IV. Connect to MyService 

   To run the client (assuming MyService is deployed on Tomcat server) type:

   java org.globus.axis.example.Client -l httpg://127.0.0.1:8443/axis/MyService.jws "hello"

   Note: you must specify the 'httpg' as the url protocol.
   
   Add all jar files inside lib/ and build/ to the classpath. Also include
   all jar files in Axis lib/ directory and xerces.jar (xerces.jar can be
   found in Tomcat's common/lib directory.
