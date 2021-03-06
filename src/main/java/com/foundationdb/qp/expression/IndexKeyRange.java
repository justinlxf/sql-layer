/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.qp.expression;

import com.foundationdb.qp.row.ValuesHolderRow;
import com.foundationdb.qp.rowtype.IndexRowType;
import com.foundationdb.server.api.dml.ColumnSelector;
import com.foundationdb.server.api.dml.ConstantColumnSelector;

public class IndexKeyRange
{
    public String toString()
    {
        StringBuilder buffer = new StringBuilder();
        buffer.append('(');
        if (lo != null && boundColumns > 0) {
            buffer.append(loInclusive() ? ">=" : ">");
            buffer.append(lo.toString());
        }
        buffer.append(',');
        if (hi != null && boundColumns > 0) {
            buffer.append(hiInclusive() ? "<=" : "<");
            buffer.append(hi.toString());
        }
        buffer.append(')');
        return buffer.toString();
    }

    public IndexRowType indexRowType()
    {
        return indexRowType;
    }

    public IndexBound lo()
    {
        return lo;
    }

    public IndexBound hi()
    {
        return hi;
    }

    public boolean loInclusive()
    {
        return loInclusive;
    }

    public boolean hiInclusive()
    {
        return hiInclusive;
    }

    public boolean unbounded()
    {
        return (boundColumns == 0);
    }

    public int boundColumns()
    {
        return boundColumns;
    }

    /**
     * Describes a full index scan.
     * @param indexRowType The row type of index keys.
     * @return IndexKeyRange covering all keys of the index.
     */
    public static IndexKeyRange unbounded(IndexRowType indexRowType) {
        IndexBound unbounded = new IndexBound(new ValuesHolderRow(indexRowType), ConstantColumnSelector.ALL_OFF);
        return new IndexKeyRange(indexRowType, unbounded, false, unbounded, false, IndexKind.CONVENTIONAL);
    }

    /**
     * Describes a range of keys between lo and hi. The bounds are inclusive or not depending on
     * loInclusive and hiInclusive. lo and hi must both be non-null. There are constraints on the bounds:
     * - The ColumnSelectors for lo and hi must select for the same columns.
     * - The selected columns must be leading columns of the index.
     *
     * @param indexRowType The row type of index keys.
     * @param lo           Lower bound of the range.
     * @param loInclusive  True if the lower bound is inclusive, false if exclusive.
     * @param hi           Upper bound of the range.
     * @param hiInclusive  True if the upper bound is inclusive, false if exclusive.
     * @return IndexKeyRange covering the keys lying between lo and hi, subject to the loInclusive and
     * hiInclusive flags.
     */
    public static IndexKeyRange bounded(IndexRowType indexRowType,
                                        IndexBound lo,
                                        boolean loInclusive,
                                        IndexBound hi,
                                        boolean hiInclusive)
    {
        if (lo == null || hi == null) {
            throw new IllegalArgumentException("IndexBound arguments must not be null");
        }
        return new IndexKeyRange(indexRowType, lo, loInclusive, hi, hiInclusive, IndexKind.CONVENTIONAL);
    }

    /**
     * Describes a range of keys between lo and hi. lo and hi must both be non-null. The ColumnSelectors for
     * lo and hi must select for the same columns.
     *
     * @param indexRowType The row type of index keys.
     * @param lo           Lower bound of the range.
     * @param hi           Upper bound of the range.
     * @return IndexKeyRange covering the keys lying between lo and hi.
     */
    public static IndexKeyRange spatial(IndexRowType indexRowType,
                                        IndexBound lo,
                                        IndexBound hi)
    {
        if (lo == null) {
            throw new IllegalArgumentException("lo must not be null");
        }
        return new IndexKeyRange(indexRowType, lo, true, hi, true, IndexKind.SPATIAL);
    }

    /**
     * Describes a range of keys starting at lo and expanding out,
     *
     * @param indexRowType The row type of index keys.
     * @param lo           Lower bound of the range.
     * @return IndexKeyRange covering the keys lying starting at lo.
     */
    public static IndexKeyRange around(IndexRowType indexRowType,
                                       IndexBound lo)
    {
        if (lo == null) {
            throw new IllegalArgumentException("IndexBound argument must not be null");
        }
        return new IndexKeyRange(indexRowType, lo, true, null, false, IndexKind.SPATIAL);
    }

    /**
     * Describes all keys in the index starting at or after lo, depending on loInclusive.
     * This is used only in lexicographic scans.
     *
     * @param indexRowType The row type of index keys.
     * @param lo           Lower bound of the range.
     * @param loInclusive  True if the lower bound is inclusive, false if exclusive.
     * @return IndexKeyRange covering the keys starting at or after lo.
     */
    public static IndexKeyRange startingAt(IndexRowType indexRowType,
                                           IndexBound lo,
                                           boolean loInclusive)
    {
        if (lo == null) {
            throw new IllegalArgumentException("IndexBound argument must not be null");
        }
        return new IndexKeyRange(indexRowType, lo, loInclusive, null, false, IndexKind.LEXICOGRAPHIC);
    }

    /**
     * Describes all keys in the index ending at or before hi, depending on hiInclusive.
     *
     * @param indexRowType The row type of index keys.
     * @param hi           Upper bound of the range.
     * @param hiInclusive  True if the upper bound is inclusive, false if exclusive.
     * @return IndexKeyRange covering the keys ending at or before lo.
     */
    public static IndexKeyRange endingAt(IndexRowType indexRowType,
                                         IndexBound hi,
                                         boolean hiInclusive)
    {
        if (hi == null) {
            throw new IllegalArgumentException("IndexBound argument must not be null");
        }
        return new IndexKeyRange(indexRowType, null, false, hi, hiInclusive, IndexKind.LEXICOGRAPHIC);
    }

    /**
     * Describes all keys in the index starting at or after lo, depending on loInclusive; and
     * ending at or before hi, depending on hiInclusive.
     * This is used only in lexicographic scans.
     *
     * @param indexRowType The row type of index keys.
     * @param lo           Lower bound of the range.
     * @param loInclusive  True if the lower bound is inclusive, false if exclusive.
     * @param hi           Upper bound of the range.
     * @param hiInclusive  True if the upper bound is inclusive, false if exclusive.
     * @return IndexKeyRange covering the keys ending at or before lo.
     */
    public static IndexKeyRange startingAtAndEndingAt(
        IndexRowType indexRowType,
        IndexBound lo,
        boolean loInclusive,
        IndexBound hi,
        boolean hiInclusive)
    {
        if (hi == null) {
            throw new IllegalArgumentException("IndexBound argument must not be null");
        }
        return new IndexKeyRange(indexRowType, lo, loInclusive, hi, hiInclusive, IndexKind.LEXICOGRAPHIC);
    }

    public boolean lexicographic()
    {
        return indexKind == IndexKind.LEXICOGRAPHIC;
    }

    public boolean spatial()
    {
        return indexKind == IndexKind.SPATIAL;
    }

    public IndexKeyRange resetLo(IndexBound newLo)
    {
        IndexKeyRange restart = new IndexKeyRange(this);
        restart.boundColumns = boundColumns(indexRowType, newLo);
        restart.lo = newLo;
        restart.loInclusive = true;
        return restart;
    }

    public IndexKeyRange resetHi(IndexBound newHi)
    {
        IndexKeyRange restart = new IndexKeyRange(this);
        restart.boundColumns = boundColumns(indexRowType, newHi);
        restart.hi = newHi;
        restart.hiInclusive = true;
        return restart;
    }

    private IndexKeyRange(IndexRowType indexRowType,
                          IndexBound lo,
                          boolean loInclusive,
                          IndexBound hi,
                          boolean hiInclusive,
                          IndexKind indexKind)
    {
        this.boundColumns =
            lo == null
            ? boundColumns(indexRowType, hi) :
            hi == null
            ? boundColumns(indexRowType, lo)
            : boundColumns(indexRowType, lo, hi);
        this.indexRowType = indexRowType;
        this.lo = lo;
        this.loInclusive = loInclusive;
        this.hi = hi;
        this.hiInclusive = hiInclusive;
        this.indexKind = indexKind;
    }

    private IndexKeyRange(IndexKeyRange indexKeyRange)
    {
        this.indexRowType = indexKeyRange.indexRowType;
        this.boundColumns = indexKeyRange.boundColumns;
        this.lo = indexKeyRange.lo;
        this.loInclusive = indexKeyRange.loInclusive;
        this.hi = indexKeyRange.hi;
        this.hiInclusive = indexKeyRange.hiInclusive;
        this.indexKind = indexKeyRange.indexKind;
    }

    private static int boundColumns(IndexRowType indexRowType, IndexBound lo, IndexBound hi)
    {
        ColumnSelector loSelector = lo.columnSelector();
        ColumnSelector hiSelector = hi.columnSelector();
        boolean selected = true;
        int boundColumns = 0;
        for (int i = 0; i < indexRowType.nFields(); i++) {
            if (loSelector.includesColumn(i) != hiSelector.includesColumn(i)) {
                throw new IllegalArgumentException(
                    String.format("IndexBound arguments specify different fields of index %s", indexRowType));
            }
            if (selected) {
                // loSelector.includesColumn(i) will equal hiSelector.includesColumn(i) for non-lexicographic
                // ranges. For lexicographic, we want boundColumns to indicate the maximum value, relying on
                // SortCursorUnidirectionalLexicographic to take care of the shorter one.
                if (loSelector.includesColumn(i) || hiSelector.includesColumn(i)) {
                    boundColumns++;
                } else {
                    selected = false;
                }
            } else {
                if (loSelector.includesColumn(i)) {
                    throw new IllegalArgumentException(
                        String.format("IndexBound arguments for index %s specify non-leading fields", indexRowType));
                }
            }
        }
        return boundColumns;
    }

    private static int boundColumns(IndexRowType indexRowType, IndexBound bound)
    {
        ColumnSelector selector = bound.columnSelector();
        boolean selected = true;
        int boundColumns = 0;
        for (int i = 0; i < indexRowType.nFields(); i++) {
            if (selected) {
                if (selector.includesColumn(i)) {
                    boundColumns++;
                } else {
                    selected = false;
                }
            } else {
                if (selector.includesColumn(i)) {
                    throw new IllegalArgumentException(
                        String.format("IndexBound arguments for index %s specify non-leading fields", indexRowType));
                }
            }
        }
        assert boundColumns > 0;
        return boundColumns;
    }

    // Object state

    private final IndexRowType indexRowType;
    private int boundColumns;
    private IndexBound lo;
    private boolean loInclusive;
    private IndexBound hi;
    private boolean hiInclusive;
    private final IndexKind indexKind;

    // A CONVENTIONAL (SQL Layer) index scan normally allows a range for only the last specified part of the bound. E.g.,
    // (1, 10, 800) - (1, 10, 888) is legal, but (1, 10, 800) - (1, 20, 888) is not, because there are two ranges,
    // 10-20 and 800-888.
    //
    // A LEXICOGRAPHIC index is required to support MySQL. MySQL requires a different approach in which we start at
    // the lower bound and scan everything in the index up to the upper bound. So (1, 10, 800) - (1, 20, 888) is
    // legal, and could return a row that is lexicographically between these bounds, but outside some range, e.g.
    // (1, 11, 900). This will also be useful in supporting queries such as select * from t where (x, y) > (5, 7).
    //
    // A SPATIAL index requires has no requirements other than specifying a match or range on any restricted column.

    public enum IndexKind
    {
        CONVENTIONAL,
        LEXICOGRAPHIC,
        SPATIAL
    }
}
