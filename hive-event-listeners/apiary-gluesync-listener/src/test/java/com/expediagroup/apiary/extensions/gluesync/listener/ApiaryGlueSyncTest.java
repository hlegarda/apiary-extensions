/**
 * Copyright (C) 2018-2024 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.expediagroup.apiary.extensions.gluesync.listener;

import static java.util.Arrays.asList;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static com.google.common.collect.Maps.newHashMap;

import static com.expediagroup.apiary.extensions.gluesync.listener.IcebergTableOperations.simpleIcebergPartitionSpec;
import static com.expediagroup.apiary.extensions.gluesync.listener.IcebergTableOperations.simpleIcebergSchema;
import static com.expediagroup.apiary.extensions.gluesync.listener.IcebergTableOperations.simpleIcebergTable;
import static com.expediagroup.apiary.extensions.gluesync.listener.service.GlueTableService.APIARY_GLUESYNC_SKIP_ARCHIVE_TABLE_PARAM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.SerDeInfo;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.events.AddPartitionEvent;
import org.apache.hadoop.hive.metastore.events.AlterTableEvent;
import org.apache.hadoop.hive.metastore.events.CreateDatabaseEvent;
import org.apache.hadoop.hive.metastore.events.CreateTableEvent;
import org.apache.hadoop.hive.metastore.events.DropDatabaseEvent;
import org.apache.hadoop.hive.serde.serdeConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.amazonaws.services.glue.AWSGlue;
import com.amazonaws.services.glue.model.AlreadyExistsException;
import com.amazonaws.services.glue.model.BatchCreatePartitionRequest;
import com.amazonaws.services.glue.model.Column;
import com.amazonaws.services.glue.model.CreateDatabaseRequest;
import com.amazonaws.services.glue.model.CreatePartitionRequest;
import com.amazonaws.services.glue.model.CreateTableRequest;
import com.amazonaws.services.glue.model.CreateTableResult;
import com.amazonaws.services.glue.model.DeleteDatabaseRequest;
import com.amazonaws.services.glue.model.DeleteTableRequest;
import com.amazonaws.services.glue.model.EntityNotFoundException;
import com.amazonaws.services.glue.model.GetDatabaseRequest;
import com.amazonaws.services.glue.model.GetDatabaseResult;
import com.amazonaws.services.glue.model.GetPartitionsResult;
import com.amazonaws.services.glue.model.InvalidInputException;
import com.amazonaws.services.glue.model.PartitionInput;
import com.amazonaws.services.glue.model.TableInput;
import com.amazonaws.services.glue.model.UpdateDatabaseRequest;
import com.amazonaws.services.glue.model.UpdateTableRequest;
import com.google.common.collect.ImmutableMap;

import com.expediagroup.apiary.extensions.gluesync.listener.metrics.MetricConstants;
import com.expediagroup.apiary.extensions.gluesync.listener.metrics.MetricService;

@RunWith(MockitoJUnitRunner.class)
public class ApiaryGlueSyncTest {

  @Mock
  private AWSGlue glueClient;
  @Mock
  private MetricService metricService;

  @Mock
  private Configuration configuration;
  @Mock
  private CreateTableResult createTableResult;

  @Captor
  private ArgumentCaptor<CreateTableRequest> createTableRequestCaptor;
  @Captor
  private ArgumentCaptor<UpdateTableRequest> updateTableRequestCaptor;
  @Captor
  private ArgumentCaptor<DeleteTableRequest> deleteTableRequestCaptor;
  @Captor
  private ArgumentCaptor<BatchCreatePartitionRequest> batchCreatePartitionRequestCaptor;
  @Captor
  private ArgumentCaptor<CreateDatabaseRequest> createDatabaseRequestCaptor;
  @Captor
  private ArgumentCaptor<UpdateDatabaseRequest> updateDatabaseRequestCaptor;
  @Captor
  private ArgumentCaptor<DeleteDatabaseRequest> deleteDatabaseRequestCaptor;
  @Captor
  private ArgumentCaptor<CreatePartitionRequest> createPartitionRequestCaptor;

  private final String tableName = "some_table";
  private final String dbName = "some_db";
  private final String[] colNames = { "col1", "col2", "col3" };
  private final String[] partNames = { "part1", "part2" };
  private final String locationUri = "uri";
  private final String description = "desc";
  private final Map<String, String> params = newHashMap(ImmutableMap.of("managed-by", "apiary-glue-sync"));

  private final String gluePrefix = "test_";
  private ApiaryGlueSync glueSync;

  @Before
  public void setup() {
    glueSync = new ApiaryGlueSync(configuration, glueClient, gluePrefix, metricService);
    when(glueClient.createTable(any(CreateTableRequest.class))).thenReturn(createTableResult);
  }

  @Test
  public void onCreateDatabase() {
    CreateDatabaseEvent event = mock(CreateDatabaseEvent.class);
    when(event.getStatus()).thenReturn(true);
    when(event.getDatabase()).thenReturn(getDatabase(description, locationUri, params));

    glueSync.onCreateDatabase(event);

    verify(glueClient).createDatabase(createDatabaseRequestCaptor.capture());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_DATABASE_SUCCESS);
    CreateDatabaseRequest createDatabaseRequest = createDatabaseRequestCaptor.getValue();

    assertThat(createDatabaseRequest.getDatabaseInput().getName(), is(gluePrefix + dbName));
    assertThat(createDatabaseRequest.getDatabaseInput().getLocationUri(), is(locationUri));
    assertThat(createDatabaseRequest.getDatabaseInput().getParameters(), is(params));
    assertThat(createDatabaseRequest.getDatabaseInput().getDescription(), is(description));
  }

  @Test
  public void onCreateDatabaseThatAlreadyExists() {
    CreateDatabaseEvent event = mock(CreateDatabaseEvent.class);
    when(event.getStatus()).thenReturn(true);
    when(event.getDatabase()).thenReturn(getDatabase(description, locationUri, params));
    when(glueClient.createDatabase(any())).thenThrow(new AlreadyExistsException(""));

    glueSync.onCreateDatabase(event);

    verify(glueClient).createDatabase(createDatabaseRequestCaptor.capture());
    verify(glueClient).updateDatabase(updateDatabaseRequestCaptor.capture());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_DATABASE_SUCCESS);
    UpdateDatabaseRequest updateDatabaseRequest = updateDatabaseRequestCaptor.getValue();

    assertThat(updateDatabaseRequest.getName(), is(gluePrefix + dbName));
    assertThat(updateDatabaseRequest.getDatabaseInput().getName(), is(gluePrefix + dbName));
    assertThat(updateDatabaseRequest.getDatabaseInput().getLocationUri(), is(locationUri));
    assertThat(updateDatabaseRequest.getDatabaseInput().getParameters(), is(params));
    assertThat(updateDatabaseRequest.getDatabaseInput().getDescription(), is(description));
  }

  @Test
  public void onDropDatabase() {
    DropDatabaseEvent event = mock(DropDatabaseEvent.class);
    when(event.getStatus()).thenReturn(true);
    when(event.getDatabase()).thenReturn(getDatabase(description, locationUri, params));
    when(glueClient.getDatabase(any(GetDatabaseRequest.class))).thenReturn(getGlueDatabaseResult(params));

    glueSync.onDropDatabase(event);

    verify(glueClient).deleteDatabase(deleteDatabaseRequestCaptor.capture());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_DATABASE_SUCCESS);
    DeleteDatabaseRequest deleteDatabaseRequest = deleteDatabaseRequestCaptor.getValue();
    assertThat(deleteDatabaseRequest.getName(), is(gluePrefix + dbName));
  }

  @Test
  public void onDropDatabaseThatDoesntExist() {
    DropDatabaseEvent event = mock(DropDatabaseEvent.class);
    when(event.getStatus()).thenReturn(true);
    when(event.getDatabase()).thenReturn(getDatabase(description, locationUri, params));
    when(glueClient.getDatabase(any(GetDatabaseRequest.class))).thenReturn(
        getGlueDatabaseResult(params));
    when(glueClient.deleteDatabase(any())).thenThrow(new EntityNotFoundException(""));

    glueSync.onDropDatabase(event);

    verify(glueClient).getDatabase(any());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_DATABASE_SUCCESS);
    verify(glueClient).deleteDatabase(deleteDatabaseRequestCaptor.capture());
  }

  @Test
  public void onDropDatabaseNotCreatedByGlueSync() {
    DropDatabaseEvent event = mock(DropDatabaseEvent.class);
    when(event.getStatus()).thenReturn(true);
    when(event.getDatabase()).thenReturn(getDatabase(description, locationUri, Collections.emptyMap()));
    when(glueClient.getDatabase(any(GetDatabaseRequest.class))).thenReturn(
        getGlueDatabaseResult(Collections.emptyMap()));

    glueSync.onDropDatabase(event);

    verify(glueClient).getDatabase(any());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_DATABASE_SUCCESS);
    verifyNoMoreInteractions(glueClient);
  }

  @Test
  public void onCreateHiveTable() {
    CreateTableEvent event = mock(CreateTableEvent.class);
    when(event.getStatus()).thenReturn(true);

    Table table = simpleHiveTable(simpleSchema(), simplePartitioning());
    when(event.getTable()).thenReturn(table);

    glueSync.onCreateTable(event);

    verify(glueClient).createTable(createTableRequestCaptor.capture());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_TABLE_SUCCESS);
    CreateTableRequest createTableRequest = createTableRequestCaptor.getValue();

    assertThat(createTableRequest.getDatabaseName(), is(gluePrefix + dbName));
    assertThat(createTableRequest.getTableInput().getName(), is(tableName));
    assertThat(toList(createTableRequest.getTableInput().getPartitionKeys()), is(asList(partNames)));
    assertThat(toList(createTableRequest.getTableInput().getStorageDescriptor().getColumns()), is(asList(colNames)));
  }

  @Test
  public void onCreateHiveTable_withIncorrectFormat() {
    CreateTableEvent event = mock(CreateTableEvent.class);
    when(event.getStatus()).thenReturn(true);

    // Setting incorrect schema for one column
    List<FieldSchema> incorrectSchema = simpleSchema();
    incorrectSchema.get(0).setComment("incorrect_comment\uD999");
    Table table = simpleHiveTable(incorrectSchema, simplePartitioning());
    when(event.getTable()).thenReturn(table);
    when(glueClient.createTable(argThat(
        req -> "incorrect_comment\uD999".contentEquals(
            req.getTableInput().getStorageDescriptor().getColumns().get(0).getComment()))))
        .thenThrow(new InvalidInputException("Invalid input"));

    glueSync.onCreateTable(event);

    verify(glueClient, times(2)).createTable(createTableRequestCaptor.capture());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_TABLE_SUCCESS);
    CreateTableRequest createTableRequest = createTableRequestCaptor.getValue();

    assertThat(createTableRequest.getDatabaseName(), is(gluePrefix + dbName));
    TableInput tableInput = createTableRequest.getTableInput();
    assertThat(tableInput.getStorageDescriptor().getColumns().get(0).getComment(), is("incorrect_comment"));
  }

  @Test
  public void onCreateIcebergTable() {
    CreateTableEvent event = mock(CreateTableEvent.class);
    when(event.getStatus()).thenReturn(true);

    Table table = simpleIcebergTable(dbName, tableName, simpleIcebergSchema(), simpleIcebergPartitionSpec(), null);
    when(event.getTable()).thenReturn(table);

    glueSync.onCreateTable(event);

    verify(glueClient).createTable(createTableRequestCaptor.capture());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_TABLE_SUCCESS);
    CreateTableRequest createTableRequest = createTableRequestCaptor.getValue();

    assertThat(createTableRequest.getDatabaseName(), is(gluePrefix + dbName));
    assertThat(createTableRequest.getTableInput().getName(), is(tableName));
    assertThat(createTableRequest.getTableInput().getStorageDescriptor().getInputFormat(),
        is("org.apache.iceberg.mr.hive.HiveIcebergInputFormat"));
    assertThat(createTableRequest.getTableInput().getStorageDescriptor().getSerdeInfo().getSerializationLibrary(),
        is("org.apache.iceberg.mr.hive.HiveIcebergSerDe"));
    assertThat(toList(createTableRequest.getTableInput().getStorageDescriptor().getColumns()), is(asList(colNames)));
  }

  @Test
  public void onAlterHiveTable() {
    AlterTableEvent event = mock(AlterTableEvent.class);
    when(event.getStatus()).thenReturn(true);

    Table newTable = simpleHiveTable(simpleSchema(), simplePartitioning());
    int lastAccessTime = 10000000;
    newTable.setLastAccessTime(lastAccessTime);
    newTable.setTableName("table2");
    when(event.getOldTable()).thenReturn(newTable); // needed to check if is table rename
    when(event.getNewTable()).thenReturn(newTable);

    glueSync.onAlterTable(event);

    verify(glueClient).updateTable(updateTableRequestCaptor.capture());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_TABLE_SUCCESS);
    UpdateTableRequest updateTableRequest = updateTableRequestCaptor.getValue();

    assertThat(updateTableRequest.getDatabaseName(), is(gluePrefix + dbName));
    assertThat(updateTableRequest.getTableInput().getName(), is("table2"));
    assertThat(updateTableRequest.getTableInput().getLastAccessTime(), is(new Date(lastAccessTime)));
    assertThat(toList(updateTableRequest.getTableInput().getPartitionKeys()), is(asList(partNames)));
    assertThat(toList(updateTableRequest.getTableInput().getStorageDescriptor().getColumns()), is(asList(colNames)));
    assertThat(updateTableRequest.getSkipArchive(), is(true));
  }

  @Test
  public void onAlterHiveTableSkipArchiveOverride() {
    AlterTableEvent event = mock(AlterTableEvent.class);
    when(event.getStatus()).thenReturn(true);

    Table newTable = simpleHiveTable(simpleSchema(), simplePartitioning());
    int lastAccessTime = 10000000;
    newTable.setLastAccessTime(lastAccessTime);
    newTable.setTableName("table2");
    newTable.putToParameters(APIARY_GLUESYNC_SKIP_ARCHIVE_TABLE_PARAM, "false");
    when(event.getOldTable()).thenReturn(newTable); // needed to check if is table rename
    when(event.getNewTable()).thenReturn(newTable);

    glueSync.onAlterTable(event);

    verify(glueClient).updateTable(updateTableRequestCaptor.capture());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_TABLE_SUCCESS);
    UpdateTableRequest updateTableRequest = updateTableRequestCaptor.getValue();

    assertThat(updateTableRequest.getDatabaseName(), is(gluePrefix + dbName));
    assertThat(updateTableRequest.getTableInput().getName(), is("table2"));
    assertThat(updateTableRequest.getTableInput().getLastAccessTime(), is(new Date(lastAccessTime)));
    assertThat(toList(updateTableRequest.getTableInput().getPartitionKeys()), is(asList(partNames)));
    assertThat(toList(updateTableRequest.getTableInput().getStorageDescriptor().getColumns()), is(asList(colNames)));
    assertThat(updateTableRequest.getSkipArchive(), is(false));
  }

  @Test
  public void onAlterHiveTable_RenameTable() {
    AlterTableEvent event = mock(AlterTableEvent.class);
    when(event.getStatus()).thenReturn(true);

    Table oldTable = simpleHiveTable(simpleSchema(), simplePartitioning());
    int lastAccessTime = 10000000;
    oldTable.setLastAccessTime(lastAccessTime);
    oldTable.setTableName("table2");
    when(event.getOldTable()).thenReturn(oldTable);

    Table newTable = simpleHiveTable(simpleSchema(), simplePartitioning());
    newTable.setLastAccessTime(lastAccessTime);
    newTable.setTableName("table2_new");
    when(event.getNewTable()).thenReturn(newTable);

    when(glueClient.getPartitions(any())).thenReturn(new GetPartitionsResult().withPartitions(
        new com.amazonaws.services.glue.model.Partition().withValues("part1Value", "part2Value")));

    glueSync.onAlterTable(event);

    verify(glueClient).createTable(createTableRequestCaptor.capture());
    CreateTableRequest createTableRequest = createTableRequestCaptor.getValue();
    verify(glueClient).batchCreatePartition(batchCreatePartitionRequestCaptor.capture());
    BatchCreatePartitionRequest batchCreatePartitionRequest = batchCreatePartitionRequestCaptor.getValue();
    verify(glueClient).deleteTable(deleteTableRequestCaptor.capture());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_TABLE_SUCCESS);
    DeleteTableRequest deleteTableRequest = deleteTableRequestCaptor.getValue();

    // test create new table
    assertThat(createTableRequest.getDatabaseName(), is(gluePrefix + dbName));
    assertThat(createTableRequest.getTableInput().getName(), is("table2_new"));
    // test copied partitions
    assertThat(batchCreatePartitionRequest.getTableName(), is("table2_new"));
    List<String> partitionValues = Arrays.asList("part1Value", "part2Value");
    assertThat(batchCreatePartitionRequest.getPartitionInputList().get(0).getValues(), is(partitionValues));
    // test old table is deleted
    assertThat(deleteTableRequest.getDatabaseName(), is(gluePrefix + dbName));
    assertThat(deleteTableRequest.getName(), is("table2"));

    assertThat(createTableRequest.getTableInput().getLastAccessTime(), is(new Date(lastAccessTime)));
    assertThat(toList(createTableRequest.getTableInput().getPartitionKeys()), is(asList(partNames)));
    assertThat(toList(createTableRequest.getTableInput().getStorageDescriptor().getColumns()), is(asList(colNames)));
  }

  @Test
  public void onCreateUnpartitionedHiveTable() {
    CreateTableEvent event = mock(CreateTableEvent.class);
    when(event.getStatus()).thenReturn(true);

    Table table = simpleHiveTable(simpleSchema(), new ArrayList<>());
    when(event.getTable()).thenReturn(table);

    glueSync.onCreateTable(event);

    verify(glueClient).createTable(createTableRequestCaptor.capture());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_TABLE_SUCCESS);
    CreateTableRequest createTableRequest = createTableRequestCaptor.getValue();

    assertThat(createTableRequest.getDatabaseName(), is(gluePrefix + dbName));
    assertThat(createTableRequest.getTableInput().getName(), is(tableName));
    assertThat(createTableRequest.getTableInput().getPartitionKeys().size(), is(0));
    assertThat(toList(createTableRequest.getTableInput().getStorageDescriptor().getColumns()), is(asList(colNames)));
  }

  @Test
  public void onAddPartition_withIncorrectFormat() {
    AddPartitionEvent event = mock(AddPartitionEvent.class);
    when(event.getStatus()).thenReturn(true);

    // Input table
    List<FieldSchema> incorrectSchema = simpleSchema();
    incorrectSchema.get(0).setComment("incorrect_comment\uD999");
    Table table = simpleHiveTable(incorrectSchema, simplePartitioning());

    // Input Partition
    Partition partition = new Partition();
    partition.setValues(Arrays.asList("part1Value", "part2Value"));
    partition.setSd(table.getSd());

    when(event.getTable()).thenReturn(table);
    when(event.getPartitionIterator()).thenReturn(Arrays.asList(partition).iterator());
    when(glueClient.createPartition(argThat(
        req -> "incorrect_comment\uD999".contentEquals(
            req.getPartitionInput().getStorageDescriptor().getColumns().get(0).getComment()))))
        .thenThrow(new InvalidInputException("Invalid input"));

    glueSync.onAddPartition(event);

    verify(glueClient, times(2)).createPartition(createPartitionRequestCaptor.capture());
    verify(metricService).incrementCounter(MetricConstants.LISTENER_PARTITION_SUCCESS);
    CreatePartitionRequest createTableRequest = createPartitionRequestCaptor.getValue();

    assertThat(createTableRequest.getDatabaseName(), is(gluePrefix + dbName));
    PartitionInput tableInput = createTableRequest.getPartitionInput();
    assertThat(tableInput.getStorageDescriptor().getColumns().get(0).getComment(), is("incorrect_comment"));
  }

  private Table simpleHiveTable(List<FieldSchema> schema, List<FieldSchema> partitions) {
    Table table = new Table();
    table.setTableName(tableName);
    table.setDbName(dbName);

    StorageDescriptor sd = new StorageDescriptor();
    sd.setCols(schema);
    sd.setInputFormat("org.apache.hadoop.mapred.TextInputFormat");
    sd.setOutputFormat("org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat");
    sd.setSerdeInfo(new SerDeInfo());
    sd.getSerdeInfo().setParameters(new HashMap<>());
    sd.getSerdeInfo().getParameters().put(serdeConstants.SERIALIZATION_FORMAT, "1");
    sd.setSortCols(new ArrayList<>());
    table.setSd(sd);
    table.setPartitionKeys(partitions);

    return table;
  }

  private List<FieldSchema> simpleSchema() {
    List<FieldSchema> fields = new ArrayList<>();
    for (String colName : colNames) {
      fields.add(new FieldSchema(colName, "string", ""));
    }
    return fields;
  }

  private List<FieldSchema> simplePartitioning() {
    List<FieldSchema> partitions = new ArrayList<>();
    for (String partName : partNames) {
      partitions.add(new FieldSchema(partName, "string", ""));
    }
    return partitions;
  }

  private List<String> toList(List<Column> columns) {
    return columns.stream().map(Column::getName).collect(Collectors.toList());
  }

  private Database getDatabase(String description, String locationUri, Map<String, String> params) {
    Database database = new Database();
    database.setName(dbName);
    database.setDescription(description);
    database.setLocationUri(locationUri);
    database.setParameters(params);
    return database;
  }

  private GetDatabaseResult getGlueDatabaseResult(Map<String, String> params) {
    return new GetDatabaseResult().withDatabase(new com.amazonaws.services.glue.model.Database().withName(
        dbName).withParameters(params));
  }
}
