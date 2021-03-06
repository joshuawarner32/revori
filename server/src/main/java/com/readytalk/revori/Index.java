/* Copyright (c) 2010-2012, Revori Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies. */

package com.readytalk.revori;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Class representing an index on a table.  Instances of this class
 * may used to specify a way to organize data for efficient access.
 */
@NotThreadSafe
public final class Index implements Comparable<Index> {
  /**
   * The table specified when this index was defined.
   */
  public final Table table;

  /**
   * An immutable list of columns which determine the organization of
   * data in the index.<p>
   *
   * This list may contain more columns than were specified when the
   * index was defined if the original list did not contain all the
   * columns of the primary key.  In this case, any missing columns
   * are added to the end of the original list to ensure each row maps
   * to a unique key in the index.
   */
  public final List<Column<?>> columns;

  /**
   * Defines an index which is associated with the specified list of
   * columns.  The order of the list determines the indexing order as
   * in an SQL DBMS.
   */
  public Index(Table table, List<Column<?>> primaryKey) {
    this.table = table;
    
    List<Column<?>> copy = new ArrayList<Column<?>>(primaryKey);

    if (table.primaryKey != null) {
      // pad index with primary key columns to make it unique
      for (Column<?> c: table.primaryKey.columns) {
        if (! copy.contains(c)) {
          copy.add(c);
        }
      }
    }

    this.columns = Collections.unmodifiableList(copy);
  }

  public int compareColumns(Index o) {
    int d = columns.size() - o.columns.size();
    if (d != 0) {
      return d;
    }

    Iterator<Column<?>> ti = columns.iterator();
    Iterator<Column<?>> oi = o.columns.iterator();
    while (ti.hasNext()) {
      d = ti.next().compareTo(oi.next());
      if (d != 0) {
        return d;
      }
    }

    return 0;
  }

  public int compareTo(Index o) {
    if (o == this) return 0;

    int d = table.compareTo(o.table);
    if (d != 0) {
      return d;
    }

    return compareColumns(o);
  }

  public int hashCode() {
    int h = table.hashCode();
    for (Column<?> c: columns) {
      h ^= c.hashCode();
    }
    return h;
  }

  /**
   * Returns true if and only if the specified object is an Index and
   * its table and columns are equal to those of this instance.
   */
  public boolean equals(Object o) {
    return o instanceof Index && compareTo((Index) o) == 0;
  }
      
  public String toString() {
    return "index[" + table + " " + columns + "]";
  }

  public boolean isPrimary() {
    // TODO: we *should* be able to use (this == table.primaryKey) here
    return equals(table.primaryKey);
  }
}
