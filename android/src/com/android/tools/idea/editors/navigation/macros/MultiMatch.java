/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation.macros;

import com.android.tools.idea.editors.navigation.NavigationEditor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MultiMatch {
  public final PsiMethod macro;
  public final Map<String, PsiMethod> subMacros = new LinkedHashMap<String, PsiMethod>(); // make deterministic while prototyping

  public MultiMatch(PsiMethod macro) {
    this.macro = macro;
  }

  public void addSubMacro(String name, PsiMethod macro) {
    subMacros.put(name, macro);
  }

  @Nullable
  public Bindings match(PsiElement element) {
    Map<String, PsiElement> bindings = Unifier.match(macro, element);
    if (NavigationEditor.DEBUG) System.out.println("bindings = " + bindings);
    if (bindings == null) {
      return null;
    }
    Map<String, Map<String, PsiElement>> subBindings = new HashMap<String, Map<String, PsiElement>>();
    for (Map.Entry<String, PsiMethod> entry : subMacros.entrySet()) {
      String name = entry.getKey();
      PsiMethod template = entry.getValue();
      Map<String, PsiElement> subBinding = Unifier.match(template, bindings.get(name));
      if (subBinding == null) {
        return null;
      }
      subBindings.put(name, subBinding);
    }
    return new Bindings(bindings, subBindings);
  }

  public String instantiate(Bindings2 bindings) {
    Map<String, String> bb = bindings.bindings;

    for (Map.Entry<String, PsiMethod> entry : subMacros.entrySet()) {
      String name = entry.getKey();
      PsiMethod template = entry.getValue();
      bb.put(name, Instantiation.instantiate2(template, bindings.subBindings.get(name)));
    }

    return Instantiation.instantiate2(macro, bb);
  }

  public static class Bindings {
    public final Map<String, PsiElement> bindings;
    public final Map<String, Map<String, PsiElement>> subBindings;

    Bindings(Map<String, PsiElement> bindings, Map<String, Map<String, PsiElement>> subBindings) {
      this.bindings = bindings;
      this.subBindings = subBindings;
    }
  }

  public static class Bindings2 {
    public final Map<String, String> bindings;
    public final Map<String, Map<String, String>> subBindings;

    Bindings2(Map<String, String> bindings, Map<String, Map<String, String>> subBindings) {
      this.bindings = bindings;
      this.subBindings = subBindings;
    }

    Bindings2() {
      this(new HashMap<String, String>(), new HashMap<String, Map<String, String>>());
    }

    public void put(String key, String value) {
      bindings.put(key, value);
    }

    public void put(String key1, String key2, String value) {
      Map<String, String> subBinding = subBindings.get(key1);
      if (subBinding == null) {
        subBindings.put(key1, subBinding = new HashMap<String, String>());
      }
      subBinding.put(key2, value);
    }
  }
}