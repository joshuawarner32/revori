package com.readytalk.oss.dbms.server.protocol;

import java.io.IOException;

public interface Writable {
  public void writeTo(WriteContext context)
    throws IOException;
}
