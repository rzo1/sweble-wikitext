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

package org.sweble.wikitext.engine.ext.builtin;

import java.util.List;

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.FullPage;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.ParserFunctionBase;
import org.sweble.wikitext.engine.PfnArgumentMode;
import org.sweble.wikitext.engine.config.ParserFunctionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngineRtData;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtNodeList;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.nodes.WtTemplateArgument;
import org.sweble.wikitext.parser.parser.LinkTargetException;
import org.sweble.wikitext.parser.utils.StringConversionException;

public class BuiltInParserFunctions
		extends
			ParserFunctionGroup
{
	private static final long serialVersionUID = 1L;

	// =========================================================================

	protected BuiltInParserFunctions(WikiConfig wikiConfig)
	{
		super("Built-in Parser Functions");
		addParserFunction(new ParserFunctionSafeSubst(wikiConfig));
		addParserFunction(new ParserFunctionMsgnw(wikiConfig));
	}

	public static BuiltInParserFunctions group(WikiConfig wikiConfig)
	{
		return new BuiltInParserFunctions(wikiConfig);
	}

	// =========================================================================
	// ==
	// == {{safesubst}}
	// ==
	// =========================================================================

	public static final class ParserFunctionSafeSubst
			extends
				ParserFunctionBase
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public ParserFunctionSafeSubst()
		{
			super(PfnArgumentMode.TEMPLATE_ARGUMENTS, "safesubst");
		}

		public ParserFunctionSafeSubst(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.TEMPLATE_ARGUMENTS, "safesubst");
		}

		@Override
		public WtNode invoke(
				WtNode template,
				ExpansionFrame preprocessorFrame,
				List<? extends WtNode> args)
		{
			if (args.size() < 0)
				return null;

			// Assuming we are  NOT doing a pre save transformation

			WtNodeList name = nf().toList(((WtTemplateArgument) args.get(0)).getValue());

			WtNodeList tmplArgs = nf().list(args.subList(1, args.size()));

			WtTemplate tmpl = nf().tmpl(
					nf().name(name),
					nf().tmplArgs(tmplArgs));

			tmpl.setRtd(template.getRtd());

			return preprocessorFrame.expand(tmpl);
		}
	}

	// =========================================================================
	// ==
	// == {{msgnw:template}}
	// ==
	// =========================================================================

	/**
	 * Returns the source of a template (without expanding it) as nowiki
	 * text, like MediaWiki does for the prefix MSGNW:. Like MediaWiki a page
	 * that does not exist yields the (escaped) link to it.
	 *
	 * Like a transclusion, the inclusion of the source is subject to the
	 * template depth, the detection of template loops and the post-expand
	 * include size (see {@link ExpansionFrame#checkInclusionLimits} and
	 * {@link ExpansionFrame#limitPostExpandIncludeSize}). Unlike in MediaWiki,
	 * a redirect is not followed: The source of the redirect is returned.
	 */
	public static final class ParserFunctionMsgnw
			extends
				ParserFunctionBase
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public ParserFunctionMsgnw()
		{
			super(PfnArgumentMode.UNEXPANDED_VALUES, "msgnw");
		}

		public ParserFunctionMsgnw(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.UNEXPANDED_VALUES, "msgnw");
		}

		@Override
		public WtNode invoke(
				WtNode template,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			if (args.size() < 1)
				return template;

			WtNode nameNode = frame.expand(args.get(0));

			String name;
			try
			{
				name = tu().astToText(nameNode).trim();
			}
			catch (StringConversionException e)
			{
				fileInvalidNameWarning(frame, WarningSeverity.NORMAL, nameNode);
				return template;
			}

			WikiConfig wikiConfig = frame.getWikiConfig();

			PageTitle title;
			try
			{
				title = PageTitle.make(wikiConfig, name, wikiConfig.getTemplateNamespace());
			}
			catch (LinkTargetException e)
			{
				fileInvalidPagenameWarning(frame, WarningSeverity.NORMAL, nameNode, name);
				return template;
			}

			WtNode limited = frame.checkInclusionLimits(template, title);
			if (limited != null)
				return limited;

			FullPage page = frame.getCallback().retrieveWikitext(frame, title);

			String source = (page != null) ?
					page.getText() :
					"[[:" + title.getPrefixedText() + "]]";

			return frame.limitPostExpandIncludeSize(
					template,
					title,
					EngineRtData.set(nf().nowiki(source)));
		}
	}
}
