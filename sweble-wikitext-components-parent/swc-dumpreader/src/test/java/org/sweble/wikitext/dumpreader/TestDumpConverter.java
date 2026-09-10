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

package org.sweble.wikitext.dumpreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.revisionFragment;

import java.io.StringReader;
import java.math.BigInteger;

import javax.xml.transform.stream.StreamSource;

import org.junit.Test;
import org.sweble.wikitext.dumpreader.model.Revision;

public class TestDumpConverter
{
	private static final String COMMENT = "<comment>COMMENT</comment>";

	private static final String TEXT = "<text xml:space=\"preserve\">TEXT</text>";

	private static final String DELETED_COMMENT = "<comment deleted=\"deleted\" />";

	private static final String DELETED_TEXT = "<text deleted=\"deleted\" />";

	// =========================================================================

	@Test
	public void testDeletedCommentAndTextAreNull() throws Exception
	{
		for (ExportSchemaVersion version : ExportSchemaVersion.values())
		{
			Revision rev = convert(version, DELETED_COMMENT, DELETED_TEXT);

			assertTrue(version.name(), rev.isCommentDeleted());
			assertNull(version.name(), rev.getCommentText());
			assertTrue(version.name(), rev.isTextDeleted());
			assertNull(version.name(), rev.getText());
		}
	}

	@Test
	public void testPresentCommentAndTextAreKept() throws Exception
	{
		for (ExportSchemaVersion version : ExportSchemaVersion.values())
		{
			Revision rev = convert(version, COMMENT, TEXT);

			assertFalse(version.name(), rev.isCommentDeleted());
			assertEquals(version.name(), "COMMENT", rev.getCommentText());
			assertFalse(version.name(), rev.isTextDeleted());
			assertEquals(version.name(), "TEXT", rev.getText());
		}
	}

	@Test
	public void testEmptyTextIsNotNull() throws Exception
	{
		for (ExportSchemaVersion version : ExportSchemaVersion.values())
			assertEquals(version.name(), "", convert(version, COMMENT, "<text xml:space=\"preserve\" />").getText());
	}

	@Test
	public void testParentIdIsNullWhenTheFormatHasNone() throws Exception
	{
		assertNull(convert(ExportSchemaVersion.V0_5, COMMENT, TEXT).getParentId());
		assertNull(convert(ExportSchemaVersion.V0_6, COMMENT, TEXT).getParentId());
	}

	@Test
	public void testParentIdIsKept() throws Exception
	{
		assertEquals(BigInteger.valueOf(2), convert(ExportSchemaVersion.V0_7, COMMENT, TEXT).getParentId());
		assertEquals(BigInteger.valueOf(2), convert(ExportSchemaVersion.V0_11, COMMENT, TEXT).getParentId());
	}

	@Test
	public void testRevisionToStringIncludesFormatAndModel() throws Exception
	{
		String s = convert(ExportSchemaVersion.V0_10, COMMENT, TEXT).toString();

		assertTrue(s, s.contains("format=text/x-wiki"));
		assertTrue(s, s.contains("model=wikitext"));
	}

	// =========================================================================

	private static Revision convert(ExportSchemaVersion version, String comment, String text) throws Exception
	{
		DumpUnmarshaller unmarshaller = new DumpUnmarshaller(version, false);
		String xml = revisionFragment(version, comment, text);
		return unmarshaller.unmarshalToRevision(new StreamSource(new StringReader(xml)));
	}
}
