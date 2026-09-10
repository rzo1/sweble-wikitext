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
package org.sweble.wikitext.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Test;
import org.sweble.wikitext.engine.config.InterwikiImpl;
import org.sweble.wikitext.engine.config.NamespaceCase;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.config.WikiConfigurationException;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;

public class PageTitleTest
{
	/** Tests fix to issue #45. */
	@Test
	public void testName() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		// Must not fail with illegal entity error
		PageTitle title = PageTitle.make(
				config,
				"Template:Did you know nominations/Steve Taylor & The Perfect Foil; Wow to the Deadness");

		PageTitle title2 = PageTitle.make(
				config,
				title.getNormalizedFullTitle());

		assertEquals(title, title2);

		PageTitle title3 = PageTitle.make(
				config,
				title.getDenormalizedFullTitle());

		assertEquals(title, title3);
	}

	/** Interwiki prefixes are case-insensitive (issue #101). */
	@Test
	public void testInterwikiPrefixesAreCaseInsensitive() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		for (String target : new String[] { "wikt:Haus", "Wikt:Haus", "WIKT:Haus" })
		{
			PageTitle title = PageTitle.make(config, target);

			assertTrue(target, title.isInterwiki());
			assertEquals(target, "wikt", title.getInterwikiLink().getPrefix());
			assertEquals(target, "Haus", title.getTitle());
		}

		PageTitle title = PageTitle.make(config, "DE:Foo");

		assertTrue(title.isInterwiki());
		assertEquals("de", title.getInterwikiLink().getPrefix());
		assertEquals("Foo", title.getTitle());
		assertEquals(PageTitle.make(config, "de:Foo"), title);
	}

	/** The interwiki prefix of this wiki is case-insensitive (issue #101). */
	@Test
	public void testInterwikiPrefixOfThisWikiIsCaseInsensitive() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		assertTrue(config.getParserConfig().isIwPrefixOfThisWiki("EN"));

		PageTitle title = PageTitle.make(config, "EN:Foo");

		assertFalse(title.isInterwiki());
		assertEquals("Foo", title.getTitle());
		assertEquals(config.getDefaultNamespace(), title.getNamespace());
	}

	/** Prefixes registered in upper case keep working (issue #101). */
	@Test
	public void testUpperCaseRegisteredInterwikiPrefix() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		InterwikiImpl iw = new InterwikiImpl(
				"MyWiki",
				"http://example.org/wiki/$1",
				false,
				false);
		config.addInterwiki(iw);

		assertSame(iw, config.getInterwiki("MyWiki"));
		assertSame(iw, config.getInterwiki("mywiki"));
		assertSame(iw, config.getInterwiki("MYWIKI"));

		PageTitle title = PageTitle.make(config, "mywiki:Foo");

		assertTrue(title.isInterwiki());
		assertSame(iw, title.getInterwikiLink());

		try
		{
			config.addInterwiki(new InterwikiImpl(
					"MYWIKI",
					"http://example.com/wiki/$1",
					false,
					false));
			fail("Prefixes differing only in case must be rejected");
		}
		catch (IllegalArgumentException e)
		{
			// expected
		}
	}

	/** Titles in first-letter namespaces are capitalized (issue #103). */
	@Test
	public void testFirstLetterNamespaceCapitalizesTitle() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		assertEquals(NamespaceCase.FIRST_LETTER, config.getDefaultNamespace().getCase());

		assertEquals("Foo_bar", PageTitle.make(config, "foo bar").getTitle());
		assertEquals("Foo", PageTitle.make(config, "User:foo").getTitle());
		assertEquals("User:Foo", PageTitle.make(config, "user:foo").getPrefixedText());
		assertEquals(PageTitle.make(config, "Foo"), PageTitle.make(config, "foo"));

		// Interwiki links are never capitalized
		assertEquals("haus", PageTitle.make(config, "wikt:haus").getTitle());
	}

	/** Titles in case-sensitive namespaces keep their case (issue #103). */
	@Test
	public void testCaseSensitiveNamespaceKeepsTitle() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		config.getNamespace(0).setCase(NamespaceCase.CASE_SENSITIVE);
		config.getNamespace(10).setCase(NamespaceCase.CASE_SENSITIVE);

		assertEquals("wort", PageTitle.make(config, "wort").getTitle());
		assertEquals("Wort", PageTitle.make(config, "Wort").getTitle());
		assertNotEquals(PageTitle.make(config, "Wort"), PageTitle.make(config, "wort"));

		// The namespace of the title decides, not the default namespace
		assertEquals("Template:foo", PageTitle.make(config, "foo", config.getTemplateNamespace()).getPrefixedText());
		assertEquals("foo", PageTitle.make(config, ":foo", config.getTemplateNamespace()).getTitle());
		assertEquals("User:Foo", PageTitle.make(config, "User:foo").getPrefixedText());
		assertEquals("Foo", PageTitle.make(config, "foo", config.getNamespace(2)).getTitle());

		// Subpages keep their case as well
		PageTitle title = PageTitle.make(config, "Template:foo/bar");
		assertEquals("foo", title.getBaseTitle().getTitle());
		assertEquals("bar", title.getSubpageTitle().getTitle());
	}

	/** The first letter is converted like MediaWiki's Language::ucfirst(). */
	@Test
	public void testFirstLetterIsConvertedLikeMediaWiki() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		// No single upper case letter
		assertEquals("ßa", PageTitle.make(config, "ßa").getTitle());
		assertEquals("ﬁx", PageTitle.make(config, "ﬁx").getTitle());

		// Letters that are not uppercased in titles
		assertEquals("ქართული", PageTitle.make(config, "ქართული").getTitle());

		// Other scripts and characters outside the BMP
		assertEquals("Éa", PageTitle.make(config, "éa").getTitle());
		assertEquals("Ωmega", PageTitle.make(config, "ωmega").getTitle());
		assertEquals("𐐀x", PageTitle.make(config, "𐐨x").getTitle());
		assertEquals("1a", PageTitle.make(config, "1a").getTitle());

		// Only the first letter is changed
		assertEquals("ÄöÜ", PageTitle.make(config, "äöÜ").getTitle());

		// The dotless i becomes I in all languages
		assertEquals("Istanbul", PageTitle.make(config, "istanbul").getTitle());
		assertEquals("Ii", PageTitle.make(config, "ıi").getTitle());
	}

	/** Turkish upper cases the dotted i to İ (MediaWiki's LanguageTr). */
	@Test
	public void testTurkishDottedI() throws Exception
	{
		for (String lang : new String[] { "tr", "az", "kaa" })
		{
			WikiConfigImpl config = DefaultConfigEnWp.generate();
			config.setContentLang(lang);

			assertEquals(lang, "İstanbul", PageTitle.make(config, "istanbul").getTitle());
			assertEquals(lang, "Ii", PageTitle.make(config, "ıi").getTitle());
			assertEquals(lang, "Ankara", PageTitle.make(config, "ankara").getTitle());
		}
	}

	private static Object serializeAndDeserialize(Object o) throws Exception
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes))
		{
			out.writeObject(o);
		}
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
		{
			return in.readObject();
		}
	}

	/** Page titles can be serialized and bound to a configuration again (issue #133). */
	@Test
	public void testSerialization() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		for (String target : new String[] { "Template:Foo/bar#frag", "Foo", "wikt:Haus" })
		{
			PageTitle title = PageTitle.make(config, target);
			PageTitle copy = (PageTitle) serializeAndDeserialize(title);

			assertEquals(target, title, copy);
			assertEquals(target, title.hashCode(), copy.hashCode());
			assertEquals(target, title.getPrefixedText(), copy.getPrefixedText());
			assertEquals(target, title.getNamespace(), copy.getNamespace());

			PageTitle bound = copy.rebind(config);
			assertEquals(target, title, bound);
			assertEquals(target, title.getUrl(), bound.getUrl());
		}

		PageTitle copy = (PageTitle) serializeAndDeserialize(PageTitle.make(config, "Foo"));
		try
		{
			copy.getUrl();
			fail("A title without configuration cannot build a URL");
		}
		catch (IllegalStateException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("rebind"));
		}
	}

	/** A missing default namespace is reported clearly (issue #133). */
	@Test
	public void testMakeWithoutDefaultNamespaceFailsWithConfigurationException() throws Exception
	{
		try
		{
			PageTitle.make(new WikiConfigImpl(), "Foo");
			fail("Expected a WikiConfigurationException");
		}
		catch (WikiConfigurationException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("default namespace"));
		}
	}
}
