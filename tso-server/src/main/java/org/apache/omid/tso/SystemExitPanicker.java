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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemExitPanicker implements Panicker {

    private static final Logger LOG = LoggerFactory.getLogger(SystemExitPanicker.class);

    @Override
    public void panic(String reason) {
        panic(reason, new Throwable("TSO Error"));
    }

    @Override
    public void panic(String reason, Throwable cause) {
        LOG.error(reason, cause);
        // Execute the shutdown sequence from a different thread to avoid deadlocks during the shutdown hooks
        Runnable shutdown = new Runnable() {
            @Override
            public void run() {
                System.exit(-1);
            }
        };
        Thread panicThread = new Thread(shutdown, "SystemExitPanicker Thread");
        panicThread.setDaemon(true);
        panicThread.start();
    }

}
