/**
 * Copyright 2011 The Open Source Research Group,
 *                University of Erlangen-Nürnberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */
package org.sweble.wom3.util;

import javax.xml.XMLConstants;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;

/**
 * Creates transformer factories that use secure processing and, if the
 * implementation supports it, do not access external DTDs and stylesheets.
 */
public final class SecureTransformerFactories
{
	private SecureTransformerFactories()
	{
	}

	/**
	 * Creates a secured instance of the default transformer factory.
	 */
	public static TransformerFactory newInstance() throws TransformerFactoryConfigurationError
	{
		return secure(TransformerFactory.newInstance());
	}

	/**
	 * Creates a secured instance of the given transformer factory
	 * implementation.
	 */
	public static TransformerFactory newInstance(String factoryClassName) throws TransformerFactoryConfigurationError
	{
		return secure(TransformerFactory.newInstance(factoryClassName, null));
	}

	/**
	 * Enables secure processing on the given factory. Access to external DTDs
	 * and stylesheets is forbidden as well if the factory supports the
	 * respective JAXP properties (Saxon 9 and Xalan 2 do not).
	 *
	 * @throws TransformerFactoryConfigurationError
	 *             If the factory does not support secure processing.
	 */
	public static TransformerFactory secure(TransformerFactory factory) throws TransformerFactoryConfigurationError
	{
		try
		{
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		}
		catch (TransformerConfigurationException e)
		{
			throw new TransformerFactoryConfigurationError(e,
					"The transformer factory " + factory.getClass().getName()
							+ " does not support secure processing");
		}
		setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
		setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
		return factory;
	}

	private static void setAttributeIfSupported(TransformerFactory factory, String name, Object value)
	{
		try
		{
			factory.setAttribute(name, value);
		}
		catch (IllegalArgumentException e)
		{
			// Not supported by this implementation
		}
	}
}
