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

import de.fau.cs.osr.utils.StringTools;
import de.fau.cs.osr.utils.visitor.VisitingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.*;
import org.sweble.wikitext.engine.utils.EngineAstTextUtils;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.parser.WtRtData;
import org.sweble.wikitext.parser.nodes.*;
import org.sweble.wikitext.parser.nodes.WtImageLink.ImageHorizAlign;
import org.sweble.wikitext.parser.nodes.WtImageLink.ImageViewFormat;
import org.sweble.wikitext.parser.parser.LinkTargetException;
import org.sweble.wikitext.parser.utils.StringConversionException;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

import java.io.StringWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class HtmlRenderer
		extends
			HtmlRendererBase
		implements
			CompleteEngineVisitorNoReturn
{
	// Fix #62: Counter for sequential number for untitled external links
	private long untitledLinkCounter = 1L;

	// Fix #89: Lower case (ASCII only) versions of the heading ids used so far
	private final Set<String> headingIds = new HashSet<String>();

	// =====================================================================

	@Override
	protected WtNode before(WtNode node)
	{
		untitledLinkCounter = 1L;
		headingIds.clear();
		return super.before(node);
	}

	// =====================================================================

	@Override
	public void visit(EngProcessedPage n)
	{
		dispatch(n.getPage());
	}

	@Override
	public void visit(EngNowiki n)
	{
		printNowiki(n.getContent());
	}

	public void visit(EngPage n)
	{
		iterate(n);
	}

	@Override
	public void visit(EngSoftErrorNode n)
	{
		visit((WtXmlElement) n);
	}

	@Override
	public void visit(WtBody n)
	{
		iterate(n);
	}

	public void visit(WtBold n)
	{
		p.indentAtBol("<b>");
		p.incIndent();
		iterate(n);
		p.decIndent();
		p.indentAtBol("</b>");
	}

	public void visit(WtDefinitionList n)
	{
		p.indentln("<dl>");
		p.incIndent();
		iterate(n);
		p.decIndent();
		p.indentln("</dl>");
	}

	public void visit(WtDefinitionListDef n)
	{
		p.indentln("<dd>");
		p.incIndent();
		iterate(n);
		p.decIndent();
		p.indentln("</dd>");
	}

	public void visit(WtDefinitionListTerm n)
	{
		p.indentln("<dt>");
		p.incIndent();
		iterate(n);
		p.decIndent();
		p.indentln("</dt>");
	}

	public void visit(WtExternalLink n)
	{
		if (n.hasTitle())
		{
			p.indentAtBol();

			pt("<a rel=\"nofollow\" class=\"external text\" href=\"%s\">%!</a>",
					escAttrKeepCharRefs(callback.makeUrl(n.getTarget())),
					n.getTitle());
		}
		else
		{
			// Fix #62: Use sequential number if the title is missing
			long seqNumber = untitledLinkCounter++;
			pt("<a rel=\"nofollow\" class=\"external autonumber\" href=\"%s\">[" + seqNumber + "]</a>",
					escAttrKeepCharRefs(callback.makeUrl(n.getTarget())));
		}
	}

	@Override
	public void visit(WtHeading n)
	{
		// We handle this case in WtSection and don't dispatch to the heading.
		throw new AssertionError();
	}

	@Override
	public void visit(WtHorizontalRule n)
	{
		p.indentAtBol("<hr />");
	}

	@Override
	public void visit(WtIgnored n)
	{
		// Well, ignore it ...
	}

	@Override
	public void visit(WtIllegalCodePoint n)
	{
		p.indentAtBol();

		final String cp = n.getCodePoint();
		for (int i = 0; i < cp.length(); ++i)
			pf("&amp;#%d;", (int) cp.charAt(i));
	}

	public void visit(WtImageLink n)
	{
		if (!n.getTarget().isResolved())
		{
			printAsWikitext(n);
			return;
		}

		PageTitle target;
		try
		{
			target = PageTitle.make(wikiConfig, n.getTarget().getAsString());
		}
		catch (LinkTargetException e)
		{
			throw new VisitingException(e);
		}

		int imgWidth = n.getWidth();
		int imgHeight = n.getHeight();

		switch (n.getFormat())
		{
			case THUMBNAIL: // FALL THROUGH
			case FRAMELESS:
				if (imgWidth <= 0)
					imgWidth = 180;
				break;
			case FRAME:
				// Like MediaWiki: framed images are never scaled
				imgWidth = -1;
				imgHeight = -1;
				break;
			default:
				break;
		}

		if (n.getUpright() && n.getFormat() != ImageViewFormat.FRAME)
		{
			imgWidth = 140;
			imgHeight = -1;
		}

		MediaInfo info;
		try
		{
			info = callback.getMediaInfo(
					target.getNormalizedFullTitle(),
					imgWidth,
					imgHeight);
		}
		catch (Exception e)
		{
			throw new VisitingException(e);
		}

		boolean exists = (info != null && info.getImgUrl() != null);

		boolean isImage = !target.getTitle().endsWith(".ogg");

		if (exists && imgHeight > 0 && info.getImgWidth() > 0 && info.getImgHeight() > 0)
		{
			// Like MediaWiki: Without a width the width of the file is used.
			// The width is then reduced to fit the requested height.
			int srcWidth = info.getImgWidth();
			int srcHeight = info.getImgHeight();
			int altWidth = (imgWidth > 0) ? imgWidth : srcWidth;
			if ((long) altWidth * srcHeight > (long) imgHeight * srcWidth)
				altWidth = fitBoxWidth(srcWidth, srcHeight, imgHeight);

			if (altWidth != imgWidth)
			{
				imgWidth = altWidth;
				try
				{
					info = callback.getMediaInfo(
							target.getNormalizedFullTitle(),
							imgWidth,
							imgHeight);
				}
				catch (Exception e)
				{
					throw new VisitingException(e);
				}
				exists = (info != null && info.getImgUrl() != null);
			}
		}

		boolean scaled = imgWidth > 0 || imgHeight > 0;

		String imgUrl = null;
		if (exists)
		{
			imgUrl = info.getImgUrl();
			if (scaled && info.getThumbUrl() != null)
				imgUrl = info.getThumbUrl();
		}

		String aClasses = "";
		String imgClasses = "";

		switch (n.getFormat())
		{
			case THUMBNAIL: // FALL THROUGH
			case FRAME:
				imgClasses += " thumbimage";
				break;
			default:
				break;
		}

		if (n.getBorder())
			imgClasses += " thumbborder";

		// -- does the image link something? --

		WtUrl linkUrl = null;
		PageTitle linkTarget = target;
		switch (n.getLink().getTargetType())
		{
			case NO_LINK:
				linkTarget = null;
				break;
			case PAGE:
			{
				WtPageName pageName = (WtPageName) n.getLink().getTarget();
				if (pageName.isResolved())
				{
					try
					{
						linkTarget = PageTitle.make(wikiConfig, pageName.getAsString());
					}
					catch (LinkTargetException e)
					{
						throw new VisitingException(e);
					}
				}
				else
				{
					linkTarget = null;
				}
				break;
			}
			case URL:
				linkTarget = null;
				linkUrl = (WtUrl) n.getLink().getTarget();
				break;
			case DEFAULT:
				if (exists && isImage)
					aClasses += " image";
				break;
		}

		// -- string caption --

		String strCaption = null;
		if (n.hasTitle())
			strCaption = makeImageCaption(n);

		// -- <img> alt --

		String alt = null;
		if (n.hasAlt())
			alt = makeImageAltText(n);

		// -- <a> classes

		if (!aClasses.isEmpty())
			aClasses = String.format(" class=\"%s\"", aClasses.trim());

		// -- <a> title --

		String aTitle = "";
		if (n.getFormat() != ImageViewFormat.FRAMELESS)
		{
			if (strCaption != null)
			{
				// Already escaped by the SafeLinkTitlePrinter
				aTitle = escAttrKeepCharRefs(strCaption);
			}
			else if (linkTarget != null)
			{
				aTitle = esc(makeImageTitle(n, linkTarget), true);
			}
			else if (linkUrl != null)
			{
				aTitle = escAttrKeepCharRefs(callback.makeUrl(linkUrl));
			}
		}
		if (!aTitle.isEmpty())
			aTitle = String.format(" title=\"%s\"", aTitle);

		// -- width & height --

		int width = -1;
		int height = -1;

		if (exists)
		{
			width = scaled ? info.getThumbWidth() : info.getImgWidth();

			height = scaled ? info.getThumbHeight() : info.getImgHeight();
		}
		else
			width = 180;

		// -- generate html --

		boolean framed = n.getFormat() == ImageViewFormat.THUMBNAIL ||
				n.getFormat() == ImageViewFormat.FRAME;

		boolean hasThumbFrame = isImage && framed;

		// Like MediaWiki: Centered images are wrapped in a "center" div and
		// otherwise treated like images with the alignment "none".
		ImageHorizAlign hAlign = n.getHAlign();
		boolean centered = (hAlign == ImageHorizAlign.CENTER);
		if (centered)
		{
			hAlign = ImageHorizAlign.NONE;
			p.indentln("<div class=\"center\">");
			p.incIndent();
		}

		boolean hasFloat = !hasThumbFrame && hAlign != ImageHorizAlign.UNSPECIFIED;

		if (hasThumbFrame)
		{
			String align;
			switch (hAlign)
			{
				case LEFT:
					align = "tleft";
					break;
				case NONE:
					align = "tnone";
					break;
				case RIGHT: // FALL THROUGH
				default:
					align = "tright";
					break;
			}

			p.indentln(String.format("<div class=\"thumb %s\">", align));
			p.incIndent();
			p.indentln(String.format("<div class=\"thumbinner\" style=\"width:%dpx;\">", width + 2));
			p.incIndent();

			aTitle = "";
			if (!exists)
				aTitle = String.format(" title=\"%s\"", esc(makeImageTitle(n, target), true));
		}
		else
		{
			if (hasFloat)
			{
				String align;
				switch (hAlign)
				{
					case LEFT:
						align = "floatleft";
						break;
					case RIGHT:
						align = "floatright";
						break;
					case NONE: // FALL THROUGH
					default:
						align = "floatnone";
						break;
				}

				p.indentln(String.format("<div class=\"%s\">", align));
				p.incIndent();
			}

			if (alt == null)
				alt = strCaption;
		}

		if (alt == null)
			alt = "";

		p.indentAtBol();
		if (linkTarget != null || linkUrl != null)
		{
			pf("<a href=\"%s\"%s%s>",
					escAttrKeepCharRefs(linkTarget != null ? callback.makeUrl(linkTarget) : callback.makeUrl(linkUrl)),
					aClasses,
					aTitle);
		}

		if (!imgClasses.isEmpty())
			imgClasses = String.format(" class=\"%s\"", imgClasses.trim());

		if (exists)
		{
			if (isImage)
			{
				pt("<img alt=\"%s\" src=\"%s\" width=\"%d\" height=\"%d\"%s />",
						escAttrKeepCharRefs(alt.trim()),
						escAttrKeepCharRefs(imgUrl),
						width,
						height,
						imgClasses);
			}
			else
			{
				p.print(esc(makeImageTitle(n, target)));
			}
		}
		else
		{
			p.print(esc(makeImageTitle(n, target)));
		}

		if (linkTarget != null || linkUrl != null)
			p.print("</a>");

		if (framed)
		{
			// Like MediaWiki: Framed images have no magnify icon
			if (exists && n.getFormat() == ImageViewFormat.THUMBNAIL)
			{
				p.indentln("<div class=\"thumbcaption\">");
				p.incIndent();
				p.indentln("<div class=\"magnify\">");
				p.incIndent();
				p.indent();
				// The magnify icon always links to the file description page
				pf("<a href=\"%s\" class=\"internal\" title=\"Enlarge\"><img src=\"/mediawiki/skins/common/images/magnify-clip.png\" width=\"15\" height=\"11\" alt=\"\" /></a>",
						escAttrKeepCharRefs(callback.makeUrl(target)));
				p.decIndent();
				p.indentln("</div>");
				dispatch(n.getTitle());
				p.decIndent();
				p.indentln("</div>");
			}
			else
			{
				p.indent();
				pt("<div class=\"thumbcaption\">%!</div>", n.getTitle());
			}
		}

		if (hasThumbFrame)
		{
			p.decIndent();
			p.indentln("</div>");
			p.decIndent();
			p.indentln("</div>");
		}
		else if (hasFloat)
		{
			p.decIndent();
			p.indentln("</div>");
		}

		if (centered)
		{
			p.decIndent();
			p.indentln("</div>");
		}
	}

	/**
	 * The width of an image of the given size scaled down to the given height
	 * (like MediaWiki's File::scaleHeight() and File::fitBoxWidth()).
	 */
	private static int fitBoxWidth(int srcWidth, int srcHeight, int maxHeight)
	{
		double idealWidth = (double) srcWidth * maxHeight / srcHeight;
		int roundedUp = (int) Math.ceil(idealWidth);
		if (Math.round((double) roundedUp * srcHeight / srcWidth) > maxHeight)
			return (int) Math.floor(idealWidth);
		return roundedUp;
	}

	@Override
	public void visit(WtImEndTag n)
	{
		// Should not happen ...
		throw new AssertionError();
	}

	@Override
	public void visit(WtImStartTag n)
	{
		// Should not happen ...
		throw new AssertionError();
	}

	public void visit(WtInternalLink n)
	{
		if (!n.getTarget().isResolved())
		{
			printAsWikitext(n);
			return;
		}

		p.indentAtBol();

		PageTitle target;
		try
		{
			target = PageTitle.make(wikiConfig, n.getTarget().getAsString());
		}
		catch (LinkTargetException e)
		{
			throw new VisitingException(e);
		}

		// FIXME: I think these should be removed in the parser already?!
		// A leading colon turns a category link into a normal link.
		if (target.getNamespace() == wikiConfig.getNamespace("Category")
				&& !target.hasInitialColon())
			return;

		// Fix #89: Links to a section of the current page ([[#Foo]] or
		// [[CurrentPage#Foo]]) only consist of the fragment
		if (isFragmentLinkToThisPage(target))
		{
			String href = "#" + HtmlSanitizer.escapeIdForLink(normalizeFragment(target.getFragment()));
			String cssClass = target.getTitle().isEmpty() ? "" : " class=\"mw-selflink-fragment\"";
			if (n.hasTitle())
			{
				pt("<a href=\"%~\"%s>%=%!%=</a>",
						href,
						cssClass,
						n.getPrefix(),
						n.getTitle(),
						n.getPostfix());
			}
			else
			{
				pt("<a href=\"%~\"%s>%=%=%=</a>",
						href,
						cssClass,
						n.getPrefix(),
						makeTitleFromTarget(n, target),
						n.getPostfix());
			}
			return;
		}

		if (target.getNamespace().isMediaNs())
		{
			printMediaLink(n, target);
			return;
		}

		if (!callback.resourceExists(target))
		{
			String title = target.getDenormalizedFullTitle();

			String path = UrlEncoding.WIKI.encode(target.getNormalizedFullTitle());

			if (n.hasTitle())
			{
				pt("<a href=\"%s\" class=\"new\" title=\"%~ (page does not exist)\">%=%!%=</a>",
						escAttrKeepCharRefs(callback.makeUrlMissingTarget(path)),
						title,
						n.getPrefix(),
						n.getTitle(),
						n.getPostfix());
			}
			else
			{
				String linkText = makeTitleFromTarget(n, target);

				pt("<a href=\"%s\" class=\"new\" title=\"%~ (page does not exist)\">%=%=%=</a>",
						escAttrKeepCharRefs(callback.makeUrlMissingTarget(path)),
						title,
						n.getPrefix(),
						linkText,
						n.getPostfix());
			}
		}
		else
		{
			if (!target.equals(pageTitle))
			{
				if (n.hasTitle())
				{
					pt("<a href=\"%s\" title=\"%~\">%=%!%=</a>",
							escAttrKeepCharRefs(callback.makeUrl(target)),
							makeLinkTitle(n, target),
							n.getPrefix(),
							n.getTitle(),
							n.getPostfix());
				}
				else
				{
					pt("<a href=\"%s\" title=\"%~\">%=%=%=</a>",
							escAttrKeepCharRefs(callback.makeUrl(target)),
							makeLinkTitle(n, target),
							n.getPrefix(),
							makeTitleFromTarget(n, target),
							n.getPostfix());
				}
			}
			else
			{
				if (n.hasTitle())
				{
					pt("<strong class=\"selflink\">%=%!%=</strong>",
							n.getPrefix(),
							n.getTitle(),
							n.getPostfix());
				}
				else
				{
					pt("<strong class=\"selflink\">%=%=%=</strong>",
							n.getPrefix(),
							makeTitleFromTarget(n, target),
							n.getPostfix());
				}
			}
		}
	}

	/**
	 * Renders a link into the Media namespace as a direct link to the file
	 * (like MediaWiki's Linker::makeMediaLinkFile()).
	 */
	private void printMediaLink(WtInternalLink n, PageTitle target)
	{
		PageTitle file = target.newWithNamespace(wikiConfig.getFileNamespace());

		MediaInfo info;
		try
		{
			info = callback.getMediaInfo(file.getNormalizedFullTitle(), -1, -1);
		}
		catch (Exception e)
		{
			throw new VisitingException(e);
		}

		String href;
		String cssClass;
		if (info != null && info.getImgUrl() != null)
		{
			href = info.getImgUrl();
			cssClass = "internal";
		}
		else
		{
			href = callback.makeUrlMissingTarget(
					UrlEncoding.WIKI.encode(file.getNormalizedFullTitle()));
			cssClass = "new";
		}

		if (n.hasTitle())
		{
			pt("<a href=\"%s\" class=\"%s\" title=\"%~\">%=%!%=</a>",
					escAttrKeepCharRefs(href),
					cssClass,
					target.getDenormalizedTitle(),
					n.getPrefix(),
					n.getTitle(),
					n.getPostfix());
		}
		else
		{
			pt("<a href=\"%s\" class=\"%s\" title=\"%~\">%=%=%=</a>",
					escAttrKeepCharRefs(href),
					cssClass,
					target.getDenormalizedTitle(),
					n.getPrefix(),
					makeTitleFromTarget(n, target),
					n.getPostfix());
		}
	}

	public void visit(WtItalics n)
	{
		p.indentAtBol("<i>");
		p.incIndent();
		iterate(n);
		p.decIndent();
		p.indentAtBol("</i>");
	}

	@Override
	public void visit(WtLinkOptionAltText n)
	{
		// Should not happen ...
		throw new AssertionError();
	}

	@Override
	public void visit(WtLinkOptionGarbage n)
	{
		// Should not happen ...
		throw new AssertionError();
	}

	@Override
	public void visit(WtLinkOptionKeyword n)
	{
		// Should not happen ...
		throw new AssertionError();
	}

	@Override
	public void visit(WtLinkOptionLinkTarget n)
	{
		// Should not happen ...
		throw new AssertionError();
	}

	@Override
	public void visit(WtLinkOptionResize n)
	{
		// Should not happen ...
		throw new AssertionError();
	}

	@Override
	public void visit(WtLinkOptions n)
	{
		// Should not happen ...
		throw new AssertionError();
	}

	@Override
	public void visit(WtLinkTitle n)
	{
		iterate(n);
	}

	public void visit(WtListItem n)
	{
		p.indentln("<li>");
		p.incIndent();
		iterate(n);
		p.decIndent();
		p.indentln("</li>");
	}

	@Override
	public void visit(WtName n)
	{
		iterate(n);
	}

	public void visit(WtNewline n)
	{
		if (!p.atBol())
			p.print(" ");
	}

	@Override
	public void visit(WtNodeList n)
	{
		iterate(n);
	}

	@Override
	public void visit(WtOnlyInclude n)
	{
		iterate(n);
	}

	public void visit(WtOrderedList n)
	{
		p.indentln("<ol>");
		p.incIndent();
		iterate(n);
		p.decIndent();
		p.indentln("</ol>");
	}

	@Override
	public void visit(WtPageName n)
	{
		// Should not happen ...
		throw new AssertionError();
	}

	@Override
	public void visit(WtPageSwitch n)
	{
		// Hide those...
	}

	public void visit(WtParagraph n)
	{
		if (!containsPre(n))
		{
			printParagraph(n);
			return;
		}

		// Like MediaWiki, close the paragraph in front of a <pre> and open a
		// new one after it if there is anything left to wrap.
		WtNodeList content = nf.list();
		for (WtNode c : n)
		{
			if (isPre(c))
			{
				if (!isBlank(content))
					printParagraph(content);
				content = nf.list();
				dispatch(c);
			}
			else
			{
				content.add(c);
			}
		}
		if (!isBlank(content))
			printParagraph(content);
	}

	private void printParagraph(WtNodeList content)
	{
		p.indentln("<p>");
		p.incIndent();
		iterate(content);
		p.decIndent();
		p.indentln("</p>");
	}

	private static boolean containsPre(WtNodeList content)
	{
		for (WtNode c : content)
		{
			if (isPre(c))
				return true;
		}
		return false;
	}

	/**
	 * @return Whether the node is a {@code <pre>} tag, either as tag
	 *         extension or as element created by the tag extension.
	 */
	private static boolean isPre(WtNode n)
	{
		if (n instanceof WtTagExtension)
			return ((WtTagExtension) n).getName().trim().equalsIgnoreCase("pre");
		if (n instanceof WtXmlElement)
			return ((WtXmlElement) n).getName().equalsIgnoreCase("pre");
		return false;
	}

	private static boolean isBlank(WtNodeList content)
	{
		for (WtNode c : content)
		{
			if (c instanceof WtText)
			{
				if (!((WtText) c).getContent().trim().isEmpty())
					return false;
			}
			else if (!(c instanceof WtNewline))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public void visit(WtParsedWikitextPage n)
	{
		iterate(n);
	}

	@Override
	public void visit(WtPreproWikitextPage n)
	{
		iterate(n);
	}

	@Override
	public void visit(WtRedirect n)
	{
		// Fixes issue #65, we render a link to the redirect target
		PageTitle target;
		try
		{
			target = PageTitle.make(this.wikiConfig, n.getTarget().getAsString());
		}
		catch (LinkTargetException e)
		{
			throw new VisitingException(e);
		}

		pt("<a href=\"%s\">%=</a>",
				escAttrKeepCharRefs(callback.makeUrl(target)),
				target.getDenormalizedFullTitle());
	}

	public void visit(WtSection n)
	{
		p.indent();
		pt("<h%d><span class=\"mw-headline\" id=\"%~\">%!</span></h%d>",
				n.getLevel(),
				makeSectionId(n.getHeading()),
				n.getHeading(),
				n.getLevel());

		p.println();
		dispatch(n.getBody());
	}

	public void visit(WtSemiPre n)
	{
		p.indent();
		++inPre;
		// MediaWiki removes the space that starts each line. Unless the parser
		// keeps it as an empty WtSemiPreLine, it is part of the text.
		inSemiPre = !isPreserveSemiPreLeadingSpace();
		semiPreLineStart = true;
		pt("<pre>%!</pre>", n);
		inSemiPre = false;
		--inPre;
		p.println();
	}

	public void visit(WtSemiPreLine n)
	{
		// Only stands for the leading space of a line, see visit(WtSemiPre)
		if (n.isEmpty() && isPreserveSemiPreLeadingSpace())
			return;

		iterate(n);
		p.println();
	}

	private boolean isPreserveSemiPreLeadingSpace()
	{
		return wikiConfig.getParserConfig().isPreserveSemiPreLeadingSpace();
	}

	@Override
	public void visit(WtSignature n)
	{
		// Without a pre-save transform MediaWiki shows signatures literally
		wrapText(StringTools.strrep('~', n.getTildeCount()));
	}

	public void visit(WtTable n)
	{
		p.indent();
		pt("<table%!>", sanitizeAttribs("table", n.getXmlAttributes()));
		p.println();

		p.incIndent();
		fixTableBody(n.getBody());
		p.decIndent();

		p.indentln("</table>");
	}

	@Override
	public void visit(WtTableCaption n)
	{
		p.indent();
		pt("<caption%!>", sanitizeAttribs("caption", n.getXmlAttributes()));
		p.println();
		p.incIndent();
		iterate(getCellContent(n.getBody()));
		p.decIndent();
		p.indentln("</caption>");
	}

	public void visit(WtTableCell n)
	{
		p.indent();
		pt("<td%!>", sanitizeAttribs("td", n.getXmlAttributes()));
		p.println();
		p.incIndent();
		iterate(getCellContent(n.getBody()));
		p.decIndent();
		p.indentln("</td>");
	}

	public void visit(WtTableHeader n)
	{
		p.indent();
		pt("<th%!>", sanitizeAttribs("th", n.getXmlAttributes()));
		p.println();
		p.incIndent();
		iterate(getCellContent(n.getBody()));
		p.decIndent();
		p.indentln("</th>");
	}

	public void visit(WtTableRow n)
	{
		boolean cellsDefined = false;
		for (WtNode cell : n.getBody())
		{
			switch (cell.getNodeType())
			{
				case WtNode.NT_TABLE_CELL:
				case WtNode.NT_TABLE_HEADER:
					cellsDefined = true;
					break;
			}
		}

		if (cellsDefined)
		{
			p.indent();
			pt("<tr%!>", sanitizeAttribs("tr", n.getXmlAttributes()));
			p.println();
			p.incIndent();
			iterate(getCellContent(n.getBody()));
			p.decIndent();
			p.indentln("</tr>");
		}
		else
		{
			iterate(n.getBody());
		}
	}

	public void visit(WtTableImplicitTableBody n)
	{
		iterate(n.getBody());
	}

	/**
	 * Tag extensions which do not show up in the page output.
	 */
	private static final Set<String> INVISIBLE_TAG_EXTENSIONS = setOf(
			"categorytree",
			"indicator",
			"inputbox",
			"section",
			"templatedata",
			"templatestyles");

	/**
	 * Tag extensions whose body is code and rendered as preformatted text.
	 */
	private static final Set<String> CODE_TAG_EXTENSIONS = setOf(
			"graph",
			"score",
			"source",
			"syntaxhighlight",
			"timeline");

	/**
	 * Tag extensions which produce inline content.
	 */
	private static final Set<String> INLINE_TAG_EXTENSIONS = setOf(
			"ce",
			"charinsert",
			"chem",
			"hiero",
			"langconvert",
			"maplink",
			"math");

	public void visit(WtTagExtension n)
	{
		String name = n.getName().trim().toLowerCase();

		// TODO: Should not get skipped!
		if (name.equals("ref") || name.equals("references"))
			return;

		// Tag extensions that were not expanded (e.g. because there is no
		// implementation for them): Keep the body visible but never interpret
		// it as wikitext or HTML.
		if (!n.hasBody() || INVISIBLE_TAG_EXTENSIONS.contains(name))
			return;

		if (name.equals("pre"))
		{
			printPre(
					sanitizeAttribs("pre", n.getXmlAttributes()),
					nf.list(nf.text(n.getBody().getContent())));
			return;
		}

		if (name.equals("nowiki"))
		{
			printNowiki(n.getBody().getContent());
			return;
		}

		String body = esc(n.getBody().getContent());
		if (CODE_TAG_EXTENSIONS.contains(name))
		{
			String cssClass = "mw-highlight";
			String lang = toCssClassName(getTagExtensionAttribute(n, "lang"));
			if (!lang.isEmpty())
				cssClass += " lang-" + lang;

			if (getTagExtensionAttribute(n, "inline") != null)
			{
				p.indentAtBol();
				p.print("<code class=\"" + cssClass + "\">" + body + "</code>");
			}
			else
			{
				p.indent();
				p.print("<pre class=\"" + cssClass + "\">" + body + "</pre>");
				p.println();
			}
		}
		else if (name.equals("poem"))
		{
			if (body.startsWith("\n"))
				body = body.substring(1);
			if (body.endsWith("\n"))
				body = body.substring(0, body.length() - 1);

			p.indent();
			p.print("<div class=\"poem\">" + body.replace("\n", "<br />\n") + "</div>");
			p.println();
		}
		else if (INLINE_TAG_EXTENSIONS.contains(name))
		{
			p.indentAtBol();
			p.print("<span class=\"mw-ext-" + toCssClassName(name) + "\">" + body + "</span>");
		}
		else
		{
			p.indent();
			p.print("<div class=\"mw-ext-" + toCssClassName(name) + "\">" + body + "</div>");
			p.println();
		}
	}

	/**
	 * @return The value of the given attribute, an empty string if the
	 *         attribute has no value or {@code null} if the attribute is not
	 *         given.
	 */
	private String getTagExtensionAttribute(WtTagExtension n, String attrName)
	{
		for (WtNode a : n.getXmlAttributes())
		{
			if (!(a instanceof WtXmlAttribute))
				continue;
			WtXmlAttribute attr = (WtXmlAttribute) a;
			if (attr.getName().isResolved() && attr.getName().getAsString().equalsIgnoreCase(attrName))
				return attr.hasValue() ? cleanAttribValue(attr.getValue()) : "";
		}
		return null;
	}

	private static String toCssClassName(String name)
	{
		if (name == null)
			return "";
		return name.toLowerCase().replaceAll("[^a-z0-9_-]", "");
	}

	private static Set<String> setOf(String... names)
	{
		Set<String> set = new HashSet<String>();
		for (String name : names)
			set.add(name);
		return set;
	}

	@Override
	public void visit(WtTagExtensionBody n)
	{
		// Should not happen ...
		throw new AssertionError();
	}

	@Override
	public void visit(WtTemplate n)
	{
		printAsWikitext(n);
	}

	@Override
	public void visit(WtTemplateArgument n)
	{
		printAsWikitext(n);
	}

	@Override
	public void visit(WtTemplateArguments n)
	{
		printAsWikitext(n);
	}

	@Override
	public void visit(WtTemplateParameter n)
	{
		printAsWikitext(n);
	}

	public void visit(WtText n)
	{
		wrapText(n.getContent());
	}

	@Override
	public void visit(WtTicks n)
	{
		// Should not happen ...
		throw new AssertionError();
	}

	public void visit(WtUnorderedList n)
	{
		p.indentln("<ul>");
		p.incIndent();
		iterate(n);
		p.decIndent();
		p.indentln("</ul>");
	}

	public void visit(WtUrl n)
	{
		p.indentAtBol();

		String url = escAttrKeepCharRefs(callback.makeUrl(n));
		pf("<a rel=\"nofollow\" class=\"external free\" href=\"%s\">%s</a>", url, url);
	}

	@Override
	public void visit(WtValue n)
	{
		iterate(n);
	}

	@Override
	public void visit(WtWhitespace n)
	{
		if (!p.atBol())
			p.println(" ");
	}

	public void visit(WtXmlAttribute n)
	{
		if (!n.getName().isResolved())
		{
			logger.warn("Unresolved attribute name: " + WtRtDataPrinter.print(n));
		}
		else
		{
			if (n.hasValue())
			{
				pt(" %s=\"%~\"", n.getName().getAsString(), cleanAttribValue(n.getValue()));
			}
			else
			{
				pf(" %s=\"%<s\"", n.getName().getAsString());
			}
		}
	}

	public void visit(WtXmlAttributeGarbage n)
	{
		logger.warn("Attribute garbage: " + WtRtDataPrinter.print(n));
	}

	@Override
	public void visit(WtXmlAttributes n)
	{
		for (WtNode n1 : n)
		{
			switch (n1.getNodeType())
			{
				case WtNode.NT_XML_ATTRIBUTE:
				case WtNode.NT_XML_ATTRIBUTE_GARBAGE:
					dispatch(n1);
					break;
				default:
					logger.warn("Non-attribute node in attributes collection: " + WtRtDataPrinter.print(n));
					break;
			}
		}
	}

	public void visit(WtXmlCharRef n)
	{
		p.indentAtBol();
		p.print(charRef(n));
	}

	@Override
	public void visit(WtXmlComment n)
	{
		// Hide those...
	}

	public void visit(WtXmlElement n)
	{
		String name = n.getName();
		if (!HtmlSanitizer.isAllowedElement(name))
		{
			printEscapedXmlElement(n);
			return;
		}

		Map<String, String> sanitized = sanitizeAttribMap(name, n.getXmlAttributes());
		if (!HtmlSanitizer.isValidTag(name, sanitized))
		{
			printEscapedXmlElement(n);
			return;
		}

		WtNodeList attribs = toXmlAttributes(sanitized);
		if (n.hasBody() && name.equalsIgnoreCase("pre"))
		{
			// Created by the <pre> tag extension
			printPre(attribs, n.getBody());
			return;
		}

		if (!VOID_ELEMENTS.contains(name.toLowerCase())
				&& (!n.hasBody() || isSelfClosing(n)))
		{
			// Like MediaWiki: A self-closing tag of a non-void element becomes
			// an empty element. Browsers would treat it as a start tag. If the
			// tree builder already moved the following content into the
			// element, that content is rendered after the element.
			if (blockElements.contains(name.toLowerCase()))
			{
				p.indent();
				pt("<%s%!></%s>", name, attribs, name);
				p.println();
			}
			else
			{
				p.indentAtBol();
				pt("<%s%!></%s>", name, attribs, name);
			}

			if (n.hasBody())
				dispatch(n.getBody());
		}
		else if (n.hasBody())
		{
			if (blockElements.contains(name.toLowerCase()))
			{
				p.indent();
				pt("<%s%!>", name, attribs);
				p.println();
				p.incIndent();
				dispatch(n.getBody());
				p.decIndent();
				p.indent();
				pf("</%s>", name);
				p.println();
			}
			else
			{
				p.indentAtBol();
				pt("<%s%!>", name, attribs);
				p.incIndent();
				dispatch(n.getBody());
				p.decIndent();
				p.indentAtBol();
				pf("</%s>", name);
			}
		}
		else
		{
			p.indentAtBol();
			pt("<%s%! />", name, attribs);
		}
	}

	/**
	 * Whether the element was written as self-closing tag (e.g.
	 * {@code <div/>}) in the wikitext. Only known if round-trip data was
	 * gathered.
	 */
	private static boolean isSelfClosing(WtXmlElement n)
	{
		WtRtData rtd = n.getRtd();
		return rtd != null
				&& !rtd.isSuppress()
				&& rtd.size() >= 2
				&& rtd.toString(1).trim().equals("/>");
	}

	/**
	 * Renders an element that is not allowed in the output as escaped text
	 * (like MediaWiki does). The content of the element is rendered as usual.
	 */
	private void printEscapedXmlElement(WtXmlElement n)
	{
		StringBuilder tag = new StringBuilder();
		tag.append('<').append(n.getName());
		for (WtNode a : n.getXmlAttributes())
		{
			if (!(a instanceof WtXmlAttribute))
				continue;

			WtXmlAttribute attr = (WtXmlAttribute) a;
			if (!attr.getName().isResolved())
				continue;

			tag.append(' ').append(attr.getName().getAsString());
			if (attr.hasValue())
				tag.append("=\"").append(cleanAttribValue(attr.getValue())).append('"');
		}
		tag.append(n.hasBody() ? ">" : " />");

		p.indentAtBol(esc(tag.toString()));
		if (n.hasBody())
		{
			dispatch(n.getBody());
			p.indentAtBol(esc("</" + n.getName() + ">"));
		}
	}

	public void visit(WtXmlEmptyTag n)
	{
		printAsWikitext(n);
	}

	public void visit(WtXmlEndTag n)
	{
		printAsWikitext(n);
	}

	public void visit(WtXmlEntityRef n)
	{
		p.indentAtBol();
		p.print(entityRef(n));
	}

	public void visit(WtXmlStartTag n)
	{
		printAsWikitext(n);
	}

	// =====================================================================

	private void wrapText(String text)
	{
		if (inPre > 0)
		{
			printPreformatted(esc(removeSemiPreIndent(text)));
		}
		else
		{
			p.indentAtBol(esc(StringTools.collapseWhitespace(text)));
		}
	}

	/**
	 * Renders a {@code <pre>} tag like MediaWiki: The content is not
	 * interpreted, {@code <nowiki>} tags are removed and only angle brackets
	 * and bare ampersands are escaped. Character references are kept.
	 */
	private void printPre(WtNodeList attribs, WtNodeList body)
	{
		p.indent();
		pt("<pre%!>", attribs);
		++inPre;
		for (WtNode c : body)
		{
			if (c instanceof WtText)
			{
				String content = ((WtText) c).getContent();
				content = NOWIKI_TAGS.matcher(content).replaceAll("$1");
				printPreformatted(escTextKeepCharRefs(content));
			}
			else
			{
				dispatch(c);
			}
		}
		--inPre;
		p.print("</pre>");
		p.println();
	}

	/**
	 * Renders the content of a {@code <nowiki>} tag like MediaWiki: Only angle
	 * brackets, bare ampersands and language converter markup are escaped.
	 * Character references are kept.
	 */
	private void printNowiki(String content)
	{
		String html = LANG_CONVERTER_MARKUP
				.matcher(escTextKeepCharRefs(content))
				.replaceAll(m -> m.group().equals("-{") ? "-&#123;" : "&#125;-");

		if (inPre > 0)
		{
			printPreformatted(html);
		}
		else
		{
			p.indentAtBol(StringTools.collapseWhitespace(html));
		}
	}

	/**
	 * Prints preformatted text as it is. The printer would otherwise merge
	 * consecutive newlines and indent an element that follows a newline.
	 */
	private void printPreformatted(String html)
	{
		p.verbatim(html);
		// Leave the "beginning of line" state, nothing must be indented
		p.verbatim("");
	}

	/**
	 * Removes the space which starts each line of a preformatted block
	 * (indent-pre), like MediaWiki does.
	 */
	private String removeSemiPreIndent(String text)
	{
		if (!inSemiPre)
			return text;

		StringBuilder b = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); ++i)
		{
			char ch = text.charAt(i);
			if (!semiPreLineStart || ch != ' ')
				b.append(ch);
			semiPreLineStart = (ch == '\n');
		}
		return b.toString();
	}

	/*
	private void wrapText(String text)
	{
		if (inPre > 0)
		{
			p.print(esc(text));
		}
		else
		{
			
			int i = 0;
			int len = text.length();
			
			while (i < len)
			{
				char ch;
				
				// If at beginning of line skip whitespace
				if (p.atBol())
				{
					while (i < len)
					{
						ch = text.charAt(i);
						if (!Character.isWhitespace(ch))
							break;
						++i;
					}
				}
				
				if (i >= len)
					break;
				
				p.flush();
				int col = p.getColumn();
				int border = 80 + p.getIndent() * 4;
				
				int j = i;
				while (j < len)
				{
					ch = text.charAt(j++);
					if (col >= border && Character.isWhitespace(ch))
						break;
					if (ch == '\n')
						break;
				}
				
				String substr = text.substring(i, j);
				if (!substr.isEmpty())
					p.indentAtBol(esc(StringTools.collapseWhitespace(substr)));
				
				if (i < len)
					p.println();
				
				i = j;
			}
		}
	}
	*/

	private void printAsWikitext(WtNode n)
	{
		// TODO: Implement
		//throw new FmtNotYetImplementedError();
		//p.indentAtBol();
	}

	private String toWikitext(WtNode value)
	{
		// TODO: Implement
		//throw new FmtNotYetImplementedError();
		return "";
	}

	// =====================================================================

	/**
	 * Fix #89: Computes the id of a heading like MediaWiki's
	 * Parser::finalizeHeadings(). The markup is stripped, character
	 * references are decoded and the whitespace is normalized. The id is not
	 * HTML escaped. Ids are unique (ignoring the case of ASCII letters)
	 * within the rendered page: Duplicates get a suffix "_2", "_3", ...
	 */
	private String makeSectionId(WtHeading n)
	{
		// The printer returns HTML escaped text without markup
		String text = makeTitleFromNodes(n);
		text = SECTION_NAME_WHITESPACE.matcher(text).replaceAll(" ").trim();
		text = HtmlSanitizer.decodeCharReferences(text, wikiConfig.getParserConfig());

		// MediaWiki gives up normalizing names with invalid characters
		if (text.indexOf('\uFFFD') < 0)
			text = normalizeFragment(text);

		String id = HtmlSanitizer.escapeIdForAttribute(text);

		String key = toLowerCaseAscii(id);
		if (headingIds.add(key))
			return id;

		int i = 2;
		while (headingIds.contains(key + "_" + i))
			++i;
		headingIds.add(key + "_" + i);
		return id + "_" + i;
	}

	/**
	 * Normalizes a section name or the fragment of a link target like
	 * MediaWiki's title parser: Bidi override characters are removed, runs of
	 * whitespace become a single space and trailing whitespace is removed.
	 */
	private static String normalizeFragment(String fragment)
	{
		fragment = BIDI_CHARS.matcher(fragment).replaceAll("");
		fragment = FRAGMENT_WHITESPACE.matcher(fragment).replaceAll(" ");
		int end = fragment.length();
		while (end > 0 && fragment.charAt(end - 1) == ' ')
			--end;
		return fragment.substring(0, end);
	}

	private static String toLowerCaseAscii(String text)
	{
		StringBuilder b = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); ++i)
		{
			char ch = text.charAt(i);
			b.append((ch >= 'A' && ch <= 'Z') ? (char) (ch + ('a' - 'A')) : ch);
		}
		return b.toString();
	}

	/**
	 * Whether the given link target has a fragment and points to the page
	 * that is rendered (or to no page at all, like [[#Foo]]).
	 */
	private boolean isFragmentLinkToThisPage(PageTitle target)
	{
		if (target.getFragment() == null || !target.isLocal())
			return false;

		if (target.getTitle().isEmpty())
			return true;

		return pageTitle != null
				&& pageTitle.isLocal()
				&& target.getNamespace().equals(pageTitle.getNamespace())
				&& target.getTitle().equals(pageTitle.getTitle());
	}

	private String makeImageAltText(WtImageLink n)
	{
		return makeTitleFromNodes(n.getAlt());
	}

	protected String makeImageCaption(WtImageLink n)
	{
		return makeTitleFromNodes(n.getTitle());
	}

	private String makeTitleFromNodes(WtNodeList titleNode)
	{
		StringWriter w = new StringWriter();
		// Untitled external links get the numbers they will be rendered with
		SafeLinkTitlePrinter p = new SafeLinkTitlePrinter(w, wikiConfig, untitledLinkCounter);
		p.go(titleNode);
		return w.toString();
	}

	// =====================================================================

	static String makeLinkTitle(WtInternalLink n, PageTitle target)
	{
		return target.getDenormalizedFullTitle();
	}

	protected String makeImageTitle(WtImageLink n, PageTitle target)
	{
		return target.getDenormalizedFullTitle();
	}

	private String makeTitleFromTarget(WtInternalLink n, PageTitle target)
	{
		return makeTitleFromTarget(target, n.getTarget());
	}

	private String makeTitleFromTarget(PageTitle target, WtPageName title)
	{
		String targetStr = title.getAsString();
		if (target.hasInitialColon() && !targetStr.isEmpty() && targetStr.charAt(0) == ':')
			targetStr = targetStr.substring(1);
		return targetStr;
	}

	// =====================================================================

	/**
	 * Pull garbage in between rows in front of the table.
	 */
	private void fixTableBody(WtNodeList body)
	{
		boolean hadRow = false;
		WtTableRow implicitRow = null;
		for (WtNode c : body)
		{
			switch (c.getNodeType())
			{
				case WtNode.NT_TABLE_HEADER: // fall through!
				case WtNode.NT_TABLE_CELL:
				{
					if (hadRow)
					{
						dispatch(c);
					}
					else
					{
						if (implicitRow == null)
							implicitRow = nf.tr(nf.emptyAttrs(), nf.body(nf.list()));
						implicitRow.getBody().add(c);
					}
					break;
				}

				case WtNode.NT_TABLE_CAPTION:
				{
					if (!hadRow && implicitRow != null)
						dispatch(implicitRow);
					implicitRow = null;
					dispatch(c);
					break;
				}

				case WtNode.NT_TABLE_ROW:
				{
					if (!hadRow && implicitRow != null)
						dispatch(implicitRow);
					hadRow = true;
					dispatch(c);
					break;
				}

				default:
				{
					if (!hadRow && implicitRow != null)
						implicitRow.getBody().add(c);
					else
						dispatch(c);
					break;
				}
			}
		}
	}

	/**
	 * If the cell content is only one paragraph (optionally followed by
	 * whitespace), the content of the paragraph is returned. Otherwise the
	 * whole cell content is returned. This is done to render cells with a
	 * single paragraph without the paragraph tags.
	 */
	protected static WtNode getCellContent(WtNodeList body)
	{
		if (body.size() >= 1 && body.get(0) instanceof WtParagraph)
		{
			boolean ok = true;
			for (int i = 1; i < body.size(); ++i)
			{
				WtNode c = body.get(i);
				boolean whitespace = (c instanceof WtNewline)
						|| (c instanceof WtWhitespace)
						|| (c instanceof WtText && ((WtText) c).getContent().trim().isEmpty());
				if (!whitespace)
				{
					ok = false;
					break;
				}
			}

			if (ok)
				body = (WtParagraph) body.get(0);
		}

		return body;
	}

	// =====================================================================

	protected String cleanAttribValue(WtNodeList value)
	{
		try
		{
			return StringTools.collapseWhitespace(tu.astToText(value)).trim();
		}
		catch (StringConversionException e)
		{
			return toWikitext(value);
		}
	}

	/**
	 * Removes or neutralizes all attributes that are not allowed on the given
	 * element (see {@link HtmlSanitizer}). Like MediaWiki, presentational
	 * attributes like {@code align} or {@code width} are kept as they are.
	 */
	protected WtNodeList sanitizeAttribs(String element, WtNodeList xmlAttributes)
	{
		return toXmlAttributes(sanitizeAttribMap(element, xmlAttributes));
	}

	private Map<String, String> sanitizeAttribMap(String element, WtNodeList xmlAttributes)
	{
		return HtmlSanitizer.sanitizeAttributes(
				element,
				toAttribMap(xmlAttributes));
	}

	private Map<String, String> toAttribMap(WtNodeList xmlAttributes)
	{
		Map<String, String> attribs = new LinkedHashMap<String, String>();
		for (WtNode a : xmlAttributes)
		{
			if (!(a instanceof WtXmlAttribute))
				continue;

			WtXmlAttribute attr = (WtXmlAttribute) a;
			if (!attr.getName().isResolved())
			{
				logger.warn("Unresolved attribute name: " + WtRtDataPrinter.print(attr));
				continue;
			}

			String name = attr.getName().getAsString();
			attribs.put(name, attr.hasValue() ? cleanAttribValue(attr.getValue()) : name);
		}
		return attribs;
	}

	private WtNodeList toXmlAttributes(Map<String, String> attribs)
	{
		WtNodeList result = nf.attrs(nf.list());
		for (Map.Entry<String, String> e : attribs.entrySet())
		{
			result.add(nf.attr(
					nf.name(nf.list(nf.text(e.getKey()))),
					nf.value(nf.list(nf.text(e.getValue())))));
		}
		return result;
	}

	// =========================================================================

	public static <T extends WtNode> String print(
			HtmlRendererCallback callback,
			WikiConfig wikiConfig,
			PageTitle pageTitle,
			T node)
	{
		return print(callback, wikiConfig, new StringWriter(), pageTitle, node).toString();
	}

	public static <T extends WtNode> Writer print(
			HtmlRendererCallback callback,
			WikiConfig wikiConfig,
			Writer writer,
			PageTitle pageTitle,
			T node)
	{
		new HtmlRenderer(callback, wikiConfig, pageTitle, writer).go(node);
		return writer;
	}

	// =========================================================================

	protected static final Logger logger = LoggerFactory.getLogger(HtmlRenderer.class);

	protected static final Set<String> blockElements = new HashSet<String>();

	private static final Pattern SECTION_NAME_WHITESPACE = Pattern.compile("[ _]+");

	private static final Pattern FRAGMENT_WHITESPACE = Pattern.compile(
			"[ _\\u00A0\\u1680\\u180E\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+");

	private static final Pattern BIDI_CHARS = Pattern.compile(
			"[\\u200E\\u200F\\u202A-\\u202E]");

	/**
	 * Elements which keep a self-closing tag ($htmlsingleonly in MediaWiki's
	 * Sanitizer).
	 */
	private static final Set<String> VOID_ELEMENTS = setOf(
			"br",
			"wbr",
			"hr",
			"meta",
			"link");

	protected final WikiConfig wikiConfig;

	protected final PageTitle pageTitle;

	protected final EngineNodeFactory nf;

	protected final EngineAstTextUtils tu;

	protected final HtmlRendererCallback callback;

	protected int inPre = 0;

	/**
	 * Whether we are in a preformatted block (indent-pre) whose lines still
	 * start with a space.
	 */
	private boolean inSemiPre = false;

	private boolean semiPreLineStart = false;

	private static final Pattern NOWIKI_TAGS =
			Pattern.compile("<nowiki>(.*?)</nowiki>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern LANG_CONVERTER_MARKUP = Pattern.compile("-\\{|\\}-");

	static
	{
		// left out del and ins, added table elements
		blockElements.add("div");
		blockElements.add("address");
		blockElements.add("blockquote");
		blockElements.add("center");
		blockElements.add("dir");
		blockElements.add("div");
		blockElements.add("dl");
		blockElements.add("fieldset");
		blockElements.add("form");
		blockElements.add("h1");
		blockElements.add("h2");
		blockElements.add("h3");
		blockElements.add("h4");
		blockElements.add("h5");
		blockElements.add("h6");
		blockElements.add("hr");
		blockElements.add("isindex");
		blockElements.add("menu");
		blockElements.add("noframes");
		blockElements.add("noscript");
		blockElements.add("ol");
		blockElements.add("p");
		blockElements.add("pre");
		blockElements.add("table");
		blockElements.add("ul");
		blockElements.add("center");
		blockElements.add("caption");
		blockElements.add("tr");
		blockElements.add("td");
		blockElements.add("th");
		blockElements.add("colgroup");
		blockElements.add("thead");
		blockElements.add("tbody");
		blockElements.add("tfoot");
	}

	// =========================================================================

	protected HtmlRenderer(
			HtmlRendererCallback callback,
			WikiConfig wikiConfig,
			PageTitle pageTitle,
			Writer w)
	{
		super(w);
		this.callback = callback;
		this.wikiConfig = wikiConfig;
		this.pageTitle = pageTitle;
		this.nf = wikiConfig.getNodeFactory();
		this.tu = wikiConfig.getAstTextUtils();
	}
}
