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

import java.util.List;

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.ext.parser_functions.ExprParser.ExprError;
import org.sweble.wikitext.engine.nodes.EngineRtData;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.utils.StringConversionException;

/**
 * <pre>
 * {{#ifexpr:
 *       expression
 *     | value if true
 *     | value if false
 * }}
 * </pre>
 *
 * Like MediaWiki's <code>ParserFunctions::ifexpr()</code>, an invalid
 * expression results in the error of the expression instead of one of the
 * branches.
 */
public class ParserFunctionIfExpr
		extends
			ParserFunctionsExtPfn.CtrlStmt
{
	private static final long serialVersionUID = 1L;

	/**
	 * For un-marshaling only.
	 */
	public ParserFunctionIfExpr()
	{
		super("ifexpr");
	}

	public ParserFunctionIfExpr(WikiConfig wikiConfig)
	{
		super(wikiConfig, "ifexpr");
	}

	@Override
	protected WtNode evaluate(
			WtTemplate pfn,
			ExpansionFrame frame,
			List<? extends WtNode> args)
	{
		if (args.size() < 1)
			return null;

		WtNode test = frame.expand(args.get(0));

		String expr = null;
		try
		{
			expr = tu().astToText(test).trim();
		}
		catch (StringConversionException e)
		{
			// Invalid expressions evaluate to false
			fileInvalidNameWarning(frame, WarningSeverity.NORMAL, test);
			return getBranch(args, false);
		}

		if (expr.isEmpty())
			return getBranch(args, false);

		ExprParser p = new ExprParser();
		String result;
		try
		{
			result = p.parse(expr);
		}
		catch (ExprError e)
		{
			fileIllegalArgumentsWarning(
					frame,
					WarningSeverity.NORMAL,
					pfn,
					"Invalid expression `" + expr + "': " + e.getMessage());

			// Like MediaWiki, return the error
			return EngineRtData.set(nf().softError(e.getMessage()));
		}

		return getBranch(args, isTrue(result));
	}

	private static boolean isTrue(String result)
	{
		if (result == null || result.isEmpty())
			return false;

		try
		{
			return Double.parseDouble(result) != 0.;
		}
		catch (NumberFormatException e)
		{
			// Like MediaWiki, treat non-numeric results like "INF" or "NAN"
			// as non-empty strings, which are true
			return true;
		}
	}

	/**
	 * @return The then or else branch or {@code null} if the branch is
	 *         missing.
	 */
	private static WtNode getBranch(List<? extends WtNode> args, boolean cond)
	{
		int index = cond ? 1 : 2;
		return (args.size() > index) ? args.get(index) : null;
	}
}
