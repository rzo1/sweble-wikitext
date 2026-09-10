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

package org.sweble.wikitext.engine.ext.parser_functions;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.parser.LinkTargetException;
import org.sweble.wikitext.parser.utils.StringConversionException;

public class ParserFunctionTitleparts
		extends
			ParserFunctionsExtPfn
{
	private static final long serialVersionUID = 1L;

	/**
	 * MediaWiki splits a title into at most this many segments. The last
	 * segment contains the rest of the title.
	 */
	private static final int MAX_SEGMENTS = 25;

	/**
	 * For un-marshaling only.
	 */
	public ParserFunctionTitleparts()
	{
		super("titleparts");
	}

	public ParserFunctionTitleparts(WikiConfig wikiConfig)
	{
		super(wikiConfig, "titleparts");
	}

	@Override
	public WtNode invoke(
			WtTemplate pfn,
			ExpansionFrame frame,
			List<? extends WtNode> args)
	{
		if (args.size() < 1)
			return pfn;

		WtNode arg0 = frame.expand(args.get(0));

		PageTitle pageTitle;
		int partCount = 0;
		int firstPart = 0;
		String titleStr = null;
		try
		{
			titleStr = tu().astToText(arg0).trim();
			pageTitle = PageTitle.make(frame.getWikiConfig(), titleStr);

			if (args.size() > 1)
			{
				WtNode arg1 = frame.expand(args.get(1));
				String countStr = tu().astToText(arg1).trim();
				try
				{
					if (!countStr.isEmpty())
						partCount = Integer.parseInt(countStr);
				}
				catch (NumberFormatException e)
				{
					fileIllegalArgumentsWarning(
							frame,
							WarningSeverity.INFORMATIVE,
							pfn,
							"Number of segments `" + countStr + "' is not a number and was ignored");
				}
			}

			if (args.size() > 2)
			{
				WtNode arg2 = frame.expand(args.get(2));
				String firstStr = tu().astToText(arg2).trim();
				try
				{
					if (!firstStr.isEmpty())
						firstPart = Integer.parseInt(firstStr);
				}
				catch (NumberFormatException e)
				{
					fileIllegalArgumentsWarning(
							frame,
							WarningSeverity.INFORMATIVE,
							pfn,
							"First segment `" + firstStr + "' is not a number and was ignored");
				}
			}
		}
		catch (StringConversionException ee)
		{
			// We have to convert the entire argument to a string to create a page name from it.
			fileIllegalArgumentsWarning(
					frame,
					WarningSeverity.NORMAL,
					pfn,
					"Parser function arguments cannot be converted into plain text");
			return pfn;
		}
		catch (LinkTargetException e)
		{
			// A page with an illegal name cannot be split properly.
			fileInvalidPagenameWarning(frame, WarningSeverity.NORMAL, arg0, titleStr);
			return pfn;
		}

		// Like MediaWiki, split the normalized title including its namespace.
		String[] parts = pageTitle.getPrefixedText().split("/", MAX_SEGMENTS);

		// The first segment is counted from 1, negative values count from the
		// end. A segment count of 0 selects all remaining segments, negative
		// values leave out segments at the end.
		if (firstPart > 0)
			--firstPart;

		return nf().text(StringUtils.join(slice(parts, firstPart, partCount), "/"));
	}

	/**
	 * Extracts a slice of an array like PHP's array_slice().
	 *
	 * @param offset
	 *            The index of the first element. Negative values count from the
	 *            end of the array.
	 * @param length
	 *            The number of elements. 0 means all remaining elements,
	 *            negative values leave out that many elements at the end of
	 *            the array.
	 */
	private static List<String> slice(String[] array, int offset, int length)
	{
		int n = array.length;

		int from = (offset < 0) ? Math.max(n + offset, 0) : Math.min(offset, n);

		int to;
		if (length > 0)
			to = (int) Math.min((long) from + length, n);
		else if (length < 0)
			to = n + length;
		else
			to = n;

		if (to <= from)
			return Arrays.asList();

		return Arrays.asList(array).subList(from, to);
	}
}
