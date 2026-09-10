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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats numbers like Module:Convert on the English Wikipedia.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Module:Convert">Module:Convert</a>
 */
public final class NumberFormater
{
	/** Wikipedia uses '−' (\u2212) as minus sign. */
	static final String MINUS = "−";

	/**
	 * Maximum number of significant figures of an output value (same as
	 * {{#expr}} and Module:Convert).
	 */
	private static final int MAX_SIG_FIG = 14;

	/** Precision values with more digits cannot be formatted by Lua. */
	private static final int MAX_PRECISION = 99;

	private static final Pattern NUMBER_RX =
			Pattern.compile("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

	private static final Pattern NOT_A_NUMBER_RX =
			Pattern.compile("[+-]?(?:inf|infinity|nan)", Pattern.CASE_INSENSITIVE);

	private static final Pattern E_NOTATION_RX =
			Pattern.compile("^([\\d.]+)[eE]([+-]?\\d+)");

	private static final Pattern FRACTION_RX =
			Pattern.compile("^\\s*(\\+?)\\s*(.*?)\\s*(\\d+)\\s*(/+|⁄)\\s*(\\d+)\\s*$");

	private static final Pattern FRACTION_PREFIX_RX =
			Pattern.compile("^(\\d+)(\\.?\\d?)\\s*([+-])$");

	private static final Pattern CLEAN_RX =
			Pattern.compile("^(\\d*)(\\.?)(.*)$");

	private static final DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);

	// uses '−' (\u2212) as minus
	// Do not use these instances directly since DecimalFormat is not thread
	// safe. Use a clone instead (see sciFmt() and fmt()).
	private static final DecimalFormat sciFmt = new DecimalFormat("0.0E0;−#", symbols);
	private static final DecimalFormat fmt = new DecimalFormat("#0;−#", symbols);
	static
	{
		fmt.setRoundingMode(RoundingMode.HALF_UP);
		fmt.setGroupingSize(3);
		fmt.setGroupingUsed(true);
	}

	private NumberFormater()
	{
		throw new UnsupportedOperationException();
	}

	private static DecimalFormat sciFmt()
	{
		return (DecimalFormat) sciFmt.clone();
	}

	private static DecimalFormat fmt()
	{
		return (DecimalFormat) fmt.clone();
	}

	public static String formatScientific(double number)
	{
		String[] split = sciFmt().format(number).split("E");
		String supExp = asSuperscriptNumber(split[1]);
		return split[0] + "×10" + supExp;
	}

	public static String formatRegular(double number)
	{
		return fmt().format(number);
	}

	public static String formatRegular(BigDecimal number)
	{
		return fmt().format(number);
	}

	/**
	 * Rounds and formats a value to the given count of digits after the
	 * floating point.
	 *
	 * @param convertedValue The value to format.
	 * @param digitsAfterFloatingPoint Digits behind the floating point [0..16].
	 * @return The formated value as String.
	 */
	public static String formatNumberRounded(
			double convertedValue,
			int digitsAfterFloatingPoint)
	{
		final int MAX_DIGITS_AFTER_FLOATING_POINT = 16;
		if (digitsAfterFloatingPoint > MAX_DIGITS_AFTER_FLOATING_POINT)
		{
			digitsAfterFloatingPoint = MAX_DIGITS_AFTER_FLOATING_POINT;
		}

		DecimalFormat tmpFmt = fmt();
		tmpFmt.setMinimumFractionDigits(digitsAfterFloatingPoint);
		tmpFmt.setMaximumFractionDigits(digitsAfterFloatingPoint);

		return tmpFmt.format(convertedValue);
	}

	/**
	 * Formats the given value like Wikipedia does on default. This includes the
	 * conversion to scientific notation, adding of thousand separators and
	 * rounding on various scales (depending on the amount of digits).
	 *
	 * @param convertedValue The value to format.
	 * @param sigFig The count of significant figures to round.
	 * @return The formated value as string.
	 */
	protected static String formatNumberDefault(
			double convertedValue,
			int sigFig)
	{
		String convertedValStr;
		final double absValue = Math.abs(convertedValue);
		if (absValue < 1e-9 || absValue > 1e9)
		{
			convertedValStr = formatScientific(convertedValue);
		} else
		{
			BigDecimal bd = new BigDecimal(convertedValue);
			int prec = bd.precision() - bd.scale();
			if (sigFig > prec)
			{
				convertedValStr = formatNumberRounded(convertedValue, sigFig - prec);
			} else
			{
				bd = bd.round(new MathContext(sigFig, RoundingMode.HALF_UP));
				convertedValStr = formatRegular(bd);
			}
		}
		return convertedValStr;
	}

	/**
	 * Checks if the given number string is probably a valid value. Since the
	 * check is only made on appearing characters, there might be the chance,
	 * that a wrong used number syntax let a conversion with
	 * {@link #parseNumber(String)} fail anyway.
	 *
	 * @param numberStr The number as string to check.
	 * @return True if the string is most likely a valid value, otherwise false.
	 * @see #parseNumber(String)
	 */
	protected static boolean isNumberValid(final String numberStr)
	{
		return numberStr.matches("[0-9,.eE/⁄\\-\\+–−]+");
	}

	/**
	 * Tries to parse a number-string into a valid double value. The following
	 * notations are supported:
	 *
	 * "12"       = 12
	 * "1,234"    = 1234
	 * "12.3e-15" = 1.23e-14
	 * "1E3"      = 1000
	 * "−12"      = -12 (Unicode minus sign)
	 *
	 * Fractions:
	 * "1/2"    = 0.5
	 * "1⁄3"    = 0.33333333
	 * "2+1⁄2"  = 2.5
	 * "-2-1⁄2" = -2.5
	 * "1//2"   = 0.5
	 *
	 * @param numberStr The number as string to convert.
	 * @return The parsed number as double value.
	 * @throws NumberFormatException If the string is not a number or if the
	 * value is not finite.
	 * @see <a href="https://en.wikipedia.org/wiki/Template:Convert/doc#Numbers">Template:Convert/doc#Numbers</a>
	 */
	protected static double parseNumber(final String numberStr)
			throws NumberFormatException
	{
		// replace all the pesky en dashes
		return parseValue(numberStr.replace('–', '-')).getValue();
	}

	/**
	 * Parses an input value of {{convert}} (extract_number() in
	 * Module:Convert).
	 *
	 * @param text The value as given in the template argument.
	 * @return The parsed value.
	 * @throws NumberFormatException If the text is not a valid number. The
	 * message of the exception describes the problem.
	 */
	public static ParsedNumber parseValue(final String text)
			throws NumberFormatException
	{
		return parseValue(text, Options.DEFAULT, null);
	}

	/**
	 * @param options The options to format the value for display.
	 * @param roundInput Null or the precision to which the displayed value is
	 * rounded (option "adj=ri0" etc.).
	 * @see #parseValue(String)
	 */
	static ParsedNumber parseValue(
			final String text,
			final Options options,
			final Integer roundInput)
			throws NumberFormatException
	{
		final String trimmed = text.trim();
		String clean = trimmed.replace(",", ""); // remove thousand separators
		if (clean.isEmpty())
		{
			throw new NumberFormatException("Needs the number to be converted");
		}

		boolean isNegative = false;
		String properSign = "";
		boolean singular = false;
		String show = null;
		int denominator = 0;
		double value;
		double altValue = Double.NaN;
		String[] fraction = null;
		int fractionStyle = 0;

		if (NOT_A_NUMBER_RX.matcher(clean).matches())
		{
			throw new NumberFormatException("Number has overflowed");
		}

		if (NUMBER_RX.matcher(clean).matches())
		{
			value = Double.parseDouble(clean);
			char sign = clean.charAt(0);
			if (sign == '+' || sign == '-')
			{
				properSign = (sign == '+') ? "+" : MINUS;
				clean = clean.substring(1);
			}
			if (value < 0)
			{
				isNegative = true;
				value = -value;
			}
		} else
		{
			for (String prefix : new String[] { "-", MINUS, "&minus;" })
			{
				if (clean.startsWith(prefix))
				{
					clean = clean.substring(prefix.length());
					if (clean.isEmpty() || Character.isWhitespace(clean.charAt(0)))
					{
						throw invalidNumber(trimmed);
					}
					isNegative = true;
					properSign = MINUS;
					break;
				}
			}

			if (isNegative && NUMBER_RX.matcher(clean).matches())
			{
				value = Double.parseDouble(clean);
			} else
			{
				Matcher m = FRACTION_RX.matcher(clean);
				if (!m.matches())
				{
					throw invalidNumber(trimmed);
				}
				String leadingPlus = m.group(1);
				String prefix = m.group(2);
				if (isNegative && !leadingPlus.isEmpty())
				{
					throw invalidNumber(trimmed);
				}

				String wholeStr = "";
				double whole = 0d;
				if (!prefix.isEmpty())
				{
					Matcher p = FRACTION_PREFIX_RX.matcher(prefix);
					if (!p.matches())
					{
						throw invalidNumber(trimmed);
					}
					if (p.group(2).isEmpty())
					{
						wholeStr = p.group(1);
					} else if (p.group(2).length() == 2)
					{
						wholeStr = p.group(1) + p.group(2);
					} else
					{
						throw invalidNumber(trimmed);
					}
					if (!p.group(3).equals(isNegative ? "-" : "+"))
					{
						throw invalidNumber(trimmed);
					}
					whole = Double.parseDouble(wholeStr);
				}

				double numerator = Double.parseDouble(m.group(3));
				double denominatorValue = Double.parseDouble(m.group(5));
				denominator = (int) Math.min(denominatorValue, Integer.MAX_VALUE);
				value = whole + numerator / denominatorValue;
				if (!isFinite(value))
				{
					throw invalidNumber(trimmed);
				}
				// For the hand unit, "12.1+3/4" means 12 hands 1.75 inches.
				altValue = whole + numerator / (denominatorValue * 10);

				// one or two slashes select the style
				fractionStyle = (m.group(4).length() == 1) ? 1 : 2;
				fraction = new String[] { leadingPlus + wholeStr, m.group(3), m.group(5) };
				show = formatFraction(isNegative, fraction[0], fraction[1], fraction[2], fractionStyle, options);
				singular = (value <= 1);
			}
		}

		if (!isFinite(value))
		{
			throw new NumberFormatException("Number has overflowed");
		}

		boolean isScientific = false;
		String rounded = null;
		if (show == null)
		{
			singular = (value == 1);
			Matcher m = E_NOTATION_RX.matcher(clean);
			if (m.find())
			{
				String significand = m.group(1);
				if (roundInput != null)
				{
					significand = toFixed(Double.parseDouble(significand) + 2e-14, roundInput);
				}
				show = properSign + withExponent(significand, m.group(2), options);
				isScientific = true;
			} else
			{
				if (roundInput != null)
				{
					rounded = toFixed(value + 2e-14, roundInput);
					singular = (Double.parseDouble(rounded) == 1);
				}
				show = properSign + withSeparator((rounded != null) ? rounded : clean, options);
			}
		}

		if (isNegative && value != 0)
		{
			value = -value;
			altValue = -(Double.isNaN(altValue) ? value : altValue);
		}
		if (Double.isNaN(altValue))
		{
			altValue = value;
		}

		ParsedNumber result = new ParsedNumber(value, clean, show, singular, denominator, isScientific);
		result.altValue = altValue;
		result.negative = isNegative;
		result.sign = properSign;
		result.rounded = rounded;
		result.fraction = fraction;
		result.fractionStyle = fractionStyle;
		return result;
	}

	/**
	 * Formats a fraction like Module:Convert (format_fraction()), which uses
	 * the markup of {{frac}} or {{sfrac}}.
	 *
	 * @param negative Whether the value is negative.
	 * @param whole The unsigned whole number before the fraction or "".
	 * @param numerator The numerator.
	 * @param denominator The denominator.
	 * @param style 1 for a fraction with a slash like {{frac}} or 2 for a
	 * stacked fraction like {{sfrac}}.
	 * @param options The options to format the whole number.
	 * @return The fraction as wikitext.
	 */
	static String formatFraction(
			boolean negative,
			String whole,
			String numerator,
			String denominator,
			int style,
			Options options)
	{
		final String sign = negative ? MINUS : "";
		final boolean hasWhole = (whole != null && !whole.isEmpty());
		final String wholeShow = hasWhole ? withSeparator(whole, options) : null;
		if (style == 2)
		{
			if (!hasWhole)
			{
				return "<span class=\"sfrac tion\">" + sign + "<span class=\"num\">" + numerator
						+ "</span><span class=\"sr-only\">/</span><span class=\"den\">" + denominator
						+ "</span></span>";
			}
			return "<span class=\"sfrac\">" + sign + wholeShow
					+ "<span class=\"sr-only\">+</span><span class=\"tion\"><span class=\"num\">" + numerator
					+ "</span><span class=\"sr-only\">/</span><span class=\"den\">" + denominator
					+ "</span></span></span>";
		}
		if (!hasWhole)
		{
			return "<span class=\"frac\">" + sign + "<span class=\"num\">" + numerator
					+ "</span>⁄<span class=\"den\">" + denominator + "</span></span>";
		}
		return "<span class=\"frac\">" + sign + wholeShow
				+ "<span class=\"sr-only\">+</span><span class=\"num\">" + numerator
				+ "</span>⁄<span class=\"den\">" + denominator + "</span></span>";
	}

	private static NumberFormatException invalidNumber(String text)
	{
		return new NumberFormatException("Value \"" + text + "\" must be a number");
	}

	private static boolean isFinite(double value)
	{
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}

	/**
	 * Rounds a value to the given precision (digits after the decimal mark, or
	 * if negative, digits before the decimal mark set to zero) and formats it
	 * (cvtround() and format_number() in Module:Convert).
	 *
	 * @param value The value to round.
	 * @param precision The precision.
	 * @return The rounded and formatted value.
	 */
	public static FormattedNumber formatRounded(double value, int precision)
	{
		return formatRounded(value, precision, false);
	}

	/**
	 * @param isScientific True if the input value used scientific notation,
	 * which makes the output use scientific notation for smaller values.
	 * @see #formatRounded(double, int)
	 */
	static FormattedNumber formatRounded(double value, int precision, boolean isScientific)
	{
		return formatRounded(value, precision, isScientific, Options.DEFAULT);
	}

	/**
	 * @param options The options to format the value.
	 * @see #formatRounded(double, int, boolean)
	 */
	static FormattedNumber formatRounded(
			double value,
			int precision,
			boolean isScientific,
			Options options)
	{
		if (precision > MAX_PRECISION)
		{
			throw new IllegalArgumentException("Precision \"" + precision + "\" is too large");
		}

		boolean isNegative = value < 0;
		double absValue = Math.abs(value);
		String show;
		Integer exponent = null;
		if (precision >= 0)
		{
			// Fudge to handle common cases of bad rounding (as Module:Convert does).
			double fudge = (precision <= 8) ? 2e-14 : 0d;
			show = toFixed(absValue + fudge, precision);
		} else
		{
			int digits = -precision;
			show = toFixed(absValue / Math.pow(10, digits), 0);
			if (!show.equals("0"))
			{
				exponent = show.length() + digits;
			}
		}
		return formatShow(show, exponent, isNegative, isScientific, options);
	}

	/**
	 * Rounds a value to the given number of significant figures and formats it
	 * (make_sigfig() and format_number() in Module:Convert).
	 *
	 * @param value The value to round.
	 * @param sigFig The number of significant figures.
	 * @return The rounded and formatted value.
	 */
	public static FormattedNumber formatSigFig(double value, int sigFig)
	{
		return formatSigFig(value, sigFig, false);
	}

	/**
	 * @param isScientific True if the input value used scientific notation,
	 * which makes the output use scientific notation for smaller values.
	 * @see #formatSigFig(double, int)
	 */
	static FormattedNumber formatSigFig(double value, int sigFig, boolean isScientific)
	{
		String[] digits = makeSigFig(Math.abs(value), sigFig);
		return formatShow(digits[0], Integer.valueOf(digits[1]), value < 0, isScientific, Options.DEFAULT);
	}

	/**
	 * Rounds a value to the given number of significant figures (make_sigfig()
	 * in Module:Convert).
	 *
	 * @param absValue The value (not negative).
	 * @param sigFig The number of significant figures.
	 * @return The digits (with an implied dot before them) and the exponent
	 * to shift the implied dot.
	 */
	static String[] makeSigFig(double absValue, int sigFig)
	{
		if (sigFig <= 0)
		{
			sigFig = 1;
		} else if (sigFig > MAX_SIG_FIG)
		{
			sigFig = MAX_SIG_FIG;
		}

		String digits;
		int exponent;
		if (absValue == 0)
		{
			digits = zeros(sigFig);
			exponent = 1;
		} else
		{
			double log = Math.log10(absValue);
			exponent = (int) log;
			double fraction = log - exponent;
			if (fraction >= 0)
			{
				fraction -= 1;
				exponent += 1;
			}
			digits = toFixed(Math.pow(10, fraction + sigFig), 0);
			if (digits.length() > sigFig)
			{
				// Overflow (for sigFig=3: like 0.9999 rounding to "1000")
				digits = digits.substring(0, sigFig);
				exponent += 1;
			}
		}
		return new String[] { digits, String.valueOf(exponent) };
	}

	/**
	 * Formats a rounded value (format_number() in Module:Convert).
	 *
	 * @param show If exponent is null, the unsigned value as digits with an
	 * optional dot. Otherwise the digits of the value with an implied dot in
	 * front of them.
	 * @param exponent Null or the exponent to shift the implied dot.
	 * @param isNegative Whether the value is negative.
	 * @param isScientific True if the input value used scientific notation.
	 * @return The formatted value.
	 */
	static FormattedNumber formatShow(
			String show,
			Integer exponent,
			boolean isNegative,
			boolean isScientific)
	{
		return formatShow(show, exponent, isNegative, isScientific, Options.DEFAULT);
	}

	/**
	 * @param options The options to format the value.
	 * @see #formatShow(String, Integer, boolean, boolean)
	 */
	static FormattedNumber formatShow(
			String show,
			Integer exponent,
			boolean isNegative,
			boolean isScientific,
			Options options)
	{
		final boolean singular;
		if (exponent != null)
		{
			singular = (exponent == 1) && show.matches("10*");
		} else
		{
			singular = show.equals("1") || show.matches("1\\.0*");
		}

		// these control when scientific notation (exponent) is used
		final int xhi = isScientific ? 4 : 10;
		final int xlo = isScientific ? 2 : 4;

		String sign = isNegative ? MINUS : "";
		int maxLen = MAX_SIG_FIG;
		if (exponent == null)
		{
			Matcher m = CLEAN_RX.matcher(show);
			m.matches();
			String integer = m.group(1);
			String dot = m.group(2);
			String decimals = m.group(3);
			if (integer.equals("0") || integer.isEmpty())
			{
				int zeroCount = 0;
				while (zeroCount < decimals.length() && decimals.charAt(zeroCount) == '0')
				{
					zeroCount++;
				}
				String figs = decimals.substring(zeroCount);
				if (figs.isEmpty())
				{
					if (zeroCount > maxLen)
					{
						show = "0." + decimals.substring(0, maxLen);
					}
				} else if (zeroCount >= xlo)
				{
					show = figs;
					exponent = -zeroCount;
				} else if (figs.length() > maxLen)
				{
					show = "0." + decimals.substring(0, zeroCount) + figs.substring(0, maxLen);
				}
			} else if (integer.length() >= xhi)
			{
				show = integer + decimals;
				exponent = integer.length();
			} else
			{
				maxLen += dot.length();
				if (show.length() > maxLen)
				{
					show = show.substring(0, maxLen);
				}
			}
		}

		if (exponent != null)
		{
			if (show.length() > maxLen)
			{
				show = show.substring(0, maxLen);
			}
			if (exponent > xhi || exponent <= -xlo
					|| (exponent == xhi && !show.equals("1" + zeros(xhi - 1))))
			{
				String significand = show;
				if (show.length() > 1)
				{
					significand = show.charAt(0) + "." + show.substring(1);
				}
				return new FormattedNumber(
						sign + withExponent(significand, String.valueOf(exponent - 1), options),
						"." + show,
						exponent,
						true,
						singular,
						sign);
			}
			if (exponent >= show.length())
			{
				show = show + zeros(exponent - show.length());
			} else if (exponent <= 0)
			{
				show = "0." + zeros(-exponent) + show;
			} else
			{
				show = show.substring(0, exponent) + "." + show.substring(exponent);
			}
		}

		if (isNegative && show.matches("0.?0*"))
		{
			sign = ""; // don't show minus if result is negative but rounds to zero
		}
		return new FormattedNumber(sign + withSeparator(show, options), show, null, false, singular, sign);
	}

	/**
	 * Formats a number with a given count of digits after the decimal mark like
	 * C's printf("%.*f") which is used by Module:Convert.
	 */
	static String toFixed(double value, int digits)
	{
		return new BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).toPlainString();
	}

	/**
	 * Inserts thousand separators into the integer part of the given unsigned
	 * number (e.g. "1234.5678" becomes "1,234.5678"). A trailing dot is
	 * removed.
	 *
	 * @param text Unsigned number with optional '.' decimal mark.
	 * @return The formatted number.
	 */
	public static String withSeparator(String text)
	{
		return withSeparator(text, Options.DEFAULT);
	}

	/**
	 * Inserts separators like Module:Convert (with_separator()) according to
	 * the "comma" option.
	 *
	 * @param text Unsigned number with optional '.' decimal mark.
	 * @param options The options.
	 * @return The formatted number (which uses markup with comma=gaps).
	 */
	static String withSeparator(String text, Options options)
	{
		if (text.endsWith("."))
		{
			text = text.substring(0, text.length() - 1);
		}
		if (text.length() < 4 || options.noComma)
		{
			return text;
		}

		// digit_groups() in Module:Convert
		int dot = text.indexOf('.');
		int lenLeft = (dot >= 0) ? dot : text.length();
		int lenRight = (dot >= 0) ? text.length() - dot - 1 : -1;
		List<Integer> groups = new ArrayList<Integer>();
		int run = lenLeft;
		int n;
		if (run < 4 || (run == 4 && options.comma5))
		{
			n = options.gaps ? run : text.length();
		} else
		{
			n = (run % 3 == 0) ? 3 : run % 3;
		}
		while (run > 0)
		{
			groups.add(n);
			run -= n;
			n = 3;
		}
		if (lenRight >= 0)
		{
			if (groups.isEmpty())
			{
				groups.add(0);
			}
			int last = groups.size() - 1;
			if (options.gaps && lenRight > 3)
			{
				boolean want4 = !options.gaps3; // no gap before a trailing single digit
				boolean isFirst = true;
				run = lenRight;
				while (run > 0)
				{
					n = (want4 && run == 4) ? 4 : Math.min(run, 3);
					if (isFirst)
					{
						isFirst = false;
						groups.set(last, groups.get(last) + 1 + n);
					} else
					{
						groups.add(n);
					}
					run -= n;
				}
			} else
			{
				groups.set(last, groups.get(last) + 1 + lenRight);
			}
		}

		List<String> parts = new ArrayList<String>(groups.size());
		int pos = 0;
		for (int length : groups)
		{
			int end = Math.min(pos + length, text.length());
			parts.add(text.substring(Math.min(pos, end), end));
			pos += length;
		}

		if (options.gaps)
		{
			if (parts.size() <= 1)
			{
				return parts.isEmpty() ? "" : parts.get(0);
			}
			final String gap = "<span style=\"margin-left: 0.25em\">";
			StringBuilder sb = new StringBuilder("<span style=\"white-space: nowrap\">");
			sb.append(parts.get(0));
			for (int k = 1; k < parts.size(); k++)
			{
				sb.append(gap).append(parts.get(k)).append("</span>");
			}
			return sb.append("</span>").toString();
		}
		return String.join(",", parts);
	}

	static String withExponent(String significand, String exponent, Options options)
	{
		return withSeparator(significand, options) + "×10" + asSuperscriptNumber(exponent);
	}

	/**
	 * Formats a number like C's printf("%.*g") (which Module:Convert uses via
	 * Lua's string.format() and tostring()).
	 *
	 * @param value The value.
	 * @param precision The number of significant digits.
	 * @return The formatted number (without trailing zeros).
	 */
	static String toGeneral(double value, int precision)
	{
		if (value == 0)
		{
			return "0";
		}
		BigDecimal bd = new BigDecimal(Math.abs(value)).round(new MathContext(precision, RoundingMode.HALF_EVEN));
		int exponent = bd.precision() - bd.scale() - 1;
		String sign = (value < 0) ? "-" : "";
		if (exponent < -4 || exponent >= precision)
		{
			String digits = bd.unscaledValue().toString();
			digits = digits.replaceFirst("0+$", "");
			String mantissa = digits.substring(0, 1)
					+ ((digits.length() > 1) ? "." + digits.substring(1) : "");
			String exp = String.valueOf(Math.abs(exponent));
			return sign + mantissa + "e" + ((exponent < 0) ? "-" : "+") + ((exp.length() < 2) ? "0" : "") + exp;
		}
		String plain = bd.setScale(Math.max(precision - 1 - exponent, 0), RoundingMode.HALF_EVEN).toPlainString();
		if (plain.indexOf('.') >= 0)
		{
			plain = plain.replaceFirst("0+$", "").replaceFirst("\\.$", "");
		}
		return sign + plain;
	}

	/**
	 * @return The value as Lua's tostring() converts it (like "%.14g").
	 */
	static String luaToString(double value)
	{
		return toGeneral(value, 14);
	}

	/**
	 * Options which change how numbers are formatted.
	 */
	static final class Options
	{
		static final Options DEFAULT = new Options();

		/** No separators (comma=off). */
		boolean noComma;

		/** Separators only for numbers with five or more digits (comma=5). */
		boolean comma5;

		/** Gaps instead of commas (comma=gaps). */
		boolean gaps;

		/** Gaps in groups of three digits only (comma=gaps3). */
		boolean gaps3;
	}

	private static String zeros(int count)
	{
		StringBuilder sb = new StringBuilder(Math.max(count, 0));
		for (int i = 0; i < count; i++)
		{
			sb.append('0');
		}
		return sb.toString();
	}

	/**
	 * Replaces all digit characters and the minus-sing with the corresponding
	 * Unicode superscript symbols.
	 *
	 * @param numStr The String containing the numbers.
	 * @return The number string with the substituted characters.
	 */
	public static String asSuperscriptNumber(String numStr)
	{
		numStr = numStr.replaceAll("0", "⁰"); // \u2070
		numStr = numStr.replaceAll("1", "¹"); // \u00B9
		numStr = numStr.replaceAll("2", "²"); // \u00B2
		numStr = numStr.replaceAll("3", "³"); // \u00B3
		numStr = numStr.replaceAll("4", "⁴"); // \u2074
		numStr = numStr.replaceAll("5", "⁵"); // \u2075
		numStr = numStr.replaceAll("6", "⁶"); // \u2076
		numStr = numStr.replaceAll("7", "⁷"); // \u2077
		numStr = numStr.replaceAll("8", "⁸"); // \u2078
		numStr = numStr.replaceAll("9", "⁹"); // \u2079
		numStr = numStr.replaceAll("-", "⁻"); // \u207B
		return numStr;
	}

	/**
	 * Replaces all digit characters and the minus-sing with the corresponding
	 * Unicode subscript symbols.
	 *
	 * @param numStr The String containing the numbers.
	 * @return The number string with the substituted characters.
	 */
	public static String asSubscriptNumber(String numStr)
	{
		numStr = numStr.replaceAll("0", "₀"); // \u2080
		numStr = numStr.replaceAll("1", "₁"); // \u2081
		numStr = numStr.replaceAll("2", "₂"); // \u2082
		numStr = numStr.replaceAll("3", "₃"); // \u2083
		numStr = numStr.replaceAll("4", "₄"); // \u2084
		numStr = numStr.replaceAll("5", "₅"); // \u2085
		numStr = numStr.replaceAll("6", "₆"); // \u2086
		numStr = numStr.replaceAll("7", "₇"); // \u2087
		numStr = numStr.replaceAll("8", "₈"); // \u2088
		numStr = numStr.replaceAll("9", "₉"); // \u2089
		numStr = numStr.replaceAll("-", "₋"); // \u208B
		return numStr;
	}

	// =========================================================================

	/**
	 * An input value of {{convert}}.
	 */
	public static final class ParsedNumber
	{
		private final double value;
		private final String clean;
		private final String show;
		private final boolean singular;
		private final int denominator;
		private final boolean scientific;
		private double altValue;
		private boolean negative;
		private String sign;
		private String rounded;
		private String[] fraction;
		private int fractionStyle;

		private ParsedNumber(
				double value,
				String clean,
				String show,
				boolean singular,
				int denominator,
				boolean scientific)
		{
			this.value = value;
			this.clean = clean;
			this.show = show;
			this.singular = singular;
			this.denominator = denominator;
			this.scientific = scientific;
		}

		/**
		 * @return The value.
		 */
		public double getValue()
		{
			return value;
		}

		/**
		 * @return The unsigned value as given, without separators (e.g.
		 * "1234.50" or "1.2e3"). Used to determine the precision.
		 */
		public String getClean()
		{
			return clean;
		}

		/**
		 * @return The value formatted for display (e.g. "−1,234.50").
		 */
		public String getShow()
		{
			return show;
		}

		/**
		 * @return True if a unit name after this value is singular.
		 */
		public boolean isSingular()
		{
			return singular;
		}

		/**
		 * @return The denominator if the value was given as a fraction,
		 * otherwise 0.
		 */
		public int getDenominator()
		{
			return denominator;
		}

		/**
		 * @return True if the value was given in e-notation (e.g. "1.2e3").
		 */
		public boolean isScientific()
		{
			return scientific;
		}

		/**
		 * @return The value for the hand unit, which differs from the value
		 * if a fraction is used ("12.1+3/4" means 12 hands 1.75 inches).
		 */
		public double getAltValue()
		{
			return altValue;
		}

		/**
		 * @return True if the value is negative (or "−0").
		 */
		boolean isNegative()
		{
			return negative;
		}

		/**
		 * @return The sign as shown: "", "+" or "−".
		 */
		String getSign()
		{
			return sign;
		}

		/**
		 * @return The unsigned rounded value (with the adj=ri option) or null.
		 */
		String getRounded()
		{
			return rounded;
		}

		/**
		 * @return The unsigned whole number (including a leading "+"),
		 * numerator and denominator of a fraction, or null if the value is
		 * not a fraction.
		 */
		String[] getFraction()
		{
			return fraction;
		}

		/**
		 * @return 1 for a fraction like "1/2", 2 for a stacked fraction like
		 * "1//2" or 0 if the value is not a fraction.
		 */
		int getFractionStyle()
		{
			return fractionStyle;
		}
	}

	/**
	 * A rounded and formatted output value of {{convert}}.
	 */
	public static final class FormattedNumber
	{
		private final String show;
		private final String clean;
		private final Integer exponent;
		private final boolean scientific;
		private final boolean singular;
		private final String sign;

		private FormattedNumber(
				String show,
				String clean,
				Integer exponent,
				boolean scientific,
				boolean singular,
				String sign)
		{
			this.show = show;
			this.clean = clean;
			this.exponent = exponent;
			this.scientific = scientific;
			this.singular = singular;
			this.sign = sign;
		}

		/**
		 * @return The unsigned value (e.g. "1234.5"), or if scientific
		 * notation is used, the digits with an implied dot before them.
		 */
		String getClean()
		{
			return clean;
		}

		/**
		 * @return The exponent for the clean digits or null.
		 */
		Integer getExponent()
		{
			return exponent;
		}

		/**
		 * @return The sign as shown: "" or "−".
		 */
		String getSign()
		{
			return sign;
		}

		/**
		 * @return The formatted value including the sign (e.g. "−1,234.5").
		 */
		public String getShow()
		{
			return show;
		}

		/**
		 * @return True if scientific notation is used (e.g. "1.2×10¹²").
		 */
		public boolean isScientific()
		{
			return scientific;
		}

		/**
		 * @return True if a unit name after this value is singular.
		 */
		public boolean isSingular()
		{
			return singular;
		}

		/**
		 * @return The absolute value after rounding.
		 */
		public double getAbsValue()
		{
			double value = Double.parseDouble(clean);
			if (exponent != null)
			{
				value = value * Math.pow(10, exponent);
			}
			return value;
		}

		/**
		 * @return The number of digits after the decimal mark.
		 */
		public int getDecimals()
		{
			int dot = clean.indexOf('.');
			return (scientific || dot < 0) ? 0 : clean.length() - dot - 1;
		}
	}
}
