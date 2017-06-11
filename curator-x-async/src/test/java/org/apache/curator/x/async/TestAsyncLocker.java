/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.curator.x.async;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.RetryOneTime;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestAsyncLocker extends CompletableBaseClassForTests
{
    @Test
    public void testBasic()
    {
        try ( CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1)) )
        {
            client.start();

            InterProcessMutex lock = new InterProcessMutex(client, "/one/two");
            complete(AsyncLocker.lockAsync(lock), (state, e) -> {
                Assert.assertNull(e);
                Assert.assertTrue(state.hasTheLock());
                state.release();
            });
        }
    }

    @Test
    public void testContention() throws Exception
    {
        try ( CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1)) )
        {
            client.start();

            InterProcessMutex lock1 = new InterProcessMutex(client, "/one/two");
            InterProcessMutex lock2 = new InterProcessMutex(client, "/one/two");
            CountDownLatch latch = new CountDownLatch(1);
            AsyncLocker.lockAsync(lock1).thenAccept(state -> {
                if ( state.hasTheLock() )
                {
                    latch.countDown();  // don't release the lock
                }
            });
            Assert.assertTrue(timing.awaitLatch(latch));

            CountDownLatch latch2 = new CountDownLatch(1);
            AsyncLocker.lockAsync(lock2, timing.forSleepingABit().milliseconds(), TimeUnit.MILLISECONDS).thenAccept(state -> {
                if ( !state.hasTheLock() )
                {
                    latch2.countDown();  // lock should still be held
                }
            });
            Assert.assertTrue(timing.awaitLatch(latch2));
        }
    }
}