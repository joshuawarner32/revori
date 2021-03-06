/* Copyright (c) 2010-2012, Revori Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies. */

package com.readytalk.revori.test;

import static com.readytalk.revori.ExpressionFactory.reference;
import static com.readytalk.revori.util.Util.cols;
import com.google.common.collect.Lists;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.readytalk.revori.BinaryOperation;
import com.readytalk.revori.Column;
import com.readytalk.revori.ConflictResolvers;
import com.readytalk.revori.DuplicateKeyResolution;
import com.readytalk.revori.Expression;
import com.readytalk.revori.ForeignKeyResolvers;
import com.readytalk.revori.Index;
import com.readytalk.revori.InsertTemplate;
import com.readytalk.revori.Parameter;
import com.readytalk.revori.PatchTemplate;
import com.readytalk.revori.QueryResult;
import com.readytalk.revori.QueryTemplate;
import com.readytalk.revori.Revision;
import com.readytalk.revori.RevisionBuilder;
import com.readytalk.revori.Revisions;
import com.readytalk.revori.Table;
import com.readytalk.revori.TableReference;

public class IndexesTest {
    
    @Test
    public void testMultiLevelIndexes(){
        Column<String> country = new Column<String>(String.class);
        Column<String> state = new Column<String>(String.class);
        Column<String> city = new Column<String>(String.class);
        Column<Integer> zip = new Column<Integer>(Integer.class);
        Column<String> color = new Column<String>(String.class);
        Table places = new Table(cols(country, state, city));

        Revision tail = Revisions.Empty;

        PatchTemplate insert = new InsertTemplate
          (places,
           cols(country, state, city, zip, color),
           Lists.newArrayList((Expression) new Parameter(),
                new Parameter(),
                new Parameter(),
                new Parameter(),
                new Parameter()), DuplicateKeyResolution.Throw);
        
        RevisionBuilder builder = tail.builder();

        builder.apply(insert,
                   "USA", "Colorado", "Denver", 80209, "teal");
        builder.apply(insert,
                   "USA", "Colorado", "Glenwood Springs", 81601, "orange");
        builder.apply(insert,
                   "USA", "New York", "New York", 10001, "blue");
        builder.apply(insert,
                   "France", "N/A", "Paris", 0, "pink");
        builder.apply(insert,
                   "England", "N/A", "London", 0, "red");
        builder.apply(insert,
                   "China", "N/A", "Beijing", 0, "red");
        builder.apply(insert,
                   "China", "N/A", "Shanghai", 0, "green");

        Revision first = builder.commit();

        TableReference placesReference = new TableReference(places);

        QueryTemplate stateEqual = new QueryTemplate
          (Lists.newArrayList(reference(placesReference, color),
                reference(placesReference, zip)),
           placesReference,
           new BinaryOperation
           (BinaryOperation.Type.Equal,
            reference(placesReference, state),
            new Parameter()));
        Object[] parameters = { "Colorado" };

        QueryResult result = tail.diff(first, stateEqual, parameters);

        assertEquals(QueryResult.Type.Inserted, result.nextRow());
        assertEquals("teal", result.nextItem());
        assertEquals(80209, result.nextItem());
        assertEquals(QueryResult.Type.Inserted, result.nextRow());
        assertEquals("orange", result.nextItem());
        assertEquals(81601, result.nextItem());
        assertEquals(QueryResult.Type.End, result.nextRow());
        Object[] parameters1 = { "Colorado" };

        result = first.diff(tail, stateEqual, parameters1);

        assertEquals(QueryResult.Type.Deleted, result.nextRow());
        assertEquals("teal", result.nextItem());
        assertEquals(80209, result.nextItem());
        assertEquals(QueryResult.Type.Deleted, result.nextRow());
        assertEquals("orange", result.nextItem());
        assertEquals(81601, result.nextItem());
        assertEquals(QueryResult.Type.End, result.nextRow());
        Object[] parameters2 = { "N/A" };

        result = tail.diff(first, stateEqual, parameters2);

        assertEquals(QueryResult.Type.Inserted, result.nextRow());
        assertEquals("red", result.nextItem());
        assertEquals(0, result.nextItem());
        assertEquals(QueryResult.Type.Inserted, result.nextRow());
        assertEquals("green", result.nextItem());
        assertEquals(0, result.nextItem());
        assertEquals(QueryResult.Type.Inserted, result.nextRow());
        assertEquals("red", result.nextItem());
        assertEquals(0, result.nextItem());
        assertEquals(QueryResult.Type.Inserted, result.nextRow());
        assertEquals("pink", result.nextItem());
        assertEquals(0, result.nextItem());
        assertEquals(QueryResult.Type.End, result.nextRow());

        QueryTemplate countryEqual = new QueryTemplate
          (Lists.newArrayList(reference(placesReference, color),
                reference(placesReference, city)),
           placesReference,
           new BinaryOperation
           (BinaryOperation.Type.Equal,
            reference(placesReference, country),
            new Parameter()));
        Object[] parameters3 = { "France" };

        result = tail.diff(first, countryEqual, parameters3);

        assertEquals(QueryResult.Type.Inserted, result.nextRow());
        assertEquals("pink", result.nextItem());
        assertEquals("Paris", result.nextItem());
        assertEquals(QueryResult.Type.End, result.nextRow());
        Object[] parameters4 = { "China" };

        result = tail.diff(first, countryEqual, parameters4);

        assertEquals(QueryResult.Type.Inserted, result.nextRow());
        assertEquals("red", result.nextItem());
        assertEquals("Beijing", result.nextItem());
        assertEquals(QueryResult.Type.Inserted, result.nextRow());
        assertEquals("green", result.nextItem());
        assertEquals("Shanghai", result.nextItem());
        assertEquals(QueryResult.Type.End, result.nextRow());

        QueryTemplate countryStateCityEqual = new QueryTemplate
          (Lists.newArrayList(reference(placesReference, color),
                reference(placesReference, city)),
           placesReference,
           new BinaryOperation
           (BinaryOperation.Type.And,
            new BinaryOperation
            (BinaryOperation.Type.And,
             new BinaryOperation
             (BinaryOperation.Type.Equal,
              reference(placesReference, country),
              new Parameter()),
             new BinaryOperation
             (BinaryOperation.Type.Equal,
              reference(placesReference, state),
              new Parameter())),
            new BinaryOperation
            (BinaryOperation.Type.Equal,
             reference(placesReference, city),
             new Parameter())));
        Object[] parameters5 = { "France", "Colorado", "Paris" };

        result = tail.diff(first, countryStateCityEqual, parameters5);

        assertEquals(QueryResult.Type.End, result.nextRow());
        Object[] parameters6 = { "France", "N/A", "Paris" };

        result = tail.diff(first, countryStateCityEqual, parameters6);

        assertEquals(QueryResult.Type.Inserted, result.nextRow());
        assertEquals("pink", result.nextItem());
        assertEquals("Paris", result.nextItem());
        assertEquals(QueryResult.Type.End, result.nextRow());
    }
    
  @Test
  public void testAddIndexAndMerge() {
    Column<String> country = new Column<String>(String.class);
    Column<String> state = new Column<String>(String.class);
    Column<String> city = new Column<String>(String.class);
    Column<Integer> zip = new Column<Integer>(Integer.class);
    Column<String> color = new Column<String>(String.class);
    Table places = new Table(cols(country, state, city));

    Revision tail = Revisions.Empty;

    PatchTemplate insert = new InsertTemplate
      (places,
       cols(country, state, city, zip, color),
       Lists.newArrayList((Expression) new Parameter(),
            new Parameter(),
            new Parameter(),
            new Parameter(),
            new Parameter()), DuplicateKeyResolution.Throw);
    
    RevisionBuilder builder = tail.builder();

    builder.apply(insert,
               "USA", "Colorado", "Denver", 80209, "teal");
    builder.apply(insert,
               "USA", "Colorado", "Glenwood Springs", 81601, "orange");
    builder.apply(insert,
               "USA", "New York", "New York", 10001, "blue");
    builder.apply(insert,
               "France", "N/A", "Paris", 0, "pink");
    builder.apply(insert,
               "England", "N/A", "London", 0, "red");
    builder.apply(insert,
               "China", "N/A", "Beijing", 0, "red");
    builder.apply(insert,
               "China", "N/A", "Shanghai", 0, "green");

    Revision first = builder.commit();
    
    builder = first.builder();
    builder.apply(insert,
        "India", "N/A", "Delhi", 0, "maroon");
    Revision left = builder.commit();
    
    builder = first.builder();
    Index index = new Index(places, cols(zip));
    builder.add(index);
    Revision right = builder.commit();
    
    Revision merge = first.merge(left, right, ConflictResolvers.Restrict, ForeignKeyResolvers.Restrict);

    // lets just double-check that we actually have the index in right...
    assertEquals("teal", right.query(index, 80209, "USA", "Colorado", "Denver", color));
    
    // lets also double-check that we actually have Delhi 
    assertEquals("maroon", merge.query(places.primaryKey, "India", "N/A", "Delhi", color));
    
    assertEquals("teal", merge.query(index, 80209, "USA", "Colorado", "Denver", color));
    assertEquals("orange", merge.query(index, 81601, "USA", "Colorado", "Glenwood Springs", color));
    assertEquals("blue", merge.query(index, 10001, "USA", "New York", "New York", color));
    assertEquals("pink", merge.query(index, 0, "France", "N/A", "Paris", color));
    assertEquals("red", merge.query(index, 0, "England", "N/A", "London", color));
    assertEquals("red", merge.query(index, 0, "China", "N/A", "Beijing", color));
    assertEquals("green", merge.query(index, 0, "China", "N/A", "Shanghai", color));
    
    assertEquals("maroon", merge.query(index, 0, "India", "N/A", "Delhi", color));
    
  }
  
  @Test
  public void testReplaceIndexAndMerge() {
    Column<String> country = new Column<String>(String.class);
    Column<String> state = new Column<String>(String.class);
    Column<String> city = new Column<String>(String.class);
    Column<Integer> zip = new Column<Integer>(Integer.class);
    Column<String> color = new Column<String>(String.class);
    Table places = new Table(cols(country, state, city));

    Revision tail = Revisions.Empty;

    PatchTemplate insert = new InsertTemplate
      (places,
       cols(country, state, city, zip, color),
       Lists.newArrayList((Expression) new Parameter(),
            new Parameter(),
            new Parameter(),
            new Parameter(),
            new Parameter()), DuplicateKeyResolution.Throw);
    
    RevisionBuilder builder = tail.builder();

    builder.apply(insert,
               "USA", "Colorado", "Denver", 80209, "teal");
    builder.apply(insert,
               "USA", "Colorado", "Glenwood Springs", 81601, "orange");
    builder.apply(insert,
               "USA", "New York", "New York", 10001, "blue");
    builder.apply(insert,
               "France", "N/A", "Paris", 0, "pink");
    builder.apply(insert,
               "England", "N/A", "London", 0, "red");
    builder.apply(insert,
               "China", "N/A", "Beijing", 0, "red");
    builder.apply(insert,
               "China", "N/A", "Shanghai", 0, "green");

    Index index = new Index(places, cols(zip));
    builder.add(index);
    Revision first = builder.commit();

    builder = first.builder();
    builder.apply(insert,
        "India", "N/A", "Delhi", 0, "maroon");
    Revision left = builder.commit();
    
    builder = first.builder();
    builder.remove(index);
    index = new Index(places, cols(zip, color));
    builder.add(index);
    Revision right = builder.commit();
    
    Revision merge = first.merge(left, right, ConflictResolvers.Restrict, ForeignKeyResolvers.Restrict);

    // lets just double-check that we actually have the index in right...
    assertEquals("teal", right.query(index, 80209, "teal", "USA", "Colorado", "Denver", color));
    
    // lets also double-check that we actually have Delhi 
    assertEquals("maroon", merge.query(places.primaryKey, "India", "N/A", "Delhi", color));
    
    assertEquals("teal", merge.query(index, 80209, "teal", "USA", "Colorado", "Denver", color));
    assertEquals("orange", merge.query(index, 81601, "orange", "USA", "Colorado", "Glenwood Springs", color));
    assertEquals("blue", merge.query(index, 10001, "blue", "USA", "New York", "New York", color));
    assertEquals("pink", merge.query(index, 0, "pink", "France", "N/A", "Paris", color));
    assertEquals("red", merge.query(index, 0, "red", "England", "N/A", "London", color));
    assertEquals("red", merge.query(index, 0, "red", "China", "N/A", "Beijing", color));
    assertEquals("green", merge.query(index, 0, "green", "China", "N/A", "Shanghai", color));
    
    assertEquals("maroon", merge.query(index, 0, "maroon", "India", "N/A", "Delhi", color));
    
  }
  
  @Test
  public void testInsertIndexDataWithMerge() {
    Column<String> country = new Column<String>(String.class);
    Column<String> state = new Column<String>(String.class);
    Column<String> city = new Column<String>(String.class);
    Column<Integer> zip = new Column<Integer>(Integer.class);
    Column<String> color = new Column<String>(String.class);
    Table places = new Table(cols(country, state, city));

    Revision tail = Revisions.Empty;

    PatchTemplate insert = new InsertTemplate
      (places,
       cols(country, state, city, zip, color),
       Lists.newArrayList((Expression) new Parameter(),
            new Parameter(),
            new Parameter(),
            new Parameter(),
            new Parameter()), DuplicateKeyResolution.Throw);
    
    RevisionBuilder builder = tail.builder();

    builder.apply(insert,
               "USA", "Colorado", "Denver", 80209, "teal");
    builder.apply(insert,
               "USA", "Colorado", "Glenwood Springs", 81601, "orange");
    builder.apply(insert,
               "USA", "New York", "New York", 10001, "blue");
    builder.apply(insert,
               "France", "N/A", "Paris", 0, "pink");
    builder.apply(insert,
               "England", "N/A", "London", 0, "red");
    builder.apply(insert,
               "China", "N/A", "Beijing", 0, "red");
    builder.apply(insert,
               "China", "N/A", "Shanghai", 0, "green");

    Index index = new Index(places, cols(zip));
    builder.add(index);
    Revision first = builder.commit();
    
    builder = first.builder();
    //builder.apply(insert,
    //    "USA", "Colorado", "Monument", 0, "grey");
    Revision left = builder.commit();

    builder = first.builder();
    builder.table(places)
      .row("China", "N/A", "Shanghai")
      .update(color, "maroon");
    Revision right = builder.commit();
    
    first.merge(left, right, ConflictResolvers.Restrict, ForeignKeyResolvers.Restrict);
    
  }
}
