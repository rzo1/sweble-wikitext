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

package org.sweble.wikitext.engine.ext.parser_functions;

import java.util.List;

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtNodeList;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.utils.StringConversionException;

public class ParserFunctionSwitch
		extends
			ParserFunctionsExtPfn.CtrlStmt
{
	private static final long serialVersionUID = 1L;

	/**
	 * For un-marshaling only.
	 */
	public ParserFunctionSwitch()
	{
		super("switch");
	}

	public ParserFunctionSwitch(WikiConfig wikiConfig)
	{
		super(wikiConfig, "switch");
	}

	@Override
	protected WtNode evaluate(
			WtTemplate pfn,
			ExpansionFrame frame,
			List<? extends WtNode> args)
	{
		if (args.size() < 1)
			return nf().list();

		return new Evaluator(frame, args).evaluate();
	}

	private final class Evaluator
	{
		private ExpansionFrame frame;

		private List<? extends WtNode> args;

		private WtNodeList after;

		private WtNodeList before;

		public Evaluator(ExpansionFrame frame, List<? extends WtNode> args)
		{
			this.frame = frame;
			this.args = args;
		}

		/**
		 * Follows ParserFunctions::switch() of MediaWiki's ParserFunctions
		 * extension.
		 */
		public WtNode evaluate()
		{
			String primary = decodeTrimToText(frame.expand(args.get(0)));

			boolean found = false;
			boolean defaultFound = false;
			WtNode defaultValue = null;
			boolean lastItemHadNoEquals = false;
			WtNode lastItem = null;

			for (int i = 1; i < args.size(); ++i)
			{
				// Process each argument of the switch (after the test string)

				after = null;
				before = nf().list();
				if (args.get(i).isNodeType(WtNode.NT_NODE_LIST))
				{
					splitNodeListAtEquals(i);
				}
				else
				{
					WtNode c = args.get(i);
					if (c.isNodeType(WtNode.NT_TEXT))
						splitTextAtEquals(c);
					else
						before.add(c);
				}

				// Now before holds the stuff in front of the "=" and after
				// contains everything after the "=". If no "=" was found,
				// before contains everything and after == null.

				if (after != null)
				{
					lastItemHadNoEquals = false;

					// A previous case without "=" matched (fall through)
					if (found)
						return after;

					String test = decodeTrimToText(frame.expand(before));
					if (equal(primary, test))
						return after;

					// A bare "#default" turns the next case into the default
					if (defaultFound || isDefault(test))
					{
						defaultValue = after;
						defaultFound = false;
					}
				}
				else
				{
					lastItemHadNoEquals = true;

					lastItem = frame.expand(before);

					String test = decodeTrimToText(lastItem);
					if (equal(primary, test))
					{
						found = true;
					}
					else if (isDefault(test))
					{
						defaultFound = true;
					}
				}
			}

			// If the last case has no "=" it is the default case, even if
			// there is an explicit "#default" case.
			if (lastItemHadNoEquals)
				return lastItem;

			return defaultValue;
		}

		private String decodeTrimToText(WtNode n)
		{
			try
			{
				return decodeCharReferences(tu().astToText(n)).trim();
			}
			catch (StringConversionException e)
			{
				// FIXME: Do recursive equality check
				return null;
			}
		}

		private boolean equal(String primary, String test)
		{
			return (primary != null) && (test != null) && phpLooseEquals(primary, test);
		}

		private boolean isDefault(String test)
		{
			return (test != null) && test.equalsIgnoreCase("#default");
		}

		private void splitNodeListAtEquals(int i)
		{
			for (WtNode c : args.get(i))
			{
				if (after == null)
				{
					if (c.isNodeType(WtNode.NT_TEXT))
					{
						splitTextAtEquals(c);
					}
					else
					{
						before.add(c);
					}
				}
				else
				{
					after.add(c);
				}
			}
		}

		private void splitTextAtEquals(WtNode c)
		{
			String text = ((WtText) c).getContent();

			int j = text.indexOf('=');
			if (j != -1)
			{
				before.add(nf().text(text.substring(0, j)));
				after = nf().list(nf().text(text.substring(j + 1)));
			}
			else
			{
				before.add(c);
			}
		}
	}
}
