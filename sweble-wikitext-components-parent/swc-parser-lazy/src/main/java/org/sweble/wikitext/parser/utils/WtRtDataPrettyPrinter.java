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

package org.sweble.wikitext.parser.utils;

import java.io.StringWriter;
import java.io.Writer;

import org.sweble.wikitext.parser.WtRtData;
import org.sweble.wikitext.parser.nodes.WtContentNode;
import org.sweble.wikitext.parser.nodes.WtDefinitionList;
import org.sweble.wikitext.parser.nodes.WtDefinitionListDef;
import org.sweble.wikitext.parser.nodes.WtDefinitionListTerm;
import org.sweble.wikitext.parser.nodes.WtEmptyImmutableNode;
import org.sweble.wikitext.parser.nodes.WtIgnored;
import org.sweble.wikitext.parser.nodes.WtLinkOptionGarbage;
import org.sweble.wikitext.parser.nodes.WtLinkTitle;
import org.sweble.wikitext.parser.nodes.WtListItem;
import org.sweble.wikitext.parser.nodes.WtNewline;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtOrderedList;
import org.sweble.wikitext.parser.nodes.WtParagraph;
import org.sweble.wikitext.parser.nodes.WtSection;
import org.sweble.wikitext.parser.nodes.WtSemiPre;
import org.sweble.wikitext.parser.nodes.WtStringNode;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.nodes.WtUnorderedList;
import org.sweble.wikitext.parser.nodes.WtXmlAttributeGarbage;

/**
 * Prints nodes which carry round-trip data exactly as they were parsed and
 * pretty prints all other nodes like the {@link WtPrettyPrinter}.
 *
 * The markup of the pretty printer never repeats syntax which is already part
 * of the round-trip data of a node:
 * <ul>
 * <li>A separator like the pipe in front of an image link option or the
 * semicolon between two language conversion rules is only printed if the node
 * it belongs to has no round-trip data.</li>
 * <li>A list item which carries the prefix of its line in its round-trip data
 * prints the whole prefix, including the part which belongs to the enclosing
 * list items.</li>
 * <li>If the AST carries round-trip data, the white space which separates
 * paragraphs, sections, lists, etc. is part of the AST. Text is then printed
 * verbatim and no newlines are added in front of or after these nodes.</li>
 * </ul>
 *
 * An AST carries round-trip data if any of its nodes does. An AST without
 * round-trip data is printed exactly like the {@link WtPrettyPrinter} prints
 * it. Nodes which the parser creates without round-trip data (e.g. formatting
 * elements which are reopened after mis-nested markup) are pretty printed as
 * well.
 */
public class WtRtDataPrettyPrinter
		extends
			WtPrettyPrinter
{
	/** Whether the printed AST carries round-trip data. */
	private boolean roundTrip;

	// =========================================================================

	@Override
	public void visit(WtNewline n)
	{
		if (roundTrip)
			p.verbatim(n.getContent());
		else
			super.visit(n);
	}

	@Override
	public void visit(WtIgnored n)
	{
		if (roundTrip)
			p.verbatim(n.getContent());
		else
			super.visit(n);
	}

	@Override
	public void visit(WtText n)
	{
		if (roundTrip)
			p.verbatim(n.getContent());
		else
			super.visit(n);
	}

	@Override
	public void visit(WtLinkOptionGarbage n)
	{
		if (roundTrip)
			iterate(n);
		else
			super.visit(n);
	}

	@Override
	public void visit(WtXmlAttributeGarbage n)
	{
		if (roundTrip)
			iterate(n);
		else
			super.visit(n);
	}

	@Override
	public void visit(WtParagraph n)
	{
		if (roundTrip)
			iterate(n);
		else
			super.visit(n);
	}

	@Override
	public void visit(WtSemiPre n)
	{
		if (roundTrip)
			iterate(n);
		else
			super.visit(n);
	}

	@Override
	public void visit(WtSection n)
	{
		if (roundTrip)
			iterateInScope(n);
		else
			super.visit(n);
	}

	@Override
	public void visit(WtDefinitionList n)
	{
		if (roundTrip)
			iterateInScope(n);
		else
			super.visit(n);
	}

	@Override
	public void visit(WtOrderedList n)
	{
		if (roundTrip)
			iterateInScope(n);
		else
			super.visit(n);
	}

	@Override
	public void visit(WtUnorderedList n)
	{
		if (roundTrip)
			iterateInScope(n);
		else
			super.visit(n);
	}

	@Override
	public void visit(WtDefinitionListDef n)
	{
		if (isPrefixPrintedByNestedItem(n))
			iterateInScope(n);
		else
			super.visit(n);
	}

	@Override
	public void visit(WtDefinitionListTerm n)
	{
		if (isPrefixPrintedByNestedItem(n))
			iterateInScope(n);
		else
			super.visit(n);
	}

	@Override
	public void visit(WtListItem n)
	{
		if (isPrefixPrintedByNestedItem(n))
			iterateInScope(n);
		else
			super.visit(n);
	}

	@Override
	public void visit(WtLinkTitle n)
	{
		WtNode link = scope.peek();
		if (link != null && isSeparatorInLinkRtd(link))
			iterate(n);
		else
			super.visit(n);
	}

	/**
	 * Checks whether the separator in front of the title of a link, which is
	 * printed from its round-trip data, is already printed.
	 */
	private static boolean isSeparatorInLinkRtd(WtNode link)
	{
		switch (link.getNodeType())
		{
			case WtNode.NT_INTERNAL_LINK:
			case WtNode.NT_EXTERNAL_LINK:
				break;
			default:
				return false;
		}

		WtRtData rtd = link.getRtd();
		if (rtd == null || rtd.size() < 2)
			return false;

		// The round-trip data of the link contains the separator (e.g. the
		// white space in front of the title of an external link).
		if (rtd.getField(1).length > 0)
			return true;

		// The target is not printed because the link continues a link which
		// the parser had to split. The separator was printed with the first
		// part.
		WtRtData targetRtd = link.get(0).getRtd();
		return (targetRtd != null) && targetRtd.isSuppress();
	}

	@Override
	protected void printSeparator(String separator, WtNode node)
	{
		// Otherwise the separator is part of the node's round-trip data
		if (node.getRtd() == null)
			super.printSeparator(separator, node);
	}

	private void iterateInScope(WtNode n)
	{
		scope.push(n);
		iterate(n);
		scope.pop();
	}

	/**
	 * Checks whether a list item only opens a nested list and the first item
	 * of the nested list carries the prefix of the whole line in its
	 * round-trip data.
	 */
	private static boolean isPrefixPrintedByNestedItem(WtNode item)
	{
		while (!item.isEmpty())
		{
			WtNode list = item.get(0);
			switch (list.getNodeType())
			{
				case WtNode.NT_DEFINITION_LIST:
				case WtNode.NT_ORDERED_LIST:
				case WtNode.NT_UNORDERED_LIST:
					break;
				default:
					return false;
			}

			if (list.isEmpty())
				return false;

			item = list.get(0);
			if (item.getRtd() != null)
				return true;
		}
		return false;
	}

	// =========================================================================

	@Override
	protected WtNode before(WtNode node)
	{
		roundTrip = hasRtd(node);
		return super.before(node);
	}

	private static boolean hasRtd(WtNode node)
	{
		if (node.getRtd() != null)
			return true;
		for (WtNode n : node)
		{
			if (hasRtd(n))
				return true;
		}
		return false;
	}

	@Override
	protected Object dispatch(WtNode node)
	{
		WtRtData rtd = node.getRtd();
		if (rtd != null)
		{
			if (!rtd.isSuppress())
			{
				// Nodes without round-trip data might need their parent
				scope.push(node);
				if (node instanceof WtStringNode)
				{
					printStringNode(rtd, (WtStringNode) node);
				}
				else if (node instanceof WtContentNode)
				{
					printContentNode(rtd, (WtContentNode) node);
				}
				else
				{
					printAnyOtherNode(rtd, node);
				}
				scope.pop();
			}
			return null;
		}
		else
		{
			return super.dispatch(node);
		}
	}

	// =========================================================================

	protected void printStringNode(WtRtData rtd, WtStringNode contentNode)
	{
		if (rtd != null)
		{
			printRtd(rtd.getField(0));
		}
		else
		{
			p.verbatim(contentNode.getContent());
		}
	}

	protected void printContentNode(WtRtData rtd, WtContentNode contentNode)
	{
		if (rtd != null)
		{
			printRtd(rtd.getField(0));
			iterate(contentNode);
			printRtd(rtd.getField(1));
		}
		else
		{
			iterate(contentNode);
		}
	}

	protected void printAnyOtherNode(WtRtData rtd, WtNode node)
	{
		if (rtd != null)
		{
			int i = 0;
			for (WtNode n : node)
			{
				printRtd(rtd.getField(i++));
				// An absent node (e.g. a link without title) prints nothing
				if (!isAbsent(n))
					dispatch(n);
			}
			printRtd(rtd.getField(i));
		}
		else
		{
			iterate(node);
		}
	}

	private static boolean isAbsent(WtNode n)
	{
		return (n instanceof WtEmptyImmutableNode)
				&& ((WtEmptyImmutableNode) n).indicatesAbsence();
	}

	protected void printRtd(Object[] fields)
	{
		for (Object o : fields)
		{
			if (o instanceof WtNode)
			{
				dispatch((WtNode) o);
			}
			else
			{
				p.verbatim(String.valueOf(o));
			}
		}
	}

	// =========================================================================

	public static <T extends WtNode> String print(T node)
	{
		return print(new StringWriter(), node).toString();
	}

	public static <T extends WtNode> Writer print(Writer writer, T node)
	{
		new WtRtDataPrettyPrinter(writer).go(node);
		return writer;
	}

	// =========================================================================

	public WtRtDataPrettyPrinter(Writer writer)
	{
		super(writer);
	}
}
