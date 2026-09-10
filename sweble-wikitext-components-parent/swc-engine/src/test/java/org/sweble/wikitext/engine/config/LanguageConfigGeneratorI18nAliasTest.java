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

package org.sweble.wikitext.engine.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.net.URL;

import org.junit.Before;
import org.junit.Test;
import org.sweble.wikitext.engine.utils.LanguageConfigGenerator;

/**
 * Name conflicts between i18n aliases generated from a siteinfo magicwords
 * response (upstream sweble/sweble-wikitext#72). Uses a local, synthetic
 * response so no network access is required.
 */
public class LanguageConfigGeneratorI18nAliasTest
{
	private WikiConfigImpl wikiConfig;

	@Before
	public void setUp() throws Exception
	{
		URL magicWords = getClass().getResource("/i18n-alias-conflicts-magicwords.xml");
		assertNotNull(magicWords);

		wikiConfig = new WikiConfigImpl();
		LanguageConfigGenerator.addi18NAliases(wikiConfig, magicWords.toString());
	}

	@Test
	public void testAllAliasesAreRegistered()
	{
		for (String id : new String[] { "ns", "namespace", "lc", "uc", "pagename", "sitename" })
			assertNotNull(id, wikiConfig.getI18nAliasById(id));
	}

	@Test
	public void testCaseSensitivePfnAliasWinsLikeInMediaWiki()
	{
		// MediaWiki resolves {{名前空間:...}} to NAMESPACE on ja.wikipedia
		// since case-sensitive function synonyms are looked up first.
		assertEquals("namespace", wikiConfig.getI18nAlias("名前空間:").getId());
		assertEquals("namespace", wikiConfig.getI18nAlias("名前空間").getId());
		assertEquals("namespace", wikiConfig.getI18nAlias("NAMESPACE:").getId());

		// The ns parser function stays reachable via its other names.
		assertEquals("ns", wikiConfig.getI18nAlias("名空:").getId());
		assertEquals("ns", wikiConfig.getI18nAlias("ns:").getId());
	}

	@Test
	public void testReportedNameWinsOverGeneratedColonVariant()
	{
		// "ABC:" is generated for lc but reported for uc.
		assertEquals("uc", wikiConfig.getI18nAlias("abc:").getId());
		assertEquals("lc", wikiConfig.getI18nAlias("LC:").getId());
	}

	@Test
	public void testReportedNameWinsOverGeneratedBareVariant()
	{
		// "XYZ" is generated for pagename (optional colon removed) but
		// reported for sitename.
		assertEquals("sitename", wikiConfig.getI18nAlias("XYZ").getId());
		assertEquals("pagename", wikiConfig.getI18nAlias("XYZ:").getId());
	}

	@Test
	public void testLosingAliasDoesNotListContestedName()
	{
		assertFalse(wikiConfig.getI18nAliasById("ns").getAliases().contains("名前空間:"));
		assertFalse(wikiConfig.getI18nAliasById("lc").getAliases().contains("ABC:"));
		assertFalse(wikiConfig.getI18nAliasById("pagename").getAliases().contains("XYZ"));

		// Every name an alias lists resolves to that alias again.
		for (I18nAlias alias : wikiConfig.getI18nAliases())
		{
			for (String name : alias.getAliases())
				assertSame(name, alias, wikiConfig.getI18nAlias(name));
		}
	}

	/**
	 * Pretty-printed responses and magic words without aliases are read
	 * correctly (issue #133).
	 */
	@Test
	public void testPrettyPrintedMagicWords() throws Exception
	{
		URL magicWords = getClass().getResource("/i18n-alias-pretty-printed-magicwords.xml");
		assertNotNull(magicWords);

		WikiConfigImpl config = new WikiConfigImpl();
		LanguageConfigGenerator.addi18NAliases(config, magicWords.toString());

		I18nAliasImpl redirect = config.getI18nAliasById("redirect");
		assertNotNull(redirect);
		assertEquals(2, redirect.getAliases().size());
		assertTrue(redirect.hasAlias("#WEITERLEITUNG"));
		assertTrue(config.getParserConfig().isRedirectKeyword("#weiterleitung"));
		assertEquals("redirect", config.getI18nAlias("#REDIRECT").getId());

		assertEquals("lc", config.getI18nAlias("klein:").getId());
		assertEquals("lc", config.getI18nAlias("LC:").getId());
		for (String name : config.getI18nAliasById("lc").getAliases())
			assertFalse(name, name.trim().isEmpty());

		I18nAliasImpl noAliases = config.getI18nAliasById("noaliases");
		assertNotNull(noAliases);
		assertTrue(noAliases.getAliases().isEmpty());
	}
}
