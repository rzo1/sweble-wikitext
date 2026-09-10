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

package org.sweble.wikitext.engine.utils;

import java.util.regex.Pattern;

/**
 * Turns section names into anchors, i.e. the id of a heading and the fragment
 * of a link to that heading, like MediaWiki does with
 * <code>$wgFragmentMode = [ 'html5' ]</code>.
 *
 * The section name is expected to be plain text: character references have
 * to be decoded and markup has to be removed before.
 */
public final class AnchorEncoder
{
	/**
	 * Like MediaWiki this is not an HTML limit but protection against overly
	 * long ids.
	 */
	private static final int MAX_ID_LENGTH = 1024;

	private static final Pattern SECTION_WHITESPACE_RX =
			Pattern.compile("[ _]+");

	private static final Pattern BIDI_OVERRIDE_RX =
			Pattern.compile("[\\u200E\\u200F\\u202A-\\u202E]+");

	private static final Pattern TITLE_WHITESPACE_RX =
			Pattern.compile("[ _\\u00A0\\u1680\\u180E\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+");

	private static final Pattern TRAILING_UNDERSCORES_RX =
			Pattern.compile("_+$");

	private static final Pattern ID_WHITESPACE_RX =
			Pattern.compile("[\\t\\n\\f\\r ]");

	private static final Pattern PERCENT_ESCAPE_RX =
			Pattern.compile("%([a-fA-F0-9]{2})");

	// =========================================================================

	private AnchorEncoder()
	{
	}

	// =========================================================================

	/**
	 * Returns the id of a heading with the given section name.
	 */
	public static String encodeId(String sectionName)
	{
		return escapeIdForAttribute(normalizeSectionName(
				normalizeSectionNameWhitespace(sectionName)));
	}

	/**
	 * Returns the fragment of a link to a heading with the given section name
	 * (without the leading '#').
	 */
	public static String encodeLinkFragment(String sectionName)
	{
		return escapeIdForLink(normalizeSectionName(
				normalizeSectionNameWhitespace(sectionName)));
	}

	// =========================================================================

	/**
	 * Like MediaWiki's Sanitizer::normalizeSectionNameWhitespace(): Runs of
	 * spaces and underscores become one space, the name is trimmed.
	 */
	public static String normalizeSectionNameWhitespace(String sectionName)
	{
		return SECTION_WHITESPACE_RX.matcher(sectionName).replaceAll(" ").trim();
	}

	/**
	 * Like MediaWiki's Parser::normalizeSectionName(): Applies the
	 * normalization of the title parser to the fragment of a link.
	 */
	public static String normalizeSectionName(String sectionName)
	{
		// The fragment of the title "#name"
		String name = BIDI_OVERRIDE_RX.matcher(sectionName).replaceAll("");
		name = TITLE_WHITESPACE_RX.matcher(name).replaceAll("_");
		name = TRAILING_UNDERSCORES_RX.matcher(name).replaceAll("");
		return name.replace('_', ' ');
	}

	/**
	 * Escapes a normalized section name to be a valid HTML id attribute like
	 * MediaWiki's Sanitizer::escapeIdForAttribute() in html5 mode.
	 */
	public static String escapeIdForAttribute(String id)
	{
		if (id.codePointCount(0, id.length()) > MAX_ID_LENGTH)
			id = id.substring(0, id.offsetByCodePoints(0, MAX_ID_LENGTH));

		return ID_WHITESPACE_RX.matcher(id).replaceAll("_");
	}

	/**
	 * Escapes a normalized section name to be a valid URL fragment like
	 * MediaWiki's Sanitizer::escapeIdForLink() in html5 mode: Unlike in an id,
	 * percent signs that look like a percent-encoded byte are escaped.
	 */
	public static String escapeIdForLink(String id)
	{
		return PERCENT_ESCAPE_RX.matcher(escapeIdForAttribute(id)).replaceAll("%25$1");
	}
}
