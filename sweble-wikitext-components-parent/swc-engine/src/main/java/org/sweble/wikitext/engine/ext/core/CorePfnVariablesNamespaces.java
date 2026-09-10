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
import org.sweble.wikitext.engine.PfnArgumentMode;
import org.sweble.wikitext.engine.config.Namespace;
import org.sweble.wikitext.engine.config.ParserFunctionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;

/**
 * The namespace variables. Like in MediaWiki, all of them refer to the
 * current page or, if called with an argument (e.g.
 * {@code {{TALKSPACE:User:Foo}}}), to the given page.
 */
public class CorePfnVariablesNamespaces
		extends
			ParserFunctionGroup
{
	private static final long serialVersionUID = 1L;

	// =========================================================================

	protected CorePfnVariablesNamespaces(WikiConfig wikiConfig)
	{
		super("Core - Variables - Namespaces");
		addParserFunction(new NamespacePfn(wikiConfig));
		addParserFunction(new NamespaceePfn(wikiConfig));
		addParserFunction(new NamespacenumberPfn(wikiConfig));
		addParserFunction(new TalkspacePfn(wikiConfig));
		addParserFunction(new TalkspaceePfn(wikiConfig));
		addParserFunction(new SubjectspacePfn(wikiConfig));
		addParserFunction(new SubjectspaceePfn(wikiConfig));
	}

	public static CorePfnVariablesNamespaces group(WikiConfig wikiConfig)
	{
		return new CorePfnVariablesNamespaces(wikiConfig);
	}

	// =========================================================================

	/**
	 * Like MediaWiki's {@code Title::getTalkNsText()}: Pages in namespaces
	 * with a negative index (Special, Media) cannot have a talk page.
	 *
	 * @return The name of the talk namespace or an empty string if there is
	 *         none.
	 */
	private static String getTalkNamespaceName(WikiConfig wikiConfig, Namespace namespace)
	{
		if (namespace.getId() < 0)
			return "";

		Namespace talkNs = wikiConfig.getTalkNamespaceFor(namespace);
		return (talkNs != null) ? talkNs.getName() : "";
	}

	/**
	 * Like MediaWiki's {@code Title::getSubjectNsText()}: Namespaces with a
	 * negative index (Special, Media) are their own subject namespace.
	 *
	 * @return The name of the subject namespace or an empty string if there
	 *         is none.
	 */
	private static String getSubjectNamespaceName(WikiConfig wikiConfig, Namespace namespace)
	{
		if (namespace.getId() < 0)
			return namespace.getName();

		Namespace subjectNs = wikiConfig.getSubjectNamespaceFor(namespace);
		return (subjectNs != null) ? subjectNs.getName() : "";
	}

	// =========================================================================
	// ==
	// == {{NAMESPACE}}
	// ==
	// =========================================================================

	public static final class NamespacePfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NamespacePfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "namespace");
		}

		public NamespacePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "namespace");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> argsValues)
		{
			PageTitle title = getTitleArgument(var, frame, argsValues);
			if (title == null)
				return var;

			return nf().text(title.getNamespace().getName());
		}
	}

	// =========================================================================
	// ==
	// == {{NAMESPACEE}}
	// ==
	// =========================================================================

	public static final class NamespaceePfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NamespaceePfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "namespacee");
		}

		public NamespaceePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "namespacee");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> argsValues)
		{
			PageTitle title = getTitleArgument(var, frame, argsValues);
			if (title == null)
				return var;

			return nf().text(UrlEncoding.WIKI.encode(title.getNamespace().getName()));
		}
	}

	// =========================================================================
	// ==
	// == {{NAMESPACENUMBER}}
	// ==
	// =========================================================================

	public static final class NamespacenumberPfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NamespacenumberPfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "namespacenumber");
		}

		public NamespacenumberPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "namespacenumber");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> argsValues)
		{
			PageTitle title = getTitleArgument(var, frame, argsValues);
			if (title == null)
				return var;

			return nf().text(String.valueOf(title.getNamespace().getId()));
		}
	}

	// =========================================================================
	// ==
	// == {{SUBJECTSPACE}}, {{ARTICLESPACE}}
	// ==
	// =========================================================================

	public static final class SubjectspacePfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public SubjectspacePfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "subjectspace");
		}

		public SubjectspacePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "subjectspace");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> argsValues)
		{
			PageTitle title = getTitleArgument(var, frame, argsValues);
			if (title == null)
				return var;

			return nf().text(getSubjectNamespaceName(
					frame.getWikiConfig(),
					title.getNamespace()));
		}
	}

	// =========================================================================
	// ==
	// == {{SUBJECTSPACEE}}, {{ARTICLESPACEE}}
	// ==
	// =========================================================================

	public static final class SubjectspaceePfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public SubjectspaceePfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "subjectspacee");
		}

		public SubjectspaceePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "subjectspacee");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> argsValues)
		{
			PageTitle title = getTitleArgument(var, frame, argsValues);
			if (title == null)
				return var;

			return nf().text(UrlEncoding.WIKI.encode(getSubjectNamespaceName(
					frame.getWikiConfig(),
					title.getNamespace())));
		}
	}

	// =========================================================================
	// ==
	// == {{TALKSPACE}}
	// ==
	// =========================================================================

	public static final class TalkspacePfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public TalkspacePfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "talkspace");
		}

		public TalkspacePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "talkspace");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> argsValues)
		{
			PageTitle title = getTitleArgument(var, frame, argsValues);
			if (title == null)
				return var;

			return nf().text(getTalkNamespaceName(
					frame.getWikiConfig(),
					title.getNamespace()));
		}
	}

	// =========================================================================
	// ==
	// == {{TALKSPACEE}}
	// ==
	// =========================================================================

	public static final class TalkspaceePfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public TalkspaceePfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "talkspacee");
		}

		public TalkspaceePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "talkspacee");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> argsValues)
		{
			PageTitle title = getTitleArgument(var, frame, argsValues);
			if (title == null)
				return var;

			return nf().text(UrlEncoding.WIKI.encode(getTalkNamespaceName(
					frame.getWikiConfig(),
					title.getNamespace())));
		}
	}
}
