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
package org.apache.omid.timestamp.storage;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.apache.omid.timestamp.storage.HBaseTimestampStorageConfig.DEFAULT_TIMESTAMP_STORAGE_CF_NAME;
import static org.apache.omid.timestamp.storage.HBaseTimestampStorageConfig.DEFAULT_TIMESTAMP_STORAGE_TABLE_NAME;
import static org.testng.Assert.assertEquals;

public class TestHBaseTimestampStorage {

    private static final Logger LOG = LoggerFactory.getLogger(TestHBaseTimestampStorage.class);

    private static final TableName TABLE_NAME = TableName.valueOf(DEFAULT_TIMESTAMP_STORAGE_TABLE_NAME);

    private static HBaseTestingUtility testutil;
    private static MiniHBaseCluster hbasecluster;
    protected static Configuration hbaseConf;


    @BeforeClass
    public static void setUpClass() throws Exception {
        // HBase setup
        hbaseConf = HBaseConfiguration.create();
        hbaseConf.setBoolean("hbase.localcluster.assign.random.ports",true);
        LOG.info("Create hbase");
        testutil = new HBaseTestingUtility(hbaseConf);
        hbasecluster = testutil.startMiniCluster(1);

    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (hbasecluster != null) {
            testutil.shutdownMiniCluster();
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        HBaseAdmin admin = testutil.getHBaseAdmin();

        if (!admin.tableExists(TableName.valueOf(DEFAULT_TIMESTAMP_STORAGE_TABLE_NAME))) {
            HTableDescriptor desc = new HTableDescriptor(TABLE_NAME);
            HColumnDescriptor datafam = new HColumnDescriptor(DEFAULT_TIMESTAMP_STORAGE_CF_NAME);
            datafam.setMaxVersions(Integer.MAX_VALUE);
            desc.addFamily(datafam);

            admin.createTable(desc);
        }

        if (admin.isTableDisabled(TableName.valueOf(DEFAULT_TIMESTAMP_STORAGE_TABLE_NAME))) {
            admin.enableTable(TableName.valueOf(DEFAULT_TIMESTAMP_STORAGE_TABLE_NAME));
        }
        HTableDescriptor[] tables = admin.listTables();
        for (HTableDescriptor t : tables) {
            LOG.info(t.getNameAsString());
        }
    }

    @AfterMethod
    public void tearDown() {
        try {
            LOG.info("tearing Down");
            HBaseAdmin admin = testutil.getHBaseAdmin();
            admin.disableTable(TableName.valueOf(DEFAULT_TIMESTAMP_STORAGE_TABLE_NAME));
            admin.deleteTable(TableName.valueOf(DEFAULT_TIMESTAMP_STORAGE_TABLE_NAME));

        } catch (IOException e) {
            LOG.error("Error tearing down", e);
        }
    }

    @Test(timeOut = 10_000)
    public void testHBaseTimestampStorage() throws Exception {

        final long INITIAL_TS_VALUE = 0;
        HBaseTimestampStorageConfig config = new HBaseTimestampStorageConfig();
        HBaseTimestampStorage tsStorage = new HBaseTimestampStorage(hbaseConf, config);

        // Test that the first time we get the timestamp is the initial value
        assertEquals(tsStorage.getMaxTimestamp(), INITIAL_TS_VALUE, "Initial value should be " + INITIAL_TS_VALUE);

        // Test that updating the timestamp succeeds when passing the initial value as the previous one
        long newTimestamp = 1;
        tsStorage.updateMaxTimestamp(INITIAL_TS_VALUE, newTimestamp);

        // Test setting a new timestamp fails (exception is thrown) when passing a wrong previous max timestamp
        long wrongTimestamp = 20;
        try {
            tsStorage.updateMaxTimestamp(wrongTimestamp, newTimestamp);
            Assert.fail("Shouldn't update");
        } catch (IOException e) {
            // Correct behavior
        }
        assertEquals(tsStorage.getMaxTimestamp(), newTimestamp, "Value should be still " + newTimestamp);

        // Test we can set a new timestamp when passing the right previous max timestamp
        long veryNewTimestamp = 40;
        tsStorage.updateMaxTimestamp(newTimestamp, veryNewTimestamp);
        assertEquals(tsStorage.getMaxTimestamp(), veryNewTimestamp, "Value should be " + veryNewTimestamp);

    }

}
