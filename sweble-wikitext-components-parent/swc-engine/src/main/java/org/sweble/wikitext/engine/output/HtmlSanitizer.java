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
package org.sweble.wikitext.engine.output;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.fau.cs.osr.utils.StringTools;
import de.fau.cs.osr.utils.XmlEntityResolver;

/**
 * Sanitizes HTML elements, attributes and inline CSS that originate from
 * user-supplied wikitext.
 * <p>
 * The rules follow MediaWiki's {@code Sanitizer} class
 * ({@code includes/Parser/Sanitizer.php}):
 * <ul>
 * <li>Only a fixed set of HTML elements may be written in wikitext (see
 * {@link #isAllowedElement(String)}). Callers are expected to render all
 * other elements as escaped text. Unlike MediaWiki, the table sections
 * ({@code tbody}, {@code thead}, {@code tfoot}) and column groups
 * ({@code colgroup}, {@code col}) are allowed as well.</li>
 * <li>Every element has its own list of allowed attributes. Event handlers
 * ({@code on*}) and all other attributes not on the list are dropped.
 * {@code data-*} attributes are allowed unless reserved by MediaWiki.</li>
 * <li>URL-bearing attributes must not use a {@code javascript:},
 * {@code vbscript:} or {@code data:} URL.</li>
 * <li>{@code style} attributes are checked with {@link #checkCss(String)}.</li>
 * </ul>
 * All methods are stateless and thread-safe. The sanitized values are not
 * HTML escaped; they must still be escaped when written into an attribute.
 */
public final class HtmlSanitizer
{
	/** Returned by {@link #checkCss(String)} for dangerous CSS. */
	public static final String INSECURE_CSS_REPLACEMENT = "/* insecure input */";

	/** Returned by {@link #checkCss(String)} for CSS with control chars. */
	public static final String INVALID_CSS_REPLACEMENT = "/* invalid control char */";

	private static final char REPLACEMENT_CHAR = '\uFFFD';

	/**
	 * Tags that must be closed, tags that can be self-closed and tags that can
	 * be nested ($htmlpairsStatic, $htmlsingle and $htmlnest in MediaWiki).
	 */
	private static final Set<String> ALLOWED_ELEMENTS = set(
			// Paired tags
			"b", "bdi", "del", "i", "ins", "u", "font", "big", "small", "sub",
			"sup", "h1", "h2", "h3", "h4", "h5", "h6", "cite", "code", "em", "s",
			"strike", "strong", "tt", "var", "div", "center", "blockquote", "ol",
			"ul", "dl", "table", "caption", "pre", "ruby", "rb", "rp", "rt", "rtc",
			"p", "span", "abbr", "dfn", "kbd", "samp", "data", "time", "mark",
			// Tags that can be self-closed
			"br", "wbr", "hr", "li", "dt", "dd", "meta", "link",
			// Tags that can be nested
			"tr", "td", "th", "q", "bdo",
			// Table sections and column groups. MediaWiki's Sanitizer does not
			// list them because its tidy stage generates them. Sweble's tree
			// builder, however, keeps them as part of the table structure (and
			// the renderer emitted them before). Escaping them would put text
			// into table context and break the table. They carry neither URLs
			// nor scripts and their attributes are still sanitized.
			"tbody", "thead", "tfoot", "colgroup", "col");

	private static final Map<String, Set<String>> ALLOWED_ATTRIBUTES =
			buildAllowedAttributes();

	/** Attributes whose value is a URL that a browser may load or follow. */
	private static final Set<String> URL_ATTRIBUTES = set(
			"href", "src", "poster", "cite");

	/** RDFa and microdata attributes which may carry URIs. */
	private static final Set<String> URI_ATTRIBUTES = set(
			"rel", "rev", "about", "property", "resource", "datatype", "typeof",
			"itemid", "itemprop", "itemref", "itemscope", "itemtype");

	private static final Set<String> UNSAFE_URL_SCHEMES = set(
			"javascript", "vbscript", "data");

	private static final Pattern ATTRIBUTE_NAME =
			Pattern.compile("[^\\s\"'<>/=\\x00-\\x1F\\x7F]+");

	private static final Pattern DATA_ATTRIBUTE =
			Pattern.compile("data-[^:= \\t\\r\\n/>\\x00_\\uFF3F]*", Pattern.CASE_INSENSITIVE);

	private static final Pattern RESERVED_DATA_ATTRIBUTE =
			Pattern.compile("data-(ooui|mw|parsoid).*", Pattern.CASE_INSENSITIVE);

	private static final Pattern XMLNS_ATTRIBUTE =
			Pattern.compile("xmlns:[:A-Z_a-z\\-.0-9]+");

	private static final Pattern EVIL_URI =
			Pattern.compile("(^|\\s|\\*/\\s*)(javascript|vbscript)([^\\w]|$)", Pattern.CASE_INSENSITIVE);

	private static final Pattern URL_SCHEME =
			Pattern.compile("^([A-Za-z][A-Za-z0-9+.\\-]*):");

	private static final Pattern CHAR_REF =
			Pattern.compile("&(?:([A-Za-z][A-Za-z0-9]*)|#([0-9]+)|#[xX]([0-9A-Fa-f]+));");

	/** Ids are truncated to this many characters (T251506). */
	private static final int MAX_ID_LENGTH = 1024;

	private static final Pattern ID_WHITESPACE =
			Pattern.compile("[\\t\\n\\f\\r ]");

	private static final Pattern PERCENT_ESCAPE =
			Pattern.compile("%([0-9A-Fa-f]{2})");

	private static final Pattern CSS_ESCAPE = Pattern.compile(
			"\\\\(?:(\\n|\\r\\n|\\r|\\f)|([0-9A-Fa-f]{1,6})[\\x20\\t\\r\\n\\f]?|(.)|$)",
			Pattern.DOTALL);

	private static final Pattern CSS_SINGLE_COMMENT =
			Pattern.compile("^\\s*/\\*[^*/]*\\*/\\s*$");

	private static final Pattern CSS_CONTROL_CHARS =
			Pattern.compile("[\\x00-\\x08\\x0B\\x0E-\\x1F\\x7F]");

	private static final Pattern CSS_INSECURE = Pattern.compile(
			"expression"
					+ "|accelerator\\s*:"
					+ "|-o-link\\s*:"
					+ "|-o-link-source\\s*:"
					+ "|-o-replace\\s*:"
					+ "|url\\s*\\("
					+ "|src\\s*\\("
					+ "|image\\s*\\("
					+ "|image-set\\s*\\("
					+ "|attr\\s*\\([^)]+[\\s,]+url"
					+ "|-moz-binding"
					+ "|(?<![\\w-])behavior\\s*:"
					+ "|javascript\\s*:"
					+ "|vbscript\\s*:",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Named character references which are decoded before checking CSS and
	 * URLs. The list covers the syntactically relevant characters only.
	 */
	private static final Map<String, String> NAMED_CHAR_REFS = buildNamedCharRefs();

	// =========================================================================

	private HtmlSanitizer()
	{
	}

	// =========================================================================

	/**
	 * Whether the given element may be written in wikitext and rendered as an
	 * HTML element.
	 */
	public static boolean isAllowedElement(String element)
	{
		return element != null && ALLOWED_ELEMENTS.contains(lower(element));
	}

	/**
	 * Whether the given attribute may appear on the given element. Only the
	 * name is checked, see {@link #sanitizeAttributeValue(String, String)} for
	 * the value.
	 */
	public static boolean isAllowedAttribute(String element, String attribute)
	{
		if (element == null || attribute == null)
			return false;

		String attr = lower(attribute);
		if (!ATTRIBUTE_NAME.matcher(attr).matches())
			return false;

		if (XMLNS_ATTRIBUTE.matcher(attr).matches())
			return true;

		if (DATA_ATTRIBUTE.matcher(attr).matches())
			return !isReservedDataAttribute(attr);

		Set<String> allowed = ALLOWED_ATTRIBUTES.get(lower(element));
		return allowed != null && allowed.contains(attr);
	}

	/**
	 * Whether the attribute is a {@code data-*} attribute reserved for
	 * MediaWiki itself (data-ooui, data-mw, data-parsoid).
	 */
	public static boolean isReservedDataAttribute(String attribute)
	{
		return RESERVED_DATA_ATTRIBUTE.matcher(attribute).matches();
	}

	/**
	 * Sanitizes the value of an (allowed) attribute.
	 *
	 * @return The value to use or {@code null} if the attribute has to be
	 *         dropped.
	 */
	public static String sanitizeAttributeValue(String attribute, String value)
	{
		String attr = lower(attribute);
		if (value == null)
			value = "";

		if (XMLNS_ATTRIBUTE.matcher(attr).matches() || URI_ATTRIBUTES.contains(attr))
			return isEvilUri(value) ? null : value;

		if (attr.equals("style"))
			return checkCss(value);

		if (URL_ATTRIBUTES.contains(attr))
			return isSafeUrl(value) ? value : null;

		if (attr.equals("srcset"))
			return isSafeSrcset(value) ? value : null;

		if (attr.equals("tabindex"))
			// Only allow tabindex of 0, which is useful for accessibility.
			return value.equals("0") ? value : null;

		return value;
	}

	/**
	 * Removes all attributes not allowed on the given element and sanitizes
	 * the values of the remaining attributes. Attribute names are converted to
	 * lower case. If an attribute occurs more than once, the last value wins.
	 *
	 * @param element
	 *            The name of the element the attributes belong to.
	 * @param attributes
	 *            Attribute names mapped to their (unescaped) values.
	 * @return A new map with the sanitized attributes in their original order.
	 */
	public static Map<String, String> sanitizeAttributes(
			String element,
			Map<String, String> attributes)
	{
		Map<String, String> out = new LinkedHashMap<String, String>();
		for (Map.Entry<String, String> e : attributes.entrySet())
		{
			if (!isAllowedAttribute(element, e.getKey()))
				continue;

			String name = lower(e.getKey());
			String value = sanitizeAttributeValue(name, e.getValue());
			if (value != null)
				out.put(name, value);
		}

		// itemtype, itemid, itemref don't make sense without itemscope
		if (!out.containsKey("itemscope"))
		{
			out.remove("itemtype");
			out.remove("itemid");
			out.remove("itemref");
		}

		return out;
	}

	/**
	 * Checks element specific constraints on the (sanitized) attributes. Only
	 * {@code meta} and {@code link} have such constraints: they are microdata
	 * elements and need an {@code itemprop} as well as {@code content} or
	 * {@code href}, respectively.
	 */
	public static boolean isValidTag(String element, Map<String, String> attributes)
	{
		String elem = lower(element);
		if (elem.equals("meta"))
			return attributes.containsKey("itemprop") && attributes.containsKey("content");
		if (elem.equals("link"))
			return attributes.containsKey("itemprop") && attributes.containsKey("href");
		return true;
	}

	// =========================================================================

	/**
	 * Whether the given URL is safe to use in a {@code href} or {@code src}
	 * attribute. Relative URLs and all schemes except {@code javascript:},
	 * {@code vbscript:} and {@code data:} are considered safe. Character
	 * references are decoded and whitespace and control characters are
	 * ignored before the scheme is determined, just like browsers do.
	 */
	public static boolean isSafeUrl(String url)
	{
		if (url == null)
			return true;

		String decoded = decodeCharReferences(url);
		StringBuilder b = new StringBuilder(decoded.length());
		for (int i = 0; i < decoded.length(); ++i)
		{
			char ch = decoded.charAt(i);
			if (ch <= 0x20 || ch == 0x7F || Character.isWhitespace(ch))
				continue;
			b.append(ch);
		}

		Matcher m = URL_SCHEME.matcher(b);
		if (!m.find())
			return true;

		return !UNSAFE_URL_SCHEMES.contains(lower(m.group(1)));
	}

	private static boolean isSafeSrcset(String value)
	{
		for (String candidate : value.split(","))
		{
			if (!isSafeUrl(candidate))
				return false;
		}
		return true;
	}

	private static boolean isEvilUri(String value)
	{
		return EVIL_URI.matcher(decodeCharReferences(value)).find();
	}

	// =========================================================================

	/**
	 * Picks apart some CSS and checks it for forbidden or unsafe structures
	 * (like {@code Sanitizer::checkCss} in MediaWiki).
	 *
	 * @return The normalized CSS (character references and escape sequences
	 *         decoded, comments removed) or a CSS comment complaining about the
	 *         input if the input is unsafe. The result must still be escaped
	 *         before it is embedded into HTML.
	 */
	public static String checkCss(String value)
	{
		value = normalizeCss(value);

		if (CSS_CONTROL_CHARS.matcher(value).find()
				|| value.indexOf(REPLACEMENT_CHAR) >= 0)
			return INVALID_CSS_REPLACEMENT;

		if (CSS_INSECURE.matcher(value).find())
			return INSECURE_CSS_REPLACEMENT;

		return value;
	}

	/**
	 * Normalizes CSS into a format we can easily search for hostile input:
	 * decodes character references and escape sequences and removes comments,
	 * unless the entire value is one single comment.
	 */
	public static String normalizeCss(String value)
	{
		if (value == null)
			return "";

		// Character references have to be decoded before escape sequences.
		value = decodeCharReferences(value);
		value = decodeCssEscapes(value);

		// Let the value through if it's nothing but a single comment.
		if (!CSS_SINGLE_COMMENT.matcher(value).matches())
		{
			// Replace comments with spaces (IE gets token splitting wrong)
			value = replaceCssComments(value);

			// Remove anything after an unterminated comment
			int commentPos = value.indexOf("/*");
			if (commentPos >= 0)
				value = value.substring(0, commentPos);
		}

		return value;
	}

	private static String decodeCssEscapes(String value)
	{
		Matcher m = CSS_ESCAPE.matcher(value);
		StringBuffer sb = new StringBuffer(value.length());
		while (m.find())
		{
			String replacement;
			if (m.group(1) != null)
			{
				// Line continuation
				replacement = "";
			}
			else
			{
				String ch;
				if (m.group(2) != null)
					ch = decodeCodePoint(Long.parseLong(m.group(2), 16));
				else if (m.group(3) != null)
					ch = m.group(3);
				else
					ch = "\\";

				if (ch.equals("\n") || ch.equals("\"") || ch.equals("'") || ch.equals("\\"))
				{
					// These characters need to be escaped in strings. Clean up
					// the escape sequence to avoid parsing errors by clients.
					replacement = "\\" + Integer.toHexString(ch.charAt(0)) + " ";
				}
				else
				{
					// Decode unnecessary escape
					replacement = ch;
				}
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static String replaceCssComments(String value)
	{
		StringBuilder b = new StringBuilder(value.length());
		int pos = 0;
		while (true)
		{
			int start = value.indexOf("/*", pos);
			if (start < 0)
				break;
			int end = value.indexOf("*/", start + 2);
			if (end < 0)
				break;
			b.append(value, pos, start).append(' ');
			pos = end + 2;
		}
		b.append(value, pos, value.length());
		return b.toString();
	}

	// =========================================================================

	/**
	 * Decodes numeric character references and a small set of named
	 * character references. Invalid code points are replaced by U+FFFD.
	 * Unknown named references are left untouched.
	 */
	public static String decodeCharReferences(String text)
	{
		return decodeCharReferences(text, null);
	}

	/**
	 * Decodes numeric and named character references like
	 * {@link #decodeCharReferences(String)}. Named references which are not
	 * in the small built-in set are looked up with the given resolver (if
	 * any). Unknown named references are left untouched.
	 */
	public static String decodeCharReferences(String text, XmlEntityResolver resolver)
	{
		if (text == null || text.indexOf('&') < 0)
			return text;

		Matcher m = CHAR_REF.matcher(text);
		StringBuffer sb = new StringBuffer(text.length());
		while (m.find())
		{
			String replacement;
			if (m.group(1) != null)
			{
				replacement = NAMED_CHAR_REFS.get(m.group(1));
				if (replacement == null && resolver != null)
					replacement = resolver.resolveXmlEntity(m.group(1));
				if (replacement == null)
					replacement = m.group();
			}
			else if (m.group(2) != null)
			{
				replacement = decodeCodePoint(parseCodePoint(m.group(2), 10));
			}
			else
			{
				replacement = decodeCodePoint(parseCodePoint(m.group(3), 16));
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Escapes a string for use in an HTML attribute value but leaves existing
	 * character references (like {@code &amp;} or {@code &#34;}) intact. Use
	 * this for values that may already be HTML encoded, like URLs handed out
	 * by an {@link HtmlRendererCallback}. A bare {@code &} and all quotes and
	 * angle brackets are always escaped.
	 */
	public static String escapeAttributeKeepingCharRefs(String text)
	{
		return escapeKeepingCharRefs(text, true);
	}

	/**
	 * Escapes a string for use as HTML text content but leaves existing
	 * character references intact. Only a bare {@code &} and angle brackets
	 * are escaped, quotes are kept. This is what MediaWiki does with the
	 * content of {@code <pre>} and {@code <nowiki>}.
	 */
	public static String escapeTextKeepingCharRefs(String text)
	{
		return escapeKeepingCharRefs(text, false);
	}

	/**
	 * Like {@link #escapeTextKeepingCharRefs(String)} but only valid character
	 * references are kept: Numeric references to code points that may be
	 * written to the output (see {@link #isValidCharReference(long)}) and
	 * known named references. Named references which are not in the small
	 * built-in set are looked up with the given resolver (if any). All other
	 * references are escaped like normal text.
	 */
	public static String escapeTextKeepingValidCharRefs(String text, XmlEntityResolver resolver)
	{
		return escapeKeepingCharRefs(text, false, true, resolver);
	}

	/**
	 * Like {@link #escapeAttributeKeepingCharRefs(String)} but only valid
	 * character references are kept (see
	 * {@link #escapeTextKeepingValidCharRefs(String, XmlEntityResolver)}).
	 */
	public static String escapeAttributeKeepingValidCharRefs(String text, XmlEntityResolver resolver)
	{
		return escapeKeepingCharRefs(text, true, true, resolver);
	}

	private static String escapeKeepingCharRefs(String text, boolean forAttribute)
	{
		return escapeKeepingCharRefs(text, forAttribute, false, null);
	}

	private static String escapeKeepingCharRefs(
			String text,
			boolean forAttribute,
			boolean validOnly,
			XmlEntityResolver resolver)
	{
		if (text == null)
			return "";

		Matcher m = CHAR_REF.matcher(text);
		StringBuilder b = new StringBuilder(text.length() + 16);
		int last = 0;
		while (m.find())
		{
			b.append(escape(text.substring(last, m.start()), forAttribute));
			if (!validOnly || isValidCharReference(m, resolver))
				b.append(m.group());
			else
				b.append(escape(m.group(), forAttribute));
			last = m.end();
		}
		b.append(escape(text.substring(last), forAttribute));
		return b.toString();
	}

	/**
	 * Whether the character reference found by the given {@link #CHAR_REF}
	 * matcher may be written to the output.
	 */
	private static boolean isValidCharReference(Matcher m, XmlEntityResolver resolver)
	{
		if (m.group(1) != null)
		{
			return NAMED_CHAR_REFS.containsKey(m.group(1))
					|| (resolver != null && resolver.resolveXmlEntity(m.group(1)) != null);
		}
		else if (m.group(2) != null)
		{
			return isValidCharReference(parseCodePoint(m.group(2), 10));
		}
		else
		{
			return isValidCharReference(parseCodePoint(m.group(3), 16));
		}
	}

	private static String escape(String text, boolean forAttribute)
	{
		if (forAttribute)
			return StringTools.escHtml(text, true);
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Turns a (decoded) section name or link fragment into the value of an
	 * {@code id} attribute like MediaWiki's
	 * {@code Sanitizer::escapeIdForAttribute()} in {@code html5} mode: the id
	 * is truncated to {@value #MAX_ID_LENGTH} characters and whitespace that
	 * is not allowed in HTML5 ids is replaced by underscores. The result is
	 * not HTML escaped.
	 */
	public static String escapeIdForAttribute(String id)
	{
		if (id.codePointCount(0, id.length()) > MAX_ID_LENGTH)
			id = id.substring(0, id.offsetByCodePoints(0, MAX_ID_LENGTH));
		return ID_WHITESPACE.matcher(id).replaceAll("_");
	}

	/**
	 * Turns a (decoded) section name or link fragment into the fragment of a
	 * link (without the leading {@code #}) like MediaWiki's
	 * {@code Sanitizer::escapeIdForLink()} in {@code html5} mode. Like
	 * {@link #escapeIdForAttribute(String)} but percent signs that look like
	 * percent encoded characters are escaped so that browsers find the id.
	 * The result is not HTML escaped.
	 */
	public static String escapeIdForLink(String id)
	{
		return PERCENT_ESCAPE.matcher(escapeIdForAttribute(id)).replaceAll("%25$1");
	}

	// =========================================================================

	private static long parseCodePoint(String digits, int radix)
	{
		// Avoid overflows, anything this long is not a valid code point anyway
		if (digits.length() > 8)
			return -1;
		return Long.parseLong(digits, radix);
	}

	private static String decodeCodePoint(long cp)
	{
		if (!isValidCodePoint(cp))
			return String.valueOf(REPLACEMENT_CHAR);
		return new String(Character.toChars((int) cp));
	}

	/**
	 * Whether a character reference to the given code point may be written
	 * to the output (like {@code Sanitizer::validateCodepoint} in MediaWiki).
	 * Unlike {@link #decodeCharReferences(String)} this also rejects the
	 * carriage return, DEL and the C1 control characters.
	 */
	public static boolean isValidCharReference(long cp)
	{
		return cp == 0x09
				|| cp == 0x0A
				|| (cp >= 0x20 && cp <= 0x7E)
				|| (cp >= 0xA0 && cp <= 0xD7FF)
				|| (cp >= 0xE000 && cp <= 0xFFFD)
				|| (cp >= 0x10000 && cp <= 0x10FFFF);
	}

	private static boolean isValidCodePoint(long cp)
	{
		return cp == 0x09
				|| cp == 0x0A
				|| cp == 0x0D
				|| (cp >= 0x20 && cp <= 0xD7FF)
				|| (cp >= 0xE000 && cp <= 0xFFFD)
				|| (cp >= 0x10000 && cp <= 0x10FFFF);
	}

	private static String lower(String s)
	{
		return s.toLowerCase(Locale.ROOT);
	}

	private static Set<String> set(String... values)
	{
		return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
	}

	private static Set<String> merge(Set<String> base, String... values)
	{
		Set<String> result = new HashSet<String>(base);
		result.addAll(Arrays.asList(values));
		return Collections.unmodifiableSet(result);
	}

	// =========================================================================

	/**
	 * Allowed attributes per element, see
	 * {@code Sanitizer::setupAttributesAllowedInternal()} in MediaWiki.
	 */
	private static Map<String, Set<String>> buildAllowedAttributes()
	{
		Set<String> common = set(
				// HTML
				"id", "class", "style", "lang", "dir", "title", "tabindex",
				// WAI-ARIA
				"aria-describedby", "aria-flowto", "aria-hidden", "aria-label",
				"aria-labelledby", "aria-level", "aria-owns", "role",
				// RDFa
				"about", "property", "resource", "datatype", "typeof",
				// Microdata
				"itemid", "itemprop", "itemref", "itemscope", "itemtype");

		Set<String> block = merge(common, "align");

		String[] tableAlign = { "align", "valign" };

		String[] tableCell = {
				"abbr", "axis", "headers", "scope", "rowspan", "colspan",
				"nowrap", "width", "height", "bgcolor" };

		Map<String, Set<String>> m = new HashMap<String, Set<String>>();

		// Microdata only, see isValidTag()
		m.put("meta", set("itemprop", "content"));
		m.put("link", set("itemprop", "href", "title"));

		m.put("aside", common);
		for (String h : new String[] { "h1", "h2", "h3", "h4", "h5", "h6" })
			m.put(h, block);

		m.put("p", block);
		m.put("hr", merge(common, "width"));
		m.put("pre", merge(common, "width"));
		m.put("blockquote", merge(common, "cite"));
		m.put("ol", merge(common, "type", "start", "reversed"));
		m.put("ul", merge(common, "type"));
		m.put("li", merge(common, "type", "value"));
		m.put("dl", common);
		m.put("dt", common);
		m.put("dd", common);
		m.put("figure", common);
		m.put("figcaption", common);
		m.put("div", block);

		// <a> is not allowed in wikitext but used by tag hooks, etc.
		m.put("a", merge(common, "href", "rel", "rev"));

		for (String e : new String[] {
				"em", "strong", "small", "s", "cite", "dfn", "abbr", "ruby",
				"rt", "rp", "code", "var", "samp", "kbd", "sub", "sup", "i",
				"b", "u", "mark", "bdi", "bdo", "span", "wbr", "rb", "rtc",
				"strike", "big", "center", "tt", "tbody", "thead", "tfoot" })
			m.put(e, common);

		m.put("q", merge(common, "cite"));
		m.put("data", merge(common, "value"));
		m.put("time", merge(common, "datetime"));
		m.put("br", merge(common, "clear"));
		m.put("ins", merge(common, "cite", "datetime"));
		m.put("del", merge(common, "cite", "datetime"));

		// Embedded content, not allowed in wikitext but used by tag hooks, etc.
		m.put("source", merge(common, "type", "src"));
		m.put("img", merge(common, "alt", "src", "width", "height", "srcset"));
		m.put("video", merge(common, "poster", "controls", "preload", "width", "height"));
		m.put("audio", merge(common, "controls", "preload", "width", "height"));
		m.put("track", merge(common, "type", "src", "srclang", "kind", "label"));
		m.put("math", set("class", "style", "id", "title"));

		// Tables
		m.put("table", merge(common,
				"summary", "width", "border", "frame", "rules", "cellspacing",
				"cellpadding", "align", "bgcolor"));
		m.put("caption", block);
		m.put("colgroup", merge(common, "span"));
		m.put("col", merge(common, "span"));
		m.put("tr", merge(merge(common, "bgcolor"), tableAlign));
		m.put("td", merge(merge(common, tableCell), tableAlign));
		m.put("th", merge(merge(common, tableCell), tableAlign));

		m.put("font", merge(common, "size", "color", "face"));

		return Collections.unmodifiableMap(m);
	}

	private static Map<String, String> buildNamedCharRefs()
	{
		Map<String, String> m = new HashMap<String, String>();
		m.put("amp", "&");
		m.put("lt", "<");
		m.put("gt", ">");
		m.put("quot", "\"");
		m.put("apos", "'");
		m.put("nbsp", "\u00A0");
		m.put("Tab", "\t");
		m.put("NewLine", "\n");
		m.put("colon", ":");
		m.put("semi", ";");
		m.put("comma", ",");
		m.put("period", ".");
		m.put("excl", "!");
		m.put("num", "#");
		m.put("ast", "*");
		m.put("sol", "/");
		m.put("bsol", "\\");
		m.put("lpar", "(");
		m.put("rpar", ")");
		m.put("lsqb", "[");
		m.put("rsqb", "]");
		m.put("lbrace", "{");
		m.put("rbrace", "}");
		return Collections.unmodifiableMap(m);
	}
}
