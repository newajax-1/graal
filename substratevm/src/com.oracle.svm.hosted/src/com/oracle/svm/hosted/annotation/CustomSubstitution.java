/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class CustomSubstitution<T extends CustomSubstitutionType<?, ?>> extends SubstitutionProcessor {

    protected final MetaAccessProvider metaAccess;
    protected final Map<ResolvedJavaType, T> typeSubstitutions;

    public CustomSubstitution(MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
        this.typeSubstitutions = new HashMap<>();
    }

    protected T getSubstitutionType(ResolvedJavaType original) {
        return typeSubstitutions.get(original);
    }

    protected void addSubstitutionType(ResolvedJavaType orignal, T substitution) {
        assert substitution != null;
        typeSubstitutions.put(orignal, substitution);
    }

    /*
     * For deoptimization, we add "*" to the method name to distinguish the different variants of
     * the method (this is necessary to make the method names unique). Remove that suffix here.
     */
    protected static String canonicalMethodName(ResolvedJavaMethod method) {
        String result = method.getName();
        while (result.endsWith("*")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    protected static List<String> findAttributes(ResolvedJavaType annotationType) {
        List<String> attributes = new ArrayList<>();
        for (ResolvedJavaMethod method : annotationType.getDeclaredMethods()) {
            String methodName = canonicalMethodName(method);
            if (methodName.equals("equals") || methodName.equals("hashCode") || methodName.equals("toString") || methodName.equals("annotationType")) {
                /* Ignore non-accessor methods. */
            } else {
                attributes.add(methodName);
            }
        }

        /* Sort them (by any order) so that the Graal graphs are deterministic. */
        Collections.sort(attributes);
        return attributes;
    }

    protected static ResolvedJavaMethod findMethod(ResolvedJavaType declaringType, String name, ResolvedJavaType... argumentTypes) {
        ResolvedJavaMethod result = null;
        outer: for (ResolvedJavaMethod method : declaringType.getDeclaredMethods()) {
            if (canonicalMethodName(method).equals(name) && method.getSignature().getParameterCount(false) == argumentTypes.length) {
                Signature sig = method.getSignature();
                for (int i = 0; i < argumentTypes.length; i++) {
                    if (!sig.getParameterType(i, null).resolve(null).isAssignableFrom(argumentTypes[i])) {
                        continue outer;
                    }
                }
                assert result == null : "more than one matching method found";
                result = method;
            }
        }
        assert result != null : "no matching method found";
        return result;
    }

    protected static ResolvedJavaField findField(ResolvedJavaType declaringType, String name) {
        ResolvedJavaField result = null;
        for (ResolvedJavaField field : declaringType.getInstanceFields(false)) {
            if (field.getName().equals(name)) {
                assert result == null : "more than one matching field found";
                result = field;
            }
        }
        assert result != null : "no matching field found";
        return result;
    }

}
