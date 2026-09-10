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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Tests for {@link NumberSpeller}.
 *
 * The expected values are the output of English Wikipedia retrieved via
 * https://en.wikipedia.org/w/api.php?action=expandtemplates in September 2026
 * for "{{#invoke:ConvertNumeric|numeral_to_english|NUMBER|...}}". There,
 * "sp=us" means useAnd = false, "adj=on" means hyphenate and "case=U" means
 * capitalize.
 */
public class NumberSpellerTest
{
	@Test
	public void testIntegers()
	{
		assertEquals("one thousand two hundred and fifty", spell("1250"));
		assertEquals("one thousand two hundred fifty", NumberSpeller.spell("1250", null, null, false, false, false));
		assertEquals("one million and one", spell("1000001"));
		assertEquals("one hundred twenty-three million four hundred fifty-six thousand seven hundred and eighty-nine",
				spell("123456789"));
		assertEquals("two billion five hundred million", spell("2500000000"));
		assertEquals("negative seven", spell("-7"));
	}

	@Test
	public void testOptions()
	{
		assertEquals("forty-two", NumberSpeller.spell("42", null, null, false, true, true));
		assertEquals("Forty-two", NumberSpeller.spell("42", null, null, true, true, false));
	}

	@Test
	public void testDecimalsAndScientificNotation()
	{
		assertEquals("twelve point zero five", spell("12.05"));
		assertEquals("zero point five", spell("0.5"));
		assertEquals("one hundred twenty-three thousand", spell("1.23e5"));
	}

	@Test
	public void testFractions()
	{
		assertEquals("two and three-quarters", NumberSpeller.spell("2", "3", "4", false, true, false));
		assertEquals("two and three-fourths", NumberSpeller.spell("2", "3", "4", false, false, false));
		assertEquals("one-eighth", NumberSpeller.spell(null, "1", "8", false, true, false));
		assertEquals("three-and-a-half", NumberSpeller.spell("3", "1", "2", false, true, true));
		// denominators other than 2, 3, 4, 5, 6, 8, 9, 10 and 16 are not supported
		assertNull(NumberSpeller.spell("5", "2", "7", false, true, false));
	}

	private static String spell(String number)
	{
		return NumberSpeller.spell(number, null, null, false, true, false);
	}
}
