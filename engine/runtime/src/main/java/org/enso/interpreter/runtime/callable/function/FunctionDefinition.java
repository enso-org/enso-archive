package org.enso.interpreter.runtime.callable.function;

import java.io.File;

/**
 * Holds the information about the function definition.
 */
public class FunctionDefinition {

  private final String name;
  private final String definedOnType;
  private final File file;

  public FunctionDefinition(String name, String definedOnType, File file) {
    this.name = name;
    this.definedOnType = definedOnType;
    this.file = file;
  }

  public FunctionDefinition(String name, String definedOnType, String path) {
    this(name, definedOnType, new File(path));
  }

  /**
   * Return the function name.
   *
   * @return the function name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns a type the function was defined on.
   *
   * @return a type the function was defined on.
   */
  public String getDefinedOnType() {
    return definedOnType;
  }

  /**
   * Returns a source file.
   *
   * @return a source file.
   */
  public File getFile() {
    return file;
  }
}
