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

import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.parser.parser.LinkTargetException;
import org.sweble.wom3.Wom3Article;
import org.sweble.wom3.Wom3Articles;
import org.sweble.wom3.Wom3Attribute;
import org.sweble.wom3.Wom3Big;
import org.sweble.wom3.Wom3Blockquote;
import org.sweble.wom3.Wom3Body;
import org.sweble.wom3.Wom3Bold;
import org.sweble.wom3.Wom3Break;
import org.sweble.wom3.Wom3Center;
import org.sweble.wom3.Wom3Cite;
import org.sweble.wom3.Wom3Code;
import org.sweble.wom3.Wom3Comment;
import org.sweble.wom3.Wom3DefinitionList;
import org.sweble.wom3.Wom3DefinitionListDef;
import org.sweble.wom3.Wom3DefinitionListTerm;
import org.sweble.wom3.Wom3Div;
import org.sweble.wom3.Wom3Document;
import org.sweble.wom3.Wom3Element;
import org.sweble.wom3.Wom3ElementNode;
import org.sweble.wom3.Wom3Emphasize;
import org.sweble.wom3.Wom3ExtLink;
import org.sweble.wom3.Wom3Font;
import org.sweble.wom3.Wom3For;
import org.sweble.wom3.Wom3Heading;
import org.sweble.wom3.Wom3HorizontalRule;
import org.sweble.wom3.Wom3Image;
import org.sweble.wom3.Wom3ImageCaption;
import org.sweble.wom3.Wom3IntLink;
import org.sweble.wom3.Wom3Italics;
import org.sweble.wom3.Wom3List;
import org.sweble.wom3.Wom3ListItem;
import org.sweble.wom3.Wom3Node;
import org.sweble.wom3.Wom3Nowiki;
import org.sweble.wom3.Wom3OrderedList;
import org.sweble.wom3.Wom3Paragraph;
import org.sweble.wom3.Wom3Pre;
import org.sweble.wom3.Wom3Redirect;
import org.sweble.wom3.Wom3Ref;
import org.sweble.wom3.Wom3Repl;
import org.sweble.wom3.Wom3Rtd;
import org.sweble.wom3.Wom3Section;
import org.sweble.wom3.Wom3Signature;
import org.sweble.wom3.Wom3SignatureFormat;
import org.sweble.wom3.Wom3Small;
import org.sweble.wom3.Wom3Span;
import org.sweble.wom3.Wom3Strike;
import org.sweble.wom3.Wom3Strong;
import org.sweble.wom3.Wom3Sub;
import org.sweble.wom3.Wom3Subst;
import org.sweble.wom3.Wom3Sup;
import org.sweble.wom3.Wom3Table;
import org.sweble.wom3.Wom3TableBody;
import org.sweble.wom3.Wom3TableCaption;
import org.sweble.wom3.Wom3TableCell;
import org.sweble.wom3.Wom3TableHeaderCell;
import org.sweble.wom3.Wom3TableRow;
import org.sweble.wom3.Wom3Teletype;
import org.sweble.wom3.Wom3Text;
import org.sweble.wom3.Wom3Title;
import org.sweble.wom3.Wom3Underline;
import org.sweble.wom3.Wom3UnorderedList;
import org.sweble.wom3.swcadapter.nodes.SwcAttr;
import org.sweble.wom3.swcadapter.nodes.SwcBody;
import org.sweble.wom3.swcadapter.nodes.SwcNode;
import org.sweble.wom3.swcadapter.nodes.SwcTagExtBody;
import org.sweble.wom3.swcadapter.nodes.SwcTagExtension;
import org.sweble.wom3.swcadapter.nodes.SwcXmlElement;

import de.fau.cs.osr.utils.WrappedException;

public class FixWomRtd
		extends
			FixWomRtdBase
{
	//	private static final String LIST_PREFIXES = "*#:;";

	public enum ListTypeEnum
	{
		// Render as HTML list items
		HTML_LIST,
		// Try to render as native if possible
		PRERENDER,
	}

	private final WikiConfig wikiConfig;

	private int inInlineBlock = 0;

	//	private String curListPrefix = "";

	private boolean inSemiPre;

	private boolean inGeneratedTable;

	//	private ListTypeEnum inListType;

	// =========================================================================

	public FixWomRtd(WikiConfig wikiConfig)
	{
		this.wikiConfig = wikiConfig;
	}

	// =========================================================================

	public static Wom3Node process(WikiConfig wikiConfig, Wom3Node wom)
	{
		new FixWomRtd(wikiConfig).go(wom);
		return wom;
	}

	// =========================================================================
	// Containers and other nodes without RTD information

	public void visit(Wom3Document document)
	{
		dispatch((Wom3Node) document.getDocumentElement());
	}

	public void visit(Wom3Articles articles)
	{
		iterate(articles);
	}

	public void visit(Wom3Article article)
	{
		iterate(article);
	}

	public void visit(Wom3Body body)
	{
		iterate(body);
	}

	public void visit(Wom3Title title)
	{
		iterate(title);
	}

	// =========================================================================
	// Other nodes without RTD information

	public void visit(Wom3Redirect redirect)
	{
		// Invisible, don't descend
	}

	// =========================================================================
	// Nodes which will be restored to HTML elements

	public void visit(Wom3Div div)
	{
		restoreHtmlRtd(div);
	}

	public void visit(Wom3Blockquote bq)
	{
		restoreHtmlRtd(bq);
	}

	public void visit(Wom3Center center)
	{
		restoreHtmlRtd(center);
	}

	public void visit(Wom3Span span)
	{
		restoreHtmlRtd(span);
	}

	public void visit(Wom3Break br)
	{
		restoreHtmlRtd(br);
	}

	public void visit(Wom3Sub sub)
	{
		restoreHtmlRtd(sub);
	}

	public void visit(Wom3Sup sup)
	{
		restoreHtmlRtd(sup);
	}

	public void visit(Wom3Cite cite)
	{
		restoreHtmlRtd(cite);
	}

	public void visit(Wom3Strong strong)
	{
		restoreHtmlRtd(strong);
	}

	public void visit(Wom3Emphasize em)
	{
		restoreHtmlRtd(em);
	}

	public void visit(Wom3Small small)
	{
		restoreHtmlRtd(small);
	}

	public void visit(Wom3Big big)
	{
		restoreHtmlRtd(big);
	}

	public void visit(Wom3Font font)
	{
		restoreHtmlRtd(font);
	}

	public void visit(Wom3Code code)
	{
		restoreHtmlRtd(code);
	}

	public void visit(Wom3Underline u)
	{
		restoreHtmlRtd(u);
	}

	public void visit(Wom3Strike strike)
	{
		restoreHtmlRtd(strike);
	}

	public void visit(Wom3Teletype tt)
	{
		restoreHtmlRtd(tt);
	}

	private void restoreHtmlRtd(Wom3ElementNode e)
	{
		// Elements without RTD were added to the WOM after conversion
		if (!startsWithRtd(e))
			generateHtmlTagRtd(e);

		fixNewlinesBeforeElement(e, false);
		iterate(e);
	}

	// =========================================================================
	// HTML/Native nodes

	public void visit(Wom3HorizontalRule hr)
	{
		if (!startsWithRtd(hr))
		{
			if (hasHtmlAttributes(hr))
				generateHtmlTagRtd(hr);
			else
				prependRtd(hr, "----");
		}

		// Only a native horizontal rule has to start at the beginning of a line
		fixNewlinesBeforeElement(hr, !hasHtmlTagRtd(hr));
		iterate(hr);
	}

	public void visit(Wom3Bold b)
	{
		if (!startsWithRtd(b))
			generateTicksRtd(b, "'''");

		fixNewlinesBeforeElement(b, false);
		iterate(b);
	}

	public void visit(Wom3Italics i)
	{
		if (!startsWithRtd(i))
			generateTicksRtd(i, "''");

		fixNewlinesBeforeElement(i, false);
		iterate(i);
	}

	public void visit(Wom3Signature sig)
	{
		if (!startsWithRtd(sig))
			prependRtd(sig, genSignatureTildes(sig.getSignatureFormat()));

		fixNewlinesBeforeElement(sig, false);
		iterate(sig);
	}

	private String genSignatureTildes(Wom3SignatureFormat format)
	{
		switch (format)
		{
			case USER:
				return "~~~";
			case USER_TIMESTAMP:
				return "~~~~";
			case TIMESTAMP:
				return "~~~~~";
		}

		// Don't push into default: case.
		// This way we'll get a warning if we missed a constant.
		throw new IllegalArgumentException("Unknown signature format: " + format);
	}

	/**
	 * Other WOM elements which have no dedicated visit() method (e.g.
	 * &lt;abbr>, &lt;del>, &lt;ins>, ...) are restored to HTML elements. MWW
	 * elements which have a dedicated implementation (e.g. tag extensions,
	 * transclusions and generic XML elements) are handled like generic
	 * elements.
	 */
	public void visit(Wom3ElementNode e)
	{
		if (e instanceof SwcNode)
		{
			processMwwElement(e);
		}
		else
		{
			restoreHtmlRtd(e);
		}
	}

	// =========================================================================
	// Generate RTD for elements that were added after conversion

	private void generateTicksRtd(Wom3ElementNode e, String ticks)
	{
		if (hasHtmlAttributes(e))
		{
			// Ticks cannot carry attributes
			generateHtmlTagRtd(e);
		}
		else
		{
			prependRtd(e, ticks);
			appendRtd(e, ticks);
		}
	}

	private void generateHtmlTagRtd(Wom3ElementNode e)
	{
		String tag = e.getLocalName();

		StringBuilder b = new StringBuilder();
		b.append('<');
		b.append(tag);
		for (Wom3Attribute attr : e.getWomAttributes())
		{
			if (!isHtmlAttribute(attr))
				continue;
			b.append(' ');
			b.append(attr.getName());
			b.append("=\"");
			b.append(escapeAttributeValue(attr.getValue()));
			b.append('"');
		}

		if (e.hasChildNodes())
		{
			b.append('>');
			prependRtd(e, b.toString());
			appendRtd(e, "</" + tag + ">");
		}
		else
		{
			b.append(" />");
			prependRtd(e, b.toString());
		}
	}

	private boolean hasHtmlAttributes(Wom3ElementNode e)
	{
		for (Wom3Attribute attr : e.getWomAttributes())
		{
			if (isHtmlAttribute(attr))
				return true;
		}
		return false;
	}

	private boolean isHtmlAttribute(Wom3Attribute attr)
	{
		// Skip namespace declarations and attributes from other namespaces
		String name = attr.getName();
		return !name.equals("xmlns") && (name.indexOf(':') == -1);
	}

	private String escapeAttributeValue(String value)
	{
		return value
				.replace("&", "&amp;")
				.replace("\"", "&quot;")
				.replace("<", "&lt;");
	}

	/**
	 * Generates the RTD of a tag extension or a generic XML element that was
	 * added after conversion.
	 */
	private void generateSwcTagRtd(
			Wom3ElementNode e,
			String tag,
			Wom3Node body)
	{
		prependRtd(e, "<" + tag);

		for (Wom3Node c = e.getFirstChild(); c != null; c = c.getNextSibling())
		{
			if (c instanceof SwcAttr)
				generateSwcAttrRtd((SwcAttr) c);
		}

		if (body != null)
		{
			insertRtdBefore(body, ">");
			appendRtd(e, "</" + tag + ">");
		}
		else
		{
			appendRtd(e, " />");
		}
	}

	private void generateSwcAttrRtd(SwcAttr attr)
	{
		if (startsWithRtd(attr))
			return;

		prependRtd(attr, " ");
		if (attr.hasValue())
		{
			String value = womToWmFast(attr.getValue());
			String quote = (value.indexOf('"') == -1) ? "\"" : "'";
			insertRtdBefore(attr.getValue(), "=" + quote);
			appendRtd(attr, quote);
		}
	}

	// =========================================================================
	// Normalize text nodes

	public void visit(Wom3Text text)
	{
		String content = text.getTextContent();
		// stripDangerousWhitespace() also updates the wiki markup
		if (!(text.getParentNode() instanceof Wom3Nowiki)
				&& !(text.getParentNode() instanceof Wom3Pre))
		{
			String stripped = stripDangerousWhitespace(text, content);
			if (stripped != content)
			{
				if (stripped.isEmpty())
				{
					Wom3Node next = text.getNextSibling();
					text.getParentNode().removeChild(text);
					continueAfterDelete(next);
				}
				else
					text.setTextContent(stripped);
			}
		}
	}

	public void visit(Wom3Rtd rtd)
	{
		String content = rtd.getTextContent();
		/*
		String stripped = stripDangerousWhitespace(rtd, content);
		if (stripped != content)
			rtd.setTextContent(stripped);
		appendWm(stripped);
		*/
		appendWm(content);
	}

	/**
	 * Reduces more than two newlines to one newline if inside a paragraph to
	 * prevent the paragraph from being split into two paragraphs.
	 * 
	 * Makes sure that there are no spaces at the beginning of a line if not
	 * inside a "semi" pre environment.
	 */
	private String stripDangerousWhitespace(Wom3Node node, String text)
	{
		int newlines = getNewlineCount();

		boolean atStartOfP = false;
		if (inInlineBlock > 0)
		{
			atStartOfP = isAtStartOfParagraph(node);
			if (newlines > 1 && !atStartOfP)
			{
				removePrecedingNewlines(node, newlines - 1);
				newlines = 1;
			}
			if (newlines > 0 && hadSpaceAfterLastNewline())
				removePrecedingSpace(node);
		}

		boolean atPageStart = isAtPageStart();

		int l = text.length();
		int firstNewline = -1;
		int lastNewline = -1;
		boolean hadSpaceSinceNl = false;

		for (int i = 0; i < l; ++i)
		{
			char ch = text.charAt(i);
			switch (ch)
			{
				case '\n':
					++newlines;
					hadSpaceSinceNl = false;
					if (firstNewline == -1)
						firstNewline = i;
					lastNewline = i;
					break;

				case ' ':
				case '\t':
					hadSpaceSinceNl = true;
					break;

				default:
				{
					if (inInlineBlock > 0 && newlines > 1 && !atStartOfP)
					{
						// Too many newlines would split the paragraph. Only
						// keep the first newline (and remove any whitespace
						// that follows it).
						int from = firstNewline + 1;
						text = text.substring(0, from) + text.substring(i);
						i = from;
						l = text.length();
						newlines = 1;
					}
					else if (!inSemiPre && newlines > 0 && hadSpaceSinceNl)
					{
						// Whitespace at the start of a line would start a
						// pre-formatted block. Only remove the whitespace.
						int from = lastNewline + 1;
						text = text.substring(0, from) + text.substring(i);
						i = from;
						l = text.length();
					}

					if ((newlines > 0 || atPageStart) && isListPrefixChar(ch))
					{
						// The character would start a list at the beginning
						// of a line. Everything in front of the dangerous
						// character stays in the original node, the dangerous
						// character is wrapped in a <nowiki> node and the rest
						// goes into a new text node. The new nodes follow the
						// original node and will be processed next.
						String checked = text.substring(0, i);
						Wom3Node nowiki = insertNowikiAfter(node, String.valueOf(ch));

						String rest = text.substring(i + 1);
						if (!rest.isEmpty())
							insertTextAfterNoMerge(nowiki, rest);

						appendWm(checked);
						return checked;
					}

					atStartOfP = false;
					atPageStart = false;
					newlines = 0;
					firstNewline = -1;
					lastNewline = -1;
					hadSpaceSinceNl = false;
					break;
				}
			}
		}

		appendWm(text);
		return text;
	}

	private static boolean isListPrefixChar(char ch)
	{
		switch (ch)
		{
			case '*':
			case '#':
			case ':':
			case ';':
				return true;
			default:
				return false;
		}
	}

	// =========================================================================

	public void visit(Wom3Nowiki nowiki)
	{
		if (!startsWithRtd(nowiki))
		{
			prependRtd(nowiki, "<" + nowiki.getTagName() + ">");
			appendRtd(nowiki, "</" + nowiki.getTagName() + ">");
		}

		fixNewlinesBeforeElement(nowiki, false);

		for (Wom3Node child : nowiki)
		{
			if (child instanceof Wom3Text)
			{
				// The content of a nowiki node is not subject to further 
				// processing.
				appendWm(child.getTextContent());
			}
			else
			{
				dispatch(child);
			}
		}
	}

	public void visit(Wom3Comment comment)
	{
		// TODO: Implement!
		if (!startsWithRtd(comment))
			throw new UnsupportedOperationException();

		// Invisible to parser, don't descend!
		// The comment's prefix and suffix are also invisible to the parser!

		// TODO: If the comment is preceded and followed by a newline (which
		// are not already part of its prefix/suffix we have to add newlines
		// to the prefix and suffix! Otherwise the newlines in front and after
		// the comment will be parsed as prefix and suffix by the parser and
		// therefore disappear!
	}

	// =========================================================================
	// Table

	public void visit(Wom3Table table)
	{
		// TODO: Implement!
		/* Extremely tricky: As with lists we have to find out if this is a
		 * native or an HTML table. If we're dealing with an HTML table,
		 * whitespace in between elements (tr, td, ...) will cause big
		 * headaches.
		 */

		// Native and HTML tables always start with RTD. A table without RTD
		// was added after conversion and is rendered as HTML table.
		boolean generate = !startsWithRtd(table);
		if (generate)
			generateHtmlTagRtd(table);

		fixNewlinesBeforeElement(table, true /*TODO: Compute correctly*/);

		boolean oldInGeneratedTable = inGeneratedTable;
		inGeneratedTable = generate;
		iterate(table);
		inGeneratedTable = oldInGeneratedTable;
	}

	public void visit(Wom3TableCaption caption)
	{
		restoreTablePartRtd(caption);

		// TODO: We're not trimming any whitespace yet since we have no clue how
		// tables and whitespace behave
		iterate(caption);
	}

	public void visit(Wom3TableBody body)
	{
		restoreTablePartRtd(body);

		// TODO: We're not trimming any whitespace yet since we have no clue how
		// tables and whitespace behave
		iterate(body);
	}

	public void visit(Wom3TableRow row)
	{
		restoreTablePartRtd(row);

		// TODO: We're not trimming any whitespace yet since we have no clue how
		// tables and whitespace behave
		iterate(row);
	}

	public void visit(Wom3TableHeaderCell header)
	{
		restoreTablePartRtd(header);

		// TODO: We're not trimming any whitespace yet since we have no clue how
		// tables and whitespace behave
		iterate(header);
	}

	public void visit(Wom3TableCell cell)
	{
		restoreTablePartRtd(cell);

		// TODO: We're not trimming any whitespace yet since we have no clue how
		// tables and whitespace behave
		iterate(cell);
	}

	private void restoreTablePartRtd(Wom3ElementNode e)
	{
		// TODO: Parts of converted tables can lack RTD (e.g. implicit table
		// bodies and rows). Therefore, we only generate RTD for parts of
		// tables which were added after conversion.
		if (inGeneratedTable && !startsWithRtd(e))
			generateHtmlTagRtd(e);
	}

	// =========================================================================
	// Section & Heading

	public void visit(Wom3Section section)
	{
		// Has no RTD information
		fixNewlinesBeforeElement(section, true);
		iterate(section);
	}

	public void visit(Wom3Heading heading)
	{
		// TODO: Implement!
		if (!startsWithRtd(heading))
			throw new UnsupportedOperationException();

		/* TODO: Tricky: Trimming might be necessary in case the heading is an 
		 * HTML element!
		 */
		iterate(heading);
	}

	// =========================================================================
	// Paragraph

	public void visit(Wom3Paragraph p)
	{
		// Always check RTD around a paragraph. Changing other nodes can always
		// lead to slight changes in the number of surrounding newlines

		// A paragraph needs two newlines in front of it if it follows another 
		// element. It needs none and tolerates one newline in front of it at
		// the start of the page.

		// TODO: Consider paragraph with bottom gap in front!

		// If this paragraph is using HTML syntax and no top or bottom gap is 
		// set the rules for gaps between block elements in general apply.
		if (hasHtmlTagRtd(p))
		{
			if (p.getTopGap() == 0 && p.getBottomGap() == 0)
			{
				// The paragraph itself is not part of the inline block it
				// starts. Fix the newlines in front of it first.
				fixNewlinesBeforeElement(p, false);
				++inInlineBlock;
				iterate(p);
				// TODO: Sure we don't have to fix the bottom gap?
				--inInlineBlock;
				return;
			}
			else
			{
				// Otherwise remove the HTML tag RTD
				p.removeChild(p.getFirstChild());
				p.removeChild(p.getLastChild());
			}
		}

		++inInlineBlock;

		int haveNewlines = getNewlineCount();
		if (isAtPageStart())
		{
			if (haveNewlines > 1)
				removePrecedingNewlines(p, haveNewlines - 1);
		}
		else
		{
			if (haveNewlines > 2)
				removePrecedingNewlines(p, haveNewlines - 2);
			else if (haveNewlines < 2)
			{
				// Not true:
				// Well, we usually only need one newline between block elements
				// but if the preceding block element is a non-HTML paragraph we
				// need two newlines
				/*
				...
				*/

				// New truth:
				// We always need two newlines in front of a non-HTML paragraph.
				// Only if it's the first paragraph in some container (section, 
				// table cell, ...) we need only one.
				/*
				int needNewlines = 2;
				if (isFirstInContainer(p))
					needNewlines = 1;
				*/

				// Even better truth:
				int needNewlines = 2;
				Wom3Node pnws = getPrecedingNonWsNode(p);
				if (!(pnws instanceof Wom3Paragraph))
				{
					if (pnws == null && isTableCellOrCaption(p.getParentNode()))
						// The first paragraph in a table cell or caption
						// directly follows the markup of the cell or caption
						needNewlines = 0;
					else if ((pnws != null && isNonHtmlBlockElement(pnws)) || isFirstInContainer(p))
						needNewlines = 1;
				}

				if (haveNewlines < needNewlines)
					addPrecedingTextNewlines(p, needNewlines - haveNewlines);
			}
		}

		// We only fix the top gap. The bottom gap has to be fixed by elements
		// that come after the paragraph (see trimNewlinesBeforeElement())
		Wom3Node text0 = getFirstTextNode(p);
		if (text0 != null)
			assureStartWithEnoughNewlines(text0, p.getTopGap());
		else if (p.getTopGap() > 0)
			insertTextBefore(p.getFirstChild(), genNewlines(p.getTopGap()));

		iterate(p);

		// TODO: Sure we don't have to fix the bottom gap?

		--inInlineBlock;
	}

	private boolean isTableCellOrCaption(Wom3Node n)
	{
		return (n instanceof Wom3TableCell)
				|| (n instanceof Wom3TableHeaderCell)
				|| (n instanceof Wom3TableCaption);
	}

	// =========================================================================
	// Definition list

	public void visit(Wom3DefinitionList list)
	{
		// TODO: Implement
		/* Really tricky: if there's no RTD information we don't know if this is
		 * a HTML list or a native list. Maybe we should first render the list 
		 * items and find out if a) they have HTML or native RTD information or
		 * b) are not suitable for a native list. What's more we have to trim 
		 * whitespace in between list items if the list is done using HTML tags.
		 */
		//fixNewlinesBeforeElement(list, true /*TODO: Compute correctly*/);
		iterate(list);
	}

	public void visit(Wom3DefinitionListTerm term)
	{
		++inInlineBlock;
		// TODO: Implement
		// TODO: Tricky: Might require trimming when HTML element!
		iterate(term);
		--inInlineBlock;
	}

	public void visit(Wom3DefinitionListDef def)
	{
		++inInlineBlock;
		// TODO: Implement
		// TODO: Tricky: Might require trimming when HTML element!
		iterate(def);
		--inInlineBlock;
	}

	// =========================================================================
	// Ordered/Unordered list

	public void visit(Wom3OrderedList list)
	{
		processList(list, "#");
	}

	public void visit(Wom3UnorderedList list)
	{
		processList(list, "*");
	}

	private void processList(Wom3List list, String bulletType)
	{
		//fixNewlinesBeforeElement(list, true);
		iterate(list);

		//	ListTypeEnum oldInListType = inListType;
		//	String oldListLevel = curListPrefix;
		//	
		//	// If a list starts with HTML RTD we assume the RTD is properly 
		//	// formatted and we treat the list like any other block element.
		//	if (hasHtmlTagRtd(list))
		//	{
		//		inListType = ListTypeEnum.HTML_LIST;
		//		curListPrefix = "";
		//		fixNewlinesBeforeElement(list, false);
		//		iterate(list);
		//	}
		//	else
		//	{
		//		// If a list is not an HTML list it does not have RTD (the list! the 
		//		// children are a different story). We also won't added RTD unless
		//		// we are forced to render a HTML list and if we do render an HTML
		//		// list it won't have newlines. Therefore, trimNewlines... should
		//		// work here.
		//		
		//		fixNewlinesBeforeElement(list, true);
		//		
		//		// Remember markup position before the children were rendered.
		//		int wmPosBeforeChildren = getWmPos();
		//		boolean needHtmlList = false;
		//		
		//		// We fix the list items first and see if they can be formatted as 
		//		// native wiki markup list or if it has to be an HTML list.
		//		inListType = ListTypeEnum.PRERENDER;
		//		curListPrefix += bulletType;
		//		for (Wom3Node child : list)
		//		{
		//			if (child instanceof Wom3ListItem)
		//			{
		//				int wmPosBeforeListItem = getWmPos();
		//				isHtmlListItem = true;
		//				dispatch(child);
		//				if (hasHtmlTagRtd(child) || countNewlinesSince(wmPosBeforeListItem) > 1)
		//				{
		//					needHtmlList = true;
		//					break;
		//				}
		//			}
		//			else
		//			{
		//				dispatch(child);
		//			}
		//		}
		//		
		//		if (needHtmlList)
		//		{
		//			// Reformat whole list
		//			inListType = ListTypeEnum.HTML_LIST;
		//			curListPrefix = "";
		//			discardWm(wmPosBeforeChildren);
		//			
		//			prependText(list, "\n");
		//			prependRtd(list, "<" + list.getTagName() + ">");
		//			appendRtd(list, "</" + list.getTagName() + ">");
		//			
		//			iterate(list);
		//		}
		//		else
		//			// The list was completey rendered as native list in the PRERENDER
		//			// trial run. No need to do it again.
		//			;
		//	}
		//	inListType = oldInListType;
		//	curListPrefix = oldListLevel;
	}

	//	// TODO: Use me ...
	//	private boolean isHtmlListItem = false;

	public void visit(Wom3ListItem li)
	{
		++inInlineBlock;

		//fixNewlinesBeforeElement(li, true);
		iterate(li);

		//	if (hasHtmlTagRtd(li))
		//	{
		//		fixNewlinesBeforeElement(li, false);
		//		iterate(li);
		//
		//		// After iterating over our children let the parent list know that 
		//		// this is an HTML list item
		//		isHtmlListItem = true;
		//	}
		//	else if (inListType == ListTypeEnum.HTML_LIST)
		//	{
		//		// Remove any old RTD first
		//		Wom3Rtd rtd0 = getFirstRtdNode(li);
		//		if (rtd0 != null)
		//		{
		//			li.removeChild(rtd0);
		//			Wom3Rtd rtd1 = getLastRtdNode(li);
		//			if (rtd1 != null)
		//				// rtd1 may be null for the last list item in a list.
		//				li.removeChild(rtd1);
		//		}
		//
		//		// Add new HTML RTD
		//		prependText(li, "\n");
		//		prependRtd(li, "<" + li.getTagName() + ">");
		//		appendRtd(li, "</" + li.getTagName() + ">");
		//
		//		// Treat like any other element
		//		fixNewlinesBeforeElement(li, false);
		//
		//		iterate(li);
		//
		//		// After iterating over our children let the parent list know that 
		//		// this is an HTML list item (unnecessary, in HTML_LIST mode the
		//		// parent list knows anyway
		//		isHtmlListItem = true;
		//	}
		//	else
		//	{
		//		// If we're not forced to render as HTML we simply render as native
		//		// and don't check if native is an option. The parent list will do
		//		// that for us afterwards and re-render the list if HTML should 
		//		// be necessary.
		//
		//		// Each list item is only allowed to have one newline at the end.
		//		// There can only be a gap of newlines between native list items 
		//		// if would they would violate this rule. If they do violate that
		//		// rule the list will re-render as HTML anyway and HTML list items
		//		// make sure that there is no such gap.
		//
		//		// TODO: I think this is missing: fixNewlinesBeforeElement(li, true);
		//
		//		Wom3Node lastLi = null;
		//
		//		// Fix prefix
		//		Wom3Rtd rtd0 = getFirstRtdNode(li);
		//		if (rtd0 != null)
		//		{
		//			// We have a prefix (or at least RTD), update it if necessary
		//			String prefix = rtd0.getTextContent();
		//			int lastBullet = lastIndexOfOneOf(prefix, LIST_PREFIXES);
		//			if (lastBullet == -1)
		//				// that's ok
		//				;
		//			lastBullet += 1;
		//			String ws = prefix.substring(lastBullet, prefix.length());
		//			String newPrefix = curListPrefix + ws;
		//
		//			if (!newPrefix.equals(prefix))
		//			{
		//				rtd0.setTextContent(newPrefix);
		//
		//				// TODO: Why inside the (rtd0 != null) if?
		//				// TODO: Probably doesn't work for list nested more than once?
		//				// If a surrounding list was dissolved the last RTD of the
		//				// last list item might be unwanted
		//				//					lastLi = findLastChildOfType(li.getParentNode(), Wom3ListItem.class);
		//				//					if (lastLi == li)
		//				//					{
		//				//						Wom3Rtd last = getLastRtdNode(li);
		//				//						if (last != null)
		//				//							li.removeChild(last);
		//				//					}
		//			}
		//		}
		//		else
		//		{
		//			// We don't have RTD at all, generate it
		//			prependRtd(li, curListPrefix + " ");
		//		}
		//
		//		iterate(li);
		//
		//		if (getNewlineCount() < 1)
		//		{
		//			// The last list item does not need to add a newline
		//			if (lastLi == null)
		//				lastLi = findLastChildOfType(li.getParentNode(), Wom3ListItem.class);
		//			if (lastLi != li)
		//				appendRtdAfterProcessing(li, genNewlines(1));
		//		}
		//	}

		--inInlineBlock;
	}

	//	private String gatherAncestorListItemPrefix(Wom3Node li)
	//	{
	//		String prefix = "";
	//		Wom3Node list = li.getParentNode();
	//		Wom3Node listContainer = list.getParentNode();
	//		while (listContainer != null)
	//		{
	//			if (!((listContainer instanceof Wom3ListItem)
	//					|| (listContainer instanceof Wom3DefinitionListTerm)
	//					|| (listContainer instanceof Wom3DefinitionListDef)))
	//				break;
	//			// The list items list is again child of a list item (c)
	//
	//			Wom3Node prev = getPrecedingNonWsNode(list);
	//			if (prev != null)
	//				break;
	//			// Our list is the first item in the containing list item (c)
	//
	//			Wom3Rtd rtd0 = getFirstRtdNode(listContainer);
	//			String containerPrefix = rtd0.getTextContent();
	//			if (rtd0 == null || lastIndexOfOneOf(containerPrefix, LIST_PREFIXES) == -1)
	//				break;
	//			// The containing list item (c) has a native list prefix
	//
	//			prefix = containerPrefix.trim() + prefix;
	//			li = listContainer;
	//		}
	//		return prefix;
	//	}

	// =========================================================================
	// A pre element

	public void visit(Wom3Pre pre)
	{
		// TODO: Implement!
		/* Tricky: We have to check if this is a <pre> tag extension (which then 
		 * would contain a <nowiki> node as well) or a whitespace prefixed pre
		 * paragraph.
		 */
		if (!startsWithRtd(pre) && !startsWithText(pre))
			throw new UnsupportedOperationException();

		fixNewlinesBeforeElement(pre, false /*TODO: Compute correctly!*/);

		Wom3Nowiki nowiki = findFirstChildOfType(pre, Wom3Nowiki.class);
		if (nowiki == null)
		{
			iterate(pre);
		}
		else
		{
			// The <nowiki> contens are invisible. Also the <nowiki> is 
			// synthetic and should not get RTD attached
			for (Wom3Node c : pre)
			{
				if (c != nowiki)
					dispatch(c);
			}
		}
	}

	// =========================================================================
	// Internal and external links and images

	public void visit(Wom3IntLink link)
	{
		fixNewlinesBeforeElement(link, false);

		if (!startsWithRtd(link))
		{
			prependRtd(link, "[[" + link.getTarget());
			Wom3Title title = link.getLinkTitle();
			if (title != null && !startsWithRtd(title))
				prependRtd(title, "|");
			appendRtd(link, "]]");
		}

		iterate(link);
	}

	public void visit(Wom3ExtLink link)
	{
		fixNewlinesBeforeElement(link, false);

		// TODO: Implement!
		if (!startsWithRtd(link))
			throw new UnsupportedOperationException();

		iterate(link);
	}

	public void visit(Wom3Image image)
	{
		// TODO: Implement!
		if (!startsWithRtd(image))
			throw new UnsupportedOperationException();

		fixNewlinesBeforeElement(image, false);
		iterate(image);
	}

	public void visit(Wom3ImageCaption caption)
	{
		// TODO: Implement!
		if (!startsWithRtd(caption))
			throw new UnsupportedOperationException();

		iterate(caption);
	}

	// =========================================================================
	// WOM incompatible elements that have been substituted

	public void visit(Wom3Subst subst)
	{
		Wom3For for_ = subst.getFor();
		if (for_.getFirstChild() instanceof Wom3Element)
		{
			String name = ((Wom3Element) for_.getFirstChild()).getLocalName();
			if (name.equals("intlink"))
			{
				processSubstIntLink(subst);
			}
			else if (name.equals("xml-entity-ref"))
			{
				processXmlEntityRef(subst);
			}
			else if (name.equals("xml-char-ref"))
			{
				processXmlCharRef(subst);
			}
			else
			{
				// Other substitutions (e.g. redirects): The replacement is
				// invisible, the original markup is kept.
				iterate(for_);
			}
		}
		else
		{
			iterate(for_);
		}
	}

	private void processXmlEntityRef(Wom3Subst subst)
	{
		// TODO Auto-generated method stub
		iterate(subst.getFor());
	}

	private void processXmlCharRef(Wom3Subst subst)
	{
		// TODO Auto-generated method stub
		iterate(subst.getFor());
	}

	private void processSubstIntLink(Wom3Subst subst)
	{
		fixNewlinesBeforeElement(subst, false);

		Wom3Repl repl = subst.getRepl();
		if (!repl.hasChildNodes()
				|| !(findFirstNonWhitespaceNode(repl) instanceof Wom3IntLink))
		{
			unwrapIntlink(subst, repl);
		}
		else
		{
			Wom3IntLink replLink = findFirstChildOfType(repl, Wom3IntLink.class);
			Wom3Title replTitle = replLink.getLinkTitle();
			String replTarget = replLink.getTarget();

			Wom3For for_ = subst.getFor();
			Wom3Element forLink = findFirstChildOfType(for_, Wom3Element.class);
			String forTarget = forLink.getAttribute("target");
			String forPrefix = forLink.getAttribute("prefix");
			String forSuffix = forLink.getAttribute("postfix");
			Wom3Title forTitle = findFirstChildOfType(forLink, Wom3Title.class);

			String newTitle = stringifyTitle(replTitle, replTarget);

			// Build the oldTitle using the new target as replacement in case 
			// there is no title node.
			String oldTitle = stringifyTitle(forPrefix, forTitle, forSuffix, forTarget);

			if (!newTitle.equals(oldTitle))
			{
				// The title has changed => unwrap "repl/intlink", remove 
				// "subst" and fix unwrapped intlink.

				unwrapIntlink(subst, repl);
			}
			else
			{
				boolean update = false;
				if (!replTarget.equals(forTarget))
				{
					// First normalize both targets!
					PageTitle replTargetNl;
					PageTitle forTargetNl;
					try
					{
						replTargetNl = PageTitle.make(wikiConfig, replTarget);
						forTargetNl = PageTitle.make(wikiConfig, forTarget);
					}
					catch (LinkTargetException e)
					{
						throw new WrappedException(e);
					}

					if (!replTargetNl.equals(forTargetNl))
					{
						// If the target changed and there was no title, we have 
						// to create a title node with the old target name. In 
						// this case we can drop the prefix/postfix and can then
						// drop the whole <subst> thing
						if (forTitle == null)
						{
							unwrapIntlink(subst, repl);
							return;
						}

						// The target has changed, fix target in  "for/e" and fix the
						// RTD inforamtion.
						forLink.setAttribute("target", replTarget);
						update = true;
					}
				}

				if (update || getFirstRtdNode(forLink) == null)
					fixIntLink(forLink, forTarget, replTarget);

				// Iterate children to update this.wt
				iterate(forLink);
			}
		}
	}

	private void unwrapIntlink(Wom3Subst subst, Wom3Repl repl)
	{
		// The link was converted or removed.
		// unwrap the "repl" and remove the whole "subst".

		Wom3Node next = subst.getNextSibling();
		moveChildrenInFrontOfXRemoveXAndAndProcess(repl, subst);
		continueAfterDelete(next);
	}

	protected String stringifyTitle(Wom3Title replTitle, String target)
	{
		return (replTitle == null) ? target : stringifyChildren(replTitle);
	}

	protected String stringifyTitle(
			String prefix,
			Wom3Title title,
			String postfix,
			String target)
	{
		String t = (title == null) ? target : stringifyChildren(title);
		if (prefix != null && !prefix.isEmpty())
			t = prefix + t;
		if (postfix != null && !postfix.isEmpty())
			t = t + postfix;
		return t;
	}

	private void fixIntLink(Wom3Element link, String oldTarget, String newTarget)
	{
		String prefix = link.getAttribute("prefix");
		String suffix = link.getAttribute("postfix");

		String rtd0Str = "[[" + newTarget;
		if (prefix != null && !prefix.isEmpty())
			rtd0Str = prefix + rtd0Str;

		String rtd1Str = "]]";
		if (suffix != null && !suffix.isEmpty())
			rtd1Str = rtd1Str + suffix;

		Wom3Rtd rtd0 = getFirstRtdNode(link);
		if (rtd0 != null)
		{
			// There may or may not be a title in between.
			Wom3Rtd rtd1 = getLastRtdNode(link);
			if (rtd0 == rtd1)
				rtd0Str += rtd1Str;
			else
				rtd1.setTextContent(rtd1Str);
			rtd0.setTextContent(rtd0Str);
		}
		else
		{
			prependRtd(link, rtd0Str);
			appendRtd(link, rtd1Str);
		}

		Wom3Title title = findFirstChildOfType(link, Wom3Title.class);
		if (title != null && !startsWithRtd(title))
			prependRtd(title, "|");
	}

	// =========================================================================

	public void visit(Wom3Element e)
	{
		processMwwElement(e);
	}

	private void processMwwElement(Wom3ElementNode e)
	{
		String name = e.getLocalName();
		if (name.equals("tagext"))
		{
			processTagExt(e);
		}
		else if (name.equals("transclusion"))
		{
			processTransclusion(e);
		}
		else if (name.equals("param"))
		{
			processParam(e);
		}
		else if (name.equals("xmlelement"))
		{
			processXmlElement(e);
		}
		else if (name.equals("attr") || name.equals("garbage"))
		{
			processAttr(e);
		}
		else
		{
			// Other elements (e.g. the original markup of substituted
			// elements or the body of generic XML elements) are passed through
			iterate(e);
		}
	}

	private void processXmlElement(Wom3ElementNode e)
	{
		if (!startsWithRtd(e) && (e instanceof SwcXmlElement))
		{
			generateSwcTagRtd(
					e,
					((SwcXmlElement) e).getTag(),
					findFirstChildOfType(e, SwcBody.class));
		}

		iterate(e);
	}

	private void processAttr(Wom3ElementNode e)
	{
		if (e instanceof SwcAttr)
			generateSwcAttrRtd((SwcAttr) e);

		// The attribute is passed through unaltered
		appendWm(womToWmFast(e));
	}

	private void processTagExt(Wom3ElementNode e)
	{
		if (!startsWithRtd(e) && (e instanceof SwcTagExtension))
		{
			generateSwcTagRtd(
					e,
					((SwcTagExtension) e).getName(),
					findFirstChildOfType(e, SwcTagExtBody.class));
		}

		// The stuff inside tag extensions is invisible to the parser
		appendWm("<" + e.getAttribute("name") + "/>");
	}

	private void processTransclusion(Wom3ElementNode e)
	{
		// The stuff inside transclusions is invisible to the parser
		appendWm("{{N|...}}");
	}

	private void processParam(Wom3ElementNode e)
	{
		// The stuff inside template parameters is invisible to the parser
		appendWm("{{{N}}}");
	}

	// =========================================================================

	public void visit(Wom3Ref ref)
	{
		iterate(ref);
	}

	// =========================================================================

	/**
	 * (a) There must not be an amount of whitespace between two block elements
	 * that would cause the parser to insert an empty paragraph. (b) There must
	 * at least be one newline between two block elements. (c) If the upper
	 * block element is a paragraph, the method must make sure that its bottom
	 * gap is respected.
	 */
	private void fixNewlinesBeforeElement(Wom3ElementNode e, boolean needNewline)
	{
		int newlines = getNewlineCount();
		int allowed = 2;
		int required = 0;

		// We need a newline (for example in front of a native wm list).
		// Page start is an implicit newline
		if (needNewline && !isAtPageStart())
			required += 1;

		if (isAtStartOfParagraph(e))
		{
			// At the start of a paragraph the paragraph made sure the top gap
			// has the right size. We don't have to remove any spaces ourselves.
			return;
		}
		else if (inInlineBlock > 0 && !needNewline)
		{
			// TODO: This is bullshit ... inInlineBlock is not a direct test.
			// There could be non-inline block in between (<dl><dd><dl><dd>...)

			// TODO: I added !needNewline, don't know if that makes so much more
			// sense but should at least work for the <dl><dd>... case.

			// Inside an inline block (paragraph, table cell, ...) only one 
			// newline is allowed, more would split text into paragraphs.
			allowed -= 1;
		}
		else
		{
			// If the previous node is a paragraph we have to respect its
			// bottom gap. The newlines of the bottom gap are part of the
			// newlines we have already seen.
			Wom3Paragraph p = getPrecedingParagraph(e);
			if (p != null && p.getBottomGap() > 0)
			{
				allowed += p.getBottomGap();
				required = Math.max(required, p.getBottomGap());
			}
		}

		if (newlines > allowed)
		{
			removePrecedingNewlines(e, newlines - allowed);
		}
		else if (newlines < required)
		{
			insertTextBeforeAfterProcessing(e, genNewlines(required - newlines));
		}
	}
}
