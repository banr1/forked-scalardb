package com.scalar.db.util;

public class CassandraAdminTestUtils extends AdminTestUtils {

  public CassandraAdminTestUtils() {
    super();
  }

  @Override
  public void dropMetadataTable() {
    // Do nothing
  }

  @Override
  public void truncateMetadataTable() {
    // Do nothing
  }
}