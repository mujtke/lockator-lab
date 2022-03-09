// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage;

import java.io.PrintStream;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ForwardingReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMCPA;
import org.sosy_lab.cpachecker.cpa.bam.BAMMultipleCEXSubgraphComputer;
import org.sosy_lab.cpachecker.cpa.lock.LockTransferRelation;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

@Options(prefix = "cpa.usage")
public class UsageCPAStatistics implements Statistics {

  public enum OutputFileType {
    ETV,
    KLEVER,
    KLEVER3,
    KLEVER_OLD
  }

  @Option(
    name = "outputType",
    description = "all variables should be printed to the one file or to the different",
    secure = true
  )
  private OutputFileType outputFileType = OutputFileType.KLEVER;

  @Option(
    name = "printUnsafesIfUnknown",
    description = "print found unsafes in case of unknown verdict",
    secure = true)
  private boolean printUnsafesInCaseOfUnknown = true;

  /* Previous container is used when internal time limit occurs
   * and we need to store statistics. In current one the information can be not
   * relevant (for example not all ARG was built).
   * It is better to store unsafes from previous iteration of refinement.
   */

  private final LogManager logger;

  private final Configuration config;
  private final LockTransferRelation lockTransfer;
  private ErrorTracePrinter errPrinter;
  private final CFA cfa;

  private BAMMultipleCEXSubgraphComputer computer;

  final StatTimer transferRelationTimer = new StatTimer("Time for transfer relation");
  final StatTimer transferForEdgeTimer = new StatTimer("Time for transfer for edge");
  final StatTimer bindingTimer = new StatTimer("Time for binding computation");
  final StatTimer checkForSkipTimer = new StatTimer("Time for checking edge for skip");
  final StatTimer innerAnalysisTimer = new StatTimer("Time for inner analyses");
  final StatTimer stopTimer = new StatTimer("Time for stop operator");
  final StatTimer innerStopTimer = new StatTimer("Time for inner stop operators");
  final StatTimer usageStopTimer = new StatTimer("Time for usage stop operator");
  final StatTimer precTimer = new StatTimer("Time for prec operator");
  final StatTimer mergeTimer = new StatTimer("Time for merge operator");
  private final StatTimer printStatisticsTimer = new StatTimer("Time for printing statistics");
  private final StatTimer printUnsafesTimer = new StatTimer("Time for unsafes printing");
  // public final StatCounter numberOfStatesCounter = new StatCounter("Number of states");

  public UsageCPAStatistics(
      Configuration pConfig, LogManager pLogger, CFA pCfa, LockTransferRelation lTransfer)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    logger = pLogger;
    lockTransfer = lTransfer;
    config = pConfig;
    cfa = pCfa;
    computer = null;
  }

  @Override
  public void printStatistics(
      final PrintStream out, final Result result, final UnmodifiableReachedSet reached) {

    StatisticsWriter writer = StatisticsWriter.writingStatisticsTo(out);
    writer.put(transferRelationTimer)
        .beginLevel()
        .put(transferForEdgeTimer)
        .beginLevel()
        .put(checkForSkipTimer)
          .put(bindingTimer)
          .put(innerAnalysisTimer)
        .endLevel()
        .endLevel()
        .put(precTimer)
        .put(mergeTimer)
        .put(stopTimer)
        .beginLevel()
        .put(usageStopTimer)
        .put(innerStopTimer)
        .endLevel();

    ReachedSet reachedSet = (ReachedSet) reached;
    while (reachedSet instanceof ForwardingReachedSet) {
      // Twice Forwarding -> ThreadModular -> Usage
      reachedSet = ((ForwardingReachedSet) reachedSet).getDelegate();
    }
    UsageReachedSet uReached = (UsageReachedSet) reachedSet;

    if (printUnsafesInCaseOfUnknown || result != Result.UNKNOWN) {
      printUnsafesTimer.start();
      try {
        switch (outputFileType) {
          case KLEVER3:
            errPrinter = new Klever3ErrorTracePrinter(config, computer, cfa, logger, lockTransfer);
            break;
          case KLEVER:
            errPrinter = new KleverErrorTracePrinter(config, computer, cfa, logger, lockTransfer);
            break;
          case KLEVER_OLD:
            errPrinter =
                new KleverErrorTracePrinterOld(config, computer, cfa, logger, lockTransfer);
            break;
          case ETV:
            errPrinter = new ETVErrorTracePrinter(config, computer, cfa, logger, lockTransfer);
            break;
          default:
            throw new UnsupportedOperationException("Unknown type " + outputFileType);
        }
        errPrinter.printErrorTraces(uReached);
        errPrinter.printStatistics(writer);
      } catch (InvalidConfigurationException e) {
        logger.log(Level.SEVERE, "Cannot create error trace printer: " + e.getMessage());
      }
      printUnsafesTimer.stop();
    }

    printStatisticsTimer.start();
    UsageState.get(reached.getFirstState()).getStatistics().printStatistics(writer);
    uReached.printStatistics(writer);
    writer.put(printUnsafesTimer);
    printStatisticsTimer.stop();
    writer.put(printStatisticsTimer);
  }

  public void setBAMCPA(BAMCPA pBamCpa) {
    computer = pBamCpa.createBAMMultipleSubgraphComputer(ARGState::getStateId);
  }

  @Override
  public @Nullable String getName() {
    return "UsageCPAStatistics";
  }
}
