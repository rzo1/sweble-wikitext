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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.ParserFunctionBase;
import org.sweble.wikitext.engine.PfnArgumentMode;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;

public abstract class ParserFunctionsExtPfn
		extends
			ParserFunctionBase
{
	private static final long serialVersionUID = 1L;

	// =========================================================================

	/**
	 * For un-marshaling only.
	 */
	public ParserFunctionsExtPfn(String name)
	{
		super(name);
	}

	/**
	 * For un-marshaling only.
	 */
	public ParserFunctionsExtPfn(PfnArgumentMode argMode, String name)
	{
		super(argMode, name);
	}

	public ParserFunctionsExtPfn(WikiConfig wikiConfig, String name)
	{
		super(wikiConfig, name);
	}

	public ParserFunctionsExtPfn(
			WikiConfig wikiConfig,
			PfnArgumentMode argMode,
			String name)
	{
		super(wikiConfig, argMode, name);
	}

	// =========================================================================

	@Override
	public final WtNode invoke(
			WtNode template,
			ExpansionFrame frame,
			List<? extends WtNode> argsValues)
	{
		return invoke((WtTemplate) template, frame, argsValues);
	}

	public abstract WtNode invoke(
			WtTemplate wtTemplate,
			ExpansionFrame frame,
			List<? extends WtNode> argsValues);

	// =========================================================================

	private static final Pattern CHAR_REFS_RX = Pattern.compile(
			"&([A-Za-z0-9\\u0080-\\uFFFF]+);|&#([0-9]+);|&#[xX]([0-9A-Fa-f]+);");

	private static final Pattern PHP_NUMERIC_RX = Pattern.compile(
			"[ \\t\\n\\r\\u000B\\f]*" +
					"([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)" +
					"[ \\t\\n\\r\\u000B\\f]*");

	private static final Pattern PHP_INTEGER_RX = Pattern.compile("[+-]?[0-9]+");

	/**
	 * Decodes named, decimal and hexadecimal character references like
	 * MediaWiki's <code>Sanitizer::decodeCharReferences()</code>. Unknown
	 * named references are kept, invalid code points are replaced by U+FFFD.
	 */
	protected String decodeCharReferences(String text)
	{
		if (text.indexOf('&') == -1)
			return text;

		StringBuilder b = new StringBuilder();

		Matcher m = CHAR_REFS_RX.matcher(text);
		int copyFrom = 0;
		while (m.find())
		{
			b.append(text, copyFrom, m.start());
			b.append(decodeCharReference(m));
			copyFrom = m.end();
		}
		b.append(text, copyFrom, text.length());

		return b.toString();
	}

	private String decodeCharReference(Matcher m)
	{
		if (m.group(1) != null)
		{
			String resolved = getWikiConfig().getParserConfig().resolveXmlEntity(m.group(1));
			return (resolved != null) ? resolved : m.group();
		}

		int codePoint = -1;
		try
		{
			codePoint = (m.group(2) != null) ?
					Integer.parseInt(m.group(2)) :
					Integer.parseInt(m.group(3), 16);
		}
		catch (NumberFormatException e)
		{
			// Too large, will be replaced by U+FFFD
		}

		return isValidCodePoint(codePoint) ?
				new String(Character.toChars(codePoint)) :
				"\uFFFD";
	}

	/**
	 * Like MediaWiki's <code>Sanitizer::validateCodepoint()</code>.
	 */
	private static boolean isValidCodePoint(int cp)
	{
		return cp == 0x09
				|| cp == 0x0A
				|| (cp >= 0x20 && cp <= 0x7E)
				|| (cp >= 0xA0 && cp <= 0xD7FF)
				|| (cp >= 0xE000 && cp <= 0xFFFD)
				|| (cp >= 0x10000 && cp <= 0x10FFFF);
	}

	/**
	 * Compares two strings like PHP's loose comparison operator
	 * <code>==</code>: If both strings are numeric (see PHP's
	 * <code>is_numeric()</code>) they are compared as numbers, otherwise they
	 * are compared as strings. Consequently "01" equals "1" and "1e3" equals
	 * "1000", but "1d" does not equal "1" and "NaN" equals "NaN".
	 */
	protected static boolean phpLooseEquals(String a, String b)
	{
		Matcher ma = PHP_NUMERIC_RX.matcher(a);
		Matcher mb = PHP_NUMERIC_RX.matcher(b);
		if (!ma.matches() || !mb.matches())
			return a.equals(b);

		String na = ma.group(1);
		String nb = mb.group(1);

		Long la = parsePhpInteger(na);
		Long lb = parsePhpInteger(nb);
		if (la != null && lb != null)
			return la.longValue() == lb.longValue();

		// Integers which do not fit into a long are compared as floating
		// point numbers. Where that is not exact, PHP compares the strings.
		int overflowA = getIntegerOverflow(na, la);
		int overflowB = getIntegerOverflow(nb, lb);
		if ((la != null && overflowB != 0) || (lb != null && overflowA != 0))
			return a.equals(b);

		double da = Double.parseDouble(na);
		double db = Double.parseDouble(nb);
		if (overflowA != 0 && overflowA == overflowB && da == db)
			return a.equals(b);

		return da == db;
	}

	private static Long parsePhpInteger(String number)
	{
		if (!PHP_INTEGER_RX.matcher(number).matches())
			return null;

		try
		{
			return Long.parseLong(number);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static int getIntegerOverflow(String number, Long value)
	{
		if (value != null || !PHP_INTEGER_RX.matcher(number).matches())
			return 0;

		return number.startsWith("-") ? -1 : 1;
	}

	// =========================================================================

	public static abstract class CtrlStmt
			extends
				ParserFunctionsExtPfn
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		protected CtrlStmt(String name)
		{
			super(PfnArgumentMode.UNEXPANDED_VALUES, name);
		}

		protected CtrlStmt(WikiConfig wikiConfig, String name)
		{
			super(wikiConfig, PfnArgumentMode.UNEXPANDED_VALUES, name);
		}

		@Override
		public final WtNode invoke(
				WtTemplate pfn,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			WtNode result = evaluate((WtTemplate) pfn, frame, args);

			// All control flow statements expand and trim their results.

			if (result != null)
			{
				return tu().trim(frame.expand(result));
			}
			else
			{
				return nf().text("");
			}
		}

		protected abstract WtNode evaluate(
				WtTemplate pfn,
				ExpansionFrame frame,
				List<? extends WtNode> args);
	}

	// =========================================================================

	public static abstract class IfThenElseStmt
			extends
				CtrlStmt
	{
		private static final long serialVersionUID = 1L;

		private final boolean hasDefault;

		private WtNode defaultValue;

		private final int thenArgIndex;

		/**
		 * For un-marshaling only.
		 */
		protected IfThenElseStmt(
				String name,
				int thenArgIndex)
		{
			super(name);
			this.hasDefault = false;
			this.thenArgIndex = thenArgIndex;
		}

		/**
		 * For un-marshaling only.
		 */
		protected IfThenElseStmt(
				String name,
				int thenArgIndex,
				boolean hasDefault)
		{
			super(name);
			this.hasDefault = hasDefault;
			this.thenArgIndex = thenArgIndex;
		}

		protected IfThenElseStmt(
				WikiConfig wikiConfig,
				String name,
				int thenArgIndex)
		{
			super(wikiConfig, name);
			this.hasDefault = false;
			this.thenArgIndex = thenArgIndex;
		}

		protected IfThenElseStmt(
				WikiConfig wikiConfig,
				String name,
				int thenArgIndex,
				boolean hasDefault)
		{
			super(wikiConfig, name);
			this.hasDefault = hasDefault;
			this.thenArgIndex = thenArgIndex;
		}

		@Override
		protected WtNode evaluate(
				WtTemplate pfn,
				ExpansionFrame frame,
				List<? extends WtNode> args)
		{
			if (args.size() <= (hasDefault ? thenArgIndex - 1 : thenArgIndex))
				return nf().text("");

			boolean cond = evaluateCondition(pfn, frame, args);

			WtNode result = defaultValue;
			if (cond)
			{
				if (args.size() > thenArgIndex)
					result = args.get(thenArgIndex);
			}
			else
			{
				int elseArgIndex = thenArgIndex + 1;

				if (args.size() > elseArgIndex)
					result = args.get(elseArgIndex);
			}

			return result;
		}

		protected void setDefault(WtNode defaultValue)
		{
			this.defaultValue = defaultValue;
		}

		protected abstract boolean evaluateCondition(
				WtTemplate pfn,
				ExpansionFrame frame,
				List<? extends WtNode> args);
	}
}
