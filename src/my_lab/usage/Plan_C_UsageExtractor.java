// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package my_lab.usage;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMCPA;
import org.sosy_lab.cpachecker.cpa.bam.cache.BAMDataManager;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageDelta;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Plan_C_UsageExtractor {

    private final LogManager logger;
    private final Plan_C_UsageContainer container;
    private BAMDataManager manager;
    private final Plan_C_UsageProcessor usageProcessor;
    private UsageDelta currentDelta;
    private final Map<AbstractState, List<UsageInfo>> stateToUsage;
    private final List<AbstractState> expandedStack;

    private final StatTimer totalTimer = new StatTimer("Time for extracting usages");
    private final AtomicInteger processingSteps;
    private final AtomicInteger numberOfActiveTasks;
    private final ShutdownNotifier notifier;
    public boolean haveUsagesExtracted;         // 增加一个字段用来判断是否在某次迭代中产生了不安全

    private final ThreadSafeTimerContainer usageExpandingTimer =
            new ThreadSafeTimerContainer("Time for usage expanding");

    private final ThreadSafeTimerContainer usageProcessingTimer =
            new ThreadSafeTimerContainer("Time for usage calculation");

    private final ThreadSafeTimerContainer addingToContainerTimer =
            new ThreadSafeTimerContainer("Time for adding to container");
    private final ThreadSafeTimerContainer.TimerWrapper expandingTimer;
    private final ThreadSafeTimerContainer.TimerWrapper processingTimer;
    private final ThreadSafeTimerContainer.TimerWrapper addingTimer;


    public Plan_C_UsageExtractor(
            ConfigurableProgramAnalysis pCpa,
            LogManager pLogger,
            Plan_C_UsageContainer pContainer,
            Plan_C_UsageConfiguration pConfig) {
        logger = pLogger;
        container = pContainer;

        BAMCPA bamCpa = CPAs.retrieveCPA(pCpa, BAMCPA.class);
        if (bamCpa != null) {
            manager = bamCpa.getData();
        }
        Plan_C_UsageCPA usageCpa = CPAs.retrieveCPA(pCpa, Plan_C_UsageCPA.class);
        usageProcessor = usageCpa.getUsageProcessor();
        processingSteps = new AtomicInteger();
        numberOfActiveTasks = new AtomicInteger();
        notifier = usageCpa.getNotifier();
        stateToUsage = new HashMap<>();
        currentDelta = null;
        expandedStack = ImmutableList.of();
        haveUsagesExtracted = false;
        expandingTimer = usageExpandingTimer.getNewTimer();
        processingTimer = usageProcessingTimer.getNewTimer();
        addingTimer = addingToContainerTimer.getNewTimer();
    }

    /**
     * 对提取Usage进行修改，变为只提取每轮更新中得到的新后继的usages
     * 不使用并行提取
     * @param firstState
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    public void extractUsages(LinkedHashMap<AbstractState, Precision> newSucsInEachIteration) {
        totalTimer.start();
//        logger.log(Level.INFO, "one iteration is over, start usage extraction");
        Multimap<AbstractState, UsageDelta> processedSets = ArrayListMultimap.create();

        Set<Map.Entry<AbstractState, Precision>> entrySet = newSucsInEachIteration.entrySet();
        Iterator<Map.Entry<AbstractState, Precision>> it = entrySet.iterator();
        AbstractState firstState = it.next().getKey();  //取出第一个状态

        UsageDelta emptyDelta = UsageDelta.constructDeltaBetween(firstState, firstState);
        currentDelta = emptyDelta;
        processedSets.put(firstState, emptyDelta);
        usageProcessor.updateRedundantUnsafes(container.getNotInterestingUnsafes());    // 不感兴趣的是那些已经细化和呈现假阳的id，updateRedundantUnsafes(...)将不感兴趣的那些id设为redundant

        numberOfActiveTasks.incrementAndGet();

        Deque<AbstractState> stateWaitlist = new ArrayDeque<>();
        stateWaitlist.add(firstState);      //firstStat为可达集中的第一个状态

        // Waitlist to be sure in order (not start from the middle point)
        while (!stateWaitlist.isEmpty()) {
            ARGState argState = (ARGState) stateWaitlist.poll();
            if (stateToUsage.containsKey(argState)) {       // 该状态已经被分析过了
                continue;
            }

            List<UsageInfo> expandedUsages = expandUsagesAndAdd(argState);

            if (needToDumpUsages(argState)) {     // 我们是否需要dump usages，感觉像是放置的意思
                addingTimer.start();
                for (UsageInfo usage : expandedUsages) {
                    container.add(usage);             // container应该属于ConcurrentUsageExtractor，不过的确实是在这些dumped的usage中查找unsafe
                    haveUsagesExtracted = true;
                }
                addingTimer.stop();
            } else {                             // 不需要dump的话，则放到stateToUsage中
                stateToUsage.put(argState, expandedUsages);
            }
            if (it.hasNext())
                stateWaitlist.add(it.next().getKey());     // 将newSucsInEachIteratiron中的下一个状态放到waitlist中
        }

//        logger.log(Level.INFO, "one Usage extraction is finished");
        totalTimer.stop();
    }

    private List<UsageInfo> expandUsagesAndAdd(ARGState state) {

        List<UsageInfo> expandedUsages = new ArrayList<>();

        for (ARGState covered : state.getCoveredByThis()) {
            expandedUsages.addAll(stateToUsage.getOrDefault(covered, ImmutableList.of()));  // 如果stateToUsage的key包含covered，则返回stateToUsage.get(covered)
            // 否则返回空的list
        }
        for (ARGState parent : state.getParents()) {
            expandedUsages.addAll(stateToUsage.getOrDefault(parent, ImmutableList.of()));
        }

        processingTimer.start();
//        List<UsageInfo> usages = usageProcessor.getUsagesForState(state);    // 利用子节点来获取usage
        List<UsageInfo> usages = usageProcessor.getUsagesForStateByParentState(state);      // 利用父节点来获取usage
        processingTimer.stop();

        expandingTimer.start();
        for (UsageInfo usage : usages) {
            UsageInfo expanded = usage.expand(currentDelta, expandedStack);
            if (expanded.isRelevant()) {    // 扩展后的usage可能是Irrelevant
                expandedUsages.add(expanded);
            }
        }
        expandingTimer.stop();

        return expandedUsages;
    }

    private boolean needToDumpUsages(AbstractState pState) {         // 是否需要dump usages

        // for debug
        return true;

//        PredicateAbstractState predicateState =
//                AbstractStates.extractStateByType(pState, PredicateAbstractState.class);
//
//        /**
//         * 似乎是这里让b=2的访问没有被记录
//         */
//        if (true) {
//            return !predicateState.isAbstractionState() || (predicateState.isAbstractionState()
//                    && !predicateState.getAbstractionFormula().isFalse());
//        }
//
//        return predicateState == null
//                || (predicateState.isAbstractionState()
//                && !predicateState.getAbstractionFormula().isFalse());  // 那些predicateState为空，或者isAbstractState并且抽象公式不是False的
    }

    public void printStatistics(StatisticsWriter pWriter) {
        StatisticsWriter writer =
                pWriter.spacer().put(totalTimer).beginLevel().put(usageProcessingTimer).beginLevel();
        usageProcessor.printStatistics(writer);
        writer.endLevel()
                .put(addingToContainerTimer)
                .put(usageExpandingTimer)
                .endLevel()
                .put("Number of different reached sets with lock effects", processingSteps);
    }

    public BAMDataManager getManager() {
        return manager;
    }

}
