/* Copyright (c) 2010-2012, Revori Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies. */

package com.readytalk.revori.imp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.readytalk.revori.Column;
import com.readytalk.revori.DiffResult;
import com.readytalk.revori.ForeignKey;
import com.readytalk.revori.ForeignKeyException;
import com.readytalk.revori.ForeignKeyResolver;
import com.readytalk.revori.Table;

class ForeignKeys {
  public static void checkForeignKeys(NodeStack baseStack,
                                      MyRevision base,
                                      NodeStack forkStack,
                                      MyRevisionBuilder builder,
                                      NodeStack scratchStack,
                                      ForeignKeyResolver resolver,
                                      @Nullable Table filter)
  {
    MyRevision r = null;
    while (r != builder.result) {
      r = builder.result;
      check(baseStack, base, forkStack, builder, scratchStack, resolver,
            filter);
    }
  }

  private static void check(NodeStack baseStack,
                            MyRevision base,
                            NodeStack forkStack,
                            final MyRevisionBuilder builder,
                            NodeStack scratchStack,
                            final ForeignKeyResolver resolver,
                            Table filter)
  {
    MyRevision fork = builder.result;

    // ensure fork remains unmodified as we iterate over it:
    builder.setToken(new Object());
        
    MyDiffResult result = new MyDiffResult
      (base, baseStack, fork, forkStack, false);

    int bottom = 0;
    int depth = 0;
    List<ReferentForeignKeyAdapter> referentKeyAdapters = null;
    List<RefererForeignKeyAdapter> refererKeyAdapters = null;
    ReferentForeignKeyAdapter.Visitor[] referentKeyVisitors = null;
    Object[] row = null;
    Table table = null;
    
    while (true) {
      DiffResult.Type type = result.next();
      switch (type) {
      case End:
        return;

      case Descend: {
        ++ depth;
      } break;

      case Ascend: {
        -- depth;
      } break;

      case Key: {
        if (depth == 0) {
          Table baseTable = (Table) result.base();
          Table forkTable = (Table) result.fork();
          
          if (baseTable != null) {
            table = baseTable;
            bottom = baseTable.primaryKey.columns.size();
            referentKeyAdapters = getReferentForeignKeyAdapters
              (baseTable, fork.root, scratchStack);
            int size = referentKeyAdapters.size();
            if (size != 0) {
              referentKeyVisitors = new ReferentForeignKeyAdapter.Visitor
                [size];
              int i = 0;
              for (final ReferentForeignKeyAdapter adapter:
                     referentKeyAdapters)
              {
                referentKeyVisitors[i++]
                  = new ReferentForeignKeyAdapter.Visitor() {
                      public void visit(Object[] row) {
                        handleBrokenReference
                          (resolver, builder, adapter.constraint, row);
                      }
                    };
              }
            }
          } else {
            referentKeyAdapters = Collections.emptyList();
            referentKeyVisitors = null;
          }

          if (forkTable != null) {
            table = forkTable;
            bottom = forkTable.primaryKey.columns.size();
            refererKeyAdapters = getRefererForeignKeyAdapters
              (forkTable, fork.root, scratchStack);
          } else {
            refererKeyAdapters = Collections.emptyList();
          }

          if ((filter != null && ! filter.equals(table))
              || (forkTable != Constants.ForeignKeyTable
                  && referentKeyAdapters.isEmpty()
                  && refererKeyAdapters.isEmpty()))
          {
            result.skip();
          }
        } else if (depth == bottom) {
          Node baseTree = result.baseTree();
          Node forkTree = result.forkTree();

          if (baseTree != Node.Null) {
            int i = 0;
            for (ReferentForeignKeyAdapter adapter: referentKeyAdapters) {
              adapter.visitBrokenReferences
                (fork, baseTree, referentKeyVisitors[i++]);
            }
          }

          if (forkTree != Node.Null) {
            if (table == Constants.ForeignKeyTable) {
              // a new foreign key has been added, so we need to look
              // at all the rows in the referer table, not just the
              // new and updated rows

              ForeignKey constraint = (ForeignKey)
                Node.find(forkTree, Constants.ForeignKeyColumn,
                          Compare.ColumnComparator).value;

              checkForeignKeys
                (new NodeStack(), MyRevision.Empty,
                 new NodeStack(), builder,
                 new NodeStack(), resolver, constraint.refererTable);
            } else {
              for (RefererForeignKeyAdapter adapter: refererKeyAdapters) {
                if (adapter.isBrokenReference(fork, forkTree)) {
                  Table referer = adapter.constraint.refererTable;
                  int count = referer.primaryKey.columns.size();

                  if (row == null || row.length != count) {
                    row = new Object[count];
                  }

                  fillRow(row, referer.primaryKey.columns, forkTree);

                  handleBrokenReference
                    (resolver, builder, adapter.constraint, row);
                }
              }
            }
          }

          result.skip();
        }
      } break;

      default:
        throw new RuntimeException("unexpected result type: " + type);
      }
    }
  }

  static List<RefererForeignKeyAdapter> getRefererForeignKeyAdapters
    (Table table, Node root, NodeStack stack)
  {
    List<RefererForeignKeyAdapter> list = new ArrayList<RefererForeignKeyAdapter>();

    for (NodeIterator keys = new NodeIterator
           (stack, Node.pathFind
            (root, Constants.ForeignKeyTable, Compare.TableComparator,
             Constants.ForeignKeyRefererIndex, Compare.IndexComparator,
             table, Constants.ForeignKeyRefererColumn.comparator));
         keys.hasNext();)
    {
      list.add(new RefererForeignKeyAdapter((ForeignKey) keys.next().key));
    }

    return list;
  }

  static List<ReferentForeignKeyAdapter> getReferentForeignKeyAdapters
    (Table table, Node root, NodeStack stack)
  {
    List<ReferentForeignKeyAdapter> list = new ArrayList<ReferentForeignKeyAdapter>();

    for (NodeIterator keys = new NodeIterator
           (stack, Node.pathFind
            (root, Constants.ForeignKeyTable, Compare.TableComparator,
             Constants.ForeignKeyReferentIndex, Compare.IndexComparator,
             table, Constants.ForeignKeyReferentColumn.comparator));
         keys.hasNext();)
    {
      list.add(new ReferentForeignKeyAdapter((ForeignKey) keys.next().key));
    }

    return list;
  }

  private static void fillRow(Object[] row, List<Column<?>> columns, Node tree)
  {
    for (int i = 0; i < row.length; ++i) {
      Node n = Node.find(tree, columns.get(i), Compare.ColumnComparator);
      row[i] = n == Node.Null ? null : n.value;
    }
  }

  private static void handleBrokenReference(ForeignKeyResolver resolver,
                                            MyRevisionBuilder builder,
                                            ForeignKey constraint,
                                            Object[] row)
  {
    ForeignKeyResolver.Action action = resolver.handleBrokenReference
      (constraint, row);
                
    switch (action) {
    case Delete: {
      Object[] path = new Object[row.length + 1];
      path[0] = constraint.refererTable;
      System.arraycopy(row, 0, path, 1, row.length);
      // System.err.println("delete " + java.util.Arrays.toString(path));
      builder.delete(path);
    } break;

    case Restrict:
      throw new ForeignKeyException
        ("broken reference from " + constraint.refererTable
         + " " + constraint.refererColumns
         + " " + java.util.Arrays.toString(row)
         + " to " + constraint.referentTable
         + " " + constraint.referentColumns);

    default:
      throw new RuntimeException("unexpected action: " + action);
    }    
  }
}
