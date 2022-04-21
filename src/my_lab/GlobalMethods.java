/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package my_lab;

import com.google.common.base.Predicates;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.sosy_lab.common.io.MoreFiles;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGToDotWriter;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.cpa.usage.storage.UnrefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageContainer;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsagePoint;
import org.sosy_lab.cpachecker.util.BiPredicates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

/** This class provides some global static functions. */
@SuppressWarnings("deprecation")
public class GlobalMethods {

  /**
   * This function exports the dot file of the reached set pReachedSet.
   *
   * @param pReachedSet The reached set.
   * @param pFileName The filename of the output dot file.
   */
  public static void exportARG(final ReachedSet pReachedSet, String pFileName) {
    assert pReachedSet != null && pFileName != null && !pFileName.isEmpty();

    // get the root state and the final state.
    ARGState rootState = (ARGState) pReachedSet.getFirstState(),
        finalState = (ARGState) pReachedSet.getLastState();
    // extract the state pair of an edge in a counterexample, used for highlighting it.
    List<Pair<ARGState, ARGState>> cexpStatePairs =
        finalState.isTarget()
            ? ARGUtils.getOnePathTo(finalState).getStatePairs()
            : new ArrayList<>();
    // export the dot file.
    try (Writer w = MoreFiles.openOutputFile(Paths.get(pFileName), Charset.defaultCharset())) {
      ARGToDotWriter.write(
          w,
          rootState,
          ARGState::getChildren,
          Predicates.alwaysTrue(),
          BiPredicates.pairIn(cexpStatePairs));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   *
   * @param unrefinedIds
   */
  public static void printUsagesInfo(String pFileName, final NavigableMap<SingleIdentifier, UnrefinedUsagePointSet> unrefinedIds) {
    assert unrefinedIds != null && pFileName != null;
    Set<Map.Entry<SingleIdentifier, UnrefinedUsagePointSet>> usagesInfo = unrefinedIds.entrySet();
    Iterator<Map.Entry<SingleIdentifier, UnrefinedUsagePointSet>> it = usagesInfo.iterator();

    try (Writer w = new FileWriter(pFileName, Charset.defaultCharset())) {
      while (it.hasNext()) {
        Map.Entry<SingleIdentifier, UnrefinedUsagePointSet> currentId = it.next();
        String variableName = currentId.getKey().toString();
        UnrefinedUsagePointSet currentInfoSet = currentId.getValue();
        NavigableSet<UsagePoint> topUsages = currentInfoSet.getTopUsages();
        String topUsagesStr = "";
        Iterator<UsagePoint> ite = topUsages.iterator();
        int i = 0;
        while (ite.hasNext()) { topUsagesStr += "\t"+ite.next().toString()+"\n"; i++; }
        if (i > 1) {
          w.write(variableName + "|---> " + topUsagesStr + "\n");
          w.write("\n");
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
