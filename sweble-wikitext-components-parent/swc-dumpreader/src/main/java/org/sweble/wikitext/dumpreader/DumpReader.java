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

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Locale;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.ValidationEvent;
import jakarta.xml.bind.ValidationEventHandler;
import jakarta.xml.bind.ValidationEventLocator;
import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.xml.sax.SAXException;

import de.fau.cs.osr.utils.WrappedException;

public abstract class DumpReader
		implements
			Closeable
{
	private final InputStream dumpInputStream;

	private final String dumpUri;

	private final Logger logger;

	private final XMLStreamReader xmlStreamReader;

	private final Unmarshaller unmarshaller;

	private final ExportSchemaVersion schemaVersion;

	private final long fileLength;

	private CountingInputStream decompressedInputStream;

	private CountingInputStream compressedInputStream;

	private long parsedCount;

	private boolean decompress;

	private static final int BUFFER_SIZE = 4096;

	/**
	 * How many bytes of the (decompressed) dump may precede the root element.
	 * The export version is determined from the root element before the
	 * stream is rewound to the beginning.
	 */
	private static final int MAX_HEADER_SIZE = 1024 * 1024;

	// =========================================================================

	/**
	 * Use a constructor that expects an encoding to prevent bug in xerces
	 * parser code.
	 * 
	 * @deprecated
	 */
	public DumpReader(File dumpFile, Logger logger) throws JAXBException, FactoryConfigurationError, XMLStreamException, IOException, SAXException
	{
		this(new FileInputStream(dumpFile), null, dumpFile.getAbsolutePath(), dumpFile.length(), logger, true);
	}

	/**
	 * Use a constructor that expects an encoding to prevent bug in xerces
	 * parser code.
	 * 
	 * @deprecated
	 */
	public DumpReader(InputStream is, String url, Logger logger) throws JAXBException, FactoryConfigurationError, XMLStreamException, IOException, SAXException
	{
		this(is, null, url, logger, true);
	}

	/**
	 * Use a constructor that expects an encoding to prevent bug in xerces
	 * parser code.
	 * 
	 * @deprecated
	 */
	public DumpReader(
			InputStream is,
			String url,
			Logger logger,
			boolean useSchema) throws JAXBException, FactoryConfigurationError, XMLStreamException, IOException, SAXException
	{
		this(is, null, url, logger, useSchema);
	}

	/**
	 * Reads a dump from a stream of unknown size; {@link #getFileSize()}
	 * returns -1.
	 */
	public DumpReader(
			InputStream is,
			Charset encoding,
			String url,
			Logger logger,
			boolean useSchema) throws JAXBException, FactoryConfigurationError, XMLStreamException, IOException, SAXException
	{
		this(is, encoding, url, -1, logger, useSchema);
	}

	/**
	 * Reads a dump file. The file's name decides whether it is decompressed
	 * and its length is returned by {@link #getFileSize()}.
	 */
	public DumpReader(
			File dumpFile,
			Charset encoding,
			Logger logger,
			boolean useSchema) throws JAXBException, FactoryConfigurationError, XMLStreamException, IOException, SAXException
	{
		this(new FileInputStream(dumpFile), encoding, dumpFile.getAbsolutePath(), dumpFile.length(), logger, useSchema);
	}

	/**
	 * The reader takes ownership of the given stream: it is closed when the
	 * dump was read, when the reader is closed or when this constructor
	 * fails.
	 *
	 * @param url
	 *            Names ending in ".bz2" or ".gz" are decompressed.
	 * @param fileSize
	 *            The size of the dump in bytes as returned by
	 *            {@link #getFileSize()} or -1 if unknown.
	 */
	public DumpReader(
			InputStream is,
			Charset encoding,
			String url,
			long fileSize,
			Logger logger,
			boolean useSchema) throws JAXBException, FactoryConfigurationError, XMLStreamException, IOException, SAXException
	{
		this.dumpInputStream = is;
		this.dumpUri = url;
		this.logger = logger;
		this.fileLength = fileSize;
		this.parsedCount = 0;

		boolean constructed = false;
		try
		{
			logger.info("Setting up parser for file " + dumpUri);

			getDumpInputStream();

			schemaVersion = determineExportVersion(encoding);

			unmarshaller = createUnmarshaller(schemaVersion.getContextPath());

			installCallbacks();

			if (useSchema)
				setSchema(DumpReader.class.getResource(schemaVersion.getSchema()));

			xmlStreamReader = createXmlStreamReader(decompressedInputStream, encoding);

			constructed = true;
		}
		finally
		{
			if (!constructed)
				closeStreams();
		}
	}

	// =========================================================================

	public void unmarshal() throws JAXBException, XMLStreamException
	{
		try
		{
			unmarshaller.unmarshal(xmlStreamReader);
		}
		finally
		{
			closeStreams();
		}
	}

	@Override
	public void close() throws IOException
	{
		closeStreams();
	}

	private void closeStreams()
	{
		IOUtils.closeQuietly(decompressedInputStream);
		IOUtils.closeQuietly(compressedInputStream);
		IOUtils.closeQuietly(dumpInputStream);
	}

	/**
	 * @return The size of the dump in bytes or -1 if unknown.
	 */
	public long getFileSize()
	{
		return fileLength;
	}

	public long getDecompressedBytesRead() throws IOException
	{
		return decompressedInputStream.getCount();
	}

	public long getCompressedBytesRead() throws IOException
	{
		if (decompress)
		{
			return compressedInputStream.getCount();
		}
		else
		{
			return getDecompressedBytesRead();
		}
	}

	public long getParsedCount()
	{
		return parsedCount;
	}

	// =========================================================================

	protected abstract void processPage(Object mediaWiki, Object page);

	protected boolean processRevision(Object page, Object revision)
	{
		// Add by default
		return true;
	}

	/**
	 * Called for every {@code <logitem>} element of an export version 0.7 or
	 * later dump. Log items are not kept in the MediaWiki object. Does
	 * nothing by default.
	 *
	 * In export versions 0.5 and 0.6 log items are part of a page and are
	 * passed to {@link #processRevision(Object, Object)} instead.
	 */
	protected void processLogItem(Object mediaWiki, Object logItem)
	{
		// Ignore by default
	}

	protected boolean processEvent(
			ValidationEvent ve,
			ValidationEventLocator vel)
	{
		logger.warn(String.format(
				Locale.ROOT,
				"%s:%d:%d: %s",
				dumpUri,
				vel.getLineNumber(),
				vel.getColumnNumber(),
				ve.getMessage()));

		return true;
	}

	// =========================================================================

	private void handlePage(Object mediaWiki, Object page)
	{
		++parsedCount;

		processPage(mediaWiki, page);
	}

	private boolean handleRevision(Object page, Object revision)
	{
		return processRevision(page, revision);
	}

	private void handleLogItem(Object mediaWiki, Object logItem)
	{
		processLogItem(mediaWiki, logItem);
	}

	protected boolean handleEvent(ValidationEvent ve, ValidationEventLocator vel)
	{
		return processEvent(ve, vel);
	}

	// =========================================================================

	private void getDumpInputStream() throws IOException
	{
		InputStream decomp;
		if (dumpUri.endsWith(".bz2"))
		{
			decompress = true;

			compressedInputStream = new CountingInputStream(dumpInputStream);

			decomp = new BZip2CompressorInputStream(compressedInputStream, true);
		}
		else if (dumpUri.endsWith(".gz"))
		{
			decompress = true;

			compressedInputStream = new CountingInputStream(dumpInputStream);

			decomp = new GzipCompressorInputStream(compressedInputStream, true);
		}
		else
		{
			decompress = false;

			decomp = dumpInputStream;
		}

		decompressedInputStream = new CountingInputStream(
				new BufferedInputStream(decomp, BUFFER_SIZE));
	}

	private ExportSchemaVersion determineExportVersion(Charset encoding) throws IOException
	{
		QName root = readRootElementName(encoding);

		for (ExportSchemaVersion version : ExportSchemaVersion.values())
		{
			if (version.getMediaWikiNamespace().equals(root.getNamespaceURI()))
				return version;
		}

		throw new IllegalArgumentException(String.format(
				Locale.ROOT,
				"Unknown xmlns '%s' of root element '%s' in %s",
				root.getNamespaceURI(),
				root.getLocalPart(),
				dumpUri));
	}

	/**
	 * Parses the dump up to its root element and rewinds the stream to the
	 * beginning. The parser may read at most {@link #MAX_HEADER_SIZE} bytes,
	 * otherwise the mark would be invalidated.
	 */
	private QName readRootElementName(Charset encoding) throws IOException
	{
		decompressedInputStream.mark(1);
		int first = decompressedInputStream.read();
		decompressedInputStream.reset();

		if (first == -1)
			throw new IllegalArgumentException("Dump is empty: " + dumpUri);

		decompressedInputStream.mark(MAX_HEADER_SIZE);

		HeaderInputStream header = new HeaderInputStream(decompressedInputStream, MAX_HEADER_SIZE);

		QName root = null;
		try
		{
			XMLStreamReader reader = createXmlStreamReader(header, encoding);
			try
			{
				// Unlike nextTag() this also steps over a document type declaration
				while ((root == null) && reader.hasNext())
				{
					if (reader.next() == XMLStreamConstants.START_ELEMENT)
						root = reader.getName();
				}
			}
			finally
			{
				reader.close();
			}
		}
		catch (XMLStreamException e)
		{
			throw new IllegalArgumentException(getNoRootElementMessage(header), e);
		}

		if (root == null)
			throw new IllegalArgumentException(getNoRootElementMessage(header));

		decompressedInputStream.reset();

		return root;
	}

	private String getNoRootElementMessage(HeaderInputStream header)
	{
		if (header.isExhausted())
			return "Cannot find the root element within the first " + MAX_HEADER_SIZE + " bytes of " + dumpUri;
		else
			return "Cannot find the root element of " + dumpUri;
	}

	/**
	 * Creates a StAX factory that neither processes document type
	 * declarations nor resolves external entities.
	 */
	static XMLInputFactory createXmlInputFactory() throws FactoryConfigurationError
	{
		XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
		xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
		xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
		return xmlInputFactory;
	}

	/**
	 * The xerces UTF8Reader is broken. If the xerces XML parser is given an
	 * input stream, it will instantiate a reader for the encoding found in the
	 * XML file, a UTF8Reader in case of an UTF8 encoded XML file. Sadly, this
	 * UTF8Reader crashes for certain input (not sure why exactly).
	 * 
	 * On the other hand, when given a reader, which is forced to work with a
	 * certain encoding, the xerces XML parser does not have this freedom and
	 * will apparently process Wikipedia dumps just fine.
	 * 
	 * Therefore, in case you have trouble to parse a XML file, by specifying an
	 * encoding, you force the use of a Reader and can circumvent the crash.
	 */
	private static XMLStreamReader createXmlStreamReader(
			InputStream in,
			Charset encoding) throws FactoryConfigurationError, XMLStreamException
	{
		XMLInputFactory xmlInputFactory = createXmlInputFactory();

		if (encoding != null)
		{
			InputStreamReader isr = new InputStreamReader(in, encoding);
			return xmlInputFactory.createXMLStreamReader(isr);
		}
		else
		{
			return xmlInputFactory.createXMLStreamReader(in);
		}
	}

	private void setSchema(URL schemaUrl) throws SAXException, JAXBException
	{
		SchemaFactory sf = SchemaFactory.newInstance(
				javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);

		sf.setResourceResolver(new LSResourceResolverImplementation());

		Schema schema = sf.newSchema(schemaUrl);

		unmarshaller.setSchema(schema);

		unmarshaller.setEventHandler(
				new ValidationEventHandler()
				{
					public boolean handleEvent(ValidationEvent ve)
					{
						try
						{
							return DumpReader.this.handleEvent(ve, ve.getLocator());
						}
						catch (RuntimeException e)
						{
							throw e;
						}
						catch (Exception e)
						{
							throw new WrappedException(e);
						}
					}
				});
	}

	private void installCallbacks()
	{
		final DumpReaderLogItemListener pageListener = new DumpReaderLogItemListener()
		{
			@Override
			public void handlePage(Object mediaWiki, Object page)
			{
				try
				{
					DumpReader.this.handlePage(mediaWiki, page);
				}
				catch (RuntimeException e)
				{
					throw e;
				}
				catch (Exception e)
				{
					throw new WrappedException(e);
				}
			}

			@Override
			public boolean handleRevisionOrUploadOrLogitem(
					Object page,
					Object revision)
			{
				try
				{
					return DumpReader.this.handleRevision(page, revision);
				}
				catch (RuntimeException e)
				{
					throw e;
				}
				catch (Exception e)
				{
					throw new WrappedException(e);
				}
			}

			@Override
			public void handleLogItem(Object mediaWiki, Object logItem)
			{
				try
				{
					DumpReader.this.handleLogItem(mediaWiki, logItem);
				}
				catch (RuntimeException e)
				{
					throw e;
				}
				catch (Exception e)
				{
					throw new WrappedException(e);
				}
			}
		};

		unmarshaller.setListener(new Unmarshaller.Listener()
		{
			public void beforeUnmarshal(Object target, Object parent)
			{
				schemaVersion.setPageListener(target, pageListener);
			}

			public void afterUnmarshal(Object target, Object parent)
			{
				schemaVersion.setPageListener(target, pageListener);
			}
		});
	}

	private Unmarshaller createUnmarshaller(String contextPath) throws JAXBException
	{
		JAXBContext context = JAXBContext.newInstance(contextPath);

		return context.createUnmarshaller();
	}

	// =========================================================================

	/**
	 * Ends after a given number of bytes and leaves the underlying stream open.
	 */
	private static final class HeaderInputStream
			extends
				InputStream
	{
		private final InputStream in;

		private int remaining;

		public HeaderInputStream(InputStream in, int limit)
		{
			this.in = in;
			this.remaining = limit;
		}

		public boolean isExhausted()
		{
			return remaining == 0;
		}

		@Override
		public int read() throws IOException
		{
			if (remaining == 0)
				return -1;
			int read = in.read();
			if (read != -1)
				--remaining;
			return read;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException
		{
			if (len == 0)
				return 0;
			if (remaining == 0)
				return -1;
			int read = in.read(b, off, Math.min(len, remaining));
			if (read > 0)
				remaining -= read;
			return read;
		}

		@Override
		public void close()
		{
			// The dump's stream stays open
		}
	}
}
