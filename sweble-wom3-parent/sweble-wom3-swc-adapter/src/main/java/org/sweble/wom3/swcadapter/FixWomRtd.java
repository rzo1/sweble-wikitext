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
import org.sweble.wom3.Wom3ImageFormat;
import org.sweble.wom3.Wom3ImageHAlign;
import org.sweble.wom3.Wom3ImageVAlign;
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
import org.sweble.wom3.swcadapter.nodes.SwcArg;
import org.sweble.wom3.swcadapter.nodes.SwcAttr;
import org.sweble.wom3.swcadapter.nodes.SwcBody;
import org.sweble.wom3.swcadapter.nodes.SwcNode;
import org.sweble.wom3.swcadapter.nodes.SwcTagExtBody;
import org.sweble.wom3.swcadapter.nodes.SwcTagExtension;
import org.sweble.wom3.swcadapter.nodes.SwcTransclusion;
import org.sweble.wom3.swcadapter.nodes.SwcXmlElement;

import de.fau.cs.osr.utils.StringTools;
import de.fau.cs.osr.utils.WrappedException;

public class FixWomRtd
		extends
			FixWomRtdBase
{
	public enum ListTypeEnum
	{
		// Render as HTML list items
		HTML_LIST,
		// Try to render as native if possible
		PRERENDER,
	}

	/**
	 * The kind of markup used for the parts of a table or the items of a list.
	 */
	private enum Markup
	{
		NATIVE,
		HTML,
	}

	private final WikiConfig wikiConfig;

	private int inInlineBlock = 0;

	private boolean inSemiPre;

	/**
	 * The markup of the table whose parts are visited.
	 */
	private Markup tableMarkup;

	/**
	 * The markup of the list whose items are visited.
	 */
	private Markup listMarkup;

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
		b.append(genHtmlAttributes(e));

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

	/**
	 * Generates the HTML attributes of an element. Each attribute is preceded
	 * by a space.
	 */
	private String genHtmlAttributes(Wom3ElementNode e)
	{
		StringBuilder b = new StringBuilder();
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
		return b.toString();
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
		// Comments without RTD were added after conversion
		if (!startsWithRtd(comment))
		{
			prependRtd(comment, "<!--");
			appendRtd(comment, "-->");
		}

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

		Markup oldTableMarkup = tableMarkup;
		tableMarkup = hasHtmlTagRtd(table) ? Markup.HTML : Markup.NATIVE;
		iterate(table);
		tableMarkup = oldTableMarkup;
	}

	public void visit(Wom3TableCaption caption)
	{
		processTablePart(caption, "|+");
	}

	public void visit(Wom3TableBody body)
	{
		// Native tables have no markup for table bodies
		processTablePart(body, null);
	}

	public void visit(Wom3TableRow row)
	{
		processTablePart(row, "|-");
	}

	public void visit(Wom3TableHeaderCell header)
	{
		processTablePart(header, "!");
	}

	public void visit(Wom3TableCell cell)
	{
		processTablePart(cell, "|");
	}

	/**
	 * Generates the RTD of a table part that was added after conversion. In a
	 * native table the part is rendered as native markup that starts on a line
	 * of its own, otherwise as HTML tag.
	 *
	 * @param nativeMarkup
	 *            The native markup of the table part or {@code null} if there
	 *            is none.
	 */
	private void processTablePart(Wom3ElementNode e, String nativeMarkup)
	{
		boolean generateNative = false;
		if ((tableMarkup != null) && isNewTablePart(e))
		{
			if (tableMarkup == Markup.HTML)
			{
				generateHtmlTagRtd(e);
			}
			else if (nativeMarkup != null)
			{
				generateNative = true;
				generateNativeTablePartRtd(e, nativeMarkup);
			}
		}

		// TODO: We're not trimming any whitespace yet since we have no clue how
		// tables and whitespace behave
		iterate(e);

		// The markup that follows has to start on a new line
		if (generateNative && (getNewlineCount() == 0) && isFollowedByMarkupOnSameLine(e))
			appendRtdAfterProcessing(e, "\n");
	}

	private void generateNativeTablePartRtd(Wom3ElementNode e, String nativeMarkup)
	{
		StringBuilder b = new StringBuilder();

		// Native table markup has to start at the beginning of a line
		if (getNewlineCount() == 0)
			b.append('\n');

		b.append(nativeMarkup);
		String attrs = genHtmlAttributes(e);
		if (e instanceof Wom3TableRow)
		{
			b.append(attrs);
			b.append('\n');
		}
		else if (attrs.isEmpty())
		{
			b.append(' ');
		}
		else
		{
			b.append(attrs);
			b.append(" | ");
		}

		prependRtd(e, b.toString());
	}

	/**
	 * Parts of converted tables can lack RTD: Implicit table bodies and rows
	 * have no RTD themselves but their cells do. Therefore, a body or row was
	 * only added after conversion if none of its descendants carries RTD
	 * either.
	 */
	private boolean isNewTablePart(Wom3ElementNode e)
	{
		if (startsWithRtd(e))
			return false;
		if ((e instanceof Wom3TableBody) || (e instanceof Wom3TableRow))
			return !containsRtd(e) && hasTableContent(e);
		return true;
	}

	private boolean hasTableContent(Wom3Node e)
	{
		for (Wom3Node c : e)
		{
			if ((c instanceof Wom3TableRow)
					|| (c instanceof Wom3TableCell)
					|| (c instanceof Wom3TableHeaderCell))
				return true;
		}
		return false;
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
		// Headings without RTD were added after conversion
		boolean generate = !startsWithRtd(heading);
		if (generate)
		{
			String equals = StringTools.strrep('=', getSectionLevel(heading));
			prependRtd(heading, equals + " ");
			appendRtd(heading, " " + equals);
		}

		/* TODO: Tricky: Trimming might be necessary in case the heading is an 
		 * HTML element!
		 */
		iterate(heading);

		// The section body has to start on a new line
		if (generate && (getNewlineCount() == 0) && isFollowedByMarkupOnSameLine(heading))
			appendRtdAfterProcessing(heading, "\n");
	}

	private int getSectionLevel(Wom3Heading heading)
	{
		Wom3Node parent = heading.getParentNode();
		if (!(parent instanceof Wom3Section))
			throw new IllegalStateException("A heading has to be the child of a section");
		return ((Wom3Section) parent).getLevel();
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
	// Lists

	/* Native lists have no RTD themselves, only their items do. A list
	 * without RTD whose items have no RTD either was added after conversion.
	 * A new list is rendered as native list if all its items fit on one line
	 * and as HTML list otherwise. New items of existing lists use the markup
	 * of their siblings.
	 */

	public void visit(Wom3DefinitionList list)
	{
		processList(list);
	}

	public void visit(Wom3DefinitionListTerm term)
	{
		// TODO: Tricky: Might require trimming when HTML element!
		processListItem(term, ';');
	}

	public void visit(Wom3DefinitionListDef def)
	{
		// TODO: Tricky: Might require trimming when HTML element!
		processListItem(def, ':');
	}

	public void visit(Wom3OrderedList list)
	{
		processList(list);
	}

	public void visit(Wom3UnorderedList list)
	{
		processList(list);
	}

	public void visit(Wom3ListItem li)
	{
		processListItem(li, (li.getParentNode() instanceof Wom3OrderedList) ? '#' : '*');
	}

	private void processList(Wom3ElementNode list)
	{
		Markup markup;
		Wom3Node itemWithRtd = findFirstListItemWithRtd(list);
		if (hasHtmlTagRtd(list))
		{
			markup = Markup.HTML;
		}
		else if (itemWithRtd != null)
		{
			markup = hasHtmlTagRtd(itemWithRtd) ? Markup.HTML : Markup.NATIVE;
		}
		else if (startsWithRtd(list))
		{
			// A converted native list whose items were all replaced
			markup = Markup.NATIVE;
		}
		else if (canRenderAsNativeList(list))
		{
			markup = Markup.NATIVE;
			fixNewlinesBeforeElement(list, true);
		}
		else
		{
			markup = Markup.HTML;
			generateHtmlTagRtd(list);
			fixNewlinesBeforeElement(list, false);
		}

		Markup oldListMarkup = listMarkup;
		listMarkup = markup;
		iterate(list);
		listMarkup = oldListMarkup;
	}

	private void processListItem(Wom3ElementNode item, char bullet)
	{
		++inInlineBlock;

		boolean generateNative = false;
		if (!startsWithRtd(item))
		{
			// Items without RTD were added after conversion
			if (!isList(item.getParentNode()) || (listMarkup == Markup.HTML))
			{
				generateHtmlTagRtd(item);
			}
			else
			{
				generateNative = true;
				String prefix = getNativeListPrefix(item, bullet);
				// A native list item has to start at the beginning of a line
				if (!isAtPageStart() && (getNewlineCount() == 0))
					prefix = "\n" + prefix;
				prependRtd(item, prefix + " ");
			}
		}

		iterate(item);

		// The markup that follows has to start on a new line
		if (generateNative && (getNewlineCount() == 0) && isFollowedByMarkupOnSameLine(item))
			appendRtdAfterProcessing(item, "\n");

		--inInlineBlock;
	}

	private Wom3Node findFirstListItemWithRtd(Wom3Node list)
	{
		for (Wom3Node c : list)
		{
			if (isListItem(c) && startsWithRtd(c))
				return c;
		}
		return null;
	}

	/**
	 * Checks if a list that was added after conversion can be rendered as
	 * native list: Every item has to fit on one line. Only nested lists which
	 * can be rendered as native lists themselves may follow the content of an
	 * item.
	 */
	private boolean canRenderAsNativeList(Wom3Node list)
	{
		boolean hasItems = false;
		for (Wom3Node item : list)
		{
			if (!isListItem(item) || !canRenderAsNativeListItem(item))
				return false;
			hasItems = true;
		}
		return hasItems;
	}

	private boolean canRenderAsNativeListItem(Wom3Node item)
	{
		boolean hadNestedList = false;
		for (Wom3Node c : item)
		{
			if (isList(c))
			{
				if (!canRenderAsNativeList(c))
					return false;
				hadNestedList = true;
			}
			else if (hadNestedList || !fitsOnOneLine(c))
			{
				return false;
			}
		}
		return true;
	}

	private boolean fitsOnOneLine(Wom3Node n)
	{
		if (n instanceof Wom3Comment)
			// Invisible to the parser
			return true;
		if (n instanceof Wom3Text)
			return n.getTextContent().indexOf('\n') == -1;
		if (isBlockElement(n))
			return false;
		for (Wom3Node c : n)
		{
			if (!fitsOnOneLine(c))
				return false;
		}
		return true;
	}

	/**
	 * Returns the prefix of a new item in a native list (e.g. "**"). It is
	 * derived from a sibling item that carries RTD. The first item of a new
	 * list extends the prefix of the enclosing list item.
	 */
	private String getNativeListPrefix(Wom3Node item, char bullet)
	{
		Wom3Node list = item.getParentNode();
		for (Wom3Node sibling : list)
		{
			if ((sibling != item) && isListItem(sibling))
			{
				String prefix = getNativeListItemPrefix(sibling);
				if (!prefix.isEmpty())
					return prefix.substring(0, prefix.length() - 1) + bullet;
			}
		}

		Wom3Node container = list.getParentNode();
		String outer = isListItem(container) ? getNativeListItemPrefix(container) : "";
		return outer + bullet;
	}

	/**
	 * Returns the list prefix characters (e.g. "*#") of the RTD that a native
	 * list item starts with or an empty string.
	 */
	private String getNativeListItemPrefix(Wom3Node item)
	{
		Wom3Rtd rtd = getFirstRtdNode(item);
		if (rtd == null)
			return "";

		String text = rtd.getTextContent();
		int from = 0;
		while ((from < text.length()) && (text.charAt(from) == '\n'))
			++from;
		int to = from;
		while ((to < text.length()) && isListPrefixChar(text.charAt(to)))
			++to;
		return text.substring(from, to);
	}

	private static boolean isList(Wom3Node n)
	{
		return (n instanceof Wom3List) || (n instanceof Wom3DefinitionList);
	}

	private static boolean isListItem(Wom3Node n)
	{
		return (n instanceof Wom3ListItem)
				|| (n instanceof Wom3DefinitionListTerm)
				|| (n instanceof Wom3DefinitionListDef);
	}

	// =========================================================================
	// A pre element

	public void visit(Wom3Pre pre)
	{
		/* Tricky: We have to check if this is a <pre> tag extension (which then 
		 * would contain a <nowiki> node as well) or a whitespace prefixed pre
		 * paragraph.
		 */
		// A pre element without RTD is either a whitespace prefixed pre
		// paragraph or it was added after conversion. In the latter case it
		// is rendered as <pre> tag.
		if (!startsWithRtd(pre) && !isSemiPre(pre))
			generateHtmlTagRtd(pre);

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

	/**
	 * Checks if every line of the content of a pre element starts with a
	 * space like the lines of a whitespace prefixed pre paragraph.
	 */
	private boolean isSemiPre(Wom3Pre pre)
	{
		String content = womToWmFast(pre);
		if (content.isEmpty() || (content.charAt(0) != ' '))
			return false;

		for (int i = content.indexOf('\n'); i != -1; i = content.indexOf('\n', i + 1))
		{
			if ((i + 1 < content.length()) && (" \n".indexOf(content.charAt(i + 1)) == -1))
				return false;
		}
		return true;
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

		// External links without RTD were added after conversion
		if (!startsWithRtd(link))
		{
			String target = link.getTarget().toString();
			Wom3Title title = link.getLinkTitle();
			if (link.isPlainUrl() && (title == null))
			{
				prependRtd(link, target);
			}
			else
			{
				prependRtd(link, "[" + target + ((title != null) ? " " : ""));
				appendRtd(link, "]");
			}
		}

		iterate(link);
	}

	public void visit(Wom3Image image)
	{
		// Images without RTD were added after conversion
		if (!startsWithRtd(image))
			generateImageRtd(image);

		fixNewlinesBeforeElement(image, false);
		iterate(image);
	}

	public void visit(Wom3ImageCaption caption)
	{
		if (!startsWithRtd(caption))
			prependRtd(caption, "|");

		iterate(caption);
	}

	private void generateImageRtd(Wom3Image image)
	{
		StringBuilder b = new StringBuilder();
		b.append("[[");
		b.append(image.getSource());

		appendImageOption(b, genImageFormat(image.getFormat()));
		if (image.isBorder())
			appendImageOption(b, "border");
		appendImageOption(b, genImageHAlign(image.getHAlign()));
		appendImageOption(b, genImageVAlign(image.getVAlign()));

		Integer width = image.getWidth();
		Integer height = image.getHeight();
		if ((width != null) || (height != null))
		{
			appendImageOption(b, ""
					+ ((width != null) ? width : "")
					+ ((height != null) ? "x" + height : "")
					+ "px");
		}

		if (image.isUpright())
			appendImageOption(b, "upright");
		if (image.getExtLink() != null)
			appendImageOption(b, "link=" + image.getExtLink());
		else if (image.getIntLink() != null)
			appendImageOption(b, "link=" + image.getIntLink());
		if (image.getAlt() != null)
			appendImageOption(b, "alt=" + image.getAlt());

		prependRtd(image, b.toString());
		appendRtd(image, "]]");
	}

	private static void appendImageOption(StringBuilder b, String option)
	{
		if (option != null)
		{
			b.append('|');
			b.append(option);
		}
	}

	private static String genImageFormat(Wom3ImageFormat format)
	{
		if (format == null)
			return null;

		switch (format)
		{
			case UNRESTRAINED:
				return null;
			case FRAMELESS:
				return "frameless";
			case THUMBNAIL:
				return "thumb";
			case FRAME:
				return "frame";
		}

		// Don't push into default: case.
		// This way we'll get a warning if we missed a constant.
		throw new IllegalArgumentException("Unknown image format: " + format);
	}

	private static String genImageHAlign(Wom3ImageHAlign hAlign)
	{
		if (hAlign == null)
			return null;

		switch (hAlign)
		{
			case DEFAULT:
				return null;
			case NONE:
				return "none";
			case LEFT:
				return "left";
			case CENTER:
				return "center";
			case RIGHT:
				return "right";
		}

		// Don't push into default: case.
		// This way we'll get a warning if we missed a constant.
		throw new IllegalArgumentException("Unknown image horizontal alignment: " + hAlign);
	}

	private static String genImageVAlign(Wom3ImageVAlign vAlign)
	{
		if (vAlign == null)
			return null;

		switch (vAlign)
		{
			case BASELINE:
				return "baseline";
			case SUB:
				return "sub";
			case SUPER:
				return "super";
			case TOP:
				return "top";
			case TEXT_TOP:
				return "text-top";
			case MIDDLE:
				return "middle";
			case BOTTOM:
				return "bottom";
			case TEXT_BOTTOM:
				return "text-bottom";
		}

		// Don't push into default: case.
		// This way we'll get a warning if we missed a constant.
		throw new IllegalArgumentException("Unknown image vertical alignment: " + vAlign);
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
		if (!startsWithRtd(e) && (e instanceof SwcTransclusion))
			generateTransclusionRtd((SwcTransclusion) e);

		// The stuff inside transclusions is invisible to the parser
		appendWm("{{N|...}}");
	}

	/**
	 * Generates the RTD of a transclusion that was added after conversion.
	 */
	private void generateTransclusionRtd(SwcTransclusion transclusion)
	{
		prependRtd(transclusion, "{{");
		for (SwcArg arg : transclusion.getArguments())
		{
			if (startsWithRtd(arg))
				continue;
			prependRtd(arg, "|");
			if (arg.hasName() && (arg.getValue() != null))
				insertRtdBefore(arg.getValue(), "=");
		}
		appendRtd(transclusion, "}}");
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
