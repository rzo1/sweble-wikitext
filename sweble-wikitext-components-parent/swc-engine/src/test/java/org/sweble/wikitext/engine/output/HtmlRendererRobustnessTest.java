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
 * Issue #87: Content that is common in real articles must not make the
 * HtmlRenderer throw.
 */
public class HtmlRendererRobustnessTest
{
	private static final String MAGNIFY_LINK = "<a href=\"/wiki/File:X.png\" class=\"internal\" title=\"Enlarge\">";

	// =========================================================================
	// External links, URLs and images in image captions and headings
	// =========================================================================

	@Test
	public void testExternalLinkWithTitleInThumbCaption() throws Exception
	{
		String html = render("[[File:X.png|thumb|Photo by [http://flickr.com Foo]]]");

		assertContains(html, "<div class=\"thumbcaption\">");
		assertContains(html, "href=\"http://flickr.com\">Foo</a>");
	}

	@Test
	public void testExternalLinkWithTitleInInlineImageCaption() throws Exception
	{
		String html = render("[[File:X.png|Photo by [http://flickr.com Foo]]]");

		assertContains(html, "alt=\"Photo by Foo\"");
		assertContains(html, "title=\"Photo by Foo\"");
	}

	@Test
	public void testUntitledExternalLinkInInlineImageCaption() throws Exception
	{
		String html = render("[[File:X.png|see [http://e.com] and [http://f.com]]]");

		assertContains(html, "alt=\"see [1] and [2]\"");
	}

	@Test
	public void testPlainUrlInInlineImageCaption() throws Exception
	{
		String html = render("[[File:X.png|see http://e.com]]");

		assertContains(html, "alt=\"see http://e.com\"");
		assertContains(html, "title=\"see http://e.com\"");
	}

	@Test
	public void testExternalLinkWithTitleInHeading() throws Exception
	{
		String html = render("== [http://e.com x] ==\nText");

		// Like MediaWiki the whitespace around the heading text is trimmed
		assertContains(html, "id=\"x\"");
		assertContains(html, "href=\"http://e.com\">x</a>");
	}

	@Test
	public void testPlainUrlInHeading() throws Exception
	{
		String html = render("== see http://e.com ==\nText");

		assertContains(html, "id=\"see_http://e.com\"");
	}

	@Test
	public void testUntitledExternalLinkNumberInHeadingMatchesRenderedLink() throws Exception
	{
		String html = render("[http://a.com]\n\n== [http://e.com] ==\nText");

		assertContains(html, "href=\"http://a.com\">[1]</a>");
		assertContains(html, "id=\"[2]\"");
		assertContains(html, "href=\"http://e.com\">[2]</a>");
	}

	@Test
	public void testNestedImageInThumbCaption() throws Exception
	{
		String html = render("[[File:X.png|thumb|cap [[File:Y.png|10px]]]]");

		assertContains(html, "<div class=\"thumbcaption\">");
		assertContains(html, "src=\"/images/Y.png\"");
	}

	@Test
	public void testNestedImageInInlineImageCaption() throws Exception
	{
		String html = render("[[File:X.png|cap [[File:Y.png|10px]]]]");

		// Like MediaWiki the nested image does not contribute any text
		assertContains(html, "alt=\"cap\"");
		assertFalse(html, html.contains("alt=\"cap Y.png"));
	}

	@Test
	public void testNestedImageInHeading() throws Exception
	{
		String html = render("== cap [[File:Y.png|10px]] ==\nText");

		// Like MediaWiki the nested image does not contribute any text
		assertContains(html, "id=\"cap\"");
	}

	@Test
	public void testSignatureInHeadingAndCaption() throws Exception
	{
		String html = render("== Sig ~~~~ ==\n[[File:X.png|by ~~~]]");

		assertContains(html, "id=\"Sig_~~~~\"");
		assertContains(html, "alt=\"by ~~~\"");
	}

	@Test
	public void testTableInInlineImageCaption() throws Exception
	{
		String html = render("[[File:X.png|a\n{|\n|b\n|}\nc]]");

		assertContains(html, "src=\"/images/X.png\"");
	}

	// =========================================================================
	// Signatures
	// =========================================================================

	@Test
	public void testSignaturesAreRenderedLiterally() throws Exception
	{
		String html = render("a ~~~ b ~~~~ c ~~~~~ d");

		assertEquals(
				"a ~~~ b ~~~~ c ~~~~~ d",
				html.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim());
	}

	// =========================================================================
	// link= option of thumbnails of existing files
	// =========================================================================

	@Test
	public void testThumbWithEmptyLinkOption() throws Exception
	{
		String html = render("[[File:X.png|thumb|link=|cap]]");

		// The image itself is not linked ...
		assertFalse(html, Pattern.compile("<a [^>]*>\\s*<img alt=\"\" src=\"/images/X.png\"").matcher(html).find());
		assertContains(html, "<img alt=\"\" src=\"/images/X.png\"");
		// ... but the magnify icon still links to the file description page
		assertContains(html, MAGNIFY_LINK);
		assertContains(html, "cap");
	}

	@Test
	public void testThumbWithUrlLinkOption() throws Exception
	{
		String html = render("[[File:X.png|thumb|link=http://example.com/|cap]]");

		assertImageLinkedTo(html, "http://example.com/");
		assertContains(html, MAGNIFY_LINK);
	}

	@Test
	public void testThumbWithPageLinkOption() throws Exception
	{
		String html = render("[[File:X.png|thumb|link=Main Page|cap]]");

		assertImageLinkedTo(html, "/wiki/Main_Page");
		assertContains(html, MAGNIFY_LINK);
	}

	@Test
	public void testThumbWithoutLinkOption() throws Exception
	{
		String html = render("[[File:X.png|thumb|cap]]");

		assertImageLinkedTo(html, "/wiki/File:X.png");
		assertContains(html, MAGNIFY_LINK);
	}

	@Test
	public void testInlineImageWithEmptyLinkOption() throws Exception
	{
		String html = render("[[File:X.png|link=]]");

		assertFalse(html, html.contains("<a "));
		assertContains(html, "<img alt=\"\" src=\"/images/X.png\"");
	}

	@Test
	public void testInlineImageWithUrlLinkOption() throws Exception
	{
		String html = render("[[File:X.png|link=http://example.com/]]");

		assertImageLinkedTo(html, "http://example.com/");
		assertContains(html, "title=\"http://example.com/\"");
	}

	@Test
	public void testInlineImageWithPageLinkOption() throws Exception
	{
		String html = render("[[File:X.png|link=Main Page]]");

		assertImageLinkedTo(html, "/wiki/Main_Page");
		assertContains(html, "title=\"Main Page\"");
	}

	// =========================================================================

	private static void assertContains(String html, String expected)
	{
		assertTrue("Expected <" + expected + "> in:\n" + html, html.contains(expected));
	}

	private static void assertImageLinkedTo(String html, String href)
	{
		Matcher m = Pattern.compile("<a href=\"([^\"]*)\"[^>]*>\\s*<img [^>]*src=\"/images/X.png\"").matcher(html);
		assertTrue(html, m.find());
		assertEquals(html, href, m.group(1));
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
	 * Every file exists, pages don't.
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
			String name = title.substring(title.indexOf(':') + 1);
			String url = "/images/" + UrlEncoding.WIKI.encode(name);
			int w = width > 0 ? width : 200;
			int h = height > 0 ? height : 100;
			return new MediaInfo(title, "/wiki/" + title, url, 200, 100, url, w, h);
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
