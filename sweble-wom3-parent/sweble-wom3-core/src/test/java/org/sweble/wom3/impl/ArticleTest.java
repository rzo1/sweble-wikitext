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

import java.util.Iterator;

import org.junit.Ignore;
import org.junit.Test;
import org.sweble.wom3.Wom3Category;
import org.sweble.wom3.Wom3Node;
import org.sweble.wom3.Wom3Redirect;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ArticleTest
{
	private ArticleImpl n;

	public ArticleTest()
	{
		n = (ArticleImpl) TestHelperDoc.genElem("article");
		n.setNamespace("User");
		n.setTitle("Page");
		n.setPath("John");
	}

	@Test
	public void testNodeHasCorrectName() throws Exception
	{
		assertEquals("article", n.getNodeName());
	}

	@Test
	public void testIsDocument() throws Exception
	{
		assertEquals(Node.ELEMENT_NODE, n.getNodeType());
	}

	@Test
	public void testHasCorrectVersion() throws Exception
	{
		assertEquals(Wom3Node.VERSION, n.getAttribute("version"));
		assertEquals(Wom3Node.VERSION, n.getVersion());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testCannotAlterVersion() throws Exception
	{
		n.setAttribute("version", "3.1415");
	}

	public void testCanSetVersionToCorrectVersion() throws Exception
	{
		n.setAttribute("version", Wom3Node.VERSION);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testCannotRemoveVersion() throws Exception
	{
		n.removeAttribute("version");
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testCannotRemoveTitle() throws Exception
	{
		n.removeAttribute("title");
	}

	public void testCanRemoveNamespaceAndPath() throws Exception
	{
		assertNotNull(n.getPath());
		assertNotNull(n.getNamespace());
		n.setPath(null);
		n.setNamespace(null);
		assertNull(n.getPath());
		assertNull(n.getNamespace());
	}

	@Test
	public void testNameIsComposedCorrectly() throws Exception
	{
		assertEquals("Page", n.getTitle());
		assertEquals("John", n.getPath());
		assertEquals("User", n.getNamespace());
		assertEquals("User:John/Page", n.getName());
	}

	@Test
	public void testCanCompensateSlashAtEndOfPath() throws Exception
	{
		n.setPath("John/Subpage/");
		assertEquals("Page", n.getTitle());
		assertEquals("John/Subpage", n.getPath());
		assertEquals("User", n.getNamespace());
		assertEquals("User:John/Subpage/Page", n.getName());
	}

	@Test
	public void testNameCompositeWithoutNamespace() throws Exception
	{
		assertEquals("Page", n.getTitle());
		assertEquals("John", n.getPath());
		n.setNamespace(null);
		assertEquals("John/Page", n.getName());
	}

	@Test
	public void testNameCompositeWithoutPath() throws Exception
	{
		assertEquals("Page", n.getTitle());
		n.setPath(null);
		assertEquals("User", n.getNamespace());
		assertEquals("User:Page", n.getName());
	}

	@Test
	public void testIsNotRedirectInitially() throws Exception
	{
		assertFalse(n.isRedirect());
	}

	@Test
	public void testIsRedirectAfterSettingRedirectNode() throws Exception
	{
		RedirectImpl redirect = (RedirectImpl) TestHelperDoc.genElem("redirect");
		n.setRedirect(redirect);
		assertTrue(n.isRedirect());
		// To cover more instructions...
		redirect = (RedirectImpl) TestHelperDoc.genElem("redirect");
		n.setRedirect(redirect);
		assertTrue(n.isRedirect());
	}

	@Test
	public void testIsRedirectAfterSettingRedirectNodeWithCategoriesAttached() throws Exception
	{
		addSomeCats();

		RedirectImpl redirect = (RedirectImpl) TestHelperDoc.genElem("redirect");
		n.setRedirect(redirect);
		assertTrue(n.isRedirect());
		// To cover more instructions...
		n.setRedirect(redirect);
		assertTrue(n.isRedirect());
		assertTrue(redirect == n.getRedirect());
	}

	@Test
	public void testResetRedirect() throws Exception
	{
		RedirectImpl redirect1 = (RedirectImpl) TestHelperDoc.genElem("redirect");
		n.setRedirect(redirect1);
		assertTrue(n.isRedirect());

		RedirectImpl redirect2 = (RedirectImpl) TestHelperDoc.genElem("redirect");
		n.setRedirect(redirect2);
		assertTrue(n.isRedirect());
		assertTrue(redirect2 == n.getRedirect());
	}

	@Test
	public void testRemoveRedirect() throws Exception
	{
		RedirectImpl redirect1 = (RedirectImpl) TestHelperDoc.genElem("redirect");
		n.setRedirect(redirect1);
		assertTrue(n.isRedirect());

		n.setRedirect(null);
		assertFalse(n.isRedirect());
		assertNull(n.getRedirect());
	}

	@Test
	@Ignore
	public void testRedirectIsBeforeAllOtherChildren() throws Exception
	{
		assertTrue(n.getBody() == n.getFirstChild());
		assertTrue(n.getBody() == n.getLastChild());

		Wom3Category cat = n.addCategory("cat");
		assertTrue(cat == n.getFirstChild());
		assertTrue(n.getBody() == n.getLastChild());

		RedirectImpl redirect = (RedirectImpl) TestHelperDoc.genElem("redirect");
		n.setRedirect(redirect);
		assertTrue(redirect == n.getFirstChild());
		assertTrue(n.getBody() == n.getLastChild());
	}

	@Test
	public void testChangeCatNameToSameNameDoesNotThrow() throws Exception
	{
		addSomeCats();
		Iterator<Wom3Category> i = n.getCategories().iterator();
		i.next();
		Wom3Category c = i.next();
		c.setName(c.getName());
	}

	@Test(expected = IllegalStateException.class)
	public void testCatNameCollisionWhenSettingNameThrows() throws Exception
	{
		addSomeCats();
		Iterator<Wom3Category> i = n.getCategories().iterator();
		Wom3Category c0 = i.next();
		Wom3Category c1 = i.next();
		c1.setName(c0.getName());
	}

	@Test
	public void testRenamingCategoryWorks() throws Exception
	{
		addSomeCats();
		Iterator<Wom3Category> i = n.getCategories().iterator();
		i.next();
		Wom3Category c1 = i.next();
		c1.setName("foo");
		assertTrue(n.hasCategory("cat1"));
		assertTrue(n.hasCategory("cat3"));
		assertTrue(n.hasCategory("foo"));
		assertFalse(n.hasCategory("cat2"));
	}

	@Test
	public void testAddingCategoryTwiceDoesNotThrow() throws Exception
	{
		n.addCategory("cat");
		n.addCategory("cat");
		assertTrue(n.hasCategory("cat"));
	}

	@Test
	public void testRemovingCategoriesWorks() throws Exception
	{
		addSomeCats();
		assertTrue(n.hasCategory("cat1"));
		assertTrue(n.hasCategory("cat2"));
		assertTrue(n.hasCategory("cat3"));

		n.removeCategory("cat2");
		assertTrue(n.hasCategory("cat1"));
		assertFalse(n.hasCategory("cat2"));
		assertTrue(n.hasCategory("cat3"));

		n.removeCategory("cat1");
		assertFalse(n.hasCategory("cat1"));
		assertFalse(n.hasCategory("cat2"));
		assertTrue(n.hasCategory("cat3"));

		n.removeCategory("cat3");
		assertFalse(n.hasCategory("cat1"));
		assertFalse(n.hasCategory("cat2"));
		assertFalse(n.hasCategory("cat3"));
	}

	@Test(expected = NullPointerException.class)
	public void testRemovingNullCategoryThrows() throws Exception
	{
		n.removeCategory(null);
	}

	@Test
	public void testRemovingNonExistingCategoryDoesNotThrow() throws Exception
	{
		n.removeCategory("idontexist");
	}

	@Test
	public void testResetBodyWithAndWithoutOtherChildNodesPresent() throws Exception
	{
		BodyImpl body = (BodyImpl) TestHelperDoc.genElem("body");
		n.setBody(body);
		assertTrue(body == n.getBody());

		n.setRedirect((Wom3Redirect) TestHelperDoc.genElem("redirect"));

		body = (BodyImpl) TestHelperDoc.genElem("body");
		n.setBody(body);
		assertTrue(body == n.getBody());

		addSomeCats();

		body = (BodyImpl) TestHelperDoc.genElem("body");
		n.setBody(body);
		assertTrue(body == n.getBody());

		n.setBody(body);
		assertTrue(body == n.getBody());
	}

	@Test(expected = NullPointerException.class)
	public void testSettingNullBodyThrows() throws Exception
	{
		n.setBody(null);
	}

	@Test
	public void testCategoriesAreReachableThroughDom() throws Exception
	{
		BodyImpl body = (BodyImpl) TestHelperDoc.genElem("body");
		n.setBody(body);

		Wom3Category cat1 = n.addCategory("cat1");
		Wom3Category cat2 = n.addCategory("cat2");

		assertSame(cat1, n.getFirstChild());
		assertSame(cat2, cat1.getNextSibling());
		assertSame(body, cat2.getNextSibling());
		assertSame(body, n.getLastChild());
		assertSame(n, cat1.getParentNode());

		NodeList children = n.getChildNodes();
		assertEquals(3, children.getLength());
		assertSame(cat1, children.item(0));
		assertSame(cat2, children.item(1));
		assertSame(body, children.item(2));

		assertEquals(2, n.getCategories().size());
	}

	@Test
	public void testCategoriesWithoutBodyAreReachableThroughDom() throws Exception
	{
		Wom3Category cat1 = n.addCategory("cat1");
		Wom3Category cat2 = n.addCategory("cat2");

		assertSame(cat1, n.getFirstChild());
		assertSame(cat2, n.getLastChild());
		assertEquals(2, n.getChildNodes().getLength());
	}

	@Test
	public void testCategoriesAreInsertedBetweenRedirectAndBody() throws Exception
	{
		BodyImpl body = (BodyImpl) TestHelperDoc.genElem("body");
		n.setBody(body);
		RedirectImpl redirect = (RedirectImpl) TestHelperDoc.genElem("redirect");
		n.setRedirect(redirect);

		Wom3Category cat1 = n.addCategory("cat1");
		Wom3Category cat2 = n.addCategory("cat2");

		assertSame(redirect, n.getFirstChild());
		assertSame(cat1, redirect.getNextSibling());
		assertSame(cat2, cat1.getNextSibling());
		assertSame(body, cat2.getNextSibling());
		assertSame(body, n.getLastChild());
	}

	@Test
	public void testRemovingCategoriesUpdatesDom() throws Exception
	{
		BodyImpl body = (BodyImpl) TestHelperDoc.genElem("body");
		n.setBody(body);
		n.addCategory("cat1");
		Wom3Category cat2 = n.addCategory("cat2");

		n.removeCategory("cat1");
		assertSame(cat2, n.getFirstChild());

		n.removeCategory("cat2");
		assertSame(body, n.getFirstChild());
		assertEquals(1, n.getChildNodes().getLength());
		assertTrue(n.getCategories().isEmpty());
	}

	@Test
	public void testCategoriesInsertedThroughDomAreListed() throws Exception
	{
		BodyImpl body = (BodyImpl) TestHelperDoc.genElem("body");
		n.setBody(body);

		Wom3Category cat = (Wom3Category) TestHelperDoc.genElem("category");
		cat.setName("x");
		n.insertBefore(cat, body);

		assertTrue(n.hasCategory("x"));
		assertEquals(1, n.getCategories().size());

		n.addCategory("y");
		assertSame(cat, n.getFirstChild());
		assertEquals(2, n.getCategories().size());
	}

	@Test
	public void testDeepCloneHasOwnCategories() throws Exception
	{
		BodyImpl body = (BodyImpl) TestHelperDoc.genElem("body");
		n.setBody(body);
		addSomeCats();

		ArticleImpl clone = (ArticleImpl) n.cloneNode(true);
		assertEquals(3, clone.getCategories().size());
		assertTrue(clone.hasCategory("cat2"));
		assertSame(clone, clone.getBody().getParentNode());
		assertSame(clone.getBody(), clone.getLastChild());

		clone.addCategory("cat4");
		assertTrue(clone.hasCategory("cat4"));
		assertFalse(n.hasCategory("cat4"));
		assertEquals(4, clone.getCategories().size());
		assertEquals(3, n.getCategories().size());

		clone.removeCategory("cat1");
		assertTrue(n.hasCategory("cat1"));
	}

	@Test
	public void testShallowCloneHasNoChildren() throws Exception
	{
		BodyImpl body = (BodyImpl) TestHelperDoc.genElem("body");
		n.setBody(body);
		n.setRedirect((Wom3Redirect) TestHelperDoc.genElem("redirect"));
		addSomeCats();

		ArticleImpl clone = (ArticleImpl) n.cloneNode(false);
		assertNull(clone.getFirstChild());
		assertNull(clone.getBody());
		assertNull(clone.getRedirect());
		assertFalse(clone.isRedirect());
		assertTrue(clone.getCategories().isEmpty());
		assertEquals(n.getTitle(), clone.getTitle());

		clone.addCategory("cat");
		assertSame(clone, ((Node) clone.getCategories().iterator().next()).getParentNode());
		assertEquals(3, n.getCategories().size());
	}

	// =========================================================================

	private void addSomeCats()
	{
		n.addCategory("cat1");
		n.addCategory("cat2");
		n.addCategory("cat3");
	}
}
