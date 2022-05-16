// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package my_lab.usage;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;
import org.sosy_lab.cpachecker.cpa.usage.refinement.RefinementResult;
import org.sosy_lab.cpachecker.cpa.usage.storage.*;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

/**
 * 对于Plan_C来说，UsageContainer中只需要保留可能形成race的id及其访问信息即可
 * 不可达的访问不会被记录进来
 */
public class Plan_C_UsageContainer {
    private final NavigableMap<SingleIdentifier, UnrefinedUsagePointSet> unrefinedIds;
    private final NavigableMap<SingleIdentifier, RefinedUsagePointSet> refinedIds;
    // 添加的变量用来判断是否有可能存在Unsafe的id，如果一轮迭代中没有发现真实的Unsafe，则需要清空，下一轮迭代中重新计算
    private final NavigableMap<SingleIdentifier, UnrefinedUsagePointSet> haveUnsafesIds;

    private Map<SingleIdentifier, Pair<UsageInfo, UsageInfo>> stableUnsafes = new TreeMap<>();

    private final Plan_C_UnsafeDetector detector;

    private Set<SingleIdentifier> falseUnsafes = new HashSet<>();
    private Set<SingleIdentifier> initialUnsafes;

    // Only for statistics
    private int initialUsages = 0;

    private final LogManager logger;

    private final StatTimer resetTimer = new StatTimer("Time for reseting unsafes");
    private final StatTimer unsafeDetectionTimer = new StatTimer("Time for unsafe detection");
    private final StatTimer searchingInCachesTimer = new StatTimer("Time for searching in caches");
    private final StatTimer addingToSetTimer = new StatTimer("Time for adding to usage point set");
    private final StatCounter sharedVariables =
            new StatCounter("Number of detected shared variables");

    private boolean usagesCalculated = false;
    private boolean oneTotalIteration = false;

    public Plan_C_UsageContainer(Plan_C_UsageConfiguration pConfig, LogManager l) {
        // TODO: debug 0516
        //unrefinedIds = new ConcurrentSkipListMap<>();
        unrefinedIds = new TreeMap<>();

        refinedIds = new TreeMap<>();
        haveUnsafesIds = new TreeMap<>();
        falseUnsafes = new TreeSet<>();
        logger = l;
        detector = new Plan_C_UnsafeDetector(pConfig);
    }

    /**
     * 向container中添加usageInfo
     * container在添加usageInfo时，会将identifier单独提取出来
     * 如果该id尚未被添加到unrefinedIds中，
     * 则将UsageInfo添加到该id所对应的UnrefinedUsagePointSet集合中
     * UnrefinedUsagePointSet中包含了对应id的topUsages和UsageInfoSets
     * UsageInfoSets： key为UsagePoint，value为UsageInfo的集合
     * @param pUsage
     */
    public void add(UsageInfo pUsage) {
        final boolean DEBUG = true;
        if (!DEBUG) {
            {
                SingleIdentifier id = pUsage.getId();
                if (id instanceof StructureIdentifier) {
                    id = ((StructureIdentifier) id).toStructureFieldIdentifier();
                }

                UnrefinedUsagePointSet uset;

                // searchingInCachesTimer.start();
                if (oneTotalIteration && !unrefinedIds.containsKey(id)) {
                    // searchingInCachesTimer.stop();
                    return;
                }

                if (!unrefinedIds.containsKey(id)) {
                    uset = new UnrefinedUsagePointSet();
                    // It is possible, that someone place the set after check
                    UnrefinedUsagePointSet present = unrefinedIds.putIfAbsent(id, uset);  // 如果id之前就对应了一个UnrefinedUsagePointSet集合，则present = null
                    if (present != null) {                                                // 否则present = 之前对应的UnrefinedUsagePointSet集合
                        uset = present;                                                     // 在此之前，uset为空
                    }
                } else {
                    uset = unrefinedIds.get(id);
                }
                // searchingInCachesTimer.stop();

                // addingToSetTimer.start();
                uset.add(pUsage);         // 将UsageInfo放到对应id的UnrefinedUsagePointSet集合中，不过会pUsage是否被覆盖进行判断
                // addingToSetTimer.stop();
            }
        }
        else {
            // TODO: debug 0513
            {
                SingleIdentifier id = pUsage.getId();
                if (id instanceof StructureIdentifier) {
                    id = ((StructureIdentifier) id).toStructureFieldIdentifier();
                }

                UnrefinedUsagePointSet uset;

                int usageInfoOld = -1, usageInfoInc = 0;
                if (!unrefinedIds.containsKey(id)) {
                    usageInfoOld = 0;
                    uset = new UnrefinedUsagePointSet();
                    // It is possible, that someone place the set after check
                    unrefinedIds.put(id, uset);
                    uset.add(pUsage);
                    usageInfoInc = uset.size() - usageInfoOld;
                } else {
                    uset = unrefinedIds.get(id);
                    usageInfoOld = uset.size();
                    uset.add(pUsage);
                    usageInfoInc = uset.size() - usageInfoOld;
                }
                assert usageInfoInc == 1 : "\u001b[31mError: \u001b[0m add usageInfo error!";
            }
        }

    }

    // 每次都要提取相应的信息
    private void calculateUnsafesIfNecessary() {
        unsafeDetectionTimer.start();

        Iterator<Entry<SingleIdentifier, UnrefinedUsagePointSet>> iterator =
                unrefinedIds.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<SingleIdentifier, UnrefinedUsagePointSet> entry = iterator.next();
            UnrefinedUsagePointSet tmpList = entry.getValue();    //包含对特定id的topUsage和UsageInfoSets
            if (detector.isUnsafe(tmpList)) {
                if (!oneTotalIteration) {
                    initialUsages += tmpList.size();
                }
            } else {
                if (!oneTotalIteration) {
                    sharedVariables.inc();
                }
                iterator.remove();    //如果没有构成Unsafe，则将unrefinedIds中对应的条目删除
            }
        }
        //到这里，unrefinedIds中的条目数量会减少很多，应该是那些不存在Unsafe的被删除了
        if (!oneTotalIteration) {
            initialUnsafes = new TreeSet<>(unrefinedIds.keySet());   //跟oneTotalIteration的取值有关，会在其取值为false时将unrefinedIds中存在着Unsafe情况的所有条目复制到initialUnsafes中
        } else {
            falseUnsafes = new TreeSet<>(initialUnsafes);     // 首次执行到此时，initialUnsafe中包含了unrefinedIds中存在Unsafe所有的id，之后经过下面两步逐渐筛检存在falseUnsafe的id
            falseUnsafes.removeAll(unrefinedIds.keySet());    // 此时falseUnsafe中剩下的就是真正的falseUnsafe（如果是falseUnsafe，则经过之前的细化后，相应的unrefinedId从haveUnsafe变成了不在含有Unsafe，因此相应的unrefinedIds中的项被删除）
            falseUnsafes.removeAll(refinedIds.keySet());      // 放到refinedIds中的id不用再管
        }

        unsafeDetectionTimer.stop();
    }

    public Set<SingleIdentifier> getFalseUnsafes() {
        return falseUnsafes;
    }

    public Iterator<SingleIdentifier> getUnsafeIterator() {
        calculateUnsafesIfNecessary();
        Set<SingleIdentifier> result = new TreeSet<>(refinedIds.keySet());
        result.addAll(unrefinedIds.keySet());
        return result.iterator();
    }

    public Iterator<SingleIdentifier> getUnrefinedUnsafeIterator() {
        // New set to avoid concurrent modification exception
        Set<SingleIdentifier> result = new TreeSet<>(unrefinedIds.keySet());
        return result.iterator();
    }

    public int getTotalUnsafeSize() {
        calculateUnsafesIfNecessary();
        return unrefinedIds.size() + refinedIds.size();
    }

    public int getProcessedUnsafeSize() {
        return refinedIds.size();
    }

    public Plan_C_UnsafeDetector getUnsafeDetector() {
        return detector;
    }

    public void resetUnrefinedUnsafes() {
        resetTimer.start();
        usagesCalculated = false;
        oneTotalIteration = true;
        unrefinedIds.forEach((k, v) -> v.reset());
        logger.log(Level.FINE, "Unsafes are reseted");
        resetTimer.stop();
    }

    public void removeState(final UsageState pUstate) {
        unrefinedIds.forEach((id, uset) -> uset.remove(pUstate));
        logger.log(Level.ALL, "All unsafes related to key state " + pUstate + " were removed from reached set");
    }

    public AbstractUsagePointSet getUsages(SingleIdentifier id) {
        if (unrefinedIds.containsKey(id)) {
            return unrefinedIds.get(id);
        } else {
            return refinedIds.get(id);
        }
    }

    public void setAsFalseUnsafe(SingleIdentifier id) {
        falseUnsafes.add(id);
        unrefinedIds.remove(id);
    }

    public void setAsRefined(SingleIdentifier id, RefinementResult result) {
        Preconditions.checkArgument(result.isTrue(), "Result is not true, can not set the set as refined");
        checkArgument(
                detector.isUnsafe(getUsages(id)),
                "Refinement is successful, but the unsafe is absent for identifier %s",
                id);

        UsageInfo firstUsage = result.getTrueRace().getFirst();
        UsageInfo secondUsage = result.getTrueRace().getSecond();

        RefinedUsagePointSet rSet = RefinedUsagePointSet.create(firstUsage, secondUsage);
        refinedIds.put(id, rSet); // 将相应的id放入refinedIds中
        unrefinedIds.remove(id);  // 将相应的id从unrefinedIds中移除
        // We need to update it here, as we may finish this iteration (even without timeout) and output
        // the old value
        stableUnsafes.put(id, Pair.of(firstUsage, secondUsage));  // 将确定会发生竞争的id和其对应的访问对放入stableUnsafes中
    }

    public void printUsagesStatistics(StatisticsWriter out) {
        int unsafeSize = getTotalUnsafeSize();
        StatInt topUsagePoints = new StatInt(StatKind.SUM, "Total amount of unrefined usage points");
        StatInt unrefinedUsages = new StatInt(StatKind.SUM, "Total amount of unrefined usages");
        StatInt refinedUsages = new StatInt(StatKind.SUM, "Total amount of refined usages");
        StatCounter failedUsages = new StatCounter("Total amount of failed usages");

        final int generalUnrefinedSize = unrefinedIds.keySet().size();
        for (UnrefinedUsagePointSet uset : unrefinedIds.values()) {
            unrefinedUsages.setNextValue(uset.size());
            topUsagePoints.setNextValue(uset.getNumberOfTopUsagePoints());
        }

        int generalRefinedSize = 0;
        int generalFailedSize = 0;

        for (RefinedUsagePointSet uset : refinedIds.values()) {
            Pair<UsageInfo, UsageInfo> pair = uset.getUnsafePair();
            UsageInfo firstUsage = pair.getFirst();
            UsageInfo secondUsage = pair.getSecond();

            if (firstUsage.isLooped()) {
                failedUsages.inc();
                generalFailedSize++;
            }
            if (secondUsage.isLooped() && !firstUsage.equals(secondUsage)) {
                failedUsages.inc();
            }
            if (!firstUsage.isLooped() && !secondUsage.isLooped()) {
                generalRefinedSize++;
                refinedUsages.setNextValue(uset.size());
            }
        }

        out.spacer()
                .put(sharedVariables)
                .put("Total amount of unsafes", unsafeSize)
                .put("Initial amount of unsafes (before refinement)", unsafeSize + falseUnsafes.size())
                .put("Initial amount of usages (before refinement)", initialUsages)
                .put("Initial amount of refined false unsafes", falseUnsafes.size())
                .put("Total amount of unrefined unsafes", generalUnrefinedSize)
                .put(topUsagePoints)
                .put(unrefinedUsages)
                .put("Total amount of refined unsafes", generalRefinedSize)
                .put(refinedUsages)
                .put("Total amount of failed unsafes", generalFailedSize)
                .put(failedUsages)
                .put(resetTimer)
                .put(unsafeDetectionTimer)
                .put(searchingInCachesTimer)
                .put(addingToSetTimer);
    }

    public String getUnsafeStatus() {
        return unrefinedIds.size()
                + " unrefined, "
                + refinedIds.size()
                + " refined; "
                + falseUnsafes.size()
                + " false unsafes";
    }

    public Set<SingleIdentifier> getNotInterestingUnsafes() {
        return new TreeSet<>(Sets.union(falseUnsafes, refinedIds.keySet()));
    }

    private void saveStableUnsafes() {
        calculateUnsafesIfNecessary();    //经过此步骤，会将unrefinedIds中存在Unsafe的条目放置到initialUnsafes中，同时unrefinedIds中不存在Unsafe的条目也会被移除

        stableUnsafes.clear();
        addUnsafesFrom(refinedIds);
        addUnsafesFrom(unrefinedIds);   //将unrefinedIds中的存在Unsafe的条目添加到stableUnsafes中
    }
    /**
     * stableUnsafes中的一个条目应该是<某个字段, 对该字段的两次访问组成的pair>
     * @param storage
     */

    private void addUnsafesFrom(
            NavigableMap<SingleIdentifier, ? extends AbstractUsagePointSet> storage) {

        for (Entry<SingleIdentifier, ? extends AbstractUsagePointSet> entry : storage.entrySet()) {
            Pair<UsageInfo, UsageInfo> tmpPair = detector.getUnsafePair(entry.getValue());
            stableUnsafes.put(entry.getKey(), tmpPair);
        }
    }

    public boolean hasUnsafes() {
        saveStableUnsafes();
        return !stableUnsafes.isEmpty();
    }

    public Map<SingleIdentifier, Pair<UsageInfo, UsageInfo>> getStableUnsafes() {
        return stableUnsafes;
    }

    /**
     * 判断UsageContainer中是否存在形成竞争的情况
     */
    public boolean hasUnsafesForUsageContainer() {
        haveUnsafesIds.clear();     //防止空指针异常
        findUnsafesIfExist();
        stableUnsafes.clear();
        if (!haveUnsafesIds.isEmpty()) {
            addUnsafesFrom(haveUnsafesIds);    // 将存在存在Unsafes的ids放到stableUnsafes中
        }

//        if (true) { return false; }        // for debug
        // TODO: debug 0510
        if (!stableUnsafes.isEmpty()) {
            for (Map.Entry<SingleIdentifier, Pair<UsageInfo, UsageInfo>> entry : stableUnsafes.entrySet()) {
                AbstractState s1 = entry.getValue().getFirstNotNull().getKeyState();
                AbstractState s2 = entry.getValue().getSecondNotNull().getKeyState();
                ARGState argS1 = AbstractStates.extractStateByType(s1, ARGState.class);
                ARGState argS2 = AbstractStates.extractStateByType(s2, ARGState.class);
                System.out.println("unsafe: " + argS1.getStateId() + ", " + argS2.getStateId());
            }

        }
        return !stableUnsafes.isEmpty();
    }

    private void findUnsafesIfExist() {
        unsafeDetectionTimer.start();
        final boolean DEBUG = false;
        if (!DEBUG) {
            {
                Iterator<Entry<SingleIdentifier, UnrefinedUsagePointSet>> iterator =
                        unrefinedIds.entrySet().iterator();             //
//    falseUnsafes = new TreeSet<>(unrefinedIds.keySet());    //先假设所有的id都为不安全
                while (iterator.hasNext()) {
                    Entry<SingleIdentifier, UnrefinedUsagePointSet> entry = iterator.next();
                    UnrefinedUsagePointSet tmpList = entry.getValue();    //包含对特定id的topUsage和UsageInfoSets
                    if (detector.isUnsafe(tmpList)) {                     //若对某个id计算出存在不安全
                        haveUnsafesIds.put(entry.getKey(), entry.getValue()); //将该id添加到haveUnsafesIds中
                    }
                }
            }
        }
        else {  // TODO: debug 0513
            {
                Iterator<Entry<SingleIdentifier, UnrefinedUsagePointSet>> iterator = unrefinedIds.entrySet().iterator();
                while (iterator.hasNext()) {
                    Entry<SingleIdentifier, UnrefinedUsagePointSet> entry = iterator.next();
                    UnrefinedUsagePointSet tmpList = entry.getValue();    //包含对特定id的topUsage和UsageInfoSets
                    // 查看UsageInfoSets
                    System.out.println("id: \u001b[32m" + entry.getKey().getName());
                    int i = 0x2460;
                    for (Entry<UsagePoint, UsageInfoSet> e : tmpList.getUsageInfoSets().entrySet()) {
                        System.out.printf("\u001b[0m\t%CusageInfoSet size: %d\n", (i++), e.getValue().size());
                        if (detector.isUnsafe(tmpList)) {                     //若对某个id计算出存在不安全
                            System.out.println("\u001b[31mrace found!\u001b[0m\n");
                            haveUnsafesIds.put(entry.getKey(), entry.getValue()); //将该id添加到haveUnsafesIds中
                        }
                    }

                }
            }
        }

        unsafeDetectionTimer.stop();
    }

    public void setFalseUnsafes(Set<SingleIdentifier> pFalseUnsafes) {
        falseUnsafes = pFalseUnsafes;
    }

    public void falseUnsafeAdd(SingleIdentifier id) {
        falseUnsafes.add(id);
    }

    public void resetHaveUnsafesIds() { haveUnsafesIds.clear(); }

    public NavigableMap<SingleIdentifier, UnrefinedUsagePointSet> getUnrefinedIds() {
        return unrefinedIds;
    }
}
