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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlRootElement(
		name = "EngineConfig",
		namespace = "org.sweble.wikitext.engine")
@XmlType(propOrder = {
		"trimTransparentBeforeParsing",
		"maxTemplateDepth",
		"maxPostExpandIncludeSize" })
@XmlAccessorType(XmlAccessType.NONE)
public class EngineConfigImpl
		implements
			EngineConfig
{
	/**
	 * MediaWiki's default for {@code $wgMaxTemplateDepth}.
	 */
	public static final int DEFAULT_MAX_TEMPLATE_DEPTH = 40;

	/**
	 * MediaWiki's default for {@code $wgMaxArticleSize} (2048 KiB).
	 */
	public static final long DEFAULT_MAX_POST_EXPAND_INCLUDE_SIZE = 2048L * 1024L;

	// =========================================================================

	@XmlElement()
	private boolean trimTransparentBeforeParsing;

	@XmlElement()
	private int maxTemplateDepth = DEFAULT_MAX_TEMPLATE_DEPTH;

	@XmlElement()
	private long maxPostExpandIncludeSize = DEFAULT_MAX_POST_EXPAND_INCLUDE_SIZE;

	// =========================================================================

	@Override
	public boolean isTrimTransparentBeforeParsing()
	{
		return trimTransparentBeforeParsing;
	}

	public void setTrimTransparentBeforeParsing(
			boolean trimTransparentBeforeParsing)
	{
		this.trimTransparentBeforeParsing = trimTransparentBeforeParsing;
	}

	@Override
	public int getMaxTemplateDepth()
	{
		return maxTemplateDepth;
	}

	public void setMaxTemplateDepth(int maxTemplateDepth)
	{
		if (maxTemplateDepth < 0)
			throw new IllegalArgumentException("maxTemplateDepth must not be negative: " + maxTemplateDepth);
		this.maxTemplateDepth = maxTemplateDepth;
	}

	@Override
	public long getMaxPostExpandIncludeSize()
	{
		return maxPostExpandIncludeSize;
	}

	public void setMaxPostExpandIncludeSize(long maxPostExpandIncludeSize)
	{
		if (maxPostExpandIncludeSize < 0)
			throw new IllegalArgumentException("maxPostExpandIncludeSize must not be negative: " + maxPostExpandIncludeSize);
		this.maxPostExpandIncludeSize = maxPostExpandIncludeSize;
	}

	// =========================================================================

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + (trimTransparentBeforeParsing ? 1231 : 1237);
		result = prime * result + maxTemplateDepth;
		result = prime * result + (int) (maxPostExpandIncludeSize ^ (maxPostExpandIncludeSize >>> 32));
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
		EngineConfigImpl other = (EngineConfigImpl) obj;
		if (trimTransparentBeforeParsing != other.trimTransparentBeforeParsing)
			return false;
		if (maxTemplateDepth != other.maxTemplateDepth)
			return false;
		if (maxPostExpandIncludeSize != other.maxPostExpandIncludeSize)
			return false;
		return true;
	}
}
