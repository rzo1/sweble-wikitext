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

package org.sweble.wikitext.dumpreader;

/**
 * A {@link DumpReaderListener} that also receives the {@code <logitem>}
 * elements of export version 0.7 and later dumps. Log items are never added
 * to the MediaWiki object's list, whether or not a listener handles them.
 *
 * In export versions 0.5 and 0.6 log items are part of a page and are passed
 * to {@link #handleRevisionOrUploadOrLogitem(Object, Object)} instead.
 */
public interface DumpReaderLogItemListener
		extends
			DumpReaderListener
{
	void handleLogItem(Object mediaWiki, Object logItem);
}
