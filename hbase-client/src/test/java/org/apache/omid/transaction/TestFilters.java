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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.BinaryComparator;
import org.apache.hadoop.hbase.filter.ColumnPrefixFilter;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.ValueFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.omid.committable.CommitTable;
import org.apache.omid.metrics.NullMetricsProvider;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.ITestContext;
import org.testng.annotations.Test;

import org.apache.phoenix.thirdparty.com.google.common.util.concurrent.ListenableFuture;
import org.apache.phoenix.thirdparty.com.google.common.util.concurrent.SettableFuture;

/**
 * Tests to verify that Get and Scan filters still work with transactions tables
 */
@Test(groups = "sharedHBase")
public class TestFilters extends OmidTestBase {

    byte[] family = Bytes.toBytes(TEST_FAMILY);
    private byte[] row1 = Bytes.toBytes("row1");
    private byte[] row2 = Bytes.toBytes("row2");
    private byte[] row3 = Bytes.toBytes("row3");
    private byte[] prefix = Bytes.toBytes("foo");
    private byte[] col1 = Bytes.toBytes("foobar");
    private byte[] col2 = Bytes.toBytes("boofar");

    @Test(timeOut = 60_000)
    public void testGetWithColumnPrefixFilter(ITestContext context) throws Exception {
        testGet(context, new ColumnPrefixFilter(prefix));
    }

    @Test(timeOut = 60_000)
    public void testGetWithValueFilter(ITestContext context) throws Exception {
        testGet(context, new ValueFilter(CompareFilter.CompareOp.EQUAL, new BinaryComparator(col1)));
    }

    private void testGet(ITestContext context, Filter f) throws Exception {

        CommitTable.Client commitTableClient = spy(getCommitTable(context).getClient());

        HBaseOmidClientConfiguration hbaseOmidClientConf = new HBaseOmidClientConfiguration();
        hbaseOmidClientConf.setConnectionString("localhost:1234");
        hbaseOmidClientConf.setHBaseConfiguration(hbaseConf);

        TTable table = new TTable(connection, TEST_TABLE);
        PostCommitActions syncPostCommitter = spy(
                new HBaseSyncPostCommitter(new NullMetricsProvider(), commitTableClient, connection));
        AbstractTransactionManager tm = HBaseTransactionManager.builder(hbaseOmidClientConf)
                .commitTableClient(commitTableClient)
                .commitTableWriter(getCommitTable(context).getWriter())
                .postCommitter(syncPostCommitter)
                .build();

        writeRows(table, tm, syncPostCommitter);

        Transaction t = tm.begin();
        Get g = new Get(row1);
        g.setFilter(f);

        Result r = table.get(t, g);
        assertEquals(r.getColumnCells(family, col1).size(), 1, "should exist in result");
        assertEquals(r.getColumnCells(family, col2).size(), 0 , "shouldn't exist in result");

        g = new Get(row2);
        g.setFilter(f);
        r = table.get(t, g);
        assertEquals(r.getColumnCells(family, col1).size(), 1, "should exist in result");
        assertEquals(r.getColumnCells(family, col2).size(), 0, "shouldn't exist in result");

        g = new Get(row3);
        g.setFilter(f);
        r = table.get(t, g);
        assertEquals(r.getColumnCells(family, col2).size(), 0, "shouldn't exist in result");

    }

    @Test(timeOut = 60_000)
    public void testScanWithColumnPrefixFilter(ITestContext context) throws Exception {
        testScan(context, new ColumnPrefixFilter(prefix));
    }

    @Test(timeOut = 60_000)
    public void testScanWithValueFilter(ITestContext context) throws Exception {
        testScan(context, new ValueFilter(CompareFilter.CompareOp.EQUAL, new BinaryComparator(col1)));
    }

    private void testScan(ITestContext context, Filter f) throws Exception {

        CommitTable.Client commitTableClient = spy(getCommitTable(context).getClient());

        HBaseOmidClientConfiguration hbaseOmidClientConf = new HBaseOmidClientConfiguration();
        hbaseOmidClientConf.getOmidClientConfiguration().setConnectionString("localhost:1234");
        hbaseOmidClientConf.setHBaseConfiguration(hbaseConf);
        TTable table = new TTable(connection, TEST_TABLE);
        PostCommitActions syncPostCommitter = spy(
                new HBaseSyncPostCommitter(new NullMetricsProvider(), commitTableClient, connection));
        AbstractTransactionManager tm = HBaseTransactionManager.builder(hbaseOmidClientConf)
                .commitTableClient(commitTableClient)
                .commitTableWriter(getCommitTable(context).getWriter())
                .postCommitter(syncPostCommitter)
                .build();

        writeRows(table, tm, syncPostCommitter);

        Transaction t = tm.begin();
        Scan s = new Scan().setFilter(f);

        ResultScanner rs = table.getScanner(t, s);

        Result r = rs.next();
        assertEquals(r.getColumnCells(family, col1).size(), 1, "should exist in result");
        assertEquals(r.getColumnCells(family, col2).size(), 0, "shouldn't exist in result");

        r = rs.next();
        assertEquals(r.getColumnCells(family, col1).size(), 1, "should exist in result");
        assertEquals(r.getColumnCells(family, col2).size(), 0, "shouldn't exist in result");

        r = rs.next();
        assertNull(r, "Last row shouldn't exist");

    }

    private void writeRows(TTable table, TransactionManager tm, PostCommitActions postCommitter)
            throws Exception {
        // create normal row with both cells
        Transaction t = tm.begin();
        Put p = new Put(row1);
        p.addColumn(family, col1, col1);
        p.addColumn(family, col2, col2);
        table.put(t, p);
        tm.commit(t);

        // create normal row, but fail to update shadow cells
        doAnswer(new Answer<ListenableFuture<Void>>() {
            public ListenableFuture<Void> answer(InvocationOnMock invocation) {
                // Do not invoke the real method
                return SettableFuture.create();
            }
        }).when(postCommitter).updateShadowCells(any(HBaseTransaction.class));

        t = tm.begin();
        p = new Put(row2);
        p.addColumn(family, col1, col1);
        p.addColumn(family, col2, col2);
        table.put(t, p);
        try {
            tm.commit(t);
        } catch (TransactionException e) {
            // Expected, see comment above
        }

        // create normal row with only one cell
        t = tm.begin();
        p = new Put(row3);
        p.addColumn(family, col2, col2);
        table.put(t, p);
        try {
            tm.commit(t);
        } catch (TransactionException e) {
            // Expected, see comment above
        }
    }

}
