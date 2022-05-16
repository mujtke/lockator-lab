// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package my_lab.algorithm;

import static com.google.common.base.Verify.verifyNotNull;
import static com.google.common.collect.FluentIterable.from;
import static my_lab.GlobalMethods.exportARG;
import static my_lab.GlobalMethods.printUsagesInfo;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.common.collect.ImmutableList;
import my_lab.usage.Plan_C_UsageReachedSet;
import org.sosy_lab.common.AbstractMBean;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.ParallelAlgorithm.ReachedSetUpdateListener;
import org.sosy_lab.cpachecker.core.algorithm.ParallelAlgorithm.ReachedSetUpdater;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.composite.CompositeState;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class Plan_C_Algorithm implements Algorithm, StatisticsProvider, ReachedSetUpdater {

    private static class CEGARStatistics implements Statistics {

        private final Timer totalTimer = new Timer();

        @Override
        public String getName() {
            return "Plan_C_algorithm";
        }

        @Override
        public void printStatistics(PrintStream out, Result pResult, UnmodifiableReachedSet pReached) {

        }
    }

    private class Plan_C_AlgorithmBean extends AbstractMBean {
        public Plan_C_AlgorithmBean() {
            super("my_lab.algorithm:type=Plan_C_algorithm", logger);
        }
    }

    private final my_lab.algorithm.Plan_C_Algorithm.CEGARStatistics stats = new my_lab.algorithm.Plan_C_Algorithm.CEGARStatistics();

    private final List<ReachedSetUpdateListener> reachedSetUpdateListeners =
            new CopyOnWriteArrayList<>();

    @Options(prefix = "cegar")
    public static class Plan_C_AlgorithmFactory implements AlgorithmFactory {

        private final AlgorithmFactory algorithmFactory;
        private final LogManager logger;

        public Plan_C_AlgorithmFactory(
                Algorithm pAlgorithm,
                ConfigurableProgramAnalysis pCpa,
                LogManager pLogger,
                Configuration pConfig,
                ShutdownNotifier pShutdownNotifier)
                throws InvalidConfigurationException {
            this(() -> pAlgorithm, pCpa, pLogger, pConfig, pShutdownNotifier);
        }

        public Plan_C_AlgorithmFactory(
                AlgorithmFactory pAlgorithmFactory,
                ConfigurableProgramAnalysis pCpa,
                LogManager pLogger,
                Configuration pConfig,
                ShutdownNotifier pShutdownNotifier)
                throws InvalidConfigurationException {
            pConfig.inject(this);
            algorithmFactory = pAlgorithmFactory;
            logger = pLogger;
        }

        @Override
        public my_lab.algorithm.Plan_C_Algorithm newInstance() {
            return new my_lab.algorithm.Plan_C_Algorithm(
                    algorithmFactory.newInstance(), logger);
        }
    }

    private final LogManager logger;
    private final Algorithm algorithm;

    /** This constructor gets a Refiner object instead of generating it from the refiner parameter. */
    private Plan_C_Algorithm(
            Algorithm pAlgorithm,
            LogManager pLogger) {
        algorithm = pAlgorithm;
        logger = pLogger;

        // don't store it because we wouldn't know when to unregister anyway
        new my_lab.algorithm.Plan_C_Algorithm.Plan_C_AlgorithmBean().register();
    }

    /**
     * 重新修改逻辑，用于Plan_C
     * @param reached
     * @return
     * @throws CPAException
     * @throws InterruptedException
     */
    @Override
    public AlgorithmStatus run(ReachedSet reached) throws CPAException, InterruptedException {
        AlgorithmStatus status = AlgorithmStatus.SOUND_AND_PRECISE;

        stats.totalTimer.start();
        try {
            boolean raceFound;    // 是否发现了race
            int dotFileIndex = 1; // for debug
            int usagePairFileIndex = 1; // for debug
            int iteration = 1;
            do {
                raceFound = false;
                // run algorithm
                // debug
//                System.out.println("get successors in iteration" + iteration++); if (iteration > 1000) { break; }

                status = status.update(algorithm.run(reached));     // 更新可达集，不会探索完全，每轮探索一个状态的后继
                if (((Plan_C_UsageReachedSet)reached).newSuccessorsInEachIteration.isEmpty()) {  // 新的一轮计算中没有产生后继状态
                    if (reached.hasWaitingState()) {    // 还有后继没有探索
                        continue;                         // 直接进入下一轮计算
                    }
                    if (!reached.hasWaitingState()) {   // 若可达图探索完成
                        //System.out.println("reachable graph's exploration finished");

                        if (((Plan_C_UsageReachedSet) reached).isStillHaveCoveredStates()) {    // 如果还有被cover的状态，则搜索还应该继续

                           // { // 重新开始计算之前，打印一些之前的ARG
                           //     if (dotFileIndex > 5) {
                           //         break;
                           //     }
                           //     exportARG(reached, "./output/thread_test/debug/_" + (dotFileIndex++) + "_.dot");
                           // }
                            /* 对cover状态的处理 */
                            /* 先将cover状态全部放回到waitlist中 */
                            ((Plan_C_UsageReachedSet)reached).rollbackCoveredStates();
                            continue;

                        }
                        else {                                                  // 没有被cover的状态
                            break;                                                // 结束整个计算
                        }
                    }
                }

                notifyReachedSetUpdateListeners(reached);
                if (haveUnsafeOrNot(reached) == true) {                         // 如果发现了race
                    //System.out.println("race found");
                    ((Plan_C_UsageReachedSet) reached).setHaveUnsafes(true);           // 将可达集合含有不安全设置为true
                    break;
                }

                // 将新产生的后继中的Location存放到visitedLocations中
                Set<AbstractState> newGottenLocs = ((Plan_C_UsageReachedSet)reached).newSuccessorsInEachIteration.keySet();
                Iterator<AbstractState> it = newGottenLocs.iterator();
                while (it.hasNext()) {
                    AbstractState p = it.next();
//                    UsageState u = (UsageState) ((ARGState)p).getWrappedState();
//                    CompositeState c = (CompositeState) u.getWrappedState();
                    AbstractStateWithLocations statesWithLocs = extractStateByType(p, AbstractStateWithLocations.class);
                    for (CFANode node : statesWithLocs.getLocationNodes()) {
                        Plan_C_UsageReachedSet.visitedLocations.add(node);
                    }
                }

                // 在下一次计算后继时，清空上一次计算得到的后继
                ((Plan_C_UsageReachedSet)reached).newSuccessorsInEachIteration.clear();

            } while (!raceFound);

        } finally {
            stats.totalTimer.stop();
            // 打印一下CEGAR算法的总时间
            //System.out.println("Total time for CEGAR algorithm:   " + stats.totalTimer);
            //System.out.println("reached.size:  " + reached.size());

            // 打印一下threading对应的ARG
            //exportARG(reached, "./output/thread_test/debug/_" + "thread16_.dot");

        }
        return status;
    }

    private boolean haveUnsafeOrNot(ReachedSet reached) {
        if (((Plan_C_UsageReachedSet) reached).newSuccessorsInEachIteration.isEmpty())
            return false;
        return ((Plan_C_UsageReachedSet)reached).haveUnsafeInNewSucs();
    }

    @Override
    public void collectStatistics(Collection<Statistics> pStatsCollection) {
        if (algorithm instanceof StatisticsProvider) {
            ((StatisticsProvider)algorithm).collectStatistics(pStatsCollection);
        }
        pStatsCollection.add(stats);
    }

    @Override
    public void register(ReachedSetUpdateListener pReachedSetUpdateListener) {
        if (algorithm instanceof ReachedSetUpdater) {
            ((ReachedSetUpdater) algorithm).register(pReachedSetUpdateListener);
        }
        reachedSetUpdateListeners.add(pReachedSetUpdateListener);
    }

    @Override
    public void unregister(ReachedSetUpdateListener pReachedSetUpdateListener) {
        if (algorithm instanceof ReachedSetUpdater) {
            ((ReachedSetUpdater) algorithm).unregister(pReachedSetUpdateListener);
        }
        reachedSetUpdateListeners.remove(pReachedSetUpdateListener);
    }

    private void notifyReachedSetUpdateListeners(ReachedSet pReachedSet) {
        for (ReachedSetUpdateListener rsul : reachedSetUpdateListeners) {
            rsul.updated(pReachedSet);
        }
    }

}
