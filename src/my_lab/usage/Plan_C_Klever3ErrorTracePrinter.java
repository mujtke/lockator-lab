// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package my_lab.usage;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cpa.bam.BAMMultipleCEXSubgraphComputer;
import org.sosy_lab.cpachecker.cpa.lock.LockTransferRelation;
import org.sosy_lab.cpachecker.cpa.usage.KleverErrorTracePrinter;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.GraphMlBuilder;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.KeyDef;
import org.w3c.dom.Element;

public class Plan_C_Klever3ErrorTracePrinter extends Plan_C_KleverErrorTracePrinter {

    public Plan_C_Klever3ErrorTracePrinter(
            Configuration pC,
            BAMMultipleCEXSubgraphComputer pT,
            CFA pCfa,
            LogManager pL,
            LockTransferRelation pLT)
            throws InvalidConfigurationException {
        super(pC, pT, pCfa, pL, pLT);
    }

    @Override
    protected String formatNote(String value) {
        return "level=\"1\" hide=\"false\" value=\"" + escapeQuotes(value) + "\"";
    }

    @Override
    protected void printWarningTo(GraphMlBuilder builder, Element element, String message) {
        String warning = "level=\"0\" hide=\"false\" value=\"" + escapeQuotes(message) + "\"";
        builder.addDataElementChild(element, KeyDef.NOTE, warning);
    }

    @Override
    protected Element printEdge(GraphMlBuilder builder, CFAEdge edge) {
        Element result = super.printEdge(builder, edge);
        if (edge instanceof CDeclarationEdge) {
            builder.addDataElementChild(result, KeyDef.VAR_DECLARATION, "true");
        }
        return result;
    }

    private String escapeQuotes(String message) {
        return message.replaceAll("\"", "\\\\\"");
    }
}
