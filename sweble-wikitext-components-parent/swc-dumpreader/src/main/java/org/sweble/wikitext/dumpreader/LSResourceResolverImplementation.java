/**
 * Copyright 2011 The Open Source Research Group,
 *                University of Erlangen-Nürnberg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sweble.wikitext.dumpreader;

import java.io.InputStream;
import java.io.Reader;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * Resolves the schemas referenced by the export schemas from the class path.
 * Every other resource is refused, so loading a schema never accesses the
 * network or the file system.
 */
final class LSResourceResolverImplementation
		implements
			LSResourceResolver
{
	private static final Logger logger =
			LoggerFactory.getLogger(LSResourceResolverImplementation.class);

	private static final String XML_SCHEMA_TYPE = "http://www.w3.org/2001/XMLSchema";

	private static final String[] MEDIAWIKI_XML_URLS = {
			"http://www.mediawiki.org/xml",
			"https://www.mediawiki.org/xml" };

	private static final String[] XML_XSD_URLS = {
			"http://www.w3.org/2001/xml.xsd",
			"https://www.w3.org/2001/xml.xsd" };

	@Override
	public LSInput resolveResource(
			String type,
			String namespaceURI,
			String publicId,
			final String systemId,
			final String baseURI)
	{
		String xsdPath = null;
		if (XML_SCHEMA_TYPE.equals(type) && (publicId == null))
			xsdPath = getXsdFileNameIfExists(getClass(), systemId);

		if (xsdPath == null)
		{
			String message = String.format(
					Locale.ROOT,
					"Refusing to resolve: type = '''%s''', namespaceURI = '''%s''', publicId = '''%s''', systemId = '''%s''', baseURI = '''%s'''",
					type,
					namespaceURI,
					publicId,
					systemId,
					baseURI);

			logger.warn(message);

			throw new IllegalArgumentException(message);
		}

		return new LSInputImplementation(xsdPath, systemId, baseURI);
	}

	private static String getXsdFileNameIfExists(
			Class<?> clazz,
			String systemId)
	{
		if (systemId == null)
			return null;

		for (String url : XML_XSD_URLS)
		{
			if (url.equals(systemId))
				return "/xml.xsd";
		}

		for (String url : MEDIAWIKI_XML_URLS)
		{
			if (systemId.startsWith(url + "/export-")
					&& systemId.endsWith(".xsd"))
			{
				String fileName = systemId.substring(url.length());
				if ((fileName.indexOf('/', 1) == -1)
						&& (clazz.getResource(fileName) != null))
					return fileName;
			}
		}

		return null;
	}

	// =========================================================================

	private final class LSInputImplementation
			implements
				LSInput
	{
		private final String xsdPath;

		private final String systemId;

		private final String baseURI;

		private LSInputImplementation(
				String xsdPath,
				String systemId,
				String baseURI)
		{
			this.xsdPath = xsdPath;
			this.systemId = systemId;
			this.baseURI = baseURI;
		}

		@Override
		public void setSystemId(String systemId)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void setStringData(String stringData)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void setPublicId(String publicId)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void setEncoding(String encoding)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void setCharacterStream(Reader characterStream)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void setCertifiedText(boolean certifiedText)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void setByteStream(InputStream byteStream)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void setBaseURI(String baseURI)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public String getSystemId()
		{
			return systemId;
		}

		@Override
		public String getStringData()
		{
			return null;
		}

		@Override
		public String getPublicId()
		{
			return null;
		}

		@Override
		public String getEncoding()
		{
			return "UTF-8";
		}

		@Override
		public Reader getCharacterStream()
		{
			return null;
		}

		@Override
		public boolean getCertifiedText()
		{
			return false;
		}

		@Override
		public InputStream getByteStream()
		{
			return getClass().getResourceAsStream(xsdPath);
		}

		@Override
		public String getBaseURI()
		{
			return baseURI;
		}
	}
}
