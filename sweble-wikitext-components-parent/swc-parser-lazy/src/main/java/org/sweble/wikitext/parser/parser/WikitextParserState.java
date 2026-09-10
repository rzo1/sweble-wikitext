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

package org.sweble.wikitext.parser.parser;

import java.util.regex.Pattern;

import org.sweble.wikitext.parser.ParserConfig;
import org.sweble.wikitext.parser.WtEntityMap;
import org.sweble.wikitext.parser.WtEntityMapImpl;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtPageName;

import de.fau.cs.osr.ptk.common.ParserState;

public class WikitextParserState
		extends
			ParserState<WikitextParserContext>
{
	private WtEntityMap entityMap = new WtEntityMapImpl();

	private ParserConfig config;

	private Pattern postfixPattern;

	private Pattern prefixPattern;

	private boolean autoCorrect;

	private boolean warningsEnabled;

	private boolean gatherRtData;

	private boolean langConvTagsEnabled;

	private int maxNestingDepth = ParserConfig.DEFAULT_MAX_NESTING_DEPTH;

	// =========================================================================

	private static final int UNKNOWN = -2;

	/**
	 * The input of the parser or {@code null} if unknown. In the latter case
	 * the look-ahead caches below are disabled.
	 */
	private String input;

	/**
	 * Position of the last "]]" in the input, -1 if there is none.
	 */
	private int lastDoubleClosingBracket = UNKNOWN;

	/**
	 * Positions for which it is known that an external link cannot be closed
	 * on the same line: [from, to].
	 */
	private int noClosingBracketFrom = 0;

	private int noClosingBracketTo = -1;

	/**
	 * Positions for which it is known that an external link might be closed
	 * on the same line: [from, to].
	 */
	private int maybeClosingBracketFrom = 0;

	private int maybeClosingBracketTo = -1;

	// =========================================================================

	@Override
	protected WikitextParserContext instantiateContext()
	{
		return new WikitextParserContext();
	}

	// =========================================================================

	public WtEntityMap getEntityMap()
	{
		return entityMap;
	}

	public WtNode getEntity(int id)
	{
		return entityMap.getEntity(id);
	}

	// =========================================================================

	public ParserConfig getConfig()
	{
		return config;
	}

	public void init(ParserConfig config, WtEntityMap entityMap)
	{
		init(config, entityMap, null);
	}

	/**
	 * @param input
	 *            The input the parser parses. Enables caches that keep the
	 *            parser from scanning ahead for closing brackets over and
	 *            over again. May be {@code null}.
	 */
	public void init(ParserConfig config, WtEntityMap entityMap, String input)
	{
		this.input = input;

		this.lastDoubleClosingBracket = UNKNOWN;

		this.noClosingBracketFrom = 0;
		this.noClosingBracketTo = -1;
		this.maybeClosingBracketFrom = 0;
		this.maybeClosingBracketTo = -1;

		this.maxNestingDepth = config.getMaxNestingDepth();

		this.config = config;

		this.entityMap = entityMap;

		this.autoCorrect = config.isAutoCorrect();

		this.warningsEnabled = config.isWarningsEnabled();

		this.gatherRtData = config.isGatherRtData();

		this.langConvTagsEnabled = config.isLangConvTagsEnabled();

		String prefix = config.getInternalLinkPrefixPattern();
		this.prefixPattern = isNullOrEmpty(prefix) ?
				null :
				Pattern.compile("(" + prefix + ")$");

		String postfix = config.getInternalLinkPostfixPattern();
		this.postfixPattern = isNullOrEmpty(postfix) ?
				null :
				Pattern.compile(postfix);
	}

	private static boolean isNullOrEmpty(String pattern)
	{
		return pattern == null || pattern.isEmpty();
	}

	// =========================================================================

	/**
	 * @return Whether another internal link, image link or language
	 *         conversion tag may be opened without exceeding
	 *         {@link ParserConfig#getMaxNestingDepth()}.
	 */
	public boolean canNest()
	{
		return getTop().getNestingDepth() < maxNestingDepth;
	}

	/**
	 * Enters a nested construct by incrementing the nesting depth of the
	 * current context. Must only be called from a stateful production, which
	 * restores the depth when it is left.
	 *
	 * @return {@code false} and leaves the depth unchanged if the maximum
	 *         nesting depth has already been reached.
	 */
	public boolean enterNesting()
	{
		WikitextParserContext c = getTop();
		if (c.getNestingDepth() >= maxNestingDepth)
			return false;
		c.setNestingDepth(c.getNestingDepth() + 1);
		return true;
	}

	// =========================================================================

	/**
	 * @return {@code false} if there is no "]]" at or after the given
	 *         position. An internal link opened in front of that position can
	 *         then never be closed.
	 */
	public boolean hasDoubleClosingBracketAfter(int pos)
	{
		if (input == null)
			return true;
		if (lastDoubleClosingBracket == UNKNOWN)
			lastDoubleClosingBracket = input.lastIndexOf("]]");
		return lastDoubleClosingBracket >= pos;
	}

	/**
	 * Checks whether an external link starting at the given position might
	 * be closed by a ']' before the end of the line.
	 * <p>
	 * The title of an external link cannot contain a newline by itself, only
	 * nested internal links, language conversion tags and XML elements can
	 * span lines. The answer is therefore only {@code false} if neither a ']'
	 * nor the opener of such an element ("[[", "-{", '<') occurs before the
	 * end of the line.
	 * <p>
	 * Like MediaWiki's noMoreClosingTag cache, the result of a scan is
	 * remembered for all positions it covered. Repeated unclosed openers on
	 * one line therefore only scan the line once.
	 */
	public boolean mayHaveClosingBracketOnLine(int pos)
	{
		if (input == null)
			return true;
		if (pos >= noClosingBracketFrom && pos <= noClosingBracketTo)
			return false;
		if (pos >= maybeClosingBracketFrom && pos <= maybeClosingBracketTo)
			return true;

		int len = input.length();
		for (int i = pos; i < len; ++i)
		{
			switch (input.charAt(i))
			{
				case ']':
				case '<':
					return maybeClosingBracket(pos, i);

				case '[':
					if (i + 1 < len && input.charAt(i + 1) == '[')
						return maybeClosingBracket(pos, i);
					break;

				case '-':
					if (i + 1 < len && input.charAt(i + 1) == '{')
						return maybeClosingBracket(pos, i);
					break;

				// The newlines of pSlEol except for '\f', which also
				// counts as space in front of the title.
				case '\n':
				case '\r':
				case '\u000B':
				case '\u0085':
				case '\u2028':
				case '\u2029':
					return noClosingBracket(pos, i);

				default:
					break;
			}
		}
		return noClosingBracket(pos, len);
	}

	private boolean maybeClosingBracket(int from, int to)
	{
		maybeClosingBracketFrom = from;
		maybeClosingBracketTo = to;
		return true;
	}

	private boolean noClosingBracket(int from, int to)
	{
		noClosingBracketFrom = from;
		noClosingBracketTo = to;
		return false;
	}

	// =========================================================================

	public boolean isAutoCorrect()
	{
		return autoCorrect;
	}

	public boolean isWarnignsEnabled()
	{
		return warningsEnabled;
	}

	public boolean isGatherRtData()
	{
		return gatherRtData;
	}

	// =========================================================================

	/**
	 * @return The pattern matching the prefix of an internal link at the end
	 *         of the text in front of the link or {@code null} if internal
	 *         links have no prefix.
	 */
	public Pattern getInternalLinkPrefixPattern()
	{
		return prefixPattern;
	}

	/**
	 * @return The pattern matching the postfix of an internal link at the
	 *         beginning of the text following the link or {@code null} if
	 *         internal links have no postfix.
	 */
	public Pattern getInternalLinkPostfixPattern()
	{
		return postfixPattern;
	}

	// =========================================================================

	public LinkBuilder getLinkBuilder()
	{
		return getTop().getLinkBuilder();
	}

	public void initLinkBuilder(WtPageName target)
	{
		getTop().initLinkBuilder(config, target);
	}

	// =========================================================================

	public ParserScopes getScope()
	{
		return getTop().getScope();
	}

	public void setScope(ParserScopes scope)
	{
		WikitextParserContext c = getTop();
		c.setScope(scope);
		if (scope.isSticky())
			c.addStickingScope(scope);
	}

	public boolean accepts(ParserAtoms atom)
	{
		WikitextParserContext c = getTop();
		if (c.getScope().accepts(atom))
		{
			int sticking = c.getStickingScopes();
			for (int i = 0; sticking != 0; ++i, sticking >>= 1)
			{
				if ((sticking & 1) != 0)
				{
					if (!ParserScopes.values()[i].accepts(atom))
						return false;
				}
			}
			return true;
		}
		return false;
	}

	public boolean inScope(ParserScopes scope)
	{
		WikitextParserContext c = getTop();
		if (c.getScope() == scope)
		{
			return true;
		}
		else
		{
			int bit = 1 << scope.ordinal();
			return 0 != (c.getStickingScopes() & bit);
		}
	}

	public boolean isLangConvTagsEnabled()
	{
		return langConvTagsEnabled;
	}
}
