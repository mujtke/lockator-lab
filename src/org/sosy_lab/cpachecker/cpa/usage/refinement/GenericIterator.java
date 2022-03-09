// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.refinement;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.ForOverride;
import java.util.ArrayList;
import java.util.List;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.interfaces.AdjustablePrecision;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public abstract class GenericIterator<I, O> extends WrappedConfigurableRefinementBlock<I, O> {
  private StatTimer totalTimer = new StatTimer("Time for generic iterator");
  private StatCounter numOfIterations = new StatCounter("Number of iterations");

  Iterable<AdjustablePrecision> completePrecisions;
  private final ShutdownNotifier shutdownNotifier;

  // Some iterations may be postponed to the end (complicated ones)
  List<O> postponedIterations = new ArrayList<>();

  protected GenericIterator(ConfigurableRefinementBlock<O> pWrapper, ShutdownNotifier pNotifier) {
    super(pWrapper);
    shutdownNotifier = pNotifier;
  }

  @Override
  public final RefinementResult performBlockRefinement(I pInput) throws CPAException, InterruptedException {

    O iteration;

    totalTimer.start();
    completePrecisions = ImmutableSet.of();
    RefinementResult result = RefinementResult.createFalse();
    init(pInput);   // ?

    try {
      while ((iteration = getNext(pInput)) != null) {   // 这里的iteration在不同的Iterator里面不相同，取决于O的类型
        shutdownNotifier.shutdownIfNecessary();
        result = iterate(iteration);
        if (result.isTrue()) {
          return result;
        }
      }
      //Check postponed iterations
      for (O i : postponedIterations) {
        shutdownNotifier.shutdownIfNecessary();
        result = iterate(i);
        if (result.isTrue()) {
          return result;
        }
      }
      result.addPrecisions(completePrecisions);
      return result;
    } finally {
      finish(pInput, result);
      completePrecisions = ImmutableSet.of();
      sendFinishSignal();
      postponedIterations.clear();
      totalTimer.stop();
      shutdownNotifier.shutdownIfNecessary();
    }
  }

  private RefinementResult iterate(O iteration) throws CPAException, InterruptedException {
    numOfIterations.inc();
    RefinementResult result = wrappedRefiner.performBlockRefinement(iteration);

    if (result.isTrue()) {
      //Finish iteration, the race is found
      result.addPrecisions(completePrecisions);
      return result;
    }

    completePrecisions = Iterables.concat(completePrecisions, result.getPrecisions());

    finishIteration(iteration, result);
    return result;
  }


  abstract protected O getNext(I pInput);

  protected void init(@SuppressWarnings("unused") I pInput) {}

  protected void finish(
      @SuppressWarnings("unused") I pInput, @SuppressWarnings("unused") RefinementResult pResult) {}

  @ForOverride
  protected void finishIteration(
      @SuppressWarnings("unused") O output, @SuppressWarnings("unused") RefinementResult r) {}

  @ForOverride
  protected void printDetailedStatistics(@SuppressWarnings("unused") StatisticsWriter pOut) {}

  @Override
  public final void printStatistics(StatisticsWriter pOut) {
    StatisticsWriter writer = pOut.spacer()
        .put(totalTimer)
        .put(numOfIterations);

    printDetailedStatistics(writer);
    wrappedRefiner.printStatistics(writer);
  }

  protected void postpone(O i) {
    postponedIterations.add(i);
  }

}
