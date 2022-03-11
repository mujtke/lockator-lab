// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm;

import com.google.common.base.Functions;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.ClassOption;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ForcedCovering;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGMergeJoinCPAEnabledAnalysis;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.cpa.usage.UsageReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.statistics.AbstractStatValue;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatHist;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class CPAAlgorithm implements Algorithm, StatisticsProvider {

  private static class CPAStatistics implements Statistics {

    private Timer totalTimer         = new Timer();
    private Timer chooseTimer        = new Timer();
    private Timer precisionTimer     = new Timer();
    private Timer transferTimer      = new Timer();
    private Timer mergeTimer         = new Timer();
    private Timer stopTimer          = new Timer();
    private Timer addTimer           = new Timer();
    private Timer forcedCoveringTimer = new Timer();

    private int   countIterations   = 0;
    private int   maxWaitlistSize   = 0;
    private long  countWaitlistSize = 0;
    private int   countSuccessors   = 0;
    private int   maxSuccessors     = 0;
    private int   countMerge        = 0;
    private int   countStop         = 0;
    private int   countBreak        = 0;

    private Map<String, AbstractStatValue> reachedSetStatistics = new HashMap<>();

    private void stopAllTimers() {
      totalTimer.stopIfRunning();
      chooseTimer.stopIfRunning();
      precisionTimer.stopIfRunning();
      transferTimer.stopIfRunning();
      mergeTimer.stopIfRunning();
      stopTimer.stopIfRunning();
      addTimer.stopIfRunning();
      forcedCoveringTimer.stopIfRunning();
    }

    private void updateReachedSetStatistics(Map<String, AbstractStatValue> newStatistics) {
      for (Entry<String, AbstractStatValue> e : newStatistics.entrySet()) {
        String key = e.getKey();
        AbstractStatValue val = e.getValue();
        if (!reachedSetStatistics.containsKey(key)) {
          reachedSetStatistics.put(key, val);
        } else {
          AbstractStatValue newVal = reachedSetStatistics.get(key);

          if (val == newVal) {
            // ignore, otherwise counters would double
          } else if (newVal instanceof StatCounter) {
            assert val instanceof StatCounter;
            ((StatCounter) newVal).mergeWith((StatCounter) val);
          } else if (newVal instanceof StatInt) {
            assert val instanceof StatInt;
            ((StatInt) newVal).add((StatInt) val);
          } else if (newVal instanceof StatHist) {
            assert val instanceof StatHist;
            ((StatHist) newVal).mergeWith((StatHist) val);
          } else {
            throw new AssertionError("Can't handle " + val.getClass().getSimpleName());
          }
        }
      }
    }

    @Override
    public String getName() {
      return "CPA algorithm";
    }

    @Override
    public void printStatistics(PrintStream out, Result pResult, UnmodifiableReachedSet pReached) {
      out.println("Number of iterations:            " + countIterations);
      if (countIterations == 0) {
        // Statistics not relevant, prevent division by zero
        return;
      }

      out.println("Max size of waitlist:            " + maxWaitlistSize);
      out.println("Average size of waitlist:        " + countWaitlistSize
          / countIterations);
      StatisticsWriter w = StatisticsWriter.writingStatisticsTo(out);
      for (AbstractStatValue c : reachedSetStatistics.values()) {
        w.put(c);
      }
      out.println("Number of computed successors:   " + countSuccessors);
      out.println("Max successors for one state:    " + maxSuccessors);
      out.println("Number of times merged:          " + countMerge);
      out.println("Number of times stopped:         " + countStop);
      out.println("Number of times breaked:         " + countBreak);
      out.println();
      out.println("Total time for CPA algorithm:     " + totalTimer + " (Max: " + totalTimer.getMaxTime().formatAs(TimeUnit.SECONDS) + ")");
      out.println("  Time for choose from waitlist:  " + chooseTimer);
      if (forcedCoveringTimer.getNumberOfIntervals() > 0) {
        out.println("  Time for forced covering:       " + forcedCoveringTimer);
      }
      out.println("  Time for precision adjustment:  " + precisionTimer);
      out.println("  Time for transfer relation:     " + transferTimer);
      if (mergeTimer.getNumberOfIntervals() > 0) {
        out.println("  Time for merge operator:        " + mergeTimer);
      }
      out.println("  Time for stop operator:           " + stopTimer);
      out.println("  Time for adding to reached set:   " + addTimer);

    }
  }

  @Options(prefix = "cpa")
  public static class CPAAlgorithmFactory implements AlgorithmFactory {

    @Option(
        secure = true,
        description = "Which strategy to use for forced coverings (empty for none)",
        name = "forcedCovering")
    @ClassOption(packagePrefix = "org.sosy_lab.cpachecker")
    private ForcedCovering.@Nullable Factory forcedCoveringClass = null;

    @Option(secure=true, description="Do not report 'False' result, return UNKNOWN instead. "
        + " Useful for incomplete analysis with no counterexample checking.")
    private boolean reportFalseAsUnknown = false;

    private final ForcedCovering forcedCovering;

    private final ConfigurableProgramAnalysis cpa;
    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;

    public CPAAlgorithmFactory(ConfigurableProgramAnalysis cpa, LogManager logger,
        Configuration config, ShutdownNotifier pShutdownNotifier) throws InvalidConfigurationException {

      config.inject(this);
      this.cpa = cpa;
      this.logger = logger;
      this.shutdownNotifier = pShutdownNotifier;

      if (forcedCoveringClass != null) {
        forcedCovering = forcedCoveringClass.create(config, logger, cpa);
      } else {
        forcedCovering = null;
      }

    }

    @Override
    public CPAAlgorithm newInstance() {
      return new CPAAlgorithm(cpa, logger, shutdownNotifier, forcedCovering, reportFalseAsUnknown);
    }
  }

  public static CPAAlgorithm create(ConfigurableProgramAnalysis cpa, LogManager logger,
      Configuration config, ShutdownNotifier pShutdownNotifier) throws InvalidConfigurationException {

    return new CPAAlgorithmFactory(cpa, logger, config, pShutdownNotifier).newInstance();
  }


  private final ForcedCovering forcedCovering;

  private final CPAStatistics               stats = new CPAStatistics();

  private final TransferRelation transferRelation;
  private final MergeOperator mergeOperator;
  private final StopOperator stopOperator;
  private final PrecisionAdjustment precisionAdjustment;

  private final LogManager                  logger;

  private final ShutdownNotifier                   shutdownNotifier;

  private final AlgorithmStatus status;
  private final ConfigurableProgramAnalysis cpa;

  private CPAAlgorithm(
      ConfigurableProgramAnalysis pCpa,
      LogManager logger,
      ShutdownNotifier pShutdownNotifier,
      ForcedCovering pForcedCovering,
      boolean pIsImprecise) {

    cpa = pCpa;
    transferRelation = cpa.getTransferRelation();
    mergeOperator = cpa.getMergeOperator();
    stopOperator = cpa.getStopOperator();
    precisionAdjustment = cpa.getPrecisionAdjustment();
    this.logger = logger;
    this.shutdownNotifier = pShutdownNotifier;
    this.forcedCovering = pForcedCovering;
    status = AlgorithmStatus.SOUND_AND_PRECISE.withPrecise(!pIsImprecise);
  }

  @Override
  public AlgorithmStatus run(final ReachedSet reachedSet) throws CPAException, InterruptedException {
    stats.totalTimer.start();
    try {
      return run0(reachedSet);
    } finally {
      stats.stopAllTimers();
      reachedSet.finalize(cpa);
      stats.updateReachedSetStatistics(reachedSet.getStatistics());
    }
  }

//  private AlgorithmStatus run0(final ReachedSet reachedSet) throws CPAException, InterruptedException {
//    while (reachedSet.hasWaitingState()) {
//      shutdownNotifier.shutdownIfNecessary();
//
//      stats.countIterations++;
//
//      // Pick next state using strategy
//      // BFS, DFS or top sort according to the configuration
//      int size = reachedSet.getWaitlist().size();
//      if (size >= stats.maxWaitlistSize) {
//        stats.maxWaitlistSize = size;
//      }
//      stats.countWaitlistSize += size;
//
//      stats.chooseTimer.start();
//      final AbstractState state = reachedSet.popFromWaitlist();     //取出的状态
//      final Precision precision = reachedSet.getPrecision(state);   //取出的状态对应的精度
//      stats.chooseTimer.stop();
//
//      logger.log(Level.FINER, "Retrieved state from waitlist");
//      try {
//        if (handleState(state, precision, reachedSet)) {
//          // Prec operator requested break
//          return status;
//        }
//      } catch (Exception e) {
//        // re-add the old state to the waitlist, there might be unhandled successors left
//        // that otherwise would be forgotten (which would be unsound)
//        reachedSet.reAddToWaitlist(state);
//        throw e;
//      }
//
//    }
//
//    return status;
//  }

  /**
   * 不再一直探索，每探索一个状态的后继之后，就退出进行下一轮
   * while -> if
   */
  private AlgorithmStatus run0(final ReachedSet reachedSet) throws CPAException, InterruptedException {
    if (reachedSet.hasWaitingState()) {
      shutdownNotifier.shutdownIfNecessary();

      stats.countIterations++;

      // Pick next state using strategy
      // BFS, DFS or top sort according to the configuration
      int size = reachedSet.getWaitlist().size();
      if (size >= stats.maxWaitlistSize) {
        stats.maxWaitlistSize = size;
      }
      stats.countWaitlistSize += size;

      stats.chooseTimer.start();
      final AbstractState state = reachedSet.popFromWaitlist();     //取出的状态
      final Precision precision = reachedSet.getPrecision(state);   //取出的状态对应的精度
      stats.chooseTimer.stop();

      logger.log(Level.FINER, "Retrieved state from waitlist");
      try {
        if (handleState(state, precision, reachedSet)) {
          // Prec operator requested break
          return status;
        }
      } catch (Exception e) {
        // re-add the old state to the waitlist, there might be unhandled successors left
        // that otherwise would be forgotten (which would be unsound)
        reachedSet.reAddToWaitlist(state);
        throw e;
      }

    }

    return status;
  }

  /**
   * Handle one state from the waitlist, i.e., produce successors etc.
   * @param state The abstract state that was taken out of the waitlist
   * @param precision The precision for this abstract state.
   * @param reachedSet The reached set.
   * @return true if analysis should terminate, false if analysis should continue with next state
   * @return 如果分析应该继续则返回值为false，如果分析应该停止则返回true
   */
  private boolean handleState(
      final AbstractState state,
      Precision precision,
      final ReachedSet reachedSet)
      throws CPAException, InterruptedException {
    logger.log(Level.ALL, "Current state is", state, "with precision", precision);

    // forceCovering
    if (forcedCovering != null) {
      stats.forcedCoveringTimer.start();
      try {
        boolean stop = forcedCovering.tryForcedCovering(state, precision, reachedSet);

        if (stop) {
          // TODO: remove state from reached set?
          return false;
        }
      } finally {
        stats.forcedCoveringTimer.stop();
      }
    }

    stats.transferTimer.start();
    Collection<? extends AbstractState> successors;
    try {
      successors = transferRelation.getAbstractSuccessors(state, reachedSet, precision);
    } finally {
      stats.transferTimer.stop();
    }
    // TODO When we have a nice way to mark the analysis result as incomplete,
    // we could continue analysis on a CPATransferException with the next state from waitlist.

    int numSuccessors = successors.size();
    logger.log(Level.FINER, "Current state has", numSuccessors, "successors");
    stats.countSuccessors += numSuccessors;
    stats.maxSuccessors = Math.max(numSuccessors, stats.maxSuccessors);

    // 分析是否应该停止，是否有可能返回true
    for (Iterator<? extends AbstractState> it = successors.iterator(); it.hasNext();) {     //for each e' in (e,π) -> e'
      AbstractState successor = it.next();
      shutdownNotifier.shutdownIfNecessary();
      logger.log(Level.FINER, "Considering successor of current state");
      logger.log(Level.ALL, "Successor of", state, "\nis", successor);

      stats.precisionTimer.start();     //(e_hat, π_hat) = prec(e', π', reached)
      PrecisionAdjustmentResult precAdjustmentResult;
      try {
        // 调整successor的抽象状态和精度
        Optional<PrecisionAdjustmentResult> precAdjustmentOptional =
            precisionAdjustment.prec(
                successor, precision, reachedSet, Functions.identity(), successor);
        //如果调整结果不存在，则跳过当前的successor
        if (!precAdjustmentOptional.isPresent()) {
          continue;
        }
        precAdjustmentResult = precAdjustmentOptional.orElseThrow();    // 如果存在该值则返回改值，否则抛出异常
      } finally {
        stats.precisionTimer.stop();
      }

      successor = precAdjustmentResult.abstractState();     // e_hat，从精度调整结果中取出抽象状态
      Precision successorPrecision = precAdjustmentResult.precision();    //π_hat，取出精度信息
      Action action = precAdjustmentResult.action();

      // 如果精度调整的结果显示CAP算法需要停止分析,successor为targetState，但还要考虑successor被覆盖的情况，如果被覆盖则分析不会停止）
      if (action == Action.BREAK) {
        stats.stopTimer.start();
        boolean stop;
        try {
          stop = stopOperator.stop(successor, reachedSet.getReached(successor), successorPrecision);
        } finally {
          stats.stopTimer.stop();
        }

        if (AbstractStates.isTargetState(successor) && stop) {
          // don't signal BREAK for covered states
          // no need to call merge and stop either, so just ignore this state
          // and handle next successor
          stats.countStop++;
          logger.log(Level.FINER, "Break was signalled but ignored because the state is covered.");
          continue;

        } else {
          stats.countBreak++;
          logger.log(Level.FINER, "Break signalled, CPAAlgorithm will stop.");  // 如果满足stop的条件，（当前分析的successor被覆盖了？），则当前分支不在进行下去

          // add the new state
          reachedSet.add(successor, successorPrecision);    //U {(e_hat, π_hat)}

          if (it.hasNext()) { // 如果还有其他的successor未分析，则应该将当前State重新放回waitlist中，防止其他的successors被遗漏
            // re-add the old state to the waitlist, there are unhandled
            // successors left that otherwise would be forgotten
            reachedSet.reAddToWaitlist(state);
          }

          return true;
        }
      }
      assert action == Action.CONTINUE : "Enum Action has unhandled values!";

      Collection<AbstractState> reached = reachedSet.getReached(successor); // 返回reachedSet的子集，至少包含与successor位置相同的所有抽象状态？

      // An optimization, we don't bother merging if we know that the
      // merge operator won't do anything (i.e., it is merge-sep).
      if (mergeOperator != MergeSepOperator.getInstance() && !reached.isEmpty()) {
        stats.mergeTimer.start();
        try {
          List<AbstractState> toRemove = new ArrayList<>();
          List<Pair<AbstractState, Precision>> toAdd = new ArrayList<>();
          try {
            logger.log(
                Level.FINER, "Considering", reached.size(), "states from reached set for merge");
            for (AbstractState reachedState : reached) {    //for each (e", π") in reached，让successor与reached中的每一个状态进行merge
              shutdownNotifier.shutdownIfNecessary();
              AbstractState mergedState =
                  mergeOperator.merge(successor, reachedState, successorPrecision);   //e_new，执行merge操作之后得到mergeState

              if (!mergedState.equals(reachedState)) {    //if e_new != e"，如果merge之后mergeState与reached中的状态不相同
                logger.log(Level.FINER, "Successor was merged with state from reached set");
                logger.log(
                    Level.ALL, "Merged", successor, "\nand", reachedState, "\n-->", mergedState);
                stats.countMerge++;

                toRemove.add(reachedState);   // \U(e", π")，将reached中对应的状态添加到toRemove
                toAdd.add(Pair.of(mergedState, successorPrecision));    //{e_new, π_hat)}，将mergeState状态及其对应的精度添加到toAdd
              }
            }
          } finally {
            // If we terminate, we should still update the reachedSet if necessary
            // because ARGCPA doesn't like states in toRemove to be in the reachedSet.
            reachedSet.removeAll(toRemove); // 将toRemove中的所有状态从reachedSet中移除
            reachedSet.addAll(toAdd);       // 将toAdd中的所有状态添加到reachedSet中

          }

          if (mergeOperator instanceof ARGMergeJoinCPAEnabledAnalysis) {
            ((ARGMergeJoinCPAEnabledAnalysis) mergeOperator).cleanUp(reachedSet);
          }

        } finally {
          stats.mergeTimer.stop();
        }
      }

      stats.stopTimer.start();      //stop
      boolean stop;
      try {
        stop = stopOperator.stop(successor, reached, successorPrecision);
      } finally {
        stats.stopTimer.stop();
      }

      if (stop) {   // 如果successor被覆盖
        logger.log(Level.FINER, "Successor is covered or unreachable, not adding to waitlist");
        stats.countStop++;

      } else {    // !stop(e_hat, reached, π_hat)，如果successor没有被覆盖，则将successor及其对应的精度放到reachedSet中，同时也会将相应的successor放到Waitlist中
        logger.log(Level.FINER, "No need to stop, adding successor to waitlist");

        stats.addTimer.start();
        reachedSet.add(successor, successorPrecision);    //reached = reached U {(e_hat, π_hat)}

        // 将对应的后继添加到newSuccessorsInEachIteration中
        ((UsageReachedSet) reachedSet).newSuccessorsInEachIteration.put(successor, successorPrecision);

        stats.addTimer.stop();
      }
    }

    return false;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    if (forcedCovering instanceof StatisticsProvider) {
      ((StatisticsProvider)forcedCovering).collectStatistics(pStatsCollection);
    }
    pStatsCollection.add(stats);
  }
}
