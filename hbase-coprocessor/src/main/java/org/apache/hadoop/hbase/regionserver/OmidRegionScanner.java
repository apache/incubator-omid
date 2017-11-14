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
package org.apache.hadoop.hbase.regionserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import org.apache.hadoop.hbase.regionserver.ScannerContext;
import org.apache.omid.transaction.HBaseTransaction;
import org.apache.omid.transaction.SnapshotFilterImpl;

public class OmidRegionScanner implements RegionScanner {

    RegionScanner scanner;
    SnapshotFilterImpl snapshotFilter;
    HBaseTransaction transaction;
    int maxVersions;
    Map<String, List<Cell>> familyDeletionCache;
    
    public OmidRegionScanner(SnapshotFilterImpl snapshotFilter,
                      RegionScanner s,
                      HBaseTransaction transaction,
                      int maxVersions) {
        this.snapshotFilter = snapshotFilter;
        this.scanner = s;
        this.transaction = transaction;
        this.maxVersions = maxVersions;
        this.familyDeletionCache = new HashMap<String, List<Cell>>();
    }

    @Override
    public boolean next(List<Cell> results) throws IOException {
       return next(results, Integer.MAX_VALUE);
    }

    public boolean next(List<Cell> result, int limit) throws IOException {
        return nextRaw(result, limit);
    }

    @Override
    public void close() throws IOException {
        scanner.close();
    }

    @Override
    public HRegionInfo getRegionInfo() {
        return scanner.getRegionInfo();
    }

    @Override
    public boolean isFilterDone() throws IOException {
        return scanner.isFilterDone();
    }

    @Override
    public boolean reseek(byte[] row) throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public long getMaxResultSize() {
        return scanner.getMaxResultSize();
    }

    @Override
    public long getMvccReadPoint() {
        return scanner.getMvccReadPoint();
    }

    @Override
    public boolean nextRaw(List<Cell> result) throws IOException {
        return nextRaw(result,Integer.MAX_VALUE);
    }

    public boolean next(List<Cell> result,
            ScannerContext scannerContext) throws IOException {
        return next(result, scannerContext.getBatchLimit());
    }

    public boolean nextRaw(List<Cell> result,
            ScannerContext scannerContext) throws IOException {
        return nextRaw(result, scannerContext.getBatchLimit());
    }

    public int getBatch() {
        return Integer.MAX_VALUE;
    }

    public boolean nextRaw(List<Cell> result, int limit) throws IOException {
        List<Cell> filteredResult = new ArrayList<Cell>();
        while (filteredResult.isEmpty()) {
            scanner.nextRaw(filteredResult);
            if (filteredResult.isEmpty()) {
                return false;
            }

            filteredResult = snapshotFilter.filterCellsForSnapshot(filteredResult, transaction, maxVersions, familyDeletionCache);
        }

        for (Cell cell : filteredResult) {
            result.add(cell);
        }

        return true;
    }

}