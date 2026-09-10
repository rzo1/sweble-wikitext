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
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import org.sweble.wom3.Wom3Article;
import org.sweble.wom3.Wom3Body;
import org.sweble.wom3.Wom3Category;
import org.sweble.wom3.Wom3Node;
import org.sweble.wom3.Wom3Redirect;

public class ArticleImpl
		extends
			BackboneContainer
		implements
			Wom3Article
{
	private static final long serialVersionUID = 1L;

	private static final ChildDescriptor[] BODY_DESCRIPTOR = {
			childDesc("redirect"),
			childDesc("category", ChildDescriptor.MULTIPLE),
			childDesc("body", ChildDescriptor.REQUIRED) };

	private RedirectImpl redirect = null;

	private SiblingRangeCollection<ArticleImpl, Backbone> categories;

	private BodyImpl body;

	// =========================================================================

	public ArticleImpl(DocumentImpl owner)
	{
		super(owner);

		setAttributeDirectNoChecks("version", Wom3Node.VERSION);

		categories = new SiblingRangeCollection<ArticleImpl, Backbone>(
				this, new SiblingCollectionsBoundIml());
	}

	private final class SiblingCollectionsBoundIml
			implements
				SiblingCollectionBounds,
				Serializable
	{
		private static final long serialVersionUID = 1L;

		@Override
		public Backbone getPred()
		{
			return redirect;
		}

		@Override
		public Backbone getSucc()
		{
			return body;
		}
	}

	// =========================================================================

	@Override
	public String getWomName()
	{
		return "article";
	}

	// =========================================================================

	@Override
	public String getName()
	{
		String namespace = getNamespace();
		String path = getPath();
		String title = getTitle();

		String name = "";
		if (namespace != null)
		{
			name += namespace;
			name += ':';
		}
		if (path != null)
		{
			name += path;
			name += '/';
		}
		name += title;

		return name;
	}

	@Override
	public String getVersion()
	{
		return Wom3Node.VERSION;
	}

	@Override
	public String getTitle()
	{
		return getStringAttr("title");
	}

	@Override
	public String setTitle(String title) throws IllegalArgumentException, NullPointerException
	{
		return setAttributeDirect(ATTR_DESC_TITLE, "title", title);
	}

	@Override
	public String getNamespace()
	{
		return getStringAttr("namespace");
	}

	@Override
	public String setNamespace(String namespace)
	{
		return setAttributeDirect(ATTR_DESC_NAMESPACE, "namespace", namespace);
	}

	@Override
	public String getPath()
	{
		return getStringAttr("path");
	}

	@Override
	public String setPath(String path)
	{
		return setAttributeDirect(ATTR_DESC_PATH, "path", path);
	}

	// =========================================================================

	@Override
	public boolean isRedirect()
	{
		return redirect != null;
	}

	@Override
	public Wom3Redirect getRedirect()
	{
		return redirect;
	}

	@Override
	public Wom3Redirect setRedirect(Wom3Redirect redirect)
	{
		return (Wom3Redirect) replaceOrInsertBeforeOrAppend(
				this.redirect, getFirstChild(), redirect, false);
	}

	// ----------------------------------------

	/**
	 * Returns an unmodifiable view of the category nodes. Other nodes between
	 * the redirect and the body (e.g. comments) are not part of the view.
	 */
	@Override
	public Collection<Wom3Category> getCategories()
	{
		return new AbstractCollection<Wom3Category>()
		{
			@Override
			public Iterator<Wom3Category> iterator()
			{
				return new CategoryIterator();
			}

			@Override
			public int size()
			{
				int count = 0;
				for (Iterator<Wom3Category> i = iterator(); i.hasNext(); i.next())
					++count;
				return count;
			}
		};
	}

	@Override
	public boolean hasCategory(String name) throws NullPointerException
	{
		return findCategory(name) != null;
	}

	@Override
	public Wom3Category removeCategory(String name) throws NullPointerException
	{
		assertWritableOnDocument();

		if (name == null)
			throw new NullPointerException();
		CategoryImpl cat = findCategory(name);
		if (cat != null)
			removeChild(cat);
		return cat;
	}

	@Override
	public Wom3Category addCategory(String name) throws NullPointerException
	{
		assertWritableOnDocument();

		CategoryImpl last = null;
		for (Wom3Category c : getCategories())
		{
			if (c.getName().equals(name))
				return c;
			last = (CategoryImpl) c;
		}

		CategoryImpl cat = (CategoryImpl)
				getOwnerDocument().createElementNS(Wom3Node.WOM_NS_URI, "category");
		cat.setName(name);

		// Insert the new category after the last category. Other nodes (e.g.
		// comments) may lie between the categories and the body.
		if (last == null)
			categories.addFirst(cat);
		else if (last.getNextSibling() == null)
			appendChild(cat);
		else
			insertBefore(cat, last.getNextSibling());
		return cat;
	}

	private CategoryImpl findCategory(String name)
	{
		for (Wom3Category cat : getCategories())
		{
			if (cat.getName().equals(name))
				return (CategoryImpl) cat;
		}
		return null;
	}

	/**
	 * Iterates over the category nodes between the redirect and the body and
	 * skips all other nodes.
	 */
	private final class CategoryIterator
			implements
				Iterator<Wom3Category>
	{
		private final Iterator<Backbone> i = categories.iterator();

		private CategoryImpl next = advance();

		private CategoryImpl advance()
		{
			while (i.hasNext())
			{
				Backbone n = i.next();
				if (n instanceof CategoryImpl)
					return (CategoryImpl) n;
			}
			return null;
		}

		@Override
		public boolean hasNext()
		{
			return next != null;
		}

		@Override
		public Wom3Category next()
		{
			if (next == null)
				throw new NoSuchElementException();
			CategoryImpl cat = next;
			next = advance();
			return cat;
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException();
		}
	}

	// ----------------------------------------

	@Override
	public Wom3Body getBody()
	{
		return body;
	}

	@Override
	public Wom3Body setBody(Wom3Body body) throws NullPointerException
	{
		return (Wom3Body) replaceOrAppend(this.body, body, true);
	}

	// =========================================================================

	@Override
	public Wom3Node cloneNode(boolean deep)
	{
		ArticleImpl newNode = (ArticleImpl) super.cloneNode(deep);

		// The copied fields still refer to our children. Look up the children
		// of the clone instead.
		newNode.redirect = null;
		newNode.body = null;
		for (Backbone child = newNode.getFirstChild(); child != null; child = child.getNextSibling())
			newNode.childInserted(child.getPreviousSibling(), child);

		newNode.categories = new SiblingRangeCollection<ArticleImpl, Backbone>(
				newNode, newNode.new SiblingCollectionsBoundIml());

		return newNode;
	}

	// =========================================================================

	@Override
	protected void allowsInsertion(Backbone prev, Backbone child)
	{
		checkInsertion(prev, child, BODY_DESCRIPTOR);
	}

	@Override
	protected void allowsRemoval(Backbone child)
	{
		checkRemoval(child, BODY_DESCRIPTOR);
	}

	@Override
	protected void allowsReplacement(Backbone oldChild, Backbone newChild)
	{
		checkReplacement(oldChild, newChild, BODY_DESCRIPTOR);
	}

	@Override
	protected void childInserted(Backbone prev, Backbone added)
	{
		if (added instanceof Wom3Redirect)
			redirect = (RedirectImpl) added;
		else if (added instanceof Wom3Body)
			body = (BodyImpl) added;
	}

	@Override
	protected void childRemoved(Backbone prev, Backbone removed)
	{
		if (removed == redirect)
			redirect = null;
		else if (removed == body)
			body = null;
	}

	// =========================================================================

	protected void validateCategoryNameChange(
			CategoryImpl catImpl,
			String newName)
	{
		CategoryImpl cat = findCategory(newName);
		if ((cat != null) && (cat != catImpl))
			throw new IllegalStateException(
					"Renaming the attribute leads to name collision in parent node!");
	}

	// =========================================================================

	protected static final AttrDescVersion ATTR_DESC_VERSION = new AttrDescVersion();

	protected static final AttrDescTitle ATTR_DESC_TITLE = new AttrDescTitle();

	protected static final AttrDescNamespace ATTR_DESC_NAMESPACE = new AttrDescNamespace();

	protected static final AttrDescPath ATTR_DESC_PATH = new AttrDescPath();

	private static final Map<String, AttributeDescriptor> NAME_MAP = new HashMap<String, AttributeDescriptor>();

	static
	{
		NAME_MAP.put("version", ATTR_DESC_VERSION);
		NAME_MAP.put("namespace", ATTR_DESC_NAMESPACE);
		NAME_MAP.put("path", ATTR_DESC_PATH);
		NAME_MAP.put("title", ATTR_DESC_TITLE);
	}

	// =========================================================================

	@Override
	protected AttributeDescriptor getAttributeDescriptor(
			String namespaceUri,
			String localName,
			String qualifiedName)
	{
		return getAttrDescStrict(namespaceUri, localName, qualifiedName, NAME_MAP);
	}

	public static final class AttrDescVersion
			extends
				AttributeDescriptor
	{
		@Override
		public int getFlags()
		{
			return makeFlags(
					false /* removable */,
					false /* readOnly */,
					false /* customAction */,
					Normalization.NON_CDATA);
		}

		@Override
		public boolean verifyAndConvert(
				Backbone parent,
				NativeAndStringValuePair verified)
		{
			super.verifyAndConvert(parent, verified);
			if (!Wom3Node.VERSION.equals(verified.strValue))
				throw new UnsupportedOperationException(
						"Cannot alter read-only attribute `version'");
			return true;
		}
	}

	public static final class AttrDescTitle
			extends
				AttributeDescriptor
	{
		@Override
		public int getFlags()
		{
			return makeFlags(
					false /* removable */,
					false /* readOnly */,
					false /* customAction */,
					Normalization.NON_CDATA);
		}

		@Override
		public boolean verifyAndConvert(
				Backbone parent,
				NativeAndStringValuePair verified)
		{
			super.verifyAndConvert(parent, verified);
			Toolbox.checkValidTitle(verified.strValue);
			return true;
		}
	}

	public static final class AttrDescNamespace
			extends
				AttributeDescriptor
	{
		@Override
		public int getFlags()
		{
			return makeFlags(
					true /* removable */,
					false /* readOnly */,
					false /* customAction */,
					Normalization.NON_CDATA);
		}

		@Override
		public boolean verifyAndConvert(
				Backbone parent,
				NativeAndStringValuePair verified)
		{
			super.verifyAndConvert(parent, verified);
			verified.value =
					verified.strValue =
							Toolbox.checkValidNamespace(verified.strValue);
			return (verified.strValue != null);
		}
	}

	public static final class AttrDescPath
			extends
				AttributeDescriptor
	{
		@Override
		public int getFlags()
		{
			return makeFlags(
					true /* removable */,
					false /* readOnly */,
					false /* customAction */,
					Normalization.NON_CDATA);
		}

		@Override
		public boolean verifyAndConvert(
				Backbone parent,
				NativeAndStringValuePair verified)
		{
			super.verifyAndConvert(parent, verified);
			verified.value =
					verified.strValue =
							Toolbox.checkValidPath(verified.strValue);
			return (verified.strValue != null);
		}
	}
}
