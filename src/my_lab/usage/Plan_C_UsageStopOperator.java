// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package my_lab.usage;

import java.util.Collection;
import java.util.Collections;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.cpa.usage.UsageCPAStatistics;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class Plan_C_UsageStopOperator implements StopOperator {

    private final StopOperator wrappedStop;
    private final Plan_C_UsageCPAStatistics stats;

    Plan_C_UsageStopOperator(StopOperator pWrappedStop, Plan_C_UsageCPAStatistics pStats) {
        wrappedStop = pWrappedStop;
        stats = pStats;
    }

    @Override
    public boolean stop(
            AbstractState pState, Collection<AbstractState> pReached, Precision pPrecision)
            throws CPAException, InterruptedException {

        UsageState usageState = (UsageState) pState;

        stats.stopTimer.start();
        for (AbstractState reached : pReached) {
            UsageState reachedUsageState = (UsageState) reached;
            stats.usageStopTimer.start();
            boolean result = usageState.isLessOrEqual(reachedUsageState);
            stats.usageStopTimer.stop();
            if (!result) {
                continue;
            }
            stats.innerStopTimer.start();
            result =
                    wrappedStop.stop(
                            usageState.getWrappedState(),
                            Collections.singleton(reachedUsageState.getWrappedState()),
                            pPrecision);
            stats.innerStopTimer.stop();
            if (result) {
                stats.stopTimer.stop();
                return true;
            }
        }
        stats.stopTimer.stop();
        return false;
    }
}

