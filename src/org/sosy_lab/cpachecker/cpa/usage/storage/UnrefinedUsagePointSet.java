// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.storage;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;

public class UnrefinedUsagePointSet implements AbstractUsagePointSet {
  private final NavigableSet<UsagePoint> topUsages;
  private final Map<UsagePoint, UsageInfoSet> usageInfoSets;

  public UnrefinedUsagePointSet() {
    // TODO: debug 0516
    //topUsages = new ConcurrentSkipListSet<>();
    topUsages = new TreeSet<>();
    //usageInfoSets = new ConcurrentSkipListMap<>();
    usageInfoSets = new TreeMap<>();
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
      assert !targetSet.isEmpty(): "add error!";
    } else {
      targetSet = new UsageInfoSet();
      // It is possible, that someone place the set after check
      UsageInfoSet present = usageInfoSets.putIfAbsent(newPoint, targetSet);  // 如果usageInfoSets.get(newPoint) != null，则present = usageInfoSets.get(newPoint)
      if (present != null) {                                                  // 相当于是如果newPoint原来有对应的UsageInfoSets，则将其取出来
        targetSet = present;
      }
      //usageInfoSets.put(newPoint, targetSet);  // 如果usageInfoSets.get(newPoint) != null，则present = usageInfoSets.get(newPoint)
    }
    // TODO: debug 0516 这里也有问题，两个不相同的Point会在topUsages.add时失败，追溯到UsagePoint的compareTo方法
    add(newPoint);    // 如果可以的话，将newPoint添加到topUsages中，即topUsages不一定会增加
    // TODO: debug 0513 这里有问题，两个coreState不相同的newInfo会被视为相同, 追溯到UsageInfo的compareTo方法
    //int beforeAdd = targetSet.size();
    targetSet.add(newInfo); // 但UsageInfoSet一定会添加新的UsageInfo
    //int afterAdd = targetSet.size();
    //System.out.println("before: " + beforeAdd + "after: " + afterAdd);
    // TODO debug 0513
    {
      final boolean DEBUG = false;
      if (DEBUG) {
        int usageInfos = 0;
        for (Map.Entry<UsagePoint, UsageInfoSet> entry : usageInfoSets.entrySet()) {
          usageInfos += entry.getValue().size();
        }
        System.out.println("variable: \u001b[32m" + newInfo.getId().getName()
                + "\t\u001b[0mtopUsages: \u001b[31m" + topUsages.size() + "\t\u001b[0musageInfos: \u001b[31m" + usageInfos + "\u001b[0m\n");
        if (usageInfos < topUsages.size()) {
          System.out.println("");
        }
      }
    }

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
      final boolean DEBUG = false;
      if (DEBUG) {
        if (point.equals(newPoint)) {
          System.out.println("Equal: \t" + point + "\n\t" + newPoint);
          return;   // 如果newPoint和topUsages中的某个point相等，则不添加
        }
        if (newPoint.covers(point)) {   // 如果topUsage中的point和新传入的newPoint之间存在覆盖关系，则topUsages的数量不会增加，被覆盖的point会被添加做另一个point的coveredUsage
          iterator.remove();
          newPoint.addCoveredUsage(point);
          System.out.println("Cover: \t" + point + "\n\t" + newPoint);
        } else if (point.covers(newPoint)) {
          point.addCoveredUsage(newPoint);
          System.out.println("Cover: \t" + point + "\n\t" + newPoint);
          return;
        }
      }
      else {
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
    }
    topUsages.add(newPoint);
  }

  public Map<UsagePoint, UsageInfoSet> getUsageInfoSets() {
    return usageInfoSets;
  }

  /**
   * 此方法慎用，存在异常
   * @param point
   * @return
   */
  public UsageInfoSet getUsageInfo(UsagePoint point) {

     // TODO: debug 0513
    final boolean DEBUG = true;
    if (DEBUG) {
      // 改用直接的方式返回UsageInfoSets
      assert usageInfoSets.containsKey(point) : "null PointerException found.";
      return usageInfoSets.get(point);
    }
   Iterator<Map.Entry<UsagePoint, UsageInfoSet>> it = usageInfoSets.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<UsagePoint, UsageInfoSet> entry = it.next();
      if (entry.getKey().equals(point)) {
        return entry.getValue();
      }
    }

    assert false;
    return null;
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

//  public void remove(UsageState pUstate) {    // 有问题
//    //Attention! Use carefully. May not work
//    for (UsagePoint point : new TreeSet<>(usageInfoSets.keySet())) {
//      UsageInfoSet uset = usageInfoSets.get(point);
//      boolean b = uset.remove(pUstate);
//      if (b) {
//        if (uset.isEmpty()) {
//          usageInfoSets.remove(point);
//        }
//        //May be two usages related to the same state. This is abstractState !
//        //return;
//      }
//    }
//  }

  /**
   * 重新修改上面的remove方法，上面的方法会报异常
   * 使用迭代器来进行匀速的移除
   * @param pUstate
   */
    public void remove(UsageState pUstate) {
//      for (Map.Entry<UsagePoint, UsageInfoSet> entry : usageInfoSets.entrySet()) {
//        UsageInfoSet uset = entry.getValue();
//        boolean b = uset.remove(pUstate);
//        if (b) {
//          if (uset.isEmpty()) {
//            usageInfoSets.remove(entry.getKey());
//          }
//        }
//      }
      // TODO: debug 0516
      Iterator it = usageInfoSets.entrySet().iterator();
      while (it.hasNext()) {
       Map.Entry<UsagePoint, UsageInfoSet> entry = (Map.Entry<UsagePoint, UsageInfoSet>) it.next();
       UsageInfoSet uset = entry.getValue();
       boolean b = uset.remove(pUstate);
       if (b) {
         if (uset.isEmpty()) {
           // 这里如果不将topUsages中对应的UsagePoint移除的话，会导致UsageInfoSets中usageInfo的数量少于topUsages中usagePoint的数量（这是不合理的）
           topUsages.remove(entry.getKey());
           it.remove();
         }
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

  public NavigableSet<UsagePoint> getTopUsages() {
    return topUsages;
  }
}
