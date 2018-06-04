/*
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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.omid.tso.client;

import com.google.common.collect.Sets;

import org.apache.omid.committable.CommitTable;
import org.apache.omid.committable.InMemoryCommitTable;
import org.apache.omid.tso.util.DummyCellIdImpl;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.concurrent.ExecutionException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestMockTSOClient {

    final static public CellId c1 = new DummyCellIdImpl(0xdeadbeefL);
    final static public CellId c2 = new DummyCellIdImpl(-0xfeedcafeL);

    @Test(timeOut = 10_000)
    public void testConflicts() throws Exception {
        CommitTable commitTable = new InMemoryCommitTable();
        TSOProtocol client = new MockTSOClient(commitTable.getWriter());

        long tr1 = client.getNewStartTimestamp().get();
        long tr2 = client.getNewStartTimestamp().get();

        client.commit(tr1, Sets.newHashSet(c1), new HashSet<CellId>()).get();

        try {
            client.commit(tr2, Sets.newHashSet(c1, c2), new HashSet<CellId>()).get();
            fail("Shouldn't have committed");
        } catch (ExecutionException ee) {
            assertEquals(ee.getCause().getClass(), AbortException.class, "Should have aborted");
        }
    }

    @Test(timeOut = 10_000)
    public void testWatermarkUpdate() throws Exception {
        CommitTable commitTable = new InMemoryCommitTable();
        TSOProtocol client = new MockTSOClient(commitTable.getWriter());
        CommitTable.Client commitTableClient = commitTable.getClient();

        long tr1 = client.getNewStartTimestamp().get();
        client.commit(tr1, Sets.newHashSet(c1), new HashSet<CellId>()).get();

        long initWatermark = commitTableClient.readLowWatermark().get();

        long tr2 = client.getNewStartTimestamp().get();
        client.commit(tr2, Sets.newHashSet(c1), new HashSet<CellId>()).get();

        long newWatermark = commitTableClient.readLowWatermark().get();
        assertTrue(newWatermark > initWatermark, "new low watermark should be bigger");
    }
}
