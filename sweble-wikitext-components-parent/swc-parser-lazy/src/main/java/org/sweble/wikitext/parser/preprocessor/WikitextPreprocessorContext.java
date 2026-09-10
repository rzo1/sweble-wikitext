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

package org.sweble.wikitext.parser.preprocessor;

import de.fau.cs.osr.ptk.common.ParserContext;

public class WikitextPreprocessorContext
		extends
			ParserContext
{
	private String tagExtensionName;

	private int templateBraces;

	/**
	 * The number of enclosing nested constructs, see
	 * {@link org.sweble.wikitext.parser.ParserConfig#getMaxNestingDepth()}.
	 * Unlike the other fields it is inherited by child contexts, so it is
	 * restored when a stateful production is left.
	 */
	private int nestingDepth;

	// =========================================================================

	public int getNestingDepth()
	{
		return nestingDepth;
	}

	public void setNestingDepth(int nestingDepth)
	{
		this.nestingDepth = nestingDepth;
	}

	public String getTagExtensionName()
	{
		return tagExtensionName;
	}

	public void setTagExtensionName(String name)
	{
		this.tagExtensionName = name;
	}

	public int getTemplateBraces()
	{
		return templateBraces;
	}

	public void setTemplateBraces(int templateBraces)
	{
		this.templateBraces = templateBraces;
	}

	// =========================================================================

	@Override
	public void clear()
	{
		this.tagExtensionName = null;
		this.templateBraces = 0;
		this.nestingDepth = 0;
	}

	@Override
	public void init(ParserContext parent)
	{
		clear();
		this.nestingDepth = ((WikitextPreprocessorContext) parent).nestingDepth;
	}
}
