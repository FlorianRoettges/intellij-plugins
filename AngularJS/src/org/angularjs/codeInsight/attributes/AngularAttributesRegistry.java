package org.angularjs.codeInsight.attributes;

import com.intellij.lang.javascript.psi.stubs.JSImplicitElement;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.xml.XmlAttributeDescriptor;
import org.angularjs.codeInsight.DirectiveUtil;
import org.angularjs.index.AngularControllerIndex;
import org.angularjs.index.AngularIndexUtil;
import org.angularjs.index.AngularModuleIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public class AngularAttributesRegistry {
  static AngularAttributeDescriptor createDescriptor(@Nullable final Project project,
                                                     @NotNull String directiveName,
                                                     @Nullable PsiElement declaration) {
    if ("ng-controller".equals(directiveName)) {
      return new AngularAttributeDescriptor(project, directiveName, AngularControllerIndex.KEY, declaration);
    }
    if ("ng-app".equals(directiveName)) {
      return new AngularAttributeDescriptor(project, directiveName, AngularModuleIndex.KEY, declaration);
    }
    return new AngularAttributeDescriptor(project, directiveName, null, declaration);
  }

  public static boolean isAngularExpressionAttribute(XmlAttribute parent) {
    final String type = getType(parent);
    return type.endsWith("expression") || type.startsWith("string");
  }

  public static boolean isJSONAttribute(XmlAttribute parent) {
    final String value = parent.getValue();
    if (value == null || !value.startsWith("{")) return false;

    final String type = getType(parent);
    return type.contains("object literal") || type.equals("mixed");
  }

  @NotNull
  private static String getType(XmlAttribute parent) {
    final String attributeName = DirectiveUtil.normalizeAttributeName(parent.getName());
    XmlAttributeDescriptor descriptor = AngularJSAttributeDescriptorsProvider.getDescriptor(attributeName, parent.getParent());
    final PsiElement directive = descriptor != null ? descriptor.getDeclaration() : null;
    if (directive instanceof JSImplicitElement) {
      final String restrict = ((JSImplicitElement)directive).getTypeString();
      final String [] args = restrict != null ? restrict.split(";", -1) : null;
      return args != null && args.length > 2 ? args[2] : "";
    }
    return "";
  }

  public static boolean isEventAttribute(String name, Project project) {
    return name.startsWith("(") && name.endsWith(")") && AngularIndexUtil.hasAngularJS2(project);
  }

  public static boolean isVariableAttribute(String name, Project project) {
    return name.startsWith("#") && AngularIndexUtil.hasAngularJS2(project);
  }

  public static boolean isTemplateAttribute(String name, Project project) {
    return name.startsWith("*") && AngularIndexUtil.hasAngularJS2(project);
  }

  public static boolean isBindingAttribute(String name, Project project) {
    return name.startsWith("[") && name.endsWith("]") && AngularIndexUtil.hasAngularJS2(project);
  }
}
