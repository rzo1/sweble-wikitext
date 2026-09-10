/**
 * Copyright 2011 The Open Source Research Group,
 *                University of Erlangen-Nürnberg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sweble.wikitext.parser.nodes;

import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.sweble.wikitext.parser.WtRtData;
import org.sweble.wikitext.parser.nodes.WtContentNode.WtEmptyContentNode;

import de.fau.cs.osr.ptk.common.ast.AstNodeImpl;
import de.fau.cs.osr.ptk.common.ast.AstNodeList;
import de.fau.cs.osr.ptk.common.ast.AstNodeListImpl;
import xtc.util.Pair;

public interface WtNodeList
		extends
			WtNode,
			AstNodeList<WtNode>
{
	public static final WtEmptyNodeList EMPTY = new WtEmptyNodeList();

	// =========================================================================

	public class WtEmptyNodeList
			extends
				WtEmptyContentNode
			implements
				WtNodeList
	{
		private static final long serialVersionUID = 2465445739660029292L;

		private WtEmptyNodeList()
		{
		}

		@Override
		public int getNodeType()
		{
			return NT_NODE_LIST;
		}

		@Override
		public String getNodeName()
		{
			return WtNodeList.class.getSimpleName();
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
				return true;
			if (other instanceof WtNodeListImpl)
				return AstNodeImpl.equalsNoTypeCheck(this, (WtNodeListImpl) other);
			return super.equals(other);
		}

		protected Object readResolve() throws ObjectStreamException
		{
			return WtNodeList.EMPTY;
		}
	}

	// =========================================================================

	public class WtNodeListImpl
			extends
				AstNodeListImpl<WtNode>
			implements
				WtNodeList
	{
		private static final long serialVersionUID = 6285729315278264384L;

		// =====================================================================

		protected WtNodeListImpl()
		{
		}

		protected WtNodeListImpl(Collection<? extends WtNode> list)
		{
			super(mergeTextNodes(list));
		}

		protected WtNodeListImpl(Pair<? extends WtNode> list)
		{
			super(mergeTextNodes(list));
		}

		protected WtNodeListImpl(WtNode child)
		{
			super(child);
		}

		protected WtNodeListImpl(Object... content)
		{
			for (Object o : content)
			{
				if (o == null)
				{
					continue;
				}
				else if (o instanceof WtNode)
				{
					add((WtNode) o);
				}
				else if (o instanceof Pair)
				{
					@SuppressWarnings("unchecked")
					Pair<? extends WtNode> cast = (Pair<? extends WtNode>) o;
					addAll(cast);
				}
				else if (o instanceof Collection)
				{
					@SuppressWarnings("unchecked")
					Collection<? extends WtNode> cast = (Collection<? extends WtNode>) o;
					addAll(cast);
				}
				else
				{
					throw new IllegalArgumentException("Can't add object of type: " + o.getClass().getName());
				}
			}
		}

		// =====================================================================

		/**
		 * Merges runs of adjacent text nodes like adding them one by one
		 * would, but builds the text of each run only once. Adding them one
		 * by one copies the text merged so far for every node, which takes
		 * quadratic time for long runs, e.g. for text interrupted by many
		 * "&lt;".
		 */
		private static List<WtNode> mergeTextNodes(Iterable<? extends WtNode> nodes)
		{
			List<WtNode> result = new ArrayList<WtNode>();
			List<WtText> run = new ArrayList<WtText>();
			for (WtNode n : nodes)
			{
				if (n == null)
					continue;

				if (n.getNodeType() == NT_TEXT && (n instanceof WtText))
				{
					WtText text = (WtText) n;
					if (text.getContent().isEmpty())
						continue;

					if (!text.hasAttributes())
					{
						run.add(text);
						continue;
					}
				}

				flushTextRun(run, result);
				result.add(n);
			}
			flushTextRun(run, result);
			return result;
		}

		private static void flushTextRun(List<WtText> run, List<WtNode> result)
		{
			if (run.size() == 1)
			{
				result.add(run.get(0));
			}
			else if (run.size() > 1)
			{
				StringBuilder sb = new StringBuilder();
				for (WtText text : run)
					sb.append(text.getContent());

				try
				{
					WtText merged = (WtText) run.get(0).clone();
					merged.setContent(sb.toString());
					result.add(merged);
				}
				catch (CloneNotSupportedException e)
				{
					// Leave the merging to the list
					result.addAll(run);
				}
			}
			run.clear();
		}

		// =====================================================================

		@Override
		public String getNodeName()
		{
			return (getClass() == WtNodeListImpl.class) ?
					WtNodeList.class.getSimpleName() :
					super.getNodeName();
		}

		// =====================================================================

		@Override
		public void setRtd(WtRtData rtd)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void setRtd(Object... glue)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void setRtd(String... glue)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public WtRtData getRtd()
		{
			return null;
		}

		@Override
		public void clearRtd()
		{
		}

		@Override
		public void suppressRtd()
		{
			throw new UnsupportedOperationException();
		}

		// =====================================================================

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
				return true;
			if (other instanceof WtEmptyNodeList)
				return AstNodeImpl.equalsNoTypeCheck(this, (WtEmptyNodeList) other);
			return super.equals(other);
		}
	}
}
