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

package org.sweble.wikitext.engine.config;

import java.io.Serializable;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(
		name = "alias",
		propOrder = { "id", "caseSensitive", "aliases" })
public class I18nAliasImpl
		implements
			I18nAlias,
			Serializable,
			Comparable<I18nAlias>
{
	private static final long serialVersionUID = 1L;

	private String id;

	private Boolean caseSensitive;

	private Set<String> aliases;

	// =========================================================================

	/**
	 * Only for de-serialization, not part of public API
	 */
	protected I18nAliasImpl()
	{
	}

	public I18nAliasImpl(
			String id,
			boolean caseSensitive,
			Collection<String> aliases)
	{
		setId(id);
		setCaseSensitive(caseSensitive);
		setAliases(aliases);
	}

	// =========================================================================

	@Override
	@XmlAttribute
	public String getId()
	{
		return id;
	}

	/**
	 * Only for de-serialization, not part of public API
	 */
	public void setId(String id)
	{
		if (id == null)
			throw new IllegalArgumentException();
		if (this.id != null)
			throw new UnsupportedOperationException();
		this.id = id;
	}

	@Override
	@XmlAttribute
	public boolean isCaseSensitive()
	{
		return caseSensitive;
	}

	/**
	 * Only for de-serialization, not part of public API
	 */
	public void setCaseSensitive(boolean caseSensitive)
	{
		if (this.caseSensitive != null)
			throw new UnsupportedOperationException();
		this.caseSensitive = caseSensitive;
	}

	@Override
	@XmlElement(name = "alias")
	public Set<String> getAliases()
	{
		if (aliases == null)
			aliases = newAliasSet();
		// Cannot return immutable aliases set since de-serialization uses this
		// method's return value to add aliases to the set.
		return aliases;
	}

	/**
	 * Only for de-serialization, not part of public API
	 */
	public void setAliases(Collection<String> aliases)
	{
		if (aliases == null)
			throw new IllegalArgumentException();
		Set<String> newAliases = newAliasSet();
		newAliases.addAll(aliases);
		this.aliases = newAliases;
	}

	/**
	 * JAXB adds the aliases to the set returned by getAliases(), which might
	 * have been created before the case sensitivity was known. Rebuild the
	 * set with the matching comparator.
	 */
	@SuppressWarnings("unused")
	private void afterUnmarshal(Unmarshaller unmarshaller, Object parent)
	{
		setAliases(getAliases());
	}

	private Set<String> newAliasSet()
	{
		// Both orders are serializable, unlike an anonymous comparator
		if (caseSensitive != null && caseSensitive)
			return new TreeSet<String>();
		return new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
	}

	@Override
	public boolean hasAlias(String alias)
	{
		return aliases.contains(alias);
	}

	// =========================================================================

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((aliases == null) ? 0 : aliases.hashCode());
		result = prime * result + ((caseSensitive == null) ? 0 : caseSensitive.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		I18nAliasImpl other = (I18nAliasImpl) obj;
		if (id == null)
		{
			if (other.id != null)
				return false;
		}
		else if (!id.equals(other.id))
			return false;
		if (caseSensitive == null)
		{
			if (other.caseSensitive != null)
				return false;
		}
		else if (!caseSensitive.equals(other.caseSensitive))
			return false;
		// Compare the names exactly: A case-insensitive set considers
		// "#REDIRECT" and "#redirect" equal although their hash codes differ.
		if (aliases == null)
		{
			if (other.aliases != null)
				return false;
		}
		else if (other.aliases == null || ConfigComparisons.compare(aliases, other.aliases) != 0)
			return false;
		return true;
	}

	// =========================================================================

	@Override
	public String toString()
	{
		return "I18nAliasImpl [name=" + id + ", caseSensitive=" + caseSensitive + ", aliases=" + aliases + "]";
	}

	// =========================================================================

	/**
	 * Orders by id. Aliases with the same id are ordered by their case
	 * sensitivity and names, which makes the order consistent with
	 * {@link #equals(Object)}.
	 */
	@Override
	public int compareTo(I18nAlias o)
	{
		int result = ConfigComparisons.compare(this.id, o.getId());
		if (result != 0)
			return result;
		result = Boolean.compare(isCaseSensitive(), o.isCaseSensitive());
		if (result != 0)
			return result;
		return ConfigComparisons.compare(this.aliases, o.getAliases());
	}
}
