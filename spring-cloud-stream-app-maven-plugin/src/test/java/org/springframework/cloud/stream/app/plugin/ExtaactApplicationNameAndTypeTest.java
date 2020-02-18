/*
 * Copyright 2020-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.app.plugin;

import javafx.util.Pair;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Assert;
import org.junit.Test;

import org.springframework.cloud.stream.app.plugin.generator.AppDefinition;

/**
 * @author Christian Tzolov
 */
public class ExtaactApplicationNameAndTypeTest<mojo> {

	SpringCloudStreamAppGeneratorMojo mojo = new SpringCloudStreamAppGeneratorMojo();

	@Test
	public void testExtraction() throws MojoFailureException {
		// source
		Pair<String, AppDefinition.AppType> pair = mojo.extractApplicationNameAndType("very-complex-name1-source");
		Assert.assertEquals(pair.getKey(), "very-complex-name1");
		Assert.assertEquals(pair.getValue(), AppDefinition.AppType.source);

		// sink
		pair = mojo.extractApplicationNameAndType(" very-complex-name2-sink  ");
		Assert.assertEquals(pair.getKey(), "very-complex-name2");
		Assert.assertEquals(pair.getValue(), AppDefinition.AppType.sink);

		// processor
		pair = mojo.extractApplicationNameAndType("very-complex-name3-processor  ");
		Assert.assertEquals(pair.getKey(), "very-complex-name3");
		Assert.assertEquals(pair.getValue(), AppDefinition.AppType.processor);
	}

	@Test(expected = MojoFailureException.class)
	public void testMissingTypeSeparator() throws MojoFailureException {
		mojo.extractApplicationNameAndType("name:source");
	}

	@Test(expected = MojoFailureException.class)
	public void testIncorrectType() throws MojoFailureException {
		mojo.extractApplicationNameAndType("name-supplier");
	}
}
