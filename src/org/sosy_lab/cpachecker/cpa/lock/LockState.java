// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.lock;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.sosy_lab.cpachecker.cpa.lock.effects.AcquireLockEffect;
import org.sosy_lab.cpachecker.cpa.lock.effects.GenericLockEffectWithId;
import org.sosy_lab.cpachecker.cpa.lock.effects.LockEffectWithId;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleNode;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleState;
import org.sosy_lab.cpachecker.cpa.usage.storage.Delta;

public final class LockState extends AbstractLockState {

  public static class LockTreeNode implements CompatibleNode {

    private final Set<LockIdentifier> locks;

    public LockTreeNode(Set<LockIdentifier> pLocks) {
      locks = pLocks;
    }

    public LockTreeNode(LockTreeNode pNode) {
      this(pNode.locks);
    }

    /**
     * lockState的相容取决于两个LockState的locks交集是否为空
     *      * 交集为空则相容
     *      * 相容对应的是可能形成竞争的情况
     * @param pState
     * @return
     */
    @Override
    public boolean isCompatibleWith(CompatibleState pState) {
      Preconditions.checkArgument(pState instanceof LockTreeNode);
      return Sets.intersection(this.locks, ((LockTreeNode) pState).locks).isEmpty();
    }

    @Override
    public int compareTo(CompatibleState pArg0) {
      Preconditions.checkArgument(pArg0 instanceof LockTreeNode);
      LockTreeNode o = (LockTreeNode) pArg0;
      int result = locks.size() - o.locks.size();
      if (result != 0) {
        return result;
      }
      Iterator<LockIdentifier> lockIterator = locks.iterator();
      Iterator<LockIdentifier> lockIterator2 = o.locks.iterator();
      while (lockIterator.hasNext()) {
        result = lockIterator.next().compareTo(lockIterator2.next());
        if (result != 0) {
          return result;
        }
      }
      return 0;
    }

    @Override
    public boolean cover(CompatibleNode pNode) {
      Preconditions.checkArgument(pNode instanceof LockTreeNode);
      LockTreeNode o = (LockTreeNode) pNode;

      // empty locks do not cover all others (special case
      if (this.locks.isEmpty()) {
        return o.locks.isEmpty();
      } else {
        return o.locks.containsAll(this.locks);
      }
    }

    @Override
    public boolean hasEmptyLockSet() {
      return locks.isEmpty();
    }

    @Override
    public Delta<CompatibleNode> getDeltaBetween(CompatibleNode pOther) {  // 如果调用者的锁集为空，则返回pOther的锁集，否则返回两者之间不相同的锁的集合
      LockTreeNode pState = (LockTreeNode) pOther;
      // TODO more deterministic
      if (locks.isEmpty()) {
        return new LockNodeDelta(pState);
      }
      LockTreeNode diff = new LockTreeNode(ImmutableSet.of());
      for (LockIdentifier lock : pState.locks) {
        if (!locks.contains(lock)) {
          diff.locks.add(lock);
        }
      }
      return new LockNodeDelta(diff);
    }

    public int getSize() {
      return locks.size();
    }

    public boolean isEmpty() {
      return locks.isEmpty();
    }

    public boolean addAll(LockTreeNode pNode) {
      return locks.addAll(pNode.locks);
    }

    @Override
    public int hashCode() {
      return Objects.hash(locks);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || !(obj instanceof LockTreeNode)) {
        return false;
      }
      LockTreeNode other = (LockTreeNode) obj;
      return Objects.equals(locks, other.locks);
    }

    @Override
    public String toString() {
      return locks.toString();
    }
  }

  public class LockStateBuilder extends AbstractLockStateBuilder {
    private Map<LockIdentifier, Integer> mutableLocks;
    private boolean changed;

    public LockStateBuilder(LockState state) {
      super(state);
      mutableLocks = state.locks;
      changed = false;
    }

    public void cloneIfNecessary() {
      if (!changed) {
        changed = true;
        mutableLocks = new TreeMap<>(mutableLocks);
      }
    }

    @Override
    public void add(LockIdentifier lockId) {
      cloneIfNecessary();
      Integer a = mutableLocks.getOrDefault(lockId, 0) + 1;
      mutableLocks.put(lockId, a);
    }

    @Override
    public void free(LockIdentifier lockId) {
      if (mutableLocks.containsKey(lockId)) {
        Integer a = mutableLocks.get(lockId) - 1;
        cloneIfNecessary();
        if (a > 0) {
          mutableLocks.put(lockId, a);
        } else {
          mutableLocks.remove(lockId);
        }
      }
    }

    @Override
    public void reset(LockIdentifier lockId) {
      cloneIfNecessary();
      mutableLocks.remove(lockId);
    }

    @Override
    public void set(LockIdentifier lockId, int num) {
      // num can be equal 0, this means, that in origin file it is 0 and we should delete locks

      Integer size = mutableLocks.get(lockId);

      if (size == null) {
        size = 0;
      }
      if (num > size) {
        for (int i = 0; i < num - size; i++) {
          add(lockId);
        }
      } else if (num < size) {
        for (int i = 0; i < size - num; i++) {
          free(lockId);
        }
      }
    }

    @Override
    public void restore(LockIdentifier lockId) {
      if (mutableToRestore == null) {
        return;
      }
      Integer size = ((LockState) mutableToRestore).locks.get(lockId);
      cloneIfNecessary();
      mutableLocks.remove(lockId);
      if (size != null) {
        mutableLocks.put(lockId, size);
      }
      isRestored = true;
    }

    @Override
    public void restoreAll() {
      mutableLocks = ((LockState) mutableToRestore).locks;
    }

    @Override
    public LockState build() {
      if (isFalseState) {
        return null;
      }
      if (isRestored) {
        mutableToRestore = mutableToRestore.toRestore;
      }
      if (locks.equals(mutableLocks) && mutableToRestore == toRestore) {
        return LockState.this;
      } else {
        return new LockState(mutableLocks, (LockState) mutableToRestore);
      }
    }

    @Override
    public LockState getOldState() {
      return LockState.this;
    }

    @Override
    public void resetAll() {
      cloneIfNecessary();
      mutableLocks.clear();
    }

    @Override
    public void reduce(Set<LockIdentifier> removeCounters, Set<LockIdentifier> totalRemove) {
      mutableToRestore = null;
      assert Sets.intersection(removeCounters, totalRemove).isEmpty();
      cloneIfNecessary();
      removeCounters.forEach(l -> mutableLocks.replace(l, 1));
      Iterator<Entry<LockIdentifier, Integer>> iterator = mutableLocks.entrySet().iterator();
      while (iterator.hasNext()) {
        LockIdentifier lockId = iterator.next().getKey();
        if (totalRemove.contains(lockId)) {
          iterator.remove();
        }
      }
    }

    @Override
    public void expand(
        AbstractLockState rootState,
        Set<LockIdentifier> expandCounters,
        Set<LockIdentifier> totalExpand) {
      mutableToRestore = rootState.toRestore;
      assert Sets.intersection(expandCounters, totalExpand).isEmpty();
      cloneIfNecessary();
      Map<LockIdentifier, Integer> rootLocks = ((LockState) rootState).locks;
      for (LockIdentifier lock : expandCounters) {
        if (rootLocks.containsKey(lock)) {
          Integer size = mutableLocks.get(lock);
          Integer rootSize = rootLocks.get(lock);
          cloneIfNecessary();
          // null is also correct (it shows, that we've found new lock)

          Integer newSize;
          if (size == null) {
            newSize = rootSize - 1;
          } else {
            newSize = size + rootSize - 1;
          }
          if (newSize > 0) {
            mutableLocks.put(lock, newSize);
          } else {
            mutableLocks.remove(lock);
          }
        }
      }
      for (LockIdentifier lockId : totalExpand) {
        if (rootLocks.containsKey(lockId)) {
          mutableLocks.put(lockId, rootLocks.get(lockId));
        }
      }

    }

    @Override
    public void setRestoreState() {
      mutableToRestore = LockState.this;
    }

    @Override
    public void setAsFalseState() {
      isFalseState = true;
    }
  }

  private final ImmutableMap<LockIdentifier, Integer> locks;
  // if we need restore state, we save it here
  // Used for function annotations like annotate.function_name.restore
  public LockState() {
    locks = ImmutableMap.of();
  }

  LockState(Map<LockIdentifier, Integer> gLocks, LockState state) {
    super(state);
    this.locks = ImmutableMap.copyOf(gLocks);
  }

  @Override
  public Map<LockIdentifier, Integer> getHashCodeForState() {
    // Special hash for BAM, in other cases use iterator
    return locks;
  }

  @Override
  public String toString() {
    if (locks.size() > 0) {
      StringBuilder sb = new StringBuilder();
      return Joiner.on("], ").withKeyValueSeparator("[").appendTo(sb, locks).append("]").toString();
    } else {
      return "Without locks";
    }
  }

  @Override
  public int getCounter(LockIdentifier lock) {
    return locks.getOrDefault(lock, 0);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(locks);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    LockState other = (LockState) obj;
    return Objects.equals(toRestore, other.toRestore) && Objects.equals(locks, other.locks);
  }

  /**
   * This method find the difference between two states in some metric. It is useful for
   * comparators. lock1.diff(lock2) <=> lock1 - lock2.
   *
   * @param pOther The other LockStatisticsState
   * @return Difference between two states
   */
  @Override
  public int compareTo(CompatibleState pOther) {
    LockState other = (LockState) pOther;

    int result = other.getSize() - this.getSize(); // decreasing queue

    if (result != 0) {
      return result;
    }

    Iterator<Entry<LockIdentifier, Integer>> iterator1 = locks.entrySet().iterator();
    Iterator<Entry<LockIdentifier, Integer>> iterator2 = other.locks.entrySet().iterator();
    // Sizes are equal
    while (iterator1.hasNext()) {
      Entry<LockIdentifier, Integer> entry1 = iterator1.next();
      Entry<LockIdentifier, Integer> entry2 = iterator2.next();
      result = entry1.getKey().compareTo(entry2.getKey());
      if (result != 0) {
        return result;
      }
      Integer Result = entry1.getValue() - entry2.getValue();
      if (Result != 0) {
        return Result;
      }
    }
    return 0;
  }

  @Override
  public LockStateBuilder builder() {
    return new LockStateBuilder(this);
  }

  @Override
  public Multiset<LockEffectWithId> getDifference(AbstractLockState pOther) {
    // Return the effect, which shows, what should we do to transform from this state to the other
    LockState other = (LockState) pOther;

    Multiset<LockEffectWithId> result = HashMultiset.create();
    Set<LockIdentifier> processedLocks = new TreeSet<>();

    for (Entry<LockIdentifier, Integer> entry : locks.entrySet()) {
      LockIdentifier lockId = entry.getKey();
      int thisCounter = entry.getValue();
      int otherCounter = other.locks.getOrDefault(lockId, 0);
      if (thisCounter > otherCounter) {
        for (int i = 0; i < thisCounter - otherCounter; i++) {
          result.add(GenericLockEffectWithId.RELEASE.applyToTarget(lockId));
        }
      } else if (thisCounter < otherCounter) {
        for (int i = 0; i < otherCounter - thisCounter; i++) {
          result.add(AcquireLockEffect.createEffectForId(lockId));
        }
      }
      processedLocks.add(lockId);
    }
    for (Entry<LockIdentifier, Integer> entry : other.locks.entrySet()) {
      LockIdentifier lockId = entry.getKey();
      if (!processedLocks.contains(lockId)) {
        for (int i = 0; i < entry.getValue(); i++) {
          result.add(AcquireLockEffect.createEffectForId(lockId));
        }
      }
    }
    return result;
  }

  @Override
  public CompatibleNode getCompatibleNode() {
    return new LockTreeNode(locks.keySet());
  }

  @Override
  protected Set<LockIdentifier> getLocks() {
    return locks.keySet();
  }

  @Override
  public boolean isLessOrEqual(AbstractLockState other) {
    // State is less, if it has the same locks as the other and may be some more

    for (LockIdentifier lock : other.getLocks()) {
      if (!locks.containsKey(lock)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public AbstractLockState join(AbstractLockState pOther) {
    Map<LockIdentifier, Integer> overlappedMap = new TreeMap<>();
    Map<LockIdentifier, Integer> otherMap = ((LockState) pOther).locks;

    for (Entry<LockIdentifier, Integer> entry : this.locks.entrySet()) {
      LockIdentifier id = entry.getKey();
      Integer value = entry.getValue();
      if (otherMap.containsKey(id)) {
        Integer otherVal = otherMap.get(id);
        overlappedMap.put(id, Integer.min(value, otherVal));
      }
    }
    return new LockState(overlappedMap, (LockState) this.toRestore);
  }
}
