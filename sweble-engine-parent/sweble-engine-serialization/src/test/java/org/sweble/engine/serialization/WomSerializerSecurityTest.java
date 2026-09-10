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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sweble.engine.serialization.CompressorFactory.CompressionFormat;
import org.sweble.engine.serialization.WomSerializer.SerializationFormat;
import org.sweble.wom3.swcadapter.utils.WtWom3Toolbox;
import org.w3c.dom.Document;

/**
 * Deserializing must neither process DOCTYPEs nor external entities in XML,
 * must only accept the classes of the WOM in the Java format and must restore
 * compressed JSON. Only local files and sockets are used.
 */
public class WomSerializerSecurityTest
{
	private static final String SECRET = "sweble-secret-4711";

	private static final String TITLE = "Placeholder";

	/**
	 * Replaced by an entity reference in the XML of the WOM.
	 */
	private static final String CONTENT = "italic";

	/**
	 * Produces WOM and SWC adapter nodes, enums, URLs and time stamps.
	 */
	private static final String WIKITEXT = ""
			+ "= Heading =\n"
			+ "'''bold''' ''" + CONTENT + "'' [http://example.org/x?y=1 external] [[Target|internal]] [[Category:C]]\n"
			+ "{| class=\"wikitable\" style=\"color:red\"\n|+ caption\n! h1 !! h2\n|-\n| a || b\n|}\n"
			+ "* item\n# item\n; term : definition\n"
			+ "<div style=\"width:10px\" title=\"t\">div</div>\n"
			+ "<font color=\"#ff0000\" size=\"3\">font</font>\n"
			+ "[[File:X.png|thumb|100px|left|alt=a|caption]]\n----\n"
			+ "<br/>{{Template|a=b}} <nowiki>''</nowiki> &amp; ~~~~\n"
			+ "#REDIRECT [[X]]\n"
			+ "<span id=\"s\">x</span><ref name=\"r\">r</ref><references/>\n"
			+ "<blockquote>q</blockquote><del>d</del><ins>i</ins>\n"
			+ "<table border=\"1\" width=\"50%\"><tr><td colspan=\"2\">c</td></tr></table>\n";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final WomSerializer serializer = new WomSerializer();

	// =========================================================================
	// == XML

	@Test
	public void testXmlExternalEntityIsNotResolved() throws Exception
	{
		File secret = new File(tmp.getRoot(), "secret.txt");
		Files.write(secret.toPath(), SECRET.getBytes(StandardCharsets.UTF_8));

		byte[] xml = getWomXml(
				"<!DOCTYPE article [<!ENTITY xxe SYSTEM \"" + secret.toURI() + "\">]>",
				TITLE,
				"&xxe;");

		assertXmlRejected(xml, null);
	}

	@Test
	public void testXmlExternalEntityIsNotFetched() throws Exception
	{
		try (ConnectionCounter server = new ConnectionCounter())
		{
			byte[] xml = getWomXml(
					"<!DOCTYPE article [<!ENTITY xxe SYSTEM \"" + server.getUrl("entity.txt") + "\">]>",
					TITLE,
					"&xxe;");

			assertXmlRejected(xml, server);
		}
	}

	@Test
	public void testXmlExternalDtdIsNotLoaded() throws Exception
	{
		try (ConnectionCounter server = new ConnectionCounter())
		{
			byte[] xml = getWomXml(
					"<!DOCTYPE article SYSTEM \"" + server.getUrl("article.dtd") + "\">",
					TITLE,
					CONTENT);

			assertXmlRejected(xml, server);
		}
	}

	@Test
	public void testXmlExternalParameterEntityIsNotResolved() throws Exception
	{
		try (ConnectionCounter server = new ConnectionCounter())
		{
			byte[] xml = getWomXml(
					"<!DOCTYPE article [<!ENTITY % remote SYSTEM \"" + server.getUrl("remote.dtd") + "\"> %remote;]>",
					TITLE,
					CONTENT);

			assertXmlRejected(xml, server);
		}
	}

	@Test
	public void testXmlInternalEntitiesAreRejected() throws Exception
	{
		byte[] xml = getWomXml(
				"<!DOCTYPE article ["
						+ "<!ENTITY a \"lol\">"
						+ "<!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">"
						+ "<!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\">"
						+ "]>",
				"&c;",
				CONTENT);

		assertXmlRejected(xml, null);
	}

	@Test
	public void testXmlWithoutDoctypeIsAccepted() throws Exception
	{
		Document wom = createWom();
		String text = wom.getDocumentElement().getTextContent();
		assertTrue(text, text.contains(CONTENT));

		byte[] xml = serializer.serialize(wom, SerializationFormat.XML, false, false);
		Document restored = serializer.deserialize(xml, SerializationFormat.XML, false);
		assertEquals(TITLE, restored.getDocumentElement().getAttribute("title"));
		assertEquals(text, restored.getDocumentElement().getTextContent());

		byte[] compressed = serializer.serializeAndCompress(
				wom,
				CompressionFormat.BZIP2,
				SerializationFormat.XML,
				false,
				false);
		restored = serializer.decompressAndDeserialize(
				compressed,
				CompressionFormat.BZIP2,
				SerializationFormat.XML,
				false);
		assertEquals(TITLE, restored.getDocumentElement().getAttribute("title"));
		assertEquals(text, restored.getDocumentElement().getTextContent());
	}

	// =========================================================================
	// == Java serialization

	@Test
	public void testJavaRejectsForeignClass() throws Exception
	{
		ForeignPayload.READ.set(false);
		byte[] serialized = serializeJava(new ForeignPayload());

		assertJavaRejected(serialized, false);
		assertJavaRejected(serialized, true);
		assertFalse("ForeignPayload.readObject() was called", ForeignPayload.READ.get());
	}

	@Test
	public void testJavaRejectsJdkCollections() throws Exception
	{
		Map<String, String> map = new HashMap<String, String>();
		map.put("key", "value");
		byte[] serialized = serializeJava(map);

		assertJavaRejected(serialized, false);
		assertJavaRejected(serialized, true);
	}

	@Test
	public void testJavaRejectsHugeArrays() throws Exception
	{
		// Integer is allowed but not an array of more than 1,000,000 elements
		byte[] serialized = serializeJava(new Integer[2000000]);

		assertJavaRejected(serialized, false);
		assertJavaRejected(serialized, true);
	}

	@Test
	public void testJavaRoundTripOfWom() throws Exception
	{
		Document wom = createWom();
		String xml = toXml(wom);

		byte[] serialized = serializer.serialize(wom, SerializationFormat.JAVA, false, false);
		assertEquals(xml, toXml(serializer.deserialize(serialized, SerializationFormat.JAVA, false)));

		byte[] compressed = serializer.serializeAndCompress(
				wom,
				CompressionFormat.BZIP2,
				SerializationFormat.JAVA,
				false,
				false);
		Document restored = serializer.decompressAndDeserialize(
				compressed,
				CompressionFormat.BZIP2,
				SerializationFormat.JAVA,
				false);
		assertEquals(xml, toXml(restored));
	}

	// =========================================================================
	// == JSON

	@Test
	public void testJsonCompressedRoundTrip() throws Exception
	{
		Document wom = createWom();
		for (boolean compact : new boolean[] { false, true })
		{
			String json = new String(
					serializer.serialize(wom, SerializationFormat.JSON, compact, false),
					StandardCharsets.UTF_8);

			byte[] compressed = serializer.serializeAndCompress(
					wom,
					CompressionFormat.BZIP2,
					SerializationFormat.JSON,
					compact,
					false);
			Document restored = serializer.decompressAndDeserialize(
					compressed,
					CompressionFormat.BZIP2,
					SerializationFormat.JSON,
					compact);

			assertEquals(
					"compact=" + compact,
					json,
					new String(
							serializer.serialize(restored, SerializationFormat.JSON, compact, false),
							StandardCharsets.UTF_8));
		}
	}

	// =========================================================================

	private Document createWom() throws Exception
	{
		File file = new File(tmp.newFolder(), TITLE + ".wikitext");
		Files.write(file.toPath(), WIKITEXT.getBytes(StandardCharsets.UTF_8));
		return new WtWom3Toolbox().wmToWom(file, StandardCharsets.UTF_8.name()).womDoc;
	}

	private String toXml(Document document) throws Exception
	{
		return new String(
				serializer.serialize(document, SerializationFormat.XML, false, false),
				StandardCharsets.UTF_8);
	}

	/**
	 * Returns the XML of a WOM document with the given DOCTYPE, title and
	 * content in place of {@link #CONTENT}.
	 */
	private byte[] getWomXml(String doctype, String title, String content) throws Exception
	{
		String xml = toXml(createWom());

		String titleAttribute = "title=\"" + TITLE + "\"";
		String contentText = ">" + CONTENT + "<";
		int prologEnd = xml.indexOf("?>") + 2;
		assertTrue(xml, prologEnd > 1 && xml.contains(titleAttribute) && xml.contains(contentText));

		xml = xml.substring(0, prologEnd)
				+ doctype
				+ xml.substring(prologEnd)
						.replace(titleAttribute, "title=\"" + title + "\"")
						.replace(contentText, ">" + content + "<");
		return xml.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Deserializes the given XML directly and compressed. Both have to fail
	 * without contacting the given server.
	 */
	private void assertXmlRejected(byte[] xml, ConnectionCounter server) throws Exception
	{
		for (boolean compressed : new boolean[] { false, true })
		{
			Document document = null;
			Exception thrown = null;
			try
			{
				document = deserialize(xml, SerializationFormat.XML, compressed);
			}
			catch (Exception e)
			{
				thrown = e;
			}

			if (server != null)
				assertEquals("Requests to the local server", 0, server.getConnections());

			if (thrown == null)
			{
				fail("The DOCTYPE was processed, title: `"
						+ document.getDocumentElement().getAttribute("title")
						+ "', content: `" + document.getDocumentElement().getTextContent() + "'");
			}
			if (!(thrown instanceof DeserializationException))
				throw new AssertionError("Unexpected exception", thrown);
			assertFalse(String.valueOf(thrown.getMessage()).contains(SECRET));
		}
	}

	private void assertJavaRejected(byte[] serialized, boolean compressed) throws Exception
	{
		Document document;
		try
		{
			document = deserialize(serialized, SerializationFormat.JAVA, compressed);
		}
		catch (DeserializationException e)
		{
			assertTrue(String.valueOf(e.getCause()), e.getCause() instanceof InvalidClassException);
			return;
		}
		fail("Accepted " + document);
	}

	private Document deserialize(
			byte[] serialized,
			SerializationFormat format,
			boolean compressed) throws Exception
	{
		if (!compressed)
			return serializer.deserialize(serialized, format, false);

		byte[] data = serializer.compress(serialized, CompressionFormat.BZIP2);
		return serializer.decompressAndDeserialize(data, CompressionFormat.BZIP2, format, false);
	}

	private static byte[] serializeJava(Object object) throws IOException
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes))
		{
			out.writeObject(object);
		}
		return bytes.toByteArray();
	}

	// =========================================================================

	/**
	 * A class that is not part of the WOM and notices being deserialized.
	 */
	public static final class ForeignPayload
			implements
				Serializable
	{
		private static final long serialVersionUID = 1L;

		static final AtomicBoolean READ = new AtomicBoolean();

		private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
		{
			in.defaultReadObject();
			READ.set(true);
		}
	}

	/**
	 * Counts the connections to a local HTTP server that answers every
	 * request with an empty document.
	 */
	private static final class ConnectionCounter
			implements
				Closeable
	{
		private final ServerSocket serverSocket;

		private final AtomicInteger connections = new AtomicInteger();

		public ConnectionCounter() throws IOException
		{
			serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
			Thread thread = new Thread(this::serve, "connection-counter");
			thread.setDaemon(true);
			thread.start();
		}

		public String getUrl(String file)
		{
			return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/" + file;
		}

		public int getConnections()
		{
			return connections.get();
		}

		@Override
		public void close() throws IOException
		{
			serverSocket.close();
		}

		private void serve()
		{
			while (!serverSocket.isClosed())
			{
				try (Socket socket = serverSocket.accept())
				{
					connections.incrementAndGet();
					skipRequestHead(socket.getInputStream());
					socket.getOutputStream().write((""
							+ "HTTP/1.1 200 OK\r\n"
							+ "Content-Type: text/plain\r\n"
							+ "Content-Length: 0\r\n"
							+ "Connection: close\r\n"
							+ "\r\n").getBytes(StandardCharsets.US_ASCII));
				}
				catch (IOException e)
				{
					// The server was closed or the client gave up
				}
			}
		}

		private static void skipRequestHead(InputStream in) throws IOException
		{
			int last = 0;
			int b;
			while ((b = in.read()) != -1)
			{
				last = (last << 8) | b;
				if (last == 0x0D0A0D0A)
					break;
			}
		}
	}
}
