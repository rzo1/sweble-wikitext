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

package org.sweble.wikitext.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;
import org.sweble.wikitext.parser.encval.ValidatedWikitext;
import org.sweble.wikitext.parser.nodes.WtIllegalCodePoint;
import org.sweble.wikitext.parser.nodes.WtIllegalCodePoint.IllegalCodePointType;
import org.sweble.wikitext.parser.utils.SimpleParserConfig;

import de.fau.cs.osr.ptk.common.ast.AstLocation;

public class EncodingValidatorTest
{
	@Test
	public void testEncodingValidator() throws IOException
	{
		String title = "dummy";

		StringBuilder source = new StringBuilder();
		source.append("Ein einfacher Test-String\n"); // L 0
		source.append("mit ein paar \uE800 und \r\n"); // L 1:13
		source.append("nat\u00FCrlich ein paar \uFDEE.\n"); // L 2:19
		source.append("Aber auch \uDBEF und \uDC80 \r"); // L 3:10, 3:16
		source.append("d\u00FCrfen nicht fehlen. Zu guter \n");// L4
		source.append("Letzt noch ein Wohlklang \u0007."); // L 5:25

		/* Ruins the test string!
		InputStreamReader in = new InputStreamReader(
		        IOUtils.toInputStream(source.toString(), "UTF-8"));
		*/

		/*
		StringReader in = new StringReader(source.toString());
		while (true)
		{
			int c = in.read();
			if (c == -1)
				break;
			
			System.out.format("%c: U+%04x\n", c, (int) c);
		}
		in.close();
		*/

		SimpleParserConfig parserConfig = new SimpleParserConfig();

		WikitextEncodingValidator v = new WikitextEncodingValidator();
		ValidatedWikitext result = v.validate(parserConfig, source.toString(), title);
		String validatedWikitext = result.getWikitext();
		WtEntityMap entityMap = result.getEntityMap();

		WtIllegalCodePoint x0 = (WtIllegalCodePoint) entityMap.getEntity(0);
		assertEquals("\uE800", x0.getCodePoint());
		assertEquals(IllegalCodePointType.PRIVATE_USE_CHARACTER, x0.getType());
		assertEquals(new AstLocation(title, 1, 13), x0.getNativeLocation());

		WtIllegalCodePoint x1 = (WtIllegalCodePoint) entityMap.getEntity(1);
		assertEquals("\uFDEE", x1.getCodePoint());
		assertEquals(IllegalCodePointType.NON_CHARACTER, x1.getType());
		assertEquals(new AstLocation(title, 2, 19), x1.getNativeLocation());

		WtIllegalCodePoint x2 = (WtIllegalCodePoint) entityMap.getEntity(2);
		assertEquals("\uDBEF", x2.getCodePoint());
		assertEquals(IllegalCodePointType.ISOLATED_SURROGATE, x2.getType());
		assertEquals(new AstLocation(title, 3, 10), x2.getNativeLocation());

		WtIllegalCodePoint x3 = (WtIllegalCodePoint) entityMap.getEntity(3);
		assertEquals("\uDC80", x3.getCodePoint());
		assertEquals(IllegalCodePointType.ISOLATED_SURROGATE, x3.getType());
		assertEquals(new AstLocation(title, 3, 16), x3.getNativeLocation());

		WtIllegalCodePoint x4 = (WtIllegalCodePoint) entityMap.getEntity(4);
		assertEquals("\u0007", x4.getCodePoint());
		assertEquals(IllegalCodePointType.CONTROL_CHARACTER, x4.getType());
		assertEquals(new AstLocation(title, 5, 25), x4.getNativeLocation());

		StringBuilder ref = new StringBuilder();
		ref.append("Ein einfacher Test-String\n");
		ref.append("mit ein paar \uE0000\uE001 und \r\n");
		ref.append("nat\u00FCrlich ein paar \uE0001\uE001.\n");
		ref.append("Aber auch \uE0002\uE001 und \uE0003\uE001 \r");
		ref.append("d\u00FCrfen nicht fehlen. Zu guter \n");
		ref.append("Letzt noch ein Wohlklang \uE0004\uE001.");

		assertEquals(ref.toString(), validatedWikitext);
	}

	@Test
	public void testTrailingLoneHighSurrogateKeepsText() throws IOException
	{
		ValidatedWikitext result = validate("Some text\uD800");

		assertEquals("Some text\uE0000\uE001", result.getWikitext());
		assertIllegal(result, 0, "\uD800", IllegalCodePointType.ISOLATED_SURROGATE, 0, 9);
		assertEquals(1, result.getEntityMap().getEntities().size());
	}

	@Test
	public void testOnlyALoneHighSurrogate() throws IOException
	{
		ValidatedWikitext result = validate("\uDBFF");

		assertEquals("\uE0000\uE001", result.getWikitext());
		assertIllegal(result, 0, "\uDBFF", IllegalCodePointType.ISOLATED_SURROGATE, 0, 0);
	}

	@Test
	public void testLeadingLoneLowSurrogateIsFlagged() throws IOException
	{
		ValidatedWikitext result = validate("\uDC00abc");

		assertEquals("\uE0000\uE001abc", result.getWikitext());
		assertIllegal(result, 0, "\uDC00", IllegalCodePointType.ISOLATED_SURROGATE, 0, 0);
	}

	@Test
	public void testLoneHighSurrogateBeforeParserEntityMarkers() throws IOException
	{
		ValidatedWikitext result = validate("\uD800\uE0000\uE001");

		assertEquals("\uE0000\uE001\uE0001\uE0010\uE0002\uE001", result.getWikitext());
		assertIllegal(result, 0, "\uD800", IllegalCodePointType.ISOLATED_SURROGATE, 0, 0);
		assertIllegal(result, 1, "\uE000", IllegalCodePointType.PRIVATE_USE_CHARACTER, 0, 1);
		assertIllegal(result, 2, "\uE001", IllegalCodePointType.PRIVATE_USE_CHARACTER, 0, 3);
	}

	@Test
	public void testParserEntityMarkersBeforeLoneLowSurrogate() throws IOException
	{
		ValidatedWikitext result = validate("\uE0000\uE001\uDC00");

		assertEquals("\uE0000\uE0010\uE0001\uE001\uE0002\uE001", result.getWikitext());
		assertIllegal(result, 0, "\uE000", IllegalCodePointType.PRIVATE_USE_CHARACTER, 0, 0);
		assertIllegal(result, 1, "\uE001", IllegalCodePointType.PRIVATE_USE_CHARACTER, 0, 2);
		assertIllegal(result, 2, "\uDC00", IllegalCodePointType.ISOLATED_SURROGATE, 0, 3);
	}

	@Test
	public void testLoneSurrogatesNextToControlCharacters() throws IOException
	{
		ValidatedWikitext result = validate("\uD800\u0007\u0008\uDC00");

		assertEquals("\uE0000\uE001\uE0001\uE001\uE0002\uE001\uE0003\uE001", result.getWikitext());
		assertIllegal(result, 0, "\uD800", IllegalCodePointType.ISOLATED_SURROGATE, 0, 0);
		assertIllegal(result, 1, "\u0007", IllegalCodePointType.CONTROL_CHARACTER, 0, 1);
		assertIllegal(result, 2, "\u0008", IllegalCodePointType.CONTROL_CHARACTER, 0, 2);
		assertIllegal(result, 3, "\uDC00", IllegalCodePointType.ISOLATED_SURROGATE, 0, 3);
	}

	@Test
	public void testLoneSurrogatesNextToNonCharacters() throws IOException
	{
		ValidatedWikitext result = validate("\uD800\uFDD0\uFFFF\uDC00");

		assertEquals("\uE0000\uE001\uE0001\uE001\uE0002\uE001\uE0003\uE001", result.getWikitext());
		assertIllegal(result, 0, "\uD800", IllegalCodePointType.ISOLATED_SURROGATE, 0, 0);
		assertIllegal(result, 1, "\uFDD0", IllegalCodePointType.NON_CHARACTER, 0, 1);
		assertIllegal(result, 2, "\uFFFF", IllegalCodePointType.NON_CHARACTER, 0, 2);
		assertIllegal(result, 3, "\uDC00", IllegalCodePointType.ISOLATED_SURROGATE, 0, 3);
	}

	@Test
	public void testTwoLoneHighSurrogates() throws IOException
	{
		ValidatedWikitext result = validate("\uD800\uD801x");

		assertEquals("\uE0000\uE001\uE0001\uE001x", result.getWikitext());
		assertIllegal(result, 0, "\uD800", IllegalCodePointType.ISOLATED_SURROGATE, 0, 0);
		assertIllegal(result, 1, "\uD801", IllegalCodePointType.ISOLATED_SURROGATE, 0, 1);
	}

	@Test
	public void testSupplementaryCodePoints() throws IOException
	{
		ValidatedWikitext result = validate("\uD83D\uDE00\uDB80\uDC00\uD83F\uDFFE\uDBFF\uDFFD\uDBFF\uDFFF");

		assertEquals("\uD83D\uDE00\uE0000\uE001\uE0001\uE001\uE0002\uE001\uE0003\uE001", result.getWikitext());
		assertIllegal(result, 0, "\uDB80\uDC00", IllegalCodePointType.PRIVATE_USE_CHARACTER, 0, 2);
		assertIllegal(result, 1, "\uD83F\uDFFE", IllegalCodePointType.NON_CHARACTER, 0, 4);
		assertIllegal(result, 2, "\uDBFF\uDFFD", IllegalCodePointType.PRIVATE_USE_CHARACTER, 0, 6);
		assertIllegal(result, 3, "\uDBFF\uDFFF", IllegalCodePointType.NON_CHARACTER, 0, 8);
	}

	@Test
	public void testAllControlCharactersAreFlagged() throws IOException
	{
		StringBuilder source = new StringBuilder();
		for (char ch = 0; ch < 0x20; ++ch)
			source.append(ch);
		source.append('\u007F');

		ValidatedWikitext result = validate(source.toString());

		StringBuilder expected = new StringBuilder();
		int id = 0;
		for (char ch = 0; ch < 0x20; ++ch)
		{
			if (ch == '\t' || ch == '\n' || ch == '\r')
				expected.append(ch);
			else
				expected.append('\uE000').append(id++).append('\uE001');
		}
		expected.append('\uE000').append(id++).append('\uE001');

		assertEquals(expected.toString(), result.getWikitext());
		assertEquals(30, result.getEntityMap().getEntities().size());
		assertIllegal(result, 28, "\u001F", IllegalCodePointType.CONTROL_CHARACTER, 4, 17);
		assertIllegal(result, 29, "\u007F", IllegalCodePointType.CONTROL_CHARACTER, 4, 18);
	}

	@Test
	public void testLineAndColumnCounting() throws IOException
	{
		ValidatedWikitext result = validate("a\r\nb\rc\nd\u2028\u0007e\u0085\uD83D\uDE00\u0007");

		assertIllegal(result, 0, "\u0007", IllegalCodePointType.CONTROL_CHARACTER, 4, 0);
		assertIllegal(result, 1, "\u0007", IllegalCodePointType.CONTROL_CHARACTER, 5, 2);
	}

	@Test
	public void testReaderOverloadHonoursConvertIllegalCodePoints() throws IOException
	{
		SimpleParserConfig parserConfig = new SimpleParserConfig(
				true /*convertIllegalCodePoints*/,
				true /*warningsEnabled*/,
				true /*gatherRtd*/,
				false /*autoCorrect*/,
				true /*langConvTagsEnabled*/);

		WikitextEncodingValidator v = new WikitextEncodingValidator();
		ValidatedWikitext result = v.validate(parserConfig, new StringReader("a\u0007b\uD800"), "dummy");

		assertEquals("a\uFFFDb\uFFFD", result.getWikitext());
		assertTrue(result.containsIllegalCodePoints());
	}

	@Test
	public void testReaderOverloadMatchesStringOverload() throws IOException
	{
		StringBuilder source = new StringBuilder();
		for (int i = 0; i < 5000; ++i)
			source.append("x\u0007\uD83D\uDE00\uD800");

		SimpleParserConfig parserConfig = new SimpleParserConfig();
		WikitextEncodingValidator v = new WikitextEncodingValidator();
		ValidatedWikitext fromString = v.validate(parserConfig, source.toString(), "dummy");
		ValidatedWikitext fromReader = v.validate(parserConfig, new StringReader(source.toString()), "dummy");

		assertEquals(fromString.getWikitext(), fromReader.getWikitext());
	}

	// =========================================================================

	private static ValidatedWikitext validate(String source) throws IOException
	{
		WikitextEncodingValidator v = new WikitextEncodingValidator();
		return v.validate(new SimpleParserConfig(), source, "dummy");
	}

	private static void assertIllegal(
			ValidatedWikitext result,
			int id,
			String codePoint,
			IllegalCodePointType type,
			int line,
			int column)
	{
		WtIllegalCodePoint cp = (WtIllegalCodePoint) result.getEntityMap().getEntity(id);
		assertEquals(codePoint, cp.getCodePoint());
		assertEquals(type, cp.getType());
		assertEquals(new AstLocation("dummy", line, column), cp.getNativeLocation());
	}
}
