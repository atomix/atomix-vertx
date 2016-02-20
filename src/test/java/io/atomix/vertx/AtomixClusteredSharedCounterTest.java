/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.vertx;

import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.test.core.ClusteredSharedCounterTest;

/**
 * Clustered shared counter test.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class AtomixClusteredSharedCounterTest extends ClusteredSharedCounterTest {
  private final AtomixVertxTestHelper helper = new AtomixVertxTestHelper();

  public void setUp() throws Exception {
    helper.setUp();
    super.setUp();
  }

  @Override
  protected ClusterManager getClusterManager() {
    return helper.createClusterManager();
  }

  public void tearDown() throws Exception {
    super.tearDown();
    helper.tearDown();
  }

}
