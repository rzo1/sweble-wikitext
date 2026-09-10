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

package org.sweble.wikitext.parser;

import java.util.Map;

import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WikitextNodeFactory;
import org.sweble.wikitext.parser.parser.LinkBuilder.LinkType;
import org.sweble.wikitext.parser.utils.AstTextUtils;

import de.fau.cs.osr.utils.XmlEntityResolver;

public interface ParserConfig
		extends
			XmlEntityResolver
{
	// ==[ Encoding validation features ]=======================================

	boolean isConvertIllegalCodePoints();

	// ==[ Parser features ]====================================================

	boolean isWarningsEnabled();

	boolean isWarningLevelEnabled(WarningSeverity severity);

	boolean isAutoCorrect();

	boolean isGatherRtData();

	// ==[ AST creation/processing ]============================================

	WikitextNodeFactory getNodeFactory();

	AstTextUtils getAstTextUtils();

	// ==[ Link classification and parsing ]====================================

	boolean isUrlProtocol(String proto);

	/**
	 * @return The regular expression matching the prefix of an internal link
	 *         at the end of the text in front of the link. {@code null} or an
	 *         empty string if internal links have no prefix.
	 */
	String getInternalLinkPrefixPattern();

	/**
	 * @return The regular expression matching the postfix (link trail) of an
	 *         internal link at the beginning of the text following the link.
	 *         {@code null} or an empty string if internal links have no
	 *         postfix.
	 */
	String getInternalLinkPostfixPattern();

	LinkType classifyTarget(String target);

	boolean isNamespace(String nsName);

	boolean isTalkNamespace(String nsName);

	boolean isInterwikiName(String iwPrefix);

	boolean isIwPrefixOfThisWiki(String iwPrefix);

	/**
	 * Resolves a (possibly localized) image link option to the id of the
	 * magic word it is an alias of. For example, {@code "thumb"} and the
	 * German {@code "mini"} both resolve to {@code "img_thumbnail"}.
	 * Parameterized options are passed in the form MediaWiki uses for their
	 * aliases, with the parameter replaced by {@code $1} (e.g.
	 * {@code "$1px"} or {@code "link=$1"}). The ids the parser understands
	 * are listed in {@link ImageLinkOptionAliases}.
	 *
	 * The English aliases are always recognized by the parser as a fallback
	 * ({@link ImageLinkOptionAliases#getDefaultId(String)}), implementations
	 * only have to provide additional (localized) aliases. The default
	 * implementation does not know any additional aliases.
	 *
	 * @return The magic word id or {@code null} if the given string is not a
	 *         known image link option alias.
	 */
	default String getImageLinkOptionId(String alias)
	{
		return null;
	}

	// ==[ Names ]==============================================================

	/**
	 * Checks whether the given name denotes a page switch (behavior switch).
	 *
	 * @param name
	 *            The name of the page switch without the enclosing underscores
	 *            (e.g. {@code NOTOC} for {@code __NOTOC__}).
	 */
	boolean isValidPageSwitchName(String name);

	boolean isValidExtensionTagName(String name);

	boolean isRedirectKeyword(String keyword);

	// ==[ Parsing XML elements ]===============================================

	boolean isValidXmlEntityRef(String name);

	Map<String, String> getXmlEntities();

	NonStandardElementBehavior getNonStandardElementBehavior(String elementName);

	boolean isFosterParenting();

	boolean isFosterParentingForTransclusions();

	// ==[ Language Conversion Tags ]===========================================

	boolean isLangConvTagsEnabled();

	boolean isLctFlag(String flag);

	String normalizeLctFlag(String flag);

	boolean isLctVariant(String variant);

	String normalizeLctVariant(String variant);

	// ==[ Misc ]===============================================================

	boolean isPreserveSemiPreLeadingSpace();

	// ==[ Robustness ]=========================================================

	/**
	 * The default value of {@link #getMaxNestingDepth()}.
	 */
	int DEFAULT_MAX_NESTING_DEPTH = 100;

	/**
	 * The maximum depth to which recursive constructs can nest: Templates,
	 * template parameters and internal links in the preprocessor; internal
	 * links, image links and language conversion tags in the parser. All
	 * constructs share one counter, the preprocessor and the parser count
	 * separately. Beyond this depth the opening markup of a construct is
	 * treated as text.
	 * <p>
	 * The limit prevents stack overflows on deeply nested input. The default
	 * of {@value #DEFAULT_MAX_NESTING_DEPTH} is far beyond what real content
	 * needs, while leaving head room on a normal 1 MB thread stack: Without a
	 * limit, parsing and post-processing overflow such a stack at about 170
	 * nested image links, 210 nested language conversion tags and 250 nested
	 * templates or internal links. The remaining stack is needed for
	 * constructs the limit does not cover (tables, lists, ...).
	 */
	default int getMaxNestingDepth()
	{
		return DEFAULT_MAX_NESTING_DEPTH;
	}
}
