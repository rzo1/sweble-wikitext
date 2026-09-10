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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sweble.wikitext.engine.config.InterwikiImpl;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
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
}
