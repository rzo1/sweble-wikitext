/**
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

package org.sweble.wikitext.engine.config;

import java.util.Iterator;

/**
 * Helpers for compareTo() implementations that are consistent with equals().
 */
final class ConfigComparisons
{
	private ConfigComparisons()
	{
	}

	/**
	 * Compares two strings, null is smaller than any string.
	 */
	static int compare(String a, String b)
	{
		if (a == null)
			return (b == null) ? 0 : -1;
		if (b == null)
			return 1;
		return a.compareTo(b);
	}

	/**
	 * Compares the strings of two collections in iteration order, a
	 * collection that is a prefix of the other one is smaller. Null is smaller
	 * than any collection.
	 */
	static int compare(Iterable<String> a, Iterable<String> b)
	{
		if (a == null)
			return (b == null) ? 0 : -1;
		if (b == null)
			return 1;

		Iterator<String> i = a.iterator();
		Iterator<String> j = b.iterator();
		while (i.hasNext() && j.hasNext())
		{
			int result = compare(i.next(), j.next());
			if (result != 0)
				return result;
		}
		if (i.hasNext())
			return 1;
		return j.hasNext() ? -1 : 0;
	}
}
