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

package org.sweble.wikitext.engine.ext.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.ParserFunctionBase;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.output.HtmlRenderer;
import org.sweble.wikitext.engine.output.HtmlRendererCallback;
import org.sweble.wikitext.engine.output.MediaInfo;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.engine.utils.LanguageConfigGenerator;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.parser.nodes.WtUrl;

/**
 * Behavior switches like {@code __NOTOC__} (rzo1/sweble-wikitext#100).
 */
public class CorePfnBehaviorSwitchesTest
{
	private static final String[][] ENGLISH_SWITCHES = {
			{ "__NOTOC__", "notoc" },
			{ "__FORCETOC__", "forcetoc" },
			{ "__TOC__", "toc" },
			{ "__NOEDITSECTION__", "noeditsection" },
			{ "__NEWSECTIONLINK__", "newsectionlink" },
			{ "__NONEWSECTIONLINK__", "nonewsectionlink" },
			{ "__NOGALLERY__", "nogallery" },
			{ "__HIDDENCAT__", "hiddencat" },
			{ "__EXPECTUNUSEDCATEGORY__", "expectunusedcategory" },
			{ "__EXPECTUNUSEDTEMPLATE__", "expectunusedtemplate" },
			{ "__NOCONTENTCONVERT__", "nocontentconvert" },
			{ "__NOCC__", "nocontentconvert" },
			{ "__NOTITLECONVERT__", "notitleconvert" },
			{ "__NOTC__", "notitleconvert" },
			{ "__INDEX__", "index" },
			{ "__NOINDEX__", "noindex" },
			{ "__STATICREDIRECT__", "staticredirect" },
			{ "__DISAMBIG__", "disambiguation" },
			{ "__NOGLOBAL__", "noglobal" },
			{ "__ARCHIVEDTALK__", "archivedtalk" },
			{ "__NOTALK__", "notalk" } };

	// =========================================================================

	@Test
	public void testEnglishSwitchesAreRegistered() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		for (String[] s : ENGLISH_SWITCHES)
		{
			ParserFunctionBase pageSwitch = config.getPageSwitch(s[0]);
			assertNotNull(s[0], pageSwitch);
			assertEquals(s[0], s[1], pageSwitch.getId());
			assertTrue(s[0], pageSwitch.isPageSwitch());

			// Page switches cannot be retrieved as parser functions
			assertNull(s[0], config.getParserFunction(s[0]));

			// The parser asks for the name without the enclosing underscores
			String name = s[0].substring(2, s[0].length() - 2);
			assertTrue(s[0], config.getParserConfig().isValidPageSwitchName(name));
		}
	}

	@Test
	public void testUnknownAndMalformedNamesAreRejected() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		assertNull(config.getPageSwitch("__FOO__"));
		assertFalse(config.getParserConfig().isValidPageSwitchName("FOO"));
		assertFalse(config.getParserConfig().isValidPageSwitchName("__NOTOC__"));

		// Magic words which are not behavior switches
		assertNull(config.getPageSwitch("PAGENAME"));
		assertFalse(config.getParserConfig().isValidPageSwitchName("PAGENAME"));
	}

	@Test
	public void testCaseSensitivityFollowsTheAlias() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		// notoc is case-insensitive, hiddencat is case-sensitive
		assertEquals("notoc", config.getPageSwitch("__notoc__").getId());
		assertNull(config.getPageSwitch("__hiddencat__"));
	}

	@Test
	public void testOnlySwitchesWithAliasesAreRegistered() throws Exception
	{
		assertTrue(CorePfnBehaviorSwitches.group(new WikiConfigImpl()).getParserFunctions().isEmpty());
	}

	@Test
	public void testSwitchesSurviveSaveAndLoad() throws Exception
	{
		StringWriter writer = new StringWriter();
		DefaultConfigEnWp.generate().save(writer);
		WikiConfigImpl config = WikiConfigImpl.load(new StringReader(writer.toString()));

		assertEquals("notoc", config.getPageSwitch("__NOTOC__").getId());
		assertTrue(config.getParserConfig().isValidPageSwitchName("NOTOC"));
	}

	@Test
	public void testEnglishSwitchesAreParsedAndNotRendered() throws Exception
	{
		WikiConfig config = DefaultConfigEnWp.generate();

		String wikitext = ""
				+ "__NOTOC__\n"
				+ "Some __TOC__ text__FORCETOC__.\n"
				+ "\n"
				+ "__FOO__ and __NOEDITSECTION__ and __notoc__\n";

		EngProcessedPage cp = process(config, wikitext);

		assertEquals(
				Arrays.asList("notoc", "toc", "forcetoc", "noeditsection"),
				new ArrayList<String>(CorePfnBehaviorSwitches.getBehaviorSwitches(config, cp)));

		String html = render(config, cp);
		assertFalse(html, html.contains("NOTOC"));
		assertFalse(html, html.contains("notoc"));
		assertFalse(html, html.contains("TOC__"));
		assertFalse(html, html.contains("NOEDITSECTION"));
		assertTrue(html, html.contains("Some  text."));
		assertTrue(html, html.contains("__FOO__ and  and"));
	}

	@Test
	public void testLocalizedSwitchesOfGeneratedGermanConfig() throws Exception
	{
		WikiConfig config = generateGermanConfig();

		assertEquals("notoc", config.getPageSwitch("__KEIN_INHALTSVERZEICHNIS__").getId());
		assertEquals("notoc", config.getPageSwitch("__KEININHALTSVERZEICHNIS__").getId());
		assertEquals("notoc", config.getPageSwitch("__keininhaltsverzeichnis__").getId());
		assertEquals("notoc", config.getPageSwitch("__NOTOC__").getId());
		assertEquals("forcetoc", config.getPageSwitch("__INHALTSVERZEICHNIS_ERZWINGEN__").getId());
		assertEquals("hiddencat", config.getPageSwitch("__VERSTECKTE_KATEGORIE__").getId());
		assertNull(config.getPageSwitch("__versteckte_kategorie__"));

		// This wiki doesn't know the switch of the Disambiguator extension
		assertNull(config.getPageSwitch("__DISAMBIG__"));

		String wikitext = ""
				+ "__KEIN_INHALTSVERZEICHNIS__\n"
				+ "Text __INHALTSVERZEICHNIS__ mit __ABSCHNITTE_NICHT_BEARBEITEN__ "
				+ "und __DISAMBIG__ und __KEIN__INHALTSVERZEICHNIS__.\n";

		EngProcessedPage cp = process(config, wikitext);

		assertEquals(
				Arrays.asList("notoc", "toc", "noeditsection"),
				new ArrayList<String>(CorePfnBehaviorSwitches.getBehaviorSwitches(config, cp)));

		String html = render(config, cp);
		assertFalse(html, html.contains("__KEIN_INHALTSVERZEICHNIS__"));
		assertFalse(html, html.contains("Text __INHALTSVERZEICHNIS__"));
		assertFalse(html, html.contains("ABSCHNITTE"));
		assertTrue(html, html.contains("Text  mit  und __DISAMBIG__ und __KEIN__INHALTSVERZEICHNIS__."));
	}

	// =========================================================================

	private static WikiConfig generateGermanConfig() throws Exception
	{
		URL siteinfo = CorePfnBehaviorSwitchesTest.class.getResource(
				"/i18n-behavior-switches-de-siteinfo.xml");
		URL namespaceAliases = CorePfnBehaviorSwitchesTest.class.getResource(
				"/i18n-behavior-switches-de-namespacealiases.xml");
		assertNotNull(siteinfo);
		assertNotNull(namespaceAliases);

		return LanguageConfigGenerator.generateWikiConfig(
				"de wiki",
				"https://de.wikipedia.org",
				"de",
				namespaceAliases.toString(),
				siteinfo.toString(),
				siteinfo.toString(),
				siteinfo.toString());
	}

	private static EngProcessedPage process(WikiConfig config, String wikitext) throws Exception
	{
		WtEngineImpl engine = new WtEngineImpl(config);
		PageTitle pageTitle = PageTitle.make(config, "Test");
		return engine.postprocess(new PageId(pageTitle, -1), wikitext, null);
	}

	private static String render(WikiConfig config, EngProcessedPage cp) throws Exception
	{
		PageTitle pageTitle = PageTitle.make(config, "Test");
		return HtmlRenderer.print(new TestCallback(), config, pageTitle, cp.getPage());
	}

	// =========================================================================

	private static final class TestCallback
			implements
				HtmlRendererCallback
	{
		@Override
		public boolean resourceExists(PageTitle target)
		{
			return false;
		}

		@Override
		public MediaInfo getMediaInfo(String title, int width, int height)
		{
			return null;
		}

		@Override
		public String makeUrl(PageTitle target)
		{
			return "/wiki/" + UrlEncoding.WIKI.encode(target.getNormalizedFullTitle());
		}

		@Override
		public String makeUrl(WtUrl target)
		{
			return target.getProtocol().isEmpty() ? target.getPath() : target.getProtocol() + ":" + target.getPath();
		}

		@Override
		public String makeUrlMissingTarget(String path)
		{
			return "/w/index.php?title=" + path + "&amp;action=edit&amp;redlink=1";
		}
	}
}
