/**
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

package org.sweble.wikitext.engine.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Fetching the siteinfo with {@link LanguageConfigGenerator#getXMLFromUrl(String)}
 * must neither process DOCTYPEs nor external entities, must only accept the
 * http, https and file schemes and has to give up on responses that never
 * arrive or never end. Only local files and sockets are used.
 */
public class LanguageConfigGeneratorSecurityTest
{
	private static final String SECRET = "sweble-secret-4711";

	private static final String SITE_INFO =
			"<?xml version=\"1.0\"?>\n"
					+ "<api><query><general sitename=\"de wiki\"/></query></api>";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	// =========================================================================
	// == DOCTYPE and external entities

	@Test
	public void testExternalGeneralEntityIsNotResolved() throws Exception
	{
		File secret = writeFile("secret.txt", SECRET);
		File siteInfo = writeFile("siteinfo.xml",
				"<?xml version=\"1.0\"?>\n"
						+ "<!DOCTYPE api [<!ENTITY xxe SYSTEM \"" + secret.toURI() + "\">]>\n"
						+ "<api><query><general sitename=\"de wiki\">&xxe;</general></query></api>");

		assertDoctypeRejected(siteInfo.toURI().toString());
	}

	@Test
	public void testExternalParameterEntityIsNotResolved() throws Exception
	{
		File secret = writeFile("secret.txt", SECRET);
		File dtd = writeFile("entities.dtd", "<!ENTITY xxe SYSTEM \"" + secret.toURI() + "\">");
		File siteInfo = writeFile("siteinfo.xml",
				"<?xml version=\"1.0\"?>\n"
						+ "<!DOCTYPE api [<!ENTITY % entities SYSTEM \"" + dtd.toURI() + "\"> %entities;]>\n"
						+ "<api><query><general sitename=\"de wiki\">&xxe;</general></query></api>");

		assertDoctypeRejected(siteInfo.toURI().toString());
	}

	@Test
	public void testExternalDtdIsNotLoaded() throws Exception
	{
		File secret = writeFile("secret.txt", SECRET);
		File dtd = writeFile("siteinfo.dtd", "<!ENTITY xxe SYSTEM \"" + secret.toURI() + "\">");
		File siteInfo = writeFile("siteinfo.xml",
				"<?xml version=\"1.0\"?>\n"
						+ "<!DOCTYPE api SYSTEM \"" + dtd.toURI() + "\">\n"
						+ "<api><query><general sitename=\"de wiki\">&xxe;</general></query></api>");

		assertDoctypeRejected(siteInfo.toURI().toString());
	}

	// =========================================================================
	// == URL schemes

	@Test
	public void testFileUrlIsAccepted() throws Exception
	{
		File siteInfo = writeFile("siteinfo.xml", SITE_INFO);

		Document document = LanguageConfigGenerator.getXMLFromUrl(siteInfo.toURI().toString());
		assertEquals("de wiki", getSiteName(document));
	}

	@Test
	public void testHttpUrlIsAccepted() throws Exception
	{
		try (LocalHttpServer server = new LocalHttpServer(
				out -> out.write(SITE_INFO.getBytes(StandardCharsets.UTF_8))))
		{
			Document document = LanguageConfigGenerator.getXMLFromUrl(server.getUrl());
			assertEquals("de wiki", getSiteName(document));

			String request = server.getRequests().get(0);
			assertTrue(request, request.startsWith("GET /w/api.php?"));
			assertTrue(request, request.contains("User-Agent: "));
		}
	}

	@Test
	public void testJarUrlIsRejected() throws Exception
	{
		File jar = tmp.newFile("siteinfo.jar");
		try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar)))
		{
			out.putNextEntry(new JarEntry("siteinfo.xml"));
			out.write(SITE_INFO.getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}

		assertSchemeRejected("jar:" + jar.toURI() + "!/siteinfo.xml");
	}

	@Test
	public void testFtpUrlIsRejected() throws Exception
	{
		// Nothing listens on port 1, the URL has to be rejected before connecting
		assertSchemeRejected("ftp://127.0.0.1:1/siteinfo.xml");
	}

	// =========================================================================
	// == HTTP redirects

	@Test
	public void testRedirectOnSameHostIsFollowed() throws Exception
	{
		try (LocalHttpServer server = new LocalHttpServer((request, out) -> {
			if (request.startsWith("GET /w/api.php?"))
				writeRedirect(out, "/moved/api.php");
			else
				writeOk(out, SITE_INFO);
		}))
		{
			Document document = LanguageConfigGenerator.getXMLFromUrl(server.getUrl());
			assertEquals("de wiki", getSiteName(document));

			assertEquals(2, server.getRequests().size());
			assertTrue(server.getRequests().get(1), server.getRequests().get(1).startsWith("GET /moved/api.php "));
		}
	}

	@Test
	public void testRedirectToOtherHostIsRejected() throws Exception
	{
		try (LocalHttpServer server = new LocalHttpServer())
		{
			// Same address and port, but another host name
			server.setResponse((request, out) -> writeRedirect(out,
					"http://localhost:" + server.getPort() + "/w/api.php"));

			assertRedirectRejected(server.getUrl());
			assertEquals(1, server.getRequests().size());
		}
	}

	@Test
	public void testRedirectToOtherPortIsRejected() throws Exception
	{
		try (LocalHttpServer target = new LocalHttpServer((request, out) -> writeOk(out, SITE_INFO));
				LocalHttpServer server = new LocalHttpServer((request, out) -> writeRedirect(out, target.getUrl())))
		{
			assertRedirectRejected(server.getUrl());
			assertTrue(target.getRequests().isEmpty());
		}
	}

	@Test
	public void testRedirectToOtherSchemeIsRejected() throws Exception
	{
		File siteInfo = writeFile("siteinfo.xml", SITE_INFO);

		try (LocalHttpServer server = new LocalHttpServer((request, out) -> writeRedirect(out,
				siteInfo.toURI().toString())))
		{
			assertRedirectRejected(server.getUrl());
		}

		try (LocalHttpServer server = new LocalHttpServer())
		{
			server.setResponse((request, out) -> writeRedirect(out,
					"https://127.0.0.1:" + server.getPort() + "/w/api.php"));

			assertRedirectRejected(server.getUrl());
			assertEquals(1, server.getRequests().size());
		}
	}

	@Test
	public void testEndlessRedirectsAreRejected() throws Exception
	{
		try (LocalHttpServer server = new LocalHttpServer((request, out) -> writeRedirect(out, "/w/api.php?again")))
		{
			assertRedirectRejected(server.getUrl());
			assertTrue(server.getRequests().toString(), server.getRequests().size() <= 5);
		}
	}

	// =========================================================================
	// == Timeouts and size limit

	@Test(timeout = 10000)
	public void testUnansweredRequestTimesOut() throws Exception
	{
		// The request ends up in the backlog of the socket and is never answered
		try (LocalHttpServer server = new LocalHttpServer((ResponseBody) null))
		{
			try
			{
				LanguageConfigGenerator.getXMLFromUrl(
						server.getUrl(),
						LanguageConfigGenerator.CONNECT_TIMEOUT_MILLIS,
						500,
						LanguageConfigGenerator.MAX_RESPONSE_BYTES);
				fail("Got a response from a server that never answers");
			}
			catch (SocketTimeoutException e)
			{
				// Expected
			}
		}
	}

	@Test(timeout = 60000)
	public void testOversizedResponseIsRejected() throws Exception
	{
		// Twice the limit
		final long padding = 2 * LanguageConfigGenerator.MAX_RESPONSE_BYTES;
		try (LocalHttpServer server = new LocalHttpServer(out -> writePaddedSiteInfo(out, padding)))
		{
			Document document;
			try
			{
				document = LanguageConfigGenerator.getXMLFromUrl(server.getUrl());
			}
			catch (IOException e)
			{
				assertTrue(e.getMessage(), e.getMessage().contains("exceeds"));
				return;
			}
			fail("Read a response with " + document.getDocumentElement().getTextContent().length()
					+ " characters of padding");
		}
	}

	// =========================================================================

	private File writeFile(String name, String content) throws IOException
	{
		File file = new File(tmp.getRoot(), name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	private static String getSiteName(Document document)
	{
		return ((Element) document.getElementsByTagName("general").item(0)).getAttribute("sitename");
	}

	private static void assertDoctypeRejected(String url) throws Exception
	{
		Document document;
		try
		{
			document = LanguageConfigGenerator.getXMLFromUrl(url);
		}
		catch (SAXException e)
		{
			assertFalse(e.getMessage(), String.valueOf(e.getMessage()).contains(SECRET));
			return;
		}
		fail("The DOCTYPE was processed, the document contains `"
				+ document.getDocumentElement().getTextContent() + "'");
	}

	private static void assertSchemeRejected(String url) throws Exception
	{
		try
		{
			LanguageConfigGenerator.getXMLFromUrl(url);
			fail("Accepted `" + url + "'");
		}
		catch (MalformedURLException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("scheme"));
		}
	}

	private static void assertRedirectRejected(String url) throws Exception
	{
		Document document;
		try
		{
			document = LanguageConfigGenerator.getXMLFromUrl(url);
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage(), String.valueOf(e.getMessage()).contains("redirect"));
			return;
		}
		fail("Followed the redirect, got `" + document.getDocumentElement().getTextContent() + "'");
	}

	private static void writeOk(OutputStream out, String body) throws IOException
	{
		writeOk(out, o -> o.write(body.getBytes(StandardCharsets.UTF_8)));
	}

	private static void writeOk(OutputStream out, ResponseBody body) throws IOException
	{
		out.write(("HTTP/1.1 200 OK\r\n"
				+ "Content-Type: text/xml; charset=utf-8\r\n"
				+ "Connection: close\r\n"
				+ "\r\n").getBytes(StandardCharsets.US_ASCII));
		body.write(out);
	}

	private static void writeRedirect(OutputStream out, String location) throws IOException
	{
		out.write(("HTTP/1.1 302 Found\r\n"
				+ "Location: " + location + "\r\n"
				+ "Content-Length: 0\r\n"
				+ "Connection: close\r\n"
				+ "\r\n").getBytes(StandardCharsets.US_ASCII));
	}

	private static void writePaddedSiteInfo(OutputStream out, long padding) throws IOException
	{
		out.write("<api><query><general sitename=\"de wiki\"/></query><padding>"
				.getBytes(StandardCharsets.UTF_8));

		byte[] chunk = new byte[64 * 1024];
		Arrays.fill(chunk, (byte) 'x');
		for (long written = 0; written < padding; written += chunk.length)
			out.write(chunk);

		out.write("</padding></api>".getBytes(StandardCharsets.UTF_8));
	}

	// =========================================================================

	private interface ResponseBody
	{
		void write(OutputStream out) throws IOException;
	}

	/**
	 * Writes the complete HTTP response (status line, headers and body) to a
	 * request.
	 */
	private interface Response
	{
		void write(String requestHead, OutputStream out) throws IOException;
	}

	/**
	 * A minimal HTTP server on the loopback interface. Without a response body
	 * it never accepts a connection, so requests are never answered.
	 */
	private static final class LocalHttpServer
			implements
				Closeable
	{
		private final ServerSocket serverSocket;

		private final List<String> requests = new CopyOnWriteArrayList<String>();

		private volatile Response response;

		/**
		 * Answers with the response that is set afterwards.
		 */
		public LocalHttpServer() throws IOException
		{
			this((Response) (request, out) -> {
				throw new IOException("No response set");
			});
		}

		public LocalHttpServer(ResponseBody body) throws IOException
		{
			this((body == null) ? null : (Response) (request, out) -> writeOk(out, body));
		}

		public LocalHttpServer(Response response) throws IOException
		{
			serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
			this.response = response;
			if (response != null)
			{
				Thread thread = new Thread(this::serve, "local-http-server");
				thread.setDaemon(true);
				thread.start();
			}
		}

		public void setResponse(Response response)
		{
			this.response = response;
		}

		public int getPort()
		{
			return serverSocket.getLocalPort();
		}

		public String getUrl()
		{
			return "http://127.0.0.1:" + serverSocket.getLocalPort()
					+ "/w/api.php?action=query&meta=siteinfo&siprop=general&format=xml";
		}

		public List<String> getRequests()
		{
			return requests;
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
					String requestHead = readRequestHead(socket.getInputStream());
					requests.add(requestHead);

					OutputStream out = socket.getOutputStream();
					response.write(requestHead, out);
					out.flush();
				}
				catch (IOException e)
				{
					// The server was closed or the client gave up
				}
			}
		}

		private static String readRequestHead(InputStream in) throws IOException
		{
			ByteArrayOutputStream head = new ByteArrayOutputStream();
			int last = 0;
			int b;
			while ((b = in.read()) != -1)
			{
				head.write(b);
				last = (last << 8) | b;
				if (last == 0x0D0A0D0A)
					break;
			}
			return new String(head.toByteArray(), StandardCharsets.ISO_8859_1);
		}
	}
}
