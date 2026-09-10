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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.FullPage;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.engine.utils.LanguageConfigGenerator;
import org.sweble.wikitext.parser.nodes.WtInternalLink;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

/**
 * Configuration from the general site information (siprop=general): link
 * trail, API endpoint, site name, URLs and time zone. Uses trimmed siteinfo
 * responses of de.wikipedia, zh.wikipedia and de.wiktionary so no network
 * access is required.
 */
public class LanguageConfigGeneratorSiteInfoTest
{
	// =========================================================================
	// == API endpoint

	@Test
	public void testApiUrlIsDerivedFromSiteUrl()
	{
		String expected = "https://de.wiktionary.org/w/api.php";
		assertEquals(expected, LanguageConfigGenerator.getApiUrl("https://de.wiktionary.org"));
		assertEquals(expected, LanguageConfigGenerator.getApiUrl("https://de.wiktionary.org/"));
		assertEquals(expected, LanguageConfigGenerator.getApiUrl("https://de.wiktionary.org/wiki/Wiktionary:Hauptseite"));
		assertEquals(expected, LanguageConfigGenerator.getApiUrl("//de.wiktionary.org"));
		assertEquals(expected, LanguageConfigGenerator.getApiUrl("de.wiktionary.org"));

		assertEquals(
				"http://localhost:8080/w/api.php",
				LanguageConfigGenerator.getApiUrl("http://localhost:8080"));

		// An explicit api.php is kept
		assertEquals(
				"https://example.org/mediawiki/api.php",
				LanguageConfigGenerator.getApiUrl("https://example.org/mediawiki/api.php?action=query"));
	}

	@Test
	public void testSiteInfoUrl()
	{
		String apiUrl = LanguageConfigGenerator.getApiUrl("https://de.wiktionary.org");
		assertEquals(
				"https://de.wiktionary.org/w/api.php?action=query&meta=siteinfo&siprop=general&format=xml",
				LanguageConfigGenerator.getSiteInfoUrl(apiUrl, "general"));
	}

	// =========================================================================
	// == Link trail

	@Test
	public void testLinkTrailConversion()
	{
		assertEquals("(?sU:[äöüßa-z]+)", LanguageConfigGenerator.convertLinkTrail("/^([äöüßa-z]+)(.*)$/sDu"));
		assertEquals("[a-z]+", LanguageConfigGenerator.convertLinkTrail("/^([a-z]+)(.*)$/"));

		// zh.wikipedia does not use link trails
		assertEquals("", LanguageConfigGenerator.convertLinkTrail("/^()(.*)$/sD"));
		assertEquals("", LanguageConfigGenerator.convertLinkTrail(""));

		// Unexpected form or unsupported (ungreedy) modifier
		assertNull(LanguageConfigGenerator.convertLinkTrail("/[a-z]+/"));
		assertNull(LanguageConfigGenerator.convertLinkTrail("/^([a-z]+)(.*)$/U"));
	}

	@Test
	public void testUnicodeModifierMakesCharacterClassesUnicodeAware()
	{
		String pattern = LanguageConfigGenerator.convertLinkTrail("/^(\\w+)(.*)$/sDu");

		Matcher m = Pattern.compile(pattern).matcher("über alles");
		assertTrue(m.lookingAt());
		assertEquals("über", m.group());
	}

	@Test
	public void testGermanLinkTrailBecomesPartOfTheLink() throws Exception
	{
		WikiConfigImpl config = configFromGeneral("/siteinfo/dewiki-general.xml", "https://de.wikipedia.org");

		List<WtInternalLink> links = new ArrayList<WtInternalLink>();
		List<String> texts = new ArrayList<String>();
		collect(parse(config, "[[Haus]]tür"), links, texts);

		// One link "Haustür" and no text left over
		assertEquals(1, links.size());
		assertEquals("tür", links.get(0).getPostfix());
		assertEquals("[[Haus]]tür", WtRtDataPrinter.print(links.get(0)));
		assertFalse(texts.toString(), texts.contains("ür"));
	}

	@Test
	public void testGermanLinkTrailIsCaseSensitive() throws Exception
	{
		WikiConfigImpl config = configFromGeneral("/siteinfo/dewiki-general.xml", "https://de.wikipedia.org");

		List<WtInternalLink> links = new ArrayList<WtInternalLink>();
		List<String> texts = new ArrayList<String>();
		collect(parse(config, "[[Haus]]Tür"), links, texts);

		assertEquals(1, links.size());
		assertEquals("", links.get(0).getPostfix());
		assertTrue(texts.toString(), texts.contains("Tür"));
	}

	@Test
	public void testChineseWikiHasNoLinkTrail() throws Exception
	{
		WikiConfigImpl config = configFromGeneral("/siteinfo/zhwiki-general.xml", "https://zh.wikipedia.org");
		assertEquals("", config.getParserConfig().getInternalLinkPostfixPattern());

		List<WtInternalLink> links = new ArrayList<WtInternalLink>();
		List<String> texts = new ArrayList<String>();
		collect(parse(config, "[[Haus]]abc"), links, texts);

		assertEquals(1, links.size());
		assertEquals("", links.get(0).getPostfix());
		assertTrue(texts.toString(), texts.contains("abc"));
	}

	// =========================================================================
	// == Link prefix

	@Test
	public void testLinkPrefixConversion()
	{
		// The character set takes precedence over the regex
		assertEquals(
				"(?sU:[\\x{0600}-\\x{06FF}]+)",
				LanguageConfigGenerator.convertLinkPrefix("\\x{0600}-\\x{06FF}", "/^(.*?)([a-z]+)$/sDu"));

		// Regex as built by MediaWiki from the character set
		assertEquals(
				"(?sU:[a-zäöüß\\-]+)",
				LanguageConfigGenerator.convertLinkPrefix(null, "/^((?>.*[^a-zäöüß\\-]|))(.+)$/sDu"));
		// Older configurations
		assertEquals(
				"(?sU:[a-zäöüß\\-]+)",
				LanguageConfigGenerator.convertLinkPrefix("", "/^((?>.*[^a-zäöüß\\-])|)(.+)$/sDu"));
		assertEquals(
				"(?sU:[a-zäöüß]+)",
				LanguageConfigGenerator.convertLinkPrefix(null, "/^(.*?)([a-zäöüß]+)$/sDu"));

		// Characters special in Java character classes are taken literally
		assertEquals("(?sU:[\\^a\\[\\&\\&]+)", LanguageConfigGenerator.convertLinkPrefix("^a[&&", null));

		// Most wikis do not use link prefixes
		assertEquals("", LanguageConfigGenerator.convertLinkPrefix("", ""));
		assertEquals("", LanguageConfigGenerator.convertLinkPrefix(null, null));

		// Unexpected form, unsupported (ungreedy) modifier or POSIX class
		assertNull(LanguageConfigGenerator.convertLinkPrefix(null, "/[a-z]+/"));
		assertNull(LanguageConfigGenerator.convertLinkPrefix(null, "/^(.*?)([a-z]+)$/U"));
		assertNull(LanguageConfigGenerator.convertLinkPrefix("[:alpha:]", null));
	}

	@Test
	public void testArabicLinkPrefixBecomesPartOfTheLink() throws Exception
	{
		WikiConfigImpl config = configFromGeneral("/siteinfo/arwiki-general.xml", "https://ar.wikipedia.org");

		String pattern = config.getParserConfig().getInternalLinkPrefixPattern();
		assertTrue(pattern, pattern.startsWith("(?sU:[a-zA-Zء-ي\\x{0610}-\\x{061A}"));
		assertTrue(pattern, pattern.endsWith("\\x{06EA}-\\x{06ED}]+)"));

		List<WtInternalLink> links = new ArrayList<WtInternalLink>();
		List<String> texts = new ArrayList<String>();
		collect(parse(config, "في بال[[كتاب]]"), links, texts);

		assertEquals(1, links.size());
		assertEquals("بال", links.get(0).getPrefix());
		assertEquals("بال[[كتاب]]", WtRtDataPrinter.print(links.get(0)));
		assertTrue(texts.toString(), texts.contains("في "));
	}

	@Test
	public void testArabicLinkPrefixOnlyTakesCharactersOfTheCharset() throws Exception
	{
		WikiConfigImpl config = configFromGeneral("/siteinfo/arwiki-general.xml", "https://ar.wikipedia.org");

		List<WtInternalLink> links = new ArrayList<WtInternalLink>();
		List<String> texts = new ArrayList<String>();
		collect(parse(config, "12[[كتاب]]"), links, texts);

		assertEquals(1, links.size());
		assertEquals("", links.get(0).getPrefix());
		assertTrue(texts.toString(), texts.contains("12"));
	}

	@Test
	public void testGermanWikiHasNoLinkPrefix() throws Exception
	{
		WikiConfigImpl config = configFromGeneral("/siteinfo/dewiki-general.xml", "https://de.wikipedia.org");
		assertNull(config.getParserConfig().getInternalLinkPrefixPattern());

		List<WtInternalLink> links = new ArrayList<WtInternalLink>();
		List<String> texts = new ArrayList<String>();
		collect(parse(config, "xnull[[Haus]]"), links, texts);

		assertEquals(1, links.size());
		assertEquals("", links.get(0).getPrefix());
		assertTrue(texts.toString(), texts.contains("xnull"));
	}

	// =========================================================================
	// == Site name, URLs, time zone

	@Test
	public void testSiteNameAndUrlsMatchMediaWiki() throws Exception
	{
		WikiConfigImpl config = configFromGeneral("/siteinfo/dewiki-general.xml", "https://de.wikipedia.org");

		assertEquals("Wikipedia", config.getSiteName());
		assertEquals("https://de.wikipedia.org/w/index.php", config.getWikiUrl());
		assertEquals("https://de.wikipedia.org/wiki/$1", config.getArticlePath());

		assertEquals("Wikipedia", expand(config, "{{SITENAME}}"));
		assertEquals("https://de.wikipedia.org/wiki/Foo_bar", expand(config, "{{fullurl:Foo bar}}"));
		assertEquals("https://de.wikipedia.org/wiki/Foo?action=edit", expand(config, "{{fullurl:Foo|action=edit}}"));
	}

	@Test
	public void testProtocolRelativeServerUsesSchemeOfSiteUrl() throws Exception
	{
		WikiConfigImpl config = configFromGeneral("/siteinfo/dewiki-general.xml", "http://de.wikipedia.org");
		assertEquals("http://de.wikipedia.org/wiki/$1", config.getArticlePath());

		config = configFromGeneral("/siteinfo/dewiki-general.xml", null);
		assertEquals("https://de.wikipedia.org/wiki/$1", config.getArticlePath());
	}

	@Test
	public void testTimezoneIsTakenFromSiteInfo() throws Exception
	{
		assertEquals(
				"Europe/Berlin",
				configFromGeneral("/siteinfo/dewiki-general.xml", "https://de.wikipedia.org").getTimezone().getID());
		assertEquals(
				"UTC",
				configFromGeneral("/siteinfo/zhwiki-general.xml", "https://zh.wikipedia.org").getTimezone().getID());
	}

	@Test
	public void testDefaultsAreKeptForExistingConfigs()
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		assertEquals("http://localhost/?title=$1", config.getArticlePath());
		assertEquals(TimeZone.getDefault(), config.getTimezone());
	}

	@Test
	public void testArticlePathAndTimezoneAreSavedAndLoaded() throws Exception
	{
		WikiConfigImpl config = configFromGeneral("/siteinfo/dewiki-general.xml", "https://de.wikipedia.org");

		StringWriter writer = new StringWriter();
		config.save(writer);
		WikiConfigImpl loaded = WikiConfigImpl.load(new StringReader(writer.toString()));

		assertEquals("https://de.wikipedia.org/wiki/$1", loaded.getArticlePath());
		assertEquals("Europe/Berlin", loaded.getTimezone().getID());
		assertEquals(config, loaded);
	}

	// =========================================================================
	// == Complete configuration

	@Test
	public void testGenerateWiktionaryConfig() throws Exception
	{
		String siteInfo = resource("/siteinfo/dewiktionary-siteinfo.xml");
		WikiConfig config = LanguageConfigGenerator.generateWikiConfig(
				"de wiki",
				"https://de.wiktionary.org",
				"de",
				resource("/siteinfo/dewiktionary-namespacealiases.xml"),
				siteInfo,
				siteInfo,
				siteInfo,
				siteInfo,
				null);

		assertEquals("Wiktionary", config.getSiteName());
		assertEquals("https://de.wiktionary.org/w/index.php", config.getWikiUrl());
		assertEquals("https://de.wiktionary.org/wiki/$1", config.getArticlePath());
		assertEquals("Europe/Berlin", config.getTimezone().getID());
		assertEquals("(?sU:[äöüßa-z]+)", config.getParserConfig().getInternalLinkPostfixPattern());

		// Wiktionary namespaces, not those of Wikipedia
		assertEquals("Wiktionary", config.getNamespace(4).getName());
		assertEquals("Flexion", config.getNamespace(108).getName());

		assertEquals("Wiktionary", expand(config, "{{PROJEKTNAME}}"));
		assertEquals(
				"https://de.wiktionary.org/wiki/Flexion:Haus",
				expand(config, "{{VOLLSTÄNDIGE_URL:Flexion:Haus}}"));

		List<WtInternalLink> links = new ArrayList<WtInternalLink>();
		collect(parse(config, "[[Haus]]tür"), links, new ArrayList<String>());
		assertEquals(1, links.size());
		assertEquals("tür", links.get(0).getPostfix());
	}

	@Test
	public void testGenerateWithoutGeneralSiteInfoKeepsGivenValues() throws Exception
	{
		String siteInfo = resource("/siteinfo/dewiktionary-siteinfo.xml");
		WikiConfig config = LanguageConfigGenerator.generateWikiConfig(
				"de wiki",
				"https://de.wiktionary.org",
				"de",
				resource("/siteinfo/dewiktionary-namespacealiases.xml"),
				siteInfo,
				siteInfo,
				siteInfo);

		assertEquals("de wiki", config.getSiteName());
		assertEquals("https://de.wiktionary.org?title=$1", config.getArticlePath());
		assertEquals("[a-z]+", config.getParserConfig().getInternalLinkPostfixPattern());
	}

	// =========================================================================

	private static String resource(String name)
	{
		URL url = LanguageConfigGeneratorSiteInfoTest.class.getResource(name);
		assertNotNull(name, url);
		return url.toString();
	}

	private static WikiConfigImpl configFromGeneral(String name, String siteUrl) throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		LanguageConfigGenerator.addGeneralSiteInfo(config, resource(name), siteUrl);
		return config;
	}

	private static PageId pageId(WikiConfig config) throws Exception
	{
		return new PageId(PageTitle.make(config, "Test"), -1);
	}

	private static WtNode parse(WikiConfig config, String wikitext) throws Exception
	{
		return new WtEngineImpl(config).parse(pageId(config), wikitext, new NullCallback()).getPage();
	}

	private static String expand(WikiConfig config, String wikitext) throws Exception
	{
		WtEngineImpl engine = new WtEngineImpl(config);
		return WtRtDataPrinter.print(engine.expand(pageId(config), wikitext, new NullCallback()).getPage());
	}

	private static void collect(WtNode node, List<WtInternalLink> links, List<String> texts)
	{
		if (node instanceof WtInternalLink)
		{
			links.add((WtInternalLink) node);
			return;
		}
		if (node instanceof WtText)
			texts.add(((WtText) node).getContent());
		for (WtNode child : node)
			collect(child, links, texts);
	}

	private static final class NullCallback
			implements
				ExpansionCallback
	{
		@Override
		public FullPage retrieveWikitext(ExpansionFrame expansionFrame, PageTitle pageTitle)
		{
			return null;
		}

		@Override
		public String fileUrl(PageTitle pageTitle, int width, int height)
		{
			return null;
		}
	}
}
