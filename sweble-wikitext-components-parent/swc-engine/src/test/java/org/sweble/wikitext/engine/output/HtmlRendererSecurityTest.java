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
 * Checks that the {@link HtmlRenderer} does not let HTML or script injection
 * through (issues #85 and #86) while still rendering legitimate markup.
 */
public class HtmlRendererSecurityTest
{
	/**
	 * An onmouseover attribute that made it into a tag, i.e. it follows the
	 * closing quote of another attribute value or whitespace.
	 */
	private static final Pattern INJECTED_HANDLER =
			Pattern.compile("<[^>]*[\"'\\s]onmouseover\\s*=");

	// =========================================================================
	// Attribute injection via page titles and image names (#86)

	@Test
	public void testTitleOfMissingPageIsEscaped() throws Exception
	{
		String html = render("[[A\"onmouseover=\"alert(1)]]", false, false);

		assertNoInjectedHandler(html);
		assertContains(html, "title=\"A&quot;onmouseover=&quot;alert(1) (page does not exist)\"");
	}

	@Test
	public void testTitleOfExistingPageIsEscaped() throws Exception
	{
		String html = render("[[A\"onmouseover=\"alert(1)]]", true, false);

		assertNoInjectedHandler(html);
		assertContains(html, "<a href=\"/wiki/A&quot;onmouseover=&quot;alert(1)\" title=\"A&quot;onmouseover=&quot;alert(1)\">");
	}

	@Test
	public void testTitleAndSrcOfImageAreEscaped() throws Exception
	{
		String html = render("[[File:A\"onmouseover=\"alert(1).png]]", false, true);

		assertNoInjectedHandler(html);
		assertContains(html, "title=\"File:A&quot;onmouseover=&quot;alert(1).png\"");
		assertContains(html, "src=\"/images/File:A&quot;onmouseover=&quot;alert(1).png\"");
	}

	@Test
	public void testSrcOfThumbnailIsEscaped() throws Exception
	{
		String html = render("[[File:A\"onmouseover=\"alert(1).png|thumb|Caption]]", false, true);

		assertNoInjectedHandler(html);
		assertContains(html, "src=\"/thumb/File:A&quot;onmouseover=&quot;alert(1).png\"");
	}

	@Test
	public void testTitleOfMissingImageIsEscaped() throws Exception
	{
		String html = render("[[File:A\"onmouseover=\"alert(1).png]]", false, false);

		assertNoInjectedHandler(html);
		assertContains(html, "title=\"File:A&quot;onmouseover=&quot;alert(1).png\"");
	}

	@Test
	public void testTitleOfMissingThumbnailIsEscaped() throws Exception
	{
		String html = render("[[File:A\"onmouseover=\"alert(1).png|thumb]]", false, false);

		assertNoInjectedHandler(html);
		assertContains(html, "title=\"File:A&quot;onmouseover=&quot;alert(1).png\"");
	}

	@Test
	public void testRedirectIsEscaped() throws Exception
	{
		String html = render("#REDIRECT [[A\"onmouseover=\"alert(1)]]", false, false);

		assertNoInjectedHandler(html);
		assertContains(html, "<a href=\"/wiki/A&quot;onmouseover=&quot;alert(1)\">A\"onmouseover=\"alert(1)</a>");
	}

	@Test
	public void testCharRefsInCallbackUrlsAreNotEscapedTwice() throws Exception
	{
		String html = render("[[Missing]]", false, false);

		assertContains(html, "href=\"/w/index.php?title=Missing&amp;action=edit&amp;redlink=1\"");
	}

	@Test
	public void testAmpersandInExternalLinkIsEscaped() throws Exception
	{
		String html = render("[http://example.com/a?b=1&c=2 Example]", false, false);

		assertContains(html, "<a rel=\"nofollow\" class=\"external text\" href=\"http://example.com/a?b=1&amp;c=2\">Example</a>");
	}

	// =========================================================================
	// Elements which are not allowed (#85)

	@Test
	public void testScriptIsEscaped() throws Exception
	{
		String html = render("<script>alert(1)</script>");

		assertNoTag(html, "script");
		assertContains(html, "&lt;script&gt;alert(1)&lt;/script&gt;");
	}

	@Test
	public void testIframeIsEscaped() throws Exception
	{
		String html = render("<iframe src=\"http://evil.example/\"></iframe>");

		assertNoTag(html, "iframe");
		assertContains(html, "&lt;iframe src=&quot;http://evil.example/&quot;&gt;&lt;/iframe&gt;");
	}

	@Test
	public void testObjectIsEscaped() throws Exception
	{
		String html = render("<object data=\"x.swf\"></object>");

		assertNoTag(html, "object");
		assertContains(html, "&lt;object data=&quot;x.swf&quot;&gt;");
	}

	@Test
	public void testStyleElementIsEscaped() throws Exception
	{
		String html = render("<style>body { background: red }</style>");

		assertNoTag(html, "style");
		assertContains(html, "&lt;style&gt;body { background: red }&lt;/style&gt;");
	}

	@Test
	public void testAnchorWithJavascriptHrefIsEscaped() throws Exception
	{
		String html = render("<a href=\"javascript:alert(1)\">x</a>");

		assertNoTag(html, "a");
		assertContains(html, "&lt;a href=&quot;javascript:alert(1)&quot;&gt;x&lt;/a&gt;");
	}

	@Test
	public void testImgWithEventHandlerIsEscaped() throws Exception
	{
		String html = render("<img src=x onerror=alert(1)>");

		assertNoTag(html, "img");
		assertContains(html, "&lt;img src=&quot;x&quot; onerror=&quot;alert(1)&quot;&gt;");
	}

	@Test
	public void testUnknownElementsAreEscaped() throws Exception
	{
		String html = render("<foo bar=\"1\">x</foo> <baz/>");

		assertNoTag(html, "foo");
		assertNoTag(html, "baz");
		assertContains(html, "&lt;foo bar=&quot;1&quot;&gt;x&lt;/foo&gt;");
		assertContains(html, "&lt;baz /&gt;");
	}

	@Test
	public void testTagExtensionsAreNotEmittedAsRawTags() throws Exception
	{
		// gallery and references are tag extensions, not HTML elements
		String html = render("<gallery>\nFile:A.png\n</gallery> <references/>");

		assertNoTag(html, "gallery");
		assertNoTag(html, "references");
	}

	// =========================================================================
	// Attributes which are not allowed (#85)

	@Test
	public void testEventHandlersAndDisallowedAttributesAreDropped() throws Exception
	{
		String html = render("<span onclick=\"alert(1)\" ONMOUSEOVER=\"alert(2)\" href=\"http://x\" class=\"c\">x</span>");

		assertContains(html, "<span class=\"c\">x</span>");
	}

	@Test
	public void testDataAttributesAreAllowedButNotReservedOnes() throws Exception
	{
		String html = render("<div class=\"c\" data-x=\"1\" data-mw=\"2\" data-parsoid=\"3\">x</div>");

		assertContains(html, "<div class=\"c\" data-x=\"1\">");
	}

	@Test
	public void testJavascriptUrlInCiteIsDropped() throws Exception
	{
		String html = render("<blockquote cite=\" JavaScript:alert(1)\">x</blockquote>"
				+ "<q cite=\"http://example.com/\">y</q>");

		assertContains(html, "<blockquote>");
		assertContains(html, "<q cite=\"http://example.com/\">y</q>");
	}

	@Test
	public void testUnsafeCssIsReplaced() throws Exception
	{
		String[] styles = {
				"background:url(javascript:alert(1))",
				"width:expression(alert(1))",
				"-moz-binding:url(http://evil.example/xss.xml#xss)",
				"behavior: url(xss.htc)",
				"background-image: image(evil.png)",
				"color: red; background: j&#97;vascript:alert(1)",
				"width: \\65xpression(alert(1))",
		};

		for (String style : styles)
		{
			String html = render("<span style=\"" + style + "\">x</span>");
			assertContains(html, "<span style=\"/* insecure input */\">x</span>");
		}
	}

	@Test
	public void testCommentsCannotHideExpression() throws Exception
	{
		String html = render("<span style=\"width: ex/**/pression(alert(1))\">x</span>");

		assertFalse(html, html.contains("expression"));
	}

	@Test
	public void testTableAttributesAreSanitized() throws Exception
	{
		String html = render(""
				+ "{| border=\"1\" onclick=\"alert(1)\" style=\"width:expression(1)\"\n"
				+ "|+ onmouseover=\"alert(2)\" | Caption\n"
				+ "|- onclick=\"alert(3)\" class=\"row\"\n"
				+ "| onclick=\"alert(4)\" style=\"background:url(x)\" colspan=\"2\" | Cell\n"
				+ "! onclick=\"alert(5)\" scope=\"col\" | Head\n"
				+ "|}");

		assertFalse(html, html.contains("onclick"));
		assertFalse(html, html.contains("onmouseover"));
		assertFalse(html, html.contains("url("));
		assertContains(html, "<table border=\"1\" style=\"/* insecure input */\">");
		assertContains(html, "<caption>");
		assertContains(html, "<tr class=\"row\">");
		assertContains(html, "<td style=\"/* insecure input */\" colspan=\"2\">");
		assertContains(html, "<th scope=\"col\">");
	}

	// =========================================================================
	// Legitimate markup is still rendered

	@Test
	public void testSpanWithStyleIsRendered() throws Exception
	{
		String html = render("<span style=\"color:red\">x</span>");

		assertContains(html, "<span style=\"color:red\">x</span>");
	}

	@Test
	public void testDivWithClassIsRendered() throws Exception
	{
		String html = render("<div class=\"note\" id=\"n1\" title=\"Note\">x</div>");

		assertContains(html, "<div class=\"note\" id=\"n1\" title=\"Note\">");
		assertContains(html, "</div>");
	}

	@Test
	public void testTableWithAttributesIsRendered() throws Exception
	{
		String html = render(""
				+ "{| class=\"wikitable\" border=\"2\" style=\"width:100%\"\n"
				+ "|- style=\"color:red\"\n"
				+ "| align=\"center\" rowspan=\"2\" | Cell\n"
				+ "|}");

		assertContains(html, "<table class=\"wikitable\" border=\"2\" style=\"width:100%\">");
		assertContains(html, "<tr style=\"color:red\">");
		assertContains(html, "<td align=\"center\" rowspan=\"2\">");
	}

	@Test
	public void testExternalLinkWithSafeHrefIsRendered() throws Exception
	{
		String html = render("[https://example.com/page Example]");

		assertContains(html, "<a rel=\"nofollow\" class=\"external text\" href=\"https://example.com/page\">Example</a>");
	}

	@Test
	public void testFormattingElementsAreRendered() throws Exception
	{
		String html = render("<b>b</b> <sup>2</sup> <code>c</code> x<br/>y <abbr title=\"abbreviation\">abbr</abbr>");

		assertContains(html, "<b>b</b>");
		assertContains(html, "<sup>2</sup>");
		assertContains(html, "<code>c</code>");
		assertContains(html, "<br />");
		assertContains(html, "<abbr title=\"abbreviation\">abbr</abbr>");
	}

	// =========================================================================

	// Table structure and elements created by the tree builder

	@Test
	public void testHtmlTableKeepsStructure() throws Exception
	{
		String html = render("<table><tr><td>x</td></tr></table>");

		assertNoEscapedMarkup(html);
		assertInOrder(html, "<table>", "<tr>", "<td>", "x", "</td>", "</tr>", "</table>");
	}

	@Test
	public void testHtmlTableWithCaptionKeepsStructure() throws Exception
	{
		String html = render("<table><caption>c</caption><tr><th>h</th></tr></table>");

		assertNoEscapedMarkup(html);
		assertInOrder(html, "<table>", "<caption>", "c", "</caption>", "<tr>", "<th>", "h", "</th>", "</tr>", "</table>");
	}

	@Test
	public void testWikitableWithHtmlRowKeepsStructure() throws Exception
	{
		String html = render("{|\n|-\n| a\n<tr><td>b</td></tr>\n|}");

		assertNoEscapedMarkup(html);
		assertInOrder(html, "<table>", "<tr>", "<td>", "a", "</td>", "</tr>", "<tr>", "<td>", "b", "</td>", "</tr>", "</table>");
	}

	@Test
	public void testUnclosedHtmlTableKeepsStructure() throws Exception
	{
		String html = render("<table><tr><td>a</table>");

		assertNoEscapedMarkup(html);
		assertInOrder(html, "<table>", "<tr>", "<td>", "a", "</td>", "</tr>", "</table>");
	}

	@Test
	public void testTableSectionsAreRendered() throws Exception
	{
		String html = render(""
				+ "<table>"
				+ "<thead><tr><th>h</th></tr></thead>"
				+ "<tbody class=\"b\" onclick=\"alert(1)\"><tr><td>x</td></tr></tbody>"
				+ "<tfoot><tr><td>f</td></tr></tfoot>"
				+ "</table>");

		assertNoEscapedMarkup(html);
		assertFalse(html, html.contains("onclick"));
		assertInOrder(html, "<table>", "<thead>", "<th>", "</thead>", "<tbody class=\"b\">", "<td>", "</tbody>", "<tfoot>", "</tfoot>", "</table>");
	}

	@Test
	public void testColgroupIsRendered() throws Exception
	{
		String html = render("<table><colgroup span=\"2\" onclick=\"alert(1)\"></colgroup><tr><td>x</td></tr></table>");

		assertNoEscapedMarkup(html);
		assertInOrder(html, "<table>", "<colgroup span=\"2\">", "</colgroup>", "<tr>", "</table>");
	}

	@Test
	public void testColgroupWithColIsRendered() throws Exception
	{
		// Issue #105
		String html = render("<table><colgroup><col span=\"2\"/></colgroup><tr><td>a</td><td>b</td></tr></table>");

		assertNoEscapedMarkup(html);
		assertInOrder(html, "<table>", "<colgroup>", "<col span=\"2\" />", "</colgroup>", "<tr>", "<td>", "a", "</td>", "<td>", "b", "</td>", "</tr>", "</table>");
	}

	@Test
	public void testColWithoutColgroupIsRendered() throws Exception
	{
		String html = render("<table><col span=\"2\" onclick=\"alert(1)\"/><tr><td>a</td></tr></table>");

		assertNoEscapedMarkup(html);
		assertFalse(html, html.contains("onclick"));
		assertInOrder(html, "<table>", "<colgroup>", "<col span=\"2\" />", "</colgroup>", "<tr>", "<td>", "a", "</td>", "</tr>", "</table>");
	}

	@Test
	public void testRepairedElementsAreRendered() throws Exception
	{
		String html = render("<b>a<i>b</b>c</i>") + render("<p>a<div>b</div>c</p>");

		assertNoEscapedMarkup(html);
		assertContains(html, "<b>a<i>b</i></b><i>c</i>");
		assertInOrder(html, "<p>", "a", "</p>", "<div>", "b", "</div>", "c", "<p>", "</p>");
	}

	@Test
	public void testStrayEndTagsDoNotCreateElements() throws Exception
	{
		String html = render("a</script>b</iframe>c</tbody>d");

		assertNoTag(html, "script");
		assertNoTag(html, "iframe");
		assertNoTag(html, "tbody");
	}

	@Test
	public void testHtmlAndBodyWrittenInSourceAreEscaped() throws Exception
	{
		String html = render("<html><body>x</body></html>");

		assertNoTag(html, "html");
		assertNoTag(html, "body");
		assertContains(html, "&lt;html&gt;&lt;body&gt;x&lt;/body&gt;&lt;/html&gt;");
	}

	// =========================================================================

	private static void assertNoEscapedMarkup(String html)
	{
		assertFalse(html, html.contains("&lt;"));
	}

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

	private static void assertNoInjectedHandler(String html)
	{
		assertFalse(html, INJECTED_HANDLER.matcher(html).find());
	}

	private static void assertNoTag(String html, String name)
	{
		assertFalse(html, html.contains("<" + name + " ") || html.contains("<" + name + ">"));
		assertFalse(html, html.contains("</" + name + ">"));
	}

	private static String render(String wikitext) throws Exception
	{
		return render(wikitext, false, false);
	}

	private static String render(
			String wikitext,
			boolean pagesExist,
			boolean mediaExists) throws Exception
	{
		WikiConfig config = DefaultConfigEnWp.generate();
		WtEngineImpl engine = new WtEngineImpl(config);
		PageTitle pageTitle = PageTitle.make(config, "Test page");
		EngProcessedPage cp = engine.postprocess(new PageId(pageTitle, -1), wikitext, null);
		return HtmlRenderer.print(
				new TestCallback(pagesExist, mediaExists),
				config,
				pageTitle,
				cp.getPage());
	}

	// =========================================================================

	private static final class TestCallback
			implements
				HtmlRendererCallback
	{
		private final boolean pagesExist;

		private final boolean mediaExists;

		public TestCallback(boolean pagesExist, boolean mediaExists)
		{
			this.pagesExist = pagesExist;
			this.mediaExists = mediaExists;
		}

		@Override
		public boolean resourceExists(PageTitle target)
		{
			return pagesExist;
		}

		@Override
		public MediaInfo getMediaInfo(String title, int width, int height)
		{
			if (!mediaExists)
				return null;

			// Deliberately naive: the title is put into the URL unencoded
			return new MediaInfo(
					title,
					"/desc/" + title,
					"/images/" + title,
					100,
					100,
					"/thumb/" + title,
					50,
					50);
		}

		@Override
		public String makeUrl(PageTitle target)
		{
			// Deliberately naive: the title is put into the URL unencoded
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
