package unittests;

import junit.framework.TestCase;

import org.junit.Test;

import static com.readytalk.oss.dbms.util.Util.list;
import static org.junit.Assert.*;

import com.readytalk.oss.dbms.DBMS;
import com.readytalk.oss.dbms.BinaryOperation;
import com.readytalk.oss.dbms.Column;
import com.readytalk.oss.dbms.ConflictResolver;
import com.readytalk.oss.dbms.Table;
import com.readytalk.oss.dbms.Expression;
import com.readytalk.oss.dbms.Revision;
import com.readytalk.oss.dbms.RevisionBuilder;
import com.readytalk.oss.dbms.PatchTemplate;
import com.readytalk.oss.dbms.InsertTemplate;
import com.readytalk.oss.dbms.UpdateTemplate;
import com.readytalk.oss.dbms.DeleteTemplate;
import com.readytalk.oss.dbms.TableReference;
import com.readytalk.oss.dbms.Parameter;
import com.readytalk.oss.dbms.Constant;
import com.readytalk.oss.dbms.ColumnReference;
import com.readytalk.oss.dbms.QueryTemplate;
import com.readytalk.oss.dbms.QueryResult;
import com.readytalk.oss.dbms.DuplicateKeyResolution;
import com.readytalk.oss.dbms.imp.MyDBMS;

public class MergeTest extends TestCase{
    
    @Test
    public void testMerges(){
    	DBMS dbms = new MyDBMS();

        final Column number = new Column(Integer.class);
        final Column name = new Column(String.class);
        final Table numbers = new Table(list(number));

        Revision tail = dbms.revision();

        PatchTemplate insert = new InsertTemplate
          (numbers,
           list(number, name),
           list((Expression) new Parameter(), new Parameter()),
           DuplicateKeyResolution.Throw);

        RevisionBuilder builder = dbms.builder(tail);

        builder.apply(insert,  1, "one");
        builder.apply(insert,  2, "two");
        builder.apply(insert,  6, "six");
        builder.apply(insert,  7, "seven");
        builder.apply(insert,  8, "eight");
        builder.apply(insert,  9, "nine");
        builder.apply(insert, 13, "thirteen");

        Revision base = builder.commit();

        builder = dbms.builder(base);

        builder.apply(insert, 4, "four");

        Revision left = builder.commit();

        TableReference numbersReference = new TableReference(numbers);

        PatchTemplate update = new UpdateTemplate
          (numbersReference,
           new BinaryOperation
           (BinaryOperation.Type.Equal,
            new ColumnReference(numbersReference, number),
            new Parameter()),
           list(name),
           list((Expression) new Parameter()));

        builder = dbms.builder(base);

        builder.apply(update,  6, "roku");
        builder.apply(insert, 42, "forty two");

        Revision right = builder.commit();

        Revision merge = dbms.merge(base, left, right, null);

        QueryTemplate any = new QueryTemplate
          (list((Expression) new ColumnReference(numbersReference, name)),
           numbersReference,
           new Constant(true));

        QueryResult result = dbms.diff(tail, merge, any);

        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "one");
        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "two");
        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "four");
        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "roku");
        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "seven");
        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "eight");
        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "nine");
        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "thirteen");
        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "forty two");
        assertEquals(result.nextRow(), QueryResult.Type.End);
        
        builder = dbms.builder(base);

        builder.apply(insert, 4, "four");

        left = builder.commit();

        builder = dbms.builder(base);

        builder.apply(insert, 4, "four");

        right = builder.commit();

        merge = dbms.merge(base, left, right, null);

        result = dbms.diff(base, merge, any);

        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "four");
        assertEquals(result.nextRow(), QueryResult.Type.End);
        
        PatchTemplate delete = new DeleteTemplate
          (numbersReference,
           new BinaryOperation
           (BinaryOperation.Type.Equal,
            new ColumnReference(numbersReference, number),
            new Parameter()));

        builder = dbms.builder(base);

        builder.apply(delete, 8);

        left = builder.commit();

        builder = dbms.builder(base);

        builder.apply(update, 8, "hachi");

        right = builder.commit();

        merge = dbms.merge(base, left, right, null);

        result = dbms.diff(base, merge, any);

        assertEquals(result.nextRow(), QueryResult.Type.Deleted);
        assertEquals(result.nextItem(), "eight");
        assertEquals(result.nextRow(), QueryResult.Type.End);

        builder = dbms.builder(base);

        builder.apply(insert, 4, "four");

        left = builder.commit();

        builder = dbms.builder(base);

        builder.apply(insert, 4, "shi");

        right = builder.commit();

        merge = dbms.merge(base, left, right, new ConflictResolver() {
            public Object resolveConflict(Table table,
                                          Column column,
                                          Object[] primaryKeyValues,
                                          Object baseValue,
                                          Object leftValue,
                                          Object rightValue)
            {
              assertEquals(table, numbers);
              assertEquals(column, name);
              assertEquals(primaryKeyValues.length, 1);
              assertEquals(primaryKeyValues[0], 4);
              assertEquals(baseValue, null);
              assertEquals(leftValue, "four");
              assertEquals(rightValue, "shi");
              
              return "quatro";
            }
          });

        result = dbms.diff(base, merge, any);

        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "quatro");
        assertEquals(result.nextRow(), QueryResult.Type.End);

        builder = dbms.builder(base);

        builder.apply(update, 1, "ichi");

        left = builder.commit();

        builder = dbms.builder(base);

        builder.apply(update, 1, "uno");

        right = builder.commit();

        merge = dbms.merge(base, left, right, new ConflictResolver() {
            public Object resolveConflict(Table table,
                                          Column column,
                                          Object[] primaryKeyValues,
                                          Object baseValue,
                                          Object leftValue,
                                          Object rightValue)
            {
              assertEquals(table, numbers);
              assertEquals(column, name);
              assertEquals(primaryKeyValues.length, 1);
              assertEquals(primaryKeyValues[0], 1);
              assertEquals(baseValue, "one");
              assertEquals(leftValue, "ichi");
              assertEquals(rightValue, "uno");
              
              return "unit";
            }
          });

        result = dbms.diff(base, merge, any);

        assertEquals(result.nextRow(), QueryResult.Type.Deleted);
        assertEquals(result.nextItem(), "one");
        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "unit");
        assertEquals(result.nextRow(), QueryResult.Type.End);

        builder = dbms.builder(tail);

        builder.apply(insert, 1, "one");

        Revision t1 = builder.commit();

        builder = dbms.builder(tail);

        builder.apply(insert, 1, "uno");

        Revision t2 = builder.commit();

        merge = dbms.merge(tail, t1, t2, new ConflictResolver() {
            public Object resolveConflict(Table table,
                                          Column column,
                                          Object[] primaryKeyValues,
                                          Object baseValue,
                                          Object leftValue,
                                          Object rightValue)
            {
              assertEquals(table, numbers);
              assertEquals(column, name);
              assertEquals(primaryKeyValues.length, 1);
              assertEquals(primaryKeyValues[0], 1);
              assertEquals(baseValue, null);
              assertEquals(leftValue, "one");
              assertEquals(rightValue, "uno");
              
              return "unit";
            }
          });

        result = dbms.diff(tail, merge, any);

        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "unit");
        assertEquals(result.nextRow(), QueryResult.Type.End);

        builder = dbms.builder(tail);

        builder.apply(insert, 1, "one");
        builder.apply(insert, 2, "two");

        t1 = builder.commit();

        builder = dbms.builder(tail);

        builder.apply(insert, 1, "uno");
        builder.apply(insert, 3, "tres");

        t2 = builder.commit();

        merge = dbms.merge(tail, t1, t2, new ConflictResolver() {
            public Object resolveConflict(Table table,
                                          Column column,
                                          Object[] primaryKeyValues,
                                          Object baseValue,
                                          Object leftValue,
                                          Object rightValue)
            {
              assertEquals(table, numbers);
              assertEquals(column, name);
              assertEquals(primaryKeyValues.length, 1);
              assertEquals(primaryKeyValues[0], 1);
              assertEquals(baseValue, null);
              assertEquals(leftValue, "one");
              assertEquals(rightValue, "uno");
              
              return "unit";
            }
          });

        result = dbms.diff(tail, merge, any);

        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "unit");
        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "two");
        assertEquals(result.nextRow(), QueryResult.Type.Inserted);
        assertEquals(result.nextItem(), "tres");
        assertEquals(result.nextRow(), QueryResult.Type.End);
    }
}