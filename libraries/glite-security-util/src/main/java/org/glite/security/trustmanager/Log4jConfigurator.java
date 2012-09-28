package org.glite.security.trustmanager;

import java.io.IOException;

import org.apache.log4j.Appender;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.PropertyConfigurator;

/**
 * Class to configure the log4j outside of the other classes allowing the use of slf4j without log4j present.
 * 
 * @author hahkala
 *
 */
public class Log4jConfigurator {
	
	/**
	 * Configures the log4j logger if needed.
	 * 
	 * @param logConfFile if this is not null, it is used for configuring the logging. Should be a normal log4j configuration file.
	 * @param logFile if the logConfFile is null, and this is not null, it is used to define the file where the logging messages go.
	 * @return returns true if the logging was configured, false otherwise.
	 * @throws IOException Thrown in case the logConfFile loading fails. 
	 */
	static public boolean configure (String logConfFile, String logFile) throws IOException {
        // if given a config file, use that.
        if (logConfFile != null) {
            PropertyConfigurator.configure(logConfFile);

            return true;
        }

        // if given no config file, but given an output file, set it up and
        // use it
        if (logFile != null) {
            Layout lay = new PatternLayout("%d{ISO8601} %-5p [%t] %c{2} %x - %m%n");
            Appender appender = new FileAppender(lay, logFile);
            BasicConfigurator.configure(appender);

            return true;
        }

        return false;
	}

}
