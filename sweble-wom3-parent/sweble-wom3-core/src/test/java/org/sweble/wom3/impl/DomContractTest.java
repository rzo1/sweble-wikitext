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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.sweble.wom3.Wom3Article;
import org.sweble.wom3.Wom3Category;
import org.sweble.wom3.Wom3DocumentFragment;
import org.sweble.wom3.Wom3ElementNode;
import org.sweble.wom3.Wom3Node;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Tests the DOM Level 3 Core contract of the child and document operations.
 */
public class DomContractTest
{
	private final DocumentImpl doc =
			DomImplementationImpl.get().createDocument(null, null, null);

	private final DocumentImpl otherDoc =
			DomImplementationImpl.get().createDocument(null, null, null);

	// =========================================================================
	// Document fragments

	@Test
	public void testInsertBeforeWithDocumentFragmentInsertsItsChildren() throws Exception
	{
		Wom3ElementNode root = elem("root");
		Wom3ElementNode ref = elem("ref");
		root.appendChild(ref);

		Wom3DocumentFragment fragment = doc.createDocumentFragment();
		Wom3ElementNode a = elem("a");
		Wom3ElementNode b = elem("b");
		fragment.appendChild(a);
		fragment.appendChild(b);

		assertSame(fragment, root.insertBefore(fragment, ref));

		assertChildren(root, a, b, ref);
		assertNull(fragment.getFirstChild());
		assertNull(fragment.getParentNode());
	}

	@Test
	public void testAppendChildWithDocumentFragmentAppendsItsChildren() throws Exception
	{
		Wom3ElementNode root = elem("root");
		Wom3ElementNode ref = elem("ref");
		root.appendChild(ref);

		Wom3DocumentFragment fragment = doc.createDocumentFragment();
		Wom3ElementNode a = elem("a");
		Wom3ElementNode b = elem("b");
		fragment.appendChild(a);
		fragment.appendChild(b);

		root.appendChild(fragment);

		assertChildren(root, ref, a, b);
		assertNull(fragment.getFirstChild());
	}

	@Test
	public void testInsertBeforeWithRejectedFragmentChildChangesNothing() throws Exception
	{
		doc.setStrictErrorChecking(true);
		final Wom3ElementNode article = wom("article");
		final Wom3ElementNode body = wom("body");
		article.appendChild(body);

		final Wom3DocumentFragment fragment = doc.createDocumentFragment();
		Wom3ElementNode cat = category("c");
		Wom3ElementNode p = wom("p");
		fragment.appendChild(cat);
		fragment.appendChild(p);

		assertRejected(() -> article.insertBefore(fragment, body));

		assertChildren(article, body);
		assertChildren(fragment, cat, p);
	}

	@Test
	public void testAppendChildWithRejectedFragmentChildChangesNothing() throws Exception
	{
		doc.setStrictErrorChecking(true);
		final Wom3ElementNode article = wom("article");
		Wom3ElementNode redirect = wom("redirect");
		article.appendChild(redirect);

		final Wom3DocumentFragment fragment = doc.createDocumentFragment();
		Wom3ElementNode cat = category("c");
		Wom3ElementNode p = wom("p");
		fragment.appendChild(cat);
		fragment.appendChild(p);

		assertRejected(() -> article.appendChild(fragment));

		assertChildren(article, redirect);
		assertChildren(fragment, cat, p);
	}

	@Test
	public void testReplaceChildWithRejectedFragmentChildChangesNothing() throws Exception
	{
		doc.setStrictErrorChecking(true);
		final Wom3ElementNode article = wom("article");
		final Wom3ElementNode redirect = wom("redirect");
		Wom3ElementNode body = wom("body");
		article.appendChild(redirect);
		article.appendChild(body);

		final Wom3DocumentFragment fragment = doc.createDocumentFragment();
		Wom3ElementNode cat1 = category("c1");
		Wom3ElementNode cat2 = category("c2");
		Wom3ElementNode p = wom("p");
		fragment.appendChild(cat1);
		fragment.appendChild(cat2);
		fragment.appendChild(p);

		assertRejected(() -> article.replaceChild(fragment, redirect));

		assertChildren(article, redirect, body);
		assertChildren(fragment, cat1, cat2, p);
		assertSame(redirect, ((Wom3Article) article).getRedirect());
	}

	// =========================================================================
	// insertBefore(x, null)

	@Test
	public void testInsertBeforeNullAppends() throws Exception
	{
		Wom3ElementNode root = elem("root");
		Wom3ElementNode a = elem("a");
		Wom3ElementNode b = elem("b");
		root.appendChild(a);

		assertSame(b, root.insertBefore(b, null));

		assertChildren(root, a, b);
	}

	// =========================================================================
	// Hierarchy checks

	@Test
	public void testAppendChildToItselfFails() throws Exception
	{
		final Wom3ElementNode root = elem("root");

		assertDomException(DOMException.HIERARCHY_REQUEST_ERR, () -> root.appendChild(root));

		assertNull(root.getParentNode());
		assertNull(root.getFirstChild());
	}

	@Test
	public void testAppendAncestorToDescendantFails() throws Exception
	{
		final Wom3ElementNode a = elem("a");
		final Wom3ElementNode b = elem("b");
		final Wom3ElementNode c = elem("c");
		a.appendChild(b);
		b.appendChild(c);

		assertDomException(DOMException.HIERARCHY_REQUEST_ERR, () -> c.appendChild(a));

		assertNull(a.getParentNode());
		assertNull(c.getFirstChild());
		assertChildren(a, b);
		assertChildren(b, c);
	}

	@Test
	public void testInsertBeforeWithAncestorFails() throws Exception
	{
		final Wom3ElementNode a = elem("a");
		final Wom3ElementNode b = elem("b");
		final Wom3ElementNode c = elem("c");
		a.appendChild(b);
		b.appendChild(c);

		assertDomException(DOMException.HIERARCHY_REQUEST_ERR, () -> b.insertBefore(a, c));

		assertNull(a.getParentNode());
		assertChildren(b, c);
	}

	@Test
	public void testReplaceChildWithAncestorFails() throws Exception
	{
		final Wom3ElementNode a = elem("a");
		final Wom3ElementNode b = elem("b");
		final Wom3ElementNode c = elem("c");
		a.appendChild(b);
		b.appendChild(c);

		assertDomException(DOMException.HIERARCHY_REQUEST_ERR, () -> b.replaceChild(a, c));

		assertNull(a.getParentNode());
		assertChildren(b, c);
	}

	@Test
	public void testAppendFragmentContainingTheParentFails() throws Exception
	{
		final Wom3DocumentFragment fragment = doc.createDocumentFragment();
		final Wom3ElementNode a = elem("a");
		fragment.appendChild(a);

		assertDomException(DOMException.HIERARCHY_REQUEST_ERR, () -> a.appendChild(fragment));

		assertChildren(fragment, a);
		assertNull(a.getFirstChild());
	}

	@Test
	public void testAppendAttributeAsChildFails() throws Exception
	{
		final Wom3ElementNode root = elem("root");
		final Attr attr = doc.createAttribute("x");

		assertDomException(DOMException.HIERARCHY_REQUEST_ERR, () -> root.appendChild(attr));

		assertNull(root.getFirstChild());
	}

	@Test
	public void testAppendDocumentAsChildFails() throws Exception
	{
		final Wom3ElementNode root = elem("root");

		assertDomException(DOMException.HIERARCHY_REQUEST_ERR, () -> root.appendChild(otherDoc));

		assertNull(root.getFirstChild());
	}

	@Test
	public void testChildOperationsOnTextNodeFail() throws Exception
	{
		final Wom3Node text = text("t");
		final Wom3ElementNode e = elem("e");

		assertDomException(DOMException.HIERARCHY_REQUEST_ERR, () -> text.appendChild(e));
		assertDomException(DOMException.HIERARCHY_REQUEST_ERR, () -> text.insertBefore(e, null));
		assertDomException(DOMException.HIERARCHY_REQUEST_ERR, () -> text.replaceChild(e, e));
		assertDomException(DOMException.NOT_FOUND_ERR, () -> text.removeChild(e));
	}

	// =========================================================================
	// Wrong document

	@Test
	public void testAppendNodeFromOtherDocumentFails() throws Exception
	{
		final Wom3ElementNode root = elem("root");
		final Wom3ElementNode foreign = otherDoc.createElement("foreign");

		assertDomException(DOMException.WRONG_DOCUMENT_ERR, () -> root.appendChild(foreign));

		assertNull(root.getFirstChild());
		assertNull(foreign.getParentNode());
	}

	// =========================================================================
	// Moving linked nodes

	@Test
	public void testAppendChildMovesNodeFromOtherParent() throws Exception
	{
		Wom3ElementNode p1 = elem("p1");
		Wom3ElementNode p2 = elem("p2");
		Wom3ElementNode a = elem("a");
		Wom3ElementNode b = elem("b");
		p1.appendChild(a);
		p1.appendChild(b);

		assertSame(a, p2.appendChild(a));

		assertChildren(p1, b);
		assertChildren(p2, a);
	}

	@Test
	public void testAppendChildMovesNodeWithinParent() throws Exception
	{
		Wom3ElementNode root = elem("root");
		Wom3ElementNode a = elem("a");
		Wom3ElementNode b = elem("b");
		Wom3ElementNode c = elem("c");
		root.appendChild(a);
		root.appendChild(b);
		root.appendChild(c);

		root.appendChild(a);
		assertChildren(root, b, c, a);

		root.appendChild(a);
		assertChildren(root, b, c, a);
	}

	@Test
	public void testInsertBeforeMovesNodeWithinParent() throws Exception
	{
		Wom3ElementNode root = elem("root");
		Wom3ElementNode a = elem("a");
		Wom3ElementNode b = elem("b");
		Wom3ElementNode c = elem("c");
		root.appendChild(a);
		root.appendChild(b);
		root.appendChild(c);

		root.insertBefore(c, a);
		assertChildren(root, c, a, b);

		root.insertBefore(a, b);
		assertChildren(root, c, a, b);

		root.insertBefore(c, c);
		assertChildren(root, c, a, b);
	}

	@Test
	public void testInsertBeforeMovesNodeFromOtherParent() throws Exception
	{
		Wom3ElementNode p1 = elem("p1");
		Wom3ElementNode p2 = elem("p2");
		Wom3ElementNode a = elem("a");
		Wom3ElementNode ref = elem("ref");
		p1.appendChild(a);
		p2.appendChild(ref);

		p2.insertBefore(a, ref);

		assertNull(p1.getFirstChild());
		assertChildren(p2, a, ref);
	}

	@Test
	public void testReplaceChildMovesNodeFromOtherParent() throws Exception
	{
		Wom3ElementNode p1 = elem("p1");
		Wom3ElementNode p2 = elem("p2");
		Wom3ElementNode a = elem("a");
		Wom3ElementNode b = elem("b");
		p1.appendChild(a);
		p2.appendChild(b);

		assertSame(b, p2.replaceChild(a, b));

		assertNull(p1.getFirstChild());
		assertChildren(p2, a);
		assertNull(b.getParentNode());
	}

	@Test
	public void testReplaceChildWithSibling() throws Exception
	{
		Wom3ElementNode root = elem("root");
		Wom3ElementNode a = elem("a");
		Wom3ElementNode b = elem("b");
		Wom3ElementNode c = elem("c");
		root.appendChild(a);
		root.appendChild(b);
		root.appendChild(c);

		assertSame(c, root.replaceChild(a, c));

		assertChildren(root, b, a);
		assertNull(c.getParentNode());
	}

	@Test
	public void testRejectedMoveLeavesNodeAtItsOldPosition() throws Exception
	{
		Wom3Article article = (Wom3Article) TestHelperDoc.genElem("article");
		Wom3ElementNode body = TestHelperDoc.genElem("body");
		Wom3ElementNode b1 = TestHelperDoc.genElem("b");
		Wom3ElementNode b = TestHelperDoc.genElem("b");
		Wom3ElementNode b2 = TestHelperDoc.genElem("b");
		body.appendChild(b1);
		body.appendChild(b);
		body.appendChild(b2);

		try
		{
			// An article does not accept a <b> element
			article.appendChild(b);
			fail("Expected an IllegalArgumentException");
		}
		catch (IllegalArgumentException e)
		{
			// Expected
		}

		assertNull(article.getFirstChild());
		assertChildren(body, b1, b, b2);
	}

	// =========================================================================
	// replaceChild(x, x)

	@Test
	public void testReplaceChildWithItselfIsAllowed() throws Exception
	{
		Wom3ElementNode root = elem("root");
		Wom3ElementNode a = elem("a");
		Wom3ElementNode b = elem("b");
		root.appendChild(a);
		root.appendChild(b);

		assertSame(a, root.replaceChild(a, a));

		assertChildren(root, a, b);
	}

	// =========================================================================
	// Non-children

	@Test
	public void testRemoveChildOfNonChildFails() throws Exception
	{
		final Wom3ElementNode root = elem("root");
		final Wom3ElementNode a = elem("a");
		root.appendChild(a);

		final Wom3ElementNode other = elem("other");
		final Wom3ElementNode y = elem("y");
		other.appendChild(y);

		assertDomException(DOMException.NOT_FOUND_ERR, () -> root.removeChild(elem("x")));
		assertDomException(DOMException.NOT_FOUND_ERR, () -> root.removeChild(y));
		assertDomException(DOMException.NOT_FOUND_ERR, () -> root.removeChild(root));

		assertChildren(root, a);
		assertChildren(other, y);
	}

	@Test
	public void testInsertBeforeNonChildReferenceFails() throws Exception
	{
		final Wom3ElementNode root = elem("root");
		final Wom3ElementNode n = elem("n");

		assertDomException(DOMException.NOT_FOUND_ERR, () -> root.insertBefore(n, elem("x")));

		assertNull(root.getFirstChild());
		assertNull(n.getParentNode());
	}

	@Test
	public void testReplaceNonChildFails() throws Exception
	{
		final Wom3ElementNode root = elem("root");
		final Wom3ElementNode n = elem("n");

		assertDomException(DOMException.NOT_FOUND_ERR, () -> root.replaceChild(n, elem("x")));

		assertNull(root.getFirstChild());
		assertNull(n.getParentNode());
	}

	// =========================================================================
	// Text content

	@Test
	public void testSetTextContentNullRemovesChildren() throws Exception
	{
		Wom3ElementNode root = elem("root");
		Wom3ElementNode a = elem("a");
		root.appendChild(a);
		root.appendChild(text("t"));

		root.setTextContent(null);

		assertNull(root.getFirstChild());
		assertFalse(root.hasChildNodes());
		assertNull(a.getParentNode());
		assertEquals("", root.getTextContent());
	}

	@Test
	public void testSetTextContentEmptyRemovesChildren() throws Exception
	{
		Wom3ElementNode root = elem("root");
		root.appendChild(elem("a"));

		root.setTextContent("");

		assertNull(root.getFirstChild());
	}

	@Test
	public void testSetTextContentReplacesChildren() throws Exception
	{
		Wom3ElementNode root = elem("root");
		root.appendChild(elem("a"));

		root.setTextContent("x");

		assertEquals(1, root.getChildNodes().getLength());
		assertEquals(Node.TEXT_NODE, root.getFirstChild().getNodeType());
		assertEquals("x", root.getTextContent());
	}

	@Test
	public void testGetTextContentExcludesComments() throws Exception
	{
		Wom3ElementNode root = elem("root");
		root.appendChild(doc.createComment("c"));
		assertEquals("", root.getTextContent());

		Wom3ElementNode inner = elem("inner");
		inner.appendChild(doc.createComment("d"));
		Wom3ElementNode outer = elem("outer");
		outer.appendChild(inner);
		assertEquals("", outer.getTextContent());

		inner.appendChild(text("b"));
		outer.insertBefore(text("a"), inner);
		outer.appendChild(doc.createComment("x"));
		outer.appendChild(doc.createCDATASection("e"));
		assertEquals("abe", outer.getTextContent());
	}

	@Test
	public void testGetTextContentOfCharacterDataIsItsData() throws Exception
	{
		assertEquals("c", doc.createComment("c").getTextContent());
		assertEquals("t", text("t").getTextContent());
	}

	// =========================================================================
	// normalize()

	@Test
	public void testNormalizeMergesAdjacentTextNodesAndRemovesEmptyOnes() throws Exception
	{
		Wom3ElementNode root = elem("root");
		root.appendChild(text("a"));
		root.appendChild(text("b"));
		root.appendChild(text(""));
		Wom3ElementNode inner = elem("inner");
		inner.appendChild(text("c"));
		inner.appendChild(text("d"));
		inner.appendChild(text(""));
		root.appendChild(inner);
		root.appendChild(text("e"));
		root.appendChild(text(""));
		Wom3ElementNode empty = elem("empty");
		empty.appendChild(text(""));
		root.appendChild(empty);

		root.normalize();

		NodeList children = root.getChildNodes();
		assertEquals(4, children.getLength());
		assertEquals("ab", children.item(0).getNodeValue());
		assertSame(inner, children.item(1));
		assertEquals("e", children.item(2).getNodeValue());
		assertSame(empty, children.item(3));

		assertEquals(1, inner.getChildNodes().getLength());
		assertEquals("cd", inner.getFirstChild().getNodeValue());

		assertNull(empty.getFirstChild());
	}

	@Test
	public void testNormalizeKeepsCdataSectionsAndComments() throws Exception
	{
		Wom3ElementNode root = elem("root");
		root.appendChild(text("a"));
		root.appendChild(doc.createCDATASection("b"));
		root.appendChild(text("c"));
		root.appendChild(doc.createComment("d"));
		root.appendChild(text("e"));

		root.normalize();

		assertEquals(5, root.getChildNodes().getLength());
	}

	@Test
	public void testNormalizeOnTextNodeDoesNothing() throws Exception
	{
		Wom3Node text = text("t");
		text.normalize();
		assertEquals("t", text.getNodeValue());
	}

	// =========================================================================
	// isEqualNode()

	@Test
	public void testIsEqualNode() throws Exception
	{
		Wom3ElementNode a = buildEqualityTree("t", "1");
		Wom3ElementNode b = buildEqualityTree("t", "1");

		assertTrue(a.isEqualNode(a));
		assertTrue(a.isEqualNode(b));
		assertTrue(b.isEqualNode(a));
		assertTrue(a.isEqualNode(a.cloneNode(true)));
		assertFalse(a.isEqualNode(a.cloneNode(false)));
		assertFalse(a.isEqualNode(null));

		assertFalse(a.isEqualNode(buildEqualityTree("u", "1")));
		assertFalse(a.isEqualNode(buildEqualityTree("t", "2")));

		b.appendChild(elem("more"));
		assertFalse(a.isEqualNode(b));

		Wom3ElementNode c = buildEqualityTree("t", "1");
		c.setAttribute("extra", "x");
		assertFalse(a.isEqualNode(c));
		assertFalse(c.isEqualNode(a));

		assertFalse(elem("a").isEqualNode(elem("b")));
		assertFalse(text("a").isEqualNode(doc.createComment("a")));
		assertTrue(text("a").isEqualNode(otherDoc.createTextNode("a")));
	}

	@Test
	public void testIsEqualNodeIgnoresAttributeOrder() throws Exception
	{
		Wom3ElementNode a = elem("e");
		a.setAttribute("x", "1");
		a.setAttribute("y", "2");

		Wom3ElementNode b = elem("e");
		b.setAttribute("y", "2");
		b.setAttribute("x", "1");

		assertTrue(a.isEqualNode(b));
	}

	private Wom3ElementNode buildEqualityTree(String text, String attrValue)
	{
		Wom3ElementNode root = elem("root");
		root.setAttribute("x", attrValue);
		Wom3ElementNode child = elem("child");
		child.appendChild(text(text));
		root.appendChild(child);
		root.appendChild(doc.createComment("c"));
		return root;
	}

	// =========================================================================
	// compareDocumentPosition()

	@Test
	public void testCompareDocumentPositionOfDisconnectedNodes() throws Exception
	{
		assertDisconnected(elem("a"), elem("b"));

		Wom3ElementNode a = elem("a");
		Wom3ElementNode child = elem("child");
		a.appendChild(child);
		assertDisconnected(child, elem("b"));
	}

	@Test
	public void testCompareDocumentPositionOfNodesFromDifferentDocuments() throws Exception
	{
		assertDisconnected(elem("a"), otherDoc.createElement("b"));
		assertDisconnected(doc, otherDoc);
	}

	@Test
	public void testCompareDocumentPositionOfDisconnectedAttributes() throws Exception
	{
		assertDisconnected(doc.createAttribute("x"), doc.createAttribute("y"));
	}

	@Test
	public void testCompareDocumentPositionOfDisconnectedNodesWithEqualIdentityHashCodes() throws Exception
	{
		// Identity hash codes are not unique. Create nodes until two collide.
		Map<Integer, Node> nodes = new HashMap<Integer, Node>();
		Node x = null;
		Node y = null;
		for (int i = 0; (y == null) && (i < 10000000); ++i)
		{
			Node n = doc.createTextNode("t");
			x = nodes.put(System.identityHashCode(n), n);
			if (x != null)
				y = n;
		}
		assertNotNull("No identity hash code collision found", y);

		assertDisconnected(x, y);
	}

	@Test
	public void testCompareDocumentPositionInDetachedTree() throws Exception
	{
		assertTreePositions(elem("root"));
	}

	@Test
	public void testCompareDocumentPositionInDocument() throws Exception
	{
		Wom3ElementNode root = elem("root");
		doc.appendChild(root);
		assertTreePositions(root);

		assertEquals(
				Node.DOCUMENT_POSITION_CONTAINED_BY | Node.DOCUMENT_POSITION_FOLLOWING,
				doc.compareDocumentPosition(root));
	}

	private void assertTreePositions(Wom3ElementNode root)
	{
		Wom3ElementNode a = elem("a");
		Wom3ElementNode b = elem("b");
		root.appendChild(a);
		root.appendChild(b);

		assertEquals(0, a.compareDocumentPosition(a));
		assertEquals(Node.DOCUMENT_POSITION_FOLLOWING, a.compareDocumentPosition(b));
		assertEquals(Node.DOCUMENT_POSITION_PRECEDING, b.compareDocumentPosition(a));
		assertEquals(
				Node.DOCUMENT_POSITION_CONTAINED_BY | Node.DOCUMENT_POSITION_FOLLOWING,
				root.compareDocumentPosition(a));
		assertEquals(
				Node.DOCUMENT_POSITION_CONTAINS | Node.DOCUMENT_POSITION_PRECEDING,
				a.compareDocumentPosition(root));
	}

	private static void assertDisconnected(Node x, Node y)
	{
		short p = x.compareDocumentPosition(y);
		short q = y.compareDocumentPosition(x);

		assertTrue((p & Node.DOCUMENT_POSITION_DISCONNECTED) != 0);
		assertTrue((p & Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC) != 0);
		assertTrue((q & Node.DOCUMENT_POSITION_DISCONNECTED) != 0);
		assertTrue((q & Node.DOCUMENT_POSITION_IMPLEMENTATION_SPECIFIC) != 0);

		boolean pPreceding = (p & Node.DOCUMENT_POSITION_PRECEDING) != 0;
		boolean pFollowing = (p & Node.DOCUMENT_POSITION_FOLLOWING) != 0;
		assertTrue(pPreceding != pFollowing);

		// The order must be consistent
		assertEquals(pPreceding, (q & Node.DOCUMENT_POSITION_FOLLOWING) != 0);
		assertEquals(pFollowing, (q & Node.DOCUMENT_POSITION_PRECEDING) != 0);

		assertEquals(p, x.compareDocumentPosition(y));
	}

	// =========================================================================
	// adoptNode()

	@Test
	public void testAdoptNodeMovesSubtreeWithCommentsAndCdataSections() throws Exception
	{
		Wom3ElementNode holder = otherDoc.createElement("holder");
		Wom3ElementNode p = otherDoc.createElement("p");
		holder.appendChild(p);
		p.setAttribute("x", "1");
		Node comment = p.appendChild(otherDoc.createComment("c"));
		Node cdata = p.appendChild(otherDoc.createCDATASection("d"));
		Node text = p.appendChild(otherDoc.createTextNode("t"));

		assertSame(p, doc.adoptNode(p));

		assertNull(p.getParentNode());
		assertNull(holder.getFirstChild());
		assertSame(doc, p.getOwnerDocument());
		assertSame(doc, p.getAttributeNode("x").getOwnerDocument());
		assertSame(doc, comment.getOwnerDocument());
		assertSame(doc, cdata.getOwnerDocument());
		assertSame(doc, text.getOwnerDocument());
		assertChildren(p, comment, cdata, text);

		Wom3ElementNode root = elem("root");
		root.appendChild(p);
		assertChildren(root, p);
	}

	@Test
	public void testAdoptNodeIsAtomic() throws Exception
	{
		Wom3ElementNode holder = otherDoc.createElement("holder");
		final Wom3ElementNode p = otherDoc.createElement("p");
		holder.appendChild(p);
		Node comment = p.appendChild(otherDoc.createComment("c"));
		Node entityRef = p.appendChild(new EntityRefNode(otherDoc));

		assertDomException(DOMException.NOT_SUPPORTED_ERR, () -> doc.adoptNode(p));

		assertChildren(holder, p);
		assertChildren(p, comment, entityRef);
		assertSame(otherDoc, p.getOwnerDocument());
		assertSame(otherDoc, comment.getOwnerDocument());
		assertSame(otherDoc, entityRef.getOwnerDocument());
	}

	@Test
	public void testAdoptDocumentFails() throws Exception
	{
		assertDomException(DOMException.NOT_SUPPORTED_ERR, () -> doc.adoptNode(otherDoc));
	}

	@Test
	public void testAdoptAttribute() throws Exception
	{
		Wom3ElementNode e = otherDoc.createElement("e");
		e.setAttribute("x", "1");
		Attr attr = e.getAttributeNode("x");

		assertSame(attr, doc.adoptNode(attr));

		assertNull(attr.getOwnerElement());
		assertSame(doc, attr.getOwnerDocument());
		assertFalse(e.hasAttribute("x"));
	}

	@Test
	public void testAdoptDocumentFragment() throws Exception
	{
		Wom3DocumentFragment fragment = otherDoc.createDocumentFragment();
		Node a = fragment.appendChild(otherDoc.createElement("a"));

		assertSame(fragment, doc.adoptNode(fragment));

		assertSame(doc, fragment.getOwnerDocument());
		assertSame(doc, a.getOwnerDocument());
	}

	@Test
	public void testAdoptNodeFromSameDocumentRemovesItFromItsParent() throws Exception
	{
		Wom3ElementNode root = elem("root");
		Wom3ElementNode a = elem("a");
		root.appendChild(a);

		assertSame(a, doc.adoptNode(a));

		assertNull(a.getParentNode());
		assertNull(root.getFirstChild());
	}

	// =========================================================================

	private Wom3ElementNode elem(String name)
	{
		return doc.createElement(name);
	}

	private Wom3Node text(String data)
	{
		return doc.createTextNode(data);
	}

	private static void assertChildren(Node parent, Node... expected)
	{
		NodeList children = parent.getChildNodes();
		assertEquals(expected.length, children.getLength());

		Node n = parent.getFirstChild();
		for (int i = 0; i < expected.length; ++i)
		{
			assertSame(expected[i], children.item(i));
			assertSame(expected[i], n);
			assertSame(parent, n.getParentNode());
			n = n.getNextSibling();
		}
		assertNull(n);

		n = parent.getLastChild();
		for (int i = expected.length - 1; i >= 0; --i)
		{
			assertSame(expected[i], n);
			n = n.getPreviousSibling();
		}
		assertNull(n);
	}

	private static void assertDomException(short code, Runnable action)
	{
		try
		{
			action.run();
			fail("Expected a DOMException with code " + code);
		}
		catch (DOMException e)
		{
			assertEquals(code, e.code);
		}
	}

	private static void assertRejected(Runnable action)
	{
		try
		{
			action.run();
			fail("Expected the operation to be rejected");
		}
		catch (RuntimeException e)
		{
			// Expected
		}
	}

	private Wom3ElementNode wom(String name)
	{
		return (Wom3ElementNode) doc.createElementNS(Wom3Node.WOM_NS_URI, name);
	}

	private Wom3ElementNode category(String name)
	{
		Wom3Category cat = (Wom3Category) wom("category");
		cat.setName(name);
		return (Wom3ElementNode) cat;
	}

	// =========================================================================

	/**
	 * An entity reference node. Adopting those is not supported.
	 */
	private static final class EntityRefNode
			extends
				Backbone
	{
		private static final long serialVersionUID = 1L;

		public EntityRefNode(DocumentImpl owner)
		{
			super(owner);
		}

		@Override
		public String getNodeName()
		{
			return "entity";
		}

		@Override
		public short getNodeType()
		{
			return Node.ENTITY_REFERENCE_NODE;
		}

		@Override
		public Backbone getParentNode()
		{
			return getParentNodeIntern();
		}

		@Override
		public void setTextContent(String textContent) throws DOMException
		{
		}
	}
}
