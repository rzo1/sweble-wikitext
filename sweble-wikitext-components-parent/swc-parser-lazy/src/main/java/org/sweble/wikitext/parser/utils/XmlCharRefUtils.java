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

package org.sweble.wikitext.parser.utils;

public final class XmlCharRefUtils
{
	/**
	 * U+10FFFF has 7 decimal and 6 hexadecimal digits. Numbers with more
	 * significant digits than this cannot be code points and are not parsed,
	 * which also keeps them from overflowing a long.
	 */
	private static final int MAX_SIGNIFICANT_DIGITS = 8;

	// =========================================================================

	private XmlCharRefUtils()
	{
	}

	// =========================================================================

	/**
	 * Parses the digits of a character reference.
	 *
	 * @param digits
	 *            The decimal or hexadecimal digits of the reference.
	 * @param radix
	 *            Either 10 or 16.
	 * @return The code point or -1 if the number is too large to be a code
	 *         point.
	 */
	public static long parseCodePoint(String digits, int radix)
	{
		int start = 0;
		while (start < digits.length() - 1 && digits.charAt(start) == '0')
			++start;

		if (digits.length() - start > MAX_SIGNIFICANT_DIGITS)
			return -1;

		return Long.parseLong(digits.substring(start), radix);
	}

	/**
	 * Whether a character reference to the given code point denotes a
	 * character (like {@code Sanitizer::validateCodepoint} in MediaWiki).
	 * MediaWiki renders references to all other code points as literal text.
	 */
	public static boolean isValidCodePoint(long cp)
	{
		return cp == 0x09
				|| cp == 0x0A
				|| (cp >= 0x20 && cp <= 0x7E)
				|| (cp >= 0xA0 && cp <= 0xD7FF)
				|| (cp >= 0xE000 && cp <= 0xFFFD)
				|| (cp >= 0x10000 && cp <= 0x10FFFF);
	}
}
