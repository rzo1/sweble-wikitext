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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.PfnArgumentMode;
import org.sweble.wikitext.engine.config.ParserFunctionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.utils.StringConversionException;

public class CorePfnFunctionsLocalization
		extends
			ParserFunctionGroup
{
	private static final long serialVersionUID = 1L;

	// =========================================================================

	protected CorePfnFunctionsLocalization(WikiConfig wikiConfig)
	{
		super("Core - Parser Functions - Localization");
		addParserFunction(new PluralPfn(wikiConfig));
		addParserFunction(new GrammarPfn(wikiConfig));
		addParserFunction(new IntPfn(wikiConfig));
	}

	public static CorePfnFunctionsLocalization group(WikiConfig wikiConfig)
	{
		return new CorePfnFunctionsLocalization(wikiConfig);
	}

	// =========================================================================
	// ==
	// == {{plural:2|is|are}}
	// ==
	// =========================================================================

	/**
	 * Selects the plural form like MediaWiki's Language::convertPlural() for
	 * English: The first form is used for the count 1, the second form for
	 * every other count. A form like "0=none" is used for exactly this count.
	 *
	 * TODO: The configuration has no place for the plural rules of other
	 * languages.
	 */
	public static final class PluralPfn
			extends
				CorePfnFunction
	{
		private static final long serialVersionUID = 1L;

		private static final Pattern EXPLICIT_FORM_RX = Pattern.compile("[0-9]+=");

		private static final Pattern PHP_FLOAT_PREFIX_RX = Pattern.compile(
				"[ \\t\\n\\r\\u000B\\f]*" +
						"[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

		/**
		 * For un-marshaling only.
		 */
		public PluralPfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "plural");
		}

		public PluralPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "plural");
		}

		@Override
		public WtNode invoke(
				WtTemplate pfn,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			if (args.size() < 1)
				return nf().list();

			String count;
			try
			{
				count = toPhpNumber(
						CorePfnFunctionsFormatting.FormatnumPfn.parseFormattedNumber(
								tu().astToText(args.get(0)).trim()));
			}
			catch (StringConversionException e)
			{
				fileInvalidNameWarning(frame, WarningSeverity.NORMAL, args.get(0));
				return pfn;
			}

			// Explicit forms like "0=none"
			List<WtNode> forms = new ArrayList<WtNode>(args.size());
			for (WtNode form : args.subList(1, args.size()))
			{
				String formText;
				try
				{
					formText = tu().astToText(form);
				}
				catch (StringConversionException e)
				{
					forms.add(form);
					continue;
				}

				if (EXPLICIT_FORM_RX.matcher(formText).find())
				{
					int pos = formText.indexOf('=');
					if (formText.substring(0, pos).equals(count))
						return nf().text(formText.substring(pos + 1));
				}
				else
				{
					forms.add(form);
				}
			}

			if (forms.isEmpty())
				return nf().list();

			int index = isOne(count) ? 0 : 1;
			return forms.get(Math.min(index, forms.size() - 1));
		}

		/**
		 * The plural rule "one" of English: "i = 1 and v = 0".
		 */
		private static boolean isOne(String count)
		{
			return count.equals("1") || count.equals("-1");
		}

		/**
		 * Converts the count to an int or float like MediaWiki does and then
		 * back into a string like PHP does.
		 */
		static String toPhpNumber(String count)
		{
			if (isDigits(count))
			{
				BigInteger value = new BigInteger(count);
				if (value.bitLength() > 63)
					return String.valueOf(Long.MAX_VALUE);
				return value.toString();
			}

			// PHP converts the numeric prefix of a string to a float
			Matcher m = PHP_FLOAT_PREFIX_RX.matcher(count);
			double value = m.lookingAt() ? Double.parseDouble(m.group().trim()) : 0.;
			return phpFloatToString(value);
		}

		private static boolean isDigits(String text)
		{
			if (text.isEmpty())
				return false;
			for (int i = 0; i < text.length(); ++i)
			{
				char ch = text.charAt(i);
				if (ch < '0' || ch > '9')
					return false;
			}
			return true;
		}

		/**
		 * Like PHP's conversion of a float into a string: The shortest
		 * representation, in exponential notation for very small and very
		 * large numbers.
		 */
		private static String phpFloatToString(double value)
		{
			if (Double.isNaN(value))
				return "NAN";
			if (Double.isInfinite(value))
				return (value > 0) ? "INF" : "-INF";
			if (value == 0)
				return (1 / value < 0) ? "-0" : "0";

			BigDecimal decimal = new BigDecimal(Double.toString(value)).stripTrailingZeros();
			int exponent = decimal.precision() - decimal.scale() - 1;
			if (exponent < -5 || exponent >= 15)
			{
				String digits = decimal.unscaledValue().abs().toString();
				String mantissa = digits.substring(0, 1) + "." +
						((digits.length() > 1) ? digits.substring(1) : "0");
				return ((value < 0) ? "-" : "") + mantissa + "E" +
						((exponent < 0) ? "-" : "+") + Math.abs(exponent);
			}

			return decimal.toPlainString();
		}
	}

	// =========================================================================
	// ==
	// == {{grammar:N|noun}}
	// ==
	// =========================================================================

	/**
	 * Like MediaWiki's Language::convertGrammar() for English, which has no
	 * grammatical transformations: The word is returned unchanged.
	 *
	 * TODO: The configuration has no place for the grammatical
	 * transformations of other languages.
	 */
	public static final class GrammarPfn
			extends
				CorePfnFunction
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public GrammarPfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "grammar");
		}

		public GrammarPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "grammar");
		}

		@Override
		public WtNode invoke(
				WtTemplate pfn,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			if (args.size() < 2)
				return nf().list();

			return args.get(1);
		}
	}

	// =========================================================================
	// ==
	// == TODO: {{gender:username
	// ==           |return text if user is male
	// ==           |return text if user is female
	// ==           |return text if user hasn't defined their gender
	// ==       }}
	// ==
	// =========================================================================

	// =========================================================================
	// ==
	// == {{int:message name}}
	// == {{int:editsectionhint|MediaWiki}}
	// ==
	// =========================================================================

	/**
	 * Without a store of interface messages every message is missing. Like
	 * MediaWiki the name of a missing message is shown in angle brackets:
	 * <code>⧼msgname⧽</code>.
	 *
	 * TODO: Look up the messages of the wiki.
	 */
	public static final class IntPfn
			extends
				CorePfnFunction
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public IntPfn()
		{
			super(PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "int");
		}

		public IntPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, PfnArgumentMode.EXPANDED_AND_TRIMMED_VALUES, "int");
		}

		@Override
		public WtNode invoke(
				WtTemplate pfn,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			if (args.size() < 1)
				return pfn;

			String name;
			try
			{
				name = tu().astToText(args.get(0));
			}
			catch (StringConversionException e)
			{
				fileInvalidNameWarning(frame, WarningSeverity.NORMAL, args.get(0));
				return pfn;
			}

			// Like MediaWiki: Without a message name it's no parser function
			if (name.isEmpty())
				return pfn;

			return nf().text("⧼" + escapeHtml(name) + "⧽");
		}

		/**
		 * Like PHP's htmlspecialchars() with ENT_QUOTES.
		 */
		private static String escapeHtml(String text)
		{
			StringBuilder b = new StringBuilder(text.length());
			for (int i = 0; i < text.length(); ++i)
			{
				char ch = text.charAt(i);
				switch (ch)
				{
					case '&':
						b.append("&amp;");
						break;
					case '<':
						b.append("&lt;");
						break;
					case '>':
						b.append("&gt;");
						break;
					case '"':
						b.append("&quot;");
						break;
					case '\'':
						b.append("&#039;");
						break;
					default:
						b.append(ch);
				}
			}
			return b.toString();
		}
	}
}
