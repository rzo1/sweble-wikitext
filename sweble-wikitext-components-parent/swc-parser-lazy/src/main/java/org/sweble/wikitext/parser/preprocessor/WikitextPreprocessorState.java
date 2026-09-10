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

package org.sweble.wikitext.parser.preprocessor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.sweble.wikitext.parser.ParserConfig;
import org.sweble.wikitext.parser.WtEntityMap;
import org.sweble.wikitext.parser.nodes.WtNode;

import de.fau.cs.osr.ptk.common.ParserState;

public class WikitextPreprocessorState
		extends
			ParserState<WikitextPreprocessorContext>
{
	private ParserConfig config;

	private WtEntityMap entityMap;

	private boolean autoCorrect;

	private boolean warningsEnabled;

	private boolean gatherRtData;

	// =========================================================================

	private boolean hasOnlyInclude;

	private boolean parseForInclusion;

	private int maxNestingDepth = ParserConfig.DEFAULT_MAX_NESTING_DEPTH;

	// =========================================================================

	private static final int UNKNOWN = -2;

	/**
	 * The input of the preprocessor or {@code null} if unknown. In the latter
	 * case the look-ahead caches below are disabled.
	 */
	private String input;

	/**
	 * Position of the last "]]" in the input, -1 if there is none.
	 */
	private int lastDoubleClosingBracket = UNKNOWN;

	/**
	 * Position of the last "}}" in the input, -1 if there is none.
	 */
	private int lastDoubleClosingBrace = UNKNOWN;

	/**
	 * Position of the last closing tag for a given tag name (as written in
	 * the opening tag), -1 if there is none.
	 */
	private final Map<String, Integer> lastClosingTag = new HashMap<String, Integer>();

	// =========================================================================

	public WikitextPreprocessorState()
	{
		super(WikitextPreprocessorContext.class);

		this.hasOnlyInclude = false;

		this.parseForInclusion = false;
	}

	// =========================================================================

	public ParserConfig getConfig()
	{
		return config;
	}

	public void init(
			ParserConfig config,
			WtEntityMap entityMap,
			boolean forInclusion)
	{
		init(config, entityMap, forInclusion, null);
	}

	/**
	 * @param input
	 *            The input the preprocessor parses. Enables caches that keep
	 *            the preprocessor from scanning ahead for closing brackets,
	 *            braces and tags over and over again. May be {@code null}.
	 */
	public void init(
			ParserConfig config,
			WtEntityMap entityMap,
			boolean forInclusion,
			String input)
	{
		this.input = input;

		this.lastDoubleClosingBracket = UNKNOWN;

		this.lastDoubleClosingBrace = UNKNOWN;

		this.lastClosingTag.clear();

		this.maxNestingDepth = config.getMaxNestingDepth();

		this.config = config;

		this.entityMap = entityMap;

		this.parseForInclusion = forInclusion;

		this.autoCorrect = config.isAutoCorrect();

		this.warningsEnabled = config.isWarningsEnabled();

		this.gatherRtData = config.isGatherRtData();
	}

	// =========================================================================

	public WtEntityMap getEntityMap()
	{
		return entityMap;
	}

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

	public void setTagExtensionName(String name)
	{
		getTop().setTagExtensionName(name);
	}

	public boolean isValidClosingTag(String name)
	{
		String cur = getTop().getTagExtensionName();
		if (cur == null)
			return false;
		return name.compareToIgnoreCase(cur) == 0;
	}

	public void setTemplateBraces(int i)
	{
		getTop().setTemplateBraces(i);
	}

	public int getTemplateBraces()
	{
		return getTop().getTemplateBraces();
	}

	public boolean hasAtLeastTemplateBraces(int i)
	{
		return getTop().getTemplateBraces() >= i;
	}

	public void eatTemplateBraces(int i)
	{
		getTop().setTemplateBraces(
				getTop().getTemplateBraces() - i);
	}

	// =========================================================================

	/**
	 * Enters a nested template, template parameter or internal link by
	 * incrementing the nesting depth of the current context. Must only be
	 * called from a stateful production, which restores the depth when it is
	 * left.
	 *
	 * @return {@code false} and leaves the depth unchanged if
	 *         {@link ParserConfig#getMaxNestingDepth()} has already been
	 *         reached.
	 */
	public boolean enterNesting()
	{
		WikitextPreprocessorContext c = getTop();
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
	 * @return {@code false} if there is no "}}" at or after the given
	 *         position. A template or parameter opened in front of that
	 *         position can then never be closed.
	 */
	public boolean hasDoubleClosingBraceAfter(int pos)
	{
		if (input == null)
			return true;
		if (lastDoubleClosingBrace == UNKNOWN)
			lastDoubleClosingBrace = input.lastIndexOf("}}");
		return lastDoubleClosingBrace >= pos;
	}

	/**
	 * Checks whether the input contains a closing tag for the current tag
	 * extension (see {@link #setTagExtensionName(String)}) at or after the
	 * given position.
	 * <p>
	 * Like MediaWiki's noMoreClosingTag cache this keeps input like
	 * "&lt;foo>&lt;foo>&lt;foo>..." from taking quadratic time: The input is
	 * scanned only once per tag name.
	 */
	public boolean hasClosingTagAfter(int pos)
	{
		String name = getTop().getTagExtensionName();
		if (input == null || name == null)
			return true;
		Integer last = lastClosingTag.get(name);
		if (last == null)
		{
			last = findLastClosingTag(name);
			lastClosingTag.put(name, last);
		}
		return last >= pos;
	}

	/**
	 * Finds the last closing tag "&lt;/name ws* >" like the ValidClosingTag
	 * production does: The name is matched case-insensitively and must not be
	 * followed by further name characters.
	 */
	private int findLastClosingTag(String name)
	{
		int i = input.lastIndexOf("</");
		while (i >= 0)
		{
			if (isClosingTagAt(i, name))
				return i;
			i = input.lastIndexOf("</", i - 1);
		}
		return -1;
	}

	private boolean isClosingTagAt(int i, String name)
	{
		int j = i + 2;
		if (!input.regionMatches(true, j, name, 0, name.length()))
			return false;
		j += name.length();

		// Neither whitespace nor '>' can be part of a tag name. If either
		// follows, the tag name ends here.
		int len = input.length();
		while (j < len && isWhitespace(input.charAt(j)))
			++j;
		return j < len && input.charAt(j) == '>';
	}

	/**
	 * The characters matched by pWsStar.
	 */
	private static boolean isWhitespace(char ch)
	{
		switch (ch)
		{
			case ' ':
			case '\t':
			case '\f':
			case '\n':
			case '\r':
			case '\u000B':
			case '\u0085':
			case '\u2028':
			case '\u2029':
				return true;
			default:
				return false;
		}
	}

	// =========================================================================

	public boolean isHasOnlyInclude()
	{
		return hasOnlyInclude;
	}

	public void setHasOnlyInclude(boolean hasOnlyInclude)
	{
		this.hasOnlyInclude = hasOnlyInclude;
	}

	public boolean isParseForInclusion()
	{
		return parseForInclusion;
	}

	public boolean isIgnoredElement(String name)
	{
		String lcName = name.toLowerCase(Locale.ROOT);
		if (isParseForInclusion())
		{
			return "noinclude".compareTo(lcName) == 0;
		}
		else
		{
			return "includeonly".compareTo(lcName) == 0;
		}
	}

	public boolean isIgnoredTag(String name)
	{
		String lcName = name.toLowerCase(Locale.ROOT);
		if (isParseForInclusion())
		{
			return "includeonly".compareTo(lcName) == 0;
		}
		else
		{
			return ("noinclude".compareTo(lcName) == 0) ||
					("onlyinclude".compareTo(lcName) == 0);
		}
	}

	public boolean isRedirectKeyword(String keyword)
	{
		return config.isRedirectKeyword(keyword);
	}

	public WtNode getEntity(int id)
	{
		return entityMap.getEntity(id);
	}
}
