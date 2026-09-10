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

public class CorePfnVariablesPageNames
		extends
			ParserFunctionGroup
{
	private static final long serialVersionUID = 1L;

	// =========================================================================

	protected CorePfnVariablesPageNames(WikiConfig wikiConfig)
	{
		super("Core - Variables - Page names");
		addParserFunction(new FullPagenamePfn(wikiConfig));
		addParserFunction(new FullPagenameePfn(wikiConfig));
		addParserFunction(new PagenamePfn(wikiConfig));
		addParserFunction(new PagenameePfn(wikiConfig));
		addParserFunction(new SubPagenamePfn(wikiConfig));
		addParserFunction(new SubPagenameePfn(wikiConfig));
		addParserFunction(new RootPagenamePfn(wikiConfig));
		addParserFunction(new RootPagenameePfn(wikiConfig));
		addParserFunction(new BasePagenamePfn(wikiConfig));
		addParserFunction(new BasePagenameePfn(wikiConfig));
		addParserFunction(new SubjectPagenamePfn(wikiConfig));
		addParserFunction(new SubjectPagenameePfn(wikiConfig));
		addParserFunction(new TalkPagenamePfn(wikiConfig));
		addParserFunction(new TalkPagenameePfn(wikiConfig));
	}

	public static CorePfnVariablesPageNames group(WikiConfig wikiConfig)
	{
		return new CorePfnVariablesPageNames(wikiConfig);
	}

	// =========================================================================

	/**
	 * Base class of the page name variables.
	 *
	 * A page name variable refers to the page that is being rendered (e.g.
	 * {@code {{PAGENAME}}}) or to the page given as argument (e.g.
	 * {@code {{PAGENAME:Foo}}}).
	 *
	 * Unlike MediaWiki (wfEscapeWikiText()) the name is not escaped with
	 * character references: Link targets, link texts and category names are
	 * not entity-decoded, so {@code [[{{PAGENAME}}]]} would show
	 * {@code A&#39;s} instead of {@code A's}. The HTML renderer escapes the
	 * resulting text instead.
	 */
	public static abstract class PageNameVariablePfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		protected PageNameVariablePfn(String name)
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, name);
		}

		protected PageNameVariablePfn(WikiConfig wikiConfig, String name)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, name);
		}

		@Override
		public final WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> argsValues)
		{
			PageTitle title = getTitleArgument(var, frame, argsValues);
			if (title == null)
				return var;

			return nf().text(getPageName(frame.getWikiConfig(), title));
		}

		/**
		 * Returns the name this variable produces for the given page.
		 */
		protected abstract String getPageName(WikiConfig config, PageTitle title);
	}

	// =========================================================================

	/**
	 * Like MediaWiki's wfUrlencode().
	 */
	private static String urlEncode(String text)
	{
		return UrlEncoding.WIKI.encode(text);
	}

	/**
	 * Returns the talk page of the given page or {@code null} if the page
	 * cannot have a talk page.
	 */
	private static PageTitle getTalkPage(WikiConfig config, PageTitle title)
	{
		Namespace ns = title.getNamespace();

		// Special pages, media and pages on other wikis have no talk page.
		if (title.isInterwiki() || ns.getId() < 0)
			return null;

		Namespace talkNs = config.getTalkNamespaceFor(ns);
		if (talkNs == null)
			return null;

		return talkNs.equals(ns) ? title : title.newWithNamespace(talkNs);
	}

	/**
	 * Returns the subject page of the given page.
	 */
	private static PageTitle getSubjectPage(WikiConfig config, PageTitle title)
	{
		Namespace ns = title.getNamespace();

		// Special pages and media are their own subject pages.
		if (ns.getId() < 0)
			return title;

		Namespace subjectNs = config.getSubjectNamespaceFor(ns);
		if (subjectNs == null || subjectNs.equals(ns))
			return title;

		return title.newWithNamespace(subjectNs);
	}

	// =========================================================================
	// ==
	// == {{FULLPAGENAME}}
	// ==
	// =========================================================================

	public static final class FullPagenamePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public FullPagenamePfn()
		{
			super("fullpagename");
		}

		public FullPagenamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "fullpagename");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			return title.getPrefixedText();
		}
	}

	// =========================================================================
	// ==
	// == {{FULLPAGENAMEE}}
	// ==
	// =========================================================================

	public static final class FullPagenameePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public FullPagenameePfn()
		{
			super("fullpagenamee");
		}

		public FullPagenameePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "fullpagenamee");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			return urlEncode(title.getPrefixedText());
		}
	}

	// =========================================================================
	// ==
	// == {{PAGENAME}}
	// ==
	// =========================================================================

	public static final class PagenamePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public PagenamePfn()
		{
			super("pagename");
		}

		public PagenamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "pagename");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			return title.getDenormalizedTitle();
		}
	}

	// =========================================================================
	// ==
	// == {{PAGENAMEE}}
	// ==
	// =========================================================================

	public static final class PagenameePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public PagenameePfn()
		{
			super("pagenamee");
		}

		public PagenameePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "pagenamee");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			return urlEncode(title.getTitle());
		}
	}

	// =========================================================================
	// ==
	// == {{SUBPAGENAME}}
	// ==
	// =========================================================================

	public static final class SubPagenamePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public SubPagenamePfn()
		{
			super("subpagename");
		}

		public SubPagenamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "subpagename");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			return title.getSubpageTitle().getDenormalizedTitle();
		}
	}

	// =========================================================================
	// ==
	// == {{SUBPAGENAMEE}}
	// ==
	// =========================================================================

	public static final class SubPagenameePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public SubPagenameePfn()
		{
			super("subpagenamee");
		}

		public SubPagenameePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "subpagenamee");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			return urlEncode(title.getSubpageTitle().getTitle());
		}
	}

	// =========================================================================
	// ==
	// == {{ROOTPAGENAME}}
	// ==
	// =========================================================================

	public static final class RootPagenamePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public RootPagenamePfn()
		{
			super("rootpagename");
		}

		public RootPagenamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "rootpagename");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			return title.getRootTitle().getDenormalizedTitle();
		}
	}

	// =========================================================================
	// ==
	// == {{ROOTPAGENAMEE}}
	// ==
	// =========================================================================

	public static final class RootPagenameePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public RootPagenameePfn()
		{
			super("rootpagenamee");
		}

		public RootPagenameePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "rootpagenamee");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			return urlEncode(title.getRootTitle().getTitle());
		}
	}

	// =========================================================================
	// ==
	// == {{BASEPAGENAME}}
	// ==
	// =========================================================================

	public static final class BasePagenamePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public BasePagenamePfn()
		{
			super("basepagename");
		}

		public BasePagenamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "basepagename");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			return title.getBaseTitle().getDenormalizedTitle();
		}
	}

	// =========================================================================
	// ==
	// == {{BASEPAGENAMEE}}
	// ==
	// =========================================================================

	public static final class BasePagenameePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public BasePagenameePfn()
		{
			super("basepagenamee");
		}

		public BasePagenameePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "basepagenamee");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			return urlEncode(title.getBaseTitle().getTitle());
		}
	}

	// =========================================================================
	// ==
	// == {{SUBJECTPAGENAME}}, {{ARTICLEPAGENAME}}
	// ==
	// =========================================================================

	public static final class SubjectPagenamePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public SubjectPagenamePfn()
		{
			super("subjectpagename");
		}

		public SubjectPagenamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "subjectpagename");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			return getSubjectPage(config, title).getPrefixedText();
		}
	}

	// =========================================================================
	// ==
	// == {{SUBJECTPAGENAMEE}}, {{ARTICLEPAGENAMEE}}
	// ==
	// =========================================================================

	public static final class SubjectPagenameePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public SubjectPagenameePfn()
		{
			super("subjectpagenamee");
		}

		public SubjectPagenameePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "subjectpagenamee");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			return urlEncode(getSubjectPage(config, title).getPrefixedText());
		}
	}

	// =========================================================================
	// ==
	// == {{TALKPAGENAME}}
	// ==
	// =========================================================================

	public static final class TalkPagenamePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public TalkPagenamePfn()
		{
			super("talkpagename");
		}

		public TalkPagenamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "talkpagename");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			PageTitle talkPage = getTalkPage(config, title);
			return (talkPage != null) ? talkPage.getPrefixedText() : "";
		}
	}

	// =========================================================================
	// ==
	// == {{TALKPAGENAMEE}}
	// ==
	// =========================================================================

	public static final class TalkPagenameePfn
			extends
				PageNameVariablePfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public TalkPagenameePfn()
		{
			super("talkpagenamee");
		}

		public TalkPagenameePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "talkpagenamee");
		}

		@Override
		protected String getPageName(WikiConfig config, PageTitle title)
		{
			PageTitle talkPage = getTalkPage(config, title);
			return (talkPage != null) ? urlEncode(talkPage.getPrefixedText()) : "";
		}
	}
}
