package com.readytalk.oss.dbms.imp;

import com.readytalk.oss.dbms.Expression;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

class ExpressionContext {
  public final Map<Expression, ExpressionAdapter> adapters = new HashMap();
  public final Set<ColumnReferenceAdapter> columnReferences = new HashSet();
  public final Object[] parameters;
  public int parameterIndex;

  public ExpressionContext(Object[] parameters) {
    this.parameters = parameters;
  }
}
