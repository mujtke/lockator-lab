// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;

@Options(prefix = "cpa.usage.skippedvariables")
public class VariableSkipper {
  @Option(description = "variables, which will be filtered by its name", secure = true)
  private Set<String> byName = ImmutableSet.of();

  @Option(description = "variables, which will be filtered by its name prefix", secure = true)
  private Set<String> byNamePrefix = ImmutableSet.of();

  @Option(description = "variables, which will be filtered by its type", secure = true)
  private Set<String> byType = ImmutableSet.of();

  @Option(description = "variables, which will be filtered by function location", secure = true)
  private Set<String> byFunction = ImmutableSet.of();

  @Option(description = "variables, which will be filtered by function prefix", secure = true)
  private Set<String> byFunctionPrefix = ImmutableSet.of();

  public VariableSkipper(Configuration pConfig) throws InvalidConfigurationException {
    pConfig.inject(this);
  }

  public boolean shouldBeSkipped(AbstractIdentifier id, String functionName) {

    if (id instanceof SingleIdentifier) {
      SingleIdentifier singleId = (SingleIdentifier) id;
      if (checkId(singleId)) {      // 判断是否属于提前标记的需要跳过的类别
        return true;
      } else if (singleId instanceof StructureIdentifier) {     // 如果Id属于结构体Id
        AbstractIdentifier owner = singleId;
        while (owner instanceof StructureIdentifier) {
          owner = ((StructureIdentifier) owner).getOwner();     // 逐渐向上获取Id的所属者
        }
        if (owner instanceof SingleIdentifier && checkId((SingleIdentifier) owner)) {     // 提取了所属者之后，再判断是否是提前标记需要跳过的
          return true;
        }
      }
    }

    // Check special functions like INIT_LIST_HEAD, in which we should skip all usages
    if (byFunction.contains(functionName)) {
      return true;
    }

    if (from(byFunctionPrefix).anyMatch(functionName::startsWith)) {
      return true;
    }

    return false;
  }

  private boolean checkId(SingleIdentifier singleId) {
    String varName = singleId.getName();

    if (byName.contains(varName)) {   // 通过名称来跳过相应的Id
      return true;
    }
    if (from(byNamePrefix).anyMatch(varName::startsWith)) {   // 通过名称前缀跳过相应的Id
      return true;
    }
    if (!byType.isEmpty()) {          // 通过类型
      CType idType = singleId.getType();
      if (idType instanceof CArrayType) {
        idType = ((CArrayType) idType).getType();
      }
      String typeString = idType.toString();
      typeString = typeString.replaceAll("\\(", "");
      typeString = typeString.replaceAll("\\)", "");
      if (byType.contains(typeString)) {
        return true;
      }
    }
    return false;
  }
}
