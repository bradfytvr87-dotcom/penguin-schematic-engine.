package com.penguin.core;

import java.util.BitSet;

/** Tracks which schematic entries are correctly built, and which one to show next. */
public final class BuildProgress {
    private final int size;
    private final BitSet done = new BitSet();
    private final BitSet skipped = new BitSet();

    public BuildProgress(int size) {
        this.size = size;
    }

    public int size() { return size; }
    public int doneCount() { return done.cardinality(); }
    public boolean isDone(int i) { return done.get(i); }
    public boolean complete() { return done.cardinality() >= size; }

    public void setDone(int i, boolean value) {
        if (value) {
            done.set(i);
            skipped.clear(i);
        } else {
            done.clear(i);
        }
    }

    /** Index of the next block to place, or -1 when everything is built. Skipped entries come last. */
    public int cursor() {
        for (int i = done.nextClearBit(0); i < size; i = done.nextClearBit(i + 1)) {
            if (!skipped.get(i)) return i;
        }
        int i = done.nextClearBit(0);
        return i < size ? i : -1;
    }

    /** Pushes the current block to the back of the queue. */
    public void skipCurrent() {
        int c = cursor();
        if (c >= 0) skipped.set(c);
    }

    public void reset() {
        done.clear();
        skipped.clear();
    }
}
