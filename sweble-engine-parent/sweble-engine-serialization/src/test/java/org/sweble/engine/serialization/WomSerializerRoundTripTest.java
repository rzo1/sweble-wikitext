/**
 * Copyright 2011 The Open Source Research Group,
 *                University of Erlangen-Nürnberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */

package org.sweble.engine.serialization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.sweble.engine.serialization.SerializationCoverageFixture.getWom;
import static org.sweble.engine.serialization.SerializationCoverageFixture.utf8;
import static org.sweble.engine.serialization.SerializationCoverageFixture.toXml;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.sweble.engine.serialization.WomSerializer.SerializationFormat;
import org.sweble.wom3.Wom3Document;
import org.sweble.wom3.Wom3Node;
import org.w3c.dom.Document;

/**
 * Serializes the WOM of a realistic page in every format and reads it back.
 */
@RunWith(Parameterized.class)
public class WomSerializerRoundTripTest
{
	@Parameters(name = "{0}, compact={1}")
	public static List<Object[]> parameters()
	{
		return Arrays.asList(new Object[][] {
				{ SerializationFormat.JAVA, false },
				{ SerializationFormat.JSON, false },
				{ SerializationFormat.JSON, true },
				{ SerializationFormat.XML, false } });
	}

	// =========================================================================

	private final SerializationFormat format;

	private final boolean compact;

	private final WomSerializer serializer = new WomSerializer();

	// =========================================================================

	public WomSerializerRoundTripTest(SerializationFormat format, boolean compact)
	{
		this.format = format;
		this.compact = compact;
	}

	// =========================================================================

	@Test
	public void testSerializeAndDeserializeRoundTrip() throws Exception
	{
		Wom3Document wom = getWom();

		byte[] serialized = serializer.serialize(wom, format, compact, false);
		Document loaded = serializer.deserialize(serialized, format, compact);

		assertLoadedArticle(wom, loaded);

		if (format == SerializationFormat.JAVA)
		{
			assertEquals(toXml(serializer, wom), toXml(serializer, loaded));
		}
		else
		{
			// Serializing the loaded document again yields the same result
			assertEquals(
					utf8(serialized),
					utf8(serializer.serialize(loaded, format, compact, false)));
		}
	}

	@Test
	public void testPrettySerializationCanBeReadBack() throws Exception
	{
		Wom3Document wom = getWom();

		byte[] pretty = serializer.serialize(wom, format, compact, true);
		Document loaded = serializer.deserialize(pretty, format, compact);

		assertLoadedArticle(wom, loaded);

		switch (format)
		{
			case JSON:
				assertTrue(utf8(pretty).contains("\n  "));
				assertEquals(
						utf8(serializer.serialize(wom, format, compact, false)),
						utf8(serializer.serialize(loaded, format, compact, false)));
				break;
			case XML:
				assertTrue(utf8(pretty).contains("\n"));
				break;
			default:
				// Java serialization has no pretty form
				assertEquals(toXml(serializer, wom), toXml(serializer, loaded));
				break;
		}
	}

	// =========================================================================

	private static void assertLoadedArticle(Wom3Document wom, Document loaded)
	{
		assertTrue(loaded instanceof Wom3Document);
		assertNotSame(wom, loaded);
		assertEquals(Wom3Node.WOM_NS_URI, loaded.getDocumentElement().getNamespaceURI());
		assertEquals(
				wom.getDocumentElement().getLocalName(),
				loaded.getDocumentElement().getLocalName());
	}
}
