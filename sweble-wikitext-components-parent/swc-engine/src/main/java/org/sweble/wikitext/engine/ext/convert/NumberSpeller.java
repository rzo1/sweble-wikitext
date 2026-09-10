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

package org.sweble.wikitext.engine.ext.convert;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spells numbers in English words (e.g. "forty-two") like the function
 * spell_number() of Module:ConvertNumeric, which is used by Module:Convert
 * for the "spell" option.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Module:ConvertNumeric">Module:ConvertNumeric</a>
 */
final class NumberSpeller
{
	private static final String[] ONES = {
			"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
			"ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
			"seventeen", "eighteen", "nineteen" };

	private static final String[] TENS = {
			null, null, "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety" };

	/** Names of groups of three digits (1 is "thousand"). */
	private static final Map<Integer, String> GROUPS = new HashMap<Integer, String>();

	/** Denominators which can be spelled: { singular, US singular, plural }. */
	private static final Map<Integer, String[]> DENOMINATORS = new HashMap<Integer, String[]>();

	private static final Pattern SCIENTIFIC_RX =
			Pattern.compile("^(-?)(\\d*)\\.?(\\d*)-?[Ee]([+-]?\\d+)$");

	private static final Pattern DECIMAL_RX = Pattern.compile("^(-?)(\\d*)(?:\\.(\\d*))?$");

	static
	{
		String[] names = {
				"thousand", "million", "billion", "trillion", "quadrillion", "quintillion",
				"sextillion", "septillion", "octillion", "nonillion", "decillion", "undecillion",
				"duodecillion", "tredecillion", "quattuordecillion", "quindecillion",
				"sexdecillion", "septendecillion", "octodecillion", "novemdecillion",
				"vigintillion", "unvigintillion", "duovigintillion", "tresvigintillion",
				"quattuorvigintillion", "quinquavigintillion", "sesvigintillion",
				"septemvigintillion", "octovigintillion", "novemvigintillion", "trigintillion",
				"untrigintillion", "duotrigintillion", "trestrigintillion",
				"quattuortrigintillion", "quinquatrigintillion", "sestrigintillion",
				"septentrigintillion", "octotrigintillion", "noventrigintillion",
				"quadragintillion" };
		for (int i = 0; i < names.length; i++)
		{
			GROUPS.put(i + 1, names[i]);
		}
		GROUPS.put(51, "quinquagintillion");
		GROUPS.put(61, "sexagintillion");
		GROUPS.put(71, "septuagintillion");
		GROUPS.put(81, "octogintillion");
		GROUPS.put(91, "nonagintillion");
		GROUPS.put(101, "centillion");

		DENOMINATORS.put(2, new String[] { "half", null, "halves" });
		DENOMINATORS.put(3, new String[] { "third", null, null });
		DENOMINATORS.put(4, new String[] { "quarter", "fourth", null });
		DENOMINATORS.put(5, new String[] { "fifth", null, null });
		DENOMINATORS.put(6, new String[] { "sixth", null, null });
		DENOMINATORS.put(8, new String[] { "eighth", null, null });
		DENOMINATORS.put(9, new String[] { "ninth", null, null });
		DENOMINATORS.put(10, new String[] { "tenth", null, null });
		DENOMINATORS.put(16, new String[] { "sixteenth", null, null });
	}

	private NumberSpeller()
	{
	}

	/**
	 * Spells a number (_numeral_to_english() in Module:ConvertNumeric).
	 *
	 * @param num The number (like "-12.5" or "1.2e6"), or null if there is
	 * only a fraction.
	 * @param numerator The numerator of a fraction or null.
	 * @param denominator The denominator of a fraction or null.
	 * @param capitalize Whether the first letter is uppercase.
	 * @param useAnd Whether "and" is used before the tens (British English,
	 * e.g. "one hundred and one").
	 * @param hyphenate Whether all spaces are replaced by hyphens (for
	 * adjectival use).
	 * @return The words, or null if the number cannot be spelled.
	 */
	static String spell(
			String num,
			String numerator,
			String denominator,
			boolean capitalize,
			boolean useAnd,
			boolean hyphenate)
	{
		final String negativeWord = "negative";
		String fractionText = "";
		if (numerator != null || denominator != null)
		{
			boolean finished = (num == null || num.isEmpty());
			String sign = "";
			if (numerator != null)
			{
				if (finished && numerator.startsWith("-"))
				{
					numerator = numerator.substring(1);
					sign = negativeWord + " ";
				}
			} else
			{
				numerator = "1";
			}
			if (!numerator.matches("\\d+") || denominator == null || !denominator.matches("\\d+")
					|| numerator.length() > 3 || denominator.length() > 3)
			{
				return null;
			}
			int n = Integer.parseInt(numerator);
			String[] dendata = DENOMINATORS.get(Integer.parseInt(denominator));
			if (dendata == null || n < 1 || n > 99)
			{
				return null;
			}
			boolean spUs = !useAnd;
			String numstr;
			String denstr;
			String sep = "-";
			if (n == 1)
			{
				denstr = (spUs && dendata[1] != null) ? dendata[1] : dendata[0];
				if (finished)
				{
					numstr = "one";
				} else if (denstr.matches("^[aeiou].*"))
				{
					numstr = "an";
					sep = " ";
				} else
				{
					numstr = "a";
					sep = " ";
				}
			} else
			{
				numstr = lessThan100(n);
				denstr = dendata[2];
				if (denstr == null)
				{
					denstr = ((spUs && dendata[1] != null) ? dendata[1] : dendata[0]) + "s";
				}
			}
			if (finished)
			{
				return finish(sign + numstr + sep + denstr, capitalize, hyphenate);
			}
			fractionText = " and " + numstr + sep + denstr;
		}

		num = scientificToDecimal(num);
		if (num.startsWith(NumberFormater.MINUS))
		{
			num = "-" + num.substring(NumberFormater.MINUS.length());
		} else if (num.startsWith("+"))
		{
			num = num.substring(1);
		}
		Matcher m = DECIMAL_RX.matcher(num);
		String decimalPlaces = m.matches() ? m.group(3) : null;
		if (decimalPlaces != null && decimalPlaces.isEmpty())
		{
			decimalPlaces = null; // like "123." from scientificToDecimal()
		}
		if (!m.matches() || (m.group(2).isEmpty() && decimalPlaces == null))
		{
			return null;
		}
		boolean negative = !m.group(1).isEmpty();
		String digits = m.group(2).isEmpty() ? "0" : m.group(2);

		// for each group of 3 digits except the last one
		StringBuilder s = new StringBuilder();
		while (digits.length() > 3)
		{
			if (s.length() > 0)
			{
				s.append(' ');
			}
			int groupNum = (digits.length() - 1) / 3;
			String group = GROUPS.get(groupNum);
			if (group == null)
			{
				return null;
			}
			int groupDigits = digits.length() - groupNum * 3;
			s.append(lessThan1000(Integer.parseInt(digits.substring(0, groupDigits)), false))
					.append(' ').append(group);
			digits = digits.substring(groupDigits).replaceFirst("^0*", "");
		}

		// the final three digits of the integer part
		if (s.length() > 0 && !digits.isEmpty())
		{
			s.append((digits.length() <= 2 && useAnd) ? " and " : " ");
		}
		if (s.length() == 0 || !digits.isEmpty())
		{
			s.append(lessThan1000(Integer.parseInt(digits), useAnd));
		}

		// "point" followed by the digits after the decimal mark
		if (decimalPlaces != null)
		{
			s.append(" point");
			for (int i = 0; i < decimalPlaces.length(); i++)
			{
				s.append(' ').append(ONES[decimalPlaces.charAt(i) - '0']);
			}
		}

		String result = s.toString().trim();
		if (negative)
		{
			result = negativeWord + " " + result;
		}
		result = result.replace("negative zero", "zero") + fractionText;
		return finish(result, capitalize, hyphenate);
	}

	private static String finish(String s, boolean capitalize, boolean hyphenate)
	{
		if (hyphenate)
		{
			s = s.replaceAll("\\s", "-");
		}
		if (capitalize && !s.isEmpty() && Character.isLowerCase(s.charAt(0)))
		{
			s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
		}
		return s;
	}

	private static String lessThan100(int num)
	{
		if (num < 20)
		{
			return ONES[num];
		} else if (num % 10 == 0)
		{
			return TENS[num / 10];
		}
		return TENS[num / 10] + "-" + ONES[num % 10];
	}

	private static String lessThan1000(int num, boolean useAnd)
	{
		if (num < 100)
		{
			return lessThan100(num);
		} else if (num % 100 == 0)
		{
			return ONES[num / 100] + " hundred";
		}
		return ONES[num / 100] + " hundred " + (useAnd ? "and " : "") + lessThan100(num % 100);
	}

	/**
	 * Converts a number in scientific notation to decimal notation without
	 * rounding (e.g. "1.23E5" to "123000."). Other text is returned unchanged.
	 */
	static String scientificToDecimal(String num)
	{
		Matcher m = SCIENTIFIC_RX.matcher(num);
		if (!m.matches())
		{
			return num;
		}
		int exponent = Integer.parseInt(m.group(4).replace("+", ""));
		boolean negative = !m.group(1).isEmpty();
		String mantissa = m.group(2) + m.group(3);
		int decimalPos = (num.indexOf('.') >= 0) ? m.group(2).length() + 1 : mantissa.length() + 1;

		// remove leading zeros unless the decimal point is in first position
		while (decimalPos > 1 && mantissa.startsWith("0"))
		{
			mantissa = mantissa.substring(1);
			decimalPos--;
		}
		// shift the decimal point right for an exponent > 0
		while (exponent > 0)
		{
			decimalPos++;
			exponent--;
			if (decimalPos > mantissa.length() + 1)
			{
				mantissa = mantissa + "0";
			}
			while (decimalPos > 1 && mantissa.startsWith("0"))
			{
				mantissa = mantissa.substring(1);
				decimalPos--;
			}
		}
		// shift the decimal point left for an exponent < 0
		while (exponent < 0)
		{
			if (decimalPos == 1)
			{
				mantissa = "0" + mantissa;
			} else
			{
				decimalPos--;
			}
			exponent++;
		}
		return (negative ? "-" : "") + mantissa.substring(0, decimalPos - 1) + "." + mantissa.substring(decimalPos - 1);
	}
}
