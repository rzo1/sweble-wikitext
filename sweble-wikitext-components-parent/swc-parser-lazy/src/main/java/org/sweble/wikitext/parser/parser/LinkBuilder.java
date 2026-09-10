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

import java.util.ArrayList;
import java.util.ListIterator;

import org.sweble.wikitext.parser.ImageLinkOptionAliases;
import org.sweble.wikitext.parser.ParserConfig;
import org.sweble.wikitext.parser.nodes.WtImageLink;
import org.sweble.wikitext.parser.nodes.WtImageLink.ImageHorizAlign;
import org.sweble.wikitext.parser.nodes.WtImageLink.ImageVertAlign;
import org.sweble.wikitext.parser.nodes.WtImageLink.ImageViewFormat;
import org.sweble.wikitext.parser.nodes.WtInternalLink;
import org.sweble.wikitext.parser.nodes.WtLinkOptionGarbage;
import org.sweble.wikitext.parser.nodes.WtLinkOptionKeyword;
import org.sweble.wikitext.parser.nodes.WtLinkOptionResize;
import org.sweble.wikitext.parser.nodes.WtLinkOptions;
import org.sweble.wikitext.parser.nodes.WtLinkTitle;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtPageName;

import de.fau.cs.osr.ptk.common.Warning;

public class LinkBuilder
{
	private final WtPageName target;

	// -- format

	private int width;

	private int height;

	private boolean upright;

	private ImageHorizAlign hAlign;

	private ImageVertAlign vAlign;

	private ImageViewFormat format;

	private boolean border;

	// -- warnings picked up along the way

	private ArrayList<Warning> warnings;

	// -- internal state

	private final ParserConfig parserConfig;

	private final LinkType targetType;

	// =========================================================================

	public LinkBuilder(ParserConfig parserConfig, WtPageName target)
	{
		this.target = target;
		this.parserConfig = parserConfig;

		LinkType targetType = LinkType.INVALID;
		if (target.isResolved())
			targetType = parserConfig.classifyTarget(target.getAsString());
		this.targetType = targetType;

		this.width = -1;
		this.height = -1;
		this.upright = false;
		this.hAlign = null;
		this.vAlign = null;
		this.format = null;
		this.border = false;
	}

	// =========================================================================

	public boolean isImageTarget()
	{
		return targetType == LinkType.IMAGE;
	}

	public boolean isValidTarget()
	{
		return targetType != LinkType.INVALID;
	}

	public boolean isKeyword(String keyword)
	{
		return ImageLinkOptionAliases.isKeywordId(
				resolveOptionId(keyword));
	}

	/**
	 * @param suffix
	 *            The unit following the digits of a size option (e.g.
	 *            {@code "px"}).
	 */
	public boolean isWidthSuffix(String suffix)
	{
		return ImageLinkOptionAliases.IMG_WIDTH.equals(
				resolveOptionId("$1" + suffix));
	}

	/**
	 * @param name
	 *            The name of a name/value option including the trailing
	 *            {@code '='} (e.g. {@code "link="}).
	 */
	public boolean isLinkTargetOptionName(String name)
	{
		return ImageLinkOptionAliases.IMG_LINK.equals(
				resolveOptionId(name + "$1"));
	}

	/**
	 * @param name
	 *            The name of a name/value option including the trailing
	 *            {@code '='} (e.g. {@code "alt="}).
	 */
	public boolean isAltOptionName(String name)
	{
		return ImageLinkOptionAliases.IMG_ALT.equals(
				resolveOptionId(name + "$1"));
	}

	/**
	 * Resolves an image link option alias using the parser configuration and
	 * falls back to the English aliases.
	 */
	private String resolveOptionId(String alias)
	{
		String id = parserConfig.getImageLinkOptionId(alias);
		return (id != null) ? id : ImageLinkOptionAliases.getDefaultId(alias);
	}

	// =========================================================================

	public void addOption(WtLinkOptionKeyword option)
	{
		String id = resolveOptionId(option.getKeyword());
		if (id == null)
			return;

		switch (id)
		{
			case ImageLinkOptionAliases.IMG_THUMBNAIL:
				addFormat(ImageViewFormat.THUMBNAIL);
				break;
			case ImageLinkOptionAliases.IMG_FRAMED:
				addFormat(ImageViewFormat.FRAME);
				break;
			case ImageLinkOptionAliases.IMG_FRAMELESS:
				addFormat(ImageViewFormat.FRAMELESS);
				break;
			case ImageLinkOptionAliases.IMG_LEFT:
				hAlign = ImageHorizAlign.LEFT;
				break;
			case ImageLinkOptionAliases.IMG_RIGHT:
				hAlign = ImageHorizAlign.RIGHT;
				break;
			case ImageLinkOptionAliases.IMG_CENTER:
				hAlign = ImageHorizAlign.CENTER;
				break;
			case ImageLinkOptionAliases.IMG_NONE:
				hAlign = ImageHorizAlign.NONE;
				break;
			case ImageLinkOptionAliases.IMG_BASELINE:
				vAlign = ImageVertAlign.BASELINE;
				break;
			case ImageLinkOptionAliases.IMG_SUB:
				vAlign = ImageVertAlign.SUB;
				break;
			case ImageLinkOptionAliases.IMG_SUPER:
				vAlign = ImageVertAlign.SUPER;
				break;
			case ImageLinkOptionAliases.IMG_TOP:
				vAlign = ImageVertAlign.TOP;
				break;
			case ImageLinkOptionAliases.IMG_TEXT_TOP:
				vAlign = ImageVertAlign.TEXT_TOP;
				break;
			case ImageLinkOptionAliases.IMG_MIDDLE:
				vAlign = ImageVertAlign.MIDDLE;
				break;
			case ImageLinkOptionAliases.IMG_BOTTOM:
				vAlign = ImageVertAlign.BOTTOM;
				break;
			case ImageLinkOptionAliases.IMG_TEXT_BOTTOM:
				vAlign = ImageVertAlign.TEXT_BOTTOM;
				break;
			case ImageLinkOptionAliases.IMG_BORDER:
				border = true;
				break;
			case ImageLinkOptionAliases.IMG_UPRIGHT:
				upright = true;
				break;
			default:
				break;
		}
	}

	private void addFormat(ImageViewFormat f)
	{
		format = (format == null) ? f : format.combine(f);
	}

	public void addOption(WtLinkOptionResize option)
	{
		int width = option.getWidth();
		if (width >= 0)
			this.width = width;

		int height = option.getHeight();
		if (height >= 0)
			this.height = height;
	}

	public void addWarning(Warning warning)
	{
		if (warnings == null)
			warnings = new ArrayList<Warning>();
		warnings.add(warning);
	}

	// =========================================================================

	public WtNode build(WtLinkOptions options, String postfix)
	{
		WtLinkTitle title = findTitle(options);

		if (this.targetType == LinkType.IMAGE)
		{
			if (hAlign == null)
				hAlign = ImageHorizAlign.UNSPECIFIED;

			if (vAlign == null)
				vAlign = ImageVertAlign.MIDDLE;

			if (format == null)
				format = ImageViewFormat.UNRESTRAINED;

			WtImageLink result = parserConfig.getNodeFactory().img(
					target,
					options,
					format,
					border,
					hAlign,
					vAlign,
					width,
					height,
					upright);

			/*
			if (title != null)
				result.setTitle(title);
			*/

			finish(result);
			return result;
		}
		else
		{
			if (postfix == null)
				postfix = "";

			WtInternalLink result = parserConfig.getNodeFactory().intLink(
					"",
					target,
					postfix);

			if (title != null)
				result.setTitle(title);

			finish(result);
			return result;
		}
	}

	/**
	 * Converts all but the last title option node into garbage nodes (since
	 * only the last is really considered). The last node becomes the title node
	 * and is returned. May return null if there are no title nodes at all.
	 */
	private WtLinkTitle findTitle(WtLinkOptions options)
	{
		ListIterator<WtNode> i = options.listIterator(options.size());
		WtLinkTitle title = null;
		while (i.hasPrevious())
		{
			WtNode n = i.previous();
			if (n.getNodeType() == WtNode.NT_LINK_TITLE)
			{
				if (title == null)
				{
					title = (WtLinkTitle) n;
				}
				else
				{
					WtLinkOptionGarbage garbage =
							parserConfig.getNodeFactory().loGarbage(
									parserConfig.getNodeFactory().list());
					garbage.exchange((WtLinkTitle) n);
					garbage.setRtd(n.getRtd());
					i.set(garbage);
				}
			}
		}
		return title;
	}

	public void finish(WtNode n)
	{
		if (warnings != null && !warnings.isEmpty())
			n.setAttribute("warnings", warnings);
	}

	// =========================================================================

	public static enum LinkType
	{
		INVALID,
		PAGE,
		IMAGE,
	}
}
