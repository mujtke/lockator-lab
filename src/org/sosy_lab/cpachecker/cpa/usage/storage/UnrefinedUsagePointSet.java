// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.storage;

import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;

public class UnrefinedUsagePointSet implements AbstractUsagePointSet {
  private final NavigableSet<UsagePoint> topUsages;
  private final Map<UsagePoint, UsageInfoSet> usageInfoSets;

  public UnrefinedUsagePointSet() {
    topUsages = new ConcurrentSkipListSet<>();
    usageInfoSets = new ConcurrentSkipListMap<>();
  }

  /**
   * 将newInfo添加到UsageInfoSets的UsageInfoSet中
   * @param newInfo
   */
  public void add(UsageInfo newInfo) {
    UsageInfoSet targetSet;
    UsagePoint newPoint = newInfo.getUsagePoint();
    if (usageInfoSets.containsKey(newPoint)) {   // 如果newPoint已经存在与UsageInfoSets中， 则视覆盖的情况将newPoint添加到topUsages中
      targetSet = usageInfoSets.get(newPoint);
    } else {
      targetSet = new UsageInfoSet();
      // It is possible, that someone place the set after check
      UsageInfoSet present = usageInfoSets.putIfAbsent(newPoint, targetSet);  // 如果usageInfoSets.get(newPoint) != null，则present = usageInfoSets.get(newPoint)
      if (present != null) {                                                  // 相当于是如果newPoint原来有对应的UsageInfoSets，则将其取出来
        targetSet = present;
      }
    }
    add(newPoint);    // 如果可以的话，将newPoint添加到topUsages中，即topUsages不一定会增加
    targetSet.add(newInfo); // 但UsageInfoSet一定会添加新的UsageInfo
  }

  /**
   *
   * @param newPoint
   */
  private void add(UsagePoint newPoint) {
    // Put newPoint in the right place in tree
    Iterator<UsagePoint> iterator = topUsages.iterator();
    while (iterator.hasNext()) {
      UsagePoint point = iterator.next();
      if (point.equals(newPoint)) {
        // Unknown problem with contains:
        // for skipList it somehow returns false for an element in the set
        return;   // 如果newPoint和topUsages中的某个point相等，则不添加
      }
      if (newPoint.covers(point)) {   // 如果topUsage中的point和新传入的newPoint之间存在覆盖关系，则topUsages的数量不会增加，被覆盖的point会被添加做另一个point的coveredUsage
        iterator.remove();
        newPoint.addCoveredUsage(point);
      } else if (point.covers(newPoint)) {
        point.addCoveredUsage(newPoint);
        return;
      }
    }
    topUsages.add(newPoint);
  }

  public UsageInfoSet getUsageInfo(UsagePoint point) {
    return usageInfoSets.get(point);
  }

  @Override
  public int size() {
    int result = 0;

    for (UsageInfoSet value : usageInfoSets.values()) {
      result += value.size();
    }

    return result;
  }

  public void reset() {
    topUsages.clear();
    usageInfoSets.clear();
  }

  public void remove(UsageState pUstate) {
    //Attention! Use carefully. May not work
    for (UsagePoint point : new TreeSet<>(usageInfoSets.keySet())) {
      UsageInfoSet uset = usageInfoSets.get(point);
      boolean b = uset.remove(pUstate);
      if (b) {
        if (uset.isEmpty()) {
          usageInfoSets.remove(point);
        }
        //May be two usages related to the same state. This is abstractState !
        //return;
      }
    }
  }

  public Iterator<UsagePoint> getPointIterator() {
    return new TreeSet<>(topUsages).iterator();
  }

  public Iterator<UsagePoint> getPointIteratorFrom(UsagePoint p) {
    return new TreeSet<>(topUsages.tailSet(p)).iterator();
  }

  public int getNumberOfTopUsagePoints() {
    return topUsages.size();
  }

  public void remove(UsagePoint currentUsagePoint) {
    usageInfoSets.remove(currentUsagePoint);
    topUsages.remove(currentUsagePoint);
    currentUsagePoint.getCoveredUsages().forEach(this::add);
  }

  NavigableSet<UsagePoint> getTopUsages() {
    return topUsages;
  }
}
