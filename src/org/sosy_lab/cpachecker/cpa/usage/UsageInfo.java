// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage;

import com.google.common.collect.ImmutableList;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocations;
import org.sosy_lab.cpachecker.core.interfaces.AbstractWrapperState;
import org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.lock.AbstractLockState;
import org.sosy_lab.cpachecker.cpa.lock.LockState.LockTreeNode;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageDelta;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsagePoint;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

public final class UsageInfo implements Comparable<UsageInfo> {

  public static enum Access {
    WRITE,
    READ;
  }

  private static class UsageCore {
    private final CFANode node;
    private final Access accessType;
    private AbstractState keyState;
    private List<CFAEdge> path;
    private final SingleIdentifier id;

    private boolean isLooped;

    private UsageCore(Access atype, CFANode n, SingleIdentifier ident) {
      node = n;
      accessType = atype;
      keyState = null;
      isLooped = false;
      id = ident;
    }
  }

  private static final UsageInfo IRRELEVANT_USAGE = new UsageInfo();

  private final UsageCore core;
  private final UsagePoint point;
  private final List<AbstractState> expandedStack;

  private UsageInfo() {
    core = null;
    point = null;
    expandedStack = null;
  }

  private UsageInfo(
      Access atype,
      CFANode n,
      SingleIdentifier ident,
      ImmutableList<CompatibleNode> pStates) {
    this(new UsageCore(atype, n, ident), new UsagePoint(pStates, atype), null);
  }

  private UsageInfo(UsageCore pCore, UsagePoint pPoint, List<AbstractState> pStack) {
    core = pCore;
    point = pPoint;
    expandedStack = pStack;
  }

  public static UsageInfo createUsageInfo(
      @NonNull Access atype, @NonNull AbstractState state, AbstractIdentifier ident) {
    if (ident instanceof SingleIdentifier) {
      ImmutableList.Builder<CompatibleNode> storedStates = ImmutableList.builder();

      for (CompatibleState s : AbstractStates.asIterable(state).filter(CompatibleState.class)) {
        if (!s.isRelevantFor((SingleIdentifier) ident)) {
          return IRRELEVANT_USAGE;
        }
        storedStates.add(s.getCompatibleNode());  // 只添加ThreadState和LockState
      }

      /**
       * new UsageInfo中，需要传入当前边所对应的CFANode，不使用LocationCPA时，传入的CFANode为null
       * 修改使其能够正常传入
       */
      AbstractStateWithLocations childStateWithLocs = AbstractStates.extractStateByType(state, Plan_C_threadingState.class);
      CFANode node = null;
      Set<CFANode> childLocs = new HashSet<>();
      for (CFANode n : childStateWithLocs.getLocationNodes()) { childLocs.add(n); }
      for (ARGState s : ((ARGState)state).getParents()) {
        AbstractStateWithLocations parentStateWithLocs = AbstractStates.extractStateByType(s, Plan_C_threadingState.class);
        Set<CFANode> parentLocs = new HashSet<>();
        for (CFANode n : parentStateWithLocs.getLocationNodes()) { parentLocs.add(n); }
        childLocs.removeAll(parentLocs);
        if (!childLocs.isEmpty()) {
          // TODO: debug 0516 这里的修改不确定是否正确
          //assert childLocs.size() == 1 : "get location error";
          node = (CFANode)childLocs.toArray()[0];
        }
      }
      assert node != null : "NullPoint exception found!";
      UsageInfo result = new UsageInfo (atype, node, (SingleIdentifier) ident, storedStates.build());
//      UsageInfo result =
//          new UsageInfo(
//              atype,
//              AbstractStates.extractLocation(state),
//              (SingleIdentifier) ident,
//              storedStates.build());
      result.core.keyState = state;
      return result;
    }
    return IRRELEVANT_USAGE;
  }

  public CFANode getCFANode() {
    return core.node;
  }

  public SingleIdentifier getId() {
    assert (core.id != null);
    return core.id;
  }

  public void setAsLooped() {
    core.isLooped = true;
  }

  public boolean isLooped() {
    return core.isLooped;
  }

  public boolean isRelevant() {
    return this != IRRELEVANT_USAGE;
  }

  @Override
  public int hashCode() {
    return Objects.hash(core.accessType, core.node, point);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    UsageInfo other = (UsageInfo) obj;
    return core.accessType == other.core.accessType
        && Objects.equals(core.node, other.core.node)
        && Objects.equals(point, other.point);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append(core.accessType);
    sb.append(" access to ");
    sb.append(core.id);
    LockTreeNode locks = getLockNode();
    if (locks == null) {
      // Lock analysis is disabled
    } else if (locks.getSize() == 0) {
      sb.append(" without locks");
    } else {
      sb.append(" with ");
      sb.append(locks);
    }

    return sb.toString();
  }

  public void setRefinedPath(List<CFAEdge> p) {
    core.keyState = null;
    core.path = p;
  }

  public AbstractState getKeyState() {
    return core.keyState;
  }

  public List<CFAEdge> getPath() {
    // assert path != null;
    return core.path;
  }

  /**
   * 这个实现的方法中，即便两个usageInfo的keyState不相同，两个UsageInfo也有可能被认定为相同
   * 原作者认为treeMap难以理解... -> 没有使用keyState作为比较的依据
   * @param pO
   * @return
   */
//  @Override
//  public int compareTo(UsageInfo pO) {
//    // TODO: debug 0513
//    final boolean DEBUG = false;
//    if (DEBUG) {
//      String id = pO.getId().getName();
//      if (id.equals("b")) {
//        System.out.println("\u001b[32m \u2460");
//      }
//    }
//
//    int result;
//
//    if (this == pO) {
//      return 0;
//    }
//    result = point.compareTo(pO.point);
//    if (result != 0) {
//      return result;
//    }
//
//    result = this.core.node.compareTo(pO.core.node);
//    if (result != 0) {
//      return result;
//    }
//    result = this.core.accessType.compareTo(pO.core.accessType);
//    if (result != 0) {
//      return result;
//    }
//    /* We can't use key states for ordering, because the treeSets can't understand,
//     * that old refined usage with zero key state is the same as new one
//     */
//    if (this.core.id != null && pO.core.id != null) {
//      // Identifiers may not be equal here:
//      // if (a.b > c.b)
//      // FieldIdentifiers are the same (when we add to container),
//      // but full identifiers (here) are not equal
//      // TODO should we distinguish them?
//
//    }
//    return 0;
//  }

  public int compareTo(UsageInfo pO) {
    // remove 0513
    final boolean DEBUG = false;
    if (DEBUG) {
      String id = pO.getId().getName();
      if (id.equals("b")) {
        System.out.println("\u001b[32m \u2460\u001b[0m");
      }
    }

    int result;

    if (this == pO) {
      return 0;
    }
    result = point.compareTo(pO.point);
    if (result != 0) {
      return result;
    }

    result = this.core.node.compareTo(pO.core.node);
    if (result != 0) {
      return result;
    }
    result = this.core.accessType.compareTo(pO.core.accessType);
    if (result != 0) {
      return result;
    }
    // TODO: debug 0513 -> 使用keyState的相关信息进行判断
    ARGState t = (ARGState) this.getKeyState();
    ARGState o = (ARGState) pO.getKeyState();
    result = t.compareTo(o);
    if (result != 0) {
      return result;
    }

    if (this.core.id != null && pO.core.id != null) {
      // Identifiers may not be equal here:
      // if (a.b > c.b)
      // FieldIdentifiers are the same (when we add to container),
      // but full identifiers (here) are not equal
      // TODO should we distinguish them?

    }
    return 0;
  }

  public UsageInfo copy() {
    return new UsageInfo(core, point, expandedStack);
  }

  public AbstractLockState getLockState() {
    return null;
  }

  public LockTreeNode getLockNode() {
    return point.get(LockTreeNode.class);
  }

  public UsagePoint getUsagePoint() {
    return point;
  }

  public UsageInfo expand(UsageDelta pDelta, List<AbstractState> pExpandedStack) {
    List<CompatibleNode> old = point.getCompatibleNodes();
    ImmutableList<CompatibleNode> newStates = pDelta.apply(old);
    if (newStates.isEmpty()) {      // 表明是无用的usage
      return IRRELEVANT_USAGE;
    }
    if (newStates == old) {         // 等于没有扩展
      return this;
    }
    return new UsageInfo(core, new UsagePoint(newStates, core.accessType), pExpandedStack);   // 返回扩展之后的结果
  }

  public List<AbstractState> getExpandedStack() {
    return expandedStack == null ? ImmutableList.of() : expandedStack;
  }
}
