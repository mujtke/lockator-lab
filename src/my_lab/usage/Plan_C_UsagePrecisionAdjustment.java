// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package my_lab.usage;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import java.util.Optional;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSetView;
import org.sosy_lab.cpachecker.cpa.usage.UsageCPAStatistics;
import org.sosy_lab.cpachecker.cpa.usage.UsageState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

class Plan_C_UsagePrecisionAdjustment implements PrecisionAdjustment {

    private final PrecisionAdjustment wrappedPrecAdjustment;
    private final Plan_C_UsageCPAStatistics stats;

    public Plan_C_UsagePrecisionAdjustment(
            PrecisionAdjustment pWrappedPrecAdjustment,
            Plan_C_UsageCPAStatistics pStats) {
        wrappedPrecAdjustment = pWrappedPrecAdjustment;
        stats = pStats;
    }

    @Override
    public Optional<PrecisionAdjustmentResult> prec(
            AbstractState pElement,
            Precision oldPrecision,
            UnmodifiableReachedSet pElements,
            Function<AbstractState, AbstractState> stateProjection,
            AbstractState fullState)
            throws CPAException, InterruptedException {

        stats.precTimer.start();
        Preconditions.checkArgument(pElement instanceof UsageState);
        UsageState element = (UsageState) pElement;

        UnmodifiableReachedSet elements =
                new UnmodifiableReachedSetView(
                        pElements, AbstractSingleWrapperState.getUnwrapFunction(), Functions.identity());

        AbstractState oldElement = element.getWrappedState();

        Optional<PrecisionAdjustmentResult> optionalUnwrappedResult =
                wrappedPrecAdjustment.prec(
                        oldElement,
                        oldPrecision,
                        elements,
                        Functions.compose(AbstractSingleWrapperState.getUnwrapFunction(), stateProjection),
                        fullState);

        if (!optionalUnwrappedResult.isPresent()) {
            stats.precTimer.stop();
            return Optional.empty();
        }

        PrecisionAdjustmentResult unwrappedResult = optionalUnwrappedResult.orElseThrow();

        AbstractState newElement = unwrappedResult.abstractState();
        Precision newPrecision = unwrappedResult.precision();
        Action action = unwrappedResult.action();

        if ((oldElement == newElement) && (oldPrecision == newPrecision)) {
            // nothing has changed
            stats.precTimer.stop();
            return Optional.of(PrecisionAdjustmentResult.create(pElement, oldPrecision, action));
        }

        UsageState resultElement = element.copy(newElement);

        stats.precTimer.stop();
        return Optional.of(PrecisionAdjustmentResult.create(resultElement, newPrecision, action));
    }
}
