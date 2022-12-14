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
package org.apache.pinot.spi.data.readers;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;


/**
 * Utils for reading file records
 */
public class RecordReaderUtils {
  private RecordReaderUtils() {
  }

  public static BufferedReader getBufferedReader(File dataFile)
      throws IOException {
    return new BufferedReader(new InputStreamReader(getInputStream(dataFile), StandardCharsets.UTF_8));
  }

  public static BufferedInputStream getBufferedInputStream(File dataFile)
      throws IOException {
    return new BufferedInputStream(getInputStream(dataFile));
  }

  public static InputStream getInputStream(File dataFile)
      throws IOException {
    FileInputStream fileInputStream = new FileInputStream(dataFile);
    try {
      return new GZIPInputStream(fileInputStream);
    } catch (Exception e) {
      // NOTE: Cannot reuse the input stream because it might already be read.
      fileInputStream.close();
      return new FileInputStream(dataFile);
    }
  }

  public static File unpackIfRequired(File dataFile, String extension)
      throws IOException {
    if (isGZippedFile(dataFile)) {
      try (final InputStream inputStream = getInputStream(dataFile)) {
        File targetFile = new File(String.format("%s.%s", dataFile.getAbsolutePath(), extension));
        Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return targetFile;
      }
    } else {
      return dataFile;
    }
  }

  private static boolean isGZippedFile(File file)
      throws IOException {
    int magic = 0;
    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
      magic = raf.read() & 0xff | ((raf.read() << 8) & 0xff00);
    }
    return magic == GZIPInputStream.GZIP_MAGIC;
  }
}
