/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.inspections.lint;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.detector.api.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;

import java.util.Map;

// TODO: Allow issues to be suppressed in Gradle files (with comments? annotations?)
public class IntellijGradleDetector extends GradleDetector {
  static final Implementation IMPLEMENTATION = new Implementation(
    IntellijGradleDetector.class,
    Scope.GRADLE_SCOPE);

  @Override
  public void visitBuildScript(@NonNull final Context context, Map<String, Object> sharedData) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        final PsiFile psiFile = IntellijLintUtils.getPsiFile(context);
        if (!(psiFile instanceof GroovyFile)) {
          return;
        }
        GroovyFile groovyFile = (GroovyFile)psiFile;
        groovyFile.accept(new GroovyRecursiveElementVisitor() {
          @Override
          public void visitClosure(GrClosableBlock closure) {
            if (closure.getParent() instanceof GrMethodCall) {
              GrMethodCall parent = (GrMethodCall)closure.getParent();
              if (parent.getInvokedExpression() instanceof GrReferenceExpression) {
                GrReferenceExpression invokedExpression = (GrReferenceExpression)(parent.getInvokedExpression());
                if (invokedExpression.getDotToken() == null) {
                  String parentName = invokedExpression.getReferenceName();
                  if (parentName != null && isInterestingBlock(parentName)) {
                    for (PsiElement element : closure.getChildren()) {
                      if (element instanceof GrApplicationStatement) {
                        GrApplicationStatement call = (GrApplicationStatement)element;
                        GrExpression propertyExpression = call.getInvokedExpression();
                        GrCommandArgumentList argumentList = call.getArgumentList();
                        if (propertyExpression instanceof GrReferenceExpression && argumentList != null) {
                          GrReferenceExpression propertyRef = (GrReferenceExpression)propertyExpression;
                          String property = propertyRef.getReferenceName();
                          if (property != null && isInterestingProperty(property, parentName)) {
                            String value = argumentList.getText();
                            checkDslPropertyAssignment(context, property, value, parentName, argumentList);
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            super.visitClosure(closure);
          }

          @Override
          public void visitAssignmentExpression(GrAssignmentExpression expression) {
            GrExpression lValue = expression.getLValue();
            if (lValue instanceof GrReferenceExpression) {
              GrReferenceExpression referenceExpression = (GrReferenceExpression)lValue;
              String referenceName = referenceExpression.getReferenceName();
              if ("projectDir".equals(referenceName)) {
                GrExpression qualifierExpression = referenceExpression.getQualifierExpression();
                if (qualifierExpression != null) {
                  PsiType type = qualifierExpression.getNominalType();
                  String className = null;
                  if (type instanceof PsiClassReferenceType) {
                      className = ((PsiClassReferenceType)type).getClassName();
                  } else if (type == null && ApplicationManager.getApplication().isUnitTestMode()) {
                    // Can't resolve types in unit test context for some reason
                    className = "Project";
                  }
                  if (className != null) {
                    if ((className.equals("Project") || className.equals("ProjectDescriptor")) && context.isEnabled(IDE_SUPPORT)) {
                      String message = "Reassigning the projectDir property of a project will make IDEs confused";
                      context.report(IDE_SUPPORT, createLocation(context, lValue), message, null);
                    }
                  }
                }
              }
            }
            super.visitAssignmentExpression(expression);
          }
        });
      }
    });
  }

  @Override
  protected int getStartOffset(@NonNull Context context, @NonNull Object cookie) {
    PsiElement element = (PsiElement)cookie;
    TextRange textRange = element.getTextRange();
    return textRange.getStartOffset();
  }

  @Override
  protected Location createLocation(@NonNull Context context, @NonNull Object cookie) {
    PsiElement element = (PsiElement)cookie;
    TextRange textRange = element.getTextRange();
    int start = textRange.getStartOffset();
    int end = textRange.getEndOffset();
    return Location.create(context.file, new DefaultPosition(-1, -1, start), new DefaultPosition(-1, -1, end));
  }
}