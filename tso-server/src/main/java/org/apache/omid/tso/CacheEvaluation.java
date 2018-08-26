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
package org.apache.omid.tso;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Random;

public class CacheEvaluation {

    private final static int ENTRIES = 1000000;
    private final static int WARMUP_ROUNDS = 2;
    private final static int ROUNDS = 4;
    private final static double HOT_PERC = 1;

    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
        int[] asoc = new int[]{8, 16, 32};
        for (int anAsoc : asoc) {
            PrintWriter writer = new PrintWriter(anAsoc + ".out", "UTF-8");
            new CacheEvaluation().testEntriesAge(new LongCache(ENTRIES, anAsoc), writer);
            writer.close();
        }
        PrintWriter writer = new PrintWriter("guava.out", "UTF-8");
        writer.close();
    }

    private void testEntriesAge(LongCache cache, PrintWriter writer) {
        Random random = new Random();

        long seed = random.nextLong();

        writer.println("# Random seed: " + seed);
        random.setSeed(seed);
        int removals = 0;
        double tempStdDev = 0;
        double tempAvg = 0;

        int i = 0;
        int largestDeletedTimestamp = 0;
        long hotItem = random.nextLong();

        Runtime.getRuntime().gc();

        for (; i < ENTRIES * WARMUP_ROUNDS; ++i) {
            long toInsert = random.nextInt(100) < HOT_PERC ? hotItem : random.nextLong();
            long removed = cache.set(toInsert, i);
            if (removed > largestDeletedTimestamp) {
                largestDeletedTimestamp = (int) removed;
            }
            if (removed > largestDeletedTimestamp) {
                largestDeletedTimestamp = (int) removed;
            }
            if (i % ENTRIES == 0) {
                int round = i / ENTRIES + 1;
                System.err.format("Warmup [%d/%d]%n", round, WARMUP_ROUNDS);
            }
        }

        long time = System.nanoTime();
        for (; i < ENTRIES * (WARMUP_ROUNDS + ROUNDS); ++i) {
            long toInsert = random.nextInt(100) < HOT_PERC ? hotItem : random.nextLong();
            long removed = cache.set(toInsert, i);
            if (removed > largestDeletedTimestamp) {
                largestDeletedTimestamp = (int) removed;
            }
            int gap = i - largestDeletedTimestamp;
            removals++;
            double oldAvg = tempAvg;
            tempAvg += (gap - tempAvg) / removals;
            tempStdDev += (gap - oldAvg) * (gap - tempAvg);
            if (i % ENTRIES == 0) {
                int round = i / ENTRIES - WARMUP_ROUNDS + 1;
                System.err.format("Progress [%d/%d]%n", round, ROUNDS);
            }
        }
        long elapsed = System.nanoTime() - time;
        double elapsedSeconds = (elapsed / (double) 1000000000);
        long totalOps = ENTRIES * ROUNDS;
        writer.println("# Free mem before GC (MB) :" + (Runtime.getRuntime().freeMemory() / (double) (1024 * 1024)));
        Runtime.getRuntime().gc();
        writer.println("# Free mem (MB) :" + (Runtime.getRuntime().freeMemory() / (double) (1024 * 1024)));
        writer.println("# Elapsed (s): " + elapsedSeconds);
        writer.println("# Elapsed per 100 ops (ms): " + (elapsed / (double) totalOps / 100 / 1000000));
        writer.println("# Ops per s : " + (totalOps / elapsedSeconds));
        writer.println("# Avg gap: " + (tempAvg));
        writer.println("# Std dev gap: " + Math.sqrt((tempStdDev / ENTRIES)));
    }

}
