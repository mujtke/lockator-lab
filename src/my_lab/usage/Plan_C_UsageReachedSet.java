// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package my_lab.usage;

import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ObjectOutputStream;
import java.util.*;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.cpachecker.core.reachedset.PartitionedReachedSet;
import org.sosy_lab.cpachecker.core.waitlist.Waitlist.WaitlistFactory;
import org.sosy_lab.cpachecker.cpa.ThreadingForPlanC.Plan_C_threadingState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMCPA;
import org.sosy_lab.cpachecker.cpa.bam.cache.BAMDataManager;
import org.sosy_lab.cpachecker.cpa.usage.UsageCPA;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;
import org.sosy_lab.cpachecker.cpa.usage.storage.ConcurrentUsageExtractor;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageConfiguration;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageContainer;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageExtractor;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;

@SuppressFBWarnings(justification = "No support for serialization", value = "SE_BAD_FIELD")
public class Plan_C_UsageReachedSet extends PartitionedReachedSet {

    private static final long serialVersionUID = 1L;

    private boolean usagesExtracted = false;
    private boolean haveUnsafes = false;

    public static class RaceProperty implements Property {
        @Override
        public String toString() {
            return "Race condition";
        }
    }

    private static final ImmutableSet<Property> RACE_PROPERTY = ImmutableSet.of(new my_lab.usage.Plan_C_UsageReachedSet.RaceProperty());

    private final LogManager logger;
    private final Plan_C_UsageConfiguration plan_c_usageConfig;
    private Plan_C_UsageExtractor serialExtractor = null;           // 提取Usage信息

    private final Plan_C_UsageContainer container;                  // Usage容器

    public LinkedHashMap<AbstractState, Precision> newSuccessorsInEachIteration;        // 用来存储每次迭代新产生的后继

    public HashMap<AbstractState, Precision> coveredStatesTable;                   // 用来存放因为被覆盖的状态，这些状态可能在后面的过程中重新放回waitList中

    public AdjustablePrecision finalPrecision = new AdjustablePrecision() {             // 用来收集细化过程中产生的精度
        @Override
        public AdjustablePrecision add(AdjustablePrecision otherPrecision) {
            return null;
        }

        @Override
        public AdjustablePrecision subtract(AdjustablePrecision otherPrecision) {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    };
    // 用来记录处理过的Unsafes
    public Set<SingleIdentifier> processedUnsafes;

    // 复制identifierIterator中的precisionMap
    public final Map<SingleIdentifier, AdjustablePrecision> precisionMap = new HashMap<>();

    // 记录是否还有covered的状态
    public boolean stillHaveCoveredStates = false;

    // 记录已经访问过的位置
    public static Set<CFANode> visitedLocations = new HashSet<>();

    // 记录被cover的状态
    public List<AbstractState> coveredStates = new ArrayList<>();


    public Plan_C_UsageReachedSet(
            WaitlistFactory waitlistFactory, Plan_C_UsageConfiguration pConfig, LogManager pLogger) {
        super(waitlistFactory);
        logger = pLogger;
        container = new Plan_C_UsageContainer(pConfig, logger);
        plan_c_usageConfig = pConfig;
        newSuccessorsInEachIteration = new LinkedHashMap<>();
        processedUnsafes = new HashSet<>();
        coveredStatesTable = new HashMap<>();
    }

    @Override
    public void remove(AbstractState pState) {
        super.remove(pState);
        UsageState ustate = UsageState.get(pState);
        container.removeState(ustate);
    }

    @Override
    public void add(AbstractState pState, Precision pPrecision) {
        super.add(pState, pPrecision);

    /*UsageState USstate = UsageState.get(pState);
    USstate.saveUnsafesInContainerIfNecessary(pState);*/
    }

    /**
     * 将covered State放入reachedSet中，但是不会将其放入waitlist中去
     * @param pState
     * @param pPrecision
     */
    public void addButSkipWaitlist(AbstractState pState, Precision pPrecision) {
        super.addButSkipWaitlist(pState, pPrecision);
    }

    @Override
    public void clear() {
        container.resetUnrefinedUnsafes();
        usagesExtracted = false;
        super.clear();
    }

    /**
     * 仿照hasViolatedProperties进行改写
     * @return 是否有race可能存在
     */
    // 检查每轮得到的后继是否导致了不安全
    // 重写extractUsages方法
    public boolean haveUnsafeInNewSucs() {
        serialExtractor.extractUsages(newSuccessorsInEachIteration);        // 从新探索得到的后继中提取Usage
        if (serialExtractor.haveUsagesExtracted) {                          // 如果有Usage被提取出的话
            serialExtractor.haveUsagesExtracted = false;
            return container.hasUnsafesForUsageContainer();                 // 提取出Usage之后，判断是否发现了不安全
        }
        return false;
    }

    public void setHaveUnsafes(boolean value) {
        this.haveUnsafes = value;
    }

    public boolean haveUnsafes() {
        return this.haveUnsafes;
    }

    public Set<Property> getUnsafesProperties() {
        if (haveUnsafes) {
            return RACE_PROPERTY;
        } else {
            return ImmutableSet.of();
        }
    }

    @Override
    public Set<Property> getViolatedProperties() {
        if (hasViolatedProperties()) {
            return RACE_PROPERTY;
        } else {
            return ImmutableSet.of();
        }
    }

    public Plan_C_UsageContainer getUsageContainer() {
        return container;
    }

    public Map<SingleIdentifier, Pair<UsageInfo, UsageInfo>> getUnsafes() {
        return container.getStableUnsafes();
    }

    private void writeObject(@SuppressWarnings("unused") ObjectOutputStream stream) {
        throw new UnsupportedOperationException("cannot serialize Logger");
    }

    @Override
    public void finalize(ConfigurableProgramAnalysis pCpa) {
        BAMCPA bamCPA = CPAs.retrieveCPA(pCpa, BAMCPA.class);
        if (bamCPA != null) {
            UsageCPA uCpa = CPAs.retrieveCPA(pCpa, UsageCPA.class);
            uCpa.getStats().setBAMCPA(bamCPA);
        }
        serialExtractor = new Plan_C_UsageExtractor(pCpa, logger, container, plan_c_usageConfig);
    }

//  public void printStatistics(StatisticsWriter pWriter) {
//    extractor.printStatistics(pWriter);
//  }

    public void printStatistics(StatisticsWriter pWriter) {
        serialExtractor.printStatistics(pWriter);
    }

    // 判断是否还有cover的状态
    public boolean isStillHaveCoveredStates() {
        return !coveredStatesTable.isEmpty();
    }

    /**
     * 将coveredStates重新放回到waitList中去，然后清空coveredStatesTable，用于下一次的计算
     * TODO:只有满足Location覆盖的节点才应该被放回Waitlist中
     */
    public void rollbackCoveredStates() {
        for (Map.Entry<AbstractState, Precision> s : coveredStatesTable.entrySet()) {
            ARGState pState = (ARGState) s.getKey();
            Plan_C_threadingState tState = extractStateByType(pState, Plan_C_threadingState.class);
            if (tState.locationCovered) {    // 如果满足位置覆盖
                putBackToWaitlist(pState);   // 在DefaultReachedSet中添加putBackToWaitlist方法
                // for debug， 放回waitlist的状态，需要将其重新放入reached中(不使用add，而是使用addButSkipWaitlist方法跳过将状态放回waitlist中)
                this.addButSkipWaitlist(s.getKey(), s.getValue());
            }
        }
        coveredStatesTable.clear(); // 清空coveredStatesTable，用于下一次的计算
    }

}


