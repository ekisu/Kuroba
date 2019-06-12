package com.github.adamantcheese.chan.utils;

import android.util.Range;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RangeSet implements Serializable {
    private List<Range<Long>> ranges = new ArrayList<>();

    private boolean rangesAreContiguous(Range<Long> left, Range<Long> right) {
        return left.getUpper() + 1 >= right.getLower();
    }

    private void joinRanges(int i, int j) {
        Range<Long> left = ranges.get(i);
        Range<Long> right = ranges.get(j);

        Range<Long> newRange = left.extend(right);

        ranges.set(i, newRange);
        ranges.remove(j);
    }

    private void tryMergeRanges() {
        for (int i = 0; i < ranges.size(); i++) {
            while (i < ranges.size() - 1
                    && rangesAreContiguous(ranges.get(i), ranges.get(i + 1))) {
                joinRanges(i, i + 1);
            }
        }
    }

    private int findRangeInsertPosition(long lowerBound) {
        int rangePosition = 0;
        while (rangePosition < ranges.size()
                && lowerBound > ranges.get(rangePosition).getLower()) {
            rangePosition++;
        }

        return rangePosition;
    }

    public void union(Range<Long> r) {
        int newRangePosition = findRangeInsertPosition(r.getLower());
        ranges.add(newRangePosition, r);

        tryMergeRanges();
    }

    public void union(long num) {
        this.union(new Range<>(num, num));
    }

    public boolean contains(Range<Long> r) {
        for (Range<Long> internalRange : ranges) {
            if (internalRange.contains(r)) {
                return true;
            }
        }

        return false;
    }

    public boolean contains(long num) {
        return this.contains(new Range<>(num, num));
    }

    public Range<Long> intersect(Range<Long> r) {
        for (Range<Long> internalRange : ranges) {
            try {
                return internalRange.intersect(r);
            } catch (IllegalArgumentException e) {} // Disjoint
        }

        return null;
    }

    private void writeObject(ObjectOutputStream stream) throws IOException {
        ArrayList<Long> numbers = new ArrayList<>();
        for (Range<Long> r : ranges) {
            numbers.add(r.getLower());
            numbers.add(r.getUpper());
        }

        stream.writeObject(numbers);
    }

    private void readObject(java.io.ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        ArrayList<Long> numbers = (ArrayList<Long>) stream.readObject();
        ranges = new ArrayList<>();

        int i = 0;
        while (i < numbers.size()) {
            ranges.add(new Range<>(numbers.get(i++), numbers.get(i++)));
        }
    }
}
