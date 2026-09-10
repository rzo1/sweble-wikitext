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

package org.sweble.wikitext.engine.ext.core;

import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.PfnArgumentMode;
import org.sweble.wikitext.engine.config.ParserFunctionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngineRtData;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtNodeList;
import org.sweble.wikitext.parser.nodes.WtTagExtension;
import org.sweble.wikitext.parser.nodes.WtTagExtensionBody;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.nodes.WtTemplateArgument;
import org.sweble.wikitext.parser.utils.StringConversionException;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

import de.fau.cs.osr.utils.XmlGrammar;

public class CorePfnFunctionsMiscellaneous
		extends
			ParserFunctionGroup
{
	private static final long serialVersionUID = 1L;

	// =========================================================================

	protected CorePfnFunctionsMiscellaneous(WikiConfig wikiConfig)
	{
		super("Core - Parser Functions - Miscellaneous");
		addParserFunction(new TagPfn(wikiConfig));
	}

	public static CorePfnFunctionsMiscellaneous group(WikiConfig wikiConfig)
	{
		return new CorePfnFunctionsMiscellaneous(wikiConfig);
	}

	// =========================================================================
	// ==
	// == TODO: {{#language:language code}}
	// ==       {{#language:ar}}
	// ==       {{#language:language code|target language code}}
	// ==       {{#language:ar|en}}
	// == TODO: {{#special:special page name}}
	// ==       {{#special:userlogin}}
	// == TODO: {{#speciale:special page name}}
	// ==       {{#speciale:userlogin}}
	// ==
	// =========================================================================

	// =========================================================================
	// ==
	// == TODO: {{#tag:tagname
	// ==           |content
	// ==           |attribute1=value1
	// ==           |attribute2=value2
	// ==       }}
	// ==
	// =========================================================================

	public static final class TagPfn
			extends
				CorePfnFunction
	{
		private static final long serialVersionUID = 1L;

		private static final Pattern QUOTED_VALUE_RX =
				Pattern.compile("[\"'](.+)[\"']|\"\"|''", Pattern.DOTALL);

		/**
		 * For un-marshaling only.
		 */
		public TagPfn()
		{
			super(PfnArgumentMode.TEMPLATE_ARGUMENTS, "tag");
		}

		public TagPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.TEMPLATE_ARGUMENTS, "tag");
		}

		@Override
		public WtNode invoke(
				WtTemplate pfn,
				ExpansionFrame frame,
				List<? extends WtNode> argsValues)
		{
			if (argsValues.size() < 1)
				return pfn;

			WtTemplateArgument nameNode = (WtTemplateArgument) argsValues.get(0);

			String nameStr;
			try
			{
				WtNode expNameNode = frame.expand(nameNode.getValue());
				// Like MediaWiki, tag names are case-insensitive
				nameStr = tu().astToText(expNameNode).trim().toLowerCase(Locale.ROOT);
			}
			catch (StringConversionException e)
			{
				fileInvalidNameWarning(frame, WarningSeverity.NORMAL, nameNode);
				return pfn;
			}

			// Like MediaWiki, a tag without content is created if there is
			// no content argument (e.g. {{#tag:nowiki}})
			WtTagExtensionBody body = null;
			if (argsValues.size() >= 2)
			{
				// FIXME: Meld 'name=' part into value
				// FIXME: Do something about the "remove comments" hack
				WtTemplateArgument bodyNode = (WtTemplateArgument) argsValues.get(1);
				WtNode expValueNode = frame.expand(bodyNode.getValue());
				expValueNode = stripComments(expValueNode);
				body = nf().tagExtBody(WtRtDataPrinter.print(expValueNode));
			}

			WtNodeList attrs = nf().list();
			for (int i = 2; i < argsValues.size(); ++i)
			{
				WtTemplateArgument arg = (WtTemplateArgument) argsValues.get(i);
				WtNode argNameNode = frame.expand(arg.getName());
				WtNode argValueNode = frame.expand(arg.getValue());
				if (argNameNode == null || argValueNode == null)
					continue;

				String argName;
				String argValue;
				try
				{
					argName = tu().astToText(argNameNode).trim();
					argValue = stripQuotes(tu().astToText(argValueNode).trim());
				}
				catch (StringConversionException e)
				{
					fileInvalidNameWarning(frame, WarningSeverity.NORMAL, arg);
					continue;
				}

				if (!XmlGrammar.xmlName().matcher(argName).matches())
				{
					fileIllegalArgumentsWarning(
							frame,
							WarningSeverity.NORMAL,
							arg,
							"Attribute name `" + argName + "' is not a valid XML name and was dropped");
					continue;
				}

				WtNodeList argValueList = nf().list(nf().text(argValue));

				attrs.add(nf().attr(
						nf().name(nf().list(nf().text(argName))),
						nf().value(argValueList)));
			}

			WtTagExtension tagExt = EngineRtData.set((body != null) ?
					nf().tagExt(nameStr, nf().attrs(attrs), body) :
					nf().tagExt(nameStr, nf().attrs(attrs)));

			return frame.expand(tagExt);
		}

		/**
		 * Like MediaWiki's tagObj(): One pair of quotes enclosing an attribute
		 * value is removed.
		 */
		private static String stripQuotes(String value)
		{
			Matcher m = QUOTED_VALUE_RX.matcher(value);
			if (!m.matches())
				return value;

			return (m.group(1) != null) ? m.group(1) : "";
		}

		private WtNode stripComments(WtNode n)
		{
			ListIterator<WtNode> i = n.listIterator();
			while (i.hasNext())
			{
				WtNode child = i.next();
				switch (child.getNodeType())
				{
					case WtNode.NT_XML_COMMENT:
					case WtNode.NT_IGNORED:
						i.remove();
						break;
					default:
						if (!child.isEmpty())
							stripComments(child);
				}
			}
			return n;
		}
	}
}
