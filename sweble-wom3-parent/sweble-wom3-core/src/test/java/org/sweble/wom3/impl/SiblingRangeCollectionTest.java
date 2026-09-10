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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.sweble.wom3.Wom3Node;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@RunWith(Parameterized.class)
public class SiblingRangeCollectionTest
{
	@Parameters
	public static Collection<Object[]> data()
	{
		return Arrays.asList(new Object[][] {
				{ false, false },
				{ false, true },
				{ true, false },
				{ true, true },
		});
	}

	// =========================================================================

	private final SiblingRangeCollection<Container, ChildNode> c;

	private final LinkedList<ChildNode> nodes;

	private final Container container;

	private final BoundaryChild pred;

	private final BoundaryChild succ;

	private DocumentImpl doc;

	// =========================================================================

	public SiblingRangeCollectionTest(boolean hasPred, boolean hasSucc)
	{
		DomImplementationImpl domImpl = new DomImplementationImpl();
		this.doc = domImpl.createDocument(Wom3Node.WOM_NS_URI, "article", null);

		this.pred = hasPred ? new BoundaryChild(doc) : null;
		this.succ = hasSucc ? new BoundaryChild(doc) : null;
		this.container = new Container(doc, pred, succ);
		this.c = container.getC();
		this.nodes = new LinkedList<ChildNode>();
		for (int i = 0; i < 10; ++i)
			nodes.add(gen(i));
	}

	// =========================================================================

	/**
	 * Every change to the collection must also be visible through the child
	 * list of the container.
	 */
	@After
	public void assertContainerChildListMatchesCollection() throws Exception
	{
		List<Backbone> expected = new ArrayList<Backbone>();
		if (pred != null)
			expected.add(pred);
		expected.addAll(c);
		if (succ != null)
			expected.add(succ);

		NodeList childNodes = container.getChildNodes();
		assertEquals(expected.size(), childNodes.getLength());
		for (int i = 0; i < expected.size(); ++i)
			assertSame(expected.get(i), childNodes.item(i));

		List<Backbone> backwards = new ArrayList<Backbone>();
		for (Backbone n = container.getLastChild(); n != null; n = n.getPreviousSibling())
			backwards.add(0, n);
		assertEquals(expected.size(), backwards.size());
		for (int i = 0; i < expected.size(); ++i)
			assertSame(expected.get(i), backwards.get(i));

		if (expected.isEmpty())
		{
			assertNull(container.getFirstChild());
			assertNull(container.getLastChild());
		}
		else
		{
			assertSame(expected.get(0), container.getFirstChild());
			assertSame(expected.get(expected.size() - 1), container.getLastChild());
		}

		for (ChildNode e : c)
			assertSame(container, e.getParentNode());
	}

	// =========================================================================

	@Test
	public void testCollectionChangesAreVisibleInContainerChildList() throws Exception
	{
		int bounds = (pred != null ? 1 : 0) + (succ != null ? 1 : 0);

		ChildNode a = gen(0);
		ChildNode b = gen(1);
		ChildNode x = gen(2);
		c.add(a);
		c.addFirst(b);
		c.add(x);

		assertSame(b, (pred == null) ? container.getFirstChild() : pred.getNextSibling());
		assertSame(x, (succ == null) ? container.getLastChild() : succ.getPreviousSibling());
		assertEquals(3 + bounds, container.getChildNodes().getLength());

		assertTrue(c.remove(a));
		assertNull(a.getParentNode());
		assertEquals(2 + bounds, container.getChildNodes().getLength());

		c.removeFirst();
		c.removeLast();
		assertTrue(c.isEmpty());
		assertEquals(bounds, container.getChildNodes().getLength());
	}

	@Test
	public void testCollectionSeesChildrenChangedThroughContainer() throws Exception
	{
		ChildNode a = gen(0);
		if (succ != null)
			container.insertBefore(a, succ);
		else
			container.appendChild(a);

		assertEquals(1, c.size());
		assertSame(a, c.getFirst());
		assertSame(a, c.getLast());

		container.removeChild(a);
		assertTrue(c.isEmpty());
		assertNull(c.peekFirst());
		assertNull(c.peekLast());
	}

	// =========================================================================

	@Test
	public void testEmptyContainerHasSize0() throws Exception
	{
		assertEquals(0, c.size());
	}

	@Test
	public void testCanGetListIteratorOfEmptyContainer() throws Exception
	{
		c.listIterator();
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testListIteratorAtNonZeroIndexForEmptyContainerFails() throws Exception
	{
		c.listIterator(1);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testListIteratorWithNegIndexFails() throws Exception
	{
		c.listIterator(-1);
	}

	@Test(expected = NoSuchElementException.class)
	public void testGetFirstFailsForEmptyContainer() throws Exception
	{
		c.getFirst();
	}

	@Test(expected = NoSuchElementException.class)
	public void testGetLastFailsForEmptyContainer() throws Exception
	{
		c.getLast();
	}

	@Test
	public void testPeekWorksWithEmptyContainer() throws Exception
	{
		assertNull(c.peek());
		assertNull(c.peekFirst());
		assertNull(c.peekLast());
	}

	@Test
	public void testPollWorksWithEmptyContainer() throws Exception
	{
		assertNull(c.poll());
		assertNull(c.pollFirst());
		assertNull(c.pollLast());
	}

	@Test
	public void testHasNextAndHasPreviousReturnFalseForEmptyContainer() throws Exception
	{
		assertFalse(c.listIterator().hasNext());
		assertFalse(c.listIterator().hasPrevious());
	}

	@Test
	public void testNextIndexReturnsZeroForEmptyContainer() throws Exception
	{
		assertEquals(0, c.listIterator().nextIndex());
		assertEquals(-1, c.listIterator().previousIndex());
	}

	@Test
	public void testCanAddToEmptyContainer() throws Exception
	{
		ChildNode e = gen(0);
		c.add(e);
		assertFalse(c.isEmpty());
		assertEquals(1, c.size());
		assertEquals(e, c.getFirst());
		assertEquals(e, c.getLast());
	}

	@Test
	public void testCanAddTwoToEmptyContainer() throws Exception
	{
		ChildNode e0 = gen(0);
		c.add(e0);
		ChildNode e1 = gen(1);
		c.add(e1);

		assertFalse(c.isEmpty());
		assertEquals(2, c.size());
		assertEquals(e0, c.getFirst());
		assertEquals(e1, c.getLast());
	}

	@Test
	public void testAddingAndRetrievingItemsByIndex() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		assertEquals(nodes.size(), c.size());
		for (int i = 0; i < nodes.size(); ++i)
			assertEquals(nodes.get(i), c.get(i));
	}

	@Test
	public void testPeekAndPollOnFilledContainer() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		assertEquals(nodes.peek(), c.peek());
		assertEquals(nodes.peekFirst(), c.peekFirst());
		assertEquals(nodes.peekLast(), c.peekLast());
		assertEquals(nodes.poll(), c.poll());
		assertEquals(nodes.pollFirst(), c.pollFirst());
		assertEquals(nodes.pollLast(), c.pollLast());
		assertEquals(nodes.size(), c.size());
		Iterator<ChildNode> i = nodes.iterator();
		for (ChildNode e : c)
			assertEquals(i.next(), e);
	}

	@Test
	public void testRemoveWithIteratorInComplexPattern() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		// Remove all even
		ListIterator<ChildNode> i = c.listIterator();
		while (i.hasNext())
		{
			i.next();
			i.remove();
			if (i.hasNext())
				i.next();
		}
		assertEquals(nodes.size() / 2, c.size());
		// Remove remaining
		while (i.hasPrevious())
		{
			i.previous();
			i.remove();
		}
	}

	@Test
	public void testRemoveWithIteratorInComplexPatternReverse() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		// Remove all even
		ListIterator<ChildNode> i = c.listIterator(c.size());
		while (i.hasPrevious())
		{
			i.previous();
			i.remove();
			if (i.hasPrevious())
				i.previous();
		}
		assertEquals(nodes.size() / 2, c.size());
		// Remove remaining
		while (i.hasNext())
		{
			i.next();
			i.remove();
		}
	}

	@Test
	public void testRemoveAll() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		while (!c.isEmpty())
			c.removeFirst();
		assertEquals(0, c.size());
	}

	@Test
	public void testRemoveAllReverse() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		while (!c.isEmpty())
			c.removeLast();
		assertEquals(0, c.size());
	}

	@Test
	public void testFillContainerFromFront() throws Exception
	{
		for (ChildNode e : nodes)
			c.addFirst(e);
		Iterator<ChildNode> i = c.iterator();
		Iterator<ChildNode> j = nodes.descendingIterator();
		while (j.hasNext())
			assertEquals(j.next(), i.next());
		assertEquals(j.hasNext(), i.hasNext());
	}

	@Test
	public void testRemoveFirstOccurrence() throws Exception
	{
		ChildNode a = gen(42);
		ChildNode b = gen(42);
		c.add(gen(0));
		c.add(a);
		c.add(gen(1));
		c.add(gen(2));
		c.add(b);
		c.add(gen(3));

		assertEquals(42, c.get(1).getFakeId());
		assertTrue(c.removeFirstOccurrence(gen(42)));
		assertFalse(a.isLinked());
		assertTrue(b.isLinked());
		assertEquals(1, c.get(1).getFakeId());
		assertEquals(42, c.get(3).getFakeId());

		assertTrue(c.removeFirstOccurrence(gen(42)));
		assertFalse(b.isLinked());
		for (int i = 0; i < 4; ++i)
			assertEquals(i, c.get(i).getFakeId());

		assertFalse(c.removeFirstOccurrence(gen(42)));
	}

	@Test
	public void testRemoveLastOccurrence() throws Exception
	{
		ChildNode a = gen(42);
		ChildNode b = gen(42);
		c.add(gen(0));
		c.add(a);
		c.add(gen(1));
		c.add(gen(2));
		c.add(b);
		c.add(gen(3));

		assertEquals(42, c.get(4).getFakeId());
		assertTrue(c.removeLastOccurrence(gen(42)));
		assertTrue(a.isLinked());
		assertFalse(b.isLinked());
		assertEquals(3, c.get(4).getFakeId());
		assertEquals(42, c.get(1).getFakeId());

		assertTrue(c.removeLastOccurrence(gen(42)));
		assertFalse(b.isLinked());
		for (int i = 0; i < 4; ++i)
			assertEquals(i, c.get(i).getFakeId());

		assertFalse(c.removeLastOccurrence(gen(42)));
	}

	@Test
	public void testReplacingAllNodesWithHigherIdNodes() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		ListIterator<ChildNode> i = c.listIterator();
		for (int j = 0; j < nodes.size(); ++j)
		{
			ChildNode e = i.next();
			assertEquals(j, e.getFakeId());
			i.set(gen(e.getFakeId() + nodes.size()));
		}
		for (int j = 0; j < 10; ++j)
			assertEquals(j + nodes.size(), c.get(j).getFakeId());
	}

	@Test
	public void testReplacingAllNodesWithHigherIdNodesReverse() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		ListIterator<ChildNode> i = c.listIterator(c.size());
		for (int j = 0; j < nodes.size(); ++j)
		{
			ChildNode e = i.previous();
			assertEquals(nodes.size() - j - 1, e.getFakeId());
			i.set(gen(e.getFakeId() + nodes.size()));
		}
		for (int j = 0; j < 10; ++j)
			assertEquals(j + nodes.size(), c.get(j).getFakeId());
	}

	@Test
	public void testInsertNegativeIdsBeforeAndAfterAllNodes() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		ListIterator<ChildNode> i = c.listIterator();
		int j = -1;
		i.add(gen(j--));
		while (i.hasNext())
		{
			i.next();
			i.add(gen(j--));
		}
		for (int k = 0; k <= nodes.size(); k++)
			assertEquals(-k - 1, c.get(k * 2).getFakeId());
		for (int k = 0; k < nodes.size(); k++)
			assertEquals(k, c.get(k * 2 + 1).getFakeId());
	}

	// =========================================================================

	@Test
	public void testReversedViewOfEmptyContainer() throws Exception
	{
		SiblingRangeCollection<Container, ChildNode>.ReversedView r = c.reversed();
		assertTrue(r.isEmpty());
		assertNull(r.peekFirst());
		assertNull(r.pollLast());
		assertFalse(r.iterator().hasNext());
	}

	@Test
	public void testReversedViewHasReverseOrder() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		List<ChildNode> expected = new ArrayList<ChildNode>(nodes);
		Collections.reverse(expected);

		SiblingRangeCollection<Container, ChildNode>.ReversedView r = c.reversed();
		assertEquals(expected.size(), r.size());
		for (int i = 0; i < expected.size(); ++i)
			assertEquals(expected.get(i), r.get(i));
		assertEquals(expected, new ArrayList<ChildNode>(r));
		assertEquals(expected, r);

		Iterator<ChildNode> i = r.descendingIterator();
		for (ChildNode e : nodes)
			assertEquals(e, i.next());
		assertFalse(i.hasNext());

		assertEquals(c.getLast(), r.getFirst());
		assertEquals(c.getFirst(), r.getLast());
		assertEquals(c.peekLast(), r.peek());
		assertEquals(c.peekFirst(), r.peekLast());
	}

	@Test
	public void testReversedOfReversedIsOriginal() throws Exception
	{
		assertSame(c, c.reversed().reversed());
	}

	@Test
	public void testReversedViewDequeOperationsWriteThrough() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		SiblingRangeCollection<Container, ChildNode>.ReversedView r = c.reversed();

		r.addFirst(gen(100));
		assertEquals(100, c.getLast().getFakeId());
		r.addLast(gen(101));
		assertEquals(101, c.getFirst().getFakeId());
		r.push(gen(102));
		assertEquals(102, c.getLast().getFakeId());
		r.add(gen(103));
		assertEquals(103, c.getFirst().getFakeId());
		assertEquals(nodes.size() + 4, c.size());

		assertEquals(102, r.pop().getFakeId());
		assertEquals(100, r.removeFirst().getFakeId());
		assertEquals(103, r.removeLast().getFakeId());
		assertEquals(101, r.pollLast().getFakeId());
		assertEquals(9, r.poll().getFakeId());
		assertEquals(nodes.size() - 1, c.size());
		assertEquals(8, c.getLast().getFakeId());
		assertEquals(0, c.getFirst().getFakeId());
	}

	@Test
	public void testReversedViewIndexOperationsWriteThrough() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		SiblingRangeCollection<Container, ChildNode>.ReversedView r = c.reversed();

		r.add(0, gen(100));
		assertEquals(100, c.getLast().getFakeId());
		r.add(r.size(), gen(101));
		assertEquals(101, c.getFirst().getFakeId());
		r.add(2, gen(102));
		assertEquals(102, c.get(c.size() - 3).getFakeId());

		assertEquals(100, r.set(0, gen(103)).getFakeId());
		assertEquals(103, c.getLast().getFakeId());

		assertEquals(103, r.remove(0).getFakeId());
		assertEquals(102, r.remove(1).getFakeId());
		assertEquals(101, r.remove(r.size() - 1).getFakeId());
		for (int i = 0; i < nodes.size(); ++i)
			assertEquals(i, c.get(i).getFakeId());
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testReversedViewGetOutOfBoundsFails() throws Exception
	{
		c.add(gen(0));
		c.reversed().get(1);
	}

	@Test
	public void testReversedViewIteratorRemoveWritesThrough() throws Exception
	{
		for (ChildNode e : nodes)
			c.add(e);
		// Remove every other node, starting with the last one
		Iterator<ChildNode> i = c.reversed().iterator();
		while (i.hasNext())
		{
			i.next();
			i.remove();
			if (i.hasNext())
				i.next();
		}
		assertEquals(nodes.size() / 2, c.size());
		for (int k = 0; k < c.size(); ++k)
			assertEquals(k * 2, c.get(k).getFakeId());
	}

	@Test
	public void testReversedViewRemoveOccurrence() throws Exception
	{
		ChildNode a = gen(42);
		ChildNode b = gen(42);
		c.add(gen(0));
		c.add(a);
		c.add(gen(1));
		c.add(b);
		SiblingRangeCollection<Container, ChildNode>.ReversedView r = c.reversed();

		// First occurrence in the view is the last one in the container
		assertTrue(r.removeFirstOccurrence(gen(42)));
		assertTrue(a.isLinked());
		assertFalse(b.isLinked());

		assertTrue(r.removeLastOccurrence(gen(42)));
		assertFalse(a.isLinked());
		assertFalse(r.contains(gen(42)));
		assertEquals(2, c.size());
	}

	// =========================================================================

	private ChildNode gen(int id)
	{
		return new ChildNode(doc, id);
	}

	// =========================================================================

	protected static final class ChildNode
			extends
				Backbone
	{
		private static final long serialVersionUID = 1L;

		private final int id;

		// ---------------------------------------------------------------------

		public ChildNode(DocumentImpl owner, int id)
		{
			super(owner);
			this.id = id;
		}

		// ---------------------------------------------------------------------

		public int getFakeId()
		{
			return id;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ChildNode other = (ChildNode) obj;
			if (id != other.id)
				return false;
			return true;
		}

		@Override
		public String getNodeName()
		{
			return ChildNode.class.getSimpleName();
		}

		@Override
		public String getLocalName()
		{
			return getNodeName();
		}

		@Override
		public short getNodeType()
		{
			return Node.ELEMENT_NODE;
		}

		@Override
		public void setTextContent(String textContent) throws DOMException
		{
		}

		@Override
		public Backbone getParentNode()
		{
			return getParentNodeIntern();
		}
	}

	// =========================================================================

	protected static final class BoundaryChild
			extends
				Backbone
	{
		private static final long serialVersionUID = 1L;

		// ---------------------------------------------------------------------

		public BoundaryChild(DocumentImpl owner)
		{
			super(owner);
		}

		// ---------------------------------------------------------------------

		@Override
		public String getNodeName()
		{
			return ChildNode.class.getSimpleName();
		}

		@Override
		public String getLocalName()
		{
			return getNodeName();
		}

		@Override
		public short getNodeType()
		{
			return Node.ELEMENT_NODE;
		}

		@Override
		public void setTextContent(String textContent) throws DOMException
		{
		}

		@Override
		public Backbone getParentNode()
		{
			return getParentNodeIntern();
		}
	}

	// =========================================================================

	protected static final class Container
			extends
				BackboneElement
			implements
				SiblingCollectionBounds
	{
		private static final long serialVersionUID = 1L;

		private final SiblingRangeCollection<Container, ChildNode> c;

		private final BoundaryChild pred;

		private final BoundaryChild succ;

		// ---------------------------------------------------------------------

		public Container(
				DocumentImpl owner,
				BoundaryChild pred,
				BoundaryChild succ)
		{
			super(owner);

			c = new SiblingRangeCollection<Container, ChildNode>(this, new SiblingCollectionBounds()
			{
				@Override
				public Backbone getSucc()
				{
					return Container.this.succ;
				}

				@Override
				public Backbone getPred()
				{
					return Container.this.pred;
				}
			});

			this.pred = pred;
			if (pred != null)
				appendChild(pred);

			this.succ = succ;
			if (succ != null)
				appendChild(succ);
		}

		// ---------------------------------------------------------------------

		@Override
		protected void allowsInsertion(Backbone prev, Backbone child)
		{
		}

		@Override
		protected void allowsRemoval(Backbone child)
		{
		}

		@Override
		protected void allowsReplacement(Backbone oldChild, Backbone newChild)
		{
		}

		// ---------------------------------------------------------------------

		public SiblingRangeCollection<Container, ChildNode> getC()
		{
			return c;
		}

		@Override
		public String getNodeName()
		{
			return Container.class.getSimpleName();
		}

		@Override
		public String getLocalName()
		{
			return getNodeName();
		}

		@Override
		public Backbone getPred()
		{
			return pred;
		}

		@Override
		public Backbone getSucc()
		{
			return succ;
		}

		@Override
		protected AttributeDescriptor getAttributeDescriptor(
				String namespaceUri,
				String localName,
				String qualifiedName)
		{
			return null;
		}
	}
}
