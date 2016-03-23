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

import org.sweble.wom3.Wom3Strike;

public class StrikeImpl
		extends
			BackboneWomElemWithUnivAttrs
		implements
			Wom3Strike
{
	private static final long serialVersionUID = 1L;
	
	// =========================================================================
	
	public StrikeImpl(DocumentImpl owner)
	{
		super(owner);
	}
	
	// =========================================================================
	
	@Override
	public String getWomName()
	{
		return "strike";
	}
}
