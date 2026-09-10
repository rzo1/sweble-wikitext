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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.dump;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.page;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.revision;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.revisionFragment;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.utf8;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.xml.bind.JAXBElement;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.stream.StreamSource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sweble.wikitext.dumpreader.DumpTestSupport.CollectingDumpReader;
import org.sweble.wikitext.dumpreader.export_0_10.MediaWikiType;
import org.sweble.wikitext.dumpreader.model.DumpConverter;
import org.sweble.wikitext.dumpreader.model.Page;
import org.w3c.dom.ls.LSInput;

public class TestDumpReaderSecurity
{
	private static final ExportSchemaVersion V = ExportSchemaVersion.V0_10;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private String secret;

	private File secretFile;

	private File externalDtd;

	@Before
	public void setUp() throws Exception
	{
		secret = "SECRET-" + UUID.randomUUID();

		secretFile = tmp.newFile("secret.txt");
		Files.write(secretFile.toPath(), utf8(secret));

		externalDtd = tmp.newFile("external.dtd");
		Files.write(externalDtd.toPath(), utf8(
				"<!ENTITY xxe SYSTEM \"" + secretFile.toURI() + "\">"));
	}

	private String internalSubsetWithExternalEntity(String root)
	{
		return "<!DOCTYPE " + root + " [<!ENTITY xxe SYSTEM \"" + secretFile.toURI() + "\">]>\n";
	}

	private String externalParameterEntity(String root)
	{
		return "<!DOCTYPE " + root + " [<!ENTITY % ext SYSTEM \"" + externalDtd.toURI() + "\"> %ext;]>\n";
	}

	private static String dumpWithText(String prolog, String text)
	{
		return dump(V, prolog, page(V, "TITLE", revision(
				V,
				"<comment>COMMENT</comment>",
				"<text xml:space=\"preserve\">" + text + "</text>")));
	}

	// =========================================================================

	@Test
	public void testXmlInputFactoryIgnoresDtdsAndExternalEntities()
	{
		XMLInputFactory factory = DumpReader.createXmlInputFactory();

		assertEquals(Boolean.FALSE, factory.getProperty(XMLInputFactory.SUPPORT_DTD));
		assertEquals(Boolean.FALSE, factory.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES));
	}

	@Test
	public void testDumpReaderDoesNotExpandExternalEntities() throws Exception
	{
		assertDumpReaderDoesNotLeak(dumpWithText(internalSubsetWithExternalEntity("mediawiki"), "&xxe;"));
	}

	@Test
	public void testDumpReaderDoesNotLoadExternalDtds() throws Exception
	{
		assertDumpReaderDoesNotLeak(dumpWithText(externalParameterEntity("mediawiki"), "&xxe;"));
	}

	@Test
	public void testDumpUnmarshallerSourceDoesNotExpandExternalEntities() throws Exception
	{
		String xml = revisionFragment(
				V,
				"<comment>COMMENT</comment>",
				"<text xml:space=\"preserve\">&xxe;</text>");

		for (String prolog : new String[] {
				internalSubsetWithExternalEntity("revision"),
				externalParameterEntity("revision") })
		{
			for (boolean useSchema : new boolean[] { false, true })
			{
				String text = null;
				try
				{
					DumpUnmarshaller unmarshaller = new DumpUnmarshaller(V, useSchema);
					text = unmarshaller.unmarshalToRevision(
							new StreamSource(new StringReader(prolog + xml))).getText();
				}
				catch (Exception e)
				{
					assertNoSecret(e);
				}
				assertNoSecret(text);
			}
		}
	}

	@Test
	public void testDumpUnmarshallerStreamAndFileDoNotExpandExternalEntities() throws Exception
	{
		String xml = dumpWithText(internalSubsetWithExternalEntity("mediawiki"), "&xxe;");
		File file = tmp.newFile("dump.xml");
		Files.write(file.toPath(), utf8(xml));

		DumpUnmarshaller unmarshaller = new DumpUnmarshaller(V, false);

		String text = null;
		try
		{
			InputStream in = new ByteArrayInputStream(utf8(xml));
			text = textOf(unmarshaller.unmarshal(in));
		}
		catch (Exception e)
		{
			assertNoSecret(e);
		}
		assertNoSecret(text);

		text = null;
		try
		{
			text = textOf(unmarshaller.unmarshal(file));
		}
		catch (Exception e)
		{
			assertNoSecret(e);
		}
		assertNoSecret(text);
	}

	// =========================================================================

	@Test
	public void testResolverResolvesHttpAndHttpsSchemaLocations() throws Exception
	{
		LSResourceResolverImplementation resolver = new LSResourceResolverImplementation();

		for (String scheme : new String[] { "http", "https" })
		{
			for (String systemId : new String[] {
					scheme + "://www.mediawiki.org/xml/export-0.10.xsd",
					scheme + "://www.mediawiki.org/xml/export-0.6-fixed.xsd",
					scheme + "://www.w3.org/2001/xml.xsd" })
			{
				LSInput input = resolver.resolveResource(
						XMLConstants.W3C_XML_SCHEMA_NS_URI,
						null,
						null,
						systemId,
						null);

				assertNotNull(systemId, input);
				InputStream in = input.getByteStream();
				assertNotNull(systemId, in);
				in.close();
			}
		}
	}

	@Test
	public void testResolverRefusesUnknownResources() throws Exception
	{
		LSResourceResolverImplementation resolver = new LSResourceResolverImplementation();

		for (String systemId : new String[] {
				"http://example.org/evil.xsd",
				"https://www.mediawiki.org/xml/export-9.99.xsd",
				secretFile.toURI().toString() })
		{
			try
			{
				LSInput input = resolver.resolveResource(
						XMLConstants.W3C_XML_SCHEMA_NS_URI,
						null,
						null,
						systemId,
						null);
				fail("Resolved " + systemId + " to " + input);
			}
			catch (RuntimeException e)
			{
				// Expected
			}
		}
	}

	// =========================================================================

	private void assertDumpReaderDoesNotLeak(String xml) throws Exception
	{
		for (boolean useSchema : new boolean[] { false, true })
		{
			final List<String> texts = new ArrayList<String>();
			try
			{
				CollectingDumpReader reader = new CollectingDumpReader(
						new ByteArrayInputStream(utf8(xml)),
						StandardCharsets.UTF_8,
						"dump.xml",
						useSchema);
				reader.unmarshal();
				for (Page p : reader.pages)
					texts.add(p.getRevisions().get(0).getText());
			}
			catch (Exception e)
			{
				assertNoSecret(e);
			}
			assertNoSecret(texts.toString());
		}
	}

	private static String textOf(Object unmarshalled)
	{
		MediaWikiType mw = (MediaWikiType) ((JAXBElement<?>) unmarshalled).getValue();
		return new DumpConverter().convertPage(mw.getPage().get(0)).getRevisions().get(0).getText();
	}

	private void assertNoSecret(String text)
	{
		if (text != null)
			assertFalse("External entity was expanded: " + text, text.contains(secret));
	}

	private void assertNoSecret(Throwable e)
	{
		for (Throwable t = e; t != null; t = t.getCause())
			assertNoSecret(String.valueOf(t.getMessage()));
	}
}
