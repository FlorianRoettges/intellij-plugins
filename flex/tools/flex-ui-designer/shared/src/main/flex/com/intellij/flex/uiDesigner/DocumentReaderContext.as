package com.intellij.flex.uiDesigner {
public interface DocumentReaderContext {
  function get file():VirtualFile;
  
  function get moduleContext():ModuleContext;
}
}
