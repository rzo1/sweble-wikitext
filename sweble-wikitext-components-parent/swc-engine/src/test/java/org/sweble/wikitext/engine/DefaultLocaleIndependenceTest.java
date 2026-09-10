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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sweble.wikitext.engine.config.I18nAliasImpl;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.output.HtmlRenderer;
import org.sweble.wikitext.engine.output.HtmlRendererCallback;
import org.sweble.wikitext.engine.output.MediaInfo;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.parser.nodes.WtExternalLink;
import org.sweble.wikitext.parser.nodes.WtImageLink;
import org.sweble.wikitext.parser.nodes.WtImageLink.ImageHorizAlign;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtPageName;
import org.sweble.wikitext.parser.nodes.WtUrl;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

/**
 * Identifiers, tag names, protocols and HTML output must not depend on the
 * default locale of the JVM (Turkish dotless i, Arabic digits).
 */
public class DefaultLocaleIndependenceTest
{
	private static final Locale TURKISH = new Locale("tr", "TR");

	private static final Locale ARABIC = new Locale("ar", "EG");

	private Locale defaultLocale;

	// =========================================================================

	@Before
	public void saveDefaultLocale()
	{
		defaultLocale = Locale.getDefault();
	}

	@After
	public void restoreDefaultLocale()
	{
		Locale.setDefault(defaultLocale);
	}

	// =========================================================================

	@Test
	public void testNamespaceLookupUnderTurkishLocale() throws Exception
	{
		Locale.setDefault(TURKISH);
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		assertNotNull(config.getNamespace("FILE"));
		assertSame(config.getNamespace("File"), config.getNamespace("FILE"));

		WtImageLink img = find(postprocess(config, "[[FILE:x.png]]"), WtImageLink.class);
		assertNotNull(img);
	}

	@Test
	public void testUppercaseTagNamesUnderTurkishLocale() throws Exception
	{
		String wikitext = "<NOWIKI>''x''</NOWIKI>\n\na<INCLUDEONLY>b</INCLUDEONLY>c";

		String expected = render(Locale.US, wikitext);
		assertFalse(expected, expected.contains("<i>"));
		assertTrue(expected, expected.contains("ac"));

		assertEquals(expected, render(TURKISH, wikitext));
	}

	@Test
	public void testParserFunctionAndProtocolUnderTurkishLocale() throws Exception
	{
		Locale.setDefault(TURKISH);
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		assertEquals("yes", expand(config, "{{#IF:x|yes|no}}"));

		WtExternalLink link = find(
				postprocess(config, "[MAILTO:someone@example.org mail]"),
				WtExternalLink.class);
		assertNotNull(link);
	}

	@Test
	public void testUppercaseImageOptionsUnderTurkishLocale() throws Exception
	{
		Locale.setDefault(TURKISH);
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		// Case-insensitive aliases, as a localized configuration may declare
		config.addI18nAlias(new I18nAliasImpl("img_right", false, Arrays.asList("right")));
		config.addI18nAlias(new I18nAliasImpl("img_link", false, Arrays.asList("link=$1")));

		WtImageLink img = find(
				postprocess(config, "[[File:X.png|RIGHT|LINK=Main Page]]"),
				WtImageLink.class);

		assertNotNull(img);
		assertEquals(ImageHorizAlign.RIGHT, img.getHAlign());
		assertEquals("Main Page", ((WtPageName) img.getLink().getTarget()).getAsString());
	}

	@Test
	public void testDivIsBlockElementUnderTurkishLocale() throws Exception
	{
		String wikitext = "a<DIV>b</DIV>c";

		// The paragraph is closed before the block element
		String expected = render(Locale.US, wikitext);
		assertTrue(expected, expected.contains("a\n</p>\n<DIV>"));

		assertEquals(expected, render(TURKISH, wikitext));
	}

	@Test
	public void testUrlencodeWikiUnderTurkishLocale() throws Exception
	{
		Locale.setDefault(TURKISH);
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		assertEquals("a_b", expand(config, "{{urlencode:a b|wiki}}"));
	}

	@Test
	public void testHtmlNumbersUnderArabicLocale() throws Exception
	{
		String wikitext = "&#1234;\n\n[[File:X.png|thumb|180px|Caption]]";

		String expected = render(Locale.US, wikitext);
		assertTrue(expected, expected.contains("width:182px;"));

		String actual = render(ARABIC, wikitext);
		assertFalse(actual, actual.matches("(?s).*[٠-٩۰-۹].*"));
		assertEquals(expected, actual);
	}

	// =========================================================================

	private static String render(Locale locale, String wikitext) throws Exception
	{
		Locale.setDefault(locale);
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		PageTitle pageTitle = PageTitle.make(config, "Test");
		EngProcessedPage page = postprocess(config, wikitext);
		return HtmlRenderer.print(new TestRendererCallback(), config, pageTitle, page);
	}

	private static EngProcessedPage postprocess(WikiConfigImpl config, String wikitext) throws Exception
	{
		PageId pageId = new PageId(PageTitle.make(config, "Test"), -1);
		return new WtEngineImpl(config).postprocess(pageId, wikitext, new NoPagesCallback());
	}

	private static String expand(WikiConfigImpl config, String wikitext) throws Exception
	{
		PageId pageId = new PageId(PageTitle.make(config, "Test"), -1);
		EngProcessedPage page = new WtEngineImpl(config).expand(pageId, wikitext, new NoPagesCallback());
		return WtRtDataPrinter.print(page.getPage());
	}

	private static <T extends WtNode> T find(WtNode node, Class<T> clazz)
	{
		if (clazz.isInstance(node))
			return clazz.cast(node);
		for (WtNode child : node)
		{
			T found = find(child, clazz);
			if (found != null)
				return found;
		}
		return null;
	}

	// =========================================================================

	private static final class NoPagesCallback
			implements
				ExpansionCallback
	{
		@Override
		public FullPage retrieveWikitext(
				ExpansionFrame expansionFrame,
				PageTitle pageTitle)
		{
			return null;
		}

		@Override
		public String fileUrl(PageTitle pageTitle, int width, int height)
		{
			return null;
		}
	}

	private static final class TestRendererCallback
			implements
				HtmlRendererCallback
	{
		@Override
		public MediaInfo getMediaInfo(String title, int width, int height)
		{
			return new MediaInfo(title, "/wiki/" + title, "/img/" + title, 1000, 800, "/thumb/" + title, 180, 144);
		}

		@Override
		public boolean resourceExists(PageTitle target)
		{
			return true;
		}

		@Override
		public String makeUrl(PageTitle target)
		{
			return "/wiki/" + UrlEncoding.WIKI.encode(target.getNormalizedFullTitle());
		}

		@Override
		public String makeUrl(WtUrl target)
		{
			return target.getProtocol() + ":" + target.getPath();
		}

		@Override
		public String makeUrlMissingTarget(String path)
		{
			return "/wiki/" + path;
		}
	}
}
