// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage;

import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ObjectOutputStream;
import java.util.*;

import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.cpachecker.core.reachedset.PartitionedReachedSet;
import org.sosy_lab.cpachecker.core.waitlist.Waitlist.WaitlistFactory;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMCPA;
import org.sosy_lab.cpachecker.cpa.bam.cache.BAMDataManager;
import org.sosy_lab.cpachecker.cpa.usage.storage.ConcurrentUsageExtractor;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageConfiguration;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageContainer;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageExtractor;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

@SuppressFBWarnings(justification = "No support for serialization", value = "SE_BAD_FIELD")
public class UsageReachedSet extends PartitionedReachedSet {

  private static final long serialVersionUID = 1L;

  private boolean usagesExtracted = false;
  private boolean haveUnsafes = false;

  public static class RaceProperty implements Property {
    @Override
    public String toString() {
      return "Race condition";
    }
  }

  private static final ImmutableSet<Property> RACE_PROPERTY = ImmutableSet.of(new RaceProperty());

  private final LogManager logger;
  private final UsageConfiguration usageConfig;
  private ConcurrentUsageExtractor extractor = null;
  private UsageExtractor serialExtractor = null;

  private final UsageContainer container;

  // 用来存储每次迭代新产生的后继
  public LinkedHashMap<AbstractState, Precision> newSuccessorsInEachIteration;
  // 用来收集细化过程中产生的精度
  public AdjustablePrecision finalPrecision = new AdjustablePrecision() {
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
  // 用来记录是否产生过新谓词
  public boolean newPrecisionFound;
  // 复制identifierIterator中的precisionMap
  public final Map<SingleIdentifier, AdjustablePrecision> precisionMap = new HashMap<>();


  public UsageReachedSet(
      WaitlistFactory waitlistFactory, UsageConfiguration pConfig, LogManager pLogger) {
    super(waitlistFactory);
    logger = pLogger;
    container = new UsageContainer(pConfig, logger);
    usageConfig = pConfig;
    newSuccessorsInEachIteration = new LinkedHashMap<>();
    processedUnsafes = new HashSet<>();
    newPrecisionFound = false;
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

  @Override
  public void clear() {
    container.resetUnrefinedUnsafes();
    usagesExtracted = false;
    super.clear();
  }

  @Override
  public boolean hasViolatedProperties() {      // 判断是否有违反的属性
    if (!usagesExtracted) {
      extractor.extractUsages(getFirstState()); // has problem: extractor属于reachedSet?
      usagesExtracted = true;                   // extractor should be concurrentUsageExtractor
    }
    return container.hasUnsafes();    // 有问题：container属于reachedSet，这里的container中的usages对应于那些dump的usages
  }                                   // container与extractor中的container相同，在concurrentUsageExtractor中添加container的项

  /**
   * 仿照hasViolatedProperties进行改写
   * @return 是否有race可能存在
   */
  // 检查每轮得到的后继是否导致了不安全
  // 重写extractUsages方法
  public boolean haveUnsafeInNewSucs() {
    serialExtractor.extractUsages(newSuccessorsInEachIteration);
    if (serialExtractor.haveUsagesExtracted) {
      serialExtractor.haveUsagesExtracted = false;
      return container.hasUnsafesForUsageContainer();
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

  public UsageContainer getUsageContainer() {
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
    serialExtractor = new UsageExtractor(pCpa, logger, container, usageConfig);
  }

//  public void printStatistics(StatisticsWriter pWriter) {
//    extractor.printStatistics(pWriter);
//  }

  public void printStatistics(StatisticsWriter pWriter) {
    serialExtractor.printStatistics(pWriter);
  }

  public BAMDataManager getBAMDataManager() {
    return extractor.getManager();
  }

}


