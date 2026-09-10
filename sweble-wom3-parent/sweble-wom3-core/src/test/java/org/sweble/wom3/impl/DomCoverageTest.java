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
package org.sweble.wom3.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sweble.wom3.Wom3ElementNode;
import org.sweble.wom3.Wom3Node;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Covers the namespace aware attribute methods, getElementsByTagName(NS) and
 * cloneNode().
 */
public class DomCoverageTest
{
	private static final String NS = "http://example.org/ns";

	private static final String OTHER_NS = "http://example.org/other";

	private final DocumentImpl doc =
			DomImplementationImpl.get().createDocument(null, null, null);

	// =========================================================================
	// Namespace aware attribute methods

	@Test
	public void testSetAndGetAttributeNS() throws Exception
	{
		Wom3ElementNode e = doc.createElementNS(NS, "ex:e");
		e.setAttributeNS(NS, "ex:a", "1");

		assertTrue(e.hasAttributeNS(NS, "a"));
		assertFalse(e.hasAttributeNS(OTHER_NS, "a"));
		assertFalse(e.hasAttributeNS(null, "a"));
		assertEquals("1", e.getAttributeNS(NS, "a"));
		assertEquals("", e.getAttributeNS(OTHER_NS, "a"));
		assertEquals("1", e.getAttribute("ex:a"));

		Attr attr = e.getAttributeNodeNS(NS, "a");
		assertEquals("ex:a", attr.getName());
		assertEquals("a", attr.getLocalName());
		assertEquals("ex", attr.getPrefix());
		assertEquals(NS, attr.getNamespaceURI());
		assertSame(e, attr.getOwnerElement());
		assertEquals(1, e.getAttributes().getLength());

		e.setAttributeNS(NS, "ex:a", "2");
		assertEquals("2", e.getAttributeNS(NS, "a"));
		assertEquals(1, e.getAttributes().getLength());
	}

	@Test
	public void testSameLocalNameInDifferentNamespaces() throws Exception
	{
		Wom3ElementNode e = doc.createElementNS(NS, "ex:e");
		e.setAttributeNS(NS, "ex:a", "1");
		e.setAttributeNS(OTHER_NS, "o:a", "2");

		assertEquals(2, e.getAttributes().getLength());
		assertEquals("1", e.getAttributeNS(NS, "a"));
		assertEquals("2", e.getAttributeNS(OTHER_NS, "a"));

		e.removeAttributeNS(NS, "a");
		assertFalse(e.hasAttributeNS(NS, "a"));
		assertTrue(e.hasAttributeNS(OTHER_NS, "a"));
		assertEquals(1, e.getAttributes().getLength());

		// Removing a missing attribute is not an error
		e.removeAttributeNS(NS, "a");
		assertEquals(1, e.getAttributes().getLength());
	}

	@Test
	public void testAttributeNSWithoutNamespace() throws Exception
	{
		Wom3ElementNode e = doc.createElementNS(NS, "ex:e");
		e.setAttributeNS(null, "plain", "v");

		assertTrue(e.hasAttributeNS(null, "plain"));
		assertEquals("v", e.getAttributeNS(null, "plain"));
		assertEquals("v", e.getAttribute("plain"));
		assertNull(e.getAttributeNodeNS(null, "plain").getNamespaceURI());

		// A DOM Level 1 attribute can be found with a null namespace URI
		e.setAttribute("lvl1", "x");
		assertTrue(e.hasAttributeNS(null, "lvl1"));
		assertEquals("x", e.getAttributeNS(null, "lvl1"));
	}

	@Test
	public void testSetAttributeNodeNS() throws Exception
	{
		Wom3ElementNode e = doc.createElementNS(NS, "ex:e");

		Attr a = doc.createAttributeNS(NS, "ex:b");
		a.setValue("3");
		assertNull(e.setAttributeNodeNS(a));
		assertSame(a, e.getAttributeNodeNS(NS, "b"));
		assertSame(e, a.getOwnerElement());

		Attr b = doc.createAttributeNS(NS, "ex:b");
		b.setValue("4");
		assertSame(a, e.setAttributeNodeNS(b));
		assertNull(a.getOwnerElement());
		assertSame(e, b.getOwnerElement());
		assertEquals("4", e.getAttributeNS(NS, "b"));
		assertEquals(1, e.getAttributes().getLength());

		assertSame(b, e.removeAttributeNode(b));
		assertFalse(e.hasAttributeNS(NS, "b"));
		assertFalse(e.hasAttributes());
	}

	// =========================================================================
	// getElementsByTagName(NS)

	private Wom3ElementNode root;

	private Wom3ElementNode item1;

	private Wom3ElementNode noNs;

	private Wom3ElementNode other;

	private Wom3ElementNode item2;

	private Wom3ElementNode foreign;

	private void buildTree()
	{
		root = doc.createElementNS(NS, "ex:root");
		item1 = doc.createElementNS(NS, "ex:item");
		noNs = doc.createElementNS(null, "item");
		other = doc.createElementNS(NS, "ex:other");
		item2 = doc.createElementNS(NS, "ex:item");
		foreign = doc.createElementNS(OTHER_NS, "o:item");

		root.appendChild(item1);
		root.appendChild(doc.createTextNode("t"));
		root.appendChild(noNs);
		root.appendChild(other);
		other.appendChild(item2);
		other.appendChild(foreign);
	}

	@Test
	public void testGetElementsByTagName() throws Exception
	{
		buildTree();

		assertNodes(root.getElementsByTagName("ex:item"), item1, item2);
		assertNodes(root.getElementsByTagName("item"), noNs);
		assertNodes(root.getElementsByTagName("*"), item1, noNs, other, item2, foreign);
		assertNodes(root.getElementsByTagName("nope"));

		// Only descendants of the element are searched
		assertNodes(other.getElementsByTagName("*"), item2, foreign);
		assertNodes(item1.getElementsByTagName("*"));
		assertNodes(doc.createElement("lonely").getElementsByTagName("*"));
	}

	@Test
	public void testGetElementsByTagNameNS() throws Exception
	{
		buildTree();

		assertNodes(root.getElementsByTagNameNS(NS, "item"), item1, item2);
		assertNodes(root.getElementsByTagNameNS(null, "item"), noNs);
		assertNodes(root.getElementsByTagNameNS("", "item"), noNs);
		assertNodes(root.getElementsByTagNameNS("*", "item"), item1, noNs, item2, foreign);
		assertNodes(root.getElementsByTagNameNS(NS, "*"), item1, other, item2);
		assertNodes(root.getElementsByTagNameNS(OTHER_NS, "item"), foreign);
		assertNodes(root.getElementsByTagNameNS("*", "*"), item1, noNs, other, item2, foreign);
		assertNodes(root.getElementsByTagNameNS(NS, "root"));

		assertNodes(item1.getElementsByTagNameNS("*", "*"));
	}

	@Test
	public void testDocumentGetElementsByTagName() throws Exception
	{
		buildTree();
		doc.appendChild(root);

		assertNodes(doc.getElementsByTagName("ex:item"), item1, item2);
		assertNodes(doc.getElementsByTagName("ex:root"), root);
		assertNodes(doc.getElementsByTagNameNS(NS, "item"), item1, item2);
		assertNodes(doc.getElementsByTagNameNS(NS, "root"), root);
		assertNodes(doc.getElementsByTagNameNS("*", "*"), root, item1, noNs, other, item2, foreign);
	}

	private static void assertNodes(NodeList list, Node... expected)
	{
		assertEquals(expected.length, list.getLength());
		for (int i = 0; i < expected.length; ++i)
			assertSame(expected[i], list.item(i));
		assertNull(list.item(expected.length));
	}

	// =========================================================================
	// cloneNode()

	@Test
	public void testShallowCloneNode() throws Exception
	{
		Wom3ElementNode e = doc.createElement("root");
		e.setAttribute("x", "1");
		Wom3ElementNode child = doc.createElement("child");
		e.appendChild(child);
		e.appendChild(doc.createTextNode("t"));

		Wom3Node clone = e.cloneNode(false);

		assertNotSame(e, clone);
		assertEquals("root", clone.getNodeName());
		assertSame(doc, clone.getOwnerDocument());
		assertNull(clone.getParentNode());
		assertNull(clone.getFirstChild());
		assertNull(clone.getLastChild());
		assertFalse(clone.hasChildNodes());
		assertEquals(0, clone.getChildNodes().getLength());

		// Attributes are always cloned
		Element cloneElem = (Element) clone;
		assertEquals("1", cloneElem.getAttribute("x"));
		assertNotSame(e.getAttributeNode("x"), cloneElem.getAttributeNode("x"));
		assertSame(cloneElem, cloneElem.getAttributeNode("x").getOwnerElement());

		// The original is untouched
		assertEquals(2, e.getChildNodes().getLength());
		assertSame(e, child.getParentNode());
	}

	@Test
	public void testDeepCloneNode() throws Exception
	{
		Wom3ElementNode e = doc.createElement("root");
		e.setAttribute("x", "1");
		Wom3ElementNode child = doc.createElement("child");
		child.setAttribute("y", "2");
		child.appendChild(doc.createTextNode("inner"));
		e.appendChild(child);
		e.appendChild(doc.createTextNode("t"));
		e.appendChild(doc.createComment("c"));

		Wom3Node clone = e.cloneNode(true);

		assertNotSame(e, clone);
		assertTrue(e.isEqualNode(clone));
		assertEquals(3, clone.getChildNodes().getLength());

		Node cloneChild = clone.getFirstChild();
		assertNotSame(child, cloneChild);
		assertSame(clone, cloneChild.getParentNode());
		assertEquals("2", ((Element) cloneChild).getAttribute("y"));
		assertEquals("inner", cloneChild.getTextContent());
		assertSame(clone.getLastChild(), cloneChild.getNextSibling().getNextSibling());
		assertEquals(Node.COMMENT_NODE, clone.getLastChild().getNodeType());

		// The clone is independent of the original
		((Element) cloneChild).setAttribute("y", "3");
		assertEquals("2", child.getAttribute("y"));
		clone.appendChild(doc.createElement("more"));
		assertEquals(3, e.getChildNodes().getLength());
		assertEquals(4, clone.getChildNodes().getLength());
	}

	@Test
	public void testCloneOfChildIsDetached() throws Exception
	{
		Wom3ElementNode e = doc.createElement("root");
		Wom3ElementNode a = doc.createElement("a");
		Wom3ElementNode b = doc.createElement("b");
		e.appendChild(a);
		e.appendChild(b);

		Wom3Node clone = a.cloneNode(true);
		assertNull(clone.getParentNode());
		assertNull(clone.getPreviousSibling());
		assertNull(clone.getNextSibling());

		e.appendChild(clone);
		assertEquals(3, e.getChildNodes().getLength());
		assertSame(clone, e.getLastChild());
		assertSame(a, e.getFirstChild());
	}

	@Test
	public void testCloneCharacterData() throws Exception
	{
		Wom3Node text = doc.createTextNode("t");
		Node textClone = text.cloneNode(false);
		assertNotSame(text, textClone);
		assertEquals("t", textClone.getNodeValue());
		assertTrue(text.isEqualNode(textClone));

		Wom3Node comment = doc.createComment("c");
		Node commentClone = comment.cloneNode(true);
		assertNotSame(comment, commentClone);
		assertEquals("c", commentClone.getNodeValue());
	}
}
