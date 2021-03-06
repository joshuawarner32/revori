/* Copyright (c) 2010-2012, Revori Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies. */

package com.readytalk.revori.imp;

import java.util.List;

interface Scan {
  public boolean isUseful();
  public boolean isSpecific();
  public boolean isUnknown();
  public List<Interval> evaluate();
}
