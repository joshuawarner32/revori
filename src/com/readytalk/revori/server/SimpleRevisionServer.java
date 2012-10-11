/* Copyright (c) 2010-2012, Revori Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies. */

package com.readytalk.revori.server;

import com.readytalk.revori.Revision;
import com.readytalk.revori.Revisions;
import com.readytalk.revori.ConflictResolver;
import com.readytalk.revori.ForeignKeyResolver;
import com.readytalk.revori.subscribe.Subscription;

import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

public class SimpleRevisionServer implements RevisionServer {
  private final ConflictResolver conflictResolver;
  private final ForeignKeyResolver foreignKeyResolver;
  private final AtomicReference<Revision> head = new AtomicReference
    (Revisions.Empty);
  public final AtomicReference<Set<Runnable>> listeners
    = new AtomicReference(new HashSet());

  public SimpleRevisionServer(ConflictResolver conflictResolver,
                              ForeignKeyResolver foreignKeyResolver)
  {
    this.conflictResolver = conflictResolver;
    this.foreignKeyResolver = foreignKeyResolver;
  }

  public Revision head() {
    return head.get();
  }

  public void merge(Revision base, Revision fork) {
    if (base != fork || base != head.get()) {
      while (! head.compareAndSet(base, fork)) {
        Revision h = head.get();
        fork = base.merge
          (h, fork, conflictResolver, foreignKeyResolver);
        base = h;
      }

      for (Runnable listener: listeners.get()) {
        listener.run();
      }
    }
  }

  public Subscription registerListener(final Runnable listener) {
    Subscription s = new Subscription() {
      public void subscribe() {
        while (true) {
          Set<Runnable> oldListeners = listeners.get();
          Set<Runnable> newListeners = new HashSet(oldListeners);
          newListeners.add(listener);
          if (listeners.compareAndSet(oldListeners, newListeners)) {
            break;
          }
        }
        listener.run();
      }

      public void cancel() {
        while (true) {
          Set<Runnable> oldListeners = listeners.get();
          Set<Runnable> newListeners = new HashSet(oldListeners);
          newListeners.remove(listener);
          if (listeners.compareAndSet(oldListeners, newListeners)) {
            break;
          }
        }
      }
    };

    s.subscribe();
    return s;
  }

}
