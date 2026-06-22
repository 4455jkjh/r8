// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.errors.InvalidDescriptorException;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class AndroidApiVersionsXmlParser {

  /**
   * Can parse android version XML files.
   *
   * @param xmlPath the XML file to parse.
   */
  public static List<ParsedApiClass> parse(Path xmlPath, ParsingListener listener)
      throws ParsingException {
    if (listener == null) {
      listener = new EmptyParsingListener();
    }
    return new AndroidApiVersionsXmlParser(xmlPath, listener).parse();
  }

  private static final AndroidApiLevel FIRST_API_LEVEL = AndroidApiLevel.B;

  private final Path xmlPath;
  private final ParsingListener listener;
  private final Map<ClassReference, ParsedApiClass> parsedClasses = new LinkedHashMap<>();

  private AndroidApiVersionsXmlParser(Path xmlPath, ParsingListener listener) {
    assert xmlPath != null;
    assert listener != null;
    this.xmlPath = xmlPath;
    this.listener = listener;
  }

  private List<ParsedApiClass> parse() throws ParsingException {
    Document xml = readXml();
    int versionsVersion = parseVersion(xml);
    var classes = xml.getElementsByTagName("class");
    for (int i = 0; i < classes.getLength(); i++) {
      Node node = classes.item(i);
      checkNodeType(node, "<class>", Node.ELEMENT_NODE, "element");
      parseClass(node, versionsVersion);
    }
    return new ArrayList<>(this.parsedClasses.values());
  }

  private void parseClass(Node node, int versionsVersion) throws ParsingException {
    var introApiLevel = parseSinceAttributeWithDefault(node, versionsVersion, FIRST_API_LEVEL);
    ClassReference classReference = Reference.classFromBinaryName(parseNameAttribute(node));
    var removedItem = node.getAttributes().getNamedItem("removed");
    if (removedItem != null) {
      // Removed classes are treated as non-existent.
      listener.skippingRemovedClass(
          classReference, parseAndroidApiLevel(versionsVersion, removedItem.getNodeValue()));
      return;
    }
    listener.startedProcessingClass(classReference);
    var parsedApiClass = register(classReference, introApiLevel);
    var classEntries = node.getChildNodes();
    for (int j = 0; j < classEntries.getLength(); j++) {
      Node classEntry = classEntries.item(j);
      parseClassEntry(classEntry, versionsVersion, parsedApiClass);
    }
  }

  private void parseClassEntry(Node node, int versionsVersion, ParsedApiClass apiClass)
      throws ParsingException {
    switch (node.getNodeName()) {
      case "extends":
        parseExtendsEntry(node, versionsVersion, apiClass);
        break;
      case "implements":
        parseImplementsEntry(node, versionsVersion, apiClass);
        break;
      case "method":
        parseMethodEntry(node, versionsVersion, apiClass);
        break;
      case "field":
        parseFieldEntry(node, versionsVersion, apiClass);
        break;
      case "#text":
      case "#comment":
        // Ignore.
        break;
      default:
        throw new ParsingException("Cannot handle class entry: " + node.getNodeName());
    }
  }

  private void parseExtendsEntry(Node node, int versionsVersion, ParsedApiClass apiClass)
      throws ParsingException {
    // TODO(jonathanlist): Check for "removed".
    ClassReference classReference = Reference.classFromBinaryName(parseNameAttribute(node));
    if (apiClass.hasSupertype(classReference)) {
      throw new ParsingException(
          "Duplicate extends entries for "
              + classReference
              + " in "
              + apiClass.getClassReference());
    }
    AndroidApiLevel since =
        parseSinceAttributeWithDefault(node, versionsVersion, apiClass.getApiLevel());
    apiClass.registerSupertype(classReference, since);
  }

  private void parseImplementsEntry(Node node, int versionsVersion, ParsedApiClass apiClass)
      throws ParsingException {
    // TODO(jonathanlist): Check for "removed".
    ClassReference interfaceReference = Reference.classFromBinaryName(parseNameAttribute(node));
    if (apiClass.hasInterface(interfaceReference)) {
      throw new ParsingException(
          "Duplicate implements entries for "
              + interfaceReference
              + " in "
              + apiClass.getClassReference());
    }
    AndroidApiLevel since =
        parseSinceAttributeWithDefault(node, versionsVersion, apiClass.getApiLevel());
    apiClass.registerInterface(interfaceReference, since);
  }

  private void parseMethodEntry(Node node, int versionsVersion, ParsedApiClass apiClass)
      throws ParsingException {
    // TODO(jonathanlist): Check for "removed".
    MethodReference methodReference =
        parseMethodReference(apiClass.getClassReference(), parseNameAttribute(node));
    if (apiClass.hasMethod(methodReference)) {
      throw new ParsingException("Duplicate entries for " + methodReference);
    }
    AndroidApiLevel since =
        parseSinceAttributeWithDefault(node, versionsVersion, apiClass.getApiLevel());
    apiClass.registerMethod(methodReference, since);
  }

  private void parseFieldEntry(Node node, int versionsVersion, ParsedApiClass apiClass)
      throws ParsingException {
    FieldTypelessReference fieldReference =
        parseFieldReference(apiClass.getClassReference(), parseNameAttribute(node));
    Node removedItem = node.getAttributes().getNamedItem("removed");
    if (removedItem != null) {
      // Removed fields are treated as non-existent.
      listener.skippingRemovedField(
          fieldReference, parseAndroidApiLevel(versionsVersion, removedItem.getNodeValue()));
      return;
    }
    listener.startedProcessingField(fieldReference);
    if (apiClass.hasField(fieldReference)) {
      throw new ParsingException("Duplicate entries for " + fieldReference);
    }
    apiClass.registerField(
        fieldReference,
        parseSinceAttributeWithDefault(node, versionsVersion, apiClass.getApiLevel()));
  }

  private static MethodReference parseMethodReference(ClassReference holder, String methodReference)
      throws ParsingException {
    int signatureStart = methodReference.indexOf('(');
    if (signatureStart <= 0) {
      throw new ParsingException(methodReference + " is not a valid method reference");
    }
    var methodName = methodReference.substring(0, signatureStart);
    try {
      // TODO(jonathanlist): Parts of Reference methods can throw Unreachable, not just here.
      return Reference.methodFromDescriptor(
          holder, methodName, methodReference.substring(signatureStart));
    } catch (InvalidDescriptorException e) {
      throw new ParsingException(methodReference + " is not a valid method reference", e);
    }
  }

  private static FieldTypelessReference parseFieldReference(
      ClassReference holder, String fieldReference) {
    return new FieldTypelessReference(holder, fieldReference);
  }

  private ParsedApiClass register(ClassReference classReference, AndroidApiLevel introApiLevel)
      throws ParsingException {
    if (parsedClasses.containsKey(classReference)) {
      throw new ParsingException("Duplicate class entry found for " + classReference);
    }
    ParsedApiClass apiClass = new ParsedApiClass(classReference, introApiLevel);
    parsedClasses.put(classReference, apiClass);
    return apiClass;
  }

  private static void checkNodeType(Node node, String nodeName, short nodeType, String nodeTypeName)
      throws ParsingException {
    if (node.getNodeType() != nodeType) {
      throw new ParsingException(
          String.format(
              "Expected %s to be %s (%s), not %s (%s)",
              nodeName, nodeTypeName, nodeType, node.getNodeName(), node.getNodeType()));
    }
  }

  private Document readXml() throws ParsingException {
    var factory = DocumentBuilderFactory.newInstance();
    Document xml;
    try {
      xml = factory.newDocumentBuilder().parse(xmlPath.toFile());
    } catch (SAXException | IOException | ParserConfigurationException e) {
      throw new ParsingException("XML parsing failed", e);
    }
    return xml;
  }

  private static int parseVersion(Document xml) throws ParsingException {
    var api = xml.getElementsByTagName("api");
    if (api.getLength() != 1) {
      throw new ParsingException("Expected exactly one <api> element, found " + api.getLength());
    }
    var apiNode = api.item(0);
    checkNodeType(apiNode, "<api>", Node.ELEMENT_NODE, "element");
    if (apiNode.getAttributes().getNamedItem("version") == null) {
      throw new ParsingException("Expected a 'version' attribute");
    }
    String versionString = apiNode.getAttributes().getNamedItem("version").getNodeValue();
    int versionsVersion;
    try {
      versionsVersion = Integer.parseInt(versionString);
    } catch (NumberFormatException e) {
      throw new ParsingException("Version cannot be parsed as an integer", e);
    }
    if (versionsVersion != 3 && versionsVersion != 4) {
      throw new ParsingException("Only version 3 and 4 are supported");
    }
    return versionsVersion;
  }

  private AndroidApiLevel parseSinceAttributeWithDefault(
      Node node, int versionsVersion, AndroidApiLevel defaultValue) throws ParsingException {
    AndroidApiLevel since = parseSinceAttribute(node, versionsVersion);
    if (since == null) {
      return defaultValue;
    } else {
      return since;
    }
  }

  private AndroidApiLevel parseSinceAttribute(Node node, int versionsVersion)
      throws ParsingException {
    Node since = node.getAttributes().getNamedItem("since");
    if (since == null) {
      return null;
    }
    String sinceVersionString = since.getNodeValue();
    return parseAndroidApiLevel(versionsVersion, sinceVersionString);
  }

  private static AndroidApiLevel parseAndroidApiLevel(int versionsVersion, String versionString)
      throws ParsingException {
    assert versionsVersion == 4 || (versionsVersion == 3 && !versionString.contains("."));
    try {
      return AndroidApiLevel.parseAndroidApiLevel(versionString);
    } catch (NumberFormatException e) {
      throw new ParsingException("'since' version cannot be parsed: " + versionString, e);
    }
  }

  private String parseNameAttribute(Node node) throws ParsingException {
    Node nameNode = node.getAttributes().getNamedItem("name");
    if (nameNode == null) {
      throw new ParsingException("Class node is missing a 'name' attribute");
    }
    return nameNode.getNodeValue();
  }

  public interface ParsingListener {
    void startedProcessingClass(ClassReference reference);

    void skippingRemovedClass(ClassReference reference, AndroidApiLevel removedAt);

    void startedProcessingField(FieldTypelessReference reference);

    void skippingRemovedField(FieldTypelessReference reference, AndroidApiLevel removedAt);
  }

  private static class EmptyParsingListener implements ParsingListener {
    @Override
    public void startedProcessingClass(ClassReference reference) {}

    @Override
    public void skippingRemovedClass(ClassReference reference, AndroidApiLevel removedAt) {}

    @Override
    public void startedProcessingField(FieldTypelessReference reference) {}

    @Override
    public void skippingRemovedField(FieldTypelessReference reference, AndroidApiLevel removedAt) {}
  }

  public static class ParsingException extends Exception {
    public ParsingException(String message) {
      super(message);
    }

    public ParsingException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
