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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngineRtData;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.utils.StringConversionException;

/**
 * <pre>
 * {{#rel2abs: path }}
 * {{#rel2abs: path | base path }}
 * </pre>
 *
 * Returns the absolute path to a subpage, relative to the given base path or
 * the title of the current page. Titles are treated as slash-separated paths.
 * Following the subpage link syntax, an initial slash makes a path relative.
 *
 * A port of MediaWiki's <code>ParserFunctions::rel2abs()</code>.
 */
public class ParserFunctionRel2Abs
		extends
			ParserFunctionsExtPfn
{
	private static final long serialVersionUID = 1L;

	private static final Pattern CURRENT_PATH_DOTS_RX = Pattern.compile("/(\\./)+");

	private static final Pattern DOUBLE_SLASHES_RX = Pattern.compile("/{2,}");

	/**
	 * For un-marshaling only.
	 */
	public ParserFunctionRel2Abs()
	{
		super("rel2abs");
	}

	public ParserFunctionRel2Abs(WikiConfig wikiConfig)
	{
		super(wikiConfig, "rel2abs");
	}

	@Override
	public WtNode invoke(
			WtTemplate pfn,
			ExpansionFrame frame,
			List<? extends WtNode> args)
	{
		String to = expandArgToString(frame, args, 0);
		String from = expandArgToString(frame, args, 1);
		if (to == null || from == null)
			return pfn;

		if (from.isEmpty())
			from = frame.getRootFrame().getTitle().getPrefixedText();

		to = StringUtils.stripEnd(to, " /");

		// if we have an empty path, or just one containing a dot
		if (to.isEmpty() || to.equals("."))
			return nf().text(from);

		// if the path isn't relative
		if (!to.startsWith("/")
				&& !to.startsWith("./")
				&& !to.startsWith("../")
				&& !to.equals(".."))
			from = "";

		// Make a long path, containing both, enclose it in /.../
		String fullPath = "/" + from + "/" + to + "/";

		// remove redundant current path dots
		fullPath = CURRENT_PATH_DOTS_RX.matcher(fullPath).replaceAll("/");

		// remove double slashes
		fullPath = DOUBLE_SLASHES_RX.matcher(fullPath).replaceAll("/");

		// remove the enclosing slashes now
		fullPath = StringUtils.strip(fullPath, "/");

		List<String> newExploded = new ArrayList<String>();
		for (String current : fullPath.split("/", -1))
		{
			if (current.equals(".."))
			{
				// removing one level
				if (newExploded.isEmpty())
				{
					// attempted to access a node above root node
					return EngineRtData.set(nf().softError(
							"Error: Invalid depth in path: \"" + fullPath +
									"\" (tried to access a node above the root node)."));
				}

				newExploded.remove(newExploded.size() - 1);
			}
			else
			{
				newExploded.add(current);
			}
		}

		return nf().text(StringUtils.join(newExploded, '/'));
	}

	/**
	 * @return The expanded and trimmed argument, an empty string if the
	 *         argument is missing, or {@code null} if the argument cannot be
	 *         converted to a string.
	 */
	private String expandArgToString(
			ExpansionFrame frame,
			List<? extends WtNode> args,
			int index)
	{
		if (args.size() <= index)
			return "";

		WtNode arg = frame.expand(args.get(index));
		try
		{
			return tu().astToText(arg).trim();
		}
		catch (StringConversionException e)
		{
			fileInvalidNameWarning(frame, WarningSeverity.NORMAL, arg);
			return null;
		}
	}
}
