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

package org.sweble.wikitext.engine.ext.core;

import java.util.List;

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.ParserFunctionBase;
import org.sweble.wikitext.engine.PfnArgumentMode;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.parser.LinkTargetException;
import org.sweble.wikitext.parser.utils.StringConversionException;

public abstract class CorePfnVariable
		extends
			ParserFunctionBase
{
	private static final long serialVersionUID = 1L;

	// =========================================================================

	/**
	 * For un-marshaling only.
	 */
	public CorePfnVariable(String name)
	{
		// Most variables don't take arguments so don't waste time with funny
		// conversions.
		super(PfnArgumentMode.TEMPLATE_ARGUMENTS, name);
	}

	/**
	 * For un-marshaling only.
	 */
	public CorePfnVariable(PfnArgumentMode argMode, String name)
	{
		super(argMode, name);
	}

	public CorePfnVariable(WikiConfig wikiConfig, String name)
	{
		// Most variables don't take arguments so don't waste time with funny
		// conversions.
		super(wikiConfig, PfnArgumentMode.TEMPLATE_ARGUMENTS, name);
	}

	public CorePfnVariable(
			WikiConfig wikiConfig,
			PfnArgumentMode argMode,
			String name)
	{
		super(wikiConfig, argMode, name);
	}

	// =========================================================================

	@Override
	public final WtNode invoke(
			WtNode var,
			ExpansionFrame frame,
			List<? extends WtNode> argsValues)
	{
		return invoke((WtTemplate) var, frame, argsValues);
	}

	public WtNode invoke(
			WtTemplate var,
			ExpansionFrame frame,
			List<? extends WtNode> argsValues)
	{
		return invoke(var, frame);
	}

	protected WtNode invoke(WtTemplate var, ExpansionFrame frame)
	{
		return var;
	}

	// =========================================================================

	/**
	 * Determines the page a variable like {@code PAGENAME} refers to.
	 *
	 * If the variable is called with a page name as argument (e.g.
	 * {@code {{PAGENAME:Foo}}}), that page is returned. The part after the
	 * colon is passed as additional first argument. Otherwise (e.g.
	 * {@code {{PAGENAME}}}) the page that is being rendered is returned.
	 *
	 * Requires the argument mode
	 * {@link PfnArgumentMode#EXPANDED_AND_TRIMMED_VALUES}.
	 *
	 * @return The page the variable refers to or {@code null} if the argument
	 *         is not a valid page name. In the latter case a warning is filed.
	 */
	protected PageTitle getTitleArgument(
			WtTemplate var,
			ExpansionFrame frame,
			List<? extends WtNode> argsValues)
	{
		// Only a call with colon has more arguments than the template itself.
		if (argsValues.size() <= var.getArgs().size())
			return frame.getRootFrame().getTitle();

		WtNode titleNode = argsValues.get(0);

		String titleStr = null;
		try
		{
			titleStr = tu().astToText(titleNode).trim();

			return PageTitle.make(frame.getWikiConfig(), titleStr);
		}
		catch (StringConversionException e)
		{
			fileInvalidNameWarning(frame, WarningSeverity.NORMAL, titleNode);
			return null;
		}
		catch (LinkTargetException e)
		{
			fileInvalidPagenameWarning(frame, WarningSeverity.NORMAL, titleNode, titleStr);
			return null;
		}
	}
}
