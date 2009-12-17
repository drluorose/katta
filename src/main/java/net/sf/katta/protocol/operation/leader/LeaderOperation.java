/**
 * Copyright 2008 the original author or authors.
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
package net.sf.katta.protocol.operation.leader;

import java.io.Serializable;
import java.util.List;

import net.sf.katta.master.LeaderContext;
import net.sf.katta.protocol.operation.OperationId;

/**
 * An operation carried out by the leader node.
 */
public interface LeaderOperation extends Serializable {

  static enum LockInstruction {
    CANCEL_THIS_OPERATION, ADD_TO_QUEUE_TAIL;
  }

  /**
   * @param context
   * @return null or a list of operationId which have to be completed before
   *         {@link #nodeOperationComplete(LeaderContext)} method is called.
   * @throws Exception
   */
  List<OperationId> execute(LeaderContext context) throws Exception;

  /**
   * Called when all operations are complete or the nodes of the incomplete
   * operations went down.
   */
  void nodeOperationComplete(LeaderContext context) throws Exception;

  /**
   * @param operation
   * @return true if the current operation, while running, locks the given
   *         operation, false otherwise
   */
  boolean locksOperation(LeaderOperation operation);

  /**
   * This method is called, if the operation is locked by another one.
   * 
   * @return a {@link LockInstruction}
   */
  LockInstruction getLockAlreadObtainedInstruction();

}
