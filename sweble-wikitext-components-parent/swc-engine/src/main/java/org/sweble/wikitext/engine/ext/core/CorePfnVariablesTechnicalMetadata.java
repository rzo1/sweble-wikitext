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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.PfnArgumentMode;
import org.sweble.wikitext.engine.config.ParserFunctionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;

public class CorePfnVariablesTechnicalMetadata
		extends
			ParserFunctionGroup
{
	private static final long serialVersionUID = 1L;

	// =========================================================================

	protected CorePfnVariablesTechnicalMetadata(WikiConfig wikiConfig)
	{
		super("Core - Variables - Technical Metadata");
		addParserFunction(new SitenamePfn(wikiConfig));
		addParserFunction(new ServerPfn(wikiConfig));
		addParserFunction(new ServernamePfn(wikiConfig));
		addParserFunction(new ScriptpathPfn(wikiConfig));
		addParserFunction(new ContentLanguagePfn(wikiConfig));
		addParserFunction(new ProtectionLevelPfn(wikiConfig));
		addParserFunction(new DisplaytitlePfn(wikiConfig));
		addParserFunction(new DefaultsortPfn(wikiConfig));
	}

	public static CorePfnVariablesTechnicalMetadata group(WikiConfig wikiConfig)
	{
		return new CorePfnVariablesTechnicalMetadata(wikiConfig);
	}

	/**
	 * Returns the URL of the wiki's script (MediaWiki's $wgServer followed by
	 * $wgScript) or {@code null} if the configured URL is not valid.
	 */
	private static URL getWikiUrl(ExpansionFrame frame)
	{
		try
		{
			return new URL(frame.getWikiConfig().getWikiUrl());
		}
		catch (MalformedURLException e)
		{
			return null;
		}
	}

	// =========================================================================
	// ==
	// == Site
	// == ----
	// == TODO: {{SITENAME}}
	// ==
	// =========================================================================

	public static final class SitenamePfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public SitenamePfn()
		{
			super("sitename");
		}

		public SitenamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "sitename");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			return nf().text(frame.getWikiConfig().getSiteName());
		}
	}

	// =========================================================================
	// ==
	// == {{SERVER}}
	// ==
	// =========================================================================

	/**
	 * Scheme and host of the wiki's URL (MediaWiki's $wgServer).
	 */
	public static final class ServerPfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public ServerPfn()
		{
			super("server");
		}

		public ServerPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "server");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			URL url = getWikiUrl(frame);
			if (url == null)
				return var;

			return nf().text(url.getProtocol() + "://" + url.getAuthority());
		}
	}

	// =========================================================================
	// ==
	// == {{SERVERNAME}}
	// ==
	// =========================================================================

	/**
	 * The host of the wiki's URL (MediaWiki's $wgServerName).
	 */
	public static final class ServernamePfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public ServernamePfn()
		{
			super("servername");
		}

		public ServernamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "servername");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			URL url = getWikiUrl(frame);
			if (url == null)
				return var;

			return nf().text(url.getHost());
		}
	}

	// =========================================================================
	// ==
	// == TODO: {{DIRMARK}}, {{DIRECTIONMARK}}
	// ==
	// =========================================================================

	// =========================================================================
	// ==
	// == {{SCRIPTPATH}}
	// ==
	// =========================================================================

	/**
	 * The directory of the wiki's script (MediaWiki's $wgScriptPath), e.g.
	 * "/w" for "https://en.wikipedia.org/w/index.php" or "" if the script is
	 * in the root directory.
	 */
	public static final class ScriptpathPfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public ScriptpathPfn()
		{
			super("scriptpath");
		}

		public ScriptpathPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "scriptpath");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			URL url = getWikiUrl(frame);
			if (url == null)
				return var;

			String path = url.getPath();
			if (path.endsWith(".php"))
				path = path.substring(0, path.lastIndexOf('/'));
			while (path.endsWith("/"))
				path = path.substring(0, path.length() - 1);

			return nf().text(path);
		}
	}

	// =========================================================================
	// ==
	// == TODO: {{STYLEPATH}}
	// == TODO: {{CURRENTVERSION}}
	// ==
	// =========================================================================

	// =========================================================================
	// ==
	// == {{CONTENTLANGUAGE}}, {{CONTENTLANG}}
	// ==
	// =========================================================================

	public static final class ContentLanguagePfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public ContentLanguagePfn()
		{
			super("contentlanguage");
		}

		public ContentLanguagePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "contentlanguage");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			return nf().text(frame.getWikiConfig().getContentLanguage());
		}
	}

	// =========================================================================
	// ==
	// == Latest revision to current page
	// == -------------------------------
	// == TODO: {{REVISIONID}}
	// == TODO: {{REVISIONDAY}}
	// == TODO: {{REVISIONDAY2}}
	// == TODO: {{REVISIONMONTH}}
	// == TODO: {{REVISIONMONTH1}}
	// == TODO: {{REVISIONYEAR}}
	// == TODO: {{REVISIONTIMESTAMP}}
	// == TODO: {{REVISIONUSER}}
	// == TODO: {{PAGESIZE:page name}}, {{PAGESIZE:page name|R}}
	// ==
	// =========================================================================

	// =========================================================================
	// ==
	// == {{PROTECTIONLEVEL:action}}
	// ==
	// =========================================================================

	public static final class ProtectionLevelPfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public ProtectionLevelPfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "protectionlevel");
		}

		public ProtectionLevelPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "protectionlevel");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			// FIXME: Proper implementation:
			return nf().list();
		}
	}

	// =========================================================================
	// ==
	// == Affects page content
	// == --------------------
	// == {{DISPLAYTITLE:title}}
	// ==
	// =========================================================================

	/**
	 * Like in MediaWiki the parser function itself renders nothing.
	 *
	 * TODO: Record the display title of the page.
	 */
	public static final class DisplaytitlePfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public DisplaytitlePfn()
		{
			super("displaytitle");
		}

		public DisplaytitlePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "displaytitle");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			return nf().list();
		}
	}

	// =========================================================================
	// ==
	// == TODO: {{DEFAULTSORT:sortkey}}, {{DEFAULTSORTKEY:sortkey}}, 
	// ==       {{DEFAULTCATEGORYSORT:sortkey}}, {{DEFAULTSORT:sortkey|noerror}}, 
	// ==       {{DEFAULTSORT:sortkey|noreplace}}
	// ==
	// =========================================================================

	public static final class DefaultsortPfn
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public DefaultsortPfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "defaultsort");
		}

		public DefaultsortPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "defaultsort");
		}

		@Override
		public WtNode invoke(
				WtTemplate var,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			// FIXME: Proper implementation:
			return nf().list();
		}
	}
}
