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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.parser.nodes.WtUrl;

/**
 * Issue #89: Heading ids and links to sections must look like the ones
 * MediaWiki generates.
 */
public class HtmlRendererHeadingIdTest
{
	private static final Pattern HEADING_ID =
			Pattern.compile("<span class=\"mw-headline\" id=\"([^\"]*)\">");

	// =========================================================================
	// Heading ids
	// =========================================================================

	@Test
	public void testWhitespaceAroundHeadingIsTrimmed() throws Exception
	{
		assertHeadingIds("== Foo ==\nText", "Foo");
	}

	@Test
	public void testSpacesBecomeUnderscores() throws Exception
	{
		assertHeadingIds("==  Foo   bar_baz  ==\nText", "Foo_bar_baz");
	}

	@Test
	public void testDuplicateHeadingsGetSuffix() throws Exception
	{
		assertHeadingIds(
				"== Same ==\n== Same ==\n== Same ==\n",
				"Same", "Same_2", "Same_3");
	}

	@Test
	public void testDuplicatesAcrossHeadingLevels() throws Exception
	{
		assertHeadingIds(
				"== A ==\n=== A ===\n==== A ====\n",
				"A", "A_2", "A_3");
	}

	@Test
	public void testDuplicatesIgnoreAsciiCase() throws Exception
	{
		assertHeadingIds(
				"== Foo ==\n== FOO ==\n== foo ==\n",
				"Foo", "FOO_2", "foo_3");
	}

	@Test
	public void testDuplicatesDoNotIgnoreNonAsciiCase() throws Exception
	{
		assertHeadingIds("== Über ==\n== über ==\n", "Über", "über");
	}

	@Test
	public void testDuplicateCountingSkipsExistingSuffixes() throws Exception
	{
		assertHeadingIds(
				"== Foo_2 ==\n== Foo ==\n== Foo ==\n",
				"Foo_2", "Foo", "Foo_3");

		assertHeadingIds(
				"== Foo ==\n== Foo ==\n== Foo 2 ==\n",
				"Foo", "Foo_2", "Foo_2_2");
	}

	@Test
	public void testDuplicatesWithDifferentMarkup() throws Exception
	{
		assertHeadingIds("== ''Foo'' ==\n== Foo ==\n", "Foo", "Foo_2");
	}

	@Test
	public void testMarkupIsStripped() throws Exception
	{
		String html = render("== ''a'' '''b''' <span class=\"x\">c</span> ==\nText");

		assertHeadingIds(html, Arrays.asList("a_b_c"));
		assertContains(html, "<i>a</i>");
	}

	@Test
	public void testLinksContributeTheirText() throws Exception
	{
		assertHeadingIds(
				"== See [[foo bar]]s and [[Target|label]] ==\nText",
				"See_foo_bars_and_label");
	}

	@Test
	public void testCategoryLinkIsIgnored() throws Exception
	{
		assertHeadingIds("== Foo [[Category:X]] ==\nText", "Foo");
	}

	@Test
	public void testEntitiesAreDecoded() throws Exception
	{
		assertHeadingIds(
				"== caf&eacute; &#233;t&#xE9; ==\nText",
				"café_été");
	}

	@Test
	public void testSpecialCharactersAreEscapedInAttribute() throws Exception
	{
		String html = render("== A &amp; B &lt;c&gt; &quot;d&quot; ==\nText");

		assertContains(html, "id=\"A_&amp;_B_&lt;c&gt;_&quot;d&quot;\"");
	}

	@Test
	public void testEntitiesAreDecodedOnlyOnce() throws Exception
	{
		String html = render("== &amp;amp; ==\nText");

		assertContains(html, "id=\"&amp;amp;\"");
	}

	@Test
	public void testNonBreakingSpaceIsWhitespace() throws Exception
	{
		assertHeadingIds("== a&nbsp;b ==\nText", "a_b");
	}

	@Test
	public void testNonAsciiTextIsKept() throws Exception
	{
		assertHeadingIds("== Über Straße 日本 ==\nText", "Über_Straße_日本");
	}

	@Test
	public void testPercentSignIsKept() throws Exception
	{
		assertHeadingIds("== 100% ==\nText", "100%");
	}

	// =========================================================================
	// Links to sections
	// =========================================================================

	@Test
	public void testFragmentOnlyLink() throws Exception
	{
		String html = render("[[#Foo]]");

		assertContains(html, "<a href=\"#Foo\">#Foo</a>");
		assertFalse(html, html.contains("class=\"new\""));
	}

	@Test
	public void testFragmentOnlyLinkWithTitle() throws Exception
	{
		assertContains(render("[[#Foo bar|label]]"), "<a href=\"#Foo_bar\">label</a>");
	}

	@Test
	public void testFragmentOnlyLinkWithLinkTrail() throws Exception
	{
		assertContains(render("[[#Foo]]s"), "<a href=\"#Foo\">#Foos</a>");
	}

	@Test
	public void testFragmentOnlyLinkWithUnderscores() throws Exception
	{
		assertContains(render("[[#Foo_bar]]"), "<a href=\"#Foo_bar\">#Foo_bar</a>");
	}

	@Test
	public void testFragmentOnlyLinkWithNonAsciiText() throws Exception
	{
		assertContains(render("[[#Über uns]]"), "<a href=\"#Über_uns\">#Über uns</a>");
	}

	@Test
	public void testFragmentOnlyLinkWithEntity() throws Exception
	{
		assertContains(render("[[#a&amp;b]]"), "href=\"#a&amp;b\"");
	}

	@Test
	public void testLinkToCurrentPageWithFragment() throws Exception
	{
		assertContains(
				render("[[Example#Sec]]"),
				"<a href=\"#Sec\" class=\"mw-selflink-fragment\">Example#Sec</a>");
	}

	@Test
	public void testLinkToCurrentPageWithFragmentAndTitle() throws Exception
	{
		assertContains(
				render("[[example#Sec two|x]]"),
				"<a href=\"#Sec_two\" class=\"mw-selflink-fragment\">x</a>");
	}

	@Test
	public void testLinkToOtherPageWithFragment() throws Exception
	{
		String html = render("[[Other#Sec]]");

		assertFalse(html, html.contains("href=\"#Sec\""));
		assertContains(html, "class=\"new\"");
	}

	@Test
	public void testFragmentLinkMatchesHeadingId() throws Exception
	{
		String html = render("== Foo bar ==\n[[#Foo bar]] [[#Foo_bar]] [[Example#Foo bar]]");

		assertHeadingIds(html, Arrays.asList("Foo_bar"));
		assertContains(html, "<a href=\"#Foo_bar\">#Foo bar</a>");
		assertContains(html, "<a href=\"#Foo_bar\">#Foo_bar</a>");
		assertContains(html, "<a href=\"#Foo_bar\" class=\"mw-selflink-fragment\">Example#Foo bar</a>");
	}

	// =========================================================================

	private static void assertContains(String html, String expected)
	{
		assertTrue("Expected <" + expected + "> in:\n" + html, html.contains(expected));
	}

	private static void assertHeadingIds(String wikitext, String... expected) throws Exception
	{
		assertHeadingIds(render(wikitext), Arrays.asList(expected));
	}

	private static void assertHeadingIds(String html, List<String> expected)
	{
		List<String> actual = new ArrayList<String>();
		Matcher m = HEADING_ID.matcher(html);
		while (m.find())
			actual.add(m.group(1));
		assertEquals(html, expected, actual);
	}

	private static String render(String wikitext) throws Exception
	{
		WikiConfig config = DefaultConfigEnWp.generate();
		WtEngineImpl engine = new WtEngineImpl(config);

		PageTitle pageTitle = PageTitle.make(config, "Example");
		PageId pageId = new PageId(pageTitle, -1);

		EngProcessedPage cp = engine.postprocess(pageId, wikitext, null);

		return HtmlRenderer.print(new TestCallback(), config, pageTitle, cp.getPage());
	}

	// =========================================================================

	/**
	 * No page exists.
	 */
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
