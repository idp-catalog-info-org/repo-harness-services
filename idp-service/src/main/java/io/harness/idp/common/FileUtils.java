/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class FileUtils {
  private static final String ZIP_EXTENSION = "zip";
  private static final String TAR_GZ_EXTENSION = "tar.gz";
  private static final String TGZ_EXTENSION = "tgz";
  private static final String TAR_BZ2_EXTENSION = "tar.bz2";
  private static final String JPEG_EXTENSION = "jpeg";
  private static final String JPG_EXTENSION = "jpg";
  private static final String PNG_EXTENSION = "png";
  private static final String SVG_EXTENSION = "svg";
  private static final List<String> SUPPORTED_PLUGIN_FILE_FORMATS =
      Arrays.asList(ZIP_EXTENSION, TAR_GZ_EXTENSION, TAR_BZ2_EXTENSION, TGZ_EXTENSION);
  private static final List<String> SUPPORTED_IMAGE_FILE_FORMATS =
      Arrays.asList(JPEG_EXTENSION, JPG_EXTENSION, PNG_EXTENSION, SVG_EXTENSION);
  public static final String PATH_SEPARATOR = "/";

  public static String readFile(String dir, String fileName) {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    String file = dir + fileName;
    try (InputStream inputStream = classLoader.getResourceAsStream(file)) {
      if (inputStream == null) {
        return null;
      }
      return IOUtils.toString(inputStream, UTF_8);
    } catch (IOException e) {
      String errMessage = "Error occurred while reading file: " + file;
      log.error(errMessage, e);
      throw new InvalidRequestException(errMessage);
    }
  }

  public static Set<String> readDirectory(Class clazz, String path) {
    Set<String> result = new HashSet<>();
    URL dirURL = clazz.getClassLoader().getResource(path);
    if (dirURL != null && dirURL.getProtocol().equals("jar")) {
      String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!"));
      try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, UTF_8))) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
          String name = entries.nextElement().getName();
          if (name.startsWith(path)) {
            String entry = name.substring(path.length());
            int checkSubdir = entry.indexOf("/");
            if (checkSubdir >= 0) {
              entry = entry.substring(0, checkSubdir);
            }
            if (StringUtils.isNotBlank(entry)) {
              result.add(entry);
            }
          }
        }
      } catch (Exception e) {
        log.error("Error reading dir {}", path, e);
      }
    }
    return result;
  }

  public static boolean isFileFormatSupported(String fileType, String extension) {
    switch (FileType.valueOf(fileType)) {
      case ZIP:
        return SUPPORTED_PLUGIN_FILE_FORMATS.contains(extension);
      case ICON:
      case SCREENSHOT:
        return SUPPORTED_IMAGE_FILE_FORMATS.contains(extension);
      default:
        throw new UnsupportedOperationException("File type " + fileType + " is not supported");
    }
  }
}
