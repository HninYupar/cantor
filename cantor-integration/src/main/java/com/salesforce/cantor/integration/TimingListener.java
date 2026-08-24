/*
 * Copyright (c) 2020, Salesforce.com, Inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.cantor.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.*;

// Records the duration of each test method.

public class TimingListener implements ITestListener {

    private static final Logger logger = LoggerFactory.getLogger(TimingListener.class);

    private final List<TestTiming> results = new ArrayList<>();

    @Override
    public void onTestSuccess(ITestResult result) {
        record(result, "PASS");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        record(result, "FAIL");
        logger.error("TEST FAILED: {}.{}",
                result.getTestClass().getRealClass().getSimpleName(),
                result.getMethod().getMethodName(),
                result.getThrowable());
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        record(result, "SKIP");
    }

    private void record(ITestResult result, String status) {
        long durationMs = result.getEndMillis() - result.getStartMillis();
        String className = result.getTestClass().getRealClass().getSimpleName();
        String methodName = result.getMethod().getMethodName();
        results.add(new TestTiming(className, methodName, durationMs, status));
    }

    public List<TestTiming> getResults() {
        return Collections.unmodifiableList(results);
    }

    public static class TestTiming {
        private final String className;
        private final String methodName;
        private final long durationMs;
        private final String status;

        public TestTiming(String className, String methodName, long durationMs, String status) {
            this.className = className;
            this.methodName = methodName;
            this.durationMs = durationMs;
            this.status = status;
        }

        public String getClassName() { return className; }
        public String getMethodName() { return methodName; }
        public long getDurationMs() { return durationMs; }
        public String getStatus() { return status; }

        @Override
        public String toString() {
            return String.format("[%s] %s.%s — %dms", status, className, methodName, durationMs);
        }
    }
}
