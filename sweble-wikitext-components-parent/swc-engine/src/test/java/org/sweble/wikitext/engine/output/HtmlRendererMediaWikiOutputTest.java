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
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.nodes.WtUrl;

/**
 * Checks that the {@link HtmlRenderer} output matches the output of
 * MediaWiki (issue #90).
 */
public class HtmlRendererMediaWikiOutputTest
{
	// =========================================================================
	// Image layout

	@Test
	public void testImageWithAlignmentNoneDoesNotFloat() throws Exception
	{
		String html = render("[[File:X.png|none]]");

		assertContains(html, "<div class=\"floatnone\">");
		assertNotContains(html, "tright");
	}

	@Test
	public void testImageWithAlignmentLeftOrRightFloats() throws Exception
	{
		String left = render("[[File:X.png|left]]");
		String right = render("[[File:X.png|right]]");

		assertContains(left, "<div class=\"floatleft\">");
		assertNotContains(left, "\"tleft\"");
		assertContains(right, "<div class=\"floatright\">");
		assertNotContains(right, "\"tright\"");
	}

	@Test
	public void testCenteredImageIsWrappedInCenterDiv() throws Exception
	{
		assertInOrder(render("[[File:X.png|center]]"),
				"<div class=\"center\">",
				"<div class=\"floatnone\">",
				"<img ",
				"</div>",
				"</div>");

		assertInOrder(render("[[File:X.png|thumb|center|Caption]]"),
				"<div class=\"center\">",
				"<div class=\"thumb tnone\">",
				"<div class=\"thumbinner\"");
	}

	@Test
	public void testThumbnailAlignment() throws Exception
	{
		assertContains(render("[[File:X.png|thumb|Caption]]"), "<div class=\"thumb tright\">");
		assertContains(render("[[File:X.png|thumb|left|Caption]]"), "<div class=\"thumb tleft\">");
		assertContains(render("[[File:X.png|thumb|none|Caption]]"), "<div class=\"thumb tnone\">");
	}

	@Test
	public void testFramedImageHasFrameAndCaptionAtFullSize() throws Exception
	{
		String html = render("[[File:X.png|frame|100px|Caption]]");

		assertInOrder(html,
				"<div class=\"thumb tright\">",
				"<div class=\"thumbinner\" style=\"width:402px;\">",
				"<img alt=\"\" src=\"/images/File:X.png\" width=\"400\" height=\"200\" class=\"thumbimage\" />",
				"<div class=\"thumbcaption\">Caption</div>");
		assertNotContains(html, "magnify");
	}

	@Test
	public void testHeightOnlyScalesImage() throws Exception
	{
		String html = render("[[File:X.png|x50px]]");

		assertContains(html, "src=\"/thumb/File:X.png\" width=\"100\" height=\"50\"");
	}

	@Test
	public void testHeightLimitsGivenWidth() throws Exception
	{
		String html = render("[[File:X.png|300x50px]]");

		assertContains(html, "src=\"/thumb/File:X.png\" width=\"100\" height=\"50\"");
	}

	@Test
	public void testImageWithZeroHeightDoesNotFail() throws Exception
	{
		String html = render("[[File:X.png|x50px]]", new TestCallback(400, 0));

		assertContains(html, "<img ");
	}

	// =========================================================================
	// Category and media links

	@Test
	public void testCategoryLinkWithLeadingColonIsRendered() throws Exception
	{
		String html = render("[[:Category:X]]");

		assertContains(html, "<a href=\"/wiki/Category:X\" title=\"Category:X\">Category:X</a>");
	}

	@Test
	public void testCategoryLinkIsNotRendered() throws Exception
	{
		assertNotContains(render("[[Category:X]]"), "<a ");
	}

	@Test
	public void testMediaLinkPointsToFile() throws Exception
	{
		assertContains(render("[[Media:X.png]]"),
				"<a href=\"/images/File:X.png\" class=\"internal\" title=\"X.png\">Media:X.png</a>");
		assertContains(render("[[Media:X.png|the file]]"),
				"<a href=\"/images/File:X.png\" class=\"internal\" title=\"X.png\">the file</a>");
	}

	@Test
	public void testMediaLinkToMissingFile() throws Exception
	{
		String html = render("[[Media:X.png]]", new TestCallback(-1, -1));

		assertContains(html,
				"<a href=\"/w/index.php?title=File:X.png&amp;action=edit&amp;redlink=1\" class=\"new\" title=\"X.png\">Media:X.png</a>");
	}

	// =========================================================================
	// Table attributes

	@Test
	public void testTableAttributesAreKept() throws Exception
	{
		String html = render(""
				+ "{| align=\"center\" width=\"10\"\n"
				+ "|+ align=\"left\" | Caption\n"
				+ "|- align=\"right\"\n"
				+ "| align=\"center\" width=\"20%\" | Cell\n"
				+ "|}");

		assertContains(html, "<table align=\"center\" width=\"10\">");
		assertContains(html, "<caption align=\"left\">");
		assertContains(html, "<tr align=\"right\">");
		assertContains(html, "<td align=\"center\" width=\"20%\">");
		assertNotContains(html, "style=");
	}

	@Test
	public void testAlignAndWidthAreKeptOnElements() throws Exception
	{
		String html = render("<div align=\"center\" width=\"10\">x</div>");

		assertContains(html, "<div align=\"center\">");
	}

	// =========================================================================
	// URL and entity escaping

	@Test
	public void testAmpersandInUrlIsEscaped() throws Exception
	{
		String html = render("http://e.com/?a=1&copy=2");

		assertContains(html, "href=\"http://e.com/?a=1&amp;copy=2\">http://e.com/?a=1&amp;copy=2</a>");
	}

	@Test
	public void testInvalidCharReferencesAreEscaped() throws Exception
	{
		String html = render("a &#0; &#x1; &#128; &foo; &amp; &#65; &nbsp; b");

		assertContains(html, "a &amp;#0; &amp;#x1; &amp;#128; &amp;foo; &amp; &#65; &nbsp; b");
	}

	@Test
	public void testInvalidCharReferencesInCaptionAreEscaped() throws Exception
	{
		String html = render("[[File:X.png|&foo; &#0;]]");

		assertContains(html, "title=\"&amp;foo; &amp;#0;\"");
	}

	// =========================================================================
	// Self-closing tags

	@Test
	public void testSelfClosingSpanBecomesEmptyElement() throws Exception
	{
		assertContains(render("a<span/>b"), "a<span></span>b");
	}

	@Test
	public void testSelfClosingDivDoesNotWrapFollowingContent() throws Exception
	{
		String html = render("<div/>after");

		assertInOrder(html, "<div></div>", "after");
		assertNotContains(html.substring(html.indexOf("after")), "</div>");
	}

	@Test
	public void testVoidElementsStaySelfClosing() throws Exception
	{
		String html = render("a<br/>b<hr/>");

		assertContains(html, "<br />");
		assertContains(html, "<hr />");
		assertNotContains(html, "</br>");
	}

	// =========================================================================
	// Link classes

	@Test
	public void testExternalLinkClasses() throws Exception
	{
		assertContains(render("[http://e.com]"),
				"<a rel=\"nofollow\" class=\"external autonumber\" href=\"http://e.com\">[1]</a>");
		assertContains(render("[http://e.com Text]"),
				"<a rel=\"nofollow\" class=\"external text\" href=\"http://e.com\">Text</a>");
		assertContains(render("http://e.com"),
				"<a rel=\"nofollow\" class=\"external free\" href=\"http://e.com\">http://e.com</a>");
	}

	// =========================================================================
	// Cells, headers and captions

	@Test
	public void testCellsWithTrailingWhitespaceAreNotWrappedInParagraph() throws Exception
	{
		String html = render(""
				+ "{|\n"
				+ "|+ Caption \n"
				+ "! Header \n"
				+ "|-\n"
				+ "| Cell 1 || Cell 2 \n"
				+ "| Cell 3\n"
				+ "|}");

		assertNotContains(html, "<p>");
		assertInOrder(html,
				"<caption>", "Caption", "</caption>",
				"<th>", "Header", "</th>",
				"<td>", "Cell 1", "</td>",
				"<td>", "Cell 2", "</td>",
				"<td>", "Cell 3", "</td>");
	}

	@Test
	public void testCellsWithSeveralParagraphsKeepParagraphs() throws Exception
	{
		String html = render("{|\n| a\n\nb\n|}");

		assertContains(html, "<p>");
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

	private static void assertNotContains(String html, String unexpected)
	{
		assertFalse("Unexpected <" + unexpected + "> in:\n" + html, html.contains(unexpected));
	}

	private static String render(String wikitext) throws Exception
	{
		return render(wikitext, new TestCallback(400, 200));
	}

	private static String render(String wikitext, TestCallback callback) throws Exception
	{
		WikiConfig config = DefaultConfigEnWp.generate();
		WtEngineImpl engine = new WtEngineImpl(config);
		PageTitle pageTitle = PageTitle.make(config, "Test page");
		EngProcessedPage cp = engine.postprocess(new PageId(pageTitle, -1), wikitext, null);
		return HtmlRenderer.print(callback, config, pageTitle, cp.getPage());
	}

	// =========================================================================

	/**
	 * Knows all pages and a file of the given size (or no file if the width
	 * is negative).
	 */
	private static final class TestCallback
			implements
				HtmlRendererCallback
	{
		private final int fileWidth;

		private final int fileHeight;

		public TestCallback(int fileWidth, int fileHeight)
		{
			this.fileWidth = fileWidth;
			this.fileHeight = fileHeight;
		}

		@Override
		public boolean resourceExists(PageTitle target)
		{
			return true;
		}

		@Override
		public MediaInfo getMediaInfo(String title, int width, int height)
		{
			if (fileWidth < 0)
				return null;

			int thumbWidth = (width > 0) ? width : fileWidth;
			int thumbHeight = (height > 0) ? height : fileHeight;
			return new MediaInfo(
					title,
					"/wiki/" + title,
					"/images/" + title,
					fileWidth,
					fileHeight,
					"/thumb/" + title,
					thumbWidth,
					thumbHeight);
		}

		@Override
		public String makeUrl(PageTitle target)
		{
			return "/wiki/" + target.getNormalizedFullTitle().replace(' ', '_');
		}

		@Override
		public String makeUrl(WtUrl target)
		{
			if (target.getProtocol().isEmpty())
				return target.getPath();
			return target.getProtocol() + ":" + target.getPath();
		}

		@Override
		public String makeUrlMissingTarget(String path)
		{
			return "/w/index.php?title=" + path + "&amp;action=edit&amp;redlink=1";
		}
	}
}
