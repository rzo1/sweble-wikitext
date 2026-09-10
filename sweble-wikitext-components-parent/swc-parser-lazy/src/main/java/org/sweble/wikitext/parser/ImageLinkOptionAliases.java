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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Ids of the image link option magic words understood by the parser and the
 * built-in English aliases which are used whenever a {@link ParserConfig}
 * does not provide (localized) aliases of its own.
 *
 * Parameterized aliases are written the way MediaWiki writes them, with the
 * parameter replaced by {@code $1} (e.g. {@code "$1px"} or
 * {@code "link=$1"}).
 */
public final class ImageLinkOptionAliases
{
	public static final String IMG_THUMBNAIL = "img_thumbnail";

	public static final String IMG_FRAMED = "img_framed";

	public static final String IMG_FRAMELESS = "img_frameless";

	public static final String IMG_LEFT = "img_left";

	public static final String IMG_RIGHT = "img_right";

	public static final String IMG_CENTER = "img_center";

	public static final String IMG_NONE = "img_none";

	public static final String IMG_BASELINE = "img_baseline";

	public static final String IMG_SUB = "img_sub";

	public static final String IMG_SUPER = "img_super";

	public static final String IMG_TOP = "img_top";

	public static final String IMG_TEXT_TOP = "img_text_top";

	public static final String IMG_MIDDLE = "img_middle";

	public static final String IMG_BOTTOM = "img_bottom";

	public static final String IMG_TEXT_BOTTOM = "img_text_bottom";

	public static final String IMG_BORDER = "img_border";

	public static final String IMG_UPRIGHT = "img_upright";

	public static final String IMG_WIDTH = "img_width";

	public static final String IMG_LINK = "img_link";

	public static final String IMG_ALT = "img_alt";

	public static final String IMG_CLASS = "img_class";

	public static final String IMG_LANG = "img_lang";

	public static final String IMG_PAGE = "img_page";

	public static final String IMG_MANUALTHUMB = "img_manualthumb";

	// =========================================================================

	/**
	 * English keyword aliases (see {@code $magicWords} in MediaWiki's
	 * MessagesEn.php). Like all image link option magic words they are
	 * case-sensitive.
	 */
	private static final Map<String, String> DEFAULT_KEYWORDS;

	/**
	 * English parameterized aliases, matched case-sensitively.
	 */
	private static final Map<String, String> DEFAULT_PARAMETERIZED;

	static
	{
		Map<String, String> k = new HashMap<String, String>();
		k.put("thumb", IMG_THUMBNAIL);
		k.put("thumbnail", IMG_THUMBNAIL);
		k.put("frame", IMG_FRAMED);
		k.put("framed", IMG_FRAMED);
		k.put("enframed", IMG_FRAMED);
		k.put("frameless", IMG_FRAMELESS);
		k.put("left", IMG_LEFT);
		k.put("right", IMG_RIGHT);
		k.put("center", IMG_CENTER);
		k.put("centre", IMG_CENTER);
		k.put("none", IMG_NONE);
		k.put("baseline", IMG_BASELINE);
		k.put("sub", IMG_SUB);
		k.put("super", IMG_SUPER);
		k.put("sup", IMG_SUPER);
		k.put("top", IMG_TOP);
		k.put("text-top", IMG_TEXT_TOP);
		k.put("middle", IMG_MIDDLE);
		k.put("bottom", IMG_BOTTOM);
		k.put("text-bottom", IMG_TEXT_BOTTOM);
		k.put("border", IMG_BORDER);
		k.put("upright", IMG_UPRIGHT);
		DEFAULT_KEYWORDS = Collections.unmodifiableMap(k);

		Map<String, String> p = new HashMap<String, String>();
		p.put("$1px", IMG_WIDTH);
		p.put("link=$1", IMG_LINK);
		p.put("alt=$1", IMG_ALT);
		p.put("upright=$1", IMG_UPRIGHT);
		p.put("upright $1", IMG_UPRIGHT);
		p.put("class=$1", IMG_CLASS);
		p.put("lang=$1", IMG_LANG);
		p.put("page=$1", IMG_PAGE);
		p.put("page $1", IMG_PAGE);
		p.put("thumbnail=$1", IMG_MANUALTHUMB);
		p.put("thumb=$1", IMG_MANUALTHUMB);
		DEFAULT_PARAMETERIZED = Collections.unmodifiableMap(p);
	}

	// =========================================================================

	private ImageLinkOptionAliases()
	{
	}

	/**
	 * Resolves one of the built-in English image link option aliases to the
	 * id of its magic word.
	 *
	 * @return The magic word id or {@code null} if the given string is not a
	 *         built-in alias.
	 */
	public static String getDefaultId(String alias)
	{
		if (alias == null)
			throw new NullPointerException();

		String id = DEFAULT_PARAMETERIZED.get(alias);
		if (id == null)
			id = DEFAULT_KEYWORDS.get(alias.trim());
		return id;
	}

	/**
	 * Whether the given magic word id denotes an image link option which is
	 * given as a plain keyword without a parameter.
	 */
	public static boolean isKeywordId(String id)
	{
		return id != null && DEFAULT_KEYWORDS.containsValue(id);
	}
}
