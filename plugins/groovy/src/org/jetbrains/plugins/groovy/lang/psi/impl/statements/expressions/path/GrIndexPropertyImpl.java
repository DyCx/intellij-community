/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ilyas
 */
public class GrIndexPropertyImpl extends GrCallExpressionImpl implements GrIndexProperty {

  public GrIndexPropertyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitIndexProperty(this);
  }

  public String toString() {
    return "Property by index";
  }

  @NotNull
  public GrExpression getSelectedExpression() {
    GrExpression result = findChildByClass(GrExpression.class);
    assert result != null;
    return result;
  }

  public PsiType getType() {
    GrExpression selected = getSelectedExpression();
    PsiType thisType = selected.getType();

    if (thisType != null) {
      if (thisType instanceof PsiArrayType) {
        PsiType componentType = ((PsiArrayType) thisType).getComponentType();
        return TypesUtil.boxPrimitiveType(componentType, getManager(), getResolveScope());
      }
      
      PsiType[] argTypes = PsiUtil.getArgumentTypes(selected, false, true);
      return TypesUtil.getOverloadedOperatorType(thisType, "getAt", this, argTypes);
    }
    return null;
  }

  @Nullable
  public PsiMethod resolveMethod() {
    return null; //todo
  }

  @NotNull
  public GroovyResolveResult[] getMethodVariants() {
    return GroovyResolveResult.EMPTY_ARRAY;
  }
}