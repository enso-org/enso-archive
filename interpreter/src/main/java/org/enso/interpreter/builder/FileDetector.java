package org.enso.interpreter.builder;

import com.oracle.truffle.api.TruffleFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.enso.interpreter.Constants;

public final class FileDetector implements TruffleFile.FileTypeDetector {

  @Override
  public String findMimeType(TruffleFile file) throws IOException {
    String name = file.getName();
    if (name != null && name.endsWith(Constants.FILE_EXTENSION)) {
      return Constants.MIME_TYPE;
    }
    return null;
  }

  @Override
  public Charset findEncoding(TruffleFile file) throws IOException {
    InputStream fileReader = file.newInputStream();
    EncodingDetector detector = new DefaultEncodingDetector();
    return detector.detect(fileReader, null);
  }
}
