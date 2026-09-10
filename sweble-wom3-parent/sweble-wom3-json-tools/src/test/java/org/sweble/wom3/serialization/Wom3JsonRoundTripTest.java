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
import static org.junit.Assert.assertNull;

import java.util.TreeMap;

import org.junit.Test;
import org.sweble.wom3.Wom3Node;
import org.sweble.wom3.impl.DomImplementationImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Serializes WOM documents to JSON (full and compact format) and back.
 */
public class Wom3JsonRoundTripTest
{
	static final String DEFAULT_NS = "urn:sweble:test:default";

	static final String EX_NS = "urn:sweble:test:example";

	private static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";

	// =========================================================================

	@Test
	public void testFullJsonRoundTripPreservesNamespacesAttributesAndText() throws Exception
	{
		assertRoundTrip(createNamespacedDocument(), false);
	}

	@Test
	public void testCompactJsonRoundTripPreservesNamespacesAttributesAndText() throws Exception
	{
		assertRoundTrip(createNamespacedDocument(), true);
	}

	@Test
	public void testFullJsonRoundTripOfDocumentWithoutNamespaces() throws Exception
	{
		assertRoundTrip(createPlainDocument(), false);
	}

	@Test
	public void testCompactJsonRoundTripOfDocumentWithoutNamespaces() throws Exception
	{
		assertRoundTrip(createPlainDocument(), true);
	}

	@Test
	public void testFullJsonDeclaresNamespacesWhereTheyChange() throws Exception
	{
		JsonObject root = toJsonTree(createNamespacedDocument(), false);

		assertEquals("root", root.get("!type").getAsString());
		assertEquals(DEFAULT_NS, root.get("@xmlns").getAsString());
		assertEquals(EX_NS, root.get("@xmlns:ex").getAsString());
		assertEquals("r1", root.get("@id").getAsString());
		assertEquals("yes", root.get("@ex:flag").getAsString());

		JsonArray children = root.getAsJsonArray("!children");

		assertEquals("#text", type(children.get(0)));
		assertEquals("Hello ", children.get(0).getAsJsonObject().get("!value").getAsString());

		// The prefix was already declared by the parent element
		JsonObject child = children.get(1).getAsJsonObject();
		assertEquals("ex:child", type(child));
		assertNull(child.get("@xmlns:ex"));
		assertEquals("v", child.get("@ex:attr").getAsString());

		assertEquals("#comment", type(children.get(2)));
		assertEquals("#cdata-section", type(children.get(3)));

		// WOM elements switch the default namespace
		JsonObject rtd = children.get(4).getAsJsonObject();
		assertEquals("rtd", type(rtd));
		assertEquals(Wom3Node.WOM_NS_URI, rtd.get("@xmlns").getAsString());

		// Same default namespace as the parent element
		JsonObject plain = children.get(6).getAsJsonObject();
		assertEquals("plain", type(plain));
		assertNull(plain.get("@xmlns"));

		// Leaving the default namespace requires an empty declaration
		JsonObject noNs = children.get(7).getAsJsonObject();
		assertEquals("nons", type(noNs));
		assertEquals("", noNs.get("@xmlns").getAsString());
	}

	@Test
	public void testCompactJsonUsesShortForms() throws Exception
	{
		JsonObject root = toJsonTree(createNamespacedDocument(), true);

		assertEquals(DEFAULT_NS, root.get("@xmlns").getAsString());
		assertEquals(EX_NS, root.get("@xmlns:ex").getAsString());
		assertEquals("r1", root.get("@id").getAsString());

		JsonArray children = root.getAsJsonArray("!root");

		// Text nodes are plain strings
		assertEquals("Hello ", children.get(0).getAsString());

		JsonObject child = children.get(1).getAsJsonObject();
		assertEquals("v", child.get("@ex:attr").getAsString());
		assertEquals(1, child.getAsJsonArray("!ex:child").size());

		assertEquals(" a comment ", children.get(2).getAsJsonObject().get("#c").getAsString());
		assertEquals("cdata <b>", children.get(3).getAsJsonObject().get("#cd").getAsString());

		// WOM rtd and text elements are collapsed into their text content
		assertEquals("[[raw]]", children.get(4).getAsJsonObject().get("#r").getAsString());
		assertEquals("wom text", children.get(5).getAsJsonObject().get("#t").getAsString());

		assertEquals("", children.get(7).getAsJsonObject().get("@xmlns").getAsString());
	}

	// =========================================================================

	private static void assertRoundTrip(Document doc, boolean compact)
	{
		Gson gson = createGson(compact);

		String json = gson.toJson(doc);
		Node fragment = gson.fromJson(json, Node.class);

		assertEquals(Node.DOCUMENT_FRAGMENT_NODE, fragment.getNodeType());
		assertEquals(1, fragment.getChildNodes().getLength());

		Node loaded = fragment.getFirstChild();
		assertEquals(dump(doc.getDocumentElement()), dump(loaded));
		assertEquals(json, gson.toJson(loaded));
	}

	private static JsonObject toJsonTree(Document doc, boolean compact)
	{
		return createGson(compact).toJsonTree(doc).getAsJsonObject();
	}

	private static String type(JsonElement e)
	{
		return e.getAsJsonObject().get("!type").getAsString();
	}

	static Gson createGson(boolean compact)
	{
		Wom3JsonTypeAdapterBase adapter = compact ?
				new Wom3NodeCompactJsonTypeAdapter() :
				new Wom3NodeJsonTypeAdapter();

		return new GsonBuilder()
				.registerTypeHierarchyAdapter(Node.class, adapter)
				.serializeNulls()
				.create();
	}

	static Document createEmptyDocument()
	{
		return DomImplementationImpl.get().createDocument(null, null, null);
	}

	/**
	 * Creates a document using a default namespace, a prefixed namespace, the
	 * WOM namespace and no namespace at all.
	 */
	static Document createNamespacedDocument()
	{
		Document doc = createEmptyDocument();

		Element root = doc.createElementNS(DEFAULT_NS, "root");
		root.setAttribute("id", "r1");
		root.setAttributeNS(EX_NS, "ex:flag", "yes");
		doc.appendChild(root);

		root.appendChild(doc.createTextNode("Hello "));

		Element child = doc.createElementNS(EX_NS, "ex:child");
		child.setAttributeNS(EX_NS, "ex:attr", "v");
		child.appendChild(doc.createTextNode("inner & \"quoted\" <text>"));
		root.appendChild(child);

		root.appendChild(doc.createComment(" a comment "));
		root.appendChild(doc.createCDATASection("cdata <b>"));

		Element rtd = doc.createElementNS(Wom3Node.WOM_NS_URI, "rtd");
		rtd.appendChild(doc.createTextNode("[[raw]]"));
		root.appendChild(rtd);

		Element text = doc.createElementNS(Wom3Node.WOM_NS_URI, "text");
		text.appendChild(doc.createTextNode("wom text"));
		root.appendChild(text);

		root.appendChild(doc.createElementNS(DEFAULT_NS, "plain"));
		root.appendChild(doc.createElementNS(null, "nons"));

		return doc;
	}

	static Document createPlainDocument()
	{
		Document doc = createEmptyDocument();

		Element root = doc.createElement("page");
		root.setAttribute("lang", "en");
		doc.appendChild(root);

		Element p = doc.createElement("p");
		p.appendChild(doc.createTextNode("Some text"));
		root.appendChild(p);

		root.appendChild(doc.createTextNode("tail"));

		return doc;
	}

	/**
	 * Dumps a node in a form that ignores namespace declaration attributes and
	 * attribute order.
	 */
	private static String dump(Node node)
	{
		StringBuilder sb = new StringBuilder();
		dump(sb, node);
		return sb.toString();
	}

	private static void dump(StringBuilder sb, Node node)
	{
		if (node.getNodeType() != Node.ELEMENT_NODE)
		{
			sb.append('[').append(node.getNodeName()).append(':').append(node.getNodeValue()).append(']');
			return;
		}

		TreeMap<String, String> attrs = new TreeMap<String, String>();
		NamedNodeMap attrMap = node.getAttributes();
		for (int i = 0; i < attrMap.getLength(); ++i)
		{
			Node attr = attrMap.item(i);
			if (XMLNS_URI.equals(attr.getNamespaceURI()) || attr.getNodeName().startsWith("xmlns"))
				continue;
			attrs.put(nsOf(attr) + "|" + attr.getNodeName(), attr.getNodeValue());
		}

		sb.append('<').append(nsOf(node)).append('|').append(node.getNodeName()).append(attrs).append('>');
		for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling())
			dump(sb, c);
		sb.append("</").append(node.getNodeName()).append('>');
	}

	private static String nsOf(Node node)
	{
		String ns = node.getNamespaceURI();
		return (ns == null) ? "" : ns;
	}
}
