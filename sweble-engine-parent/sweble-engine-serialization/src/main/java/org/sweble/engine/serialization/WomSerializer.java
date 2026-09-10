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
package org.sweble.engine.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.IOUtils;
import org.apache.xerces.parsers.DOMParser;
import org.sweble.engine.serialization.CompressorFactory.CompressionFormat;
import org.sweble.wom3.serialization.Wom3JsonTypeAdapterBase;
import org.sweble.wom3.serialization.Wom3NodeCompactJsonTypeAdapter;
import org.sweble.wom3.serialization.Wom3NodeJsonTypeAdapter;
import org.sweble.wom3.util.SecureTransformerFactories;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class WomSerializer
{
	private static final Charset CHARSET = Charset.forName("UTF8");

	/**
	 * The filter used when deserializing the {@link SerializationFormat#JAVA}
	 * format. It only accepts the classes of the WOM and the JDK and Joda-Time
	 * classes a WOM refers to, and limits the nesting depth, the number of
	 * references, the length of arrays and the size of the stream. To accept
	 * additional classes, e.g. of a custom document implementation, prepend
	 * their patterns and pass the result to
	 * {@link #setJavaDeserializationFilter(ObjectInputFilter)}:
	 *
	 * <pre>
	 * ObjectInputFilter.Config.createFilter("com.example.wom.**;" + DEFAULT_JAVA_DESERIALIZATION_FILTER)
	 * </pre>
	 */
	public static final String DEFAULT_JAVA_DESERIALIZATION_FILTER = ""
			+ "maxdepth=10000;"
			+ "maxrefs=10000000;"
			+ "maxarray=1000000;"
			+ "maxbytes=268435456;"
			+ "org.sweble.wom3.**;"
			+ "java.lang.Object;"
			+ "java.lang.Enum;"
			+ "java.lang.Number;"
			+ "java.lang.Boolean;"
			+ "java.lang.Byte;"
			+ "java.lang.Character;"
			+ "java.lang.Short;"
			+ "java.lang.Integer;"
			+ "java.lang.Long;"
			+ "java.lang.Float;"
			+ "java.lang.Double;"
			+ "java.lang.String;"
			+ "java.util.ArrayList;"
			+ "java.net.URL;"
			+ "org.joda.time.DateTime;"
			+ "org.joda.time.DateTimeZone$Stub;"
			+ "org.joda.time.base.*;"
			+ "org.joda.time.chrono.*;"
			+ "org.joda.time.tz.*;"
			+ "!*";

	// =========================================================================

	public static enum SerializationFormat
	{
		JAVA,
		JSON,
		XML
	}

	// =========================================================================

	private Transformer prettyXmlTransformer;

	private Transformer normalXmlTransformer;

	private String documentImplClassName = org.sweble.wom3.impl.DocumentImpl.class.getName();

	private ObjectInputFilter javaDeserializationFilter =
			ObjectInputFilter.Config.createFilter(DEFAULT_JAVA_DESERIALIZATION_FILTER);

	// =========================================================================

	public WomSerializer()
	{
	}

	// =========================================================================

	public String getDocumentImplClassName()
	{
		return documentImplClassName;
	}

	public void setDocumentImplClassName(String documentImplClassName)
	{
		this.documentImplClassName = documentImplClassName;
	}

	public ObjectInputFilter getJavaDeserializationFilter()
	{
		return javaDeserializationFilter;
	}

	/**
	 * Sets the filter used when deserializing the
	 * {@link SerializationFormat#JAVA} format, see
	 * {@link #DEFAULT_JAVA_DESERIALIZATION_FILTER}.
	 */
	public void setJavaDeserializationFilter(ObjectInputFilter javaDeserializationFilter)
	{
		if (javaDeserializationFilter == null)
			throw new IllegalArgumentException("The deserialization filter must not be null");
		this.javaDeserializationFilter = javaDeserializationFilter;
	}

	// =========================================================================

	public byte[] serialize(
			Document wom,
			SerializationFormat serializationFormat,
			boolean compact,
			boolean pretty) throws IOException, SerializationException
	{
		final byte[] result;
		switch (serializationFormat)
		{
			case JAVA:
			{
				ByteArrayOutputStream baos = null;
				ObjectOutputStream oos = null;
				try
				{
					baos = new ByteArrayOutputStream();
					oos = new ObjectOutputStream(baos);
					oos.writeObject(wom);
				}
				finally
				{
					IOUtils.closeQuietly(baos);
					IOUtils.closeQuietly(oos);
				}
				result = baos.toByteArray();
				break;
			}
			case JSON:
			{
				result = getGson(createDocumentForSerialization(), compact, pretty)
						.toJson(wom)
						.getBytes(CHARSET);
				break;
			}
			case XML:
			{
				ByteArrayOutputStream baos = null;
				try
				{
					baos = new ByteArrayOutputStream();
					getXmlTransformer(pretty).transform(
							new DOMSource(wom),
							new StreamResult(baos));
				}
				catch (TransformerException e)
				{
					throw new SerializationException(e);
				}
				finally
				{
					IOUtils.closeQuietly(baos);
				}
				result = baos.toByteArray();
				break;
			}
			default:
				throw new IllegalArgumentException();
		}
		return result;
	}

	public Document deserialize(
			byte[] serialized,
			SerializationFormat serializationFormat,
			boolean compact) throws IOException, DeserializationException
	{
		Document result;
		switch (serializationFormat)
		{
			case JAVA:
			{
				result = readJava(new ByteArrayInputStream(serialized));
				break;
			}
			case JSON:
			{
				result = readJson(new StringReader(new String(serialized, CHARSET)), compact);
				break;
			}
			case XML:
			{
				ByteArrayInputStream bais = null;
				try
				{
					bais = new ByteArrayInputStream(serialized);
					InputSource is = new InputSource(bais);
					DOMParser parser = getXmlParser();
					parser.parse(is);
					result = parser.getDocument();
				}
				catch (SAXException e)
				{
					throw new DeserializationException(e);
				}
				finally
				{
					IOUtils.closeQuietly(bais);
				}
				break;
			}
			default:
				throw new IllegalArgumentException();
		}
		return result;
	}

	public byte[] compress(
			byte[] serialized,
			CompressionFormat compressionFormat) throws IOException, CompressionException
	{
		ByteArrayOutputStream out = null;
		OutputStream cos = null;
		InputStream in = null;
		try
		{
			out = new ByteArrayOutputStream();
			cos = CompressorFactory.createCompressorOutputStream(compressionFormat, out);
			in = new ByteArrayInputStream(serialized);
			IOUtils.copy(in, cos);
		}
		catch (CompressorFactoryException e)
		{
			throw new CompressionException(e);
		}
		finally
		{
			IOUtils.closeQuietly(in);
			IOUtils.closeQuietly(cos);
			IOUtils.closeQuietly(out);
		}
		return out.toByteArray();
	}

	public byte[] decompress(
			byte[] compressed,
			CompressionFormat compressionFormat) throws IOException, CompressionException
	{
		InputStream in = null;
		InputStream cin = null;
		ByteArrayOutputStream out = null;
		try
		{
			in = new ByteArrayInputStream(compressed);
			cin = CompressorFactory.createCompressorInputStream(compressionFormat, in);
			out = new ByteArrayOutputStream(compressed.length * 2);
			IOUtils.copy(cin, out);
		}
		catch (CompressorFactoryException e)
		{
			throw new CompressionException(e);
		}
		finally
		{
			IOUtils.closeQuietly(out);
			IOUtils.closeQuietly(cin);
			IOUtils.closeQuietly(in);
		}
		return out.toByteArray();
	}

	public byte[] serializeAndCompress(
			Document wom,
			CompressionFormat compressionFormat,
			SerializationFormat serializationFormat,
			boolean compact,
			boolean pretty) throws IOException, CompressionException, SerializationException
	{
		ByteArrayOutputStream out = null;
		OutputStream cos = null;
		try
		{
			out = new ByteArrayOutputStream();
			cos = CompressorFactory.createCompressorOutputStream(compressionFormat, out);
			switch (serializationFormat)
			{
				case JAVA:
				{
					ObjectOutputStream oos = null;
					try
					{
						oos = new ObjectOutputStream(cos);
						oos.writeObject(wom);
					}
					finally
					{
						IOUtils.closeQuietly(oos);
					}
					break;
				}
				case JSON:
				{
					OutputStreamWriter osw = null;
					try
					{
						osw = new OutputStreamWriter(cos, CHARSET);
						Gson gson = getGson(createDocumentForSerialization(), compact, pretty);
						gson.toJson(wom, osw);
					}
					finally
					{
						IOUtils.closeQuietly(osw);
					}
					break;
				}
				case XML:
				{
					getXmlTransformer(pretty).transform(
							new DOMSource(wom),
							new StreamResult(cos));
					break;
				}
				default:
					throw new IllegalArgumentException();
			}
		}
		catch (CompressorFactoryException e)
		{
			throw new CompressionException(e);
		}
		catch (TransformerException e)
		{
			throw new SerializationException(e);
		}
		finally
		{
			IOUtils.closeQuietly(cos);
			IOUtils.closeQuietly(out);
		}
		return out.toByteArray();
	}

	public Document decompressAndDeserialize(
			byte[] compressed,
			CompressionFormat compressionFormat,
			SerializationFormat serializationFormat,
			boolean compact) throws IOException, DeserializationException, CompressionException
	{
		ByteArrayInputStream in = null;
		InputStream cin = null;
		try
		{
			in = new ByteArrayInputStream(compressed);
			cin = CompressorFactory.createCompressorInputStream(compressionFormat, in);

			Document result;
			switch (serializationFormat)
			{
				case JAVA:
				{
					result = readJava(cin);
					break;
				}
				case JSON:
				{
					result = readJson(new InputStreamReader(cin, CHARSET), compact);
					break;
				}
				case XML:
				{
					InputSource is = new InputSource(cin);
					DOMParser parser = getXmlParser();
					parser.parse(is);
					result = parser.getDocument();
					break;
				}
				default:
					throw new IllegalArgumentException();
			}
			return result;
		}
		catch (CompressorFactoryException e)
		{
			throw new CompressionException(e);
		}
		catch (SAXException e)
		{
			throw new DeserializationException(e);
		}
		finally
		{
			IOUtils.closeQuietly(cin);
			IOUtils.closeQuietly(in);
		}
	}

	// =========================================================================

	private Transformer getNormalXmlTransformer() throws TransformerConfigurationException
	{
		if (normalXmlTransformer == null)
		{
			TransformerFactory tf = SecureTransformerFactories.newInstance();

			normalXmlTransformer = tf.newTransformer();
		}
		return normalXmlTransformer;
	}

	private Transformer getPrettyXmlTransformer() throws TransformerConfigurationException
	{
		if (prettyXmlTransformer == null)
		{
			TransformerFactory tf = SecureTransformerFactories.newInstance(
					"net.sf.saxon.TransformerFactoryImpl");

			InputStream xslt = getClass().getResourceAsStream("/org/sweble/wom3/pretty-print.xslt");

			prettyXmlTransformer = tf.newTransformer(new StreamSource(xslt));
		}
		return prettyXmlTransformer;
	}

	private Transformer getXmlTransformer(boolean pretty) throws TransformerConfigurationException
	{
		return pretty ?
				getPrettyXmlTransformer() :
				getNormalXmlTransformer();
	}

	/**
	 * Creates a parser that rejects DOCTYPEs and neither loads external DTDs
	 * nor resolves external entities.
	 */
	private DOMParser getXmlParser() throws SAXNotRecognizedException, SAXNotSupportedException
	{
		DOMParser parser = new DOMParser();
		parser.setProperty(
				"http://apache.org/xml/properties/" + "dom/document-class-name",
				documentImplClassName);
		parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		parser.setFeature("http://xml.org/sax/features/external-general-entities", false);
		parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		parser.setProperty(
				"http://apache.org/xml/properties/security-manager",
				new org.apache.xerces.util.SecurityManager());
		return parser;
	}

	// =========================================================================

	/**
	 * Reads a document in the {@link SerializationFormat#JAVA} format. Only
	 * the classes accepted by the deserialization filter are instantiated. A
	 * process-wide filter (jdk.serialFilter) is consulted as well.
	 */
	private Document readJava(InputStream in) throws IOException, DeserializationException
	{
		ObjectInputStream ois = null;
		try
		{
			ois = new ObjectInputStream(in);
			ois.setObjectInputFilter(getEffectiveJavaDeserializationFilter());

			Object result = ois.readObject();
			if (!(result instanceof Document))
			{
				throw new DeserializationException("Expected a " + Document.class.getName() + " but got "
						+ ((result == null) ? "null" : result.getClass().getName()));
			}
			return (Document) result;
		}
		catch (InvalidClassException | ClassNotFoundException e)
		{
			throw new DeserializationException(e);
		}
		finally
		{
			IOUtils.closeQuietly(ois);
		}
	}

	private ObjectInputFilter getEffectiveJavaDeserializationFilter()
	{
		final ObjectInputFilter filter = javaDeserializationFilter;
		final ObjectInputFilter processWideFilter = ObjectInputFilter.Config.getSerialFilter();
		if (processWideFilter == null)
			return filter;

		return info -> {
			ObjectInputFilter.Status status = filter.checkInput(info);
			if (status == ObjectInputFilter.Status.REJECTED)
				return status;
			ObjectInputFilter.Status processWideStatus = processWideFilter.checkInput(info);
			return (processWideStatus == ObjectInputFilter.Status.REJECTED) ? processWideStatus : status;
		};
	}

	/**
	 * Reads a document in the {@link SerializationFormat#JSON} format. The
	 * type adapters return the document element in a document fragment.
	 */
	private Document readJson(Reader reader, boolean compact) throws DeserializationException
	{
		Node node = getGson(createDocumentForDeserialization(), compact, false)
				.fromJson(reader, Node.class);
		if (!(node instanceof DocumentFragment))
		{
			throw new DeserializationException("Expected a document fragment but got "
					+ ((node == null) ? "null" : node.getClass().getName()));
		}

		DocumentFragment fragment = (DocumentFragment) node;
		Node firstChild = fragment.getFirstChild();
		if (firstChild == null)
			throw new DeserializationException("The serialized document is empty");

		if (firstChild.getParentNode() != null)
			firstChild.getParentNode().removeChild(firstChild);

		Document doc = (Document) fragment.getOwnerDocument();
		if (doc.getDocumentElement() != null)
			doc.removeChild(doc.getDocumentElement());
		doc.appendChild(firstChild);

		return doc;
	}

	// =========================================================================

	private Gson getGson(Document doc, boolean compact, boolean pretty)
	{
		GsonBuilder builder = new GsonBuilder();

		Wom3JsonTypeAdapterBase typeAdapter = compact ?
				(new Wom3NodeCompactJsonTypeAdapter()) :
				(new Wom3NodeJsonTypeAdapter());

		typeAdapter.setDoc(doc);

		builder.registerTypeHierarchyAdapter(Node.class, typeAdapter);

		builder.serializeNulls();

		if (pretty)
			builder.setPrettyPrinting();

		return builder.create();
	}

	private Document createDocumentForDeserialization() throws DeserializationException
	{
		try
		{
			return (Document) Class.forName(documentImplClassName).getDeclaredConstructor().newInstance();
		}
		catch (InstantiationException | InvocationTargetException | ClassNotFoundException | IllegalAccessException |
               NoSuchMethodException e)
		{
			throw new DeserializationException(e);
		}
    }

	private Document createDocumentForSerialization() throws SerializationException
	{
		try
		{
			return (Document) Class.forName(documentImplClassName).getDeclaredConstructor().newInstance();
		}
		catch (InstantiationException | InvocationTargetException | ClassNotFoundException | IllegalAccessException |
			   NoSuchMethodException e)
		{
			throw new SerializationException(e);
		}
	}
}
