/* Copyright (c) 2010-2012, Revori Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies. */

package com.readytalk.revori.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.readytalk.revori.Column;
import com.readytalk.revori.DiffResult;
import com.readytalk.revori.Revision;
import com.readytalk.revori.Table;
import com.readytalk.revori.imp.Constants;

public class Util {

  public static <T> List<T> append(List<T> list, T ... elements) {
    return appendToList(list, elements);
  }

  private static <T> List<T> appendToList(List<T> list, T[] elements) {
    List<T> result = new ArrayList<T>(list.size() + elements.length);
    
    result.addAll(list);
    result.addAll(Arrays.asList(elements));
    
    return result;
  }
  
  public static List<Column<?>> cols(Column<?> ... elements) {
    return Lists.newArrayList(elements);
  }

  public static <T> Set<T> set(T ... elements) {
	return Sets.newHashSet(elements);
  }

  public static <T> Set<T> union(Collection<T> ... sets) {
    Set<T> set = new HashSet<T>();
    for (Collection<T> s: sets) {
      set.addAll(s);
    }
    return set;
  }

  public static <T extends Comparable<T>> int compare(Collection<T> a,
                            Collection<T> b)
  {
    int d = a.size() - b.size();
    if (d != 0) {
      return d;
    }

    Iterator<T> ai = a.iterator();
    Iterator<T> bi = b.iterator();
    while (ai.hasNext()) {
      d = ai.next().compareTo(bi.next());
      if (d != 0) {
        return d;
      }
    }

    return 0;
  }

  public static <T> String toString(T[] array, int offset, int length) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = offset; i < offset + length; ++i) {
      sb.append(array[i]);
      if (i < offset + length - 1) {
        sb.append(", ");
      }
    }
    sb.append("]");
    return sb.toString();
  }

  public static String toString(Revision base, Revision fork) {
    StringBuilder sb = new StringBuilder();

    final int MaxDepth = 16;
    Object[] path = new Object[MaxDepth];
    int depth = 0;
    DiffResult result = base.diff(fork, true);
    while (true) {
      DiffResult.Type type = result.next();
      switch (type) {
      case End:
        // sb.append("end\n");
        return sb.toString();

      case Descend: {
        // sb.append("descend\n");
        ++ depth;
      } break;

      case Ascend: {
        // sb.append("ascend\n");
        path[depth--] = null;
      } break;

      case Key: {
        Object forkKey = result.fork();
        if (forkKey != null) {
          path[depth] = forkKey;

          if (Constants.serializable((Table) path[0], forkKey, depth)) {
            // sb.append("key ").append(forkKey).append("\n");
          } else {
            result.skip();
          }
        } else {
          Object baseKey = result.base();
          path[depth] = baseKey;

          if (Constants.serializable((Table) path[0], baseKey, depth)) {
            sb.append("delete");
            sb.append(toString(path, 0, depth + 1));
            sb.append("\n");
          }
          result.skip();
        }
      } break;

      case Value: {
        path[depth] = result.fork();
        sb.append("insert");
        sb.append(toString(path, 0, depth + 1));
        sb.append("\n");
      } break;

      default:
        throw new RuntimeException("unexpected result type: " + type);
      }
    }
  }

  public static Object convert(Class<?> type,
                                String value)
  {
    if (type == Integer.class
        || type == Long.class)
    {
      return Long.parseLong(value.trim());
    } else if (type == String.class) {
      return value;
    } else {
      throw new RuntimeException("unexpected type: " + type);
    }
  }
}
