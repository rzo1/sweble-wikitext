/**
 * Copyright 2011 The Open Source Research Group,
 *                University of Erlangen-Nürnberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */
package org.sweble.wom3.swcadapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.net.URL;

import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wikitext.engine.config.ParserConfigImpl;
import org.sweble.wom3.Wom3Document;
import org.sweble.wom3.Wom3ElementNode;
import org.sweble.wom3.Wom3ExtLink;
import org.sweble.wom3.Wom3Image;
import org.sweble.wom3.Wom3ImageCaption;
import org.sweble.wom3.Wom3ImageFormat;
import org.sweble.wom3.Wom3ImageHAlign;
import org.sweble.wom3.Wom3Node;
import org.sweble.wom3.Wom3Section;
import org.sweble.wom3.Wom3Title;
import org.sweble.wom3.swcadapter.nodes.SwcNode;
import org.sweble.wom3.util.Wom3Toolbox;

/**
 * Tests the round trip Wikitext -> AST -> WOM -> {@link FixWomRtd} -> Wikitext
 * for unchanged and for edited WOM documents.
 */
public class FixWomRtdTest
		extends
			WtWom3IntegrationTestBase
{
	public FixWomRtdTest()
	{
		super(getTestResourcesFixture());
	}

	// =========================================================================
	// Round trip of unchanged documents

	@Test
	public void testNativeTableRoundTrip() throws Exception
	{
		assertRoundTrip(""
				+ "{| class=\"wikitable\" style=\"width:100%\"\n"
				+ "|+ A caption\n"
				+ "|-\n"
				+ "! Header 1 !! Header 2\n"
				+ "|- style=\"color:red\"\n"
				+ "| Cell 1 || style=\"x\" | Cell 2\n"
				+ "|}\n");
	}

	@Test
	public void testHtmlTableRoundTrip() throws Exception
	{
		assertRoundTrip(""
				+ "<table border=\"1\">\n"
				+ "<caption>Caption</caption>\n"
				+ "<tr><th>H</th><td>C</td></tr>\n"
				+ "</table>\n");
	}

	@Test
	public void testTableWithColgroupRoundTrip() throws Exception
	{
		assertRoundTrip(""
				+ "<table>\n"
				+ "<colgroup><col span=\"2\" /></colgroup>\n"
				+ "<tr><td>C</td></tr>\n"
				+ "</table>\n");
	}

	@Test
	public void testNativeListsRoundTrip() throws Exception
	{
		assertRoundTrip(""
				+ "* Item 1\n"
				+ "** Item 1.1\n"
				+ "* Item 2\n"
				+ "\n"
				+ "# One\n"
				+ "# Two\n"
				+ "\n"
				+ "; Term : Definition\n"
				+ ": Another definition\n");
	}

	@Test
	public void testHtmlListsRoundTrip() throws Exception
	{
		assertRoundTrip(""
				+ "<ul>\n"
				+ "<li>Item 1</li>\n"
				+ "<li>Item 2</li>\n"
				+ "</ul>\n"
				+ "<dl><dt>Term</dt><dd>Def</dd></dl>\n");
	}

	@Test
	public void testInlineDefinitionListDefRoundTrip() throws Exception
	{
		assertRoundTrip("Some <dd>inline</dd> text\n");
	}

	@Test
	public void testAttributesRoundTrip() throws Exception
	{
		assertRoundTrip(""
				+ "<span style=\"color:red\" class='a b'>Text</span>\n"
				+ "\n"
				+ "<div id=foo title=\"&amp;\">Block</div>\n"
				+ "\n"
				+ "<span &garbage />\n");
	}

	@Test
	public void testNativeFormattingRoundTrip() throws Exception
	{
		assertRoundTrip(""
				+ "''italics'' and '''bold''' and '''''both'''''\n"
				+ "\n"
				+ "a\n"
				+ "----\n"
				+ "b\n"
				+ "\n"
				+ "<b>b</b> <i>i</i> <hr />\n");
	}

	@Test
	public void testMiscHtmlElementsRoundTrip() throws Exception
	{
		assertRoundTrip(""
				+ "<abbr title=\"t\">a</abbr> <del>d</del> <ins>i</ins> "
				+ "<dfn>d</dfn> <kbd>k</kbd> <samp>s</samp> <var>v</var> "
				+ "<s>s</s> <u>u</u> <tt>t</tt> <sub>1</sub> <sup>2</sup>\n");
	}

	@Test
	public void testGenericXmlElementRoundTrip() throws Exception
	{
		assertRoundTrip("<address class=\"x\">An address</address>\n");
	}

	@Test
	public void testTagExtensionsRoundTrip() throws Exception
	{
		assertRoundTrip(""
				+ "Text<ref name=\"n\">A reference</ref> more text.\n"
				+ "\n"
				+ "<pre attr=\"value\">I'm a tag extension</pre>\n"
				+ "\n"
				+ "<references />\n", null);
	}

	@Test
	public void testNowikiRoundTrip() throws Exception
	{
		assertRoundTrip("Some <nowiki>''not italic''</nowiki> text\n");
	}

	@Test
	public void testLanguageConversionRoundTrip() throws Exception
	{
		// Without variants language conversion markup is text
		ParserConfigImpl pc = getWikiConfig().getParserConfig();
		pc.addLctVariantMapping("zh-hans", "zh-hans");
		pc.addLctVariantMapping("zh-hant", "zh-hant");
		pc.addLctVariantMapping("zh-cn", "zh-cn");
		pc.addLctVariantMapping("zh-tw", "zh-tw");

		String wm = ""
				+ "A -{zh-hans:x; zh-hant:y}- B\n"
				+ "\n"
				+ "-{plain text}-\n"
				+ "\n"
				+ "-{A|zh-cn:a;zh-tw:b}-\n"
				+ "\n"
				+ "-{H|zh-cn:c;zh-tw:d}-\n"
				+ "\n"
				+ "-{R|raw}-\n";

		assertRoundTrip(wm);
		assertRoundTrip(wm, null);
	}

	@Test
	public void testPageSwitchRoundTrip() throws Exception
	{
		assertRoundTrip("__NOTOC__\nSome text\n");
	}

	@Test
	public void testLinksAndReferencesRoundTrip() throws Exception
	{
		assertRoundTrip(""
				+ "[[Target]] [[Target|Title]] pre[[Target]]post\n"
				+ "\n"
				+ "[http://example.org Title] http://example.org\n"
				+ "\n"
				+ "&amp; &#65; &nbsp;\n");
	}

	@Test
	public void testSectionsAndCommentsRoundTrip() throws Exception
	{
		assertRoundTrip(""
				+ "== Heading ==\n"
				+ "Text <!-- comment --> more\n"
				+ "\n"
				+ "=== Sub ===\n"
				+ " semi pre\n");
	}

	@Test
	public void testSignaturesAndRedirectRoundTrip() throws Exception
	{
		assertRoundTrip("#REDIRECT [[Target]]\n\n~~~ ~~~~ ~~~~~\n");
	}

	@Test
	public void testSoftErrorIsConverted() throws Exception
	{
		// The failing expression is expanded to an EngSoftErrorNode
		Wom3Document wom = toWom("{{#expr: 1 +}}\n", new TestExpansionCallback());

		assertNotNull(fix(wom));
	}

	@Test
	public void testTemplatesRoundTrip() throws Exception
	{
		assertRoundTrip("{{Template|a=b|c}} {{{param|default}}}\n", null);
	}

	// =========================================================================
	// Nowiki insertion

	@Test
	public void testNowikiIsInsertedBeforeListCharacterAtLineStart() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode p = findFirst(wom, "p");
		Wom3ElementNode text = findFirst(p, "text");
		text.setTextContent("Some\n\n* text");

		assertEquals("Some\n<nowiki>*</nowiki> text\n", fix(wom));
	}

	// =========================================================================
	// Edited documents

	@Test
	public void testNewBoldIsRenderedAsTicks() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode p = findFirst(wom, "p");
		p.insertBefore(elem(wom, "b", "bold"), findFirst(p, "text"));

		assertEquals("'''bold'''Some text\n", fix(wom));
	}

	@Test
	public void testNewItalicsIsRenderedAsTicks() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode p = findFirst(wom, "p");
		p.insertBefore(elem(wom, "i", "italics"), findFirst(p, "text"));

		assertEquals("''italics''Some text\n", fix(wom));
	}

	@Test
	public void testNewNestedBoldItalicsIsRenderedAsTicks() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode p = findFirst(wom, "p");
		Wom3ElementNode b = elem(wom, "b", null);
		b.appendChild(elem(wom, "i", "both"));
		p.insertBefore(b, findFirst(p, "text"));

		assertEquals("'''''both'''''Some text\n", fix(wom));
	}

	@Test
	public void testNewHorizontalRuleIsRenderedAsDashes() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode body = findFirst(wom, "body");
		body.appendChild(elem(wom, "hr", null));

		assertEquals("Some text\n----", fix(wom));
	}

	@Test
	public void testNewSpanIsRenderedAsHtmlTag() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode p = findFirst(wom, "p");
		Wom3ElementNode span = elem(wom, "span", "span");
		span.setAttribute("style", "color:red");
		p.insertBefore(span, findFirst(p, "text"));

		assertEquals("<span style=\"color:red\">span</span>Some text\n", fix(wom));
	}

	@Test
	public void testNewUnderlineIsRenderedAsHtmlTag() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode p = findFirst(wom, "p");
		p.insertBefore(elem(wom, "u", "u"), findFirst(p, "text"));

		assertEquals("<u>u</u>Some text\n", fix(wom));
	}

	@Test
	public void testNewEmptyBreakIsRenderedAsEmptyHtmlTag() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode p = findFirst(wom, "p");
		p.insertBefore(elem(wom, "br", null), findFirst(p, "text"));

		assertEquals("<br />Some text\n", fix(wom));
	}

	@Test
	public void testNewElementInExistingBoldIsRendered() throws Exception
	{
		Wom3Document wom = toWom("'''bold'''\n", new TestExpansionCallback());

		// Insert in front of the closing ticks
		Wom3ElementNode b = findFirst(wom, "b");
		b.insertBefore(elem(wom, "i", "it"), b.getLastChild());

		assertEquals("'''bold''it'''''\n", fix(wom));
	}

	@Test
	public void testNewNativeTableIsRenderedAsHtmlTable() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode td = elem(wom, "td", "cell");
		Wom3ElementNode tr = elem(wom, "tr", null);
		tr.appendChild(td);
		Wom3ElementNode tbody = elem(wom, "tbody", null);
		tbody.appendChild(tr);
		Wom3ElementNode table = elem(wom, "table", null);
		table.setAttribute("border", "1");
		table.appendChild(tbody);
		findFirst(wom, "body").appendChild(table);

		assertEquals(""
				+ "Some text\n"
				+ "<table border=\"1\"><tbody><tr><td>cell</td></tr></tbody></table>",
				fix(wom));
	}

	@Test
	public void testNewUnorderedListIsRenderedAsNativeList() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		findFirst(wom, "body").appendChild(list(wom, "ul", "li", "a", "b"));

		assertEquals("Some text\n* a\n* b", fix(wom));
	}

	@Test
	public void testNewNestedListIsRenderedAsNativeList() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode ul = list(wom, "ul", "li", "a", "b");
		ul.getFirstChild().appendChild(list(wom, "ol", "li", "x", "y"));
		findFirst(wom, "body").appendChild(ul);

		assertEquals("Some text\n* a\n*# x\n*# y\n* b", fix(wom));
	}

	@Test
	public void testNewDefinitionListIsRenderedAsNativeList() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode dl = elem(wom, "dl", null);
		dl.appendChild(elem(wom, "dt", "Term"));
		dl.appendChild(elem(wom, "dd", "Def"));
		findFirst(wom, "body").appendChild(dl);

		assertEquals("Some text\n; Term\n: Def", fix(wom));
	}

	@Test
	public void testNewListWithMultiLineItemIsRenderedAsHtmlList() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		findFirst(wom, "body").appendChild(list(wom, "ul", "li", "a\nb"));

		assertEquals("Some text\n<ul><li>a\nb</li></ul>", fix(wom));
	}

	@Test
	public void testNewListInParsedListItemIsRenderedAsNativeList() throws Exception
	{
		Wom3Document wom = toWom("* a\n* b\n", new TestExpansionCallback());

		findFirst(wom, "li").appendChild(list(wom, "ul", "li", "x"));

		assertEquals("* a\n** x\n* b\n", fix(wom));
	}

	@Test
	public void testNewItemAppendedToNativeList() throws Exception
	{
		Wom3Document wom = toWom("* a\n* b\n", new TestExpansionCallback());

		findFirst(wom, "ul").appendChild(elem(wom, "li", "c"));

		assertEquals("* a\n* b\n* c\n", fix(wom));
	}

	@Test
	public void testNewItemInsertedIntoNativeList() throws Exception
	{
		Wom3Document wom = toWom("* a\n* b\n", new TestExpansionCallback());

		Wom3ElementNode ul = findFirst(wom, "ul");
		ul.insertBefore(elem(wom, "li", "x"), ul.getLastChild());

		assertEquals("* a\n* x\n* b\n", fix(wom));
	}

	@Test
	public void testNewItemAppendedToNestedNativeList() throws Exception
	{
		Wom3Document wom = toWom("* a\n** b\n", new TestExpansionCallback());

		findFirst(findFirst(wom, "li"), "ul").appendChild(elem(wom, "li", "c"));

		assertEquals("* a\n** b\n** c\n", fix(wom));
	}

	@Test
	public void testNewItemsAppendedToNativeDefinitionList() throws Exception
	{
		Wom3Document wom = toWom("; Term : Def\n", new TestExpansionCallback());

		Wom3ElementNode dl = findFirst(wom, "dl");
		dl.appendChild(elem(wom, "dt", "T2"));
		dl.appendChild(elem(wom, "dd", "D2"));

		assertEquals("; Term : Def\n; T2\n: D2\n", fix(wom));
	}

	@Test
	public void testNewItemInHtmlListIsRenderedAsHtml() throws Exception
	{
		Wom3Document wom = toWom("<ul>\n<li>a</li>\n</ul>\n", new TestExpansionCallback());

		Wom3ElementNode ul = findFirst(wom, "ul");
		ul.insertBefore(elem(wom, "li", "b"), ul.getLastChild());

		assertEquals("<ul>\n<li>a</li>\n<li>b</li></ul>\n", fix(wom));
	}

	@Test
	public void testNewRowInNativeTableIsRenderedNatively() throws Exception
	{
		Wom3Document wom = toWom("{|\n|-\n| a\n|}\n", new TestExpansionCallback());

		findFirst(wom, "tbody").appendChild(row(wom, elem(wom, "td", "b")));

		assertEquals("{|\n|-\n| a\n|-\n| b\n|}\n", fix(wom));
	}

	@Test
	public void testNewRowAfterImplicitRowInNativeTableIsRenderedNatively() throws Exception
	{
		Wom3Document wom = toWom("{|\n| a\n|}\n", new TestExpansionCallback());

		Wom3ElementNode tr = row(wom, elem(wom, "th", "h"), elem(wom, "td", "b"));
		tr.setAttribute("class", "x");
		findFirst(wom, "tbody").appendChild(tr);

		assertEquals("{|\n| a\n|- class=\"x\"\n! h\n| b\n|}\n", fix(wom));
	}

	@Test
	public void testNewCellsInNativeTableAreRenderedNatively() throws Exception
	{
		Wom3Document wom = toWom("{|\n| a\n|}\n", new TestExpansionCallback());

		Wom3ElementNode tr = findFirst(wom, "tr");
		tr.appendChild(elem(wom, "td", "b"));
		Wom3ElementNode th = elem(wom, "th", "h");
		th.setAttribute("style", "color:red");
		tr.appendChild(th);

		assertEquals("{|\n| a\n| b\n! style=\"color:red\" | h\n|}\n", fix(wom));
	}

	@Test
	public void testNewRowInHtmlTableIsRenderedAsHtml() throws Exception
	{
		Wom3Document wom = toWom("<table>\n<tr><td>a</td></tr>\n</table>\n", new TestExpansionCallback());

		findFirst(wom, "tbody").appendChild(row(wom, elem(wom, "td", "b")));

		assertEquals("<table>\n<tr><td>a</td></tr>\n<tr><td>b</td></tr></table>\n", fix(wom));
	}

	@Test
	public void testNewTransclusionIsRendered() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode arg1 = mww(wom, "arg", null);
		arg1.appendChild(mww(wom, "name", "a"));
		arg1.appendChild(mww(wom, "value", "b"));
		Wom3ElementNode arg2 = mww(wom, "arg", null);
		arg2.appendChild(mww(wom, "value", "c"));
		Wom3ElementNode transclusion = mww(wom, "transclusion", null);
		transclusion.appendChild(mww(wom, "name", "T"));
		transclusion.appendChild(arg1);
		transclusion.appendChild(arg2);

		Wom3ElementNode p = findFirst(wom, "p");
		p.insertBefore(transclusion, findFirst(p, "text"));

		assertEquals("{{T|a=b|c}}Some text\n", fix(wom));
	}

	@Test
	public void testNewSectionIsRenderedWithNativeHeading() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode body = elem(wom, "body", null);
		body.appendChild(elem(wom, "p", "text"));
		Wom3Section section = (Wom3Section) elem(wom, "section", null);
		section.setLevel(3);
		section.appendChild(elem(wom, "heading", "H"));
		section.appendChild(body);
		findFirst(wom, "body").appendChild(section);

		assertEquals("Some text\n=== H ===\ntext", fix(wom));
	}

	@Test
	public void testNewHeadingInParsedSectionIsRendered() throws Exception
	{
		Wom3Document wom = toWom("== A ==\ntext\n", new TestExpansionCallback());

		Wom3ElementNode section = findFirst(wom, "section");
		section.replaceChild(elem(wom, "heading", "B"), findFirst(section, "heading"));

		assertEquals("== B ==\ntext\n", fix(wom));
	}

	@Test
	public void testNewExternalLinkIsRendered() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ExtLink link = (Wom3ExtLink) elem(wom, "extlink", null);
		link.setTarget(new URL("http://example.org"));
		link.setLinkTitle((Wom3Title) elem(wom, "title", "Title"));
		Wom3ElementNode p = findFirst(wom, "p");
		p.insertBefore(link, findFirst(p, "text"));

		assertEquals("[http://example.org Title]Some text\n", fix(wom));
	}

	@Test
	public void testNewPlainUrlIsRendered() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ExtLink link = (Wom3ExtLink) elem(wom, "extlink", null);
		link.setTarget(new URL("http://example.org"));
		link.setPlainUrl(true);
		Wom3ElementNode p = findFirst(wom, "p");
		findFirst(p, "text").setTextContent("See ");
		p.appendChild(link);

		assertEquals("See http://example.org\n", fix(wom));
	}

	@Test
	public void testNewImageIsRendered() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3Image image = (Wom3Image) elem(wom, "image", null);
		image.setSource("File:X.png");
		image.setFormat(Wom3ImageFormat.THUMBNAIL);
		image.setHAlign(Wom3ImageHAlign.LEFT);
		image.setWidth(100);
		image.setAlt("Alt");
		image.setCaption((Wom3ImageCaption) elem(wom, "imgcaption", "Caption"));
		findFirst(wom, "body").appendChild(image);

		assertEquals("Some text\n[[File:X.png|thumb|left|100px|alt=Alt|Caption]]", fix(wom));
	}

	@Test
	public void testNewCommentIsRendered() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode p = findFirst(wom, "p");
		p.insertBefore(elem(wom, "comment", " c "), findFirst(p, "text"));

		assertEquals("<!-- c -->Some text\n", fix(wom));
	}

	@Test
	public void testNewPreIsRenderedAsPreTag() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		findFirst(wom, "body").appendChild(elem(wom, "pre", "x"));

		assertEquals("Some text\n<pre>x</pre>", fix(wom));
	}

	@Test
	public void testTextWithLeadingSpaceAfterNewlineIsFixed() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode p = findFirst(wom, "p");
		findFirst(p, "text").setTextContent("Some\n  text");

		assertEquals("Some\ntext\n", fix(wom));
	}

	@Test
	public void testSpaceInPrecedingTextNodeIsRemoved() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode p = findFirst(wom, "p");
		findFirst(p, "text").setTextContent("Some\n ");
		p.appendChild(elem(wom, "text", "text"));

		assertEquals("Some\ntext\n", fix(wom));
	}

	// =========================================================================
	// FixWomRtdBase

	@Test(expected = IllegalStateException.class)
	public void testRemovingNewlinesWithoutPrecedingTextThrows() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		new FixWomRtdBase().removePrecedingNewlines(findFirst(wom, "text"), 1);
	}

	@Test
	public void testRemovingNewlinesFromMultiplePrecedingTextNodes() throws Exception
	{
		Wom3Document wom = toWom("Some text\n", new TestExpansionCallback());

		Wom3ElementNode p = findFirst(wom, "p");
		findFirst(p, "text").setTextContent("Some");
		p.appendChild(elem(wom, "text", "\n"));
		p.appendChild(elem(wom, "text", "\n"));
		Wom3ElementNode b = elem(wom, "b", "text");
		p.appendChild(b);

		FixWomRtdBase fixer = new FixWomRtdBase();
		fixer.appendWm("Some\n\n");
		fixer.removePrecedingNewlines(b, 2);

		assertEquals("Sometext", Wom3Toolbox.womToWmFast(p));
	}

	// =========================================================================

	private void assertRoundTrip(String wm) throws Exception
	{
		assertRoundTrip(wm, new TestExpansionCallback());
	}

	private void assertRoundTrip(String wm, ExpansionCallback callback) throws Exception
	{
		assertEquals(wm, fix(toWom(wm, callback)));
	}

	private Wom3Document toWom(String wm, ExpansionCallback callback) throws Exception
	{
		return wmToWom(
				new File("FixWomRtdTest.wikitext"),
				wm,
				makePageId("FixWomRtdTest"),
				callback).womDoc;
	}

	private String fix(Wom3Document wom)
	{
		FixWomRtd.process(getWikiConfig(), wom);
		return Wom3Toolbox.womToWmFast(wom);
	}

	private static Wom3ElementNode elem(
			Wom3Document wom,
			String name,
			String text)
	{
		Wom3ElementNode e = (Wom3ElementNode) wom.createElementNS(Wom3Node.WOM_NS_URI, name);
		if (name.equals("text"))
		{
			e.setTextContent(text);
		}
		else if (text != null)
		{
			Wom3ElementNode t = (Wom3ElementNode) wom.createElementNS(Wom3Node.WOM_NS_URI, "text");
			t.setTextContent(text);
			e.appendChild(t);
		}
		return e;
	}

	private static Wom3ElementNode list(
			Wom3Document wom,
			String listName,
			String itemName,
			String... items)
	{
		Wom3ElementNode list = elem(wom, listName, null);
		for (String item : items)
			list.appendChild(elem(wom, itemName, item));
		return list;
	}

	private static Wom3ElementNode row(Wom3Document wom, Wom3ElementNode... cells)
	{
		Wom3ElementNode tr = elem(wom, "tr", null);
		for (Wom3ElementNode cell : cells)
			tr.appendChild(cell);
		return tr;
	}

	private static Wom3ElementNode mww(Wom3Document wom, String name, String text)
	{
		Wom3ElementNode e = (Wom3ElementNode) wom.createElementNS(
				SwcNode.MWW_NS_URI,
				SwcNode.DEFAULT_MWW_NS_PREFIX + ":" + name);
		if (text != null)
			e.appendChild(elem(wom, "text", text));
		return e;
	}

	private static Wom3ElementNode findFirst(Wom3Node n, String localName)
	{
		Wom3ElementNode found = findFirstRec(n, localName);
		assertNotNull("No <" + localName + "> element found", found);
		return found;
	}

	private static Wom3ElementNode findFirstRec(Wom3Node n, String localName)
	{
		for (Wom3Node c = n.getFirstChild(); c != null; c = c.getNextSibling())
		{
			if (c instanceof Wom3ElementNode)
			{
				if (localName.equals(c.getLocalName())
						&& Wom3Node.WOM_NS_URI.equals(c.getNamespaceURI()))
					return (Wom3ElementNode) c;

				Wom3ElementNode found = findFirstRec(c, localName);
				if (found != null)
					return found;
			}
		}
		return null;
	}
}
