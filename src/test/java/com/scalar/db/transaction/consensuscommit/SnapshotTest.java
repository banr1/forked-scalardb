package com.scalar.db.transaction.consensuscommit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.scalar.db.api.Consistency;
import com.scalar.db.api.Delete;
import com.scalar.db.api.DistributedStorage;
import com.scalar.db.api.Get;
import com.scalar.db.api.Isolation;
import com.scalar.db.api.Put;
import com.scalar.db.api.Result;
import com.scalar.db.api.Scan;
import com.scalar.db.api.Scanner;
import com.scalar.db.exception.storage.ExecutionException;
import com.scalar.db.exception.transaction.CommitConflictException;
import com.scalar.db.exception.transaction.CrudRuntimeException;
import com.scalar.db.io.Key;
import com.scalar.db.io.TextValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** */
public class SnapshotTest {
  private static final String ANY_KEYSPACE_NAME = "keyspace";
  private static final String ANY_TABLE_NAME = "table";
  private static final String ANY_ID = "id";
  private static final String ANY_NAME_1 = "name1";
  private static final String ANY_NAME_2 = "name2";
  private static final String ANY_NAME_3 = "name3";
  private static final String ANY_NAME_4 = "name4";
  private static final String ANY_NAME_5 = "name5";
  private static final String ANY_NAME_6 = "name6";
  private static final String ANY_TEXT_1 = "text1";
  private static final String ANY_TEXT_2 = "text2";
  private static final String ANY_TEXT_3 = "text3";
  private static final String ANY_TEXT_4 = "text4";
  private static final String ANY_TEXT_5 = "text5";
  private static final String ANY_TEXT_6 = "text6";
  private Snapshot snapshot;
  private Map<Snapshot.Key, Optional<TransactionResult>> readSet;
  private Map<Scan, Optional<List<Snapshot.Key>>> scanSet;
  private Map<Snapshot.Key, Put> writeSet;
  private Map<Snapshot.Key, Delete> deleteSet;

  @Mock private PrepareMutationComposer prepareComposer;
  @Mock private CommitMutationComposer commitComposer;
  @Mock private RollbackMutationComposer rollbackComposer;
  @Mock private TransactionResult result;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  private Snapshot prepareSnapshot(Isolation isolation) {
    return prepareSnapshot(isolation, SerializableStrategy.EXTRA_WRITE);
  }

  private Snapshot prepareSnapshot(Isolation isolation, SerializableStrategy strategy) {
    readSet = new ConcurrentHashMap<>();
    scanSet = new ConcurrentHashMap<>();
    writeSet = new ConcurrentHashMap<>();
    deleteSet = new ConcurrentHashMap<>();

    return new Snapshot(ANY_ID, isolation, strategy, readSet, scanSet, writeSet, deleteSet);
  }

  private Get prepareGet() {
    Key partitionKey = new Key(new TextValue(ANY_NAME_1, ANY_TEXT_1));
    Key clusteringKey = new Key(new TextValue(ANY_NAME_2, ANY_TEXT_2));
    return new Get(partitionKey, clusteringKey)
        .withConsistency(Consistency.LINEARIZABLE)
        .forNamespace(ANY_KEYSPACE_NAME)
        .forTable(ANY_TABLE_NAME);
  }

  private Get prepareAnotherGet() {
    Key partitionKey = new Key(new TextValue(ANY_NAME_5, ANY_TEXT_5));
    Key clusteringKey = new Key(new TextValue(ANY_NAME_6, ANY_TEXT_6));
    return new Get(partitionKey, clusteringKey)
        .withConsistency(Consistency.LINEARIZABLE)
        .forNamespace(ANY_KEYSPACE_NAME)
        .forTable(ANY_TABLE_NAME);
  }

  private Scan prepareScan() {
    Key partitionKey = new Key(new TextValue(ANY_NAME_1, ANY_TEXT_1));
    Key clusteringKey = new Key(new TextValue(ANY_NAME_2, ANY_TEXT_2));
    return new Scan(partitionKey)
        .withStart(clusteringKey)
        .withConsistency(Consistency.LINEARIZABLE)
        .forNamespace(ANY_KEYSPACE_NAME)
        .forTable(ANY_TABLE_NAME);
  }

  private Put preparePut() {
    Key partitionKey = new Key(new TextValue(ANY_NAME_1, ANY_TEXT_1));
    Key clusteringKey = new Key(new TextValue(ANY_NAME_2, ANY_TEXT_2));
    return new Put(partitionKey, clusteringKey)
        .withConsistency(Consistency.LINEARIZABLE)
        .forNamespace(ANY_KEYSPACE_NAME)
        .forTable(ANY_TABLE_NAME)
        .withValue(new TextValue(ANY_NAME_3, ANY_TEXT_3))
        .withValue(new TextValue(ANY_NAME_4, ANY_TEXT_4));
  }

  private Put prepareAnotherPut() {
    Key partitionKey = new Key(new TextValue(ANY_NAME_5, ANY_TEXT_5));
    Key clusteringKey = new Key(new TextValue(ANY_NAME_6, ANY_TEXT_6));
    return new Put(partitionKey, clusteringKey)
        .withConsistency(Consistency.LINEARIZABLE)
        .forNamespace(ANY_KEYSPACE_NAME)
        .forTable(ANY_TABLE_NAME);
  }

  private Delete prepareDelete() {
    Key partitionKey = new Key(new TextValue(ANY_NAME_1, ANY_TEXT_1));
    Key clusteringKey = new Key(new TextValue(ANY_NAME_2, ANY_TEXT_2));
    return new Delete(partitionKey, clusteringKey)
        .withConsistency(Consistency.LINEARIZABLE)
        .forNamespace(ANY_KEYSPACE_NAME)
        .forTable(ANY_TABLE_NAME);
  }

  private void configureBehavior() {
    doNothing().when(prepareComposer).add(any(Put.class), any(TransactionResult.class));
    doNothing().when(prepareComposer).add(any(Delete.class), any(TransactionResult.class));
    doNothing().when(commitComposer).add(any(Put.class), any(TransactionResult.class));
    doNothing().when(commitComposer).add(any(Delete.class), any(TransactionResult.class));
    doNothing().when(rollbackComposer).add(any(Put.class), any(TransactionResult.class));
    doNothing().when(rollbackComposer).add(any(Delete.class), any(TransactionResult.class));
  }

  @Test
  public void put_ResultGiven_ShouldHoldWhatsGivenInReadSet() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SNAPSHOT);
    Snapshot.Key key = new Snapshot.Key(prepareGet());

    // Act
    snapshot.put(key, Optional.of(result));

    // Assert
    assertThat(readSet.get(key)).isEqualTo(Optional.of(result));
  }

  @Test
  public void put_PutGiven_ShouldHoldWhatsGivenInWriteSet() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SNAPSHOT);
    Put put = preparePut();
    Snapshot.Key key = new Snapshot.Key(put);

    // Act
    snapshot.put(key, put);

    // Assert
    assertThat(writeSet.get(key)).isEqualTo(put);
  }

  @Test
  public void put_DeleteGiven_ShouldHoldWhatsGivenInDeleteSet() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SNAPSHOT);
    Delete delete = prepareDelete();
    Snapshot.Key key = new Snapshot.Key(delete);

    // Act
    snapshot.put(key, delete);

    // Assert
    assertThat(deleteSet.get(key)).isEqualTo(delete);
  }

  @Test
  public void put_ScanGiven_ShouldHoldWhatsGivenInScanSet() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SNAPSHOT);
    Scan scan = prepareScan();
    Snapshot.Key key =
        new Snapshot.Key(
            scan.forNamespace().get(),
            scan.forTable().get(),
            scan.getPartitionKey(),
            scan.getStartClusteringKey().get());
    List<Snapshot.Key> expected = Arrays.asList(key);

    // Act
    snapshot.put(scan, Optional.of(expected));

    // Assert
    assertThat(scanSet.get(scan).get()).isEqualTo(expected);
  }

  @Test
  public void get_KeyGivenContainedInWriteSet_ShouldReturnFromWriteSet() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SNAPSHOT);
    Put put = preparePut();
    Snapshot.Key key = new Snapshot.Key(prepareGet());
    snapshot.put(key, Optional.of(result));
    snapshot.put(key, put);

    // Act Assert
    assertThatThrownBy(
            () -> {
              snapshot.get(key);
            })
        .isInstanceOf(CrudRuntimeException.class);
  }

  @Test
  public void get_KeyGivenContainedInReadSet_ShouldReturnFromReadSet() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SNAPSHOT);
    Snapshot.Key key = new Snapshot.Key(prepareGet());
    snapshot.put(key, Optional.of(result));

    // Act
    Optional<TransactionResult> actual = snapshot.get(key);

    // Assert
    assertThat(actual).isEqualTo(Optional.of(result));
  }

  @Test
  public void get_KeyGivenNotContainedInSnapshot_ShouldReturnEmpty() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SNAPSHOT);
    Snapshot.Key key = new Snapshot.Key(prepareGet());

    // Act
    Optional<TransactionResult> result = snapshot.get(key);

    // Assert
    assertThat(result.isPresent()).isFalse();
  }

  @Test
  public void get_ScanNotContainedInSnapshotGiven_ShouldReturnEmptyList() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SNAPSHOT);
    Scan scan = prepareScan();

    // Act
    Optional<List<Snapshot.Key>> keys = snapshot.get(scan);

    // Assert
    assertThat(keys.isPresent()).isFalse();
  }

  @Test
  public void to_PrepareMutationComposerGivenAndSnapshotIsolationSet_ShouldCallComposerProperly() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SNAPSHOT);
    Put put = preparePut();
    Delete delete = prepareDelete();
    snapshot.put(new Snapshot.Key(prepareGet()), Optional.of(result));
    snapshot.put(new Snapshot.Key(put), put);
    snapshot.put(new Snapshot.Key(delete), delete);
    configureBehavior();

    // Act
    snapshot.to(prepareComposer);

    // Assert
    verify(prepareComposer).add(put, result);
    verify(prepareComposer).add(delete, result);
  }

  @Test
  public void
      to_PrepareMutationComposerGivenAndSerializableIsolationSet_ShouldCallComposerProperly() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SERIALIZABLE);
    Put put = preparePut();
    Delete delete = prepareDelete();
    snapshot.put(new Snapshot.Key(prepareGet()), Optional.of(result));
    snapshot.put(new Snapshot.Key(prepareAnotherGet()), Optional.of(result));
    snapshot.put(new Snapshot.Key(put), put);
    snapshot.put(new Snapshot.Key(delete), delete);
    configureBehavior();

    // Act
    snapshot.to(prepareComposer);

    // Assert
    Put putFromGet = prepareAnotherPut();
    verify(prepareComposer).add(put, result);
    verify(prepareComposer).add(putFromGet, result);
    verify(prepareComposer).add(delete, result);
  }

  @Test
  public void to_CommitMutationComposerGiven_ShouldCallComposerProperly() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SNAPSHOT);
    Put put = preparePut();
    Delete delete = prepareDelete();
    snapshot.put(new Snapshot.Key(prepareGet()), Optional.of(result));
    snapshot.put(new Snapshot.Key(put), put);
    snapshot.put(new Snapshot.Key(delete), delete);

    // Act
    snapshot.to(commitComposer);

    // Assert
    verify(commitComposer).add(put, result);
    verify(commitComposer).add(delete, result);
  }

  @Test
  public void to_CommitMutationComposerGivenSerializableIsolationSet_ShouldCallComposerProperly() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SERIALIZABLE);
    Put put = preparePut();
    Delete delete = prepareDelete();
    snapshot.put(new Snapshot.Key(prepareGet()), Optional.of(result));
    snapshot.put(new Snapshot.Key(put), put);
    snapshot.put(new Snapshot.Key(delete), delete);
    configureBehavior();

    // Act
    snapshot.to(commitComposer);

    // Assert
    // no effect on CommitMutationComposer
    verify(commitComposer).add(put, result);
    verify(commitComposer).add(delete, result);
  }

  @Test
  public void to_RollbackMutationComposerGiven_ShouldCallComposerProperly() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SNAPSHOT);
    Put put = preparePut();
    Delete delete = prepareDelete();
    snapshot.put(new Snapshot.Key(prepareGet()), Optional.of(result));
    snapshot.put(new Snapshot.Key(put), put);
    snapshot.put(new Snapshot.Key(delete), delete);
    configureBehavior();

    // Act
    snapshot.to(rollbackComposer);

    // Assert
    verify(rollbackComposer).add(put, result);
    verify(rollbackComposer).add(delete, result);
  }

  @Test
  public void
      to_RollbackMutationComposerGivenSerializableIsolationSet_ShouldCallComposerProperly() {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SERIALIZABLE);
    Put put = preparePut();
    Delete delete = prepareDelete();
    snapshot.put(new Snapshot.Key(prepareGet()), Optional.of(result));
    snapshot.put(new Snapshot.Key(put), put);
    snapshot.put(new Snapshot.Key(delete), delete);
    configureBehavior();

    // Act
    snapshot.to(rollbackComposer);

    // Assert
    // no effect on RollbackMutationComposer
    verify(rollbackComposer).add(put, result);
    verify(rollbackComposer).add(delete, result);
  }

  @Test
  public void toSerializableWithExtraRead_ReadSetNotChanged_ShouldProcessWithoutExceptions()
      throws ExecutionException {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SERIALIZABLE, SerializableStrategy.EXTRA_READ);
    Get get = prepareAnotherGet();
    Put put = preparePut();
    when(result.getValues()).thenReturn(ImmutableMap.of(Attribute.ID, new TextValue(ANY_ID)));
    TransactionResult txResult = new TransactionResult(result);
    snapshot.put(new Snapshot.Key(get), Optional.of(txResult));
    snapshot.put(new Snapshot.Key(put), put);
    DistributedStorage storage = mock(DistributedStorage.class);
    when(storage.get(get)).thenReturn(Optional.of(txResult));

    // Act Assert
    assertThatCode(() -> snapshot.toSerializableWithExtraRead(storage)).doesNotThrowAnyException();

    // Assert
    verify(storage).get(get);
  }

  @Test
  public void toSerializableWithExtraRead_ReadSetUpdated_ShouldThrowCommitConflictException()
      throws ExecutionException {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SERIALIZABLE, SerializableStrategy.EXTRA_READ);
    Get get = prepareAnotherGet();
    Put put = preparePut();
    when(result.getValues())
        .thenReturn(ImmutableMap.of(Attribute.ID, new TextValue(ANY_ID)))
        .thenReturn(ImmutableMap.of(Attribute.ID, new TextValue(ANY_ID + "x")));
    TransactionResult txResult = new TransactionResult(result);
    snapshot.put(new Snapshot.Key(get), Optional.of(txResult));
    snapshot.put(new Snapshot.Key(put), put);
    DistributedStorage storage = mock(DistributedStorage.class);
    TransactionResult changedTxResult = new TransactionResult(result);
    when(storage.get(get)).thenReturn(Optional.of(changedTxResult));

    // Act Assert
    assertThatThrownBy(
            () -> {
              snapshot.toSerializableWithExtraRead(storage);
            })
        .isInstanceOf(CommitConflictException.class);

    // Assert
    verify(storage).get(get);
  }

  @Test
  public void toSerializableWithExtraRead_ReadSetExtended_ShouldThrowCommitConflictException()
      throws ExecutionException {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SERIALIZABLE, SerializableStrategy.EXTRA_READ);
    Get get = prepareAnotherGet();
    Put put = preparePut();
    snapshot.put(new Snapshot.Key(get), Optional.empty());
    snapshot.put(new Snapshot.Key(put), put);
    DistributedStorage storage = mock(DistributedStorage.class);
    when(result.getValues()).thenReturn(ImmutableMap.of());
    TransactionResult txResult = new TransactionResult(result);
    when(storage.get(get)).thenReturn(Optional.of(txResult));

    // Act Assert
    assertThatThrownBy(() -> snapshot.toSerializableWithExtraRead(storage))
        .isInstanceOf(CommitConflictException.class);

    // Assert
    verify(storage).get(get);
  }

  @Test
  public void toSerializableWithExtraRead_ScanSetNotChanged_ShouldProcessWithoutExceptions()
      throws ExecutionException {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SERIALIZABLE, SerializableStrategy.EXTRA_READ);
    Scan scan = prepareScan();
    Get get = prepareGet();
    Put put = preparePut();
    when(result.getValues()).thenReturn(ImmutableMap.of(Attribute.ID, new TextValue(ANY_ID)));
    TransactionResult txResult = new TransactionResult(result);
    Snapshot.Key key = new Snapshot.Key(get);
    snapshot.put(key, Optional.of(txResult));
    snapshot.put(scan, Optional.of(Arrays.asList(key)));
    snapshot.put(new Snapshot.Key(put), put);
    DistributedStorage storage = mock(DistributedStorage.class);
    Scanner scanner = mock(Scanner.class);
    when(scanner.iterator()).thenReturn(Arrays.asList((Result) txResult).iterator());
    when(storage.scan(scan)).thenReturn(scanner);

    // Act Assert
    assertThatCode(() -> snapshot.toSerializableWithExtraRead(storage)).doesNotThrowAnyException();

    // Assert
    verify(storage).scan(scan);
  }

  @Test
  public void toSerializableWithExtraRead_ScanSetUpdated_ShouldProcessWithoutExceptions()
      throws ExecutionException {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SERIALIZABLE, SerializableStrategy.EXTRA_READ);
    Scan scan = prepareScan();
    Get get = prepareGet();
    Put put = preparePut();
    when(result.getValues())
        .thenReturn(ImmutableMap.of(Attribute.ID, new TextValue(ANY_ID)))
        .thenReturn(ImmutableMap.of(Attribute.ID, new TextValue(ANY_ID + "x")));
    TransactionResult txResult = new TransactionResult(result);
    Snapshot.Key key = new Snapshot.Key(get);
    snapshot.put(key, Optional.of(txResult));
    snapshot.put(scan, Optional.of(Arrays.asList(key)));
    snapshot.put(new Snapshot.Key(put), put);
    DistributedStorage storage = mock(DistributedStorage.class);
    TransactionResult changedTxResult = new TransactionResult(result);
    Scanner scanner = mock(Scanner.class);
    when(scanner.iterator()).thenReturn(Arrays.asList((Result) changedTxResult).iterator());
    when(storage.scan(scan)).thenReturn(scanner);

    // Act Assert
    assertThatThrownBy(() -> snapshot.toSerializableWithExtraRead(storage))
        .isInstanceOf(CommitConflictException.class);

    // Assert
    verify(storage).scan(scan);
  }

  @Test
  public void toSerializableWithExtraRead_ScanSetExtended_ShouldProcessWithoutExceptions()
      throws ExecutionException {
    // Arrange
    snapshot = prepareSnapshot(Isolation.SERIALIZABLE, SerializableStrategy.EXTRA_READ);
    Scan scan = prepareScan();
    Put put = preparePut();
    when(result.getValues()).thenReturn(ImmutableMap.of(Attribute.ID, new TextValue(ANY_ID + "x")));
    snapshot.put(scan, Optional.of(Collections.emptyList()));
    snapshot.put(new Snapshot.Key(put), put);
    DistributedStorage storage = mock(DistributedStorage.class);
    TransactionResult txResult = new TransactionResult(result);
    Scanner scanner = mock(Scanner.class);
    when(scanner.iterator()).thenReturn(Arrays.asList((Result) txResult).iterator());
    when(storage.scan(scan)).thenReturn(scanner);

    // Act Assert
    assertThatThrownBy(() -> snapshot.toSerializableWithExtraRead(storage))
        .isInstanceOf(CommitConflictException.class);

    // Assert
    verify(storage).scan(scan);
  }
}