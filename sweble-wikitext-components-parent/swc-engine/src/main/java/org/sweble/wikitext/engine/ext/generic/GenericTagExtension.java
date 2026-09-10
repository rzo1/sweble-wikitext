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

import java.util.Map;

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.TagExtensionBase;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtNodeList;
import org.sweble.wikitext.parser.nodes.WtTagExtension;
import org.sweble.wikitext.parser.nodes.WtTagExtensionBody;

/**
 * A tag extension for which no implementation is available (e.g.
 * {@code <syntaxhighlight>} or {@code <gallery>}).
 * <p>
 * Registering such a tag makes the parser treat it as tag extension, so its
 * body is not parsed as wikitext. Expanding the tag extension leaves the
 * {@link WtTagExtension} node untouched and thus keeps the raw body.
 */
public final class GenericTagExtension
		extends
			TagExtensionBase
{
	private static final long serialVersionUID = 1L;

	/**
	 * For un-marshaling only.
	 */
	public GenericTagExtension(String id)
	{
		super(id);
	}

	public GenericTagExtension(WikiConfig wikiConfig, String id)
	{
		super(wikiConfig, id);
	}

	@Override
	public WtNode invoke(
			ExpansionFrame frame,
			WtTagExtension tagExt,
			Map<String, WtNodeList> attrs,
			WtTagExtensionBody body)
	{
		return tagExt;
	}
}
