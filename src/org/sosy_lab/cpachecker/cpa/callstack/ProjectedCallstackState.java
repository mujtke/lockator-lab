/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2019  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.sosy_lab.cpachecker.cpa.callstack;

public class ProjectedCallstackState extends CallstackState {
  private static final long serialVersionUID = 3250857147115751058L;

  private final static ProjectedCallstackState instance = new ProjectedCallstackState();

  private ProjectedCallstackState() {
    super(null, null, null);
  }

  @Override
  public Object getPartitionKey() {
    return null;
  }

  public static ProjectedCallstackState getInstance() {
    return instance;
  }
}
