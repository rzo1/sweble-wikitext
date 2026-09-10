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

import org.junit.Test;
import static org.junit.Assert.*;


public class NumberFormaterTest
{
	private static final double EPSILON = 1E-6;

	@Test
	public void testIsNumberValid()
	{
		assertTrue(NumberFormater.isNumberValid("1"));
		assertTrue(NumberFormater.isNumberValid("123"));
		assertTrue(NumberFormater.isNumberValid("–123")); // en dash (u+2013)
		assertTrue(NumberFormater.isNumberValid("1,234,567"));
		assertTrue(NumberFormater.isNumberValid("0.5"));
		assertTrue(NumberFormater.isNumberValid("12.3e-15"));
		assertTrue(NumberFormater.isNumberValid("12.3e–15")); // en dash
		assertTrue(NumberFormater.isNumberValid("1/2"));
		assertTrue(NumberFormater.isNumberValid("1⁄2"));
		assertTrue(NumberFormater.isNumberValid("2+1⁄2"));
		assertTrue(NumberFormater.isNumberValid("-2-1⁄2"));
		assertTrue(NumberFormater.isNumberValid("–2–1⁄2")); // en dash
		assertTrue(NumberFormater.isNumberValid("1//2"));
		assertTrue(NumberFormater.isNumberValid("2+1//2"));

		assertTrue(NumberFormater.isNumberValid("−123")); // minus sign (u+2212)
		assertTrue(NumberFormater.isNumberValid("1E3"));

		assertFalse(NumberFormater.isNumberValid("12 34"));
		assertFalse(NumberFormater.isNumberValid("0x1234"));
		assertFalse(NumberFormater.isNumberValid("1234d"));
	}

	@Test
	public void testParseNumberUnicodeMinusAndExponent()
	{
		assertEquals(-123d, NumberFormater.parseNumber("−123"), EPSILON); // minus sign
		assertEquals(-2.5, NumberFormater.parseNumber("−2-1⁄2"), EPSILON);
		assertEquals(1000d, NumberFormater.parseNumber("1E3"), EPSILON);
		assertEquals(0.0025, NumberFormater.parseNumber("2.5E-3"), EPSILON);
	}

	@Test(expected=NumberFormatException.class)
	public void testParseNumberDivisionByZero()
	{
		NumberFormater.parseNumber("1/0");
	}

	@Test
	public void testRoundToPrecision()
	{
		// Values as in Module:Convert (cvtround), e.g. 1234 m in ft
		assertEquals("4,049", NumberFormater.formatRounded(4048.556430446194, 0).getShow());
		assertEquals("4,000", NumberFormater.formatRounded(4048.556430446194, -2).getShow());
		assertEquals("0.30", NumberFormater.formatRounded(0.3048, 2).getShow());
		assertEquals("150,000,000", NumberFormater.formatRounded(149597870.691, -7).getShow());
		assertEquals("1.0×10⁻¹⁰", NumberFormater.formatRounded(1e-10, 11).getShow());
		assertEquals("0", NumberFormater.formatRounded(0, 0).getShow());
		assertEquals("0.00", NumberFormater.formatRounded(0.004, 2).getShow());
		assertEquals("0.00", NumberFormater.formatRounded(-0.004, 2).getShow());
	}

	@Test
	public void testRoundToSignificantFigures()
	{
		assertEquals("149,600,000", NumberFormater.formatSigFig(149597870.691, 4).getShow());
		assertEquals("0.001550", NumberFormater.formatSigFig(0.00155, 4).getShow());
		assertEquals("0.8361274", NumberFormater.formatSigFig(0.83612736, 7).getShow());
		assertEquals("0.00", NumberFormater.formatSigFig(0, 3).getShow());
	}

	@Test
	public void testFormatInput()
	{
		assertEquals("1,234", NumberFormater.withSeparator("1234"));
		assertEquals("1,234.5678", NumberFormater.withSeparator("1234.5678"));
		assertEquals("123", NumberFormater.withSeparator("123"));
		assertEquals("0.001", NumberFormater.withSeparator("0.001"));
	}

	@Test
	public void testSeparatorOptions()
	{
		// as in the output of {{convert}} with comma=off, comma=5 and
		// comma=gaps on English Wikipedia (September 2026)
		NumberFormater.Options off = new NumberFormater.Options();
		off.noComma = true;
		assertEquals("12345", NumberFormater.withSeparator("12345", off));

		NumberFormater.Options comma5 = new NumberFormater.Options();
		comma5.comma5 = true;
		assertEquals("1234", NumberFormater.withSeparator("1234", comma5));
		assertEquals("12,345", NumberFormater.withSeparator("12345", comma5));
		assertEquals("123,456.789", NumberFormater.withSeparator("123456.789", comma5));

		NumberFormater.Options gaps = new NumberFormater.Options();
		gaps.gaps = true;
		assertEquals("<span style=\"white-space: nowrap\">12<span style=\"margin-left: 0.25em\">345.6789</span></span>",
				NumberFormater.withSeparator("12345.6789", gaps));
		assertEquals("<span style=\"white-space: nowrap\">12.345<span style=\"margin-left: 0.25em\">6789</span></span>",
				NumberFormater.withSeparator("12.3456789", gaps));
		assertEquals("123", NumberFormater.withSeparator("123", gaps));
	}

	@Test
	public void testLuaNumberFormats()
	{
		// C's "%g" and Lua's tostring() ("%.14g")
		assertEquals("62", NumberFormater.toGeneral(62, 6));
		assertEquals("4.5", NumberFormater.toGeneral(4.5, 6));
		assertEquals("1e+06", NumberFormater.toGeneral(1e6, 6));
		assertEquals("1.5e-05", NumberFormater.toGeneral(1.5e-5, 6));
		assertEquals("39", NumberFormater.luaToString(39.0));
		assertEquals("39.370078740157", NumberFormater.luaToString(39.37007874015748));
	}

	@Test
	public void testFractionMarkup()
	{
		// like {{convert|2+1/2|in|cm}} on English Wikipedia (with "⁄" for "&frasl;")
		assertEquals("<span class=\"frac\">2<span class=\"sr-only\">+</span><span class=\"num\">1</span>⁄"
				+ "<span class=\"den\">2</span></span>", NumberFormater.parseValue("2+1/2").getShow());
		assertEquals(2.5, NumberFormater.parseValue("2+1/2").getValue(), EPSILON);
		// "12.1+3/4" hands are 12 hands 1.75 inches
		assertEquals(12.175, NumberFormater.parseValue("12.1+3/4").getAltValue(), EPSILON);
	}

	@Test
	public void testParseNumber1()
	{
		assertEquals(1d, NumberFormater.parseNumber("1"), EPSILON);
		assertEquals(123d, NumberFormater.parseNumber("123"), EPSILON);
		assertEquals(-123d, NumberFormater.parseNumber("–123"), EPSILON); // en dash
		assertEquals(1234567d, NumberFormater.parseNumber("1,234,567"), EPSILON);
		assertEquals(0.5, NumberFormater.parseNumber("0.5"), EPSILON);
		assertEquals(1.23e-4, NumberFormater.parseNumber("12.3e-5"), EPSILON);
		assertEquals(1.23e-4, NumberFormater.parseNumber("12.3e–5"), EPSILON); // en dash
		assertEquals(0.5, NumberFormater.parseNumber("1/2"), EPSILON);
		assertEquals(0.33333333, NumberFormater.parseNumber("1⁄3"), EPSILON);
		assertEquals(2.5, NumberFormater.parseNumber("2+1⁄2"), EPSILON);
		assertEquals(-2.5, NumberFormater.parseNumber("-2-1⁄2"), EPSILON);
		assertEquals(0.5, NumberFormater.parseNumber("1//2"), EPSILON);
		assertEquals(2.5, NumberFormater.parseNumber("2+1//2"), EPSILON);
		assertEquals(123d, NumberFormater.parseNumber("+123"), EPSILON);
		assertEquals(-123d, NumberFormater.parseNumber("-123"), EPSILON);
	}

	@Test(expected=NumberFormatException.class)
	public void testParseNumber2()
	{
		NumberFormater.parseNumber("-2+1⁄2");
	}

	@Test
	public void testFormatNumberDefault()
	{
		final int SIG_FIG = 2;
		assertEquals("1.0", NumberFormater.formatNumberDefault(1, SIG_FIG));
		assertEquals("−1.0", NumberFormater.formatNumberDefault(-1, SIG_FIG));
		assertEquals("11", NumberFormater.formatNumberDefault(11, SIG_FIG));
		assertEquals("110", NumberFormater.formatNumberDefault(111, SIG_FIG));
		assertEquals("120", NumberFormater.formatNumberDefault(115, SIG_FIG));
		assertEquals("1,100", NumberFormater.formatNumberDefault(1111, SIG_FIG));
		assertEquals("0.10", NumberFormater.formatNumberDefault(0.1, SIG_FIG));
		assertEquals("0.11", NumberFormater.formatNumberDefault(0.11, SIG_FIG));
		assertEquals("0.11", NumberFormater.formatNumberDefault(0.111, SIG_FIG));
		assertEquals("0.11", NumberFormater.formatNumberDefault(0.114, SIG_FIG));
		assertEquals("0.12", NumberFormater.formatNumberDefault(0.115, SIG_FIG));
		assertEquals("−0.11", NumberFormater.formatNumberDefault(-0.114, SIG_FIG));
		assertEquals("−0.12", NumberFormater.formatNumberDefault(-0.115, SIG_FIG));
		assertEquals("0.0011", NumberFormater.formatNumberDefault(0.0011, SIG_FIG));
		assertEquals("0.0000000010", NumberFormater.formatNumberDefault(0.000000001, SIG_FIG));
		assertEquals("1.0×10⁻¹⁰", NumberFormater.formatNumberDefault(0.0000000001, SIG_FIG));
		assertEquals("−1.0×10⁻¹⁰", NumberFormater.formatNumberDefault(-0.0000000001, SIG_FIG));
		assertEquals("11,000,000", NumberFormater.formatNumberDefault(11000000, SIG_FIG));
		assertEquals("−11,000,000", NumberFormater.formatNumberDefault(-11000000, SIG_FIG));
		assertEquals("1.1×10¹⁰", NumberFormater.formatNumberDefault(11000000000d, SIG_FIG));
		assertEquals("1", NumberFormater.formatNumberDefault(1.234, 1));
		assertEquals("1.2", NumberFormater.formatNumberDefault(1.234, 2));
		assertEquals("1.23", NumberFormater.formatNumberDefault(1.234, 3));
		assertEquals("1.234", NumberFormater.formatNumberDefault(1.234, 4));
		assertEquals("1.2340", NumberFormater.formatNumberDefault(1.234, 5));
		assertEquals("10", NumberFormater.formatNumberDefault(12.34, 1));
		assertEquals("12", NumberFormater.formatNumberDefault(12.34, 2));
		assertEquals("12.3", NumberFormater.formatNumberDefault(12.34, 3));
		assertEquals("12.34", NumberFormater.formatNumberDefault(12.34, 4));
		assertEquals("12.340", NumberFormater.formatNumberDefault(12.34, 5));
		assertEquals("100", NumberFormater.formatNumberDefault(123.4, 1));
		assertEquals("120", NumberFormater.formatNumberDefault(123.4, 2));
		assertEquals("123", NumberFormater.formatNumberDefault(123.4, 3));
		assertEquals("123.4", NumberFormater.formatNumberDefault(123.4, 4));
		assertEquals("123.40", NumberFormater.formatNumberDefault(123.4, 5));
		assertEquals("1,000", NumberFormater.formatNumberDefault(1234.5, 1));
		assertEquals("1,200", NumberFormater.formatNumberDefault(1234.5, 2));
		assertEquals("1,230", NumberFormater.formatNumberDefault(1234.5, 3));
		assertEquals("1,235", NumberFormater.formatNumberDefault(1234.5, 4));
		assertEquals("1,234.5", NumberFormater.formatNumberDefault(1234.5, 5));
		
	}

	@Test
	public void testFormatNumberRounded()
	{
		assertEquals("1", NumberFormater.formatNumberRounded(1.123456789, 0));
		assertEquals("1.1", NumberFormater.formatNumberRounded(1.123456789, 1));
		assertEquals("1.12", NumberFormater.formatNumberRounded(1.123456789, 2));
		assertEquals("1.123", NumberFormater.formatNumberRounded(1.123456789, 3));
		assertEquals("1", NumberFormater.formatNumberRounded(1.4, 0));
		assertEquals("2", NumberFormater.formatNumberRounded(1.5, 0));
		assertEquals("−1", NumberFormater.formatNumberRounded(-1.4, 0));
		assertEquals("−2", NumberFormater.formatNumberRounded(-1.5, 0));
		assertEquals("1", NumberFormater.formatNumberRounded(1.123456789, -1));
		assertEquals("1.123456789012346", NumberFormater.formatNumberRounded(1.123456789012345678, 15));
		assertEquals("1.1234567890123457", NumberFormater.formatNumberRounded(1.123456789012345678, 16));
		assertEquals("1.1234567890123457", NumberFormater.formatNumberRounded(1.123456789012345678, 17));
		assertEquals("−1.123456789012346", NumberFormater.formatNumberRounded(-1.123456789012345678, 15));
		assertEquals("−1.1234567890123457", NumberFormater.formatNumberRounded(-1.123456789012345678, 16));
		assertEquals("−1.1234567890123457", NumberFormater.formatNumberRounded(-1.123456789012345678, 17));
		assertEquals("100,000.00", NumberFormater.formatNumberRounded(100000, 2));
	}
}
