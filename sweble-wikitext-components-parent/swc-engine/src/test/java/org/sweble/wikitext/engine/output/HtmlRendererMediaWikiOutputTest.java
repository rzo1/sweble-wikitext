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

import java.util.regex.Pattern;

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
	public void testParameterizedImageOptionsAreNotCaptions() throws Exception
	{
		// Like in MediaWiki the last caption wins, options are no captions
		String html = render("[[File:X.png|thumb|Caption|upright=1.5|class=noviewer|lang=de|page=2]]");

		assertInOrder(html, "<div class=\"thumbcaption\">", "Caption", "</div>");
		assertNotContains(html, "upright=1.5");
		assertNotContains(html, "class=noviewer");
		assertNotContains(html, "lang=de");
		assertNotContains(html, "page=2");
	}

	@Test
	public void testManualThumbIsNotCaption() throws Exception
	{
		String html = render("[[File:X.png|Caption|thumb=Y.png]]");

		assertContains(html, "<div class=\"thumb tright\">");
		assertNotContains(html, "thumb=Y.png");
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
	// Block output of tag extensions (issue #136)

	@Test
	public void testBlockTagExtensionsCloseParagraph() throws Exception
	{
		assertInOrder(render("a <syntaxhighlight lang=\"java\">int x;</syntaxhighlight> b"),
				"<p>", "a", "</p>", "<pre class=\"mw-highlight lang-java\">int x;</pre>", "<p>", "b", "</p>");
		assertInOrder(render("a <poem>\nx\ny\n</poem> b"),
				"<p>", "a", "</p>", "<div class=\"poem\">x<br />", "</div>", "<p>", "b", "</p>");
		assertInOrder(render("a <gallery>\nFile:X.png\n</gallery> b"),
				"<p>", "a", "</p>", "<div class=\"mw-ext-gallery\">", "</div>", "<p>", "b", "</p>");
	}

	@Test
	public void testBlockTagExtensionAloneIsNotWrappedInParagraph() throws Exception
	{
		String html = render("<poem>x</poem>");

		assertContains(html, "<div class=\"poem\">x</div>");
		assertNotContains(html, "<p>");
	}

	@Test
	public void testInlineTagExtensionsStayInParagraph() throws Exception
	{
		String html = render("a <syntaxhighlight lang=\"x\" inline>y</syntaxhighlight> <math>z</math> b");

		assertInOrder(html,
				"<p>", "a", "<code class=\"mw-highlight lang-x\">y</code>", "<span class=\"mw-ext-math\">z</span>", "b", "</p>");
		assertEquals(html, 1, html.split("<p>", -1).length - 1);
	}

	// =========================================================================
	// Unexpanded templates and parameters (issue #136)

	@Test
	public void testUnexpandedTemplatesAndParametersAreShownAsText() throws Exception
	{
		assertContains(render("a {{foo|b=c}} d {{{1|x}}} e"), "a {{foo|b=c}} d {{{1|x}}} e");
	}

	@Test
	public void testUnexpandedTemplatesAreEscaped() throws Exception
	{
		String html = render("{{foo|<b>x</b>|\"&\"}}");

		assertContains(html, "{{foo|&lt;b&gt;x&lt;/b&gt;|&quot;&amp;&quot;}}");
		assertNotContains(html, "<b>");
	}

	@Test
	public void testUnexpandedTemplateInAttributeValue() throws Exception
	{
		assertContains(render("<span title=\"a {{x}}\">y</span>"), "<span title=\"a {{x}}\">y</span>");
	}

	// =========================================================================
	// Void elements (issue #136)

	@Test
	public void testMetaAndLinkDoNotWrapFollowingContent() throws Exception
	{
		String meta = render("<meta itemprop=\"a\" content=\"b\">text");
		String link = render("<link itemprop=\"a\" href=\"http://e.com/\">text");

		assertContains(meta, "<meta itemprop=\"a\" content=\"b\" />text");
		assertNotContains(meta, "</meta>");
		assertContains(link, "<link itemprop=\"a\" href=\"http://e.com/\" />text");
		assertNotContains(link, "</link>");
	}

	// =========================================================================
	// Empty paragraphs (issue #136)

	@Test
	public void testNoEmptyParagraphs() throws Exception
	{
		String[] inputs = {
				"a\n\n<div>x</div>\n\nb",
				"[[Category:X]]\n\ntext",
				"__NOTOC__\n\ntext",
				"<templatestyles src=\"x\"/>\n\ntext",
				"<!-- c -->\n\ntext",
				"a\n<pre>x</pre>\n\n<pre>y</pre>\nb",
		};

		for (String input : inputs)
		{
			String html = render(input);
			assertFalse(html, EMPTY_PARAGRAPH.matcher(html).find());
			assertContains(html, "<p>");
		}
	}

	// =========================================================================
	// Title of frameless images (issue #136)

	@Test
	public void testFramelessImageGetsCaptionAsTitle() throws Exception
	{
		// Like MediaWiki's Linker::makeImageLink()
		assertContains(render("[[File:X.png|frameless|Cap]]"),
				"<a href=\"/wiki/File:X.png\" class=\"image\" title=\"Cap\"><img alt=\"Cap\" ");
		assertContains(render("[[File:X.png|frameless|link=Main Page]]"),
				"<a href=\"/wiki/Main_Page\" title=\"Main Page\">");
	}

	@Test
	public void testFramelessImageWithoutCaptionHasNoTitle() throws Exception
	{
		assertContains(render("[[File:X.png|frameless]]"),
				"<a href=\"/wiki/File:X.png\" class=\"image\"><img alt=\"\" ");
	}

	// =========================================================================

	private static final Pattern EMPTY_PARAGRAPH = Pattern.compile("<p>\\s*</p>");

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
