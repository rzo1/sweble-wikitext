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

package org.sweble.wikitext.engine.ext.generic;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.sweble.wikitext.engine.config.TagExtensionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;

/**
 * Extension tags commonly available on Wikipedia for which no implementation
 * exists. They are registered as {@link GenericTagExtension}s so that their
 * bodies are not parsed as wikitext.
 */
public class WikipediaTagExtensions
		extends
			TagExtensionGroup
{
	private static final long serialVersionUID = 1L;

	/**
	 * Names of the extension tags in this group. The implemented extension
	 * tags ({@code pre}, {@code nowiki}, {@code math} and {@code ref}) are
	 * registered by their own groups.
	 */
	public static final List<String> TAG_NAMES = Collections.unmodifiableList(Arrays.asList(
			"categorytree",
			"ce",
			"charinsert",
			"chem",
			"gallery",
			"graph",
			"hiero",
			"imagemap",
			"indicator",
			"inputbox",
			"langconvert",
			"mapframe",
			"maplink",
			"poem",
			"references",
			"score",
			"section",
			"source",
			"syntaxhighlight",
			"templatedata",
			"templatestyles",
			"timeline"));

	// =========================================================================

	protected WikipediaTagExtensions(WikiConfig wikiConfig)
	{
		super("Extension - Wikipedia (generic)");
		for (String name : TAG_NAMES)
			addTagExtension(new GenericTagExtension(wikiConfig, name));
	}

	public static WikipediaTagExtensions group(WikiConfig wikiConfig)
	{
		return new WikipediaTagExtensions(wikiConfig);
	}
}
