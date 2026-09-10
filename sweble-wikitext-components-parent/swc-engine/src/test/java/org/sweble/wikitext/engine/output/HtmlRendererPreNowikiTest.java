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
package org.sweble.wikitext.engine.output;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.FullPage;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.ParserConfigImpl;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.nodes.WtUrl;

/**
 * Checks that the {@link HtmlRenderer} renders {@code <pre>}, {@code <nowiki>}
 * and preformatted blocks (indent-pre) like MediaWiki (issue #88).
 *
 * The tag extensions are rendered without expansion (they stay tag extension
 * nodes) and with expansion (they become a pre element and a nowiki node).
 */
public class HtmlRendererPreNowikiTest
{
	// =========================================================================
	// <pre>

	@Test
	public void testPreKeepsWhitespaceAndEntities() throws Exception
	{
		for (String html : renderBoth("<pre>a\n   b    c &lt;x&gt; ''y''</pre>"))
		{
			assertContains(html, "<pre>a\n   b    c &lt;x&gt; ''y''</pre>");
			assertFalse(html, html.contains("<p>"));
			assertFalse(html, html.contains("\t"));
			assertFalse(html, html.contains("&amp;"));
		}
	}

	@Test
	public void testPreIsNotWrappedInParagraph() throws Exception
	{
		for (String html : renderBoth("x\n<pre>a</pre>\ny"))
		{
			assertInOrder(html, "<p>", "x", "</p>", "<pre>a</pre>", "<p>", "y", "</p>");
			assertTrue(html, html.indexOf("</p>") < html.indexOf("<pre>"));
		}
	}

	@Test
	public void testMarkupInPreIsNotInterpreted() throws Exception
	{
		for (String html : renderBoth("<pre><b>x</b> [[y]] {{z}}</pre>"))
			assertContains(html, "<pre>&lt;b&gt;x&lt;/b&gt; [[y]] {{z}}</pre>");
	}

	@Test
	public void testNowikiTagsInPreAreRemoved() throws Exception
	{
		for (String html : renderBoth("<pre>a <nowiki>''b'' <i>c</i></nowiki> <NOWIKI>d</NOWIKI></pre>"))
			assertContains(html, "<pre>a ''b'' &lt;i&gt;c&lt;/i&gt; d</pre>");
	}

	@Test
	public void testCharRefsInPreAreKeptAndBareAmpersandIsEscaped() throws Exception
	{
		for (String html : renderBoth("<pre>a & b &amp; &#60; &#x3C; \"c\"</pre>"))
			assertContains(html, "<pre>a &amp; b &amp; &#60; &#x3C; \"c\"</pre>");
	}

	@Test
	public void testInvalidCharRefsInPreAreEscaped() throws Exception
	{
		// Issue #136: Like in normal text
		for (String html : renderBoth("<pre>&#0; &#xD800; &#99999999999; &foo; &amp; &#65; &nbsp;</pre>"))
			assertContains(html, "<pre>&amp;#0; &amp;#xD800; &amp;#99999999999; &amp;foo; &amp; &#65; &nbsp;</pre>");
	}

	@Test
	public void testBlankLinesInPreAreKept() throws Exception
	{
		for (String html : renderBoth("<pre>a\n\n\nb\n</pre>"))
			assertContains(html, "<pre>a\n\n\nb\n</pre>");
	}

	@Test
	public void testPreInNestedBlockIsNotIndented() throws Exception
	{
		for (String html : renderBoth("{|\n|\n<pre>a\n  b</pre>\n|}"))
			assertContains(html, "<pre>a\n  b</pre>");
	}

	@Test
	public void testAttributesOfPreAreSanitized() throws Exception
	{
		for (String html : renderBoth("<pre class=\"c\" onclick=\"alert(1)\" style=\"width:expression(1)\">a</pre>"))
			assertContains(html, "<pre class=\"c\" style=\"/* insecure input */\">a</pre>");
	}

	// =========================================================================
	// <nowiki>

	@Test
	public void testNowikiKeepsEntities() throws Exception
	{
		for (String html : renderBoth("<nowiki>&amp; &lt;</nowiki>"))
		{
			assertContains(html, "&amp; &lt;");
			assertFalse(html, html.contains("&amp;amp;"));
			assertFalse(html, html.contains("&amp;lt;"));
		}
	}

	@Test
	public void testMarkupInNowikiIsNotInterpreted() throws Exception
	{
		for (String html : renderBoth("x <nowiki>''a'' [[b]] <b>c</b> {{d}} &#60;</nowiki> y"))
		{
			assertContains(html, "x ''a'' [[b]] &lt;b&gt;c&lt;/b&gt; {{d}} &#60; y");
			assertFalse(html, html.contains("<b>"));
			assertFalse(html, html.contains("<a "));
			assertFalse(html, html.contains("<i>"));
		}
	}

	@Test
	public void testNowikiIsNotRenderedAsBlock() throws Exception
	{
		for (String html : renderBoth("x <nowiki>a & b</nowiki> y"))
		{
			assertContains(html, "x a &amp; b y");
			assertFalse(html, html.contains("<div"));
		}
	}

	@Test
	public void testLanguageConverterMarkupInNowikiIsEscaped() throws Exception
	{
		for (String html : renderBoth("<nowiki>-{a}-</nowiki>"))
			assertContains(html, "-&#123;a&#125;-");
	}

	@Test
	public void testInvalidCharRefsInNowikiAreDecodedToReplacementChar() throws Exception
	{
		// Issue #166: Like MediaWiki invalid numeric references become U+FFFD,
		// unknown entities stay text
		for (String html : renderBoth("x <nowiki>&#0; &#xD800; &#X110000; &#99999999999; &foo; &amp; &#65; &#X41; &nbsp;</nowiki> y"))
			assertContains(html, "x � � � � &amp;foo; &amp; &#65; &#X41; &nbsp; y");
	}

	@Test
	public void testNowikiInPreformattedBlock() throws Exception
	{
		for (String html : renderBoth(" a <nowiki><b>b</b>  &amp;</nowiki>"))
			assertContains(html, "<pre>a &lt;b&gt;b&lt;/b&gt;  &amp;</pre>");
	}

	// =========================================================================
	// Preformatted blocks (indent-pre)

	@Test
	public void testLeadingSpaceOfEachLineIsRemoved() throws Exception
	{
		for (String html : renderBoth(" a\n  b\n c <b>d</b> &amp;"))
			assertContains(html, "<pre>a\n b\nc <b>d</b> &amp;</pre>");
	}

	@Test
	public void testLineWithOnlyASpaceBecomesEmptyLine() throws Exception
	{
		for (String html : renderBoth(" a\n \n b"))
			assertContains(html, "<pre>a\n\nb</pre>");
	}

	@Test
	public void testMarkupAtLineStartIsNotIndented() throws Exception
	{
		for (String html : renderBoth("{|\n|\n a\n <b>b</b>\n|}"))
			assertContains(html, "<pre>a\n<b>b</b>");
	}

	@Test
	public void testLeadingSpaceIsRemovedIfPreservedByParser() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		((ParserConfigImpl) config.getParserConfig()).setPreserveSemiPreLeadingSpace(true);

		String html = render(config, " a\n  b\n c", false);

		assertContains(html, "<pre>a\n b\nc</pre>");
	}

	// =========================================================================

	private static void assertInOrder(String html, String... parts)
	{
		int pos = 0;
		for (String part : parts)
		{
			int i = html.indexOf(part, pos);
			assertTrue("Expected <" + part + "> after position " + pos + " in:\n" + html, i >= 0);
			pos = i + part.length();
		}
	}

	private static void assertContains(String html, String expected)
	{
		assertTrue("Expected <" + expected + "> in:\n" + html, html.contains(expected));
	}

	/**
	 * @return The rendered page without and with expansion.
	 */
	private static String[] renderBoth(String wikitext) throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		return new String[] {
				render(config, wikitext, false),
				render(config, wikitext, true) };
	}

	private static String render(
			WikiConfigImpl config,
			String wikitext,
			boolean expand) throws Exception
	{
		WtEngineImpl engine = new WtEngineImpl(config);
		PageTitle pageTitle = PageTitle.make(config, "Test page");
		EngProcessedPage cp = engine.postprocess(
				new PageId(pageTitle, -1),
				wikitext,
				expand ? new NoPagesCallback() : null);
		return HtmlRenderer.print(new TestCallback(), config, pageTitle, cp.getPage());
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
			return "/wiki/" + target.getNormalizedFullTitle().replace(' ', '_');
		}

		@Override
		public String makeUrl(WtUrl target)
		{
			return target.getProtocol() + ":" + target.getPath();
		}

		@Override
		public String makeUrlMissingTarget(String path)
		{
			return "/w/index.php?title=" + path + "&amp;action=edit&amp;redlink=1";
		}
	}
}
