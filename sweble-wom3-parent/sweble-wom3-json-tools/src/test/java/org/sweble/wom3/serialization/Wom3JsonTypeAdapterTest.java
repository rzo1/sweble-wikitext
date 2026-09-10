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
package org.sweble.wom3.serialization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sweble.wom3.impl.DomImplementationImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class Wom3JsonTypeAdapterTest
{
	private final Document doc =
			DomImplementationImpl.get().createDocument(null, null, null);

	private final Wom3NodeJsonTypeAdapter verbose = new Wom3NodeJsonTypeAdapter();

	private final Wom3NodeCompactJsonTypeAdapter compact = new Wom3NodeCompactJsonTypeAdapter();

	// =========================================================================
	// Serializing nodes with null data

	@Test
	public void testVerboseSerializesNullDataAsJsonNull() throws Exception
	{
		JsonObject text = verbose.serialize(doc.createTextNode(null), Node.class, null).getAsJsonObject();
		assertEquals("#text", text.get("!type").getAsString());
		assertTrue(text.get("!value").isJsonNull());

		JsonObject comment = verbose.serialize(doc.createComment(null), Node.class, null).getAsJsonObject();
		assertTrue(comment.get("!value").isJsonNull());

		JsonObject cdata = verbose.serialize(doc.createCDATASection(null), Node.class, null).getAsJsonObject();
		assertTrue(cdata.get("!value").isJsonNull());
	}

	@Test
	public void testCompactSerializesNullDataAsJsonNull() throws Exception
	{
		assertTrue(compact.serialize(doc.createTextNode(null), Node.class, null).isJsonNull());

		JsonObject comment = compact.serialize(doc.createComment(null), Node.class, null).getAsJsonObject();
		assertTrue(comment.get("#c").isJsonNull());
	}

	@Test
	public void testSerializingElementWithNullTextChildDoesNotFail() throws Exception
	{
		Element e = doc.createElement("e");
		e.appendChild(doc.createTextNode(null));

		JsonObject o = verbose.serialize(e, Node.class, null).getAsJsonObject();
		JsonObject child = o.get("!children").getAsJsonArray().get(0).getAsJsonObject();
		assertTrue(child.get("!value").isJsonNull());

		JsonObject c = compact.serialize(e, Node.class, null).getAsJsonObject();
		assertTrue(c.get("!e").getAsJsonArray().get(0).isJsonNull());
	}

	// =========================================================================
	// Round trip

	@Test
	public void testRoundTrip() throws Exception
	{
		Element e = doc.createElement("e");
		e.setAttribute("a", "1");
		e.appendChild(doc.createTextNode("text"));
		e.appendChild(doc.createComment("comment"));
		e.appendChild(doc.createCDATASection("cdata"));
		Element child = doc.createElement("child");
		child.appendChild(doc.createTextNode("inner"));
		e.appendChild(child);

		for (Wom3JsonTypeAdapterInterface adapter : new Wom3JsonTypeAdapterInterface[] { verbose, compact })
		{
			JsonElement json = adapter.serialize(e, Node.class, null);
			Node fragment = adapter.deserialize(json, Node.class, null);
			assertTrue(e.isEqualNode(fragment.getFirstChild()));
		}
	}

	// =========================================================================
	// Rejecting malformed input

	@Test
	public void testVerboseRejectsMissingValue() throws Exception
	{
		assertRejected(verbose, "{\"!type\":\"#text\"}");
		assertRejected(verbose, "{\"!type\":\"#comment\"}");
		assertRejected(verbose, "{\"!type\":\"#cdata-section\"}");
	}

	@Test
	public void testVerboseRejectsNullValue() throws Exception
	{
		assertRejected(verbose, "{\"!type\":\"#text\",\"!value\":null}");
		assertRejected(verbose, "{\"!type\":\"#comment\",\"!value\":null}");
	}

	@Test
	public void testVerboseRejectsNonStringValue() throws Exception
	{
		assertRejected(verbose, "{\"!type\":\"#text\",\"!value\":{}}");
		assertRejected(verbose, "{\"!type\":\"#text\",\"!value\":[\"a\",\"b\"]}");
		assertRejected(verbose, "{\"!type\":\"#text\",\"!value\":[]}");
	}

	@Test
	public void testVerboseRejectsMalformedStructure() throws Exception
	{
		assertRejected(verbose, "null");
		assertRejected(verbose, "\"text\"");
		assertRejected(verbose, "[]");
		assertRejected(verbose, "{}");
		assertRejected(verbose, "{\"!type\":null}");
		assertRejected(verbose, "{\"!type\":{}}");
		assertRejected(verbose, "{\"!type\":\"e\",\"!children\":null}");
		assertRejected(verbose, "{\"!type\":\"e\",\"!children\":[null]}");
		assertRejected(verbose, "{\"!type\":\"e\",\"!children\":[\"text\"]}");
		assertRejected(verbose, "{\"!type\":\"e\",\"@a\":null}");
		assertRejected(verbose, "{\"!type\":\"e\",\"@a\":[]}");
		assertRejected(verbose, "{\"!type\":\"e\",\"!value\":\"x\"}");
		assertRejected(verbose, "{\"!type\":\"#text\",\"!value\":\"x\",\"!children\":[]}");
		assertRejected(verbose, "{\"!type\":\"#unknown\",\"!value\":\"x\"}");
		assertRejected(verbose, "{\"!type\":\"e\",\"x\":1}");
	}

	@Test
	public void testCompactRejectsNullValue() throws Exception
	{
		assertRejected(compact, "{\"#t\":null}");
		assertRejected(compact, "{\"#c\":null}");
		assertRejected(compact, "{\"#cd\":null}");
		assertRejected(compact, "{\"#r\":null}");
	}

	@Test
	public void testCompactRejectsNonStringValue() throws Exception
	{
		assertRejected(compact, "{\"#t\":{}}");
		assertRejected(compact, "{\"#c\":[\"a\",\"b\"]}");
		assertRejected(compact, "{\"#c\":[]}");
	}

	@Test
	public void testCompactRejectsMalformedStructure() throws Exception
	{
		assertRejected(compact, "null");
		assertRejected(compact, "[]");
		assertRejected(compact, "{}");
		assertRejected(compact, "{\"!e\":null}");
		assertRejected(compact, "{\"!e\":{}}");
		assertRejected(compact, "{\"!e\":[null]}");
		assertRejected(compact, "{\"!e\":[],\"#t\":\"x\"}");
		assertRejected(compact, "{\"!e\":[],\"@a\":null}");
		assertRejected(compact, "{\"#t\":\"x\",\"@a\":\"y\"}");
		assertRejected(compact, "{\"#unknown\":\"x\"}");
		assertRejected(compact, "{\"x\":1}");
	}

	@Test
	public void testRejectsUndeclaredPrefixes() throws Exception
	{
		assertRejected(verbose, "{\"!type\":\"p:e\"}");
		assertRejected(verbose, "{\"!type\":\"e\",\"@p:a\":\"v\"}");

		assertRejected(compact, "{\"!p:e\":[]}");
		assertRejected(compact, "{\"!e\":[],\"@p:a\":\"v\"}");
	}

	@Test
	public void testRejectsNamespaceErrors() throws Exception
	{
		assertRejected(verbose, "{\"!type\":\"p:e\",\"@xmlns:p\":\"\"}");
		assertRejected(verbose, "{\"!type\":\"xml:e\",\"@xmlns:xml\":\"urn:x\"}");
		assertRejected(verbose, "{\"!type\":\"e\",\"@xmlns:p\":\"urn:p\",\"@p:a:b\":\"v\"}");

		assertRejected(compact, "{\"!p:e\":[],\"@xmlns:p\":\"\"}");
		assertRejected(compact, "{\"!xml:e\":[],\"@xmlns:xml\":\"urn:x\"}");
		assertRejected(compact, "{\"!e\":[],\"@xmlns:p\":\"urn:p\",\"@p:a:b\":\"v\"}");
	}

	@Test
	public void testRejectsInvalidNames() throws Exception
	{
		assertRejected(verbose, "{\"!type\":\"1e\"}");
		assertRejected(verbose, "{\"!type\":\"a b\"}");
		assertRejected(verbose, "{\"!type\":\"e\",\"@1a\":\"v\"}");
		assertRejected(verbose, "{\"!type\":\"e\",\"!children\":[{\"!type\":\"<x>\"}]}");

		assertRejected(compact, "{\"!1e\":[]}");
		assertRejected(compact, "{\"!a b\":[]}");
		assertRejected(compact, "{\"!e\":[],\"@1a\":\"v\"}");
		assertRejected(compact, "{\"!e\":[{\"!<x>\":[]}]}");
	}

	// =========================================================================

	private static void assertRejected(Wom3JsonTypeAdapterInterface adapter, String json)
	{
		JsonElement parsed = new Gson().fromJson(json, JsonElement.class);
		if (parsed == null)
			parsed = JsonNull.INSTANCE;
		try
		{
			adapter.deserialize(parsed, Node.class, null);
			fail("Expected a JsonParseException for: " + json);
		}
		catch (JsonParseException e)
		{
			// Expected
		}
	}
}
