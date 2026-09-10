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

package org.sweble.wikitext.example;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.regex.Pattern;

import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngNowiki;
import org.sweble.wikitext.engine.nodes.EngPage;
import org.sweble.wikitext.engine.output.HtmlSanitizer;
import org.sweble.wikitext.parser.nodes.WtBold;
import org.sweble.wikitext.parser.nodes.WtDefinitionList;
import org.sweble.wikitext.parser.nodes.WtDefinitionListDef;
import org.sweble.wikitext.parser.nodes.WtDefinitionListTerm;
import org.sweble.wikitext.parser.nodes.WtExternalLink;
import org.sweble.wikitext.parser.nodes.WtHorizontalRule;
import org.sweble.wikitext.parser.nodes.WtIgnored;
import org.sweble.wikitext.parser.nodes.WtIllegalCodePoint;
import org.sweble.wikitext.parser.nodes.WtImageLink;
import org.sweble.wikitext.parser.nodes.WtInternalLink;
import org.sweble.wikitext.parser.nodes.WtItalics;
import org.sweble.wikitext.parser.nodes.WtLctRuleConv;
import org.sweble.wikitext.parser.nodes.WtLctVarConv;
import org.sweble.wikitext.parser.nodes.WtListItem;
import org.sweble.wikitext.parser.nodes.WtNewline;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtNodeList;
import org.sweble.wikitext.parser.nodes.WtOrderedList;
import org.sweble.wikitext.parser.nodes.WtPageSwitch;
import org.sweble.wikitext.parser.nodes.WtParagraph;
import org.sweble.wikitext.parser.nodes.WtRedirect;
import org.sweble.wikitext.parser.nodes.WtSection;
import org.sweble.wikitext.parser.nodes.WtSemiPre;
import org.sweble.wikitext.parser.nodes.WtSemiPreLine;
import org.sweble.wikitext.parser.nodes.WtSignature;
import org.sweble.wikitext.parser.nodes.WtTable;
import org.sweble.wikitext.parser.nodes.WtTableCaption;
import org.sweble.wikitext.parser.nodes.WtTableCell;
import org.sweble.wikitext.parser.nodes.WtTableHeader;
import org.sweble.wikitext.parser.nodes.WtTableImplicitTableBody;
import org.sweble.wikitext.parser.nodes.WtTableRow;
import org.sweble.wikitext.parser.nodes.WtTagExtension;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.nodes.WtTemplateArgument;
import org.sweble.wikitext.parser.nodes.WtTemplateParameter;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.nodes.WtUnorderedList;
import org.sweble.wikitext.parser.nodes.WtUrl;
import org.sweble.wikitext.parser.nodes.WtWhitespace;
import org.sweble.wikitext.parser.nodes.WtXmlAttributes;
import org.sweble.wikitext.parser.nodes.WtXmlCharRef;
import org.sweble.wikitext.parser.nodes.WtXmlComment;
import org.sweble.wikitext.parser.nodes.WtXmlElement;
import org.sweble.wikitext.parser.nodes.WtXmlEmptyTag;
import org.sweble.wikitext.parser.nodes.WtXmlEndTag;
import org.sweble.wikitext.parser.nodes.WtXmlEntityRef;
import org.sweble.wikitext.parser.nodes.WtXmlStartTag;
import org.sweble.wikitext.parser.parser.LinkTargetException;

import de.fau.cs.osr.ptk.common.AstVisitor;
import de.fau.cs.osr.utils.StringTools;

/**
 * A visitor to convert an article AST into a pure text representation. To
 * better understand the visitor pattern as implemented by the Visitor class,
 * please take a look at the following resources:
 * <ul>
 * <li><a
 * href="http://en.wikipedia.org/wiki/Visitor_pattern">http://en.wikipedia
 * .org/wiki/Visitor_pattern</a> (classic pattern)</li>
 * <li><a
 * href="http://www.javaworld.com/javaworld/javatips/jw-javatip98.html">http
 * ://www.javaworld.com/javaworld/javatips/jw-javatip98.html</a> (the version we
 * use here)</li>
 * </ul>
 *
 * The methods needed to descend into an AST and visit the children of a given
 * node <code>n</code> are
 * <ul>
 * <li><code>dispatch(n)</code> - visit node <code>n</code>,</li>
 * <li><code>iterate(n)</code> - visit the <b>children</b> of node
 * <code>n</code>,</li>
 * <li><code>map(n)</code> - visit the <b>children</b> of node <code>n</code>
 * and gather the return values of the <code>visit()</code> calls in a list,</li>
 * <li><code>mapInPlace(n)</code> - visit the <b>children</b> of node
 * <code>n</code> and replace each child node <code>c</code> with the return
 * value of the call to <code>visit(c)</code>.</li>
 * </ul>
 */
public class TextConverter
		extends
			AstVisitor<WtNode>
{
	private static final Pattern ws = Pattern.compile("\\s+");

	/**
	 * Separates the cells of a table row.
	 */
	private static final String CELL_SEPARATOR = " | ";

	/**
	 * XML elements which are rendered as a list.
	 */
	private static final Set<String> LIST_ELEMENTS = setOf("dl", "ol", "ul");

	/**
	 * XML elements which start and end a line.
	 */
	private static final Set<String> BLOCK_ELEMENTS = setOf(
			"blockquote",
			"caption",
			"center",
			"dd",
			"div",
			"dt",
			"h1",
			"h2",
			"h3",
			"h4",
			"h5",
			"h6",
			"li");

	/**
	 * Tag extensions whose body is code and printed as preformatted text.
	 */
	private static final Set<String> CODE_TAG_EXTENSIONS = setOf(
			"graph",
			"score",
			"source",
			"syntaxhighlight",
			"timeline");

	private final WikiConfig config;

	private final int wrapCol;

	private StringBuilder sb;

	private StringBuilder line;

	private int extLinkNum;

	/**
	 * Becomes true if we are no long at the Beginning Of the whole Document.
	 */
	private boolean pastBod;

	private int needNewlines;

	private boolean needSpace;

	private boolean noWrap;

	/**
	 * The nesting depth of lists and tables.
	 */
	private int depth;

	/**
	 * The nesting depth of preformatted blocks.
	 */
	private int preDepth;

	/**
	 * True inside a semi-pre block whose lines start with a space that has to
	 * be removed.
	 */
	private boolean inSemiPre;

	/**
	 * True at the beginning of a line of a semi-pre block.
	 */
	private boolean semiPreLineStart;

	/**
	 * True if the next cell is the first cell of a table row.
	 */
	private boolean firstCell;

	private LinkedList<Integer> sections;

	// =========================================================================

	public TextConverter(WikiConfig config, int wrapCol)
	{
		this.config = config;
		this.wrapCol = wrapCol;
	}

	@Override
	protected WtNode before(WtNode node)
	{
		// This method is called by go() before visitation starts
		sb = new StringBuilder();
		line = new StringBuilder();
		extLinkNum = 1;
		pastBod = false;
		needNewlines = 0;
		needSpace = false;
		noWrap = false;
		depth = 0;
		preDepth = 0;
		inSemiPre = false;
		semiPreLineStart = false;
		firstCell = true;
		sections = new LinkedList<Integer>();
		return super.before(node);
	}

	@Override
	protected Object after(WtNode node, Object result)
	{
		finishLine();

		// This method is called by go() after visitation has finished
		// The return value will be passed to go() which passes it to the caller
		return sb.toString();
	}

	// =========================================================================

	public void visit(WtNode n)
	{
		// Fallback for all nodes that are not explicitly handled below: Print
		// the content of the node.
		iterate(n);
	}

	public void visit(WtNodeList n)
	{
		iterate(n);
	}

	public void visit(WtUnorderedList e)
	{
		list(e);
	}

	public void visit(WtOrderedList e)
	{
		list(e);
	}

	public void visit(WtListItem item)
	{
		newline(1);
		iterate(item);
	}

	public void visit(WtDefinitionList e)
	{
		list(e);
	}

	public void visit(WtDefinitionListTerm term)
	{
		newline(1);
		iterate(term);
	}

	public void visit(WtDefinitionListDef def)
	{
		newline(1);
		iterate(def);
	}

	public void visit(EngPage p)
	{
		iterate(p);
	}

	public void visit(WtText text)
	{
		write(text.getContent());
	}

	public void visit(EngNowiki nowiki)
	{
		write(nowiki.getContent());
	}

	public void visit(WtWhitespace w)
	{
		write(" ");
	}

	public void visit(WtNewline n)
	{
		write(" ");
	}

	public void visit(WtBold b)
	{
		write("**");
		iterate(b);
		write("**");
	}

	public void visit(WtItalics i)
	{
		write("//");
		iterate(i);
		write("//");
	}

	public void visit(WtXmlCharRef cr)
	{
		int codePoint = cr.getCodePoint();
		if (HtmlSanitizer.isValidCharReference(codePoint))
		{
			write(Character.toChars(codePoint));
		}
		else
		{
			write('�');
		}
	}

	public void visit(WtXmlEntityRef er)
	{
		String ch = er.getResolved();
		if (ch == null)
		{
			write('&');
			write(er.getName());
			write(';');
		}
		else
		{
			write(ch);
		}
	}

	public void visit(WtUrl wtUrl)
	{
		if (!wtUrl.getProtocol().isEmpty())
		{
			write(wtUrl.getProtocol());
			write(':');
		}
		write(wtUrl.getPath());
	}

	public void visit(WtExternalLink link)
	{
		write('[');
		write(extLinkNum++);
		write(']');
	}

	public void visit(WtInternalLink link)
	{
		try
		{
			if (link.getTarget().isResolved())
			{
				PageTitle page = PageTitle.make(config, link.getTarget().getAsString());
				// [[:Category:Foo]] is a link, not a category statement
				if (page.getNamespace().equals(config.getNamespace("Category")) && !page.hasInitialColon())
					return;
			}
		}
		catch (LinkTargetException e)
		{
		}

		write(link.getPrefix());
		if (!link.hasTitle())
		{
			if (link.getTarget().isResolved())
			{
				// The leading colon of [[:Foo]] is not shown
				String target = link.getTarget().getAsString();
				if (target.startsWith(":"))
					target = target.substring(1);
				write(target);
			}
			else
			{
				iterate(link.getTarget());
			}
		}
		else
		{
			iterate(link.getTitle());
		}
		write(link.getPostfix());
	}

	public void visit(WtRedirect n)
	{
		write("REDIRECT ");
		write(n.getTarget().getAsString());
		newline(2);
	}

	public void visit(WtSignature n)
	{
		// Without a pre-save transform MediaWiki shows signatures literally
		write(StringTools.strrep('~', n.getTildeCount()));
	}

	public void visit(WtLctVarConv n)
	{
		iterate(n.getText());
	}

	public void visit(WtSection s)
	{
		finishLine();
		StringBuilder saveSb = sb;
		boolean saveNoWrap = noWrap;

		sb = new StringBuilder();
		noWrap = true;

		iterate(s.getHeading());
		finishLine();
		String title = sb.toString().trim();

		sb = saveSb;

		if (s.getLevel() >= 1)
		{
			while (sections.size() > s.getLevel())
				sections.removeLast();
			while (sections.size() < s.getLevel())
				sections.add(1);

			StringBuilder sb2 = new StringBuilder();
			for (int i = 0; i < sections.size(); ++i)
			{
				if (i < 1)
					continue;

				sb2.append(sections.get(i));
				sb2.append('.');
			}

			if (sb2.length() > 0)
				sb2.append(' ');
			sb2.append(title);
			title = sb2.toString();
		}

		newline(2);
		write(title);
		newline(1);
		write(StringTools.strrep('-', title.length()));
		newline(2);

		noWrap = saveNoWrap;

		iterate(s.getBody());

		while (sections.size() > s.getLevel())
			sections.removeLast();
		sections.add(sections.removeLast() + 1);
	}

	public void visit(WtParagraph p)
	{
		newline(2);
		iterate(p);
		newline(2);
	}

	public void visit(WtSemiPre pre)
	{
		startPre();
		boolean saveInSemiPre = inSemiPre;
		// MediaWiki removes the space that starts each line. Unless the parser
		// keeps it apart, it is part of the text.
		inSemiPre = !config.getParserConfig().isPreserveSemiPreLeadingSpace();
		semiPreLineStart = true;
		iterate(pre);
		inSemiPre = saveInSemiPre;
		endPre();
	}

	public void visit(WtSemiPreLine n)
	{
		iterate(n);
		newline(1);
		semiPreLineStart = true;
	}

	public void visit(WtHorizontalRule hr)
	{
		horizontalRule();
	}

	public void visit(WtTable table)
	{
		table(table.getBody());
	}

	public void visit(WtTableImplicitTableBody body)
	{
		iterate(body.getBody());
	}

	public void visit(WtTableCaption caption)
	{
		newline(1);
		iterate(getCellContent(caption.getBody()));
		newline(1);
	}

	public void visit(WtTableRow row)
	{
		row(row.getBody());
	}

	public void visit(WtTableHeader header)
	{
		cell(header.getBody());
	}

	public void visit(WtTableCell cell)
	{
		cell(cell.getBody());
	}

	public void visit(WtXmlElement e)
	{
		String name = e.getName().toLowerCase();
		if (name.equals("br"))
		{
			newline(1);
		}
		else if (name.equals("hr"))
		{
			horizontalRule();
		}
		else if (name.equals("pre"))
		{
			startPre();
			iterate(e.getBody());
			endPre();
		}
		else if (name.equals("table"))
		{
			table(e.getBody());
		}
		else if (name.equals("tr"))
		{
			row(e.getBody());
		}
		else if (name.equals("td") || name.equals("th"))
		{
			cell(e.getBody());
		}
		else if (LIST_ELEMENTS.contains(name))
		{
			list(e.getBody());
		}
		else if (name.equals("p"))
		{
			newline(2);
			iterate(e.getBody());
			newline(2);
		}
		else if (BLOCK_ELEMENTS.contains(name))
		{
			newline(1);
			iterate(e.getBody());
			newline(1);
		}
		else
		{
			iterate(e.getBody());
		}
	}

	public void visit(WtTagExtension n)
	{
		String name = n.getName().trim().toLowerCase();

		// TODO: Should not get skipped!
		if (name.equals("ref") || name.equals("references"))
			return;

		if (!n.hasBody())
			return;

		String content = n.getBody().getContent();
		if (name.equals("pre") || CODE_TAG_EXTENSIONS.contains(name))
		{
			startPre();
			write(content);
			endPre();
		}
		else if (name.equals("poem"))
		{
			newline(1);
			for (String l : content.trim().split("\n"))
			{
				if (l.trim().isEmpty())
				{
					newline(2);
				}
				else
				{
					write(l);
					newline(1);
				}
			}
		}
		else
		{
			write(content);
		}
	}

	// =========================================================================
	// Stuff we want to hide

	public void visit(WtImageLink n)
	{
	}

	public void visit(WtIllegalCodePoint n)
	{
	}

	public void visit(WtXmlComment n)
	{
	}

	public void visit(WtTemplate n)
	{
	}

	public void visit(WtTemplateArgument n)
	{
	}

	public void visit(WtTemplateParameter n)
	{
	}

	public void visit(WtPageSwitch n)
	{
	}

	public void visit(WtLctRuleConv n)
	{
	}

	public void visit(WtIgnored n)
	{
	}

	public void visit(WtXmlAttributes n)
	{
	}

	public void visit(WtXmlStartTag n)
	{
	}

	public void visit(WtXmlEndTag n)
	{
	}

	public void visit(WtXmlEmptyTag n)
	{
	}

	// =========================================================================

	private void list(WtNode list)
	{
		newline(1);
		++depth;
		iterate(list);
		--depth;
		newline(blockNewlines());
	}

	private void table(WtNode body)
	{
		boolean saveFirstCell = firstCell;
		newline(blockNewlines());
		++depth;
		firstCell = true;
		iterate(body);
		--depth;
		newline(blockNewlines());
		firstCell = saveFirstCell;
	}

	private void row(WtNode body)
	{
		newline(1);
		firstCell = true;
		iterate(body);
		newline(1);
	}

	private void cell(WtNode body)
	{
		if (!firstCell)
			write(CELL_SEPARATOR);
		firstCell = false;
		iterate(getCellContent(body));
	}

	/**
	 * @return The content of the paragraph if it is the only content of a
	 *         table cell, otherwise the given body. This keeps the cells of a
	 *         row on one line.
	 */
	private static WtNode getCellContent(WtNode body)
	{
		if (body.size() >= 1 && body.get(0) instanceof WtParagraph)
		{
			for (int i = 1; i < body.size(); ++i)
			{
				WtNode c = body.get(i);
				boolean whitespace = (c instanceof WtNewline)
						|| (c instanceof WtWhitespace)
						|| (c instanceof WtText && ((WtText) c).getContent().trim().isEmpty());
				if (!whitespace)
					return body;
			}
			return body.get(0);
		}
		return body;
	}

	private void horizontalRule()
	{
		newline(1);
		write(StringTools.strrep('-', wrapCol));
		newline(2);
	}

	/**
	 * @return The number of newlines around a list or table: Top-level blocks
	 *         are separated by an empty line, nested blocks are not.
	 */
	private int blockNewlines()
	{
		return (depth == 0) ? 2 : 1;
	}

	private void startPre()
	{
		newline(2);
		++preDepth;
	}

	private void endPre()
	{
		--preDepth;
		newline(2);
	}

	private void newline(int num)
	{
		if (pastBod)
		{
			if (num > needNewlines)
				needNewlines = num;
		}
	}

	private void wantSpace()
	{
		if (pastBod)
			needSpace = true;
	}

	private void finishLine()
	{
		sb.append(line.toString());
		line.setLength(0);
	}

	private void writeNewlines(int num)
	{
		finishLine();
		sb.append(StringTools.strrep('\n', num));
		needNewlines = 0;
		needSpace = false;
	}

	private void writeWord(String s)
	{
		int length = s.length();
		if (length == 0)
			return;

		if (!noWrap && preDepth == 0 && needNewlines <= 0)
		{
			if (needSpace)
				length += 1;

			if (line.length() + length >= wrapCol && line.length() > 0)
				writeNewlines(1);
		}

		if (needSpace && needNewlines <= 0)
			line.append(' ');

		if (needNewlines > 0)
			writeNewlines(needNewlines);

		needSpace = false;
		pastBod = true;
		line.append(s);
	}

	/**
	 * Writes preformatted text: Whitespace and line breaks are kept.
	 */
	private void writePre(String s)
	{
		if (needNewlines > 0)
			writeNewlines(needNewlines);
		else if (needSpace)
			line.append(' ');

		needSpace = false;
		pastBod = true;

		String[] lines = s.split("\n", -1);
		for (int i = 0; i < lines.length; ++i)
		{
			if (i > 0)
			{
				writeNewlines(1);
				semiPreLineStart = true;
			}

			String l = lines[i];
			if (inSemiPre && semiPreLineStart && !l.isEmpty())
			{
				if (l.charAt(0) == ' ')
					l = l.substring(1);
				semiPreLineStart = false;
			}
			line.append(l);
		}
	}

	private void write(String s)
	{
		if (s.isEmpty())
			return;

		if (preDepth > 0)
		{
			writePre(s);
			return;
		}

		if (Character.isWhitespace(s.charAt(0)))
			wantSpace();

		String[] words = ws.split(s);
		for (int i = 0; i < words.length;)
		{
			writeWord(words[i]);
			if (++i < words.length)
				wantSpace();
		}

		if (Character.isWhitespace(s.charAt(s.length() - 1)))
			wantSpace();
	}

	private void write(char[] cs)
	{
		write(String.valueOf(cs));
	}

	private void write(char ch)
	{
		writeWord(String.valueOf(ch));
	}

	private void write(int num)
	{
		writeWord(String.valueOf(num));
	}

	private static Set<String> setOf(String... names)
	{
		return new HashSet<String>(Arrays.asList(names));
	}
}
