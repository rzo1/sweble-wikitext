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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sweble.engine.serialization.SerializationCoverageFixture.getWom;
import static org.sweble.engine.serialization.SerializationCoverageFixture.utf8;
import static org.sweble.engine.serialization.SerializationCoverageFixture.toXml;

import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.sweble.engine.serialization.CompressorFactory.CompressionFormat;
import org.sweble.engine.serialization.WomSerializer.SerializationFormat;
import org.sweble.wom3.Wom3Document;
import org.w3c.dom.Document;

/**
 * Compresses the serialized WOM of a realistic page with every compression
 * format and reads it back.
 */
@RunWith(Parameterized.class)
public class WomSerializerCompressionRoundTripTest
{
	/**
	 * XZ is covered by {@link WomSerializerCoverageTest}.
	 */
	@Parameters(name = "{0}")
	public static List<Object[]> parameters()
	{
		return Arrays.asList(new Object[][] {
				{ CompressionFormat.BZIP2 },
				{ CompressionFormat.GZIP } });
	}

	// =========================================================================

	private final CompressionFormat compression;

	private final WomSerializer serializer = new WomSerializer();

	// =========================================================================

	public WomSerializerCompressionRoundTripTest(CompressionFormat compression)
	{
		this.compression = compression;
	}

	// =========================================================================

	@Test
	public void testCompressAndDecompressRoundTrip() throws Exception
	{
		byte[] raw = serializer.serialize(getWom(), SerializationFormat.XML, false, false);

		byte[] compressed = serializer.compress(raw, compression);
		assertTrue(compressed.length < raw.length);

		assertArrayEquals(raw, serializer.decompress(compressed, compression));
	}

	@Test
	public void testJavaSerializeAndCompressRoundTrip() throws Exception
	{
		assertCombinedRoundTrip(SerializationFormat.JAVA, false);
	}

	@Test
	public void testXmlSerializeAndCompressRoundTrip() throws Exception
	{
		assertCombinedRoundTrip(SerializationFormat.XML, false);
	}

	@Ignore("fixed in #130")
	@Test
	public void testJsonSerializeAndCompressRoundTrip() throws Exception
	{
		assertCombinedRoundTrip(SerializationFormat.JSON, false);
	}

	@Ignore("fixed in #130")
	@Test
	public void testCompactJsonSerializeAndCompressRoundTrip() throws Exception
	{
		assertCombinedRoundTrip(SerializationFormat.JSON, true);
	}

	@Test
	public void testSerializeAndCompressEqualsCompressedSerialization() throws Exception
	{
		Wom3Document wom = getWom();

		for (SerializationFormat format : new SerializationFormat[] { SerializationFormat.XML, SerializationFormat.JSON })
		{
			for (boolean compact : new boolean[] { false, true })
			{
				byte[] compressed = serializer.serializeAndCompress(wom, compression, format, compact, false);

				assertEquals(
						format + ", compact=" + compact,
						utf8(serializer.serialize(wom, format, compact, false)),
						utf8(serializer.decompress(compressed, compression)));
			}
		}
	}

	// =========================================================================

	private void assertCombinedRoundTrip(SerializationFormat format, boolean compact) throws Exception
	{
		Wom3Document wom = getWom();

		byte[] compressed = serializer.serializeAndCompress(wom, compression, format, compact, false);
		Document loaded = serializer.decompressAndDeserialize(compressed, compression, format, compact);

		assertTrue(loaded instanceof Wom3Document);

		if (format == SerializationFormat.JSON)
		{
			assertEquals(
					utf8(serializer.serialize(wom, format, compact, false)),
					utf8(serializer.serialize(loaded, format, compact, false)));
		}
		else
		{
			assertEquals(toXml(serializer, wom), toXml(serializer, loaded));
		}
	}
}
