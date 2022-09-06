package com.scalar.db.server;

import com.scalar.db.exception.storage.ExecutionException;
import com.scalar.db.schemaloader.SchemaLoaderIntegrationTestBase;
import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;

public class SchemaLoaderIntegrationTestWithScalarDbServer extends SchemaLoaderIntegrationTestBase {

  private ScalarDbServer server;

  @Override
  protected void initialize() throws IOException {
    Properties properties = ServerEnv.getServerProperties();
    if (properties != null) {
      server = new ScalarDbServer(properties);
      server.start();
    }
  }

  @Override
  protected Properties getProperties() {
    return ServerEnv.getProperties();
  }

  @AfterAll
  @Override
  public void afterAll() throws ExecutionException, IOException {
    super.afterAll();
    if (server != null) {
      server.shutdown();
    }
  }
}