/* Copyright (c) 2010-2012, Revori Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies. */

package com.readytalk.revori;

/**
 * Type used for incrementally defining a new revision by applying a
 * series of inserts, updates, and/or deletes to a base revision.
 */
public interface RevisionBuilder {
  /**
   * Applies the specified patch to this builder.<p>
   *
   * The patch is defined by visiting the nodes of the expression
   * trees referred to by the specified patch template and resolving
   * any parameter expressions found using the values of the specified
   * parameter array.  The expression tree nodes are visited
   * left-to-right in the order they where specified when the patch
   * template was defined.
   *
   * @return the number of rows affected by the patch
   *
   * @throws DuplicateKeyException if the specified patch introduces a
   * duplicate primary key
   *
   * @throws ClassCastException if an inserted or updated value cannot
   * be cast to the declared type of its column
   */
  public int apply(PatchTemplate template,
                   Object ... parameters)
    throws DuplicateKeyException,
           ClassCastException;
  
  /**
   * Deletes any rows matching the specified path.  This is equivalent
   * to delete(path, 0, path.length).
   */
  public RevisionBuilder delete(Object ... path);

  /**
   * Deletes any rows matching the specified path.  If pathLength is
   * zero, all rows in all tables are deleted.  If pathLength is
   * greater than or equal to one, path[pathOffset] is assumed to be a
   * Table, and the remainder (if any) are interpreted as primary key
   * values for that table.  A row is considered to match if its first
   * pathLength primary key values are equal to the elements of the
   * path delimited by pathOffset+1 and pathLength-1.
   */
  public RevisionBuilder delete(Object[] path,
                                int pathOffset,
                                int pathLength);

  /**
   * Inserts or updates a row containing the specified values.  This
   * is equivalent to insert(duplicateKeyResolution, path, 0,
   * path.length).
   */
  public RevisionBuilder insert(DuplicateKeyResolution duplicateKeyResolution,
                                Object ... path);

  /**
   * Inserts or updates a row containing the specified values.  The
   * path is interpreted as follows:<p>
   *
   * <ul><li>path[pathOffset]: the Table into which the row should be
   * inserted</li>
   *
   * <li>path[pathOffset+1..pathOffset+pathLength-3]: primary key values for
   * the specified table</li></ul><p>
   *
   * If pathLength is greater than the number of primary key values
   * plus one, the remaining two values are interpreted as follows:
   *
   * <ul><li>path[pathOffset+pathLength-2]: column to insert into or
   * update</li>
   *
   * <li>path[pathOffset+pathLength-1]: column value to insert or
   * update</li></ul><p>
   *
   * If there is already row with a matching primary key in the table,
   * this method will act according to the specified
   * DuplicateKeyResolution.
   */
  public RevisionBuilder insert(DuplicateKeyResolution duplicateKeyResolution,
                                Object[] path,
                                int pathOffset,
                                int pathLength);

  /**
   * Prepares a TableBuilder to update the given table.
   * @return said table builder
   */
  public TableBuilder table(Table table);

  /**
   * Adds the specified index to this builder.  This has no effect if
   * the index is already present.
   */
  public RevisionBuilder add(Index index);

  /**
   * Removes the specified index from this builder.  This has no
   * effect if the index is not found.
   *
   * @throws IllegalArgumentException if the specified index is a
   * primary key.
   */
  public RevisionBuilder remove(Index index);

  public RevisionBuilder add(View view);

  public RevisionBuilder remove(View view);

  /**
   * Adds the specified foreign key constraint to this builder.  This
   * has no effect if the constraint is already present.
   */
  public RevisionBuilder add(ForeignKey constraint);

  /**
   * Removes the specified foreign key constraint from this builder.
   * This has no effect if the constraint is not found.
   */
  public RevisionBuilder remove(ForeignKey constraint);

  /**
   * Identical to commit(ForeignKeyResolvers.Restrict). 
   */
  public Revision commit();

  /**
   * Commits the changes accumulated in this builder, producing a
   * revision which reflects the base revision with which was created
   * plus any modifications applied thereafter.
   *
   * Any foreign key violations present at the time of this call will
   * trigger calls to foreignKeyResolver.handleBrokenReference.
   */
  public Revision commit(ForeignKeyResolver foreignKeyResolver);
}
