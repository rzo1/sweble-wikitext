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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.sweble.wikitext.engine.ParserFunctionBase;
import org.sweble.wikitext.engine.config.I18nAlias;
import org.sweble.wikitext.engine.config.ParserFunctionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtPageSwitch;

/**
 * MediaWiki's behavior switches (double underscore magic words), e.g.
 * {@code __NOTOC__}.
 * <p>
 * A behavior switch is only registered if the wiki configuration contains an
 * i18n alias for its magic word id (e.g. {@code notoc}). This way only the
 * switches known to the configured wiki are recognized, including those
 * provided by extensions (e.g. {@code __DISAMBIG__}). The aliases have to
 * include the enclosing underscores (e.g. {@code __KEIN_INHALTSVERZEICHNIS__}).
 * <p>
 * Recognized behavior switches are parsed into {@link WtPageSwitch} nodes and
 * don't produce any output. The switches set on a page can be retrieved using
 * {@link #getBehaviorSwitches(WikiConfig, WtNode)}.
 */
public class CorePfnBehaviorSwitches
		extends
			ParserFunctionGroup
{
	private static final long serialVersionUID = 1L;

	// =========================================================================

	protected CorePfnBehaviorSwitches(WikiConfig wikiConfig)
	{
		super("Core - Variables - Behavior Switches");

		Set<String> aliasIds = new HashSet<String>();
		for (I18nAlias alias : wikiConfig.getI18nAliases())
			aliasIds.add(alias.getId());

		// Table of contents
		addIfAliasExists(aliasIds, new NoTocSwitch(wikiConfig));
		addIfAliasExists(aliasIds, new ForceTocSwitch(wikiConfig));
		addIfAliasExists(aliasIds, new TocSwitch(wikiConfig));

		// Editing
		addIfAliasExists(aliasIds, new NoEditSectionSwitch(wikiConfig));
		addIfAliasExists(aliasIds, new NewSectionLinkSwitch(wikiConfig));
		addIfAliasExists(aliasIds, new NoNewSectionLinkSwitch(wikiConfig));

		// Categories
		addIfAliasExists(aliasIds, new NoGallerySwitch(wikiConfig));
		addIfAliasExists(aliasIds, new HiddenCatSwitch(wikiConfig));
		addIfAliasExists(aliasIds, new ExpectUnusedCategorySwitch(wikiConfig));

		// Language conversion
		addIfAliasExists(aliasIds, new NoContentConvertSwitch(wikiConfig));
		addIfAliasExists(aliasIds, new NoTitleConvertSwitch(wikiConfig));

		// Other
		addIfAliasExists(aliasIds, new ExpectUnusedTemplateSwitch(wikiConfig));
		addIfAliasExists(aliasIds, new IndexSwitch(wikiConfig));
		addIfAliasExists(aliasIds, new NoIndexSwitch(wikiConfig));
		addIfAliasExists(aliasIds, new StaticRedirectSwitch(wikiConfig));

		// Extensions
		addIfAliasExists(aliasIds, new DisambiguationSwitch(wikiConfig));
		addIfAliasExists(aliasIds, new NoGlobalSwitch(wikiConfig));
		addIfAliasExists(aliasIds, new ArchivedTalkSwitch(wikiConfig));
		addIfAliasExists(aliasIds, new NoTalkSwitch(wikiConfig));
	}

	public static CorePfnBehaviorSwitches group(WikiConfig wikiConfig)
	{
		return new CorePfnBehaviorSwitches(wikiConfig);
	}

	private void addIfAliasExists(Set<String> aliasIds, ParserFunctionBase pageSwitch)
	{
		if (aliasIds.contains(pageSwitch.getId()))
			addParserFunction(pageSwitch);
	}

	// =========================================================================

	/**
	 * Returns the magic word ids (e.g. {@code notoc}) of all behavior switches
	 * that are set in the given AST, in order of their first appearance.
	 */
	public static Set<String> getBehaviorSwitches(WikiConfig wikiConfig, WtNode ast)
	{
		Set<String> ids = new LinkedHashSet<String>();
		collectBehaviorSwitches(wikiConfig, ast, ids);
		return ids;
	}

	private static void collectBehaviorSwitches(
			WikiConfig wikiConfig,
			WtNode node,
			Set<String> ids)
	{
		if (node instanceof WtPageSwitch)
		{
			String name = ((WtPageSwitch) node).getName();
			ParserFunctionBase pageSwitch = wikiConfig.getPageSwitch("__" + name + "__");
			if (pageSwitch != null)
				ids.add(pageSwitch.getId());
		}

		for (WtNode child : node)
			collectBehaviorSwitches(wikiConfig, child, ids);
	}

	// =========================================================================
	// ==
	// == __NOTOC__
	// ==
	// =========================================================================

	public static final class NoTocSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NoTocSwitch()
		{
			super("notoc");
		}

		public NoTocSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "notoc");
		}
	}

	// =========================================================================
	// ==
	// == __FORCETOC__
	// ==
	// =========================================================================

	public static final class ForceTocSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public ForceTocSwitch()
		{
			super("forcetoc");
		}

		public ForceTocSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "forcetoc");
		}
	}

	// =========================================================================
	// ==
	// == __TOC__
	// ==
	// =========================================================================

	public static final class TocSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public TocSwitch()
		{
			super("toc");
		}

		public TocSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "toc");
		}
	}

	// =========================================================================
	// ==
	// == __NOEDITSECTION__
	// ==
	// =========================================================================

	public static final class NoEditSectionSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NoEditSectionSwitch()
		{
			super("noeditsection");
		}

		public NoEditSectionSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "noeditsection");
		}
	}

	// =========================================================================
	// ==
	// == __NEWSECTIONLINK__
	// ==
	// =========================================================================

	public static final class NewSectionLinkSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NewSectionLinkSwitch()
		{
			super("newsectionlink");
		}

		public NewSectionLinkSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "newsectionlink");
		}
	}

	// =========================================================================
	// ==
	// == __NONEWSECTIONLINK__
	// ==
	// =========================================================================

	public static final class NoNewSectionLinkSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NoNewSectionLinkSwitch()
		{
			super("nonewsectionlink");
		}

		public NoNewSectionLinkSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "nonewsectionlink");
		}
	}

	// =========================================================================
	// ==
	// == __NOGALLERY__
	// ==
	// =========================================================================

	public static final class NoGallerySwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NoGallerySwitch()
		{
			super("nogallery");
		}

		public NoGallerySwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "nogallery");
		}
	}

	// =========================================================================
	// ==
	// == __HIDDENCAT__
	// ==
	// =========================================================================

	public static final class HiddenCatSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public HiddenCatSwitch()
		{
			super("hiddencat");
		}

		public HiddenCatSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "hiddencat");
		}
	}

	// =========================================================================
	// ==
	// == __EXPECTUNUSEDCATEGORY__
	// ==
	// =========================================================================

	public static final class ExpectUnusedCategorySwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public ExpectUnusedCategorySwitch()
		{
			super("expectunusedcategory");
		}

		public ExpectUnusedCategorySwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "expectunusedcategory");
		}
	}

	// =========================================================================
	// ==
	// == __NOCONTENTCONVERT__, __NOCC__
	// ==
	// =========================================================================

	public static final class NoContentConvertSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NoContentConvertSwitch()
		{
			super("nocontentconvert");
		}

		public NoContentConvertSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "nocontentconvert");
		}
	}

	// =========================================================================
	// ==
	// == __NOTITLECONVERT__, __NOTC__
	// ==
	// =========================================================================

	public static final class NoTitleConvertSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NoTitleConvertSwitch()
		{
			super("notitleconvert");
		}

		public NoTitleConvertSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "notitleconvert");
		}
	}

	// =========================================================================
	// ==
	// == __EXPECTUNUSEDTEMPLATE__
	// ==
	// =========================================================================

	public static final class ExpectUnusedTemplateSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public ExpectUnusedTemplateSwitch()
		{
			super("expectunusedtemplate");
		}

		public ExpectUnusedTemplateSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "expectunusedtemplate");
		}
	}

	// =========================================================================
	// ==
	// == __INDEX__
	// ==
	// =========================================================================

	public static final class IndexSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public IndexSwitch()
		{
			super("index");
		}

		public IndexSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "index");
		}
	}

	// =========================================================================
	// ==
	// == __NOINDEX__
	// ==
	// =========================================================================

	public static final class NoIndexSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NoIndexSwitch()
		{
			super("noindex");
		}

		public NoIndexSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "noindex");
		}
	}

	// =========================================================================
	// ==
	// == __STATICREDIRECT__
	// ==
	// =========================================================================

	public static final class StaticRedirectSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public StaticRedirectSwitch()
		{
			super("staticredirect");
		}

		public StaticRedirectSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "staticredirect");
		}
	}

	// =========================================================================
	// ==
	// == __DISAMBIG__ (Disambiguator)
	// ==
	// =========================================================================

	public static final class DisambiguationSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public DisambiguationSwitch()
		{
			super("disambiguation");
		}

		public DisambiguationSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "disambiguation");
		}
	}

	// =========================================================================
	// ==
	// == __NOGLOBAL__ (GlobalUserPage)
	// ==
	// =========================================================================

	public static final class NoGlobalSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NoGlobalSwitch()
		{
			super("noglobal");
		}

		public NoGlobalSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "noglobal");
		}
	}

	// =========================================================================
	// ==
	// == __ARCHIVEDTALK__ (DiscussionTools)
	// ==
	// =========================================================================

	public static final class ArchivedTalkSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public ArchivedTalkSwitch()
		{
			super("archivedtalk");
		}

		public ArchivedTalkSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "archivedtalk");
		}
	}

	// =========================================================================
	// ==
	// == __NOTALK__ (DiscussionTools)
	// ==
	// =========================================================================

	public static final class NoTalkSwitch
			extends
				CorePfnBehaviorSwitch
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public NoTalkSwitch()
		{
			super("notalk");
		}

		public NoTalkSwitch(WikiConfig wikiConfig)
		{
			super(wikiConfig, "notalk");
		}
	}
}
