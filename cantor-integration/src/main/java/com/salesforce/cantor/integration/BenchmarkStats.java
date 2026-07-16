package com.salesforce.cantor.integration;

import java.util.*;

public class BenchmarkStats {
    private final String name;
    private final List<Long> durationsSorted;

    public BenchmarkStats(final String name, final List<Long> durations) {
        this.name = name;
        this.durationsSorted = new ArrayList<>(durations);
        Collections.sort(this.durationsSorted);
    }

    public String getName() {
        return name;
    }

    public int getCount() {

        return durationsSorted.size();
    }

    public long getSum() {
        long total = 0;
        for (final long t: durationsSorted) {
            total += t;
        }
        return total;
    }

    public double getAvg() {

        return (double) getSum() / getCount();
    }

    public long getMin() {
        return durationsSorted.get(0);
    }

    public long getMax() {
        return durationsSorted.get(durationsSorted.size() - 1);
    }

    public long calculatePercentile(final double percentile) {
        double rank = (percentile / 100.0) * getCount();
        int index = (int) Math.ceil(rank);
        return durationsSorted.get(index - 1);
    }

    public long getP50() {
        return calculatePercentile(50);
    }

    public long getP90() {
        return calculatePercentile(90);
    }

    public long getP95() {
        return calculatePercentile(95);
    }

    public long getP99() {
        return calculatePercentile(99);
    }

}
