package com.scalar.db.storage.rpc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scalar.db.api.Delete;
import com.scalar.db.api.Get;
import com.scalar.db.api.Mutation;
import com.scalar.db.api.Put;
import com.scalar.db.api.Scan;
import com.scalar.db.exception.storage.ExecutionException;
import com.scalar.db.exception.storage.NoMutationException;
import com.scalar.db.io.Key;
import com.scalar.db.rpc.DistributedStorageGrpc;
import com.scalar.db.rpc.GetResponse;
import com.scalar.db.rpc.OpenScannerResponse;
import io.grpc.Status;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GrpcStorageTest {

  @Mock private DistributedStorageGrpc.DistributedStorageBlockingStub stub;
  @Mock private GrpcTableMetadataManager metadataManager;

  private GrpcStorage storage;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    // Arrange
    storage = new GrpcStorage(stub, metadataManager);
    storage.with("namespace", "table");
  }

  @Test
  public void get_isCalledWithProperArguments_StubShouldBeCalledProperly()
      throws ExecutionException {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Get get = new Get(partitionKey);
    when(stub.get(any())).thenReturn(GetResponse.newBuilder().build());

    // Act
    storage.get(get);

    // Assert
    verify(stub).get(any());
  }

  @Test
  public void get_StubThrowInvalidArgumentError_ShouldThrowIllegalArgumentException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Get get = new Get(partitionKey);
    when(stub.get(any())).thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> storage.get(get)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void get_StubThrowInternalError_ShouldThrowExecutionException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Get get = new Get(partitionKey);
    when(stub.get(any())).thenThrow(Status.INTERNAL.asRuntimeException());

    // Act
    assertThatThrownBy(() -> storage.get(get)).isInstanceOf(ExecutionException.class);
  }

  @Test
  public void scan_isCalledWithProperArguments_StubShouldBeCalledProperly()
      throws ExecutionException {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Scan scan = new Scan(partitionKey);
    when(stub.openScanner(any())).thenReturn(OpenScannerResponse.newBuilder().build());

    // Act
    storage.scan(scan);

    // Assert
    verify(stub).openScanner(any());
  }

  @Test
  public void scan_StubThrowInvalidArgumentError_ShouldThrowIllegalArgumentException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Scan scan = new Scan(partitionKey);
    when(stub.openScanner(any())).thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> storage.scan(scan)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void scan_StubThrowInternalError_ShouldThrowExecutionException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Scan scan = new Scan(partitionKey);
    when(stub.openScanner(any())).thenThrow(Status.INTERNAL.asRuntimeException());

    // Act
    assertThatThrownBy(() -> storage.scan(scan)).isInstanceOf(ExecutionException.class);
  }

  @Test
  public void put_isCalledWithProperArguments_StubShouldBeCalledProperly()
      throws ExecutionException {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Put put = new Put(partitionKey);

    // Act
    storage.put(put);

    // Assert
    verify(stub).mutate(any());
  }

  @Test
  public void put_StubThrowInvalidArgumentError_ShouldThrowIllegalArgumentException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Put put = new Put(partitionKey);
    when(stub.mutate(any())).thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> storage.put(put)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void put_StubThrowFailedPreconditionError_ShouldThrowNoMutationException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Put put = new Put(partitionKey);
    when(stub.mutate(any())).thenThrow(Status.FAILED_PRECONDITION.asRuntimeException());

    // Act
    assertThatThrownBy(() -> storage.put(put)).isInstanceOf(NoMutationException.class);
  }

  @Test
  public void put_StubThrowInternalError_ShouldThrowExecutionException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Put put = new Put(partitionKey);
    when(stub.mutate(any())).thenThrow(Status.INTERNAL.asRuntimeException());

    // Act
    assertThatThrownBy(() -> storage.put(put)).isInstanceOf(ExecutionException.class);
  }

  @Test
  public void puts_isCalledWithProperArguments_StubShouldBeCalledProperly()
      throws ExecutionException {
    // Arrange
    Key partitionKey1 = Key.newBuilder().addInt("col1", 1).build();
    Key partitionKey2 = Key.newBuilder().addInt("col1", 2).build();
    List<Put> puts = Arrays.asList(new Put(partitionKey2), new Put(partitionKey1));

    // Act
    storage.put(puts);

    // Assert
    verify(stub).mutate(any());
  }

  @Test
  public void puts_StubThrowInvalidArgumentError_ShouldThrowIllegalArgumentException() {
    // Arrange
    Key partitionKey1 = Key.newBuilder().addInt("col1", 1).build();
    Key partitionKey2 = Key.newBuilder().addInt("col1", 2).build();
    List<Put> puts = Arrays.asList(new Put(partitionKey2), new Put(partitionKey1));
    when(stub.mutate(any())).thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> storage.put(puts)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void puts_StubThrowFailedPreconditionError_ShouldThrowNoMutationException() {
    // Arrange
    Key partitionKey1 = Key.newBuilder().addInt("col1", 1).build();
    Key partitionKey2 = Key.newBuilder().addInt("col1", 2).build();
    List<Put> puts = Arrays.asList(new Put(partitionKey2), new Put(partitionKey1));
    when(stub.mutate(any())).thenThrow(Status.FAILED_PRECONDITION.asRuntimeException());

    // Act
    assertThatThrownBy(() -> storage.put(puts)).isInstanceOf(NoMutationException.class);
  }

  @Test
  public void puts_StubThrowInternalError_ShouldThrowExecutionException() {
    // Arrange
    Key partitionKey1 = Key.newBuilder().addInt("col1", 1).build();
    Key partitionKey2 = Key.newBuilder().addInt("col1", 2).build();
    List<Put> puts = Arrays.asList(new Put(partitionKey2), new Put(partitionKey1));
    when(stub.mutate(any())).thenThrow(Status.INTERNAL.asRuntimeException());

    // Act
    assertThatThrownBy(() -> storage.put(puts)).isInstanceOf(ExecutionException.class);
  }

  @Test
  public void delete_isCalledWithProperArguments_StubShouldBeCalledProperly()
      throws ExecutionException {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Delete delete = new Delete(partitionKey);

    // Act
    storage.delete(delete);

    // Assert
    verify(stub).mutate(any());
  }

  @Test
  public void delete_StubThrowInvalidArgumentError_ShouldThrowIllegalArgumentException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Delete delete = new Delete(partitionKey);
    when(stub.mutate(any())).thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> storage.delete(delete)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void delete_StubThrowFailedPreconditionError_ShouldThrowNoMutationException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Delete delete = new Delete(partitionKey);
    when(stub.mutate(any())).thenThrow(Status.FAILED_PRECONDITION.asRuntimeException());

    // Act
    assertThatThrownBy(() -> storage.delete(delete)).isInstanceOf(NoMutationException.class);
  }

  @Test
  public void delete_StubThrowInternalError_ShouldThrowExecutionException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    Delete delete = new Delete(partitionKey);
    when(stub.mutate(any())).thenThrow(Status.INTERNAL.asRuntimeException());

    // Act
    assertThatThrownBy(() -> storage.delete(delete)).isInstanceOf(ExecutionException.class);
  }

  @Test
  public void deletes_isCalledWithProperArguments_StubShouldBeCalledProperly()
      throws ExecutionException {
    // Arrange
    Key partitionKey1 = Key.newBuilder().addInt("col1", 1).build();
    Key partitionKey2 = Key.newBuilder().addInt("col1", 2).build();
    List<Delete> deletes = Arrays.asList(new Delete(partitionKey2), new Delete(partitionKey1));

    // Act
    storage.delete(deletes);

    // Assert
    verify(stub).mutate(any());
  }

  @Test
  public void deletes_StubThrowInvalidArgumentError_ShouldThrowIllegalArgumentException() {
    // Arrange
    Key partitionKey1 = Key.newBuilder().addInt("col1", 1).build();
    Key partitionKey2 = Key.newBuilder().addInt("col1", 2).build();
    List<Delete> deletes = Arrays.asList(new Delete(partitionKey2), new Delete(partitionKey1));
    when(stub.mutate(any())).thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> storage.delete(deletes)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void deletes_StubThrowFailedPreconditionError_ShouldThrowNoMutationException() {
    // Arrange
    Key partitionKey1 = Key.newBuilder().addInt("col1", 1).build();
    Key partitionKey2 = Key.newBuilder().addInt("col1", 2).build();
    List<Delete> deletes = Arrays.asList(new Delete(partitionKey2), new Delete(partitionKey1));
    when(stub.mutate(any())).thenThrow(Status.FAILED_PRECONDITION.asRuntimeException());

    // Act
    assertThatThrownBy(() -> storage.delete(deletes)).isInstanceOf(NoMutationException.class);
  }

  @Test
  public void deletes_StubThrowInternalError_ShouldThrowExecutionException() {
    // Arrange
    Key partitionKey1 = Key.newBuilder().addInt("col1", 1).build();
    Key partitionKey2 = Key.newBuilder().addInt("col1", 2).build();
    List<Delete> deletes = Arrays.asList(new Delete(partitionKey2), new Delete(partitionKey1));
    when(stub.mutate(any())).thenThrow(Status.INTERNAL.asRuntimeException());

    // Act
    assertThatThrownBy(() -> storage.delete(deletes)).isInstanceOf(ExecutionException.class);
  }

  @Test
  public void mutate_isCalledWithProperArguments_StubShouldBeCalledProperly()
      throws ExecutionException {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    List<Mutation> mutations = Arrays.asList(new Put(partitionKey), new Delete(partitionKey));

    // Act
    storage.mutate(mutations);

    // Assert
    verify(stub).mutate(any());
  }

  @Test
  public void mutate_StubThrowInvalidArgumentError_ShouldThrowIllegalArgumentException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    List<Mutation> mutations = Arrays.asList(new Put(partitionKey), new Delete(partitionKey));
    when(stub.mutate(any())).thenThrow(Status.INVALID_ARGUMENT.asRuntimeException());

    // Act Assert
    assertThatThrownBy(() -> storage.mutate(mutations))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void mutate_StubThrowFailedPreconditionError_ShouldThrowNoMutationException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    List<Mutation> mutations = Arrays.asList(new Put(partitionKey), new Delete(partitionKey));
    when(stub.mutate(any())).thenThrow(Status.FAILED_PRECONDITION.asRuntimeException());

    // Act
    assertThatThrownBy(() -> storage.mutate(mutations)).isInstanceOf(NoMutationException.class);
  }

  @Test
  public void mutate_StubThrowInternalError_ShouldThrowExecutionException() {
    // Arrange
    Key partitionKey = Key.newBuilder().addInt("col1", 1).build();
    List<Mutation> mutations = Arrays.asList(new Put(partitionKey), new Delete(partitionKey));
    when(stub.mutate(any())).thenThrow(Status.INTERNAL.asRuntimeException());

    // Act
    assertThatThrownBy(() -> storage.mutate(mutations)).isInstanceOf(ExecutionException.class);
  }
}