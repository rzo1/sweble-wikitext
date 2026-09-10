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

package org.sweble.wikitext.engine.config;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlType;

/**
 * How the titles of the pages in a namespace are normalized, like the
 * {@code case} attribute of the namespaces in MediaWiki's siteinfo
 * (MediaWiki's $wgCapitalLinks and $wgCapitalLinkOverrides).
 */
@XmlType(name = "namespaceCase")
@XmlEnum
public enum NamespaceCase
{
	/**
	 * The first letter of a title is converted to upper case, e.g.
	 * {@code [[foo]]} links to the page "Foo". This is the default.
	 */
	@XmlEnumValue("first-letter")
	FIRST_LETTER("first-letter"),

	/**
	 * Titles are case-sensitive, e.g. {@code [[foo]]} and {@code [[Foo]]}
	 * link to different pages (e.g. on Wiktionary).
	 */
	@XmlEnumValue("case-sensitive")
	CASE_SENSITIVE("case-sensitive");

	// =========================================================================

	private final String value;

	private NamespaceCase(String value)
	{
		this.value = value;
	}

	/**
	 * Returns the name of the setting as used by MediaWiki's siteinfo and in
	 * the configuration XML, e.g. "first-letter".
	 */
	public String getValue()
	{
		return value;
	}

	/**
	 * Returns the setting with the given name as reported by MediaWiki's
	 * siteinfo (e.g. "case-sensitive") or null if the name is unknown.
	 */
	public static NamespaceCase fromValue(String value)
	{
		for (NamespaceCase c : values())
		{
			if (c.value.equals(value))
				return c;
		}
		return null;
	}
}
