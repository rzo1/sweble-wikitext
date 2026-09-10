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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.dump;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.page;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.read;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.utf8;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.sweble.wikitext.dumpreader.DumpTestSupport.CollectingDumpReader;
import org.sweble.wikitext.dumpreader.DumpTestSupport.TrickleInputStream;

public class TestDumpReaderVersionDetection
{
	private static final ExportSchemaVersion V = ExportSchemaVersion.V0_10;

	private static final Charset[] ENCODINGS = { StandardCharsets.UTF_8, null };

	// =========================================================================

	@Test
	public void testEveryExportVersionIsDetected() throws Exception
	{
		for (ExportSchemaVersion version : ExportSchemaVersion.values())
			assertDetected(version, dump(version, page(version, "TITLE")));
	}

	@Test
	public void testSingleQuotedNamespace() throws Exception
	{
		String xml = "<?xml version='1.0' encoding='UTF-8'?>\n"
				+ "<mediawiki xmlns='" + V.getMediaWikiNamespace() + "' version='0.10' xml:lang='en'>"
				+ page(V, "TITLE")
				+ "</mediawiki>";

		assertDetected(V, xml);
	}

	@Test
	public void testPrefixedRootElement() throws Exception
	{
		String xml = dump(V, page(V, "TITLE"))
				.replaceAll("<(/?)([a-z])", "<$1mw:$2")
				.replace("xmlns=", "xmlns:mw=");

		assertTrue(xml.contains("<mw:mediawiki xmlns:mw="));
		assertDetected(V, xml);
	}

	@Test
	public void testLongLeadingComment() throws Exception
	{
		String xml = dump(V, "<!--" + repeat('x', 100000) + "-->\n", page(V, "TITLE"));

		assertDetected(V, xml);
	}

	@Test
	public void testNamespaceMentionedInLeadingComment() throws Exception
	{
		String comment = "<!-- converted from <mediawiki xmlns=\""
				+ ExportSchemaVersion.V0_5.getMediaWikiNamespace()
				+ "\"> -->\n";

		assertDetected(V, dump(V, comment, page(V, "TITLE")));
	}

	@Test
	public void testShortReads() throws Exception
	{
		byte[] xml = utf8(dump(V, "<!-- some comment -->\n", page(V, "TITLE")));

		for (Charset encoding : ENCODINGS)
		{
			CollectingDumpReader reader = read(
					new TrickleInputStream(new ByteArrayInputStream(xml), 3),
					"dump.xml",
					encoding);

			assertEquals(1, reader.pages.size());
			assertTrue(V.getMediaWikiType().isInstance(reader.mediaWikis.get(0)));
			assertEquals("TEXT", reader.firstText());
		}
	}

	@Test
	public void testEmptyInputIsRejected() throws Exception
	{
		assertRejected("", "empty");
	}

	@Test
	public void testUnknownNamespaceIsRejected() throws Exception
	{
		assertRejected("<?xml version=\"1.0\"?><mediawiki xmlns=\"http://example.org/\"/>", "Unknown xmlns");
	}

	@Test
	public void testInputWithoutRootElementIsRejected() throws Exception
	{
		assertRejected("<?xml version=\"1.0\"?>\n<!-- nothing here -->\n", "root element");
	}

	// =========================================================================

	private static void assertDetected(ExportSchemaVersion version, String xml) throws Exception
	{
		for (Charset encoding : ENCODINGS)
		{
			CollectingDumpReader reader = read(
					new ByteArrayInputStream(utf8(xml)),
					"dump.xml",
					encoding);

			assertEquals(version.name(), 1, reader.pages.size());
			assertTrue(
					version.name() + " detected as " + reader.mediaWikis.get(0).getClass(),
					version.getMediaWikiType().isInstance(reader.mediaWikis.get(0)));
			assertEquals("TITLE", reader.pages.get(0).getTitle());
			assertEquals("TEXT", reader.firstText());
		}
	}

	private static void assertRejected(String xml, String messagePart) throws Exception
	{
		for (Charset encoding : ENCODINGS)
		{
			try
			{
				read(new ByteArrayInputStream(utf8(xml)), "dump.xml", encoding);
				fail("Expected an IllegalArgumentException");
			}
			catch (IllegalArgumentException e)
			{
				assertTrue(e.getMessage(), e.getMessage().contains(messagePart));
			}
		}
	}

	private static String repeat(char c, int count)
	{
		StringBuilder b = new StringBuilder(count);
		for (int i = 0; i < count; ++i)
			b.append(c);
		return b.toString();
	}
}
