package org.enso.interpreter.runtime;

import com.oracle.truffle.api.TruffleLanguage;
import org.enso.interpreter.Constants;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Class representing runtime options supported by Enso engine. */
public class RuntimeOptions {
  private static String optionName(String name) {
    return Constants.LANGUAGE_ID + "." + name;
  }

  public static final String PACKAGES_PATH = optionName("packagesPath");
  private static final OptionKey<String> PACKAGES_PATH_KEY = new OptionKey<>("");
  private static final OptionDescriptor PACKAGES_PATH_DESCRIPTOR =
      OptionDescriptor.newBuilder(PACKAGES_PATH_KEY, PACKAGES_PATH).build();

  public static final OptionDescriptors OPTION_DESCRIPTORS =
      OptionDescriptors.create(Arrays.asList(PACKAGES_PATH_DESCRIPTOR));

  public static List<File> getPackagesPaths(TruffleLanguage.Env env) {
    return Arrays.stream(env.getOptions().get(PACKAGES_PATH_KEY).split(env.getPathSeparator()))
        .map(File::new)
        .collect(Collectors.toList());
  }
}
