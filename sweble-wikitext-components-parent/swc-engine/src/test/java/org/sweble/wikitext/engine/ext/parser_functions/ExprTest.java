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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.sweble.wikitext.engine.ext.parser_functions.ExprParser.ExprError;

/**
 * The expected values are the results of the ExprParser of MediaWiki's
 * ParserFunctions extension running on PHP 8.3.
 */
public class ExprTest
{
	private ExprParser p;

	@Before
	public void setUp()
	{
		p = new ExprParser();
	}

	// =========================================================================

	@Test
	public void testIssueExamplesMatchMediaWiki() throws Exception
	{
		assertExpr("0.33333333333333", "1/3");
		assertExpr("0.3", "0.1+0.2");
		assertExpr("3000000000", "3000000000");
		assertExpr("2147483648", "2^31");
		assertExpr("1.0E+15", "1e15");
		assertExpr("1.0E-5", "1e-5");
		assertError("Division by zero.", "1/0");
		assertExpr("1", "7.5 mod 2");
		assertExpr("1", "7 fmod 2");
		assertExpr("1.4142135623731", "sqrt 2");
		assertError("Expression error: Unrecognized word \"foo\".", "1 foo");
	}

	@Test
	public void testConstantsOnlyReturnConstant() throws Exception
	{
		assertExpr("2.718281828459", "e");
		assertExpr("3.1415926535898", "pi");
		assertExpr("-2.718281828459", "-e");
	}

	@Test
	public void testScientificNotationWorks() throws Exception
	{
		assertExpr("150000", "1.5e5");
		assertExpr("0.002", "2e-3");
		assertExpr("1.0E+22", "1 e 22");
		assertExpr("INF", "1e1000");
		assertExpr("0", "1e-400");
	}

	@Test
	public void testUnaryPlusMinusAndNotWork() throws Exception
	{
		assertExpr("-5", "-5");
		assertExpr("5", "+5");
		assertExpr("0", "not 5");
		assertExpr("1", "not 0");
		assertExpr("1", "not 0.0");
		assertExpr("1", "not -0");
	}

	@Test
	public void testUnaryFunctionsWork() throws Exception
	{
		assertExpr("0.4794255386042", "sin 0.5");
		assertExpr("0.87758256189037", "cos 0.5");
		assertExpr("0.54630248984379", "tan 0.5");
		assertExpr("0.5235987755983", "asin 0.5");
		assertExpr("1.0471975511966", "acos 0.5");
		assertExpr("0.46364760900081", "atan 0.5");
		assertExpr("1.6487212707001", "exp 0.5");
		assertExpr("-0.69314718055995", "ln 0.5");
		assertExpr("0.5", "abs -0.5");
		assertExpr("0", "floor 0.5");
		assertExpr("0", "trunc 0.5");
		assertExpr("1", "ceil 0.5");
		assertExpr("1.5707963267949", "asin 1");
		assertExpr("1.2246467991474E-16", "sin pi");
		assertExpr("-1", "cos pi");
	}

	@Test
	public void testBinaryFunctionsWork() throws Exception
	{
		assertExpr("256", "2^8");
		assertExpr("16", "2*8");
		assertExpr("0.25", "2/8");
		assertExpr("2.5", "5 div 2");
		assertExpr("2", "2mod8");
		assertExpr("2.57", "2.567 round 2");
		assertExpr("10", "2+8");
		assertExpr("-6", "2-8");
	}

	@Test
	public void testComparisonOperatorsWork() throws Exception
	{
		assertExpr("1", "1=1");
		assertExpr("0", "1=0");

		assertExpr("0", "1!=1");
		assertExpr("1", "1!=0");
		assertExpr("0", "1<>1");
		assertExpr("1", "1<>0");

		assertExpr("0", "1<0");
		assertExpr("1", "0<1");
		assertExpr("0", "0<0");

		assertExpr("1", "1>0");
		assertExpr("0", "0>1");
		assertExpr("0", "0>0");

		assertExpr("1", "1>=0");
		assertExpr("0", "0>=1");
		assertExpr("1", "0>=0");

		assertExpr("0", "1<=0");
		assertExpr("1", "0<=1");
		assertExpr("1", "0<=0");
	}

	@Test
	public void testLogicalOperatorsWork() throws Exception
	{
		assertExpr("1", "1 and 1");
		assertExpr("0", "1 and 0");
		assertExpr("0", "0 and 1");
		assertExpr("0", "0 and 0");

		assertExpr("1", "1 or 1");
		assertExpr("1", "1 or 0");
		assertExpr("1", "0 or 1");
		assertExpr("0", "0 or 0");

		assertExpr("1", "0.1 and 0.1");
		assertExpr("1", "0 or 0.1");
	}

	@Test
	public void testPrecedenceRules() throws Exception
	{
		assertExpr("9.7583988362772", "5 + 5 * 2 ^ sin 2e5");
		assertExpr("10.25", "5.1234 + 5.1234 round 2");
		assertExpr("1", "1 and 0 = 0");
		assertExpr("4", "-2^2");
		assertExpr("64", "2^3^2");
	}

	@Test
	public void testParantheses() throws Exception
	{
		assertExpr("50", "(5 + 5) * 5");
		assertExpr("75", "(5 + (5 + 5)) * 5");
		assertExpr("", "()");
	}

	// =========================================================================

	@Test
	public void testFloatsAreRoundedToFourteenSignificantDigits() throws Exception
	{
		assertExpr("0.66666666666667", "2/3");
		assertExpr("-0.66666666666667", "-2/3");
		assertExpr("14.285714285714", "100/7");
		assertExpr("0.3", "0.1*3");
		assertExpr("1", "1/7*7");
		assertExpr("123.456", "123.456");
		assertExpr("3.1415926535898", "3.14159265358979323846");
		assertExpr("99999999999999", "99999999999999.4");
		assertExpr("1.0E+14", "99999999999999.5");
		assertExpr("1.0E+15", "1e15+0.3");
		assertExpr("1.2345678901235E-10", "1.23456789012345e-10");
		assertExpr("0.00012345678901235", "0.000123456789012345678");
	}

	@Test
	public void testTiesAtFourteenDigitsRoundHalfEven() throws Exception
	{
		assertExpr("1.2345678901234E+14", "123456789012345");
		assertExpr("1.2345678901236E+14", "123456789012355");
		assertEquals("12345678901234", ExprParser.formatFloat(12345678901234.5));
	}

	@Test
	public void testIntegralFloatsPrintWithoutExponent() throws Exception
	{
		assertExpr("3000000000", "3000000000");
		assertExpr("4294967296", "4294967296");
		assertExpr("2147483648", "2147483647+1");
		assertExpr("-2147483649", "-2147483649");
		assertExpr("10000000000000", "1e13");
		assertExpr("99999999999999", "99999999999999");
		assertExpr("99999999999999", "1e14-1");
	}

	@Test
	public void testLargeAndSmallFloatsUseExponent() throws Exception
	{
		assertExpr("1.0E+14", "1e14");
		assertExpr("1.2345678901235E+15", "1234567890123456");
		assertExpr("9.007199254741E+15", "2^53");
		assertExpr("9.007199254741E+15", "2^53+1");
		assertExpr("9.2233720368548E+18", "2^63");
		assertExpr("1.844674407371E+19", "2^64");
		assertExpr("1.0E+21", "1e21");
		assertExpr("1.0E+100", "1e100");
		assertExpr("3.1415926535898E+20", "pi*1e20");
		assertExpr("0.5", "0.5");
		assertExpr("0.0001", "0.0001");
		assertExpr("0.00012345", "0.00012345");
		assertExpr("-0.001", "-0.001");
		assertExpr("1.0E-5", "0.00001");
		assertExpr("-1.0E-5", "-1e-5");
		assertExpr("1.5E-7", "1.5e-7");
		assertExpr("2.5E-5", "2.5e-5");
		assertExpr("1.0E-100", "1e-100");
	}

	@Test
	public void testFormatFloatHandlesSpecialValues() throws Exception
	{
		assertEquals("0", ExprParser.formatFloat(0.));
		assertEquals("-0", ExprParser.formatFloat(-0.));
		assertEquals("NAN", ExprParser.formatFloat(Double.NaN));
		assertEquals("INF", ExprParser.formatFloat(Double.POSITIVE_INFINITY));
		assertEquals("-INF", ExprParser.formatFloat(Double.NEGATIVE_INFINITY));
		assertEquals("1.7976931348623E+308", ExprParser.formatFloat(Double.MAX_VALUE));
		assertEquals("2.2250738585072E-308", ExprParser.formatFloat(Double.MIN_NORMAL));
		assertEquals("4.9406564584125E-324", ExprParser.formatFloat(Double.MIN_VALUE));
		assertEquals("-1.5E-300", ExprParser.formatFloat(-1.5e-300));
		assertEquals("0.8", ExprParser.formatFloat(0.1 + 0.7));
	}

	@Test
	public void testNegativeZero() throws Exception
	{
		assertExpr("-0", "-0");
		assertExpr("-0", "-0.0");
		assertExpr("-0", "0*-1");
		assertExpr("-0", "-0.4 round 0");
		assertExpr("-0", "ceil -0.5");
		assertExpr("-0", "sqrt -0");
		assertExpr("0", "0-0");
		assertExpr("0", "abs -0");
		assertExpr("0", "-(trunc 0)");
		assertExpr("0", "-trunc 0");
	}

	@Test
	public void testInfinityAndNan() throws Exception
	{
		assertExpr("INF", "10^400");
		assertExpr("-INF", "-(10^400)");
		assertExpr("INF", "1e308*10");
		assertExpr("-INF", "-1e308*10");
		assertExpr("NAN", "10^400 - 10^400");
		assertExpr("NAN", "0 e 400");
		assertExpr("NAN", "(-8)^(1/3)");
		assertExpr("NAN", "(-2)^0.5");
		assertExpr("INF", "0^-1");
		assertExpr("NAN", "(10^400)/(10^400)");
		assertExpr("NAN", "1/((10^400)-(10^400))");
		assertExpr("0", "1/(10^400)");
		assertExpr("INF", "ln (10^400)");
		assertExpr("INF", "exp 1000");
		assertExpr("0", "exp -1000");
		assertExpr("INF", "sqrt (10^400)");
	}

	@Test
	public void testNanInComparisonsAndLogic() throws Exception
	{
		String nan = "((10^400)-(10^400))";
		assertExpr("0", nan + " = " + nan);
		assertExpr("0", "not " + nan);
		assertExpr("0", nan + " < 1");
		assertExpr("1", nan + " and 1");
		assertExpr("NAN", nan + " round 2");
	}

	@Test
	public void testPowFollowsC() throws Exception
	{
		assertExpr("0.5", "2^-1");
		assertExpr("1", "1^(10^400)");
		assertExpr("1", "(0-1)^(10^400)");
		assertExpr("1", "1^((10^400)-(10^400))");
		assertExpr("1", "((10^400)-(10^400))^0");
	}

	// =========================================================================

	@Test
	public void testIntegerResultsPrintAllDigits() throws Exception
	{
		assertExpr("1000000000000000", "trunc 1e15");
		assertExpr("1.0E+15", "trunc 1e15 + 0");
		assertExpr("1.0E+15", "floor 1e15");
		assertExpr("10000000000000000", "trunc 1e15 * trunc 10");
		assertExpr("4611686018427387904", "trunc 2 ^ trunc 62");
		assertExpr("2000000000000000000", "(trunc 2) e (trunc 18)");
		assertExpr("2.0E+18", "(trunc 2) e 18");
		assertExpr("2000", "(trunc 2) e (trunc 3)");
		assertExpr("1000", "1 e (trunc 3)");
		assertExpr("0.1", "(trunc 1) e (trunc 0-1)");
		assertExpr("2", "trunc 6 / trunc 3");
		assertExpr("3.5", "trunc 7 / trunc 2");
		assertExpr("1024", "trunc 2 ^ trunc 10");
		assertExpr("0.5", "trunc 2 ^ trunc -1");
		assertExpr("1", "trunc 0 ^ trunc 0");
		assertExpr("0", "trunc 0 ^ trunc 5");
		assertExpr("1.0E+20", "trunc 10 ^ trunc 20");
		assertExpr("5", "floor (trunc 5)");
		assertExpr("5", "abs (trunc -5)");
		assertExpr("-5", "-(trunc 5)");
	}

	@Test
	public void testIntegerOverflowYieldsFloat() throws Exception
	{
		assertExpr("1.0E+20", "trunc 1e18 * trunc 100");
		assertExpr("1.844674407371E+19", "trunc (2^62) * trunc 4");
		assertExpr("9.2233720368548E+18", "trunc 4611686018427387904 * trunc 2");
		assertExpr("9.2233720370002E+18", "(trunc 3037000500) ^ (trunc 2)");
		assertExpr("1.2157665459057E+19", "trunc 3 ^ trunc 40");
		assertExpr("9.2233720368548E+18", "trunc 2 ^ trunc 63");
		assertExpr("9.2233720368548E+18", "(trunc -9223372036854775808) / (trunc -1)");
		assertExpr("9.2233720368548E+18", "-(trunc -9223372036854775808)");
		assertExpr("9.2233720368548E+18", "abs trunc -9223372036854775808");
		assertExpr("-9223372036854775807", "(trunc 9223372036854775807) + (trunc 1)");
	}

	@Test
	public void testTruncCastsLikePhp() throws Exception
	{
		assertExpr("-2", "trunc -2.7");
		assertExpr("7766279631452241920", "trunc 1e20");
		assertExpr("-7766279631452241920", "trunc -1e20");
		assertExpr("0", "trunc (10^400)");
		assertExpr("-9223372036854775808", "trunc 9223372036854775807");
	}

	// =========================================================================

	@Test
	public void testModCastsOperandsToInt() throws Exception
	{
		assertExpr("1", "7.5 mod 2");
		assertExpr("-1", "-7 mod 3");
		assertExpr("1", "7 mod -3");
		assertExpr("6", "1e20 mod 7");
		assertExpr("0", "(10^400) mod 7");
		assertExpr("0", "(trunc -9223372036854775808) mod (trunc -1)");
		assertError("Division by zero.", "5 mod 0.5");
		assertError("Division by zero.", "5 mod 0");
		assertError("Division by zero.", "7 mod (10^400)");
	}

	@Test
	public void testFmod() throws Exception
	{
		assertExpr("1", "7 fmod 2");
		assertExpr("1.5", "7.5 fmod 2");
		assertExpr("-1.5", "-7.5 fmod 2");
		assertExpr("2", "7 fmod 2.5");
		assertExpr("0", "1e300 fmod 3");
		assertExpr("NAN", "(10^400) fmod 2");
		assertExpr("7", "7 fmod (10^400)");
		assertError("Division by zero.", "7 fmod 0");
		assertError("Division by zero.", "7 fmod -0");
	}

	@Test
	public void testSqrt() throws Exception
	{
		assertExpr("4", "sqrt 16");
		assertExpr("3.4142135623731", "sqrt 2 + 2");
		assertExpr("-1", "sqrt 0-1");
		assertError("In sqrt: Result is not a number.", "sqrt -1");
		assertError("In sqrt: Result is not a number.", "sqrt ((10^400)-(10^400))");
	}

	@Test
	public void testRoundMatchesPhp() throws Exception
	{
		assertExpr("1.01", "1.005 round 2");
		assertExpr("0.29", "0.285 round 2");
		assertExpr("3", "2.5 round 0");
		assertExpr("-3", "-2.5 round 0");
		assertExpr("1200", "1234.5678 round -2");
		assertExpr("1.96", "1.95583 round 2");
		assertExpr("5.05", "5.045 round 2");
		assertExpr("5.06", "5.055 round 2");
		assertExpr("-1.96", "-1.955 round 2");
		assertExpr("1242000", "1241757 round -3");
		assertExpr("3.142", "3.14159 round 3");
		assertExpr("0.3", "0.30000000000000004 round 16");
		assertExpr("0.1", "0.1 round 20");
		assertExpr("2.0E-24", "1.5e-24 round 24");
		assertExpr("1.23456E-20", "1.23456e-20 round 25");
		assertExpr("5", "5 round 400");
		assertExpr("0", "1e300 round -400");
		assertExpr("0", "123 round -400");
		assertExpr("0", "1e-300 round 305");
		assertExpr("1.0E+300", "1e300 round -25");
		assertExpr("10", "trunc 5 round -1");
		assertExpr("1234", "trunc 1234 round 2");
		assertExpr("1.0E+15", "(trunc 1e15) round 0");
		assertExpr("0", "123 round (trunc -9223372036854775808)");
		assertExpr("0", "1.5 round (trunc 9223372036854775807)");
		assertExpr("20", "12-(((0.5-(8))round 0)mod 12)");
		assertExpr("8", "(((10.5+8)round 0)mod 12)+1");
	}

	// =========================================================================

	@Test
	public void testNumbersAreParsedLikePhp() throws Exception
	{
		assertExpr("1.2", "1.2.3");
		assertExpr("0", ".");
		assertExpr("5", "5.");
		assertExpr("0.5", ".5");
		assertExpr("0", "..5");
		assertExpr("1", "1..2");
		assertExpr("12", "00012");
	}

	@Test
	public void testWordsAreCaseInsensitive() throws Exception
	{
		assertExpr("0", "SIN 0");
		assertExpr("3.1415926535898", "Pi");
		assertExpr("1", "1 MOD 2");
		assertExpr("2000", "2 E 3");
	}

	@Test
	public void testEscapedOperatorsAreUnescaped() throws Exception
	{
		assertExpr("1", "3 &lt; 5");
		assertExpr("0", "3&gt;5");
		assertExpr("2", "5 \u2212 3");
		assertExpr("2", "5 &minus; 3");
	}

	// =========================================================================

	@Test
	public void testErrorMessagesMatchMediaWiki() throws Exception
	{
		assertError("Expression error: Missing operand for +.", "1 +");
		assertError("Expression error: Missing operand for <>.", "1 !=");
		assertError("Expression error: Missing operand for sin.", "sin");
		assertError("Expression error: Missing operand for e.", "e e");
		assertError("Expression error: Missing operand for e.", "1 e");
		assertError("Expression error: Unclosed bracket.", "(1");
		assertError("Expression error: Unclosed bracket.", "(");
		assertError("Expression error: Unexpected closing bracket.", "1)");
		assertError("Expression error: Unexpected closing bracket.", ")");
		assertError("Expression error: Unexpected number.", "1 2");
		assertError("Expression error: Unexpected number.", "pi pi");
		assertError("Expression error: Unexpected number.", "2 pi");
		assertError("Expression error: Unrecognized word \"foo\".", "foo");
		assertError("Expression error: Unrecognized word \"foo\".", "1 FOO");
		assertError("Expression error: Unrecognized punctuation character \"&\".", "1 & 2");
		assertError("Expression error: Unrecognized punctuation character \"!\".", "!1");
		assertError("Expression error: Unrecognized punctuation character \"\u00e9\".", "1 + \u00e9");
		assertError("Expression error: Unrecognized punctuation character \"\uD83D\uDE00\".", "\uD83D\uDE00");
		assertError("Expression error: Unexpected * operator.", "* 2");
		assertError("Expression error: Unexpected sqrt operator.", "3 SQRT 4");
		assertError("Expression error: Unexpected ( operator.", "2 (3)");
		assertError("Invalid argument for ln: less than or equal to 0.", "ln 0");
		assertError("Invalid argument for ln: less than or equal to 0.", "ln -1");
		assertError("Invalid argument for asin: less than -1 or greater than 1.", "asin 2");
		assertError("Invalid argument for acos: less than -1 or greater than 1.", "acos -2");
		assertError("Division by zero.", "5 div 0");
		assertError("Division by zero.", "1/-0");
	}

	@Test
	public void testStackExhaustion() throws Exception
	{
		StringBuilder expr = new StringBuilder();
		for (int i = 0; i < 102; ++i)
			expr.append('(');
		expr.append('1');
		assertError("Expression error: Stack exhausted.", expr.toString());
	}

	// =========================================================================

	private void assertExpr(String expected, String expr) throws Exception
	{
		assertEquals(expr, expected, p.parse(expr));
	}

	private void assertError(String expected, String expr)
	{
		try
		{
			String result = p.parse(expr);
			fail("Expected error for " + expr + " but got: " + result);
		}
		catch (ExprError e)
		{
			assertEquals(expr, expected, e.getMessage());
		}
	}
}
