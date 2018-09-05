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
package org.apache.omid.transaction;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.testng.ITestContext;
import org.testng.annotations.Test;

@Test(groups = "sharedHBase")
public class TestReadPath extends OmidTestBase {

    final byte[] family = Bytes.toBytes(TEST_FAMILY);
    final byte[] row = Bytes.toBytes("row");
    private final byte[] col = Bytes.toBytes("col1");
    final byte[] data = Bytes.toBytes("data");
    private final byte[] uncommitted = Bytes.toBytes("uncommitted");

    @Test(timeOut = 10_000)
    public void testReadInterleaved(ITestContext context) throws Exception {
        TransactionManager tm = newTransactionManager(context);
        TTable table = new TTable(connection, TEST_TABLE);

        // Put some data on the DB
        Transaction t1 = tm.begin();
        Transaction t2 = tm.begin();

        Put put = new Put(row);
        put.addColumn(family, col, data);
        table.put(t1, put);
        tm.commit(t1);

        Get get = new Get(row);
        Result result = table.get(t2, get);
        assertFalse(result.containsColumn(family, col), "Should be unable to read column");
    }

    @Test(timeOut = 10_000)
    public void testReadWithSeveralUncommitted(ITestContext context) throws Exception {
        TransactionManager tm = newTransactionManager(context);
        TTable table = new TTable(connection, TEST_TABLE);

        // Put some data on the DB
        Transaction t = tm.begin();
        Put put = new Put(row);
        put.addColumn(family, col, data);
        table.put(t, put);
        tm.commit(t);
        List<Transaction> running = new ArrayList<>();

        // Shade the data with uncommitted data
        for (int i = 0; i < 10; ++i) {
            t = tm.begin();
            put = new Put(row);
            put.addColumn(family, col, uncommitted);
            table.put(t, put);
            running.add(t);
        }

        // Try to read from row, it should ignore the uncommitted data and return the original committed value
        t = tm.begin();
        Get get = new Get(row);
        Result result = table.get(t, get);
        Cell cell = result.getColumnLatestCell(family, col);
        assertNotNull(cell, "KeyValue is null");
        byte[] value = CellUtil.cloneValue(cell);
        assertTrue(Arrays.equals(data, value), "Read data doesn't match");
        tm.commit(t);

        table.close();

        for (Transaction r : running) {
            tm.rollback(r);
        }

    }

}
