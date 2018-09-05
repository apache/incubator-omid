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

import static org.testng.Assert.assertEquals;

import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.testng.ITestContext;
import org.testng.annotations.Test;

@Test(groups = "sharedHBase")
public class TestAutoFlush extends OmidTestBase {

    @Test(timeOut = 10_000)
    public void testReadWithSeveralUncommitted(ITestContext context) throws Exception {

        byte[] family = Bytes.toBytes(TEST_FAMILY);
        byte[] row = Bytes.toBytes("row");
        byte[] col = Bytes.toBytes("col1");
        byte[] data = Bytes.toBytes("data");
        TransactionManager tm = newTransactionManager(context);
        TTable table = new TTable(connection, TEST_TABLE);

        // Turn off autoflush
        table.setAutoFlush(false);

        Transaction t = tm.begin();
        Put put = new Put(row);
        put.addColumn(family, col, data);
        table.put(t, put);

        // Data shouldn't be in DB yet
        Get get = new Get(row);
        Result result = table.getHTable().get(get);
        assertEquals(result.size(), 0, "Writes are already in DB");

        tm.commit(t);

        // After commit, both the cell and shadow cell should be there.
        // That's why we check for two elements in the test assertion
        result = table.getHTable().get(get);
        assertEquals(result.size(), 2, "Writes were not flushed to DB");
    }

}
