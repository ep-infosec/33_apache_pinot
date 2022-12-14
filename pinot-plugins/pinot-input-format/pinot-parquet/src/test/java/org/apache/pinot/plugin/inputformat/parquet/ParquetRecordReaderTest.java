/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.plugin.inputformat.parquet;

import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.pinot.plugin.inputformat.avro.AvroUtils;
import org.apache.pinot.spi.data.FieldSpec;
import org.apache.pinot.spi.data.readers.AbstractRecordReaderTest;
import org.apache.pinot.spi.data.readers.GenericRow;
import org.apache.pinot.spi.data.readers.RecordReader;
import org.testng.Assert;
import org.testng.annotations.Test;


public class ParquetRecordReaderTest extends AbstractRecordReaderTest {
  private final File _dataFile = new File(_tempDir, "data.parquet");
  private final String _gzipFileName = "data.parquet.gz";
  private final File _testParquetFileWithInt96AndDecimal =
      new File(getClass().getClassLoader().getResource("test-file-with-int96-and-decimal.snappy.parquet").getFile());

  @Override
  protected RecordReader createRecordReader()
      throws Exception {
    ParquetRecordReader recordReader = new ParquetRecordReader();
    recordReader.init(_dataFile, _sourceFields, null);
    return recordReader;
  }

  @Override
  protected void writeRecordsToFile(List<Map<String, Object>> recordsToWrite)
      throws Exception {
    Schema schema = AvroUtils.getAvroSchemaFromPinotSchema(getPinotSchema());
    List<GenericRecord> records = new ArrayList<>();
    for (Map<String, Object> r : recordsToWrite) {
      GenericRecord record = new GenericData.Record(schema);
      for (FieldSpec fieldSpec : getPinotSchema().getAllFieldSpecs()) {
        record.put(fieldSpec.getName(), r.get(fieldSpec.getName()));
      }
      records.add(record);
    }
    try (ParquetWriter<GenericRecord> writer = ParquetUtils.getParquetAvroWriter(new Path(_dataFile.getAbsolutePath()),
        schema)) {
      for (GenericRecord record : records) {
        writer.write(record);
      }
    }
  }

  private void compressGzip(String sourcePath, String targetPath)
      throws IOException {
    try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(Paths.get(targetPath).toFile()))) {
      Files.copy(Paths.get(sourcePath), gos);
    }
  }

  @Test
  public void testParquetAvroRecordReader()
      throws IOException {
    ParquetAvroRecordReader avroRecordReader = new ParquetAvroRecordReader();
    avroRecordReader.init(_dataFile, null, new ParquetRecordReaderConfig());
    testReadParquetFile(avroRecordReader, SAMPLE_RECORDS_SIZE);
  }

  private void testReadParquetFile(RecordReader reader, int totalRecords)
      throws IOException {
    int numRecordsRead = 0;
    while (reader.hasNext()) {
      reader.next();
      numRecordsRead++;
    }
    Assert.assertEquals(numRecordsRead, totalRecords);
  }

  @Test
  public void testParquetNativeRecordReader()
      throws IOException {
    ParquetNativeRecordReader nativeRecordReader = new ParquetNativeRecordReader();
    nativeRecordReader.init(_testParquetFileWithInt96AndDecimal, ImmutableSet.of(), new ParquetRecordReaderConfig());
    testReadParquetFile(nativeRecordReader, 1965);
    nativeRecordReader.init(_dataFile, ImmutableSet.of(), new ParquetRecordReaderConfig());
    testReadParquetFile(nativeRecordReader, SAMPLE_RECORDS_SIZE);
  }

  @Test
  public void testFileMetadataParsing()
      throws IOException {
    final ParquetRecordReader parquetRecordReader = new ParquetRecordReader();
    File avroParquetFile = new File(getClass().getClassLoader().getResource("data-avro.parquet").getFile());
    parquetRecordReader.init(avroParquetFile, null, null);
    // Should be avro since file metadata has avro schema
    Assert.assertTrue(parquetRecordReader.useAvroParquetRecordReader());

    final ParquetRecordReader parquetRecordReader2 = new ParquetRecordReader();
    File nativeParquetFile = new File(getClass().getClassLoader().getResource("users.parquet").getFile());
    parquetRecordReader.init(nativeParquetFile, null, null);
    // Should be native since file metadata does not have avro schema
    Assert.assertFalse(parquetRecordReader.useAvroParquetRecordReader());
  }

  @Test
  public void testComparison()
      throws IOException {
    testComparison(_dataFile, SAMPLE_RECORDS_SIZE);
    testComparison(new File(getClass().getClassLoader().getResource("users.parquet").getFile()), 1);
    testComparison(new File(getClass().getClassLoader().getResource("test-comparison.gz.parquet").getFile()), 363667);
    testComparison(new File(getClass().getClassLoader().getResource("test-comparison.snappy.parquet").getFile()), 2870);
    testComparison(new File(getClass().getClassLoader().getResource("baseballStats.snappy.parquet").getFile()), 97889);
    testComparison(new File(getClass().getClassLoader().getResource("githubEvents.snappy.parquet").getFile()), 10000);
    testComparison(new File(getClass().getClassLoader().getResource("starbucksStores.snappy.parquet").getFile()), 6443);
    testComparison(new File(getClass().getClassLoader().getResource("airlineStats.snappy.parquet").getFile()), 19492);
    testComparison(new File(getClass().getClassLoader().getResource("githubActivities.gz.parquet").getFile()), 2000);
  }

  private void testComparison(File dataFile, int totalRecords)
      throws IOException {
    final ParquetRecordReader avroRecordReader = new ParquetRecordReader();
    ParquetRecordReaderConfig avroRecordReaderConfig = new ParquetRecordReaderConfig();
    avroRecordReaderConfig.setUseParquetAvroRecordReader(true);
    avroRecordReader.init(dataFile, null, avroRecordReaderConfig);
    final ParquetRecordReader nativeRecordReader = new ParquetRecordReader();
    ParquetRecordReaderConfig parquetRecordReaderConfig = new ParquetRecordReaderConfig();
    parquetRecordReaderConfig.setUseParquetNativeRecordReader(true);
    nativeRecordReader.init(dataFile, null, parquetRecordReaderConfig);
    Assert.assertTrue(avroRecordReader.useAvroParquetRecordReader());
    Assert.assertFalse(nativeRecordReader.useAvroParquetRecordReader());

    GenericRow avroReuse = new GenericRow();
    GenericRow nativeReuse = new GenericRow();
    int recordsRead = 0;
    while (avroRecordReader.hasNext()) {
      Assert.assertTrue(nativeRecordReader.hasNext());
      final GenericRow avroReaderRow = avroRecordReader.next(avroReuse);
      final GenericRow nativeReaderRow = nativeRecordReader.next(nativeReuse);
      Assert.assertEquals(nativeReaderRow.toString(), avroReaderRow.toString());
      Assert.assertTrue(avroReaderRow.equals(nativeReaderRow));
      recordsRead++;
    }
    Assert.assertEquals(recordsRead, totalRecords,
        "Message read from ParquetRecordReader doesn't match the expected number.");
  }

  @Test
  public void testGzipParquetRecordReader()
      throws IOException {
    compressGzip(_dataFile.getAbsolutePath(), String.format("%s/%s", _tempDir, _gzipFileName));
    final File gzDataFile = new File(_tempDir, _gzipFileName);
    ParquetRecordReader recordReader = new ParquetRecordReader();
    recordReader.init(gzDataFile, _sourceFields, null);
    testReadParquetFile(recordReader, SAMPLE_RECORDS_SIZE);
  }

  @Test
  public void testGzipParquetAvroRecordReader()
      throws IOException {
    ParquetAvroRecordReader avroRecordReader = new ParquetAvroRecordReader();
    compressGzip(_dataFile.getAbsolutePath(), String.format("%s/%s", _tempDir, _gzipFileName));
    final File gzDataFile = new File(_tempDir, _gzipFileName);
    avroRecordReader.init(gzDataFile, null, new ParquetRecordReaderConfig());
    testReadParquetFile(avroRecordReader, SAMPLE_RECORDS_SIZE);
  }

  @Test
  public void testGzipParquetNativeRecordReader()
      throws IOException {
    ParquetNativeRecordReader nativeRecordReader = new ParquetNativeRecordReader();

    final String gzParquetFileWithInt96AndDecimal =
        String.format("%s.gz", _testParquetFileWithInt96AndDecimal.getAbsolutePath());
    compressGzip(_testParquetFileWithInt96AndDecimal.getAbsolutePath(), gzParquetFileWithInt96AndDecimal);
    final File gzTestParquetFileWithInt96AndDecimal = new File(gzParquetFileWithInt96AndDecimal);
    nativeRecordReader.init(gzTestParquetFileWithInt96AndDecimal, ImmutableSet.of(), new ParquetRecordReaderConfig());
    testReadParquetFile(nativeRecordReader, 1965);

    compressGzip(_dataFile.getAbsolutePath(), String.format("%s/%s", _tempDir, _gzipFileName));
    final File gzDataFile = new File(_tempDir, _gzipFileName);
    nativeRecordReader.init(gzDataFile, ImmutableSet.of(), new ParquetRecordReaderConfig());
    testReadParquetFile(nativeRecordReader, SAMPLE_RECORDS_SIZE);
  }
}
