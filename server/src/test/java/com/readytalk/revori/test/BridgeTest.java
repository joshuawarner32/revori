/* Copyright (c) 2010-2012, Revori Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies. */

package com.readytalk.revori.test;
import static com.readytalk.revori.DuplicateKeyResolution.Throw;
import static com.readytalk.revori.util.Util.cols;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.readytalk.revori.Column;
import com.readytalk.revori.Revision;
import com.readytalk.revori.RevisionBuilder;
import com.readytalk.revori.Table;
import com.readytalk.revori.subscribe.Subscription;
import com.readytalk.revori.server.Bridge;
import com.readytalk.revori.server.RevisionServer;
import com.readytalk.revori.server.SimpleRevisionServer;

public class BridgeTest {
  @Test
  public void testBasic() {
    RevisionServer left = new SimpleRevisionServer(null, null);
    RevisionServer right = new SimpleRevisionServer(null, null);

    Bridge bridge = new Bridge();

    Column<Integer> number = new Column<Integer>(Integer.class, "number");
    Column<String> name = new Column<String>(String.class, "name");
    Table numbers = new Table(cols(number), "numbers");
    Bridge.Path path = new Bridge.Path(numbers);

    Subscription subscription = bridge.register(left, path, right, path);

    { Revision base = left.head();
      RevisionBuilder builder = base.builder();

      builder.insert(Throw, numbers, 1, name, "one");

      left.merge(base, builder.commit());
    }

    assertEquals("one", left.head().query(numbers.primaryKey, 1, name));
    assertEquals("one", right.head().query(numbers.primaryKey, 1, name));

    { Revision base = right.head();
      RevisionBuilder builder = base.builder();

      builder.insert(Throw, numbers, 2, name, "two");

      right.merge(base, builder.commit());
    }

    assertEquals("two", left.head().query(numbers.primaryKey, 2, name));
    assertEquals("two", right.head().query(numbers.primaryKey, 2, name));

    subscription.cancel();
  }

  @Test
  public void testAggregate() {
    RevisionServer left1 = new SimpleRevisionServer(null, null);
    RevisionServer left2 = new SimpleRevisionServer(null, null);
    RevisionServer right = new SimpleRevisionServer(null, null);

    Bridge bridge = new Bridge();

    Column<Integer> number = new Column<Integer>(Integer.class, "number");
    Column<String> name = new Column<String>(String.class, "name");
    Table numbers = new Table(cols(number), "numbers");
    Bridge.Path leftPath = new Bridge.Path(numbers);

    Column<Integer> origin = new Column<Integer>(Integer.class, "origin");
    Table originNumbers = new Table(cols(origin, number), "origin numbers");

    bridge.register(left1, leftPath, right, new Bridge.Path(originNumbers, 1));
    bridge.register(left2, leftPath, right, new Bridge.Path(originNumbers, 2));

    { Revision base = left1.head();
      RevisionBuilder builder = base.builder();

      builder.insert(Throw, numbers, 1, name, "one");

      left1.merge(base, builder.commit());
    }

    assertEquals("one", left1.head().query(numbers.primaryKey, 1, name));
    assertEquals
      (left1.head().query(originNumbers.primaryKey, 1, 1, name), null);
    assertEquals(null, left2.head().query(numbers.primaryKey, 1, name));
    assertEquals
      (left2.head().query(originNumbers.primaryKey, 1, 1, name), null);
    assertEquals(null, right.head().query(numbers.primaryKey, 1, name));
    assertEquals
      (right.head().query(originNumbers.primaryKey, 1, 1, name), "one");

    { Revision base = right.head();
      RevisionBuilder builder = base.builder();

      builder.insert(Throw, originNumbers, 2, 2, name, "two");

      right.merge(base, builder.commit());
    }

    assertEquals(null, left1.head().query(numbers.primaryKey, 2, name));
    assertEquals
      (left1.head().query(originNumbers.primaryKey, 2, 2, name), null);
    assertEquals("two", left2.head().query(numbers.primaryKey, 2, name));
    assertEquals
      (left2.head().query(originNumbers.primaryKey, 2, 2, name), null);
    assertEquals(null, right.head().query(numbers.primaryKey, 2, name));
    assertEquals
      (right.head().query(originNumbers.primaryKey, 2, 2, name), "two");

    { Revision base = right.head();
      RevisionBuilder builder = base.builder();

      builder.insert(Throw, originNumbers, 1, 2, name, "two");

      right.merge(base, builder.commit());
    }

    assertEquals("two", left1.head().query(numbers.primaryKey, 2, name));
    assertEquals
      (left1.head().query(originNumbers.primaryKey, 1, 2, name), null);
    assertEquals("two", left2.head().query(numbers.primaryKey, 2, name));
    assertEquals
      (left2.head().query(originNumbers.primaryKey, 1, 2, name), null);
    assertEquals(null, right.head().query(numbers.primaryKey, 2, name));
    assertEquals
      (right.head().query(originNumbers.primaryKey, 1, 2, name), "two");
    assertEquals
      (right.head().query(originNumbers.primaryKey, 2, 2, name), "two");

    { Revision base = right.head();
      RevisionBuilder builder = base.builder();

      builder.delete(originNumbers, 1, 2);

      right.merge(base, builder.commit());
    }

    assertEquals(null, left1.head().query(numbers.primaryKey, 2, name));
    assertEquals("two", left2.head().query(numbers.primaryKey, 2, name));
    assertEquals(null, right.head().query(numbers.primaryKey, 2, name));
    assertEquals
      (right.head().query(originNumbers.primaryKey, 1, 2, name), null);
    assertEquals
      (right.head().query(originNumbers.primaryKey, 2, 2, name), "two");

    { Revision base = left2.head();
      RevisionBuilder builder = base.builder();

      builder.delete(numbers, 2);

      left2.merge(base, builder.commit());
    }

    assertEquals(null, left2.head().query(numbers.primaryKey, 2, name));
    assertEquals
      (right.head().query(originNumbers.primaryKey, 2, 2, name), null);
  }
}
