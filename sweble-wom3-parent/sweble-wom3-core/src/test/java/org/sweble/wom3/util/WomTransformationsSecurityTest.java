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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.Test;

/**
 * The transformer factories handed out for WOM transformations have to use
 * secure processing.
 */
public class WomTransformationsSecurityTest
{
	private static final String SECRET_PROPERTY = "sweble.test.secret";

	private static final String SECRET = "sweble-secret-4711";

	@Test
	public void testSaxonFactoryUsesSecureProcessing() throws Exception
	{
		assertTrue(SaxonWomTransformations.getSaxonTransformerFactory()
				.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
	}

	@Test
	public void testXalanFactoryUsesSecureProcessing() throws Exception
	{
		assertTrue(XalanWomTransformations.getXalanTransformerFactory()
				.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
	}

	@Test
	public void testXalanFactoryRejectsJavaExtensionFunctions() throws Exception
	{
		String stylesheet = ""
				+ "<xsl:stylesheet version=\"1.0\""
				+ " xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\""
				+ " xmlns:system=\"xalan://java.lang.System\">"
				+ "<xsl:template match=\"/\">"
				+ "<out><xsl:value-of select=\"system:getProperty('" + SECRET_PROPERTY + "')\"/></out>"
				+ "</xsl:template>"
				+ "</xsl:stylesheet>";

		StringWriter out = new StringWriter();
		System.setProperty(SECRET_PROPERTY, SECRET);
		try
		{
			Transformer transformer = XalanWomTransformations.getXalanTransformerFactory()
					.newTransformer(new StreamSource(new StringReader(stylesheet)));
			transformer.transform(
					new StreamSource(new StringReader("<in/>")),
					new StreamResult(out));
		}
		catch (TransformerException e)
		{
			// Expected, extension functions are disabled
		}
		finally
		{
			System.clearProperty(SECRET_PROPERTY);
		}

		assertFalse(out.toString(), out.toString().contains(SECRET));
	}
}
