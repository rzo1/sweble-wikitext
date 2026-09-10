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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

/**
 * Evaluates the expressions of the {@code #expr} and {@code #ifexpr} parser
 * functions.
 *
 * Follows the ExprParser of MediaWiki's ParserFunctions extension as it
 * behaves on PHP 8.3. Operands are either PHP integers ({@link Long}) or PHP
 * floats ({@link Double}), every operation yields the type PHP would yield and
 * results are converted to strings the way PHP does with its default
 * {@code precision} of 14 significant digits.
 */
public class ExprParser
{
	private static final int maxStackSize = 100;

	/**
	 * PHP's default {@code precision} setting, used when a float is converted
	 * to a string.
	 */
	private static final int PHP_PRECISION = 14;

	private static final MathContext PHP_PRECISION_CONTEXT =
			new MathContext(PHP_PRECISION, RoundingMode.HALF_EVEN);

	private static final double TWO_POW_63 = 0x1p63;

	private static final double TWO_POW_64 = 0x1p64;

	private static final double[] EXACT_POWERS_OF_TEN = {
			1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
			1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22 };

	private static final double[] LOG10_STEPS = {
			1e-8, 1e-7, 1e-6, 1e-5, 1e-4, 1e-3, 1e-2, 1e-1, 1e0, 1e1, 1e2,
			1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13,
			1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22 };

	private static final Map<String, Token> TOKENS = new HashMap<String, Token>();

	static
	{
		TOKENS.put("(", Token.LPAREN);
		TOKENS.put(")", Token.RPAREN);
		TOKENS.put("!=", Token.NEQ);
		TOKENS.put("*", Token.TIMES);
		TOKENS.put("+", Token.PLUS);
		//tokens.put("+", Token.POS);
		TOKENS.put("-", Token.MINUS);
		//tokens.put("-", Token.NEG);
		TOKENS.put("/", Token.DIVIDE);
		TOKENS.put("<", Token.LE);
		TOKENS.put("<=", Token.LEQ);
		TOKENS.put("<>", Token.NEQ);
		TOKENS.put("=", Token.EQ);
		TOKENS.put(">", Token.GR);
		TOKENS.put(">=", Token.GEQ);
		TOKENS.put("^", Token.POW);
		TOKENS.put("abs", Token.ABS);
		TOKENS.put("acos", Token.ARCCOS);
		TOKENS.put("and", Token.AND);
		TOKENS.put("asin", Token.ARCSINE);
		TOKENS.put("atan", Token.ARCTAN);
		TOKENS.put("ceil", Token.CEIL);
		TOKENS.put("cos", Token.COSINE);
		TOKENS.put("div", Token.DIVIDE);
		TOKENS.put("e", Token.E);
		//tokens.put("e", Token.SCIENTIFIC);
		TOKENS.put("exp", Token.EXP);
		TOKENS.put("floor", Token.FLOOR);
		TOKENS.put("fmod", Token.FMOD);
		TOKENS.put("ln", Token.LN);
		TOKENS.put("mod", Token.MOD);
		TOKENS.put("not", Token.NOT);
		TOKENS.put("or", Token.OR);
		TOKENS.put("pi", Token.PI);
		TOKENS.put("round", Token.ROUND);
		TOKENS.put("sin", Token.SINE);
		TOKENS.put("sqrt", Token.SQRT);
		TOKENS.put("tan", Token.TANGENS);
		TOKENS.put("trunc", Token.TRUNC);
	}

	// =====================================================================

	private final Stack<Number> operands = new Stack<Number>();

	private final Stack<Token> operators = new Stack<Token>();

	private Production expecting;

	// =====================================================================

	/**
	 * http://montcs.bloomu.edu/~bobmon/Information/RPN/infix2rpn.shtml
	 *
	 * @throws ExprError
	 */
	public String parse(String expr) throws ExprError
	{
		operands.clear();
		operators.clear();
		expecting = Production.EXPR;
		int i = 0;

		expr = unescape(expr);

		while (i < expr.length())
		{
			if (operands.size() > maxStackSize
					|| operators.size() > maxStackSize)
				throw new ExprError("stack_exhausted");

			char ch = expr.charAt(i);

			if (isWs(ch))
			{
				i = skipWs(expr, i);
				continue;
			}
			else if (isNumberChar(ch))
			{
				expect(Production.EXPR, "unexpected_number");
				i = pushOperand(expr, i);
				expecting = Production.OPERATOR;
				continue;
			}
			else
			{
				String word = null;
				Token token = null;
				if (isAlphaChar(ch))
				{
					word = parseWordToken(expr, i).toLowerCase(Locale.ROOT);
					token = TOKENS.get(word);
					if (token == null)
						throw new ExprError("unrecognised_word", word);
				}
				else
				{
					if (i + 1 < expr.length())
					{
						// Try two-character operators
						word = expr.substring(i, i + 2);
						token = TOKENS.get(word);
					}

					if (token == null)
					{
						// Try one-character operators
						word = String.valueOf(ch);
						token = TOKENS.get(word);
					}

					if (token == null)
						throw new ExprError(
								"unrecognised_punctuation",
								new String(Character.toChars(expr.codePointAt(i))));
				}

				i += word.length();

				switch (token)
				{

				// -- Constants ----------------------------------------

					case E:
					{
						if (expecting == Production.OPERATOR)
						{
							processBinaryOp(Token.SCIENTIFIC, word);
							continue;
						}
						token.apply(operands);
						expecting = Production.OPERATOR;
						continue;
					}
					case PI:
					{
						expect(Production.EXPR, "unexpected_number");
						token.apply(operands);
						expecting = Production.OPERATOR;
						continue;
					}

					// -- Unary operators ----------------------------------

					case NOT:
					case SINE:
					case COSINE:
					case TANGENS:
					case ARCSINE:
					case ARCCOS:
					case ARCTAN:
					case EXP:
					case LN:
					case ABS:
					case FLOOR:
					case TRUNC:
					case CEIL:
					case SQRT:
					{
						expect(Production.EXPR, "unexpected_operator", word);
						operators.push(token);
						continue;
					}

					// -- Binary or Unary ----------------------------------

					case PLUS:
					case MINUS:
					{
						if (expecting == Production.EXPR)
						{
							operators.push(token == Token.PLUS ?
									Token.POS :
									Token.NEG);
						}
						else
						{
							processBinaryOp(token, word);
						}
						continue;
					}

					// -- Binary operators ---------------------------------

					case EQ:
					case NEQ:
					case LE:
					case GR:
					case LEQ:
					case GEQ:
					case TIMES:
					case DIVIDE:
					case MOD:
					case FMOD:
					case POW:
					case ROUND:
					case AND:
					case OR:
					{
						processBinaryOp(token, word);
						continue;
					}

					// -- Parentheses --------------------------------------

					case LPAREN:
					{
						expect(Production.EXPR, "unexpected_operator", word);
						operators.push(token);
						continue;
					}

					case RPAREN:
					{
						Token lastOp = null;
						while (!operators.isEmpty())
						{
							lastOp = operators.peek();
							if (lastOp == Token.LPAREN)
								break;

							lastOp.apply(operands);
							operators.pop();
						}

						if (lastOp != Token.LPAREN)
							throw new ExprError("unexpected_closing_bracket");

						operators.pop();
						expecting = Production.OPERATOR;
						continue;
					}

					default:
						throw new AssertionError();
				}
			}
		}

		while (!operators.isEmpty())
		{
			Token op = operators.pop();
			if (op == Token.LPAREN)
				throw new ExprError("unclosed_bracket");

			op.apply(operands);
		}

		return implode("<br />\n", operands);
	}

	// =====================================================================

	private String unescape(String expr)
	{
		expr = expr.replace("&lt;", "<");
		expr = expr.replace("&gt;", ">");
		expr = expr.replace("&minus;", "-");
		expr = expr.replace("\u2212", "-");
		return expr;
	}

	// =====================================================================

	private boolean isWs(char ch)
	{
		return ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n';
	}

	private int skipWs(String expr, int i)
	{
		int j = i + 1;
		while (j < expr.length() && isWs(expr.charAt(j)))
			++j;
		return j;
	}

	// =====================================================================

	private static boolean isDigit(char ch)
	{
		return ch >= '0' && ch <= '9';
	}

	private static boolean isNumberChar(char ch)
	{
		return ch == '.' || isDigit(ch);
	}

	private int pushOperand(String expr, int i)
	{
		int j = i + 1;
		while (j < expr.length())
		{
			char ch = expr.charAt(j);
			if (!isNumberChar(ch))
				break;
			++j;
		}

		operands.push(parseNumber(expr.substring(i, j)));

		return j;
	}

	/**
	 * Converts a run of digits and decimal points like PHP's (float) cast: only
	 * the leading numeric prefix counts, a second decimal point and everything
	 * after it is silently dropped.
	 */
	private static double parseNumber(String number)
	{
		int end = skipDigits(number, 0);
		if (end < number.length() && number.charAt(end) == '.')
			end = skipDigits(number, end + 1);

		String prefix = number.substring(0, end);
		if (prefix.equals("."))
			return 0.;

		return Double.parseDouble(prefix);
	}

	private static int skipDigits(String number, int i)
	{
		while (i < number.length() && isDigit(number.charAt(i)))
			++i;
		return i;
	}

	// =====================================================================

	private boolean isAlphaChar(char ch)
	{
		return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
	}

	private String parseWordToken(String expr, int i)
	{
		int j = i + 1;
		while (j < expr.length())
		{
			char chx = expr.charAt(j);
			if (!isAlphaChar(chx))
				break;
			++j;
		}

		return expr.substring(i, j);
	}

	// =====================================================================

	private void expect(Production p, String msg) throws ExprError
	{
		if (expecting != p)
			throw new ExprError(msg);
	}

	private void expect(Production p, String msg, String word) throws ExprError
	{
		if (expecting != p)
			throw new ExprError(msg, word);
	}

	// =====================================================================

	private void processBinaryOp(Token op, String word) throws ExprError
	{
		expect(Production.OPERATOR, "unexpected_operator", word);

		while (!operators.isEmpty())
		{
			Token lastOp = operators.peek();
			if (op.getPrecedence() > lastOp.getPrecedence())
				break;

			lastOp.apply(operands);
			operators.pop();
		}

		operators.push(op);
		expecting = Production.EXPR;
	}

	// =====================================================================

	private static String implode(String separator, Stack<Number> operands)
	{
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < operands.size();)
		{
			b.append(toPhpString(operands.get(i)));
			if (++i < operands.size())
				b.append(separator);
		}
		return b.toString();
	}

	// =====================================================================
	// PHP number semantics
	// =====================================================================

	private static String toPhpString(Number value)
	{
		if (value instanceof Long)
			return value.toString();
		return formatFloat(value.doubleValue());
	}

	/**
	 * Converts a float to a string like PHP's {@code zend_gcvt()} with
	 * {@code precision=14}: the value is rounded to 14 significant digits and
	 * printed in exponential notation (e.g. {@code 1.0E+15} or {@code 1.0E-5})
	 * if its decimal exponent is below -4 or above 14.
	 */
	static String formatFloat(double value)
	{
		if (Double.isNaN(value))
			return "NAN";
		if (Double.isInfinite(value))
			return (value > 0) ? "INF" : "-INF";

		String digits;
		int decpt;
		if (value == 0.)
		{
			digits = "0";
			decpt = 1;
		}
		else
		{
			BigDecimal rounded = new BigDecimal(Math.abs(value))
					.round(PHP_PRECISION_CONTEXT)
					.stripTrailingZeros();
			digits = rounded.unscaledValue().toString();
			decpt = digits.length() - rounded.scale();
		}

		StringBuilder b = new StringBuilder();
		// Also true for -0.0, which PHP prints as "-0"
		if ((Double.doubleToRawLongBits(value) & Long.MIN_VALUE) != 0)
			b.append('-');

		if (decpt > PHP_PRECISION || decpt < -3)
		{
			int exponent = decpt - 1;
			b.append(digits.charAt(0));
			b.append('.');
			b.append((digits.length() > 1) ? digits.substring(1) : "0");
			b.append('E');
			b.append((exponent < 0) ? '-' : '+');
			b.append(Math.abs(exponent));
		}
		else if (decpt <= 0)
		{
			b.append("0.");
			for (int i = decpt; i < 0; ++i)
				b.append('0');
			b.append(digits);
		}
		else
		{
			for (int i = 0; i < decpt; ++i)
				b.append((i < digits.length()) ? digits.charAt(i) : '0');
			if (digits.length() > decpt)
			{
				b.append('.');
				b.append(digits, decpt, digits.length());
			}
		}

		return b.toString();
	}

	/**
	 * PHP's truth value of a number; NaN is true.
	 */
	private static boolean toBool(Number value)
	{
		if (value instanceof Long)
			return value.longValue() != 0;
		return value.doubleValue() != 0.;
	}

	/**
	 * PHP's (int) cast. NaN and infinity become 0, values outside the range of
	 * an integer wrap around modulo 2^64.
	 */
	private static long toInt(Number value)
	{
		if (value instanceof Long)
			return value.longValue();

		double d = value.doubleValue();
		if (Double.isNaN(d) || Double.isInfinite(d))
			return 0;

		if (d >= TWO_POW_63 || d < -TWO_POW_63)
		{
			double dmod = d % TWO_POW_64;
			if (dmod < 0)
				dmod += TWO_POW_64;
			if (dmod >= TWO_POW_63)
				return (long) (dmod - TWO_POW_64);
			return (long) dmod;
		}

		return (long) d;
	}

	private static boolean bothInts(Number left, Number right)
	{
		return (left instanceof Long) && (right instanceof Long);
	}

	private static Number add(Number left, Number right)
	{
		if (bothInts(left, right))
		{
			try
			{
				return Math.addExact(left.longValue(), right.longValue());
			}
			catch (ArithmeticException e)
			{
				// Integer overflow yields a float in PHP
			}
		}
		return left.doubleValue() + right.doubleValue();
	}

	private static Number subtract(Number left, Number right)
	{
		if (bothInts(left, right))
		{
			try
			{
				return Math.subtractExact(left.longValue(), right.longValue());
			}
			catch (ArithmeticException e)
			{
				// Integer overflow yields a float in PHP
			}
		}
		return left.doubleValue() - right.doubleValue();
	}

	private static Number multiply(Number left, Number right)
	{
		if (bothInts(left, right))
		{
			try
			{
				return Math.multiplyExact(left.longValue(), right.longValue());
			}
			catch (ArithmeticException e)
			{
				// Integer overflow yields a float in PHP
			}
		}
		return left.doubleValue() * right.doubleValue();
	}

	/**
	 * PHP's division; the divisor must not be zero. Integers yield an integer
	 * if the division is exact.
	 */
	private static Number divide(Number left, Number right)
	{
		if (bothInts(left, right))
		{
			long l = left.longValue();
			long r = right.longValue();
			if (r == -1 && l == Long.MIN_VALUE)
				return (double) Long.MIN_VALUE / -1;
			if (l % r == 0)
				return l / r;
			return (double) l / r;
		}
		return left.doubleValue() / right.doubleValue();
	}

	/**
	 * PHP's {@code pow()}: integers with a non-negative integer exponent yield
	 * an integer unless the result overflows.
	 */
	private static Number pow(Number base, Number exponent)
	{
		if (!bothInts(base, exponent))
			return cPow(base.doubleValue(), exponent.doubleValue());

		long l2 = base.longValue();
		long i = exponent.longValue();
		if (i < 0)
			return cPow(l2, i);
		if (i == 0)
			return 1L;
		if (l2 == 0)
			return 0L;

		long l1 = 1;
		while (i >= 1)
		{
			if (i % 2 != 0)
			{
				--i;
				try
				{
					l1 = Math.multiplyExact(l1, l2);
				}
				catch (ArithmeticException e)
				{
					return (double) l1 * (double) l2 * cPow(l2, i);
				}
			}
			else
			{
				i /= 2;
				try
				{
					l2 = Math.multiplyExact(l2, l2);
				}
				catch (ArithmeticException e)
				{
					return (double) l1 * cPow((double) l2 * (double) l2, i);
				}
			}
		}
		return l1;
	}

	/**
	 * C's {@code pow()} as used by PHP.
	 */
	private static double cPow(double base, double exponent)
	{
		// C defines these cases where Java's Math.pow() returns NaN
		if (base == 1. || (base == -1. && Double.isInfinite(exponent)))
			return 1.;

		// Powers of ten are correctly rounded by C's pow()
		if (base == 10. && exponent == Math.rint(exponent) && Math.abs(exponent) < 1000.)
			return Double.parseDouble("1e" + (long) exponent);

		return Math.pow(base, exponent);
	}

	private static Number abs(Number value)
	{
		if (value instanceof Long)
		{
			long l = value.longValue();
			if (l == Long.MIN_VALUE)
				return -(double) Long.MIN_VALUE;
			return Math.abs(l);
		}
		return Math.abs(value.doubleValue());
	}

	private static boolean equal(Number left, Number right)
	{
		if (bothInts(left, right))
			return left.longValue() == right.longValue();
		return left.doubleValue() == right.doubleValue();
	}

	private static boolean less(Number left, Number right)
	{
		if (bothInts(left, right))
			return left.longValue() < right.longValue();
		return left.doubleValue() < right.doubleValue();
	}

	private static boolean lessOrEqual(Number left, Number right)
	{
		if (bothInts(left, right))
			return left.longValue() <= right.longValue();
		return left.doubleValue() <= right.doubleValue();
	}

	/**
	 * PHP's {@code round()} in PHP_ROUND_HALF_UP mode. The result is always a
	 * float.
	 */
	private static double round(Number value, long precision)
	{
		int places;
		if (precision >= 0)
			places = (int) Math.min(precision, Integer.MAX_VALUE);
		else
			places = (int) Math.max(precision, Integer.MIN_VALUE);

		if ((value instanceof Long) && places >= 0)
			return value.doubleValue();

		return round(value.doubleValue(), places);
	}

	/**
	 * Port of PHP 8.3's {@code _php_math_round()} which pre-rounds the value to
	 * the 15 significant digits guaranteed by a double.
	 */
	private static double round(double value, int places)
	{
		if (Double.isNaN(value) || Double.isInfinite(value) || value == 0.)
			return value;

		places = Math.max(places, Integer.MIN_VALUE + 1);
		int precisionPlaces = 14 - intLog10Abs(value);

		double f1 = intPow10(Math.abs(places));

		double tmp;
		if (precisionPlaces > places && precisionPlaces - 15 < places)
		{
			double f2 = intPow10(Math.abs(precisionPlaces));
			tmp = (precisionPlaces >= 0) ? value * f2 : value / f2;

			// Pre-round the result to the precision
			tmp = roundHelper(tmp);

			f2 = intPow10(Math.abs(places - precisionPlaces));
			// because places < precisionPlaces
			tmp = tmp / f2;
		}
		else
		{
			tmp = (places >= 0) ? value * f1 : value / f1;

			// This value is beyond our precision, so rounding it is pointless
			if (Math.abs(tmp) >= 1e15)
				return value;
		}

		tmp = roundHelper(tmp);

		if (Math.abs(places) < 23)
		{
			tmp = (places > 0) ? tmp / f1 : tmp * f1;
		}
		else
		{
			// PHP prints the value with "%15fe%d" and parses it back with
			// strtod() which cannot parse "inf"
			if (Double.isInfinite(tmp))
				return 0.;

			tmp = new BigDecimal(tmp).scaleByPowerOfTen(-places).doubleValue();
			if (Double.isInfinite(tmp))
				tmp = value;
		}

		return tmp;
	}

	private static double roundHelper(double value)
	{
		if (value >= 0.)
			return Math.floor(value + 0.5);
		return Math.ceil(value - 0.5);
	}

	/**
	 * Returns floor(log10(abs(value))) like PHP's {@code php_intlog10abs()}.
	 */
	private static int intLog10Abs(double value)
	{
		value = Math.abs(value);

		if (value < 1e-8 || value > 1e22)
			return (int) Math.floor(Math.log10(value));

		// Do a binary search with 5 steps
		int result = 15;
		if (value < LOG10_STEPS[result])
			result -= 8;
		else
			result += 8;
		if (value < LOG10_STEPS[result])
			result -= 4;
		else
			result += 4;
		if (value < LOG10_STEPS[result])
			result -= 2;
		else
			result += 2;
		if (value < LOG10_STEPS[result])
			result -= 1;
		else
			result += 1;
		if (value < LOG10_STEPS[result])
			result -= 1;
		result -= 8;
		return result;
	}

	private static double intPow10(int power)
	{
		if (power < 0 || power > 22)
			return cPow(10., power);
		return EXACT_POWERS_OF_TEN[power];
	}

	// =========================================================================

	public static final class ExprError
			extends
				Exception
	{
		private static final long serialVersionUID = 1L;

		/**
		 * The English messages of MediaWiki's ParserFunctions extension.
		 */
		private static final Map<String, String> MESSAGES = new HashMap<String, String>();

		static
		{
			MESSAGES.put("stack_exhausted", "Expression error: Stack exhausted.");
			MESSAGES.put("unexpected_number", "Expression error: Unexpected number.");
			MESSAGES.put("unrecognised_word", "Expression error: Unrecognized word \"%s\".");
			MESSAGES.put("unexpected_operator", "Expression error: Unexpected %s operator.");
			MESSAGES.put("missing_operand", "Expression error: Missing operand for %s.");
			MESSAGES.put("unexpected_closing_bracket", "Expression error: Unexpected closing bracket.");
			MESSAGES.put("unrecognised_punctuation", "Expression error: Unrecognized punctuation character \"%s\".");
			MESSAGES.put("unclosed_bracket", "Expression error: Unclosed bracket.");
			MESSAGES.put("division_by_zero", "Division by zero.");
			MESSAGES.put("invalid_argument", "Invalid argument for %s: less than -1 or greater than 1.");
			MESSAGES.put("invalid_argument_ln", "Invalid argument for ln: less than or equal to 0.");
			MESSAGES.put("unknown_error", "Expression error: Unknown error (%s).");
			MESSAGES.put("not_a_number", "In %s: Result is not a number.");
		}

		private final String param;

		/**
		 * @param message
		 *            The key of the message, e.g. "division_by_zero".
		 */
		public ExprError(String message)
		{
			this(message, null);
		}

		/**
		 * @param message
		 *            The key of the message, e.g. "division_by_zero".
		 * @param param
		 *            The parameter of the message, e.g. the name of the
		 *            operator.
		 */
		public ExprError(String message, String param)
		{
			super(makeMessage(message, param));
			this.param = param;
		}

		private static String makeMessage(String message, String param)
		{
			String format = MESSAGES.get(message);
			if (format == null)
				return String.format(MESSAGES.get("unknown_error"), message);
			return String.format(format, (param != null) ? param : "");
		}

		public String getParam()
		{
			return param;
		}
	}

	// =========================================================================

	private static enum Production
	{
		EXPR,
		OPERATOR;
	}

	// =========================================================================

	private static enum Token
	{
		// -- Constants -- e, pi --

		E(-1, "e")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				operands.push(Math.E);
			}
		},
		PI(-1, "pi")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				operands.push(Math.PI);
			}
		},

		// -- Binary -- 10e^x --

		SCIENTIFIC(10, "e")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push(multiply(left, pow(10L, right)));
			}
		},

		// -- Unary -- +, -, ! --

		POS(10, "+")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
			}
		},
		NEG(10, "-")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				Number arg = operands.pop();
				// PHP negates by multiplying with -1
				operands.push(multiply(arg, -1L));
			}
		},

		NOT(9, "not")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				Number arg = operands.pop();
				operands.push(toBool(arg) ? 0L : 1L);
			}
		},

		// -- Unary -- sin, cos, tan, atan, acos, atan --

		SINE(9, "sin")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				double arg = operands.pop().doubleValue();
				operands.push(Math.sin(arg));
			}
		},
		COSINE(9, "cos")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				double arg = operands.pop().doubleValue();
				operands.push(Math.cos(arg));
			}
		},
		TANGENS(9, "tan")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				double arg = operands.pop().doubleValue();
				operands.push(Math.tan(arg));
			}
		},
		ARCSINE(9, "asin")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				double arg = operands.pop().doubleValue();
				if (arg < -1 || arg > 1)
					throw new ExprError("invalid_argument", toString());
				operands.push(Math.asin(arg));
			}
		},
		ARCCOS(9, "acos")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				double arg = operands.pop().doubleValue();
				if (arg < -1 || arg > 1)
					throw new ExprError("invalid_argument", toString());
				operands.push(Math.acos(arg));
			}
		},
		ARCTAN(9, "atan")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				double arg = operands.pop().doubleValue();
				operands.push(Math.atan(arg));
			}
		},

		// -- Unary -- e^x, ln(x) --

		EXP(9, "exp")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				double arg = operands.pop().doubleValue();
				operands.push(Math.exp(arg));
			}
		},
		LN(9, "ln")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				double arg = operands.pop().doubleValue();
				if (arg <= 0)
					throw new ExprError("invalid_argument_ln", toString());
				operands.push(Math.log(arg));
			}
		},

		// -- Unary -- abs, floor, trunc, ceil, sqrt --

		ABS(9, "abs")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				Number arg = operands.pop();
				operands.push(abs(arg));
			}
		},

		FLOOR(9, "floor")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				double arg = operands.pop().doubleValue();
				operands.push(Math.floor(arg));
			}
		},
		TRUNC(9, "trunc")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				Number arg = operands.pop();
				operands.push(toInt(arg));
			}
		},
		CEIL(9, "ceil")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				double arg = operands.pop().doubleValue();
				operands.push(Math.ceil(arg));
			}
		},
		SQRT(9, "sqrt")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireOneOp(this, operands);
				double result = Math.sqrt(operands.pop().doubleValue());
				if (Double.isNaN(result))
					throw new ExprError("not_a_number", toString());
				operands.push(result);
			}
		},

		// -- Binary -- --

		POW(8, "^")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push(pow(left, right));
			}
		},
		TIMES(7, "*")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push(multiply(left, right));
			}
		},
		DIVIDE(7, "/")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				if (!toBool(right))
					throw new ExprError("division_by_zero", toString());
				operands.push(divide(left, right));
			}
		},
		MOD(7, "mod")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				long right = toInt(operands.pop());
				long left = toInt(operands.pop());
				if (right == 0)
					throw new ExprError("division_by_zero", toString());
				operands.push(left % right);
			}
		},
		FMOD(7, "fmod")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				double right = operands.pop().doubleValue();
				double left = operands.pop().doubleValue();
				if (!toBool(right))
					throw new ExprError("division_by_zero", toString());
				// Java's remainder of doubles is C's fmod()
				operands.push(left % right);
			}
		},

		// -- Binary -- --

		PLUS(6, "+")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push(add(left, right));
			}
		},
		MINUS(6, "-")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push(subtract(left, right));
			}
		},

		// -- Binary -- round --

		ROUND(5, "round")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				long digits = toInt(operands.pop());
				Number value = operands.pop();
				operands.push(round(value, digits));
			}
		},

		// -- Binary -- --

		EQ(4, "=")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push(equal(left, right) ? 1L : 0L);
			}
		},
		NEQ(4, "<>")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push(equal(left, right) ? 0L : 1L);
			}
		},
		LE(4, "<")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push(less(left, right) ? 1L : 0L);
			}
		},
		GR(4, ">")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push(less(right, left) ? 1L : 0L);
			}
		},
		LEQ(4, "<=")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push(lessOrEqual(left, right) ? 1L : 0L);
			}
		},
		GEQ(4, ">=")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push(lessOrEqual(right, left) ? 1L : 0L);
			}
		},

		// -- Binary -- --

		AND(3, "and")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push((toBool(left) && toBool(right)) ? 1L : 0L);
			}
		},
		OR(2, "or")
		{
			@Override
			public void apply(Stack<Number> operands) throws ExprError
			{
				requireTwoOps(this, operands);
				Number right = operands.pop();
				Number left = operands.pop();
				operands.push((toBool(left) || toBool(right)) ? 1L : 0L);
			}
		},

		// -- Binary -- --

		LPAREN(-1, "(")
		{
			@Override
			public void apply(Stack<Number> operands)
			{
				throw new AssertionError();
			}
		},
		RPAREN(-1, ")")
		{
			@Override
			public void apply(Stack<Number> operands)
			{
				throw new AssertionError();
			}
		};

		// -----------------------------------------------------------------

		private final int precedence;

		private final String name;

		// -----------------------------------------------------------------

		Token(int precedence, String name)
		{
			this.name = name;
			this.precedence = precedence;
		}

		// -----------------------------------------------------------------

		public abstract void apply(Stack<Number> operands) throws ExprError;

		public int getPrecedence()
		{
			return precedence;
		}

		@Override
		public String toString()
		{
			return name;
		}

		// -----------------------------------------------------------------

		private static void requireOneOp(Token op, Stack<Number> operands) throws ExprError
		{
			if (operands.isEmpty())
				throw new ExprError("missing_operand", op.toString());
		}

		private static void requireTwoOps(Token op, Stack<Number> operands) throws ExprError
		{
			if (operands.size() < 2)
				throw new ExprError("missing_operand", op.toString());
		}
	}
}
