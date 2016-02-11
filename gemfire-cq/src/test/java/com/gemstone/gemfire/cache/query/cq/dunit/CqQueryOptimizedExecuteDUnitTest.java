/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gemstone.gemfire.cache.query.cq.dunit;

import com.gemstone.gemfire.cache.CacheException;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.query.data.Portfolio;
import com.gemstone.gemfire.cache.query.internal.cq.CqServiceImpl;
import com.gemstone.gemfire.cache.query.internal.cq.CqServiceProvider;
import com.gemstone.gemfire.cache30.CacheSerializableRunnable;
import com.gemstone.gemfire.internal.AvailablePortHelper;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
import com.gemstone.gemfire.test.dunit.Host;
import com.gemstone.gemfire.test.dunit.Invoke;
import com.gemstone.gemfire.test.dunit.LogWriterUtils;
import com.gemstone.gemfire.test.dunit.NetworkUtils;
import com.gemstone.gemfire.test.dunit.SerializableRunnable;
import com.gemstone.gemfire.test.dunit.VM;
import com.gemstone.gemfire.test.dunit.Wait;

/**
 * Test class for testing {@link CqServiceImpl#EXECUTE_QUERY_DURING_INIT} flag
 *
 */
public class CqQueryOptimizedExecuteDUnitTest extends CqQueryDUnitTest{

  public CqQueryOptimizedExecuteDUnitTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    super.setUp();
    Invoke.invokeInEveryVM(new SerializableRunnable("getSystem") {
      public void run() {
        CqServiceImpl.EXECUTE_QUERY_DURING_INIT = false;
      }
    });
  }
  
  @Override
  protected final void preTearDownCacheTestCase() throws Exception {
    Invoke.invokeInEveryVM(new SerializableRunnable("getSystem") {
      public void run() {
        CqServiceImpl.EXECUTE_QUERY_DURING_INIT = true;
        CqServiceProvider.MAINTAIN_KEYS = true;
      }
    });
  }
  
  public void testCqExecuteWithoutQueryExecution() throws Exception {
    final Host host = Host.getHost(0);
    final VM server = host.getVM(0);
    final VM client = host.getVM(1);
    final int numOfEntries = 10;
    final String cqName = "testCqExecuteWithoutQueryExecution_1";

    createServer(server);
    // Create values.
    createValues(server, regions[0], numOfEntries);

    final int thePort = server.invoke(() -> CqQueryDUnitTest.getCacheServerPort());
    final String host0 = NetworkUtils.getServerHostName(server.getHost());

    // Create client.
    createClient(client, thePort, host0);

     /* Create CQs. */
    createCQ(client, cqName, cqs[0]); 
    
    validateCQCount(client, 1);
    
    executeCQ(client, cqName, false, null);

    server.invoke(new CacheSerializableRunnable("execute cq") {
      public void run2() throws CacheException {
        assertFalse("CqServiceImpl.EXECUTE_QUERY_DURING_INIT flag should be false ", CqServiceImpl.EXECUTE_QUERY_DURING_INIT);
        int numOfQueryExecutions = (Integer) ((GemFireCacheImpl)getCache()).getCachePerfStats().getStats().get("queryExecutions");
        assertEquals("Number of query executions for cq.execute should be 0 ", 0, numOfQueryExecutions);
      }
    });
    
    // Create more values.
    server.invoke(new CacheSerializableRunnable("Create values") {
      public void run2() throws CacheException {
        Region region1 = getRootRegion().getSubregion(regions[0]);
        for (int i = numOfEntries+1; i <= numOfEntries*2; i++) {
          region1.put(KEY+i, new Portfolio(i));
        }
        LogWriterUtils.getLogWriter().info("### Number of Entries in Region :" + region1.keys().size());
      }
    });
    
 
    waitForCreated(client, cqName, KEY+numOfEntries*2);

    validateCQ(client, cqName,
        /* resultSize: */ noTest,
        /* creates: */ numOfEntries,
        /* updates: */ 0,
        /* deletes; */ 0,
        /* queryInserts: */ numOfEntries,
        /* queryUpdates: */ 0,
        /* queryDeletes: */ 0,
        /* totalEvents: */ numOfEntries);
    
    // Update values.
    createValues(server, regions[0], 5);
    createValues(server, regions[0], 10);
    
    waitForUpdated(client, cqName, KEY+numOfEntries);
    
    
    // validate Update events.
    validateCQ(client, cqName,
        /* resultSize: */ noTest,
        /* creates: */ numOfEntries,
        /* updates: */ 15,
        /* deletes; */ 0,
        /* queryInserts: */ numOfEntries,
        /* queryUpdates: */ 15,
        /* queryDeletes: */ 0,
        /* totalEvents: */ numOfEntries + 15);
    
    // Validate delete events.
    deleteValues(server, regions[0], 5);
    waitForDestroyed(client, cqName, KEY+5);
    
    validateCQ(client, cqName,
        /* resultSize: */ noTest,
        /* creates: */ numOfEntries,
        /* updates: */ 15,
        /* deletes; */5,
        /* queryInserts: */ numOfEntries,
        /* queryUpdates: */ 15,
        /* queryDeletes: */ 5,
        /* totalEvents: */ numOfEntries + 15 + 5);

    closeClient(client);   
    closeServer(server);    
  }

  public void testCqExecuteWithoutQueryExecutionAndNoRSCaching() throws Exception {
    final Host host = Host.getHost(0);
    final VM server = host.getVM(0);
    final VM client = host.getVM(1);
    final int numOfEntries = 10;
    final String cqName = "testCqExecuteWithoutQueryExecution_1";

    server.invoke(new CacheSerializableRunnable("execute cq") {
      public void run2() throws CacheException {
        CqServiceProvider.MAINTAIN_KEYS = false;
      }
    });
    
    createServer(server);
    // Create values.
    createValues(server, regions[0], numOfEntries);

    final int thePort = server.invoke(() -> CqQueryDUnitTest.getCacheServerPort());
    final String host0 = NetworkUtils.getServerHostName(server.getHost());

    // Create client.
    createClient(client, thePort, host0);

     /* Create CQs. */
    createCQ(client, cqName, cqs[0]); 
    
    validateCQCount(client, 1);
    
    executeCQ(client, cqName, false, null);

    server.invoke(new CacheSerializableRunnable("execute cq") {
      public void run2() throws CacheException {
        assertFalse("CqServiceImpl.EXECUTE_QUERY_DURING_INIT flag should be false ", CqServiceImpl.EXECUTE_QUERY_DURING_INIT);
        assertFalse("gemfire.cq.MAINTAIN_KEYS flag should be false ", CqServiceProvider.MAINTAIN_KEYS);
        int numOfQueryExecutions = (Integer) ((GemFireCacheImpl)getCache()).getCachePerfStats().getStats().get("queryExecutions");
        assertEquals("Number of query executions for cq.execute should be 0 ", 0, numOfQueryExecutions);
      }
    });
    
    // Create more values.
    server.invoke(new CacheSerializableRunnable("Create values") {
      public void run2() throws CacheException {
        Region region1 = getRootRegion().getSubregion(regions[0]);
        for (int i = numOfEntries+1; i <= numOfEntries*2; i++) {
          region1.put(KEY+i, new Portfolio(i));
        }
        LogWriterUtils.getLogWriter().info("### Number of Entries in Region :" + region1.keys().size());
      }
    });
    
    waitForCreated(client, cqName, KEY+numOfEntries*2);

    validateCQ(client, cqName,
        /* resultSize: */ noTest,
        /* creates: */ numOfEntries,
        /* updates: */ 0,
        /* deletes; */ 0,
        /* queryInserts: */ numOfEntries,
        /* queryUpdates: */ 0,
        /* queryDeletes: */ 0,
        /* totalEvents: */ numOfEntries);
    
    // Update values.
    createValues(server, regions[0], 5);
    createValues(server, regions[0], 10);
    
    waitForUpdated(client, cqName, KEY+numOfEntries);
    
    
    // validate Update events.
    validateCQ(client, cqName,
        /* resultSize: */ noTest,
        /* creates: */ numOfEntries,
        /* updates: */ 15,
        /* deletes; */ 0,
        /* queryInserts: */ numOfEntries,
        /* queryUpdates: */ 15,
        /* queryDeletes: */ 0,
        /* totalEvents: */ numOfEntries + 15);
    
    // Validate delete events.
    deleteValues(server, regions[0], 5);
    waitForDestroyed(client, cqName, KEY+5);
    
    validateCQ(client, cqName,
        /* resultSize: */ noTest,
        /* creates: */ numOfEntries,
        /* updates: */ 15,
        /* deletes; */5,
        /* queryInserts: */ numOfEntries,
        /* queryUpdates: */ 15,
        /* queryDeletes: */ 5,
        /* totalEvents: */ numOfEntries + 15 + 5);

    closeClient(client);   
    closeServer(server);    
  }

// Moved from CqQueryDUnitTest class.
//Tests when two pools each are configured to a different server
  // Each cq uses a different pool and the servers are shutdown.
  // The listeners for each cq should receive a connect and disconnect
  // when their respective servers are shutdown
  public void testCQAllServersLeaveMultiplePool() throws Exception {
    final Host host = Host.getHost(0);
    VM server1 = host.getVM(1);
    VM server2 = host.getVM(2);
    VM client = host.getVM(3);

    createServer(server1);

    final int port1 = server1.invoke(() -> CqQueryDUnitTest.getCacheServerPort());
    final String host0 = NetworkUtils.getServerHostName(server1.getHost());
    final int[] ports = AvailablePortHelper.getRandomAvailableTCPPorts(1);

    createServer(server2, ports[0]);
    final int thePort2 = server2.invoke(() -> CqQueryDUnitTest.getCacheServerPort());
    Wait.pause(8 * 1000);

    // Create client
    createClientWith2Pools(client, new int[] { port1 }, new int[] { thePort2 },
        host0, "-1");

    // Create CQs.
    createCQ(client, "testCQAllServersLeave_" + 11, cqs[11], true);
    executeCQ(client, "testCQAllServersLeave_" + 11, false, null);

    createCQ(client, "testCQAllServersLeave_" + 12, cqs[12], true);
    executeCQ(client, "testCQAllServersLeave_" + 12, false, null);

    Wait.pause(5 * 1000);
    waitForCqsConnected(client, "testCQAllServersLeave_11", 1);
    waitForCqsConnected(client, "testCQAllServersLeave_12", 1);
    // CREATE.
    createValues(server2, regions[0], 10);
    createValues(server2, regions[1], 10);
    waitForCreated(client, "testCQAllServersLeave_11", KEY + 10);

    // Close server1 pause is unnecessary here since each cq has it's own pool
    // and each pool is configured only to 1 server.
    closeServer(server1);
    waitForCqsDisconnected(client, "testCQAllServersLeave_11", 1);

    createValues(server2, regions[1], 20);
    waitForCreated(client, "testCQAllServersLeave_12", KEY + 19);
    
    // Close server 2 pause unnecessary here since each cq has it's own pool
    closeServer(server2);
    waitForCqsDisconnected(client, "testCQAllServersLeave_12", 1);

    // Close.
    closeClient(client);
  }  

}
