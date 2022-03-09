// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.refinement;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AdjustablePrecision;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.ForwardingReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMTransferRelation;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateRefiner;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.cpa.usage.UsageCPA;
import org.sosy_lab.cpachecker.cpa.usage.UsageReachedSet;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageContainer;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Precisions;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;


@Options(prefix="cpa.usage")
public class IdentifierIterator extends WrappedConfigurableRefinementBlock<ReachedSet, SingleIdentifier> implements Refiner {

  private class Stats implements Statistics {

    private final StatTimer innerRefinementTimer = new StatTimer("Time for inner refinement");
    private final StatTimer precisionTimer = new StatTimer("Time for precision operations");
    private final StatTimer finishingTimer = new StatTimer("Time for final operations");

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
      StatisticsWriter writer = StatisticsWriter.writingStatisticsTo(pOut);
      writer.put(innerRefinementTimer).put(precisionTimer).put(finishingTimer);
      IdentifierIterator.this.printStatistics(writer);
    }

    @Override
    public String getName() {
      return "UsageStatisticsRefiner";
    }

  }

  private final ConfigurableProgramAnalysis cpa;
  private final LogManager logger;

  @Option(name="precisionReset", description="The value of marked unsafes, after which the precision should be cleaned",
      secure = true)
  private int precisionReset = Integer.MAX_VALUE; //

  // TODO Option is broken!!
  @Option(
    name = "totalARGCleaning",
    description = "clean all ARG or try to reuse some parts of it (memory consuming)",
    secure = true
  )
  private boolean totalARGCleaning = true;

  @Option(
      name = "hideFilteredUnsafes",
      description = "filtered unsafes, which can not be removed using precision, may be hidden",
      secure = true)
  private boolean hideFilteredUnsafes = false;

  private final boolean disableAllCaching;

  private final Stats stats;
  private final BAMTransferRelation transfer;

  int i = 0;
  int lastFalseUnsafeSize = -1;
  int lastTrueUnsafes = 0;

  private final Map<SingleIdentifier, AdjustablePrecision> precisionMap = new HashMap<>();

  public IdentifierIterator(ConfigurableRefinementBlock<SingleIdentifier> pWrapper, Configuration config,
      ConfigurableProgramAnalysis pCpa,
      BAMTransferRelation pTransfer,
      boolean pDisableCaching)
      throws InvalidConfigurationException {
    super(pWrapper);
    config.inject(this);
    cpa = pCpa;
    UsageCPA uCpa = CPAs.retrieveCPA(pCpa, UsageCPA.class);
    logger = uCpa.getLogger();
    transfer = pTransfer;
    stats = new Stats();
    disableAllCaching = pDisableCaching;
  }

  public static Refiner create(ConfigurableProgramAnalysis pCpa) throws InvalidConfigurationException {
    if (!(pCpa instanceof WrapperCPA)) {
      throw new InvalidConfigurationException(BAMPredicateRefiner.class.getSimpleName() + " could not find the PredicateCPA");
    }

    UsageCPA usageCpa = ((WrapperCPA) pCpa).retrieveWrappedCpa(UsageCPA.class);
    if (usageCpa == null) {
      throw new InvalidConfigurationException(UsageCPA.class.getSimpleName() + " needs a UsageCPA");
    }

    return new RefinementBlockFactory(pCpa, usageCpa.getConfig())
        .create();
  }

  @Override
  public RefinementResult performBlockRefinement(ReachedSet pReached) throws CPAException, InterruptedException {

    UsageReachedSet uReached;
    if (pReached instanceof UsageReachedSet) {
      uReached = (UsageReachedSet) pReached;
    } else if (pReached instanceof ForwardingReachedSet) {
      uReached = (UsageReachedSet) ((ForwardingReachedSet) pReached).getDelegate();
    } else {
      throw new UnsupportedOperationException("UsageRefiner requires UsageReachedSet");
    }
    UsageContainer container = uReached.getUsageContainer();
    Set<SingleIdentifier> processedUnsafes = new HashSet<>();

    logger.log(Level.INFO, ("Perform US refinement: " + i++));
    int originUnsafeSize = container.getTotalUnsafeSize();
    if (lastFalseUnsafeSize == -1) {
      lastFalseUnsafeSize = originUnsafeSize;
    }
    int counter = lastFalseUnsafeSize - originUnsafeSize;
    boolean newPrecisionFound = false;  //

    sendUpdateSignal(PredicateRefinerAdapter.class, pReached);
    sendUpdateSignal(PointIterator.class, container);

    Iterator<SingleIdentifier> iterator = container.getUnrefinedUnsafeIterator(); // 返回的是unrefinedUnsafeIds的key集合
    boolean isPrecisionChanged = false;
    AbstractState firstState = pReached.getFirstState();
    AdjustablePrecision finalPrecision = (AdjustablePrecision) pReached.getPrecision(firstState);
    AdjustablePrecision initialPrecision =
        (AdjustablePrecision) cpa.getInitialPrecision(
            AbstractStates.extractLocation(firstState),
            StateSpacePartition.getDefaultPartition());   // initialPrecision初始化为空

    while (iterator.hasNext()) {    // 针对的是container中的Unsafe
      SingleIdentifier currentId = iterator.next(); //unrefinedUnsafeIds中的id

      stats.innerRefinementTimer.start();
      RefinementResult result = wrappedRefiner.performBlockRefinement(currentId);   // 细化
      /**
       * 这里针对细化的结果可以将真反例打印出来
       */
      if (result.isTrue())
        System.out.println("race:"+result.getTrueRace().toString());

      stats.innerRefinementTimer.stop();
      newPrecisionFound |= result.isFalse();  // 按位或，相当于只要有针对一个id的细化产生了新谓词，则newPrecisionFound就会为true

      Collection<AdjustablePrecision> info = result.getPrecisions();    // 从反例中得到的新精度(谓词)

      if (!info.isEmpty()) {    //获取的新精度不为空
        stats.precisionTimer.start();
        AdjustablePrecision updatedPrecision =
            precisionMap.getOrDefault(currentId, initialPrecision);   // 如果currentId原本就有精度，则返回原来的精度，否则将其精度设置为initialPrecision

        for (AdjustablePrecision p : info) {
          updatedPrecision = updatedPrecision.add(p); // 添加新发现的精度
        }

        assert !updatedPrecision.isEmpty() : "Updated precision is expected to be not empty";
        precisionMap.put(currentId, updatedPrecision);
        finalPrecision = finalPrecision.add(updatedPrecision);
        isPrecisionChanged = true;
        stats.precisionTimer.stop();
      }

      if (result.isTrue()) {    // 如果不安全的细化结果为true，则将相应的Id放入refinedIds中
        container.setAsRefined(currentId, result); // 将currentId对应的条目从unrefinedIds中移除，添加到refinedIds中，同时将该Id对应及其对应的访问对放入stableUnsafes中
        processedUnsafes.add(currentId);    // 添加到processedUnsafes中的id要么是形成真实race的id，要么是选择进行忽略的id
      } else if (hideFilteredUnsafes && result.isFalse() && !isPrecisionChanged) {  // 如果设置了hideFilteredUnsafe为true，默认设置为false
        //We do not add a precision, but consider the unsafe as false               // 对于假的race，可能存在不产生新精度的情况，此时通过设置hideFilteredUnsafes来隐藏
        //set it as false now, because it will occur again, as precision is not changed
        //We can not look at precision size here - the result can be false due to heuristics
        container.setAsFalseUnsafe(currentId);    // 将对应的id放到falseUnsafe中，同时将该id从unrefinedIds中移除
        processedUnsafes.add(currentId);
      }
    }
    int newTrueUnsafeSize = container.getProcessedUnsafeSize();
    counter += (newTrueUnsafeSize - lastTrueUnsafes);
    if (counter >= precisionReset) {    // precisionReset = 2^31 - 1，所以？ 重置？
      Precision p = pReached.getPrecision(pReached.getFirstState());
      pReached.updatePrecision(pReached.getFirstState(),
          Precisions.replaceByType(p, PredicatePrecision.empty(), Predicates.instanceOf(PredicatePrecision.class)));

      //TODO will we need other finish signal?
      //wrappedRefiner.finish(getClass());
      lastFalseUnsafeSize = originUnsafeSize;
      lastTrueUnsafes = newTrueUnsafeSize;
    }
    if (newPrecisionFound) {    // 如果发现了新谓词
      stats.finishingTimer.start();
      if (disableAllCaching) {    // 在配置文件中有设置disableAllCaching = true
        BAMPredicateCPA bamcpa = CPAs.retrieveCPA(cpa, BAMPredicateCPA.class);
        if (bamcpa != null) {
          bamcpa.clearAllCaches();
        }
      }
      ARGState.clearIdGenerator();
      // Note the following clean should be done always independently from option disableAllCaching
      if (totalARGCleaning && transfer != null) {
        transfer.cleanCaches();
      } else {
        /* MultipleARGSubtreeRemover subtreesRemover = transfer.getMultipleARGSubtreeRemover();
        subtreesRemover.cleanCaches();*/
      }
      pReached.clear(); // 清空可达集合

      processedUnsafes.addAll(
          Sets.intersection(precisionMap.keySet(), container.getFalseUnsafes()));
      for (AdjustablePrecision prec :
              from(processedUnsafes)
              .transform(precisionMap::remove)
              .filter(Predicates.notNull())) {
        finalPrecision = finalPrecision.subtract(prec);   // 减去一些精度
      }

      CFANode firstNode = AbstractStates.extractLocation(firstState);
      //Get new state to remove all links to the old ARG
      pReached.add(cpa.getInitialState(firstNode, StateSpacePartition.getDefaultPartition()), finalPrecision);
      // 如果发现了新的精度，则利用新产生的谓词和初始状态更新pReached
      // 更新之后，pReached中只有一个初始状态，伴随着新的精度
      //TODO should we signal about removed ids?

      sendFinishSignal();
      stats.finishingTimer.stop();
    }
    logger.log(Level.INFO, container.getUnsafeStatus());
    //pStat.UnsafeCheck.stopIfRunning();
    if (newPrecisionFound) {
      return RefinementResult.createTrue();
    } else {
      return RefinementResult.createFalse();
    }
  }

  @Override
  public void printStatistics(StatisticsWriter pOut) {
    wrappedRefiner.printStatistics(pOut);
  }

  @Override
  public void collectStatistics(Collection<Statistics> statsCollection) {
    statsCollection.add(stats);
    super.collectStatistics(statsCollection);
  }

  @Override
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException {
    return performBlockRefinement(pReached).isTrue();
  }
}
