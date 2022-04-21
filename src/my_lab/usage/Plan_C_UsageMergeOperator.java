// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package my_lab.usage;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.usage.UsageCPAStatistics;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class Plan_C_UsageMergeOperator implements MergeOperator {

    private final MergeOperator wrappedMerge;
    private final Plan_C_UsageCPAStatistics stats;
    private final boolean argsBind;

    public Plan_C_UsageMergeOperator(MergeOperator wrapped, Plan_C_UsageCPAStatistics pStats, boolean argsBinded) {
        wrappedMerge = wrapped;
        stats = pStats;
        argsBind = argsBinded;
    }

    @Override
    public AbstractState merge(AbstractState pState1, AbstractState pState2, Precision pPrecision)
            throws CPAException, InterruptedException {
        if (argsBind) {
            return pState2;
        } else {
            stats.mergeTimer.start();
            UsageState uState1 = (UsageState) pState1;
            UsageState uState2 = (UsageState) pState2;

            AbstractState wrappedState1 = uState1.getWrappedState();
            AbstractState wrappedState2 = uState2.getWrappedState();

            AbstractState mergedState = wrappedMerge.merge(wrappedState1, wrappedState2, pPrecision);

            UsageState result;

            if (uState1.isLessOrEqual(uState2)) {
                result = uState2.copy(mergedState);
            } else if (uState2.isLessOrEqual(uState1)) {
                result = uState1.copy(mergedState);
            } else {
                result = uState1.copy(mergedState);
                result.join(uState2);
            }

            if (mergedState.equals(wrappedState2) && result.equals(uState2)) {
                stats.mergeTimer.stop();
                return pState2;
            } else {
                stats.mergeTimer.stop();
                return result;
            }
        }
    }
}
