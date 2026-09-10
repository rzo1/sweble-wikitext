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

package org.sweble.wikitext.engine;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sweble.wikitext.engine.nodes.EngLogProcessingPass;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtText;

/**
 * Helpers for inspecting engine results and logs.
 */
final class EngineCoverageTestUtils
{
	private EngineCoverageTestUtils()
	{
	}

	// =========================================================================

	/**
	 * Returns all nodes of the given type in the subtree (including the root).
	 */
	static <T> List<T> findAll(WtNode root, Class<T> type)
	{
		List<T> result = new ArrayList<T>();
		collect(root, type, result);
		return result;
	}

	static <T> T single(WtNode root, Class<T> type)
	{
		List<T> found = findAll(root, type);
		assertEquals("Number of " + type.getSimpleName() + " nodes", 1, found.size());
		return found.get(0);
	}

	/**
	 * Concatenates the content of all text nodes in the subtree.
	 */
	static String textOf(WtNode root)
	{
		StringBuilder sb = new StringBuilder();
		for (WtText text : findAll(root, WtText.class))
			sb.append(text.getContent());
		return sb.toString();
	}

	/**
	 * Asserts the types of the passes directly contained in the log.
	 */
	static void assertPasses(EngLogProcessingPass log, Class<?>... expected)
	{
		List<Class<?>> actual = new ArrayList<Class<?>>();
		for (WtNode pass : log)
			actual.add(pass.getClass());
		assertEquals(Arrays.asList(expected), actual);
	}

	// =========================================================================

	private static <T> void collect(WtNode node, Class<T> type, List<T> result)
	{
		if (type.isInstance(node))
			result.add(type.cast(node));
		for (WtNode child : node)
			collect(child, type, result);
	}
}
