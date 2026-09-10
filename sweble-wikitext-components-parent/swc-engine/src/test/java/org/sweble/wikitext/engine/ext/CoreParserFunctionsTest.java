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

package org.sweble.wikitext.engine.ext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.FullPage;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.output.HtmlRenderer;
import org.sweble.wikitext.engine.output.HtmlRendererCallback;
import org.sweble.wikitext.engine.output.MediaInfo;
import org.sweble.wikitext.engine.utils.AnchorEncoder;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.parser.nodes.WtUrl;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

/**
 * Core parser functions and magic words (formatnum, padright, anchorencode,
 * plural, grammar, int, the URL functions, nse, DISPLAYTITLE, {{=}}, msgnw,
 * ...).
 *
 * The expected values are what MediaWiki produces for the English Wikipedia.
 */
public class CoreParserFunctionsTest
{
	private final WikiConfigImpl config = DefaultConfigEnWp.generate();

	private final WtEngineImpl engine = new WtEngineImpl(config);

	/**
	 * The pages that can be transcluded, by full title.
	 */
	private final Map<String, String> pages = new HashMap<String, String>();

	// =========================================================================
	// == formatnum

	@Test
	public void testFormatnumGroupsDigits() throws Exception
	{
		assertEquals("1,234,567.891", expand("{{formatnum:1234567.891}}"));
		assertEquals("1,234,567", expand("{{formatnum:1234567}}"));
		assertEquals("123", expand("{{formatnum:123}}"));
		assertEquals("1,000.50", expand("{{formatnum:1000.50}}"));
		assertEquals("0.5", expand("{{formatnum:0.5}}"));
		assertEquals("12,345,678,901", expand("{{FORMATNUM:12345678901}}"));
	}

	@Test
	public void testFormatnumUsesMinusSign() throws Exception
	{
		assertEquals("−1,234.5", expand("{{formatnum:-1234.5}}"));
		assertEquals("−0", expand("{{formatnum:-0}}"));
	}

	@Test
	public void testFormatnumWithRawSuffix() throws Exception
	{
		assertEquals("1234567.891", expand("{{formatnum:1,234,567.891|R}}"));
		assertEquals("-1234", expand("{{formatnum:−1,234|R}}"));
		assertEquals("INF", expand("{{formatnum:∞|R}}"));
		// The raw suffix is case-sensitive
		assertEquals("1,234", expand("{{formatnum:1234|r}}"));
	}

	@Test
	public void testFormatnumWithoutSeparators() throws Exception
	{
		assertEquals("1234567", expand("{{formatnum:1234567|NOSEP}}"));
		assertEquals("−12", expand("{{formatnum:-12|nosep}}"));
	}

	@Test
	public void testFormatnumLossless() throws Exception
	{
		assertEquals("1,234", expand("{{formatnum:1234|LOSSLESS}}"));
	}

	@Test
	public void testFormatnumFormatsNumbersInText() throws Exception
	{
		assertEquals("1,000 and 2,000", expand("{{formatnum:1000 and 2000}}"));
		assertEquals("abc", expand("{{formatnum:abc}}"));
		assertEquals("", expand("{{formatnum:}}"));
		assertEquals("∞", expand("{{formatnum:INF}}"));
		assertEquals("NaN", expand("{{formatnum:NAN}}"));
	}

	// =========================================================================
	// == padleft, padright

	@Test
	public void testPadright() throws Exception
	{
		assertEquals("700", expand("{{padright:7|3}}"));
		assertEquals("abcxyx", expand("{{padright:abc|6|xy}}"));
		assertEquals("abc", expand("{{padright:abc|2}}"));
		assertEquals("abb", expand("{{PADRIGHT:a|3.9|b}}"));
		assertEquals("a", expand("{{padright:a|-3|b}}"));
	}

	@Test
	public void testPadrightCountsCodePoints() throws Exception
	{
		assertEquals("äöö", expand("{{padright:ä|3|ö}}"));
		assertEquals("a😀😀", expand("{{padright:a|3|😀}}"));
	}

	@Test
	public void testPadrightIsLimitedTo500Characters() throws Exception
	{
		assertEquals(500, expand("{{padright:|1000|x}}").length());
	}

	@Test
	public void testPadleftStillPadsInFront() throws Exception
	{
		assertEquals("007", expand("{{padleft:7|3}}"));
	}

	// =========================================================================
	// == anchorencode

	@Test
	public void testAnchorencode() throws Exception
	{
		assertEquals("a_b", expand("{{anchorencode:a b}}"));
		assertEquals("a_b", expand("{{ANCHORENCODE:  a  _ b  }}"));
		assertEquals("äöü", expand("{{anchorencode:äöü}}"));
		assertEquals("100%_sure", expand("{{anchorencode:100% sure}}"));
		assertEquals("%2541", expand("{{anchorencode:%41}}"));
	}

	@Test
	public void testAnchorencodeStripsMarkup() throws Exception
	{
		assertEquals("bar_baz", expand("{{anchorencode:[[Foo|bar]] baz}}"));
		assertEquals("Foo_baz", expand("{{anchorencode:[[Foo]] baz}}"));
		assertEquals("text", expand("{{anchorencode:[http://example.org text]}}"));
		assertEquals("bold_text", expand("{{anchorencode:'''bold''' text}}"));
		assertEquals("a_b", expand("{{anchorencode:<span>a</span> b}}"));
	}

	@Test
	public void testAnchorencodeDecodesCharacterReferences() throws Exception
	{
		assertEquals("x_&_y", expand("{{anchorencode:x &amp; y}}"));
		assertEquals("a_b", expand("{{anchorencode:a&#32;b}}"));
	}

	@Test
	public void testAnchorencodeEscapesWikitext() throws Exception
	{
		assertEquals("a&#123;b&#125;", expand("{{anchorencode:a{b} }}"));
		assertEquals("http&#58;//example.org", expand("{{anchorencode:http://example.org}}"));
	}

	@Test
	public void testAnchorEncoderEncodesHeadingIds() throws Exception
	{
		assertEquals("a_b", AnchorEncoder.encodeId(" a  b "));
		assertEquals("%41", AnchorEncoder.encodeId("%41"));
		assertEquals("%2541", AnchorEncoder.encodeLinkFragment("%41"));
		assertEquals("a_b", AnchorEncoder.encodeId("a b"));
	}

	// =========================================================================
	// == plural

	@Test
	public void testPlural() throws Exception
	{
		assertEquals("are", expand("{{plural:2|is|are}}"));
		assertEquals("is", expand("{{plural:1|is|are}}"));
		assertEquals("are", expand("{{plural:0|is|are}}"));
		assertEquals("is", expand("{{plural:-1|is|are}}"));
		assertEquals("are", expand("{{plural:1.5|is|are}}"));
		assertEquals("are", expand("{{plural:1,000|is|are}}"));
		assertEquals("are", expand("{{PLURAL:2|is|are}}"));
	}

	@Test
	public void testPluralUsesLastFormIfFormsAreMissing() throws Exception
	{
		assertEquals("one", expand("{{plural:5|one}}"));
		assertEquals("", expand("{{plural:5}}"));
	}

	@Test
	public void testPluralWithExplicitForms() throws Exception
	{
		assertEquals("none", expand("{{plural:0|0=none|one|many}}"));
		assertEquals("one", expand("{{plural:1|0=none|one|many}}"));
		assertEquals("many", expand("{{plural:5|0=none|one|many}}"));
		assertEquals("twelve", expand("{{plural:12|12=twelve|one|many}}"));
	}

	// =========================================================================
	// == grammar

	@Test
	public void testGrammarReturnsWordUnchanged() throws Exception
	{
		assertEquals("noun", expand("{{grammar:N|noun}}"));
		assertEquals("Wikipedia", expand("{{GRAMMAR:genitive|Wikipedia}}"));
		assertEquals("", expand("{{grammar:genitive}}"));
	}

	// =========================================================================
	// == int

	@Test
	public void testIntShowsMissingMessage() throws Exception
	{
		assertEquals("⧼mainpage⧽", expand("{{int:mainpage}}"));
		assertEquals("⧼a&lt;b⧽", expand("{{INT:a<b}}"));
	}

	// =========================================================================
	// == localurl, fullurl, canonicalurl

	@Test
	public void testLocalurl() throws Exception
	{
		assertEquals("/?title=Foo_bar", expand("{{localurl:Foo bar}}"));
		assertEquals("/?title=Foo&action=edit", expand("{{localurl:Foo|action=edit}}"));
		assertEquals("/?title=Foo", expand("{{localurl:Foo#Bar}}"));
		assertEquals("/?title=File:X.png", expand("{{localurl:Media:X.png}}"));
	}

	@Test
	public void testLocalurle() throws Exception
	{
		assertEquals("/?title=Foo&amp;a=1&amp;b=2", expand("{{localurle:Foo|a=1&b=2}}"));
	}

	@Test
	public void testFullurlAndCanonicalurl() throws Exception
	{
		assertEquals("http://localhost/?title=Foo_bar", expand("{{fullurl:Foo bar}}"));
		assertEquals("http://localhost/?title=Foo#Bar_baz", expand("{{fullurl:Foo#Bar baz}}"));
		assertEquals("http://localhost/?title=Foo&amp;a=1&amp;b=2", expand("{{fullurle:Foo|a=1&b=2}}"));
		assertEquals("http://localhost/?title=Foo", expand("{{canonicalurl:Foo}}"));
		assertEquals("http://localhost/?title=Foo&amp;a=1&amp;b=2", expand("{{canonicalurle:Foo|a=1&b=2}}"));
	}

	@Test
	public void testUrlsUseArticlePathAndScript() throws Exception
	{
		config.setWikiUrl("https://en.wikipedia.org/w/index.php");
		config.setArticlePath("https://en.wikipedia.org/wiki/$1");

		assertEquals("/wiki/Foo_bar", expand("{{localurl:Foo bar}}"));
		assertEquals("/w/index.php?title=Foo&action=edit", expand("{{localurl:Foo|action=edit}}"));
		assertEquals("/wiki/Foo", expand("{{localurl:Foo|}}"));
		assertEquals("https://en.wikipedia.org/wiki/Foo_bar", expand("{{fullurl:Foo bar}}"));
		assertEquals("https://en.wikipedia.org/w/index.php?title=Foo&action=edit", expand("{{fullurl:Foo|action=edit}}"));
		assertEquals("https://en.wikipedia.org/wiki/Foo", expand("{{canonicalurl:Foo}}"));
	}

	// =========================================================================
	// == nse

	@Test
	public void testNse() throws Exception
	{
		assertEquals("User_talk", expand("{{nse:User talk}}"));
		assertEquals("User_talk", expand("{{nse:3}}"));
		assertEquals("User talk", expand("{{ns:3}}"));
		assertEquals("", expand("{{NSE:0}}"));
	}

	// =========================================================================
	// == SERVER, SERVERNAME, SCRIPTPATH

	@Test
	public void testServerVariables() throws Exception
	{
		assertEquals("http://localhost", expand("{{SERVER}}"));
		assertEquals("localhost", expand("{{SERVERNAME}}"));
		assertEquals("", expand("{{SCRIPTPATH}}"));
	}

	@Test
	public void testServerVariablesOfWikiWithScriptPath() throws Exception
	{
		config.setWikiUrl("https://en.wikipedia.org/w/index.php");

		assertEquals("https://en.wikipedia.org", expand("{{SERVER}}"));
		assertEquals("en.wikipedia.org", expand("{{SERVERNAME}}"));
		assertEquals("/w", expand("{{SCRIPTPATH}}"));
	}

	// =========================================================================
	// == DISPLAYTITLE, DEFAULTSORT

	@Test
	public void testDisplaytitleRendersNothing() throws Exception
	{
		assertEquals("", expand("{{DISPLAYTITLE:x}}"));
		assertEquals("ab", expand("a{{DISPLAYTITLE:''Test''|noreplace}}b"));
	}

	@Test
	public void testDefaultsortRendersNothing() throws Exception
	{
		assertEquals("", expand("{{DEFAULTSORT:Foo}}"));
		assertEquals("", expand("{{DEFAULTSORT:Foo|noerror}}"));
		assertEquals("", expand("{{DEFAULTSORTKEY:Foo}}"));
	}

	// =========================================================================
	// == {{=}}

	@Test
	public void testEquals() throws Exception
	{
		assertEquals("=", expand("{{=}}"));
		assertEquals("a=b", expand("a{{ = }}b"));
		assertEquals("a=b", expand("{{#if:x|a{{=}}b}}"));
	}

	// =========================================================================
	// == msgnw

	@Test
	public void testMsgnwReturnsSourceOfTemplate() throws Exception
	{
		pages.put("Template:Foo", "'''{{{1}}}''' [[x]]");

		String expanded = expand("{{msgnw:Foo}}");
		assertTrue(expanded, expanded.contains("'''{{{1}}}''' [[x]]"));

		String html = render("{{msgnw:Foo}}");
		// The source is shown as text (nowiki escapes only <, > and &, like MediaWiki)
		assertTrue(html, html.contains("'''{{{1}}}''' [[x]]"));
		assertFalse(html, html.contains("<b>"));
		assertFalse(html, html.contains("<a "));
	}

	@Test
	public void testMsgnwOfPageInMainNamespace() throws Exception
	{
		pages.put("Main", "main page");

		String expanded = expand("{{MSGNW::Main}}");
		assertTrue(expanded, expanded.contains("main page"));
	}

	@Test
	public void testMsgnwOfMissingTemplate() throws Exception
	{
		String html = render("{{msgnw:Missing}}");
		assertTrue(html, html.contains("[[:Template:Missing]]"));
		assertFalse(html, html.contains("<a "));
	}

	// =========================================================================
	// == lc, uc, lcfirst, ucfirst

	@Test
	public void testCaseFunctionsWithNonAsciiText() throws Exception
	{
		assertEquals("äöü", expand("{{lc:ÄÖÜ}}"));
		assertEquals("STRASSE", expand("{{uc:straße}}"));
		assertEquals("Éclair", expand("{{ucfirst:éclair}}"));
		assertEquals("ärger", expand("{{lcfirst:Ärger}}"));
		assertEquals("𐐀x", expand("{{ucfirst:𐐨x}}"));
		assertEquals("𐐨X", expand("{{lcfirst:𐐀X}}"));
	}

	@Test
	public void testCaseFunctionsDoNotDependOnDefaultLocale() throws Exception
	{
		Locale defaultLocale = Locale.getDefault();
		try
		{
			Locale.setDefault(new Locale("tr"));

			assertEquals("I", expand("{{uc:i}}"));
			assertEquals("i", expand("{{lc:I}}"));
			assertEquals("Istanbul", expand("{{ucfirst:istanbul}}"));
		}
		finally
		{
			Locale.setDefault(defaultLocale);
		}
	}

	// =========================================================================

	private String expand(String wikitext) throws Exception
	{
		PageId pageId = new PageId(PageTitle.make(config, "Test"), -1);

		EngProcessedPage page = engine.expand(pageId, wikitext, new MapCallback());

		return WtRtDataPrinter.print(page.getPage());
	}

	private String render(String wikitext) throws Exception
	{
		PageTitle pageTitle = PageTitle.make(config, "Test");
		PageId pageId = new PageId(pageTitle, -1);

		EngProcessedPage page = engine.postprocess(pageId, wikitext, new MapCallback());

		return HtmlRenderer.print(new TestRendererCallback(), config, pageTitle, page);
	}

	// =========================================================================

	private final class MapCallback
			implements
				ExpansionCallback
	{
		@Override
		public FullPage retrieveWikitext(
				ExpansionFrame expansionFrame,
				PageTitle pageTitle)
		{
			String text = pages.get(pageTitle.getPrefixedText());
			if (text == null)
				return null;
			return new FullPage(new PageId(pageTitle, -1), text);
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
			return null;
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
			return "";
		}

		@Override
		public String makeUrlMissingTarget(String path)
		{
			return "/wiki/" + path;
		}
	}
}
