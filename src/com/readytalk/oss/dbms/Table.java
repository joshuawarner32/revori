package com.readytalk.oss.dbms;

import java.util.List;

/**
 * Class representing a table.  Instances of this class do not hold
 * any data; they're used only to identify a collection of rows of
 * interest in a query or update.
 */
public final class Table implements Comparable<Table> {
  private static long nextId = 1;

  private synchronized static String makeId() {
    return (nextId++) + "." + Table.class.getName() + ".id";
  }

  /**
   * The primary key specified when this table was defined.
   */
  public final Index primaryKey;
  
  /**
   * The order, to provide an absolute order on tables independent of id
   */
  public final int order;

  /**
   * The ID specified when this table was defined.
   */
  public final String id;

  /**
   * Defines a table using the specified list of columns as the
   * primary key.<p>
   *
   * Instances of Table are considered equal if and only if their orders, IDs
   * and primary keys are equal.
   */
  public Table(List<Column> primaryKey, String id) {
    this.primaryKey = new Index(this, primaryKey);
    this.order = 0;
    this.id = id;

    if (id == null) throw new NullPointerException();
  }

  /**
   * Defines a table using the specified list of columns as the
   * primary key.<p>
   *
   * Instances of Table are considered equal if and only if their orders, IDs
   * and primary keys are equal.
   */
  public Table(List<Column> primaryKey, String id, int order) {
    this.primaryKey = new Index(this, primaryKey);
    this.order = order;
    this.id = id;

    if (id == null) throw new NullPointerException();
  }

  /**
   * Defines a table using the specified list of columns as the
   * primary key.  The order is initialized to be greater than the order
   * of any of the <code>comesAfter</code> tables.<p>
   *
   * Instances of Table are considered equal if and only if their orders, IDs
   * and primary keys are equal.
   */
  public Table(List<Column> primaryKey, String id, List<Table> comesAfter) {
    this.primaryKey = new Index(this, primaryKey);
    this.id = id;
    
    int o = 0;
    for(Table t : comesAfter) {
      if(t.order >= o) {
        o = t.order + 1;
      }
    }
    this.order = o;

    if (id == null) throw new NullPointerException();
  }

  /**
   * Defines a table using the specified primary key and an
   * automatically generated ID.<p>
   */
  public Table(List<Column> primaryKey) {
    this(primaryKey, makeId());
  }

  public int compareTo(Table o) {
    int d = order - o.order;
    if (d != 0) {
      return d;
    }
    d = id.compareTo(o.id);
    if (d != 0) {
      return d;
    }

    return primaryKey.compareColumns(o.primaryKey);
  }

  public int hashCode() {
    return id.hashCode();
  }

  /**
   * Returns true if and only if the specified object is a Table and
   * its ID and primaryKey are equal to those of this instance.
   */
  public boolean equals(Object o) {
    return o instanceof Table && compareTo((Table) o) == 0;
  }
      
  public String toString() {
    return "table[" + order + " " + id + " " + primaryKey.columns + "]";
  }
}
