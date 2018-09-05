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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;

import org.apache.omid.HBaseShims;
import org.apache.omid.tools.hbase.HBaseLogin;
import org.apache.omid.tools.hbase.SecureHBaseConfig;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;

public class CompactorUtil {

    public static void setOmidCompaction(Connection conn, TableName table, byte[] columnFamily, String value)
            throws IOException {
        HBaseShims.setCompaction(conn, table, columnFamily, OmidCompactor.OMID_COMPACTABLE_CF_FLAG, value);
    }

    public static void enableOmidCompaction(Connection conn,
                                            TableName table, byte[] columnFamily) throws IOException {

        setOmidCompaction(conn, table, columnFamily, Boolean.TRUE.toString());
    }

    public static void disableOmidCompaction(Connection conn,
                                             TableName table, byte[] columnFamily) throws IOException {
        setOmidCompaction(conn, table, columnFamily, Boolean.FALSE.toString());
    }

    static class Config {
        @Parameter(names = "-table", required = true)
        String table;

        @Parameter(names = "-columnFamily", required = false)
        String columnFamily;

        @Parameter(names = "-help")
        boolean help = false;

        @Parameter(names = "-enable")
        boolean enable = false;

        @Parameter(names = "-disable")
        boolean disable = false;

        @ParametersDelegate
        private SecureHBaseConfig loginFlags = new SecureHBaseConfig();

    }

    public static void main(String[] args) throws IOException {
        Config cmdline = new Config();
        JCommander jcommander = new JCommander(cmdline, args);
        if (cmdline.help) {
            jcommander.usage("CompactorUtil");
            System.exit(1);
        }

        HBaseLogin.loginIfNeeded(cmdline.loginFlags);

        Configuration conf = HBaseConfiguration.create();
        Connection conn = ConnectionFactory.createConnection(conf);

        if (cmdline.enable) {
            enableOmidCompaction(conn, TableName.valueOf(cmdline.table),
                    Bytes.toBytes(cmdline.columnFamily));
        } else if (cmdline.disable) {
            disableOmidCompaction(conn, TableName.valueOf(cmdline.table),
                    Bytes.toBytes(cmdline.columnFamily));
        } else {
            System.err.println("Must specify enable or disable");
        }
    }
}
