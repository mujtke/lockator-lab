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
package org.sosy_lab.cpachecker.cpa.lock;

import org.sosy_lab.cpachecker.core.interfaces.AbstractEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ApplyOperator;

import java.util.Iterator;
import java.util.concurrent.locks.Lock;

public class LockApplyOperator implements ApplyOperator {

  public LockApplyOperator() {}

  @Override
  public AbstractState apply(AbstractState pState1, AbstractState pState2) {
    LockState state1 = (LockState) pState1;
    LockState state2 = (LockState) pState2;
    if (state1.isCompatibleWith(state2)) {
      return state1;
    }
    return null;
  }

  /**
   * 增加对lock状态的相容性判断，直接调用isCompatible()函数，该函数就是判断锁集是否相交的
   * ----同时对锁集的相交进行判断（useless）
   */
  public boolean compatible(AbstractState pState1, AbstractState pState2) {
    LockState state1 = (LockState) pState1;
    LockState state2 = (LockState) pState2;

//    Iterator<LockIdentifier> it1 = state1.getLocks().iterator();
//    Iterator<LockIdentifier> it2 = state2.getLocks().iterator();
//
//    //锁集相交的判断
//    boolean lockSetIntersect = false;
//    for( ; it1.hasNext(); ) {
//      String state1Lock = it1.next().toString();
//      if(state1Lock.equals("Without locks"))  break;
//      for( ; it2.hasNext(); ) {
//        String state2Lock = it2.next().toString();
//        if(state2Lock.equals("Without locks"))  break;
//        if(state1Lock.equals(state2Lock)) {
//          //锁集相交不为空
//          lockSetIntersect = true;
//        }
//      }
//    }
//
//    if(state1.isCompatibleWith(state2) && !lockSetIntersect) {
//      //相容且锁集不相交才返回true
//      return true;
//    }
    if(state1.isCompatibleWith(state2)) {
      return true;
    }
    return false;
  }

  @Override
  public AbstractState project(AbstractState pParent, AbstractState pChild) {
    return pParent;
  }

  @Override
  public AbstractState project(AbstractState pParent, AbstractState pChild, AbstractEdge pEdge) {
    return pParent;
  }

  @Override
  public boolean isInvariantToEffects(AbstractState pState) {
    return true;
  }

  @Override
  public boolean canBeAnythingApplied(AbstractState pState) {
    return true;
  }

}
