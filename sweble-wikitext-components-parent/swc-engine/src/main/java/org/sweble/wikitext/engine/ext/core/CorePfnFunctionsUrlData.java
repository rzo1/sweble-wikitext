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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.IllegalArgumentsWarning;
import org.sweble.wikitext.engine.InvalidNameWarning;
import org.sweble.wikitext.engine.InvalidPagenameWarning;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.PfnArgumentMode;
import org.sweble.wikitext.engine.config.Namespace;
import org.sweble.wikitext.engine.config.ParserFunctionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngineRtData;
import org.sweble.wikitext.engine.output.HtmlSanitizer;
import org.sweble.wikitext.engine.utils.AnchorEncoder;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.engine.utils.UrlType;
import org.sweble.wikitext.parser.ParserConfig;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.parser.LinkTargetException;
import org.sweble.wikitext.parser.utils.StringConversionException;

public class CorePfnFunctionsUrlData
		extends
			ParserFunctionGroup
{
	private static final long serialVersionUID = 1L;

	// =========================================================================

	protected CorePfnFunctionsUrlData(WikiConfig wikiConfig)
	{
		super("Core - Parser Functions - URL data");
		addParserFunction(new LocalurlPfn(wikiConfig));
		addParserFunction(new LocalurlePfn(wikiConfig));
		addParserFunction(new FullurlPfn(wikiConfig));
		addParserFunction(new FullurlePfn(wikiConfig));
		addParserFunction(new CanonicalurlPfn(wikiConfig));
		addParserFunction(new CanonicalurlePfn(wikiConfig));
		addParserFunction(new FilepathPfn(wikiConfig));
		addParserFunction(new UrlencodePfn(wikiConfig));
		addParserFunction(new AnchorencodePfn(wikiConfig));
	}

	public static CorePfnFunctionsUrlData group(WikiConfig wikiConfig)
	{
		return new CorePfnFunctionsUrlData(wikiConfig);
	}

	// =========================================================================

	/**
	 * Base class of the parser functions which return the URL of a page, like
	 * MediaWiki's CoreParserFunctions::urlFunction().
	 *
	 * The full URL is made from the article path of the wiki or, if a query is
	 * given, from the URL of the wiki's script ("...?title=page&amp;query").
	 * The local URL is the full URL without scheme and host. The wiki has no
	 * separate canonical server: The canonical URL is the full URL.
	 */
	public static abstract class UrlPfn
			extends
				CorePfnFunction
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		protected UrlPfn(String name)
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, name);
		}

		protected UrlPfn(WikiConfig wikiConfig, String name)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, name);
		}

		/**
		 * The kind of URL this parser function returns.
		 */
		protected abstract UrlType getUrlType();

		/**
		 * Returns true if the URL is HTML-escaped (the "e" variants).
		 */
		protected abstract boolean isEscaped();

		@Override
		public WtNode invoke(
				WtTemplate pfn,
				ExpansionFrame frame,
				List<? extends WtNode> argsValues)
		{
			if (argsValues.size() < 1)
			{
				frame.fileWarning(
						new IllegalArgumentsWarning(
								WarningSeverity.NORMAL,
								getClass(),
								"Parser function was called with too few arguments!",
								pfn));
				return pfn;
			}
			else if (argsValues.size() > 2)
			{
				frame.fileWarning(
						new IllegalArgumentsWarning(
								WarningSeverity.NONE,
								getClass(),
								"Parser function was called with too many arguments!",
								pfn));
			}

			WtNode titleNode = argsValues.get(0);

			String titleStr;
			try
			{
				titleStr = tu().astToText(titleNode);
			}
			catch (StringConversionException e1)
			{
				frame.fileWarning(
						new InvalidNameWarning(
								WarningSeverity.NORMAL,
								getClass(),
								titleNode));
				return pfn;
			}

			PageTitle title;
			try
			{
				title = PageTitle.make(frame.getWikiConfig(), titleStr);
			}
			catch (LinkTargetException e)
			{
				try
				{
					titleStr = URLDecoder.decode(titleStr, "UTF-8");
					title = PageTitle.make(frame.getWikiConfig(), titleStr);
				}
				catch (LinkTargetException e2)
				{
					frame.fileWarning(
							new InvalidPagenameWarning(
									WarningSeverity.NORMAL,
									getClass(),
									titleNode,
									titleStr));
					return pfn;
				}
				catch (UnsupportedEncodingException e2)
				{
					frame.fileWarning(
							new InvalidNameWarning(
									WarningSeverity.NORMAL,
									getClass(),
									titleNode));
					return pfn;
				}
			}

			String queryStr = null;
			if (argsValues.size() >= 2)
			{
				WtNode queryNode = argsValues.get(1);

				try
				{
					queryStr = tu().astToText(queryNode);
				}
				catch (StringConversionException e)
				{
					frame.fileWarning(
							new InvalidNameWarning(
									WarningSeverity.NORMAL,
									getClass(),
									queryNode));
				}
			}

			Namespace ns = title.getNamespace();
			if (ns.isMediaNs())
				title = title.newWithNamespace(frame.getWikiConfig().getFileNamespace());

			UrlType urlType = getUrlType();
			boolean withFragment = (urlType != UrlType.LOCAL);

			URL titleUrl;
			try
			{
				titleUrl = makeUrl(frame.getWikiConfig(), title, queryStr, withFragment);
			}
			catch (MalformedURLException e)
			{
				// Try without query string and fragment ...
				titleUrl = title.getUrl();

				frame.fileWarning(
						new InvalidNameWarning(
								WarningSeverity.NORMAL,
								getClass(),
								pfn));
			}

			URL url = frame.getUrlService().convertUrl(urlType, titleUrl);

			String result;
			if (urlType == UrlType.LOCAL && !title.isInterwiki())
				result = url.getFile();
			else
				result = url.toExternalForm();

			return nf().text(isEscaped() ? escapeHtml(result) : result);
		}

		/**
		 * Like MediaWiki's Title::getFullURL(): Without query the article path
		 * is used, with query the URL of the wiki's script.
		 */
		private static URL makeUrl(
				WikiConfig wikiConfig,
				PageTitle title,
				String query,
				boolean withFragment) throws MalformedURLException
		{
			boolean hasQuery = (query != null && !query.isEmpty());

			URL url;
			if (title.isInterwiki() || !hasQuery)
			{
				url = title.getUrl(hasQuery ? query : null);
			}
			else
			{
				url = new URL(wikiConfig.getWikiUrl() +
						"?title=" + UrlEncoding.WIKI.encode(title.getNormalizedFullTitle()) +
						"&" + query);
			}

			String fragment = title.getFragment();
			if (withFragment && fragment != null && !fragment.isEmpty())
				url = new URL(url.toExternalForm() + "#" + AnchorEncoder.encodeLinkFragment(fragment));

			return url;
		}

		/**
		 * Like PHP's htmlspecialchars() with ENT_COMPAT.
		 */
		private static String escapeHtml(String text)
		{
			return text
					.replace("&", "&amp;")
					.replace("<", "&lt;")
					.replace(">", "&gt;")
					.replace("\"", "&quot;");
		}
	}

	// =========================================================================
	// ==
	// == {{localurl:page name}}
	// == {{localurl:page name|query_string}}
	// ==
	// =========================================================================

	public static final class LocalurlPfn
			extends
				UrlPfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalurlPfn()
		{
			super("localurl");
		}

		public LocalurlPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localurl");
		}

		@Override
		protected UrlType getUrlType()
		{
			return UrlType.LOCAL;
		}

		@Override
		protected boolean isEscaped()
		{
			return false;
		}
	}

	// =========================================================================
	// ==
	// == {{localurle:page name}}
	// == {{localurle:page name|query_string}}
	// ==
	// =========================================================================

	public static final class LocalurlePfn
			extends
				UrlPfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalurlePfn()
		{
			super("localurle");
		}

		public LocalurlePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localurle");
		}

		@Override
		protected UrlType getUrlType()
		{
			return UrlType.LOCAL;
		}

		@Override
		protected boolean isEscaped()
		{
			return true;
		}
	}

	// =========================================================================
	// ==
	// == {{fullurl:page name}}
	// == {{fullurl:page name|query_string}}
	// == {{fullurl:interwiki:remote page name|query_string}}
	// ==
	// =========================================================================

	public static final class FullurlPfn
			extends
				UrlPfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public FullurlPfn()
		{
			super("fullurl");
		}

		public FullurlPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "fullurl");
		}

		@Override
		protected UrlType getUrlType()
		{
			return UrlType.FULL;
		}

		@Override
		protected boolean isEscaped()
		{
			return false;
		}
	}

	// =========================================================================
	// ==
	// == {{fullurle:page name}}
	// == {{fullurle:page name|query_string}}
	// ==
	// =========================================================================

	public static final class FullurlePfn
			extends
				UrlPfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public FullurlePfn()
		{
			super("fullurle");
		}

		public FullurlePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "fullurle");
		}

		@Override
		protected UrlType getUrlType()
		{
			return UrlType.FULL;
		}

		@Override
		protected boolean isEscaped()
		{
			return true;
		}
	}

	// =========================================================================
	// ==
	// == {{canonicalurl:page name}}
	// == {{canonicalurl:page name|query_string}}
	// == {{canonicalurl:interwiki:remote page name|query_string}}
	// ==
	// =========================================================================

	public static final class CanonicalurlPfn
			extends
				UrlPfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CanonicalurlPfn()
		{
			super("canonicalurl");
		}

		public CanonicalurlPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "canonicalurl");
		}

		@Override
		protected UrlType getUrlType()
		{
			return UrlType.CANONICAL;
		}

		@Override
		protected boolean isEscaped()
		{
			return false;
		}
	}

	// =========================================================================
	// ==
	// == {{canonicalurle:page name}}
	// == {{canonicalurle:page name|query_string}}
	// ==
	// =========================================================================

	public static final class CanonicalurlePfn
			extends
				UrlPfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CanonicalurlePfn()
		{
			super("canonicalurle");
		}

		public CanonicalurlePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "canonicalurle");
		}

		@Override
		protected UrlType getUrlType()
		{
			return UrlType.CANONICAL;
		}

		@Override
		protected boolean isEscaped()
		{
			return true;
		}
	}

	// =========================================================================
	// ==
	// == {{filepath:file name}}
	// == {{filepath:file name|nowiki}}
	// == {{filepath:file name|thumbnail_size}}
	// ==
	// =========================================================================

	public static final class FilepathPfn
			extends
				CorePfnFunction
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public FilepathPfn()
		{
			super("filepath");
		}

		public FilepathPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "filepath");
		}

		@Override
		public WtNode invoke(
				WtTemplate pfn,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			if (args.size() < 1)
				return pfn;

			WtNode titleNode = args.get(0);

			PageTitle title;
			String titleStr = null;
			try
			{
				titleStr = tu().astToText(titleNode).trim();

				title = PageTitle.make(frame.getWikiConfig(), titleStr);

				title = title.newWithNamespace(frame.getWikiConfig().getFileNamespace());
			}
			catch (StringConversionException e1)
			{
				fileInvalidNameWarning(frame, WarningSeverity.NORMAL, titleNode);
				return pfn;
			}
			catch (LinkTargetException e)
			{
				fileInvalidPagenameWarning(frame, WarningSeverity.NORMAL, titleNode, titleStr);
				return pfn;
			}

			int size = -1;
			boolean nowiki = false;
			if (args.size() > 1)
			{
				try
				{
					String opt1 = tu().astToText(args.get(1)).trim();

					String opt2 = null;
					if (args.size() > 2)
						opt2 = tu().astToText(args.get(2)).trim();

					String sizeStr = opt1;
					if ("nowiki".equals(opt1))
					{
						nowiki = true;
						sizeStr = opt2;
					}
					else if ("nowiki".equals(opt2))
					{
						nowiki = true;
					}

					if (sizeStr != null)
						size = Integer.parseInt(sizeStr);
				}
				catch (StringConversionException e)
				{
					fileIllegalArgumentsWarning(
							frame,
							WarningSeverity.INFORMATIVE,
							pfn,
							"Options of parser function cannot be converted into plain text and were ignored");
				}
				catch (NumberFormatException e)
				{
					fileIllegalArgumentsWarning(
							frame,
							WarningSeverity.INFORMATIVE,
							pfn,
							"Size option of parser function is not a number and was ignored");
				}
			}

			String url;
			try
			{
				url = frame.getCallback().fileUrl(title, size, -1);
			}
			catch (Exception e)
			{
				fileIllegalArgumentsWarning(
						frame,
						WarningSeverity.NORMAL,
						pfn,
						"Retrieving the URL of file `" + titleStr + "' failed: " + e);
				return pfn;
			}

			if (url == null)
				return nf().text("");

			return nowiki ? EngineRtData.set(nf().nowiki(url)) : nf().text(url);
		}
	}

	// =========================================================================
	// ==
	// == {{urlencode:string}} (or {{urlencode:string|QUERY}})
	// == {{urlencode:string|WIKI}}
	// == {{urlencode:string|PATH}}
	// ==
	// =========================================================================

	public static final class UrlencodePfn
			extends
				CorePfnFunction
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public UrlencodePfn()
		{
			super("urlencode");
		}

		public UrlencodePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "urlencode");
		}

		@Override
		public WtNode invoke(
				WtTemplate pfn,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			if (args.size() < 1)
				return pfn;

			String text;
			try
			{
				text = tu().astToText(args.get(0)).trim();
			}
			catch (StringConversionException e1)
			{
				fileInvalidNameWarning(frame, WarningSeverity.NORMAL, args.get(0));
				return pfn;
			}

			UrlEncoding encoder = UrlEncoding.QUERY;
			if (args.size() > 1)
			{
				try
				{
					String encoderName = tu().astToText(args.get(1)).trim();

					encoder = UrlEncoding.valueOf(encoderName.toUpperCase());
				}
				catch (StringConversionException e)
				{
					fileInvalidNameWarning(frame, WarningSeverity.INFORMATIVE, args.get(1));
				}
				catch (IllegalArgumentException e)
				{
					fileIllegalArgumentsWarning(
							frame,
							WarningSeverity.INFORMATIVE,
							args.get(1),
							"Unknown URL encoding, falling back to QUERY");
				}
			}

			return nf().text(encoder.encode(text));
		}
	}

	// =========================================================================
	// ==
	// == {{anchorencode:string}}
	// ==
	// =========================================================================

	/**
	 * Encodes a section name like the fragment of a link to that section,
	 * like MediaWiki's CoreParserFunctions::anchorencode().
	 *
	 * MediaWiki additionally replaces characters with character references
	 * (Sanitizer::safeEncodeAttribute()). Here this is only done for
	 * characters which would change the meaning of the wikitext: Link targets
	 * are not entity-decoded, so {@code [[#{{anchorencode:A & B}}]]} would
	 * link to "#A_&amp;amp;_B" otherwise.
	 */
	public static final class AnchorencodePfn
			extends
				CorePfnFunction
	{
		private static final long serialVersionUID = 1L;

		private static final Pattern INTERNAL_LINK_WITH_TEXT_RX =
				Pattern.compile("\\[\\[:?([^\\[|]+)\\|([^\\[]+)\\]\\]");

		private static final Pattern INTERNAL_LINK_RX =
				Pattern.compile("\\[\\[:?([^\\[]+)\\|?\\]\\]");

		private static final Pattern EXTERNAL_LINK_RX =
				Pattern.compile("\\[([A-Za-z][A-Za-z0-9+.\\-]*:(?://)?)([^ ]+?) ([^\\[]+)\\]");

		private static final Pattern QUOTES_RX =
				Pattern.compile("''+");

		private static final Pattern HTML_TAG_RX =
				Pattern.compile("<[^>]*>");

		private static final Pattern CHAR_REF_RX =
				Pattern.compile("&(?:#[0-9]+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);");

		/**
		 * For un-marshaling only.
		 */
		public AnchorencodePfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "anchorencode");
		}

		public AnchorencodePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "anchorencode");
		}

		@Override
		public WtNode invoke(
				WtTemplate pfn,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			if (args.size() < 1)
				return nf().list();

			String text;
			try
			{
				text = tu().astToText(args.get(0));
			}
			catch (StringConversionException e)
			{
				fileInvalidNameWarning(frame, WarningSeverity.NORMAL, args.get(0));
				return pfn;
			}

			ParserConfig parserConfig = frame.getWikiConfig().getParserConfig();

			// Like Parser::guessSectionNameFromWikiText()
			String section = stripSectionName(text, parserConfig);
			section = AnchorEncoder.normalizeSectionNameWhitespace(section);
			section = HtmlSanitizer.decodeCharReferences(section);
			section = AnchorEncoder.normalizeSectionName(section);

			String fragment = AnchorEncoder.escapeIdForLink(section);

			return nf().text(armor(fragment, parserConfig));
		}

		/**
		 * Like MediaWiki's Parser::stripSectionName(): Removes link markup,
		 * quotes and HTML tags.
		 */
		private static String stripSectionName(String text, ParserConfig parserConfig)
		{
			text = INTERNAL_LINK_WITH_TEXT_RX.matcher(text).replaceAll("$2");
			text = INTERNAL_LINK_RX.matcher(text).replaceAll("$1");

			Matcher m = EXTERNAL_LINK_RX.matcher(text);
			StringBuffer b = new StringBuffer();
			while (m.find())
			{
				String replacement = parserConfig.isUrlProtocol(m.group(1)) ?
						m.group(3) :
						m.group();
				m.appendReplacement(b, Matcher.quoteReplacement(replacement));
			}
			m.appendTail(b);
			text = b.toString();

			// Like doQuotes(): '' is italic, ''' is bold, ''''' is both. A
			// quote in front of bold and more than five quotes are text.
			m = QUOTES_RX.matcher(text);
			b = new StringBuffer();
			while (m.find())
			{
				int count = m.group().length();
				String replacement = "";
				if (count == 4)
					replacement = "'";
				else if (count > 5)
					replacement = m.group().substring(5);
				m.appendReplacement(b, replacement);
			}
			m.appendTail(b);
			text = b.toString();

			return HTML_TAG_RX.matcher(text).replaceAll("");
		}

		/**
		 * Replaces characters which would be interpreted as wikitext with
		 * character references, like MediaWiki's
		 * Sanitizer::safeEncodeAttribute().
		 */
		private static String armor(String text, ParserConfig parserConfig)
		{
			StringBuilder b = new StringBuilder(text.length());
			Matcher charRef = CHAR_REF_RX.matcher(text);
			for (int i = 0; i < text.length(); ++i)
			{
				char ch = text.charAt(i);
				switch (ch)
				{
					case '&':
						charRef.region(i, text.length());
						b.append(charRef.lookingAt() ? "&amp;" : "&");
						break;
					case '<':
						b.append("&lt;");
						break;
					case '>':
						b.append("&gt;");
						break;
					case '{':
						b.append("&#123;");
						break;
					case '}':
						b.append("&#125;");
						break;
					case '[':
						b.append("&#91;");
						break;
					case ']':
						b.append("&#93;");
						break;
					case '|':
						b.append("&#124;");
						break;
					case '＿':
						b.append("&#xFF3F;");
						break;
					case '\'':
						if (i + 1 < text.length() && text.charAt(i + 1) == '\'')
						{
							b.append("&#39;&#39;");
							++i;
						}
						else
						{
							b.append(ch);
						}
						break;
					case ':':
						b.append(endsUrlProtocol(text, i, parserConfig) ? "&#58;" : ":");
						break;
					default:
						b.append(ch);
				}
			}
			return b.toString();
		}

		/**
		 * Checks if the colon at the given index ends the name of a URL
		 * protocol (e.g. "http://" or "mailto:").
		 */
		private static boolean endsUrlProtocol(String text, int colon, ParserConfig parserConfig)
		{
			String suffix = text.startsWith("//", colon + 1) ? "://" : ":";
			for (int start = colon - 1; start >= 0; --start)
			{
				char ch = text.charAt(start);
				if (!Character.isLetterOrDigit(ch) && ch != '+' && ch != '.' && ch != '-')
					break;

				String scheme = text.substring(start, colon);
				if (parserConfig.isUrlProtocol(scheme + suffix) || parserConfig.isUrlProtocol(scheme + ":"))
					return true;
			}
			return false;
		}
	}
}
