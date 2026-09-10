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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sweble.wikitext.parser.ParserConfig;
import org.sweble.wikitext.parser.parser.LinkTargetException.Reason;

import de.fau.cs.osr.utils.StringTools;
import de.fau.cs.osr.utils.XmlGrammar;

/**
 * Expects the string to contain only valid Unicode characters. It must not
 * contain invalid, non- or private use characters. It further expects the
 * string to not contain the following characters:
 * [\u0000-\u001F\u007F\uFFFD&lt;&gt;{}|[\]].
 * 
 * The parser checks if the link target contains any of the following entites,
 * which are not allowed in link targets:
 * 
 * <ul>
 * <li>Percent encoding of URIs:
 * 
 * <pre>
 * %[0-9A-Fa-f]{2}
 * </pre>
 * 
 * </li>
 * <li>XML entity references:
 * 
 * <pre>
 * &amp;&lt;Name&gt;;
 * </pre>
 * 
 * </li>
 * <li>XML char references:
 * 
 * <pre>
 * (&amp;#[0-9]+;)|(&amp;#x[0-9A-Fa-f]+;)
 * </pre>
 * 
 * </li>
 * <li>Relative path components:
 * 
 * <pre>
 * (^\.\.?($|/))|(/\.\.?/)|(/\.\.?$)
 * </pre>
 * 
 * </li>
 * <li>No magic tilde sequences:
 * 
 * <pre>
 * ~~~
 * </pre>
 * 
 * </li>
 * </ul>
 */
public class LinkTargetParser
{
	private String title;

	private String fragment;

	private String namespace;

	private String interwiki;

	private boolean initialColon;

	// =========================================================================

	private final static Pattern bidiCharPattern = Pattern.compile(
			"[\u200E\u200F\u202A-\u202E]");

	/**
	 * The characters MediaWiki converts into underscores in titles.
	 */
	private final static String SPACE_CHARS =
			" _\u00A0\u1680\u180E\u2000-\u200A\u2028\u2029\u202F\u205F\u3000";

	private final static Pattern spacePlusPattern = Pattern.compile(
			"[" + SPACE_CHARS + "]+");

	private final static Pattern trimSpacesPattern = Pattern.compile(
			"^[" + SPACE_CHARS + "]+|[" + SPACE_CHARS + "]+$");

	/**
	 * MediaWiki's prefix regexp "^(.+?)_*:_*(.*)$". MediaWiki converts all
	 * whitespace into underscores before matching, we have not done that yet.
	 */
	private final static Pattern namespaceSeparatorPattern = Pattern.compile(
			"^(.+?)[" + SPACE_CHARS + "]*:[" + SPACE_CHARS + "]*(.*)$");

	private final static Pattern xmlReferencePattern = Pattern.compile(
			"&" + XmlGrammar.RE_XML_NAME + ";|&#([0-9]+);|&#[xX]([0-9A-Fa-f]+);");

	private final static Pattern invalidTitle = Pattern.compile(
			// Percent encoding for URIs
			"(%[0-9A-Fa-f]{2})" +

					// XML entity reference
					"|(&" + XmlGrammar.RE_XML_NAME + ";)" +

					// XML char reference
					"|((&#[0-9]+;)|(&#x[0-9A-Fa-f]+;))" +

					// Relative path components
					"|(^\\.\\.?($|/))" +
					"|(/\\.\\.?/)" +
					"|(/\\.\\.?$)" +

					// No magic tilde sequences
					"|(~~~)" +

					// No invalid characters
					"|[\\u0000-\\u001F\\u007F\\uFFFD<>{}\\|\\[\\]]");

	// =========================================================================

	public void parse(ParserConfig config, final String target) throws LinkTargetException
	{
		this.title = null;
		this.fragment = null;
		this.namespace = null;
		this.interwiki = null;
		this.initialColon = false;

		String result = target;

		// Decode URL encoded characters
		{
			result = urlDecode(result);
		}

		// Decode XML entities
		{
			result = xmlDecode(config, result);
		}

		// Strip bidi override characters
		{
			Matcher matcher = bidiCharPattern.matcher(result);
			result = matcher.replaceAll("");
		}

		// Trim whitespace (*)
		{
			result = StringTools.trim(result);
		}

		/*
		// Remove trailing whitespace characters
		result = StringTools.trimUnderscores(result);
		*/

		if (result.isEmpty())
			throw new LinkTargetException(Reason.EMPTY_TARGET, target);

		// Has the link an initial colon? Can be reset by identifyNamespaces!
		if (result.charAt(0) == ':')
		{
			this.initialColon = true;
			result = result.substring(1);
			result = trimSpaces(result);
		}

		// Identify namespaces and interwiki names
		result = identifyNamespaces(config, target, result);

		// Get the part after the '#'
		result = extractFragment(result);

		// Perform sanity checks on remaining title
		{
			// Fixes issue #45:
			// "&_foo_;" become "& foo ;" and will not be recognized as illegal entity.
			// Related to (**)
			result = result.replace('_', ' ');

			Matcher matcher = invalidTitle.matcher(result);
			if (matcher.find())
				throw new LinkTargetException(
						Reason.INVALID_ENTITIES,
						target,
						matcher.group());
		}

		// Fixes issue #45:
		// (**) Strip whitespace characters
		// IMPORTANT: Was done after (*). Led to problems for titles like
		// '& foo ;' which became '&_foo_;' and were treated as illegal XML
		// entities by the sanity check. Also when done here it will not
		// affect the fragment which seems to be a good thing...
		{
			Matcher matcher = spacePlusPattern.matcher(result);
			result = matcher.replaceAll("_");
		}

		// Empty links to a namespace alone are not allowed
		if (result.isEmpty() &&
				this.interwiki == null &&
				this.namespace != null)
		{
			throw new LinkTargetException(Reason.ONLY_NAMESPACE, target);
		}

		this.title = result;
	}

	private String identifyNamespaces(
			ParserConfig config,
			final String target,
			String result) throws LinkTargetException
	{
		Matcher matcher = namespaceSeparatorPattern.matcher(result);
		if (matcher.matches())
		{
			// We have at least ONE namespace
			String nsName = normalizeNsName(matcher.group(1));

			if (config.isNamespace(nsName))
			{
				// It is a KNOWN namespace
				result = matcher.group(2);
				this.namespace = nsName;

				checkNoNsAfterTalkNs(config, target, result, nsName);
			}
			else if (config.isInterwikiName(nsName))
			{
				// It is a KNOWN interwiki name
				result = matcher.group(2);

				if (config.isIwPrefixOfThisWiki(nsName))
				{
					// It points to THIS wiki
					if (result.isEmpty())
					{
						throw new LinkTargetException(Reason.NO_ARTICLE_TITLE, target);
					}
					else
					{
						matcher = namespaceSeparatorPattern.matcher(result);
						if (matcher.matches())
						{
							// There are more namespace parts
							nsName = normalizeNsName(matcher.group(1));

							if (config.isNamespace(nsName))
							{
								result = matcher.group(2);
								this.namespace = nsName;

								checkNoNsAfterTalkNs(config, target, result, nsName);
							}
							else if (config.isInterwikiName(nsName))
							{
								throw new LinkTargetException(Reason.IW_IW_LINK, target, nsName);
							}
						}
					}
				}
				else
				{
					this.interwiki = nsName;

					if (!result.isEmpty() && result.charAt(0) == ':')
					{
						this.initialColon = true;
						result = result.substring(1);
						result = trimSpaces(result);
					}
				}
			}
		}
		return result;
	}

	private void checkNoNsAfterTalkNs(
			ParserConfig config,
			final String target,
			String result,
			String nsName) throws LinkTargetException
	{
		Matcher matcher;
		if (config.isTalkNamespace(nsName))
		{
			matcher = namespaceSeparatorPattern.matcher(result);
			if (matcher.matches())
			{
				nsName = normalizeNsName(matcher.group(1));
				if ((config.isNamespace(nsName) || config.isInterwikiName(nsName)))
					throw new LinkTargetException(Reason.TALK_NS_IW_LINK, target, nsName);
			}
		}
	}

	private String extractFragment(String result)
	{
		int i = result.indexOf('#');
		if (i != -1)
		{
			String fragment = result.substring(i + 1);
			this.fragment = StringTools.trimUnderscores(fragment);

			// All whitespace becomes an underscore later on, so trim all of
			// it like MediaWiki's rtrim($dbkey, '_').
			result = trimSpaces(result.substring(0, i));
		}
		return result;
	}

	/**
	 * Decodes percent encoded characters like PHP's rawurldecode(), which is
	 * what MediaWiki applies to link targets containing a '%' character. Runs
	 * of consecutive percent encoded bytes are decoded as UTF-8. Invalid
	 * UTF-8 sequences are replaced with U+FFFD and will render the title
	 * invalid, just like MediaWiki rejects titles containing invalid UTF-8.
	 * Unlike urldecode(), '+' characters are not converted into spaces.
	 */
	static String urlDecode(String text)
	{
		int i = text.indexOf('%');
		if (i < 0)
			return text;

		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE);

		StringBuilder b = new StringBuilder(text.length());
		b.append(text, 0, i);

		ByteBuffer bytes = ByteBuffer.allocate(text.length() / 3);
		while (i < text.length())
		{
			char ch = text.charAt(i);
			if (ch == '%' && isPercentEscape(text, i))
			{
				bytes.put((byte) ((hexValue(text.charAt(i + 1)) << 4) | hexValue(text.charAt(i + 2))));
				i += 3;
			}
			else
			{
				flushBytes(decoder, bytes, b);
				b.append(ch);
				++i;
			}
		}
		flushBytes(decoder, bytes, b);

		return b.toString();
	}

	private static boolean isPercentEscape(String text, int i)
	{
		return (i + 2 < text.length()) &&
				(hexValue(text.charAt(i + 1)) >= 0) &&
				(hexValue(text.charAt(i + 2)) >= 0);
	}

	private static int hexValue(char ch)
	{
		if (ch >= '0' && ch <= '9')
			return ch - '0';
		if (ch >= 'a' && ch <= 'f')
			return ch - 'a' + 10;
		if (ch >= 'A' && ch <= 'F')
			return ch - 'A' + 10;
		return -1;
	}

	private static void flushBytes(
			CharsetDecoder decoder,
			ByteBuffer bytes,
			StringBuilder b)
	{
		if (bytes.position() == 0)
			return;

		bytes.flip();
		try
		{
			b.append(decoder.decode(bytes));
		}
		catch (CharacterCodingException e)
		{
			// Cannot happen, malformed input is replaced
			throw new AssertionError(e);
		}
		bytes.clear();
	}

	private static String trimSpaces(String text)
	{
		return trimSpacesPattern.matcher(text).replaceAll("");
	}

	/**
	 * MediaWiki converts all whitespace into single underscores before it
	 * looks up namespace names. Namespaces are registered with spaces, so we
	 * convert into single spaces instead: "User _talk" becomes "User
	 * talk".
	 */
	private static String normalizeNsName(String nsName)
	{
		return spacePlusPattern.matcher(nsName).replaceAll(" ");
	}

	/**
	 * Decodes entity and character references like MediaWiki's
	 * Sanitizer::decodeCharReferences(). References to invalid code points
	 * are replaced with U+FFFD and render the title invalid, just like
	 * MediaWiki rejects titles containing U+FFFD. Unknown entity references
	 * are kept and render the title invalid, too.
	 */
	private static String xmlDecode(ParserConfig config, String text)
	{
		if (text.indexOf('&') < 0)
			return text;

		StringBuilder b = new StringBuilder(text.length());

		int start = 0;
		Matcher m = xmlReferencePattern.matcher(text);
		while (m.find())
		{
			b.append(text, start, m.start());

			String resolved;
			if (m.group(1) != null)
				resolved = config.resolveXmlEntity(m.group(1));
			else if (m.group(2) != null)
				resolved = decodeCharRef(m.group(2), 10);
			else
				resolved = decodeCharRef(m.group(3), 16);

			b.append((resolved != null) ? resolved : m.group());
			start = m.end();
		}
		b.append(text, start, text.length());

		return b.toString();
	}

	private static String decodeCharRef(String digits, int radix)
	{
		int codePoint = -1;
		try
		{
			codePoint = Integer.parseInt(digits, radix);
		}
		catch (NumberFormatException e)
		{
			// Too large
		}

		if (!isValidCodePoint(codePoint))
			return "\uFFFD";

		return new String(Character.toChars(codePoint));
	}

	/**
	 * Like MediaWiki's Sanitizer::validateCodepoint().
	 */
	private static boolean isValidCodePoint(int cp)
	{
		return cp == 0x09 ||
				cp == 0x0A ||
				(cp >= 0x20 && cp <= 0x7E) ||
				(cp >= 0xA0 && cp <= 0xD7FF) ||
				(cp >= 0xE000 && cp <= 0xFFFD) ||
				(cp >= 0x10000 && cp <= 0x10FFFF);
	}

	// =========================================================================

	public String getTitle()
	{
		return title;
	}

	public String getFragment()
	{
		return fragment;
	}

	public String getNamespace()
	{
		return namespace;
	}

	public String getInterwiki()
	{
		return interwiki;
	}

	public boolean isInitialColon()
	{
		return initialColon;
	}
}
