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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.sweble.engine.serialization.SerializationCoverageFixture.getWom;
import static org.sweble.engine.serialization.SerializationCoverageFixture.toXml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Ignore;
import org.junit.Test;
import org.sweble.engine.serialization.CompressorFactory.CompressionFormat;
import org.sweble.engine.serialization.WomSerializer.SerializationFormat;
import org.sweble.wom3.Wom3Node;
import org.sweble.wom3.impl.DocumentImpl;
import org.sweble.wom3.serialization.Wom3NodeCompactJsonTypeAdapter;
import org.sweble.wom3.serialization.Wom3NodeJsonTypeAdapter;
import org.w3c.dom.Document;

import com.google.gson.GsonBuilder;

/**
 * Error handling of the {@link WomSerializer} and the
 * {@link SerializationLabToolbox}.
 */
public class WomSerializerCoverageTest
{
	private static final String UNKNOWN_CLASS = "org.sweble.does.not.Exist";

	// =========================================================================

	@Test
	public void testDefaultDocumentImplementation() throws Exception
	{
		assertEquals(DocumentImpl.class.getName(), new WomSerializer().getDocumentImplClassName());
	}

	@Test(expected = DeserializationException.class)
	public void testDeserializingMalformedXmlFails() throws Exception
	{
		new WomSerializer().deserialize(bytes("<article"), SerializationFormat.XML, false);
	}

	@Test(expected = IOException.class)
	public void testDeserializingCorruptJavaStreamFails() throws Exception
	{
		new WomSerializer().deserialize(new byte[] { 1, 2, 3, 4 }, SerializationFormat.JAVA, false);
	}

	@Test(expected = CompressionException.class)
	public void testDecompressingDataInWrongFormatFails() throws Exception
	{
		new WomSerializer().decompress(bytes("not gzip"), CompressionFormat.GZIP);
	}

	@Test(expected = CompressionException.class)
	public void testDecompressAndDeserializeOfDataInWrongFormatFails() throws Exception
	{
		new WomSerializer().decompressAndDeserialize(
				bytes("not bzip2"),
				CompressionFormat.BZIP2,
				SerializationFormat.XML,
				false);
	}

	@Test(expected = SerializationException.class)
	public void testJsonSerializationWithUnknownDocumentClassFails() throws Exception
	{
		WomSerializer serializer = new WomSerializer();
		serializer.setDocumentImplClassName(UNKNOWN_CLASS);
		serializer.serialize(getWom(), SerializationFormat.JSON, false, false);
	}

	@Test(expected = DeserializationException.class)
	public void testJsonDeserializationWithUnknownDocumentClassFails() throws Exception
	{
		WomSerializer serializer = new WomSerializer();
		byte[] json = serializer.serialize(getWom(), SerializationFormat.JSON, false, false);

		serializer.setDocumentImplClassName(UNKNOWN_CLASS);
		assertEquals(UNKNOWN_CLASS, serializer.getDocumentImplClassName());
		serializer.deserialize(json, SerializationFormat.JSON, false);
	}

	@Ignore("XZ needs org.tukaani:xz at runtime, which is not a dependency (NoClassDefFoundError)")
	@Test
	public void testXzCompressionRoundTrip() throws Exception
	{
		WomSerializer serializer = new WomSerializer();

		byte[] raw = serializer.serialize(getWom(), SerializationFormat.XML, false, false);
		byte[] compressed = serializer.compress(raw, CompressionFormat.XZ);
		assertArrayEquals(raw, serializer.decompress(compressed, CompressionFormat.XZ));

		Document loaded = serializer.decompressAndDeserialize(
				serializer.serializeAndCompress(getWom(), CompressionFormat.XZ, SerializationFormat.XML, false, false),
				CompressionFormat.XZ,
				SerializationFormat.XML,
				false);
		assertEquals(toXml(serializer, getWom()), toXml(serializer, loaded));
	}

	// =========================================================================

	@Test
	public void testLabToolboxJsonRoundTrip() throws Exception
	{
		for (boolean compact : new boolean[] { false, true })
		{
			for (boolean pretty : new boolean[] { false, true })
			{
				String json = SerializationLabToolbox.womToJson(getWom(), compact, pretty);

				// The article is wrapped in a document fragment
				Wom3Node loaded = SerializationLabToolbox.jsonToWom(json, compact, pretty);

				assertEquals(json, SerializationLabToolbox.womToJson(loaded.getFirstChild(), compact, pretty));
			}
		}
	}

	@Test
	public void testLabToolboxCreatesMatchingAdapters() throws Exception
	{
		assertTrue(SerializationLabToolbox.createWom3JsonTypeAdapter(true) instanceof Wom3NodeCompactJsonTypeAdapter);
		assertTrue(SerializationLabToolbox.createWom3JsonTypeAdapter(false) instanceof Wom3NodeJsonTypeAdapter);

		GsonBuilder builder = new GsonBuilder();
		assertSame(builder, SerializationLabToolbox.registerWom3GsonAdapter(builder, false));
	}

	// =========================================================================

	private static byte[] bytes(String s)
	{
		return s.getBytes(StandardCharsets.UTF_8);
	}
}
