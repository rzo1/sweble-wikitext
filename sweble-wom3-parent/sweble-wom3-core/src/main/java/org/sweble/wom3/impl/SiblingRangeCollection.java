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

import java.io.Serializable;
import java.util.AbstractList;
import java.util.AbstractSequentialList;
import java.util.Deque;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * A view of the children of a container that lie between two bounding
 * children of the container. If a bound is {@code null}, the range extends to
 * the first or last child of the container respectively.
 *
 * All modifications are performed through the DOM operations of the container
 * which keeps the child list of the container consistent. Changes made to the
 * child list of the container are reflected in this collection.
 */
public class SiblingRangeCollection<U extends BackboneWithChildren, T extends Backbone>
		extends
			AbstractSequentialList<T>
		implements
			Serializable,
			Deque<T>
{
	private static final long serialVersionUID = 1L;

	private final U container;

	private final SiblingCollectionBounds bounds;

	// =========================================================================

	public SiblingRangeCollection(U container, SiblingCollectionBounds bounds)
	{
		this.container = container;
		this.bounds = bounds;
	}

	@Override
	public ListIterator<T> listIterator(int index)
	{
		return new ListIter(index);
	}

	@Override
	public int size()
	{
		int count = 0;
		for (T i = first(); i != null; i = advance(i))
			++count;
		return count;
	}

	// =========================================================================

	public boolean add(T e)
	{
		addLast(e);
		return true;
	}

	// =========================================================================

	@Override
	public T peek()
	{
		return peekFirst();
	}

	@Override
	public T peekFirst()
	{
		return first();
	}

	@Override
	public T peekLast()
	{
		return last();
	}

	@Override
	public T getFirst()
	{
		T first = first();
		if (first == null)
			throw new NoSuchElementException();
		return first;
	}

	@Override
	public T getLast()
	{
		T last = last();
		if (last == null)
			throw new NoSuchElementException();
		return last;
	}

	@Override
	public T element()
	{
		return getFirst();
	}

	@Override
	public void addFirst(T e)
	{
		checkBeforeAdd(e);

		Backbone pred = bounds.getPred();
		insert(e, (pred != null) ? pred.getNextSibling() : container.getFirstChild());
	}

	@Override
	public void addLast(T e)
	{
		checkBeforeAdd(e);

		insert(e, bounds.getSucc());
	}

	@Override
	public boolean offer(T e)
	{
		return add(e);
	}

	@Override
	public boolean offerFirst(T e)
	{
		addFirst(e);
		return true;
	}

	@Override
	public boolean offerLast(T e)
	{
		addLast(e);
		return true;
	}

	@Override
	public T removeFirst()
	{
		T removed = first();
		checkBeforeRemove(removed);
		container.removeChild(removed);
		return removed;
	}

	@Override
	public T removeLast()
	{
		T removed = last();
		checkBeforeRemove(removed);
		container.removeChild(removed);
		return removed;
	}

	@Override
	public T poll()
	{
		return pollFirst();
	}

	@Override
	public T pollFirst()
	{
		if (first() != null)
			return removeFirst();
		return null;
	}

	@Override
	public T pollLast()
	{
		if (last() != null)
			return removeLast();
		return null;
	}

	@Override
	public void push(T e)
	{
		addFirst(e);
	}

	@Override
	public T pop()
	{
		return removeFirst();
	}

	@Override
	public T remove()
	{
		return removeFirst();
	}

	@Override
	public boolean removeFirstOccurrence(Object o)
	{
		return remove(o);
	}

	@Override
	public boolean removeLastOccurrence(Object o)
	{
		if (o == null)
			throw new NullPointerException();
		Iterator<T> i = descendingIterator();
		while (i.hasNext())
		{
			if (o.equals(i.next()))
			{
				i.remove();
				return true;
			}
		}
		return false;
	}

	@Override
	public Iterator<T> descendingIterator()
	{
		return new DescIter();
	}

	/**
	 * Returns a reverse-ordered view of this collection. Changes to the view
	 * are reflected in this collection and vice versa.
	 *
	 * Since Java 21 both {@link java.util.List} and {@link Deque} declare
	 * {@code reversed()} with unrelated return types, so this class has to
	 * declare a {@code reversed()} that returns both a list and a deque.
	 */
	public ReversedView reversed()
	{
		return new ReversedView();
	}

	// =========================================================================

	/**
	 * The first node after the predecessor bound or {@code null} if the range
	 * is empty.
	 */
	private T first()
	{
		Backbone pred = bounds.getPred();
		Backbone first = (pred != null) ? pred.getNextSibling() : container.getFirstChild();
		return (first == bounds.getSucc()) ? null : cast(first);
	}

	/**
	 * The last node in front of the successor bound or {@code null} if the
	 * range is empty.
	 */
	private T last()
	{
		Backbone succ = bounds.getSucc();
		Backbone last = (succ != null) ? succ.getPreviousSibling() : container.getLastChild();
		return (last == bounds.getPred()) ? null : cast(last);
	}

	/**
	 * Inserts the given node in front of {@code before} or appends it to the
	 * container if {@code before} is {@code null}.
	 */
	private void insert(T e, Backbone before)
	{
		if (before == null)
			container.appendChild(e);
		else
			container.insertBefore(e, before);
	}

	private void checkBeforeAdd(T e)
	{
		if (e == null)
			throw new IllegalArgumentException("Argument `e' is null.");

		if (e.isLinked())
			throw new IllegalStateException(
					"Given node `e' is still child of another WOM node.");
	}

	private void checkBeforeRemove(T e)
	{
		if (e == null)
			throw new NoSuchElementException();
	}

	private T retreat(T i)
	{
		Backbone p = i.getPreviousSibling();
		return (p == bounds.getPred()) ? null : cast(p);
	}

	private T advance(T i)
	{
		Backbone n = i.getNextSibling();
		return (n == bounds.getSucc()) ? null : cast(n);
	}

	@SuppressWarnings("unchecked")
	private T cast(Backbone n)
	{
		return (T) n;
	}

	// =========================================================================

	private final class ListIter
			implements
				ListIterator<T>
	{
		private T lastReturned = null;

		private T next;

		private int nextIndex;

		public ListIter(int index)
		{
			if (index < 0)
				throw new IndexOutOfBoundsException();

			T i = first();
			for (int count = 0;; ++count)
			{
				if (count == index)
					break;
				if (i == null)
					throw new IndexOutOfBoundsException();
				i = advance(i);
			}

			next = i;
			nextIndex = index;
		}

		@Override
		public boolean hasNext()
		{
			return next != null;
		}

		@Override
		public T next()
		{
			if (!hasNext())
				throw new NoSuchElementException();

			lastReturned = next;
			next = advance(next);
			++nextIndex;
			return lastReturned;
		}

		@Override
		public boolean hasPrevious()
		{
			return nextIndex > 0;
		}

		@Override
		public T previous()
		{
			if (!hasPrevious())
				throw new NoSuchElementException();

			lastReturned = (next != null) ? retreat(next) : last();
			next = lastReturned;
			--nextIndex;
			return lastReturned;
		}

		@Override
		public int nextIndex()
		{
			return nextIndex;
		}

		@Override
		public int previousIndex()
		{
			return nextIndex - 1;
		}

		@Override
		public void remove()
		{
			T removed = lastReturned;
			if (removed == null)
				throw new IllegalStateException();

			T lastNext = advance(removed);

			container.removeChild(removed);

			// Fix iterator
			if (next == removed)
				next = lastNext;
			else
				nextIndex--;
			lastReturned = null;
		}

		@Override
		public void set(T e)
		{
			T replaced = lastReturned;
			if (replaced == null)
				throw new IllegalStateException();

			checkBeforeAdd(e);

			container.replaceChild(e, replaced);

			// Fix iterator
			if (next == replaced)
				next = e;
			lastReturned = e;
		}

		@Override
		public void add(T e)
		{
			lastReturned = null;
			if (next == null)
			{
				addLast(e);
			}
			else
			{
				checkBeforeAdd(e);
				container.insertBefore(e, next);
			}
			nextIndex++;
		}
	}

	// =========================================================================

	private class DescIter
			implements
				Iterator<T>
	{
		private final ListIter i = new ListIter(size());

		public boolean hasNext()
		{
			return i.hasPrevious();
		}

		public T next()
		{
			return i.previous();
		}

		public void remove()
		{
			i.remove();
		}
	}

	// =========================================================================

	/**
	 * Reverse-ordered view of the enclosing collection.
	 */
	public final class ReversedView
			extends
				AbstractList<T>
			implements
				Deque<T>
	{
		public SiblingRangeCollection<U, T> reversed()
		{
			return SiblingRangeCollection.this;
		}

		@Override
		public int size()
		{
			return SiblingRangeCollection.this.size();
		}

		@Override
		public T get(int index)
		{
			return SiblingRangeCollection.this.get(reverseIndex(index));
		}

		@Override
		public T set(int index, T e)
		{
			return SiblingRangeCollection.this.set(reverseIndex(index), e);
		}

		@Override
		public void add(int index, T e)
		{
			int size = size();
			if (index < 0 || index > size)
				throw new IndexOutOfBoundsException();
			SiblingRangeCollection.this.add(size - index, e);
		}

		@Override
		public T remove(int index)
		{
			return SiblingRangeCollection.this.remove(reverseIndex(index));
		}

		@Override
		public Iterator<T> iterator()
		{
			return SiblingRangeCollection.this.descendingIterator();
		}

		@Override
		public Iterator<T> descendingIterator()
		{
			return SiblingRangeCollection.this.iterator();
		}

		private int reverseIndex(int index)
		{
			int size = size();
			if (index < 0 || index >= size)
				throw new IndexOutOfBoundsException();
			return size - 1 - index;
		}

		// =====================================================================

		@Override
		public boolean add(T e)
		{
			addLast(e);
			return true;
		}

		@Override
		public void addFirst(T e)
		{
			SiblingRangeCollection.this.addLast(e);
		}

		@Override
		public void addLast(T e)
		{
			SiblingRangeCollection.this.addFirst(e);
		}

		@Override
		public boolean offer(T e)
		{
			return add(e);
		}

		@Override
		public boolean offerFirst(T e)
		{
			addFirst(e);
			return true;
		}

		@Override
		public boolean offerLast(T e)
		{
			addLast(e);
			return true;
		}

		@Override
		public T removeFirst()
		{
			return SiblingRangeCollection.this.removeLast();
		}

		@Override
		public T removeLast()
		{
			return SiblingRangeCollection.this.removeFirst();
		}

		@Override
		public T pollFirst()
		{
			return SiblingRangeCollection.this.pollLast();
		}

		@Override
		public T pollLast()
		{
			return SiblingRangeCollection.this.pollFirst();
		}

		@Override
		public T getFirst()
		{
			return SiblingRangeCollection.this.getLast();
		}

		@Override
		public T getLast()
		{
			return SiblingRangeCollection.this.getFirst();
		}

		@Override
		public T peekFirst()
		{
			return SiblingRangeCollection.this.peekLast();
		}

		@Override
		public T peekLast()
		{
			return SiblingRangeCollection.this.peekFirst();
		}

		@Override
		public boolean removeFirstOccurrence(Object o)
		{
			return SiblingRangeCollection.this.removeLastOccurrence(o);
		}

		@Override
		public boolean removeLastOccurrence(Object o)
		{
			return SiblingRangeCollection.this.removeFirstOccurrence(o);
		}

		@Override
		public T remove()
		{
			return removeFirst();
		}

		@Override
		public T poll()
		{
			return pollFirst();
		}

		@Override
		public T element()
		{
			return getFirst();
		}

		@Override
		public T peek()
		{
			return peekFirst();
		}

		@Override
		public void push(T e)
		{
			addFirst(e);
		}

		@Override
		public T pop()
		{
			return removeFirst();
		}
	}
}
