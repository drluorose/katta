/**
 * Copyright 2008 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.katta.index.indexer;

import java.io.IOException;

import net.sf.katta.util.Logger;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;

public class IndexUploader implements IIndexPublisher {

  private Path _ouputPath;
  private FileSystem _fileSystem;

  public void configure(final JobConf jobConf) throws Exception {
    final Path path = new Path(jobConf.get(IndexJobConf.INDEX_UPLOAD_PATH));
    _ouputPath = new Path(path, "indexes");
    _fileSystem = FileSystem.get(jobConf);
    _fileSystem.mkdirs(_ouputPath);

  }

  public void publish(final String pathToIndex) throws IOException {
    Logger.debug("copy '" + pathToIndex + "' to '" + _ouputPath + "'");
    _fileSystem.copyFromLocalFile(true, new Path(pathToIndex), _ouputPath);
  }

}
