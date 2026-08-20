// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parser for API and Jar amendments files (*.api.txt and *.jar.txt). This format is only written
 * and read internally so it is free to change.
 *
 * <p>Empty lines and lines starting with {@code //} or {@code #} are ignored.
 *
 * <p><b>API Amendments format (*.api.txt):</b>
 *
 * <ul>
 *   <li>{@code class <class-descriptor> <api-level>}
 *   <li>{@code method <holder-descriptor> <method-name> <method-descriptor> <api-level>}
 *   <li>{@code field <holder-descriptor> <field-name> <api-level>}
 * </ul>
 *
 * <p><b>Jar Amendments format (*.jar.txt):</b>
 *
 * <ul>
 *   <li>{@code class <class-descriptor> extends <super-descriptor> implements <interfaces|none>}
 *   <li>{@code interface <interface-descriptor> extends <interfaces|none>}
 *   <li>{@code method <holder-descriptor> <method-name> <method-descriptor> <static|instance>}
 *   <li>{@code field <holder-descriptor> <field-name>}
 * </ul>
 *
 * <p>Multiple interfaces are comma-separated without spaces (e.g. {@code
 * Ljava/lang/Cloneable;,Ljava/io/Serializable;}) or {@code none} if there are no interfaces.
 */
public class ApiAmendmentsParser {

  public static ApiAmendments parseApiAmendments(InputStream inputStream) throws IOException {
    ApiAmendments amendments = new ApiAmendments();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        String[] parts = splitLine(line);
        if (parts == null) {
          continue;
        }
        parseApiLine(parts[0], parts, amendments, lineNumber);
      }
    }
    return amendments;
  }

  private static void parseApiLine(
      String type, String[] parts, ApiAmendments amendments, int lineNumber) {
    switch (type) {
      case "class":
        parseClassAmendment(parts, amendments, lineNumber);
        break;
      case "method":
        parseMethodAmendment(parts, amendments, lineNumber);
        break;
      case "field":
        parseFieldAmendment(parts, amendments, lineNumber);
        break;
      default:
        throw new RuntimeException("Unknown type " + type + " at line " + lineNumber);
    }
  }

  private static void parseClassAmendment(
      String[] parts, ApiAmendments amendments, int lineNumber) {
    if (parts.length != 3) {
      throw new RuntimeException("Invalid class amendment at line " + lineNumber);
    }
    ClassReference classReference = Reference.classFromDescriptor(parts[1]);
    AndroidApiLevel apiLevel = AndroidApiLevel.parseAndroidApiLevel(parts[2]);
    amendments.addClass(classReference, apiLevel);
  }

  private static void parseMethodAmendment(
      String[] parts, ApiAmendments amendments, int lineNumber) {
    if (parts.length != 5) {
      throw new RuntimeException("Invalid method amendment at line " + lineNumber);
    }
    ClassReference holder = Reference.classFromDescriptor(parts[1]);
    String name = parts[2];
    String descriptor = parts[3];
    AndroidApiLevel apiLevel = AndroidApiLevel.parseAndroidApiLevel(parts[4]);
    amendments.addMethod(Reference.methodFromDescriptor(holder, name, descriptor), apiLevel);
  }

  private static void parseFieldAmendment(
      String[] parts, ApiAmendments amendments, int lineNumber) {
    if (parts.length != 4) {
      throw new RuntimeException("Invalid field amendment at line " + lineNumber);
    }
    ClassReference holder = Reference.classFromDescriptor(parts[1]);
    String name = parts[2];
    AndroidApiLevel apiLevel = AndroidApiLevel.parseAndroidApiLevel(parts[3]);
    amendments.addField(new FieldTypelessReference(holder, name), apiLevel);
  }

  public static JarAmendments parseJarAmendments(InputStream inputStream) throws IOException {
    JarAmendments amendments = new JarAmendments();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        String[] parts = splitLine(line);
        if (parts == null) {
          continue;
        }
        parseJarLine(parts[0], parts, amendments, lineNumber);
      }
    }
    return amendments;
  }

  private static void parseJarLine(
      String type, String[] parts, JarAmendments amendments, int lineNumber) {
    switch (type) {
      case "class":
        parseJarClassAmendment(parts, amendments, lineNumber);
        break;
      case "interface":
        parseJarInterfaceAmendment(parts, amendments, lineNumber);
        break;
      case "method":
        parseJarMethodAmendment(parts, amendments, lineNumber);
        break;
      case "field":
        parseJarFieldAmendment(parts, amendments, lineNumber);
        break;
      default:
        throw new RuntimeException("Unknown type " + type + " at line " + lineNumber);
    }
  }

  private static String[] splitLine(String line) {
    line = line.trim();
    if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) {
      return null;
    } else {
      String[] split = line.split(" ");
      assert split.length >= 1;
      return split;
    }
  }

  private static void parseJarClassAmendment(
      String[] parts, JarAmendments amendments, int lineNumber) {
    if (parts.length != 6) {
      throw new RuntimeException("Invalid jar class amendment at line " + lineNumber);
    }
    ClassReference classReference = Reference.classFromDescriptor(parts[1]);
    if (!parts[2].equals("extends")) {
      throw new RuntimeException("Expected 'extends' as third word at line " + lineNumber);
    }
    ClassReference superClass = Reference.classFromDescriptor(parts[3]);
    if (!parts[4].equals("implements")) {
      throw new RuntimeException("Expected 'implements' as fifth word at line " + lineNumber);
    }
    List<ClassReference> interfaces = parseInterfaces(parts[5]);
    amendments.addClass(classReference, superClass, interfaces);
  }

  private static void parseJarInterfaceAmendment(
      String[] parts, JarAmendments amendments, int lineNumber) {
    if (parts.length != 4) {
      throw new RuntimeException("Invalid jar interface amendment at line " + lineNumber);
    }
    ClassReference interfaceReference = Reference.classFromDescriptor(parts[1]);
    if (!parts[2].equals("extends")) {
      throw new RuntimeException("Expected 'extends' as third word at line " + lineNumber);
    }
    List<ClassReference> interfaces = parseInterfaces(parts[3]);
    amendments.addInterface(interfaceReference, interfaces);
  }

  private static List<ClassReference> parseInterfaces(String interfacesStr) {
    if (interfacesStr.equals("none")) {
      return ImmutableList.of();
    }
    return Arrays.stream(interfacesStr.split(","))
        .map(Reference::classFromDescriptor)
        .collect(Collectors.toList());
  }

  private static void parseJarMethodAmendment(
      String[] parts, JarAmendments amendments, int lineNumber) {
    if (parts.length != 5) {
      throw new RuntimeException("Invalid jar method amendment at line " + lineNumber);
    }
    ClassReference holder = Reference.classFromDescriptor(parts[1]);
    String name = parts[2];
    String descriptor = parts[3];
    String relation = parts[4];
    boolean isStatic;
    if (relation.equals("static")) {
      isStatic = true;
    } else if (relation.equals("instance")) {
      isStatic = false;
    } else {
      throw new RuntimeException(
          "Expected 'static' or 'instance' as fifth word at line " + lineNumber);
    }
    amendments.addMethod(holder, name, descriptor, isStatic);
  }

  private static void parseJarFieldAmendment(
      String[] parts, JarAmendments amendments, int lineNumber) {
    if (parts.length != 3) {
      throw new RuntimeException("Invalid jar field amendment at line " + lineNumber);
    }
    ClassReference holder = Reference.classFromDescriptor(parts[1]);
    String name = parts[2];
    amendments.addField(holder, name);
  }
}
