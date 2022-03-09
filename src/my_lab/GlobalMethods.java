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
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.sosy_lab.common.io.MoreFiles;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGToDotWriter;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.util.BiPredicates;
import org.sosy_lab.cpachecker.util.Pair;

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
}
