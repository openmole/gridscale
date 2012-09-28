/*
Copyright (c) Members of the EGEE Collaboration. 2004. 
See http://www.eu-egee.org/partners/ for details on the copyright
holders.  

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 

    http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
 */
package org.glite.security.util;

import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

/**
 * Case insensitive version of Properties class. All keys are lowercased when
 * used.
 * 
 * @see java.util.Properties
 * @author Joni Hahkala Created on July 25, 2002, 10:56 PM
 */
public class CaseInsensitiveProperties extends java.util.Properties {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1735376359346160599L;

	/**
	 * Creates a new instance of CaseInsensitiveProperties.
	 * 
	 * @param defaults
	 *            the default values to set.
	 */
	public CaseInsensitiveProperties(Properties defaults) {
		super();
		loadProperties(defaults);
	}

	/**
	 * Creates a new instance of CaseInsensitiveProperties.
	 */
	public CaseInsensitiveProperties() {
		super();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Properties#getProperty(java.lang.String)
	 */
	public synchronized String getProperty(String key) {
		return super.getProperty(key.toLowerCase());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Properties#getProperty(java.lang.String, java.lang.String)
	 */
	public synchronized String getProperty(String key, String defaultValue) {
		return super.getProperty(key.toLowerCase(), defaultValue);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Properties#setProperty(java.lang.String, java.lang.String)
	 */
	public synchronized Object setProperty(String key, String value) {
		return super.setProperty(key.toLowerCase(), value);
	}

	/**
	 * Loads the properties from the stream inputStream.
	 * 
	 * @see java.util.Properties#load(java.io.InputStream)
	 */
	public synchronized void load(java.io.InputStream inputStream)
			throws java.io.IOException {
		Properties tempProperties = new Properties();
		tempProperties.load(inputStream);
		loadProperties(tempProperties);
	}

	/**
	 * Loads the properties from inProperties into this instance. The defaults
	 * from inProperties are loaded first and the non-default properties are set
	 * after that. Thus default setting (Key1=value1) is overridden with
	 * non-default setting (key1=value2).
	 * 
	 * @param inProperties
	 *            the properties to load.
	 */
	@SuppressWarnings("unchecked")
	public void loadProperties(Properties inProperties) {
		// load properties from inProperties
		Enumeration<String> names = (Enumeration<String>)inProperties.propertyNames();

		while (names.hasMoreElements()) {
			String key = names.nextElement();
			setProperty(key, inProperties.getProperty(key));
		}
	}

	/**
	 * @see java.util.Hashtable#remove(java.lang.Object)
	 * 
	 * This implementation only accepts strings as keys, if other than String
	 * is used, an exception is thrown.
	 */
	public synchronized Object remove(Object keyObj) throws IllegalArgumentException {
		if (!(keyObj instanceof String)) {
			throw new IllegalArgumentException(
					"CaseInsensitiveProperties.remove accepts only Strings as argument");
		}

		String key = (String) keyObj;

		return super.remove(key.toLowerCase());
	}

	/**
	 * Puts a String in to the property storage case insensitivitizing it.
	 * 
	 * @param key
	 *            The key to store the key under.
	 * @param value
	 *            The value to store.
	 * @return The previous value stored under this key, null if there wasn't
	 *         one.
	 */
	public Object put(String key, String value) {
		return setProperty(key, value);
	}

	/**
	 * Gets the string stored under the given key.
	 * 
	 * @param key
	 *            The key to use to get the value.
	 * @return The value if there was one, null otherwise.
	 */
	public String get(String key) {
		return getProperty(key);
	}

	/**
	 * Checks whether the key exists in the storage.
	 * 
	 * @param key
	 *            the key to search for.
	 * @return true if key is used, false if not.
	 */
	public synchronized boolean containsKey(String key) {
		// TODO Auto-generated method stub
		return super.containsKey(key.toLowerCase());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Hashtable#putAll(java.util.Map)
	 */
	@SuppressWarnings("unchecked")
	public synchronized void putAll(Map<? extends Object,? extends Object> t) {
		if (t == null){
			throw new NullPointerException("The Property object is null.");
		}
		
		for (Map.Entry<? extends Object, ? extends Object> entry : t.entrySet()) {
			if(!(entry.getKey() instanceof String)){
				throw new IllegalArgumentException("Property object contained a key that is not a String!" + entry.getKey().toString());
			}
			if(!(entry.getValue() instanceof String)){
				throw new IllegalArgumentException("Property object contained a value that is not a String!" + entry.getKey().toString());
			}
            put(((String)entry.getKey()).toLowerCase(), entry.getValue());
        }
	}

}
