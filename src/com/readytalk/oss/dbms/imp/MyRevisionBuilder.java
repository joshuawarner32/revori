package com.readytalk.oss.dbms.imp;

import static com.readytalk.oss.dbms.util.Util.expect;
import static com.readytalk.oss.dbms.util.Util.list;
import static com.readytalk.oss.dbms.util.Util.copy;

import static com.readytalk.oss.dbms.SourceFactory.reference;
import com.readytalk.oss.dbms.RevisionBuilder;
import com.readytalk.oss.dbms.TableBuilder;
import com.readytalk.oss.dbms.RowBuilder;
import com.readytalk.oss.dbms.Index;
import com.readytalk.oss.dbms.View;
import com.readytalk.oss.dbms.Table;
import com.readytalk.oss.dbms.Column;
import com.readytalk.oss.dbms.Revision;
import com.readytalk.oss.dbms.DuplicateKeyResolution;
import com.readytalk.oss.dbms.DuplicateKeyException;
import com.readytalk.oss.dbms.PatchTemplate;
import com.readytalk.oss.dbms.TableReference;
import com.readytalk.oss.dbms.QueryResult;
import com.readytalk.oss.dbms.UpdateTemplate;
import com.readytalk.oss.dbms.InsertTemplate;
import com.readytalk.oss.dbms.DeleteTemplate;
import com.readytalk.oss.dbms.ColumnList;
import com.readytalk.oss.dbms.ForeignKey;
import com.readytalk.oss.dbms.ForeignKeyResolver;
import com.readytalk.oss.dbms.ForeignKeyResolvers;
import com.readytalk.oss.dbms.SourceVisitor;
import com.readytalk.oss.dbms.Source;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

class MyRevisionBuilder implements RevisionBuilder {
  private static final Map<Class, PatchTemplateAdapter> adapters
    = new HashMap<Class, PatchTemplateAdapter>();

  static {
    adapters.put(UpdateTemplate.class, new UpdateTemplateAdapter());
    adapters.put(InsertTemplate.class, new InsertTemplateAdapter());
    adapters.put(DeleteTemplate.class, new DeleteTemplateAdapter());
  }

  public Object token;
  public final NodeStack stack;
  public final Comparable[] keys;
  public final Node[] blazedRoots;
  public final Node[] blazedLeaves;
  public final Node[] found;
  public final Node.BlazeResult blazeResult = new Node.BlazeResult();
  public NodeStack indexUpdateIterateStack;
  public NodeStack indexUpdateBaseStack;
  public NodeStack indexUpdateForkStack;
  public MyRevision base;
  public MyRevision indexBase;
  public MyRevision result;
  public int max = -1;
  public boolean dirtyIndexes;

  public MyRevisionBuilder(Object token,
                           MyRevision base,
                           NodeStack stack)
  {
    this.token = token;
    this.base = base;
    this.indexBase = base;
    this.result = base;
    this.stack = stack;
    keys = new Comparable[Constants.MaxDepth + 1];
    blazedRoots = new Node[Constants.MaxDepth + 1];
    blazedLeaves = new Node[Constants.MaxDepth + 1];
    found = new Node[Constants.MaxDepth + 1];
  }

  public void setToken(Object token) {
    if (token != this.token) {
      this.token = token;
      for (int i = 0; i < max; ++i) {
        found[i] = null;
        blazedLeaves[i] = null;
        blazedRoots[i + 1] = null;
      }
    }
  }

  public void setKey(int index, Comparable key) {
    if (max < index || ! Compare.equal(key, keys[index])) {
      max = index;
      keys[index] = key;
      found[index] = null;
      blazedLeaves[index] = null;
      blazedRoots[index + 1] = null;
    }
  }

  public Node blaze(int index, Comparable key) {
    setKey(index, key);
    return blaze(index);
  }

  public void insertOrUpdate(int index, Comparable key, Object value) {
    blaze(index, key).value = value;
  }

  public void delete(int index, Comparable key) {
    setKey(index, key);
    delete(index);
  }

  public void deleteAll() {
    result = MyRevision.Empty;
    max = -1;
  }

  private void delete(int index) {
    Node root = blazedRoots[index];
    if (root == null) {
      if (index == 0) {
        root = Node.delete(token, stack, result.root, keys[0]);

        if (root != result.root) {
          result = getRevision(token, result, root);
        }
      } else {
        Node original = find(index);
        Node originalRoot = (Node) find(index - 1).value;

        if (original == Node.Null) {
          return;
        } else if (original == originalRoot
                   && original.left == Node.Null
                   && original.right == Node.Null)
        {
          delete(index - 1);
        } else {
          root = Node.delete
            (token, stack, (Node) blaze(index - 1).value, keys[index]);
          blazedLeaves[index - 1].value = root;
          blazedRoots[index] = root;
          blazedLeaves[index] = null;
        }
      }
    } else {
      deleteBlazed(index);
    }

    if (max >= index) {
      max = index - 1;
    }
  }

  private Node find(int index) {
    Node n = blazedLeaves[index];
    if (n == null) {
      n = found[index];
      if (n == null) {
        if (index == 0) {
          n = Node.find(result.root, keys[0]);
          found[0] = n;
        } else {
          n = Node.find((Node) find(index - 1).value, keys[index]);
          found[index] = n;
        }
      }
    }
    return n;
  }

  private void deleteBlazed(int index) {
    blazedLeaves[index] = null;
    Node root = Node.delete(token, stack, blazedRoots[index], keys[index]);
    blazedRoots[index] = root;
    blazedLeaves[index] = null;
    if (root == null) {
      if (index == 0) {
        result.root = Node.delete(token, stack, result.root, keys[0]);
      } else {
        deleteBlazed(index - 1);
      }
    } else {
      if (index == 0) {
        result.root = root;
      } else {
        blazedLeaves[index - 1].value = root;
      }
    }
  }

  private Node blaze(int index) {
    Node n = blazedLeaves[index];
    if (n == null) {
      if (index == 0) {
        Node root = Node.blaze
          (blazeResult, token, stack, result.root, keys[0]);

        if (root != result.root) {
          result = getRevision(token, result, root);
        }

        blazedRoots[0] = root;
        blazedLeaves[0] = blazeResult.node;
        blazedRoots[1] = (Node) blazeResult.node.value;
        return blazeResult.node;
      } else {
        Node root = Node.blaze
          (blazeResult, token, stack, (Node) blaze(index - 1).value,
           keys[index]);

        blazedLeaves[index - 1].value = root;
        blazedRoots[index] = root;
        blazedLeaves[index] = blazeResult.node;
        return blazeResult.node;
      }
    } else {
      return n;
    }
  }

  public void updateIndexTree(Index index,
                              MyRevision base,
                              NodeStack baseStack,
                              NodeStack forkStack)
  {
    expect(! index.equals(index.table.primaryKey));

    TableIterator iterator
      = new TableIterator
      (reference(index.table), base, baseStack, result, forkStack,
       ConstantAdapter.True, new ExpressionContext(null), false);

    setKey(Constants.TableDataDepth, index.table);
    setKey(Constants.IndexDataDepth, index);

    List<Column<?>> keyColumns = index.columns;

    while (true) {
      QueryResult.Type type = iterator.nextRow();
      switch (type) {
      case End:
        return;
      
      case Inserted: {
        Node tree = (Node) iterator.pair.fork.value;

        int i = 0;
        for (; i < keyColumns.size() - 1; ++i) {
          setKey
            (i + Constants.IndexDataBodyDepth,
             (Comparable) Node.find(tree, keyColumns.get(i)).value);
        }

        Node n = blaze
          (i + Constants.IndexDataBodyDepth,
           (Comparable) Node.find(tree, keyColumns.get(i)).value);

        expect(n.value == Node.Null);
      
        n.value = tree;
      } break;

      case Deleted: {
        Node tree = (Node) iterator.pair.base.value;

        int i = 0;
        for (; i < keyColumns.size() - 1; ++i) {
          setKey
            (i + Constants.IndexDataBodyDepth,
             (Comparable) Node.find(tree, keyColumns.get(i)).value);
        }

        delete
          (i + Constants.IndexDataBodyDepth,
           (Comparable) Node.find(tree, keyColumns.get(i)).value);            
      } break;
      
      default:
        throw new RuntimeException("unexpected result type: " + type);
      }
    }
  }

  private Node makeTree(NodeStack stack,
                        List<Column<?>> columns,
                        List<ExpressionAdapter> expressions)
  {
    Node.BlazeResult result = new Node.BlazeResult();
    Node n = Node.Null;
    for (int i = 0; i < columns.size(); ++i) {
      Column<?> c = columns.get(i);
      Object v = expressions.get(i).evaluate(true);
      if (v != null && ! c.type.isInstance(v)) {
        throw new ClassCastException
          (v.getClass().getName() + " cannot be cast to " + c.type.getName());
      }
      n = Node.blaze(result, token, stack, n, c);
      result.node.value = v;
    }
    return n;
  }

  public void updateViewTree(View view,
                             MyRevision base,
                             NodeStack baseStack,
                             NodeStack forkStack)
  {
    MyQueryResult qr = new MyQueryResult
      (base, baseStack, result, forkStack, view.query,
       view.parameters.toArray(new Object[view.parameters.size()]));

    setKey(Constants.TableDataDepth, view.table);
    setKey(Constants.IndexDataDepth, view.table.primaryKey);

    List<Column<?>> keyColumns = view.table.primaryKey.columns;

    final List<ExpressionAdapter> expressions = qr.expressions;

    List<AggregateAdapter> aggregates;
    int maxValues;
    if (view.query.hasAggregates) {
      aggregates = new ArrayList<AggregateAdapter>();

      maxValues = 0;
      for (int i = view.aggregateOffset; i < view.aggregateExpressionOffset;
           ++i)
      {
        AggregateAdapter aa = (AggregateAdapter) expressions.get(i);
        if (maxValues < aa.aggregate.expressions.size()) {
          maxValues = aa.aggregate.expressions.size();
        }
        aggregates.add(aa);
      }
    } else {
      maxValues = 0;
      aggregates = null;
    }

    Object[] values = new Object[maxValues];
    NodeStack stack = new NodeStack();

    boolean done = false;
    while (! done) {
      QueryResult.Type type = qr.nextRow();
      switch (type) {
      case End:
        done = true;
        break;
      
      case Inserted: {
        int i = 0;
        for (; i < keyColumns.size() - 1; ++i) {
          setKey
            (i + Constants.IndexDataBodyDepth,
             (Comparable) expressions.get
             (view.primaryKeyOffset + i).evaluate(true));
        }

        Node n = blaze
          (i + Constants.IndexDataBodyDepth,
           (Comparable) expressions.get
           (view.primaryKeyOffset + i).evaluate(true));

        // System.out.println("inserted " + Util.toString(keys, 0, Constants.IndexDataBodyDepth + i + 1));

        if (view.query.hasAggregates) {
          int columnOffset = view.aggregateOffset;
          int expressionOffset = view.aggregateExpressionOffset;
          for (AggregateAdapter a: aggregates) {
            for (int j = 0; j < a.aggregate.expressions.size(); ++j) {
              values[j] = expressions.get(expressionOffset++).evaluate(true);
            }

            a.add
              (Node.find
               ((Node) n.value, view.columns.get(columnOffset++)).value,
               values);
          }
        } else {
          expect(n.value == Node.Null);
        }
      
        n.value = makeTree(stack, view.columns, expressions);

        for (AggregateAdapter a: aggregates) {
          a.value = Compare.Undefined;
        }
      } break;

      case Deleted: {
        for (int i = 0; i < keyColumns.size(); ++i) {
          setKey
            (i + Constants.IndexDataBodyDepth,
             (Comparable) expressions.get
             (view.primaryKeyOffset + i).evaluate(true));
        }
        int index = keyColumns.size() - 1 + Constants.IndexDataBodyDepth;

        // System.out.println("deleted " + Util.toString(keys, 0, index + 1));

        if (view.query.hasAggregates) {
          Node n = find(index);

          int columnOffset = view.aggregateOffset;
          int expressionOffset = view.aggregateOffset;
          for (AggregateAdapter a: aggregates) {
            for (int j = 0; j < a.aggregate.expressions.size(); ++j) {
              values[j] = expressions.get(expressionOffset++).evaluate(true);
            }
            a.subtract
              (Node.find
               ((Node) n.value, view.columns.get(columnOffset++)).value,
               values);
          }
        }

        if (view.query.hasAggregates
            && ((Integer) expressions.get(view.aggregateOffset).evaluate(true))
            != 0)
        {
          blaze(index).value = makeTree(stack, view.columns, expressions);
        } else {
          delete(index);
        }

        for (AggregateAdapter a: aggregates) {
          a.value = Compare.Undefined;
        }
      } break;
      
      default:
        throw new RuntimeException("unexpected result type: " + type);
      }
    }

    // now, filter out rows which fail the query test if that test
    // uses an aggregate function
    if (view.query.hasAggregates) {
      // todo: only do this if the query test has aggregates

      qr.reset();

      while (true) {
        QueryResult.Type type = qr.nextRow();
        switch (type) {
        case End:
          return;

        case Inserted:
        case Deleted: {
          for (int i = 0; i < keyColumns.size(); ++i) {
            setKey
              (i + Constants.IndexDataBodyDepth,
               (Comparable) expressions.get
               (view.primaryKeyOffset + i).evaluate(true));
          }

          int index = keyColumns.size() - 1 + Constants.IndexDataBodyDepth;

          int columnOffset = view.aggregateOffset;
          for (AggregateAdapter a: aggregates) {
            a.value = Node.find
              ((Node) find(index).value,
               view.columns.get(columnOffset++)).value;
          }

          // System.out.print("filter: ");
          // for (ColumnReferenceAdapter r: qr.expressionContext.columnReferences)
          // {
          //   System.out.print(r.column + ":" + r.value + " ");
          // }
          // System.out.println(": " + qr.test.evaluate(false));

          if (qr.test.evaluate(false) == Boolean.FALSE) {
            // todo: rather than delete this row from the view we
            // should actually just undo whatever operation we did in
            // the first pass, now that we know this query row should
            // not be included.  Note that we'll need to operate on a
            // copy of the view rather than the tentative one we just
            // built to make this work.

            delete(index);
          }

          for (AggregateAdapter a: aggregates) {
            a.value = Compare.Undefined;
          }
        } break;
      
        default:
          throw new RuntimeException("unexpected result type: " + type);
        }
      }
    }
  }

  private void checkStacks() {
    if (indexUpdateIterateStack == null) {
      indexUpdateIterateStack = new NodeStack();
      indexUpdateBaseStack = new NodeStack();
      indexUpdateForkStack = new NodeStack();
    }
  }

  private void updateIndexes() {
    if (dirtyIndexes && indexBase != result) {
      checkStacks();

      DiffIterator iterator = new DiffIterator
        (indexBase.root, indexUpdateBaseStack,
         result.root, indexUpdateForkStack,
         list(Interval.Unbounded).iterator(), false);

      DiffIterator.DiffPair pair = new DiffIterator.DiffPair();

      Set<View> viewSet = new HashSet();
      while (iterator.next(pair)) {
        if (pair.fork != null) {
          for (NodeIterator indexes = new NodeIterator
                 (indexUpdateIterateStack, Node.pathFind
                  (result.root, Constants.IndexTable,
                   Constants.IndexTable.primaryKey, (Table) pair.fork.key));
               indexes.hasNext();)
          {
            updateIndexTree
              ((Index) indexes.next().key, indexBase,
               indexUpdateBaseStack, indexUpdateForkStack);
          }

          for (NodeIterator views = new NodeIterator
                 (indexUpdateIterateStack, Node.pathFind
                  (result.root, Constants.ViewTable,
                   Constants.ViewTable.primaryKey, (Table) pair.fork.key));
               views.hasNext();)
          {
            viewSet.add((View) views.next().key);
          }
        }
      }

      for (View v: viewSet) {
        updateViewTree
          (v, indexBase, indexUpdateBaseStack, indexUpdateForkStack);
      }
    }

    dirtyIndexes = false;
    indexBase = result;
  }

  public void updateIndex(Index index) {
    if (! Compare.equal(index.table.primaryKey, index)) {
      updateIndexes();
    }
  }

  private void checkForeignKeys(ForeignKeyResolver resolver) {
    // todo: is there a performance problem with creating new
    // NodeStacks every time this method is called?  If so, are there
    // common cases were we can avoid creating them, or should we try
    // to recycle them somehow?

    ForeignKeys.checkForeignKeys
      (new NodeStack(), base, new NodeStack(), this, new NodeStack(),
       resolver, null);
  }

  public void prepareForUpdate(Table table) {
    // since we update non-primary-key indexes lazily, we may need to
    // freeze a copy of the last revision which contained up-to-date
    // indexes so we can do a diff later and use it to update them

    if (Node.pathFind(result.root, Constants.IndexTable,
                      Constants.IndexTable.primaryKey, table) != Node.Null
        || Node.pathFind(result.root, Constants.ViewTable,
                         Constants.ViewTable.primaryKey, table) != Node.Null)
    {
      dirtyIndexes = true;

      if (indexBase == result) {
        setToken(new Object());
      }
    }
  }

  private void pathInsert(Table table, Comparable ... path) {
    setKey(Constants.TableDataDepth, table);
    setKey(Constants.IndexDataDepth, table.primaryKey);

    Node tree = Node.Null;
    Node.BlazeResult result = new Node.BlazeResult();
    List<Column<?>> columns = table.primaryKey.columns;
    for (int i = 0; i < columns.size(); ++i) {
      tree = Node.blaze(result, token, stack, tree, columns.get(i));
      result.node.value = path[i];

      if (i == columns.size() - 1) {
        insertOrUpdate(Constants.IndexDataBodyDepth + i, path[i], tree);
      } else {
        setKey(Constants.IndexDataBodyDepth + i, path[i]);
      }
    }
  }

  private void pathDelete(Table table, Comparable ... path) {
    setKey(Constants.TableDataDepth, table);
    setKey(Constants.IndexDataDepth, table.primaryKey);

    List<Column<?>> columns = table.primaryKey.columns;
    for (int i = 0; i < columns.size(); ++i) {
      if (i == columns.size() - 1) {
        delete(Constants.IndexDataBodyDepth + i, path[i]);
      } else {
        setKey(Constants.IndexDataBodyDepth + i, path[i]);
      }
    }
  }

  private void addIndex(Index index)
  {
    if (index.equals(index.table.primaryKey)
        || Node.pathFind
        (result.root, Constants.IndexTable, Constants.IndexTable.primaryKey,
         index.table, index) != Node.Null)
    {
      // the specified index is already present -- ignore
      return;
    }

    // flush any changes out to the existing indexes, since we don't
    // want to get confused later when some indexes are up-to-date and
    // some aren't:
    updateIndexes();

    checkStacks();

    updateIndexTree
      (index, MyRevision.Empty, indexUpdateBaseStack, indexUpdateForkStack);

    pathInsert(Constants.IndexTable, index.table, index);
  }

  private void removeIndex(Index index)
  {
    if (index.equals(index.table.primaryKey)) {
      throw new IllegalArgumentException("cannot remove primary key");
    }

    pathDelete(Constants.IndexTable, index.table, index);

    setKey(Constants.TableDataDepth, index.table);
    delete(Constants.IndexDataDepth, index);
  }

  private void addView(View view)
  {
    addView(view, MyRevision.Empty);
  }

  void addView(final View view, MyRevision base)
  {
    view.query.source.visit(new SourceVisitor() {
        public void visit(Source source) {
          if (source instanceof TableReference) {
            Table table = ((TableReference) source).table;

            if (Node.pathFind
                (result.root, Constants.ViewTable,
                 Constants.ViewTable.primaryKey, table, view) != Node.Null)
            {
              // the specified view is already present -- ignore
              return;
            }

            pathInsert(Constants.ViewTable, table, view);
          }
        }
      });

    // flush any changes out to the existing indexes, since we don't
    // want to get confused later when some indexes are up-to-date and
    // some aren't:
    updateIndexes();

    checkStacks();

    updateViewTree
      (view, base, indexUpdateBaseStack, indexUpdateForkStack);
  }

  private void removeView(final View view)
  {
    view.query.source.visit(new SourceVisitor() {
        public void visit(Source source) {
          if (source instanceof TableReference) {
            Table table = ((TableReference) source).table;

            pathDelete(Constants.ViewTable, table, view);
          }
        }
      });

    delete(Constants.TableDataDepth, view.table);
  }

  private void addForeignKey(ForeignKey constraint)
  {
    if (Node.pathFind
        (result.root, Constants.ForeignKeyTable,
         Constants.ForeignKeyTable.primaryKey, constraint) != Node.Null)
    {
      // the specified foreign key is already present -- ignore
      return;
    }

    insert(DuplicateKeyResolution.Throw, Constants.ForeignKeyTable, constraint,
           Constants.ForeignKeyRefererColumn, constraint.refererTable);

    insert(DuplicateKeyResolution.Throw, Constants.ForeignKeyTable, constraint,
           Constants.ForeignKeyReferentColumn, constraint.referentTable);

    add(Constants.ForeignKeyRefererIndex);
    add(Constants.ForeignKeyReferentIndex);

    dirtyIndexes = true;
    updateIndexes();
  }

  private void removeForeignKey(ForeignKey constraint)
  {
    pathDelete(Constants.ForeignKeyTable, constraint);

    if (Node.pathFind(result.root, Constants.ForeignKeyTable) == Node.Null) {
      // the last foreign key constraint has been removed -- remove
      // the indexes

      remove(Constants.ForeignKeyRefererIndex);
      remove(Constants.ForeignKeyReferentIndex);
    } else {
      dirtyIndexes = true;
      updateIndexes();
    }
  }

  private void delete(Comparable[] keys)
  {
    if (keys.length == 0) {
      deleteAll();
      return;
    }

    Table table = (Table) keys[0];

    if (keys.length == 1) {
      delete(Constants.TableDataDepth, table);
      return;
    }

    prepareForUpdate(table);

    setKey(Constants.TableDataDepth, table);
    setKey(Constants.IndexDataDepth, table.primaryKey);

    int i = 1;
    for (; i < keys.length - 1; ++i) {
      setKey(i - 1 + Constants.IndexDataBodyDepth, keys[i]);
    }

    delete(i - 1 + Constants.IndexDataBodyDepth, keys[i]);
  }

  private void insert(int depth,
                      List<Column<?>> columns,
                      Comparable[] path)
  {
    for (int i = 0; i < path.length; ++i) {
      blaze(depth, columns.get(i)).value = path[i];
    }
  }

  private void insert(DuplicateKeyResolution duplicateKeyResolution,
                      Table table,
                      Column<?> column,
                      Object value,
                      Comparable[] path)
  {
    prepareForUpdate(table);

    setKey(Constants.TableDataDepth, table);
    setKey(Constants.IndexDataDepth, table.primaryKey);

    for (int i = 0; i < path.length; ++i) {
      setKey(i + Constants.IndexDataBodyDepth, path[i]);
    }

    Node n;
    if (column == null) {
      n = blaze((path.length - 1) + Constants.IndexDataBodyDepth);
    } else {
      n = blaze(path.length + Constants.IndexDataBodyDepth, column);
    }

    if (n.value == Node.Null) {
      if (column != null) {
        n.value = value;
      }
      insert(path.length + Constants.IndexDataBodyDepth,
             table.primaryKey.columns, path);
    } else {
      switch (duplicateKeyResolution) {
      case Skip:
        break;

      case Overwrite:
        if (column != null) {
          n.value = value;
        }
        insert(path.length + Constants.IndexDataBodyDepth,
               table.primaryKey.columns, path);
        break;

      case Throw:
        throw new DuplicateKeyException();

      default:
        throw new RuntimeException
          ("unexpected resolution: " + duplicateKeyResolution);
      }
    }
  }

  private class MyTableBuilder implements TableBuilder {
    private class MyRowBuilder implements RowBuilder {
      private Object[] path;

      public MyRowBuilder() {
        path = new Object[3 + table.primaryKey.columns.size()];
        path[0] = table;
      }

      public void init(Comparable[] keys) {
        for(int i = 0; i < keys.length; i++) {
          path[i + 1] = keys[i];
        }
      }
      
      public <T> RowBuilder column(Column<T> key,
                               T value)
      {
        path[path.length - 2] = key;
        path[path.length - 1] = value;
        insert(DuplicateKeyResolution.Overwrite, path);
        return this;
      }
      
      public RowBuilder columns(ColumnList columns,
                                Object ... values)
      {
        // TODO: optimize
        if(columns.columns.size() != values.length) {
          throw new IllegalArgumentException
            ("wrong number of parameters (expected "
             + columns.columns.size() + "; got "
             + values.length + ")");
        }
        for(int i = 0; i < values.length; i++) {
          column((Column)columns.columns.get(i), values[i]);
        }
        return this;
      }

      public RowBuilder delete(Column<?> key) {
        throw new RuntimeException("not implemented");
        //return this;
      }

      public TableBuilder up() {
        MyTableBuilder.this.row = this;
        return MyTableBuilder.this;
      }
    }

    private Table table;
    public MyRowBuilder row;

    public MyTableBuilder(Table table) {
      this.table = table;
    }

    public RowBuilder row(Comparable ... key) {
      if(row != null) {
        row.init(key);
        MyRowBuilder ret = row;
        row = null;
        return ret;
      }
      MyRowBuilder ret = new MyRowBuilder();
      ret.init(key);
      return ret;
    }

    public TableBuilder key(Comparable ... key) {
      MyRowBuilder row = (MyRowBuilder) row(key);
      insert(DuplicateKeyResolution.Overwrite, row.path, 0, key.length + 1);
      return row.up(); // identically, return this (but up() can recycle the RowBuilder)
    }

    public TableBuilder delete(Comparable ... key) {
      return this;
    }

    public RevisionBuilder up() {
      return MyRevisionBuilder.this;
    }
  }



  public TableBuilder table(Table table)
  {
    return new MyTableBuilder(table);
  }

  public void drop(Table table) {
    
  }

  public int apply(PatchTemplate template,
                   Object ... parameters)
  {
    if (token == null) {
      throw new IllegalStateException("builder already committed");
    }

    try {
      if (parameters.length != template.parameterCount()) {
        throw new IllegalArgumentException
          ("wrong number of parameters (expected "
           + template.parameterCount() + "; got "
           + parameters.length + ")");
      }

      return adapters.get
        (template.getClass()).apply(this, template, copy(parameters));
    } catch (RuntimeException e) {
      token = null;
      throw e;
    }
  }

  public void delete(Object[] path,
                     int pathOffset,
                     int pathLength)
  {
    Comparable[] myPath = new Comparable[pathLength];
    for (int i = 0; i < pathLength; ++i) {
      myPath[i] = (Comparable) path[pathOffset + i];
    }
    
    delete(myPath);
  }

  public void delete(Object ... path)
  {
    delete(path, 0, path.length);
  }

  public void insert(DuplicateKeyResolution duplicateKeyResolution,
                     Object[] path,
                     int pathOffset,
                     int pathLength)
  {
    Table table;
    try {
      table = (Table) path[pathOffset];
    } catch (ClassCastException e) {
      throw new IllegalArgumentException
        ("expected table as first path element");        
    }

    List<Column<?>> columns = table.primaryKey.columns;

    if (pathLength == columns.size() + 1) {
      Comparable[] myPath = new Comparable[columns.size()];
      for (int i = 0; i < myPath.length; ++i) {
        myPath[i] = (Comparable) path[pathOffset + i + 1];
      }

      insert(duplicateKeyResolution, table, null, null, myPath);
    } else if (pathLength == columns.size() + 3) {
      Column<?> column;
      try {
        column = (Column<?>) path[pathOffset + columns.size() + 1];
      } catch (ClassCastException e) {
        throw new IllegalArgumentException
          ("expected column as second-to-last path element");        
      }

      Object value = path[pathOffset + columns.size() + 2];
      if (value != null && ! column.type.isInstance(value)) {
        throw new ClassCastException
          (value.getClass() + " cannot be cast to " + column.type);
      }

      Comparable[] myPath = new Comparable[columns.size()];
      for (int i = 0; i < myPath.length; ++i) {
        Comparable c = (Comparable) path[pathOffset + i + 1];
        if (columns.get(i) == column) {
          throw new IllegalArgumentException
            ("cannot use insert to update a primary key column");        
        }
        myPath[i] = c;
      }

      insert(duplicateKeyResolution, table, column, value, myPath);
    } else {
      throw new IllegalArgumentException
        ("wrong number of parameters for primary key");
    }

  }

  public void insert(DuplicateKeyResolution duplicateKeyResolution,
                     Object ... path)
  {
    insert(duplicateKeyResolution, path, 0, path.length);
  }

  public void add(Index index)
  {
    if (token == null) {
      throw new IllegalStateException("builder already committed");
    }

    try {
      addIndex(index);
    } catch (RuntimeException e) {
      token = null;
      throw e;
    }
  }

  public void remove(Index index)
  {
    if (token == null) {
      throw new IllegalStateException("builder already committed");
    }

    try {
      removeIndex(index);
    } catch (RuntimeException e) {
      token = null;
      throw e;
    }
  }

  public void add(View view)
  {
    if (token == null) {
      throw new IllegalStateException("builder already committed");
    }

    try {
      addView(view);
    } catch (RuntimeException e) {
      token = null;
      throw e;
    }
  }

  public void remove(View view)
  {
    if (token == null) {
      throw new IllegalStateException("builder already committed");
    }

    try {
      removeView(view);
    } catch (RuntimeException e) {
      token = null;
      throw e;
    }
  }

  public void add(ForeignKey constraint)
  {
    if (token == null) {
      throw new IllegalStateException("builder already committed");
    }

    try {
      addForeignKey(constraint);
    } catch (RuntimeException e) {
      token = null;
      throw e;
    }
  }

  public void remove(ForeignKey constraint)
  {
    if (token == null) {
      throw new IllegalStateException("builder already committed");
    }

    try {
      removeForeignKey(constraint);
    } catch (RuntimeException e) {
      token = null;
      throw e;
    }
  }

  public boolean committed() {
    return token != null;
  }

  public Revision commit() {
    return commit(ForeignKeyResolvers.Restrict);
  }

  public Revision commit(ForeignKeyResolver foreignKeyResolver) {
    if (token == null) {
      return result;
    }

    updateIndexes();

    checkForeignKeys(foreignKeyResolver);

    token = null;

    return result;
  }

  private static MyRevision getRevision(Object token,
                                        MyRevision basis,
                                        Node root)
  {
    if (token == basis.token) {
      basis.root = root;
      return basis;
    } else {
      return new MyRevision(token, root);
    }
  }
}
