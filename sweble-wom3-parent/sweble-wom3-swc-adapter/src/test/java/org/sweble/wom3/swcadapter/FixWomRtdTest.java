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

import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wom3.Wom3Document;
import org.sweble.wom3.Wom3ElementNode;
import org.sweble.wom3.Wom3Node;
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
