package com.scalar.db.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.scalar.db.api.DistributedStorage;
import com.scalar.db.api.DistributedStorageAdmin;
import com.scalar.db.api.Put;
import com.scalar.db.api.Result;
import com.scalar.db.api.Scan;
import com.scalar.db.api.Scan.Ordering;
import com.scalar.db.api.Scan.Ordering.Order;
import com.scalar.db.api.Scanner;
import com.scalar.db.api.TableMetadata;
import com.scalar.db.config.DatabaseConfig;
import com.scalar.db.exception.storage.ExecutionException;
import com.scalar.db.io.DataType;
import com.scalar.db.io.Key;
import com.scalar.db.io.Value;
import com.scalar.db.service.StorageFactory;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

@SuppressFBWarnings(value = {"MS_PKGPROTECT", "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD"})
public abstract class StorageSingleClusteringKeyScanIntegrationTestBase {

  protected static final String TEST_NAME = "single_ckey";
  protected static final String NAMESPACE = "integration_testing_" + TEST_NAME;
  protected static final String PARTITION_KEY = "pkey";
  protected static final String CLUSTERING_KEY = "ckey";
  protected static final String COL_NAME = "col";

  private static final int CLUSTERING_KEY_NUM = 20;

  private static final Random RANDOM = new Random();

  private static boolean initialized;
  protected static DistributedStorageAdmin admin;
  protected static DistributedStorage storage;
  protected static String namespace;
  protected static Set<DataType> clusteringKeyTypes;

  private static long seed;

  @Before
  public void setUp() throws Exception {
    if (!initialized) {
      StorageFactory factory =
          new StorageFactory(TestUtils.addSuffix(getDatabaseConfig(), TEST_NAME));
      admin = factory.getAdmin();
      namespace = getNamespace();
      clusteringKeyTypes = getClusteringKeyTypes();
      createTables();
      storage = factory.getStorage();
      seed = System.currentTimeMillis();
      System.out.println(
          "The seed used in the single clustering key scan integration test is " + seed);
      initialized = true;
    }
  }

  protected abstract DatabaseConfig getDatabaseConfig();

  protected String getNamespace() {
    return NAMESPACE;
  }

  protected Set<DataType> getClusteringKeyTypes() {
    return new HashSet<>(Arrays.asList(DataType.values()));
  }

  protected Map<String, String> getCreateOptions() {
    return Collections.emptyMap();
  }

  private void createTables() throws ExecutionException {
    Map<String, String> options = getCreateOptions();
    admin.createNamespace(namespace, true, options);
    for (DataType clusteringKeyType : clusteringKeyTypes) {
      for (Order clusteringOrder : Order.values()) {
        createTable(clusteringKeyType, clusteringOrder, options);
      }
    }
  }

  private void createTable(
      DataType clusteringKeyType, Order clusteringOrder, Map<String, String> options)
      throws ExecutionException {
    admin.createTable(
        namespace,
        getTableName(clusteringKeyType, clusteringOrder),
        TableMetadata.newBuilder()
            .addColumn(PARTITION_KEY, DataType.INT)
            .addColumn(CLUSTERING_KEY, clusteringKeyType)
            .addColumn(COL_NAME, DataType.INT)
            .addPartitionKey(PARTITION_KEY)
            .addClusteringKey(CLUSTERING_KEY)
            .build(),
        true,
        options);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    deleteTables();
    admin.close();
    storage.close();
  }

  private static void deleteTables() throws ExecutionException {
    for (DataType clusteringKeyType : clusteringKeyTypes) {
      for (Order clusteringOrder : Order.values()) {
        admin.dropTable(namespace, getTableName(clusteringKeyType, clusteringOrder));
      }
    }
    admin.dropNamespace(namespace);
  }

  private void truncateTable(DataType clusteringKeyType, Order clusteringOrder)
      throws ExecutionException {
    admin.truncateTable(namespace, getTableName(clusteringKeyType, clusteringOrder));
  }

  private static String getTableName(DataType clusteringKeyType, Order clusteringOrder) {
    return clusteringKeyType + "_" + clusteringOrder;
  }

  @Test
  public void scan_WithoutClusteringKeyRange_ShouldReturnProperResult()
      throws ExecutionException, IOException {
    for (DataType clusteringKeyType : clusteringKeyTypes) {
      for (Order clusteringOrder : Order.values()) {
        truncateTable(clusteringKeyType, clusteringOrder);
        List<Value<?>> clusteringKeyValues = prepareRecords(clusteringKeyType, clusteringOrder);
        for (Boolean reverse : Arrays.asList(false, true)) {
          scan_WithoutClusteringKeyRange_ShouldReturnProperResult(
              clusteringKeyValues, clusteringKeyType, clusteringOrder, reverse);
        }
      }
    }
  }

  protected void scan_WithoutClusteringKeyRange_ShouldReturnProperResult(
      List<Value<?>> clusteringKeyValues,
      DataType clusteringKeyType,
      Order clusteringOrder,
      boolean reverse)
      throws ExecutionException, IOException {
    // Arrange
    Scan scan =
        new Scan(getPartitionKey())
            .withOrdering(
                new Ordering(
                    CLUSTERING_KEY,
                    reverse ? TestUtils.reverseOrder(clusteringOrder) : clusteringOrder))
            .forNamespace(namespace)
            .forTable(getTableName(clusteringKeyType, clusteringOrder));

    List<Value<?>> expected = getExpected(clusteringKeyValues, null, null, null, null, reverse);

    // Act
    List<Result> actual = scanAll(scan);

    // Assert
    assertScanResult(actual, expected, description(clusteringKeyType, null, null, reverse));
  }

  @Test
  public void scan_WithClusteringKeyRange_ShouldReturnProperResult()
      throws ExecutionException, IOException {
    for (DataType clusteringKeyType : clusteringKeyTypes) {
      for (Order clusteringOrder : Order.values()) {
        truncateTable(clusteringKeyType, clusteringOrder);
        List<Value<?>> clusteringKeyValues = prepareRecords(clusteringKeyType, clusteringOrder);
        for (Boolean startInclusive : Arrays.asList(true, false)) {
          for (Boolean endInclusive : Arrays.asList(true, false)) {
            for (Boolean reverse : Arrays.asList(false, true)) {
              scan_WithClusteringKeyRange_ShouldReturnProperResult(
                  clusteringKeyValues,
                  clusteringKeyType,
                  clusteringOrder,
                  startInclusive,
                  endInclusive,
                  reverse);
            }
          }
        }
      }
    }
  }

  protected void scan_WithClusteringKeyRange_ShouldReturnProperResult(
      List<Value<?>> clusteringKeyValues,
      DataType clusteringKeyType,
      Order clusteringOrder,
      boolean startInclusive,
      boolean endInclusive,
      boolean reverse)
      throws ExecutionException, IOException {
    // Arrange
    Value<?> startClusteringKeyValue;
    Value<?> endClusteringKeyValue;
    if (clusteringKeyType == DataType.BOOLEAN) {
      startClusteringKeyValue = clusteringKeyValues.get(0);
      endClusteringKeyValue = clusteringKeyValues.get(1);
    } else {
      startClusteringKeyValue = clusteringKeyValues.get(4);
      endClusteringKeyValue = clusteringKeyValues.get(14);
    }

    Scan scan =
        new Scan(getPartitionKey())
            .withStart(new Key(startClusteringKeyValue), startInclusive)
            .withEnd(new Key(endClusteringKeyValue), endInclusive)
            .withOrdering(
                new Ordering(
                    CLUSTERING_KEY,
                    reverse ? TestUtils.reverseOrder(clusteringOrder) : clusteringOrder))
            .forNamespace(namespace)
            .forTable(getTableName(clusteringKeyType, clusteringOrder));

    List<Value<?>> expected =
        getExpected(
            clusteringKeyValues,
            startClusteringKeyValue,
            startInclusive,
            endClusteringKeyValue,
            endInclusive,
            reverse);

    // Act
    List<Result> actual = scanAll(scan);

    // Assert
    assertScanResult(
        actual, expected, description(clusteringKeyType, startInclusive, endInclusive, reverse));
  }

  @Test
  public void scan_WithClusteringKeyRangeWithSameValues_ShouldReturnProperResult()
      throws ExecutionException, IOException {
    for (DataType clusteringKeyType : clusteringKeyTypes) {
      for (Order clusteringOrder : Order.values()) {
        truncateTable(clusteringKeyType, clusteringOrder);
        List<Value<?>> clusteringKeyValues = prepareRecords(clusteringKeyType, clusteringOrder);
        for (Boolean startInclusive : Arrays.asList(true, false)) {
          for (Boolean endInclusive : Arrays.asList(true, false)) {
            for (Boolean reverse : Arrays.asList(false, true)) {
              scan_WithClusteringKeyRangeWithSameValues_ShouldReturnProperResult(
                  clusteringKeyValues,
                  clusteringKeyType,
                  clusteringOrder,
                  startInclusive,
                  endInclusive,
                  reverse);
            }
          }
        }
      }
    }
  }

  protected void scan_WithClusteringKeyRangeWithSameValues_ShouldReturnProperResult(
      List<Value<?>> clusteringKeyValues,
      DataType clusteringKeyType,
      Order clusteringOrder,
      boolean startInclusive,
      boolean endInclusive,
      boolean reverse)
      throws ExecutionException, IOException {
    // Arrange
    Value<?> startAndEndClusteringKeyValue;
    if (clusteringKeyType == DataType.BOOLEAN) {
      startAndEndClusteringKeyValue = clusteringKeyValues.get(0);
    } else {
      startAndEndClusteringKeyValue = clusteringKeyValues.get(9);
    }

    Scan scan =
        new Scan(getPartitionKey())
            .withStart(new Key(startAndEndClusteringKeyValue), startInclusive)
            .withEnd(new Key(startAndEndClusteringKeyValue), endInclusive)
            .withOrdering(
                new Ordering(
                    CLUSTERING_KEY,
                    reverse ? TestUtils.reverseOrder(clusteringOrder) : clusteringOrder))
            .forNamespace(namespace)
            .forTable(getTableName(clusteringKeyType, clusteringOrder));

    List<Value<?>> expected =
        getExpected(
            clusteringKeyValues,
            startAndEndClusteringKeyValue,
            startInclusive,
            startAndEndClusteringKeyValue,
            endInclusive,
            reverse);

    // Act
    List<Result> actual = scanAll(scan);

    // Assert
    assertScanResult(
        actual, expected, description(clusteringKeyType, startInclusive, endInclusive, reverse));
  }

  @Test
  public void scan_WithClusteringKeyRangeWithMinAndMaxValue_ShouldReturnProperResult()
      throws ExecutionException, IOException {
    for (DataType clusteringKeyType : clusteringKeyTypes) {
      for (Order clusteringOrder : Order.values()) {

        truncateTable(clusteringKeyType, clusteringOrder);
        List<Value<?>> clusteringKeyValues = prepareRecords(clusteringKeyType, clusteringOrder);
        for (Boolean startInclusive : Arrays.asList(true, false)) {
          for (Boolean endInclusive : Arrays.asList(true, false)) {
            for (Boolean reverse : Arrays.asList(false, true)) {
              scan_WithClusteringKeyRangeWithMinAndMaxValue_ShouldReturnProperResult(
                  clusteringKeyValues,
                  clusteringKeyType,
                  clusteringOrder,
                  startInclusive,
                  endInclusive,
                  reverse);
            }
          }
        }
      }
    }
  }

  protected void scan_WithClusteringKeyRangeWithMinAndMaxValue_ShouldReturnProperResult(
      List<Value<?>> clusteringKeyValues,
      DataType clusteringKeyType,
      Order clusteringOrder,
      boolean startInclusive,
      boolean endInclusive,
      boolean reverse)
      throws ExecutionException, IOException {
    // Arrange
    Value<?> startClusteringKeyValue = getMinValue(CLUSTERING_KEY, clusteringKeyType);
    Value<?> endClusteringKeyValue = getMaxValue(CLUSTERING_KEY, clusteringKeyType);

    Scan scan =
        new Scan(getPartitionKey())
            .withStart(new Key(startClusteringKeyValue), startInclusive)
            .withEnd(new Key(endClusteringKeyValue), endInclusive)
            .withOrdering(
                new Ordering(
                    CLUSTERING_KEY,
                    reverse ? TestUtils.reverseOrder(clusteringOrder) : clusteringOrder))
            .forNamespace(namespace)
            .forTable(getTableName(clusteringKeyType, clusteringOrder));

    List<Value<?>> expected =
        getExpected(
            clusteringKeyValues,
            startClusteringKeyValue,
            startInclusive,
            endClusteringKeyValue,
            endInclusive,
            reverse);

    // Act
    List<Result> actual = scanAll(scan);

    // Assert
    assertScanResult(
        actual, expected, description(clusteringKeyType, startInclusive, endInclusive, reverse));
  }

  @Test
  public void scan_WithClusteringKeyStartRange_ShouldReturnProperResult()
      throws ExecutionException, IOException {
    for (DataType clusteringKeyType : clusteringKeyTypes) {
      for (Order clusteringOrder : Order.values()) {
        truncateTable(clusteringKeyType, clusteringOrder);
        List<Value<?>> clusteringKeyValues = prepareRecords(clusteringKeyType, clusteringOrder);
        for (Boolean startInclusive : Arrays.asList(true, false)) {
          for (Boolean reverse : Arrays.asList(false, true)) {
            scan_WithClusteringKeyStartRange_ShouldReturnProperResult(
                clusteringKeyValues, clusteringKeyType, clusteringOrder, startInclusive, reverse);
          }
        }
      }
    }
  }

  protected void scan_WithClusteringKeyStartRange_ShouldReturnProperResult(
      List<Value<?>> clusteringKeyValues,
      DataType clusteringKeyType,
      Order clusteringOrder,
      boolean startInclusive,
      boolean reverse)
      throws ExecutionException, IOException {
    // Arrange
    Value<?> startClusteringKeyValue;
    if (clusteringKeyType == DataType.BOOLEAN) {
      startClusteringKeyValue = clusteringKeyValues.get(0);
    } else {
      startClusteringKeyValue = clusteringKeyValues.get(4);
    }

    Scan scan =
        new Scan(getPartitionKey())
            .withStart(new Key(startClusteringKeyValue), startInclusive)
            .withOrdering(
                new Ordering(
                    CLUSTERING_KEY,
                    reverse ? TestUtils.reverseOrder(clusteringOrder) : clusteringOrder))
            .forNamespace(namespace)
            .forTable(getTableName(clusteringKeyType, clusteringOrder));

    List<Value<?>> expected =
        getExpected(
            clusteringKeyValues, startClusteringKeyValue, startInclusive, null, null, reverse);

    // Act
    List<Result> actual = scanAll(scan);

    // Assert
    assertScanResult(
        actual, expected, description(clusteringKeyType, startInclusive, null, reverse));
  }

  @Test
  public void scan_WithClusteringKeyStartRangeWithMinValue_ShouldReturnProperResult()
      throws ExecutionException, IOException {
    for (DataType clusteringKeyType : clusteringKeyTypes) {
      for (Order clusteringOrder : Order.values()) {
        truncateTable(clusteringKeyType, clusteringOrder);
        List<Value<?>> clusteringKeyValues = prepareRecords(clusteringKeyType, clusteringOrder);
        for (Boolean startInclusive : Arrays.asList(true, false)) {
          for (Boolean reverse : Arrays.asList(false, true)) {
            scan_WithClusteringKeyStartRangeWithMinValue_ShouldReturnProperResult(
                clusteringKeyValues, clusteringKeyType, clusteringOrder, startInclusive, reverse);
          }
        }
      }
    }
  }

  protected void scan_WithClusteringKeyStartRangeWithMinValue_ShouldReturnProperResult(
      List<Value<?>> clusteringKeyValues,
      DataType clusteringKeyType,
      Order clusteringOrder,
      boolean startInclusive,
      boolean reverse)
      throws ExecutionException, IOException {
    // Arrange
    Value<?> startClusteringKeyValue = getMinValue(CLUSTERING_KEY, clusteringKeyType);

    Scan scan =
        new Scan(getPartitionKey())
            .withStart(new Key(startClusteringKeyValue), startInclusive)
            .withOrdering(
                new Ordering(
                    CLUSTERING_KEY,
                    reverse ? TestUtils.reverseOrder(clusteringOrder) : clusteringOrder))
            .forNamespace(namespace)
            .forTable(getTableName(clusteringKeyType, clusteringOrder));

    List<Value<?>> expected =
        getExpected(
            clusteringKeyValues, startClusteringKeyValue, startInclusive, null, null, reverse);

    // Act
    List<Result> actual = scanAll(scan);

    // Assert
    assertScanResult(
        actual, expected, description(clusteringKeyType, startInclusive, null, reverse));
  }

  @Test
  public void scan_WithClusteringKeyEndRange_ShouldReturnProperResult()
      throws ExecutionException, IOException {
    for (DataType clusteringKeyType : clusteringKeyTypes) {
      for (Order clusteringOrder : Order.values()) {
        truncateTable(clusteringKeyType, clusteringOrder);
        List<Value<?>> clusteringKeyValues = prepareRecords(clusteringKeyType, clusteringOrder);
        for (Boolean endInclusive : Arrays.asList(true, false)) {
          for (Boolean reverse : Arrays.asList(false, true)) {
            scan_WithClusteringKeyEndRange_ShouldReturnProperResult(
                clusteringKeyValues, clusteringKeyType, clusteringOrder, endInclusive, reverse);
          }
        }
      }
    }
  }

  protected void scan_WithClusteringKeyEndRange_ShouldReturnProperResult(
      List<Value<?>> clusteringKeyValues,
      DataType clusteringKeyType,
      Order clusteringOrder,
      boolean endInclusive,
      boolean reverse)
      throws ExecutionException, IOException {
    // Arrange
    Value<?> endClusteringKeyValue;
    if (clusteringKeyType == DataType.BOOLEAN) {
      endClusteringKeyValue = clusteringKeyValues.get(1);
    } else {
      endClusteringKeyValue = clusteringKeyValues.get(14);
    }

    Scan scan =
        new Scan(getPartitionKey())
            .withEnd(new Key(endClusteringKeyValue), endInclusive)
            .withOrdering(
                new Ordering(
                    CLUSTERING_KEY,
                    reverse ? TestUtils.reverseOrder(clusteringOrder) : clusteringOrder))
            .forNamespace(namespace)
            .forTable(getTableName(clusteringKeyType, clusteringOrder));

    List<Value<?>> expected =
        getExpected(clusteringKeyValues, null, null, endClusteringKeyValue, endInclusive, reverse);

    // Act
    List<Result> actual = scanAll(scan);

    // Assert
    assertScanResult(actual, expected, description(clusteringKeyType, null, endInclusive, reverse));
  }

  @Test
  public void scan_WithClusteringKeyEndRangeWithMaxValue_ShouldReturnProperResult()
      throws ExecutionException, IOException {
    for (DataType clusteringKeyType : clusteringKeyTypes) {
      for (Order clusteringOrder : Order.values()) {
        truncateTable(clusteringKeyType, clusteringOrder);
        List<Value<?>> clusteringKeyValues = prepareRecords(clusteringKeyType, clusteringOrder);
        for (Boolean endInclusive : Arrays.asList(true, false)) {
          for (Boolean reverse : Arrays.asList(false, true)) {
            scan_WithClusteringKeyEndRangeWithMaxValue_ShouldReturnProperResult(
                clusteringKeyValues, clusteringKeyType, clusteringOrder, endInclusive, reverse);
          }
        }
      }
    }
  }

  protected void scan_WithClusteringKeyEndRangeWithMaxValue_ShouldReturnProperResult(
      List<Value<?>> clusteringKeyValues,
      DataType clusteringKeyType,
      Order clusteringOrder,
      boolean endInclusive,
      boolean reverse)
      throws ExecutionException, IOException {
    // Arrange
    Value<?> endClusteringKey = getMaxValue(CLUSTERING_KEY, clusteringKeyType);

    Scan scan =
        new Scan(getPartitionKey())
            .withEnd(new Key(endClusteringKey), endInclusive)
            .withOrdering(
                new Ordering(
                    CLUSTERING_KEY,
                    reverse ? TestUtils.reverseOrder(clusteringOrder) : clusteringOrder))
            .forNamespace(namespace)
            .forTable(getTableName(clusteringKeyType, clusteringOrder));

    List<Value<?>> expected =
        getExpected(clusteringKeyValues, null, null, endClusteringKey, endInclusive, reverse);

    // Act
    List<Result> actual = scanAll(scan);

    // Assert
    assertScanResult(actual, expected, description(clusteringKeyType, null, endInclusive, reverse));
  }

  private List<Value<?>> prepareRecords(DataType clusteringKeyType, Order clusteringOrder)
      throws ExecutionException {
    RANDOM.setSeed(seed);

    List<Value<?>> ret = new ArrayList<>();
    List<Put> puts = new ArrayList<>();

    if (clusteringKeyType == DataType.BOOLEAN) {
      TestUtils.booleanValues(CLUSTERING_KEY)
          .forEach(
              clusteringKeyValue -> {
                ret.add(clusteringKeyValue);
                puts.add(preparePut(clusteringKeyType, clusteringOrder, clusteringKeyValue));
              });
    } else {
      Set<Value<?>> valueSet = new HashSet<>();

      // Add min and max clustering key values
      Arrays.asList(
              getMinValue(CLUSTERING_KEY, clusteringKeyType),
              getMaxValue(CLUSTERING_KEY, clusteringKeyType))
          .forEach(
              clusteringKeyValue -> {
                valueSet.add(clusteringKeyValue);
                ret.add(clusteringKeyValue);
                puts.add(preparePut(clusteringKeyType, clusteringOrder, clusteringKeyValue));
              });

      IntStream.range(0, CLUSTERING_KEY_NUM - 2)
          .forEach(
              i -> {
                Value<?> clusteringKeyValue;
                while (true) {
                  clusteringKeyValue = getRandomValue(RANDOM, CLUSTERING_KEY, clusteringKeyType);
                  // reject duplication
                  if (!valueSet.contains(clusteringKeyValue)) {
                    valueSet.add(clusteringKeyValue);
                    break;
                  }
                }

                ret.add(clusteringKeyValue);
                puts.add(preparePut(clusteringKeyType, clusteringOrder, clusteringKeyValue));
              });
    }
    try {
      List<Put> buffer = new ArrayList<>();
      for (Put put : puts) {
        buffer.add(put);
        if (buffer.size() == 20) {
          storage.mutate(buffer);
          buffer.clear();
        }
      }
      if (!buffer.isEmpty()) {
        storage.mutate(buffer);
      }
    } catch (ExecutionException e) {
      throw new ExecutionException("put data to database failed", e);
    }

    ret.sort(
        clusteringOrder == Order.ASC
            ? com.google.common.collect.Ordering.natural()
            : com.google.common.collect.Ordering.natural().reverse());
    return ret;
  }

  private Put preparePut(
      DataType clusteringKeyType, Order clusteringOrder, Value<?> clusteringKeyValue) {
    return new Put(getPartitionKey(), new Key(clusteringKeyValue))
        .withValue(COL_NAME, 1)
        .forNamespace(namespace)
        .forTable(getTableName(clusteringKeyType, clusteringOrder));
  }

  private Key getPartitionKey() {
    return new Key(PARTITION_KEY, 1);
  }

  protected Value<?> getRandomValue(Random random, String columnName, DataType dataType) {
    return TestUtils.getRandomValue(random, columnName, dataType);
  }

  protected Value<?> getMinValue(String columnName, DataType dataType) {
    return TestUtils.getMinValue(columnName, dataType);
  }

  protected Value<?> getMaxValue(String columnName, DataType dataType) {
    return TestUtils.getMaxValue(columnName, dataType);
  }

  private String description(
      DataType clusteringKeyType,
      @Nullable Boolean startInclusive,
      @Nullable Boolean endInclusive,
      boolean reverse) {
    StringBuilder builder = new StringBuilder();
    builder.append(String.format("failed with clusteringKeyType: %s", clusteringKeyType));
    if (startInclusive != null) {
      builder.append(String.format(", startInclusive: %s", startInclusive));
    }
    if (endInclusive != null) {
      builder.append(String.format(", endInclusive: %s", endInclusive));
    }
    builder.append(String.format(", reverse: %s", reverse));
    return builder.toString();
  }

  private List<Result> scanAll(Scan scan) throws ExecutionException, IOException {
    try (Scanner scanner = storage.scan(scan)) {
      return scanner.all();
    }
  }

  private List<Value<?>> getExpected(
      List<Value<?>> clusteringKeyValues,
      @Nullable Value<?> startClusteringKeyValue,
      @Nullable Boolean startInclusive,
      @Nullable Value<?> endClusteringKeyValue,
      @Nullable Boolean endInclusive,
      boolean reverse) {
    List<Value<?>> ret = new ArrayList<>();
    for (Value<?> clusteringKeyValue : clusteringKeyValues) {

      if (startClusteringKeyValue != null && startInclusive != null) {
        int compare =
            Objects.compare(
                clusteringKeyValue,
                startClusteringKeyValue,
                com.google.common.collect.Ordering.natural());
        if (!(startInclusive ? compare >= 0 : compare > 0)) {
          continue;
        }
      }
      if (endClusteringKeyValue != null && endInclusive != null) {
        int compare =
            Objects.compare(
                clusteringKeyValue,
                endClusteringKeyValue,
                com.google.common.collect.Ordering.natural());
        if (!(endInclusive ? compare <= 0 : compare < 0)) {
          continue;
        }
      }
      ret.add(clusteringKeyValue);
    }

    if (reverse) {
      Collections.reverse(ret);
    }
    return ret;
  }

  private void assertScanResult(
      List<Result> actualResults, List<Value<?>> expected, String description) {
    List<Value<?>> actual = new ArrayList<>();
    for (Result actualResult : actualResults) {
      assertThat(actualResult.getValue(CLUSTERING_KEY).isPresent()).isTrue();
      actual.add(actualResult.getValue(CLUSTERING_KEY).get());
    }
    assertThat(actual).describedAs(description).isEqualTo(expected);
  }
}