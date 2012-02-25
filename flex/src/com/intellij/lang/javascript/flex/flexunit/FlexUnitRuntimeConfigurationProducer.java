package com.intellij.lang.javascript.flex.flexunit;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.flex.FlexModuleType;
import com.intellij.lang.javascript.flex.projectStructure.model.FlexBuildConfigurationManager;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.impl.JSPsiImplUtils;
import com.intellij.lang.javascript.psi.util.JSUtils;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlexUnitRuntimeConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  private PsiElement mySourceElement;

  public FlexUnitRuntimeConfigurationProducer() {
    super(FlexUnitRunConfigurationType.getInstance());
  }

  public PsiElement getSourceElement() {
    return mySourceElement;
  }

  protected RunnerAndConfigurationSettings findExistingByElement(final Location location,
                                                                 @NotNull final RunnerAndConfigurationSettings[] existingConfigurations,
                                                                 final ConfigurationContext context) {
    if (existingConfigurations.length == 0) return null;
    if (!(location instanceof PsiLocation)) return null;
    final Module module = location.getModule();
    if (module == null || ModuleType.get(module) != FlexModuleType.getInstance()) return null;

    PsiElement element = location.getPsiElement();
    element = findTestElement(element);
    if (element == null) return null;

    final FlexUnitRunnerParameters fakeParams = new FlexUnitRunnerParameters();
    if (!configureRunnerParameters(fakeParams, context.getModule(), element)) return null;

    for (final RunnerAndConfigurationSettings configuration : existingConfigurations) {
      final RunConfiguration runConfiguration = configuration.getConfiguration();
      final FlexUnitRunnerParameters params = ((FlexUnitRunConfiguration)runConfiguration).getRunnerParameters();
      if (params.getModuleName().equals(fakeParams.getModuleName())
          && params.getScope() == fakeParams.getScope()
          && (params.getScope() != FlexUnitRunnerParameters.Scope.Package || params.getPackageName().equals(fakeParams.getPackageName()))
          && (params.getScope() == FlexUnitRunnerParameters.Scope.Package || params.getClassName().equals(fakeParams.getClassName()))
          && (params.getScope() != FlexUnitRunnerParameters.Scope.Method || params.getMethodName().equals(fakeParams.getMethodName()))) {
        return configuration;
      }
    }
    return null;
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    if (!(location instanceof PsiLocation)) return null;
    final Module module = location.getModule();
    if (module == null || ModuleType.get(module) != FlexModuleType.getInstance()) return null;

    PsiElement element = location.getPsiElement();
    element = findTestElement(element);
    if (element == null) return null;

    final RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(location.getProject(), context);
    final LocatableConfiguration runConfig = (LocatableConfiguration)settings.getConfiguration();
    final FlexUnitRunnerParameters params = ((FlexUnitRunConfiguration)runConfig).getRunnerParameters();
    if (!configureRunnerParameters(params, context.getModule(), element)) return null;

    // todo need better logic to select BC
    params.setBCName(FlexBuildConfigurationManager.getInstance(context.getModule()).getActiveConfiguration().getName());

    mySourceElement = location.getPsiElement();
    settings.setName(runConfig.suggestedName());
    return settings;
  }

  // todo only some of BCs for current module may have FlexUnit library
  private static boolean configureRunnerParameters(final FlexUnitRunnerParameters params, final Module module, final PsiElement element) {
    if (element instanceof JSClass) {
      final JSClass clazz = (JSClass)element;

      Pair<Module, FlexUnitSupport> supportForModule = FlexUnitSupport.getModuleAndSupport(clazz);
      if (supportForModule == null || !supportForModule.second.isTestClass(clazz, true)) return false;

      forClass(clazz, supportForModule.first, params);
    }
    else if (element instanceof JSFunction) {
      JSFunction method = (JSFunction)element;
      final JSClass clazz = (JSClass)element.getParent();

      Pair<Module, FlexUnitSupport> supportForModule = FlexUnitSupport.getModuleAndSupport(clazz);
      if (supportForModule == null || !supportForModule.second.isTestClass(clazz, true)) return false;

      if (!supportForModule.second.isTestMethod(method)) {
        forClass(clazz, supportForModule.first, params);
      }
      else {
        params.setClassName(clazz.getQualifiedName());
        params.setMethodName(method.getName());
        params.setScope(FlexUnitRunnerParameters.Scope.Method);
        params.setModuleName(supportForModule.first.getName());
      }
    }
    else if (element instanceof PsiDirectory) {
      if (!forDirectory((PsiDirectory)element, module, params)) return false;
    }
    else if (element instanceof PsiDirectoryContainer) {
      if (!forPackage((PsiDirectoryContainer)element, module, params)) return false;
    }
    else {
      return false;
    }

    return true;
  }

  private static boolean forPackage(PsiDirectoryContainer psiPackage, Module module, FlexUnitRunnerParameters params) {
    if (module == null) return false;
    for (PsiDirectory directory : psiPackage.getDirectories(module.getModuleScope())) {
      if (forDirectory(directory, module, params)) {
        return true;
      }
    }
    return false;
  }

  private static boolean forDirectory(PsiDirectory directory, Module module, FlexUnitRunnerParameters params) {
    final VirtualFile file = directory.getVirtualFile();
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(directory.getProject()).getFileIndex();
    VirtualFile rootForFile = projectFileIndex.getSourceRootForFile(file);
    if (rootForFile == null) return false;

    String packageName = VfsUtilCore.getRelativePath(file, rootForFile, '.');
    if (!JSUtils.packageExists(packageName, GlobalSearchScope.moduleScope(module))) return false;

    params.setPackageName(packageName);
    params.setScope(FlexUnitRunnerParameters.Scope.Package);
    params.setModuleName(module.getName());
    return true;
  }

  private static void forClass(JSClass clazz, Module module, FlexUnitRunnerParameters params) {
    params.setClassName(clazz.getQualifiedName());
    params.setScope(FlexUnitRunnerParameters.Scope.Class);
    params.setModuleName(module.getName());
  }

  @Nullable
  private static PsiElement findTestElement(PsiElement element) {
    if (element.getLanguage().isKindOf(JavaScriptSupportLoader.JAVASCRIPT.getLanguage())) {
      final PsiNamedElement parent = PsiTreeUtil.getNonStrictParentOfType(element, JSFunction.class, JSClass.class, JSFile.class);
      if (parent instanceof JSClass) {
        return parent;
      }
      if (parent instanceof JSFunction && parent.getParent() instanceof JSClass) {
        return parent;
      }
      if (parent instanceof JSFile) {
        return JSPsiImplUtils.findClass((JSFile)parent);
      }
    }
    else if (element instanceof PsiDirectory) {
      return element;
    }
    else if (element instanceof PsiDirectoryContainer) {
      return element;
    }

    return null;
  }

  public int compareTo(Object o) {
    return PREFERED;
  }
}
