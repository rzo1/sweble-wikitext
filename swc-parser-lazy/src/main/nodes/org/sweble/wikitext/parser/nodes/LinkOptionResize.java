/* 
 * This file is auto-generated.
 * DO NOT MODIFY MANUALLY!
 * 
 * Generated by AstNodeGenerator.
 * Last generated: 2012-09-26 11:07:49.
 */

package org.sweble.wikitext.parser.nodes;

import de.fau.cs.osr.ptk.common.ast.AstNodePropertyIterator;

/**
 * <h1>Link Option: Resize</h1> <h2>Grammar</h2>
 * <ul>
 * <li>
 * <p>
 * LinkOptionResize ::= Ws* Digit+ Space* 'px' Ws*
 * </p>
 * </li>
 * <li>
 * <p>
 * LinkOptionResize ::= Ws* Digit+ 'x' Digit* Space* 'px' Ws*
 * </p>
 * </li>
 * <li>
 * <p>
 * LinkOptionResize ::= Ws* 'x' Digit+ Space* 'px' Ws*
 * </p>
 * </li>
 * </ul>
 * <ul>
 * <li>
 * <p>
 * If heightString is null the first production matched.
 * </p>
 * </li>
 * <li>
 * <p>
 * If widthString is null the third production matched.
 * </p>
 * </li>
 * <li>
 * <p>
 * If both are null there is no information which production matched.
 * </p>
 * </li>
 * <li>
 * <p>
 * Otherwise the second production matched.
 * </p>
 * </li>
 * <li>
 * <p>
 * If one of the dimensions is missing the value of the width/height is set to
 * -1.
 * </p>
 * </li>
 * </ul>
 */
public class LinkOptionResize
		extends
			WtLeafNode

{
	private static final long serialVersionUID = 1L;
	
	// =========================================================================
	
	public LinkOptionResize()
	{
		super();
		
	}
	
	public LinkOptionResize(int width, int height)
	{
		super();
		setWidth(width);
		setHeight(height);
		
	}
	
	@Override
	public int getNodeType()
	{
		return org.sweble.wikitext.parser.AstNodeTypes.NT_LINK_OPTION_RESIZE;
	}
	
	// =========================================================================
	// Properties
	
	private int width;
	
	public final int getWidth()
	{
		return this.width;
	}
	
	public final int setWidth(int width)
	{
		int old = this.width;
		this.width = width;
		return old;
	}
	
	private int height;
	
	public final int getHeight()
	{
		return this.height;
	}
	
	public final int setHeight(int height)
	{
		int old = this.height;
		this.height = height;
		return old;
	}
	
	@Override
	public final int getPropertyCount()
	{
		return 2;
	}
	
	@Override
	public final AstNodePropertyIterator propertyIterator()
	{
		return new AstNodePropertyIterator()
		{
			@Override
			protected int getPropertyCount()
			{
				return 2;
			}
			
			@Override
			protected String getName(int index)
			{
				switch (index)
				{
					case 0:
						return "width";
					case 1:
						return "height";
						
					default:
						throw new IndexOutOfBoundsException();
				}
			}
			
			@Override
			protected Object getValue(int index)
			{
				switch (index)
				{
					case 0:
						return LinkOptionResize.this.getWidth();
					case 1:
						return LinkOptionResize.this.getHeight();
						
					default:
						throw new IndexOutOfBoundsException();
				}
			}
			
			@Override
			protected Object setValue(int index, Object value)
			{
				switch (index)
				{
					case 0:
						return LinkOptionResize.this.setWidth((Integer) value);
					case 1:
						return LinkOptionResize.this.setHeight((Integer) value);
						
					default:
						throw new IndexOutOfBoundsException();
				}
			}
		};
	}
	
	// =========================================================================
	// Children
	
	// =========================================================================
	
}