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
package net.sf.katta.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An unsynchronized map like structure which maintains for each key a
 * {@link List} of values.
 * 
 * 
 */
public class One2ManyListMap<K, V> {

  protected final Map<K, List<V>> _map;

  public One2ManyListMap() {
    this(new HashMap<K, List<V>>());
  }

  public One2ManyListMap(Map<K, List<V>> map) {
    _map = map;
  }

  public void add(K key, V value) {
    List<V> values = _map.get(key);
    if (values == null) {
      values = new ArrayList<V>(3);
      _map.put(key, values);
    }
    values.add(value);
  }

  public void addAll(K key, Collection<V> newValues) {
    List<V> values = _map.get(key);
    if (values == null) {
      values = new ArrayList<V>(newValues.size());
      _map.put(key, values);
    }
    values.addAll(newValues);
  }

  /**
   * @param key
   * @return the list to the given key or an empty list
   */
  public List<V> getValues(K key) {
    List<V> values = _map.get(key);
    if (values == null) {
      values = Collections.EMPTY_LIST;
    }
    return values;
  }

  /**
   * Removes the value from the given key list.
   * 
   * @param key
   * @param value
   * @return if value has exists or not
   */
  public boolean removeValue(K key, V value) {
    List<V> values = _map.get(key);
    if (values == null) {
      return false;
    }

    boolean success = values.remove(value);
    if (values.size() == 0) {
      _map.remove(key);
    }

    return success;
  }

  /**
   * @param key
   * @return a list with all values belonging to the given key
   */
  public List<V> removeKey(K key) {
    List<V> list = getValues(key);
    _map.remove(key);
    return list;
  }

  /**
   * @param key
   * @return true if map contains the key
   */
  public boolean containsKey(K key) {
    return _map.containsKey(key);
  }

  /**
   * @see HashMap#keySet()
   * @return a set view of the keys contained in this map.
   */
  public Set<K> keySet() {
    return _map.keySet();
  }

  /**
   * @see HashMap#size()
   * @return the size of the map
   */
  public int size() {
    return _map.size();
  }

  public Map<K, List<V>> asMap() {
    return _map;
  }

  @Override
  public String toString() {
    return _map.toString();
  }
}
