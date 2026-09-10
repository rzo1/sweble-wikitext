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

import java.io.IOException;
import java.io.Reader;

import org.sweble.wikitext.parser.encval.ValidatedWikitext;
import org.sweble.wikitext.parser.nodes.WikitextNodeFactory;
import org.sweble.wikitext.parser.nodes.WtIllegalCodePoint;
import org.sweble.wikitext.parser.nodes.WtIllegalCodePoint.IllegalCodePointType;

import de.fau.cs.osr.ptk.common.ast.AstLocation;

/**
 * Replaces isolated surrogates, non-characters, private use characters and
 * control characters with {@link WtIllegalCodePoint} parser entities or, if
 * {@link ParserConfig#isConvertIllegalCodePoints()} is set, with U+FFFD.
 *
 * Since the parser entity markers U+E000 and U+E001 are private use
 * characters, the validated wikitext contains no markers other than the ones
 * inserted here.
 */
public class WikitextEncodingValidator
{
	private static final int READ_BUFFER_SIZE = 8192;

	// =========================================================================

	public ValidatedWikitext validate(
			ParserConfig parserConfig,
			WtEntityMap entityMap,
			String title,
			String source)
			throws IOException
	{
		Validator validator = new Validator(parserConfig, entityMap, title);

		String wikitext = validator.validate(source);

		return new ValidatedWikitext(wikitext, entityMap, validator.containsIllegalCodePoints);
	}

	public ValidatedWikitext validate(
			ParserConfig parserConfig,
			WtEntityMap entityMap,
			String title,
			Reader source)
			throws IOException
	{
		return validate(parserConfig, entityMap, title, readFully(source));
	}

	public ValidatedWikitext validate(
			ParserConfig parserConfig,
			String source,
			String title) throws IOException
	{
		return validate(parserConfig, new WtEntityMapImpl(), title, source);
	}

	public ValidatedWikitext validate(
			ParserConfig parserConfig,
			Reader source,
			String title) throws IOException
	{
		return validate(parserConfig, new WtEntityMapImpl(), title, source);
	}

	// =========================================================================

	private static String readFully(Reader source) throws IOException
	{
		StringBuilder b = new StringBuilder();
		char[] buffer = new char[READ_BUFFER_SIZE];

		int read;
		while ((read = source.read(buffer)) != -1)
			b.append(buffer, 0, read);

		return b.toString();
	}

	// =========================================================================

	private static final class Validator
	{
		private final WtEntityMap entityMap;

		private final String file;

		private final WikitextNodeFactory nf;

		private final boolean convertIllegalCodePoints;

		private StringBuilder text;

		private boolean containsIllegalCodePoints = false;

		// =====================================================================

		public Validator(
				ParserConfig parserConfig,
				WtEntityMap entityMap,
				String file)
		{
			this.entityMap = entityMap;
			this.file = file;
			this.nf = parserConfig.getNodeFactory();
			this.convertIllegalCodePoints = parserConfig.isConvertIllegalCodePoints();
		}

		// =====================================================================

		public String validate(String source)
		{
			final int length = source.length();

			text = new StringBuilder(length);

			int line = 0;
			int column = 0;

			// Start of the legal characters not yet copied to the output
			int copyFrom = 0;

			int i = 0;
			while (i < length)
			{
				char ch = source.charAt(i);

				int count = 1;
				IllegalCodePointType type;
				if (Character.isHighSurrogate(ch))
				{
					if (i + 1 < length && Character.isLowSurrogate(source.charAt(i + 1)))
					{
						count = 2;
						type = classifySupplementary(Character.toCodePoint(ch, source.charAt(i + 1)));
					}
					else
					{
						type = IllegalCodePointType.ISOLATED_SURROGATE;
					}
				}
				else if (Character.isLowSurrogate(ch))
				{
					type = IllegalCodePointType.ISOLATED_SURROGATE;
				}
				else
				{
					type = classifyBmp(ch);
				}

				if (type != null)
				{
					text.append(source, copyFrom, i);
					wrapIllegalCodePoint(line, column, source.substring(i, i + count), type);
					copyFrom = i + count;
				}

				// Count lines and columns like the JFlex lexer that did this before
				switch (ch)
				{
					case '\r':
						// "\r\n" is only counted once, by the '\n'
						if (i + 1 == length || source.charAt(i + 1) != '\n')
							++line;
						column = 0;
						break;

					case '\n':
					case 0x0B:
					case 0x0C:
					case 0x85:
					case 0x2028:
					case 0x2029:
						++line;
						column = 0;
						break;

					default:
						column += count;
						break;
				}

				i += count;
			}

			text.append(source, copyFrom, length);

			return text.toString();
		}

		private static IllegalCodePointType classifyBmp(char ch)
		{
			if (ch < 0x20)
			{
				return (ch == '\t' || ch == '\n' || ch == '\r') ?
						null :
						IllegalCodePointType.CONTROL_CHARACTER;
			}
			else if (ch < 0x7F)
			{
				return null;
			}
			else if (ch == 0x7F)
			{
				return IllegalCodePointType.CONTROL_CHARACTER;
			}
			else if (ch >= 0xE000 && ch <= 0xF8FF)
			{
				return IllegalCodePointType.PRIVATE_USE_CHARACTER;
			}
			else if ((ch >= 0xFDD0 && ch <= 0xFDEF) || ch >= 0xFFFE)
			{
				return IllegalCodePointType.NON_CHARACTER;
			}
			return null;
		}

		private static IllegalCodePointType classifySupplementary(int cp)
		{
			// U+xxFFFE and U+xxFFFF
			if ((cp & 0xFFFE) == 0xFFFE)
				return IllegalCodePointType.NON_CHARACTER;

			// U+F0000 to U+FFFFD and U+100000 to U+10FFFD
			if (cp >= 0xF0000)
				return IllegalCodePointType.PRIVATE_USE_CHARACTER;

			return null;
		}

		private void wrapIllegalCodePoint(
				int line,
				int column,
				String codePoint,
				IllegalCodePointType type)
		{
			WtIllegalCodePoint p = nf.illegalCp(codePoint, type);
			p.setRtd(codePoint);
			p.setNativeLocation(new AstLocation(file, line, column));

			int id = entityMap.registerEntity(p);

			if (convertIllegalCodePoints)
			{
				text.append('\uFFFD');
			}
			else
			{
				text.append('\uE000');
				text.append(id);
				text.append('\uE001');
			}

			containsIllegalCodePoints = true;
		}
	}
}
