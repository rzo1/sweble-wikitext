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
import org.sweble.wikitext.engine.config.Namespace;
import org.sweble.wikitext.engine.config.ParserFunctionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.utils.StringConversionException;

public class CorePfnFunctionsNamespaces
		extends
			ParserFunctionGroup
{
	private static final long serialVersionUID = 1L;

	// =========================================================================

	protected CorePfnFunctionsNamespaces(WikiConfig wikiConfig)
	{
		super("Core - Parser Functions - Namespaces");
		addParserFunction(new NsPfn(wikiConfig));
		addParserFunction(new NsePfn(wikiConfig));
	}

	public static CorePfnFunctionsNamespaces group(WikiConfig wikiConfig)
	{
		return new CorePfnFunctionsNamespaces(wikiConfig);
	}

	// =========================================================================

	/**
	 * Looks up a namespace by name, alias or index. Like in MediaWiki,
	 * underscores in names are treated like spaces (e.g. "User_talk").
	 *
	 * @return The namespace or {@code null} if the given index is unknown.
	 * @throws NumberFormatException
	 *             Thrown if the argument is neither a known name nor an index.
	 */
	private static Namespace getNamespace(WikiConfig wikiConfig, String arg)
	{
		Namespace namespace = wikiConfig.getNamespace(arg.replace('_', ' '));
		if (namespace == null)
			namespace = wikiConfig.getNamespace(Integer.parseInt(arg));
		return namespace;
	}

	// =========================================================================
	// ==
	// == {{ns:index}}
	// == {{ns:canonical name}}
	// == {{ns:local alias}}
	// ==
	// =========================================================================

	public static final class NsPfn
			extends
				CorePfnFunction
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NsPfn()
		{
			super("ns");
		}

		public NsPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "ns");
		}

		@Override
		public WtNode invoke(
				WtTemplate wtTemplate,
				ExpansionFrame preprocessorFrame,
				List<? extends WtNode> args)
		{
			if (args.size() < 0)
				return null;

			WtNode arg0 = preprocessorFrame.expand(args.get(0));

			String arg;
			try
			{
				arg = tu().astToText(arg0).trim();
			}
			catch (StringConversionException e1)
			{
				fileInvalidNameWarning(preprocessorFrame, WarningSeverity.NORMAL, arg0);
				return null;
			}

			Namespace namespace;
			try
			{
				namespace = getNamespace(preprocessorFrame.getWikiConfig(), arg);
			}
			catch (NumberFormatException e)
			{
				fileIllegalArgumentsWarning(
						preprocessorFrame,
						WarningSeverity.NORMAL,
						wtTemplate,
						"Unknown namespace `" + arg + "'");
				return null;
			}

			String result = "";
			if (namespace != null)
				result = namespace.getName();

			return nf().text(result);
		}
	}

	// =========================================================================
	// ==
	// == {{nse:index}}
	// == {{nse:canonical name}}
	// == {{nse:local alias}}
	// ==
	// =========================================================================

	/**
	 * Like {@code ns} but the name of the namespace is URL-encoded like
	 * MediaWiki's wfUrlencode() after spaces were replaced by underscores.
	 */
	public static final class NsePfn
			extends
				CorePfnFunction
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NsePfn()
		{
			super("nse");
		}

		public NsePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "nse");
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

			String arg;
			try
			{
				arg = tu().astToText(arg0).trim();
			}
			catch (StringConversionException e)
			{
				fileInvalidNameWarning(frame, WarningSeverity.NORMAL, arg0);
				return pfn;
			}

			Namespace namespace;
			try
			{
				namespace = getNamespace(frame.getWikiConfig(), arg);
			}
			catch (NumberFormatException e)
			{
				fileIllegalArgumentsWarning(
						frame,
						WarningSeverity.NORMAL,
						pfn,
						"Unknown namespace `" + arg + "'");
				return pfn;
			}

			String result = "";
			if (namespace != null)
				result = UrlEncoding.WIKI.encode(namespace.getName().replace(' ', '_'));

			return nf().text(result);
		}
	}
}
