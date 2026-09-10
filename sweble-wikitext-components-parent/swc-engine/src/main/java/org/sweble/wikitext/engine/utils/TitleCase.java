/**
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

import java.util.Locale;

/**
 * Converts the first letter of a title to upper case like MediaWiki's
 * Language::ucfirst().
 */
public final class TitleCase
{
	private static final Locale TURKIC = Locale.forLanguageTag("tr");

	// =========================================================================

	private TitleCase()
	{
	}

	/**
	 * Converts the first character (code point) of the given text to upper
	 * case like MediaWiki's Language::ucfirst() for the given content
	 * language:
	 *
	 * <ul>
	 * <li>A character is only replaced if its upper case form is a single
	 * character. Characters whose upper case form consists of several
	 * characters are kept as they are, e.g. "ß" (not "SS") or "ﬁ" (not "FI").
	 * This matches the simple case mapping of PHP before 7.3, which Wikimedia
	 * wikis keep with $wgOverrideUcfirstCharacters.</li>
	 * <li>Georgian letters (Mkhedruli) are kept, their upper case form
	 * (Mtavruli) is not used in titles. This also makes the result
	 * independent of the Unicode version of the JVM.</li>
	 * <li>In Turkish, Azerbaijani and Karakalpak a dotted "i" becomes a dotted
	 * capital "İ" (MediaWiki's LanguageTr, LanguageAz and LanguageKaa). In all
	 * languages a dotless "ı" becomes "I".</li>
	 * </ul>
	 *
	 * @param contentLanguage
	 *            The content language of the wiki, e.g. "tr". May be null.
	 */
	public static String ucfirst(String text, String contentLanguage)
	{
		if (text == null || text.isEmpty())
			return text;

		int first = text.codePointAt(0);
		int length = Character.charCount(first);

		String original = text.substring(0, length);
		String upper = original.toUpperCase(getLocale(contentLanguage));
		if (upper.equals(original))
			return text;

		if (upper.codePointCount(0, upper.length()) != 1)
			return text;

		if (isGeorgianMtavruli(upper.codePointAt(0)))
			return text;

		return upper + text.substring(length);
	}

	private static Locale getLocale(String contentLanguage)
	{
		if (contentLanguage == null)
			return Locale.ROOT;

		switch (contentLanguage.toLowerCase(Locale.ROOT))
		{
			case "tr":
			case "az":
			case "kaa":
				return TURKIC;
			default:
				// No other language has special rules for the first letter
				return Locale.ROOT;
		}
	}

	private static boolean isGeorgianMtavruli(int codePoint)
	{
		return codePoint >= 0x1C90 && codePoint <= 0x1CBF;
	}
}
