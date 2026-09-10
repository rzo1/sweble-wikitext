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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.sweble.wom3.serialization.Wom3JsonRoundTripTest.DEFAULT_NS;
import static org.sweble.wom3.serialization.Wom3JsonRoundTripTest.EX_NS;
import static org.sweble.wom3.serialization.Wom3JsonRoundTripTest.createEmptyDocument;
import static org.sweble.wom3.serialization.Wom3JsonRoundTripTest.createGson;

import org.junit.Test;
import org.sweble.wom3.serialization.ScopeStack.Scope;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * Error handling of the WOM JSON type adapters and the namespace scope stack.
 */
public class Wom3JsonToolsCoverageTest
{
	// ==[ Full format: malformed input ]=======================================

	@Test
	public void testFullFormatRejectsMalformedInput() throws Exception
	{
		assertRejected(false, "[]", JsonParseException.class);
		assertRejected(false, "{\"!children\": []}", JsonParseException.class);
		assertRejected(false, "{\"!type\": [], \"!children\": []}", JsonParseException.class);
		assertRejected(false, "{\"!type\": \"#bogus\", \"!value\": \"x\"}", JsonParseException.class);
		assertRejected(false, "{\"!type\": \"#text\", \"!value\": \"x\", \"@a\": \"b\"}", JsonParseException.class);
		assertRejected(false, "{\"!type\": \"#text\", \"!children\": []}", JsonParseException.class);
		assertRejected(false, "{\"!type\": \"e\", \"!value\": \"x\"}", JsonParseException.class);
		assertRejected(false, "{\"!type\": \"e\", \"!children\": {}}", JsonParseException.class);
		assertRejected(false, "{\"!type\": \"e\", \"!children\": [], \"unexpected\": 1}", JsonParseException.class);
		assertRejected(false, "{\"!type\": \"e\", \"@a\": [], \"!children\": []}", JsonParseException.class);
	}

	@Test
	public void testFullFormatRejectsUndeclaredPrefixes() throws Exception
	{
		assertRejected(false, "{\"!type\": \"p:e\", \"!children\": []}", NamespaceException.class);
		assertRejected(false, "{\"!type\": \"e\", \"@p:a\": \"v\", \"!children\": []}", NamespaceException.class);
	}

	@Test
	public void testFullFormatRejectsConflictingNamespaceDeclarations() throws Exception
	{
		// The element name declares "p" implicitly in the same scope as the
		// conflicting explicit declaration
		Node fragment = parse(false, "{\"!type\": \"p:e\", \"@xmlns:p\": \"urn:a\", \"!children\": []}");
		assertEquals("urn:a", fragment.getFirstChild().getNamespaceURI());
	}

	// ==[ Compact format: malformed input ]====================================

	@Test
	public void testCompactFormatRejectsMalformedInput() throws Exception
	{
		assertRejected(true, "[]", JsonParseException.class);
		assertRejected(true, "{\"!a\": [], \"!b\": []}", JsonParseException.class);
		assertRejected(true, "{\"#t\": \"x\", \"#c\": \"y\"}", JsonParseException.class);
		assertRejected(true, "{\"#bogus\": \"x\"}", JsonParseException.class);
		assertRejected(true, "{\"@a\": \"b\"}", JsonParseException.class);
		assertRejected(true, "{\"#t\": \"x\", \"@a\": \"b\"}", JsonParseException.class);
		assertRejected(true, "{\"unexpected\": 1}", JsonParseException.class);
		assertRejected(true, "{\"!e\": \"not an array\"}", JsonParseException.class);
		assertRejected(true, "{\"!e\": [], \"@a\": []}", JsonParseException.class);
	}

	@Test
	public void testCompactFormatRejectsUndeclaredPrefixes() throws Exception
	{
		assertRejected(true, "{\"!p:e\": []}", NamespaceException.class);
		assertRejected(true, "{\"!e\": [], \"@p:a\": \"v\"}", NamespaceException.class);
	}

	@Test
	public void testCompactFormatReadsBareStringAsTextNode() throws Exception
	{
		Node text = parse(true, "\"just text\"").getFirstChild();

		assertEquals(Node.TEXT_NODE, text.getNodeType());
		assertEquals("just text", text.getNodeValue());
	}

	@Test
	public void testCompactFormatResolvesPrefixesFromAncestors() throws Exception
	{
		Node root = parse(true, "{\"@xmlns:p\": \"urn:p\", \"!p:a\": [{\"!p:b\": []}]}").getFirstChild();

		assertEquals("urn:p", root.getNamespaceURI());
		assertEquals("urn:p", root.getFirstChild().getNamespaceURI());
		assertEquals("b", root.getFirstChild().getLocalName());
	}

	// ==[ Serialization of unsupported nodes ]=================================

	@Test
	public void testAttributesCannotBeSerializedOnTheirOwn() throws Exception
	{
		Document doc = createEmptyDocument();
		for (boolean compact : new boolean[] { false, true })
		{
			Node attr = doc.createAttribute("a");
			try
			{
				createGson(compact).toJson(attr);
				fail("Expected UnsupportedNodeException");
			}
			catch (UnsupportedNodeException e)
			{
				assertSame(attr, e.getNode());
			}
		}
	}

	@Test
	public void testNamespacedAttributeWithoutPrefixCannotBeSerialized() throws Exception
	{
		Document doc = createEmptyDocument();
		Element root = doc.createElementNS(DEFAULT_NS, "root");
		root.setAttributeNS(EX_NS, "bare", "v");
		doc.appendChild(root);

		for (boolean compact : new boolean[] { false, true })
		{
			try
			{
				createGson(compact).toJson(doc);
				fail("Expected JsonParseException");
			}
			catch (JsonParseException e)
			{
				// Expected
			}
		}
	}

	// ==[ Target document ]====================================================

	@Test
	public void testAdapterCreatesDocumentLazily() throws Exception
	{
		Wom3JsonTypeAdapterInterface adapter = new Wom3NodeCompactJsonTypeAdapter();

		Document doc = adapter.getDoc();
		assertNotNull(doc);
		assertSame(doc, adapter.getDoc());
	}

	@Test
	public void testDeserializationUsesConfiguredDocument() throws Exception
	{
		for (boolean compact : new boolean[] { false, true })
		{
			Document target = createEmptyDocument();

			Wom3JsonTypeAdapterBase adapter = compact ?
					new Wom3NodeCompactJsonTypeAdapter() :
					new Wom3NodeJsonTypeAdapter();
			adapter.setDoc(target);

			String json = compact ?
					"{\"!e\": []}" :
					"{\"!type\": \"e\", \"!children\": []}";

			Node fragment = new GsonBuilder()
					.registerTypeHierarchyAdapter(Node.class, adapter)
					.create()
					.fromJson(json, Node.class);

			assertSame(target, fragment.getOwnerDocument());
			assertSame(target, fragment.getFirstChild().getOwnerDocument());
		}
	}

	// ==[ Scope stack ]========================================================

	@Test
	public void testScopeStackResolvesFromInnermostScope() throws Exception
	{
		ScopeStack stack = new ScopeStack();
		assertNull(stack.getXmlns());
		assertNull(stack.getNsUriForPrefix("a"));

		Scope outer = stack.push();
		outer.put("a", "urn:outer");
		outer.setXmlns("urn:default");

		Scope inner = stack.push();
		assertSame(outer, inner.getParent());
		inner.put("b", "urn:inner");

		assertEquals("urn:outer", stack.getNsUriForPrefix("a"));
		assertEquals("urn:inner", stack.getNsUriForPrefix("b"));
		assertEquals("urn:default", stack.getXmlns());

		inner.put("a", "urn:shadowed");
		inner.setXmlns("urn:other");
		assertEquals("urn:shadowed", stack.getNsUriForPrefix("a"));
		assertEquals("urn:other", stack.getXmlns());

		stack.pop();
		assertEquals("urn:outer", stack.getNsUriForPrefix("a"));
		assertNull(stack.getNsUriForPrefix("b"));
		assertEquals("urn:default", stack.getXmlns());

		stack.pop();
		assertNull(stack.getXmlns());
	}

	@Test
	public void testScopeAcceptsRepeatedIdenticalDeclarations() throws Exception
	{
		Scope scope = new ScopeStack().push();

		scope.put("a", "urn:a");
		scope.put("a", "urn:a");
		scope.setXmlns("urn:d");
		scope.setXmlns("urn:d");

		assertEquals("urn:a", scope.getNsUriForPrefix("a"));
		assertEquals("urn:d", scope.getXmlns());
	}

	@Test(expected = NamespaceException.class)
	public void testScopeRejectsConflictingPrefixDeclaration() throws Exception
	{
		Scope scope = new ScopeStack().push();
		scope.put("a", "urn:a");
		scope.put("a", "urn:b");
	}

	@Test(expected = NamespaceException.class)
	public void testScopeRejectsConflictingDefaultNamespace() throws Exception
	{
		Scope scope = new ScopeStack().push();
		scope.setXmlns("urn:a");
		scope.setXmlns("urn:b");
	}

	@Test(expected = InternalError.class)
	public void testPoppingEmptyScopeStackFails() throws Exception
	{
		new ScopeStack().pop();
	}

	// =========================================================================

	private static Node parse(boolean compact, String json)
	{
		return createGson(compact).fromJson(json, Node.class);
	}

	private static void assertRejected(
			boolean compact,
			String json,
			Class<? extends RuntimeException> expected)
	{
		try
		{
			parse(compact, json);
			fail("Expected " + expected.getSimpleName() + " for: " + json);
		}
		catch (RuntimeException e)
		{
			if (!expected.isInstance(e))
				throw new AssertionError("Expected " + expected.getSimpleName() + " for: " + json, e);
		}
	}
}
