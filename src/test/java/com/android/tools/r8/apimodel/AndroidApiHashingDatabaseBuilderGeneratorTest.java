// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.UnorderedCollectionMatcher.matchesItemsOneToOne;
import static com.android.tools.r8.androidapi.AndroidApiLevelDatabaseHelper.notModeledTypes;
import static com.android.tools.r8.androidapi.AndroidApiLevelDatabaseTestHelper.notModeledFields;
import static com.android.tools.r8.androidapi.AndroidApiLevelDatabaseTestHelper.notModeledMethods;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.AndroidApiLevelCompute.DefaultAndroidApiLevelCompute;
import com.android.tools.r8.androidapi.AndroidApiLevelDatabaseTestHelper;
import com.android.tools.r8.androidapi.AndroidApiLevelHashingDatabaseImpl;
import com.android.tools.r8.androidapi.ApiDatabaseEntry;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.androidapi.GenerateCovariantReturnTypeMethodsTest.CovariantMethodsInJarResult;
import com.android.tools.r8.androidapi.SunMiscUnsafeApiTest;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.IntBox;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidApiHashingDatabaseBuilderGeneratorTest extends TestBase {

  protected final TestParameters parameters;
  private static final Path API_DATABASE_FOLDER =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "api_database");
  private static final Path API_DATABASE =
      API_DATABASE_FOLDER.resolve("api_database").resolve("resources").resolve("api_database.ser");

  // Update the API_LEVEL below to have the database generated for a new api level.
  private static final AndroidApiLevel API_LEVEL = AndroidApiLevel.API_DATABASE_LEVEL;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public AndroidApiHashingDatabaseBuilderGeneratorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static class GenerateDatabaseResourceFilesResult {

    private final Path apiLevels;

    public GenerateDatabaseResourceFilesResult(Path apiLevels) {
      this.apiLevels = apiLevels;
    }
  }

  private static GenerateDatabaseResourceFilesResult generateResourcesFiles(
      Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries) throws Exception {
    TemporaryFolder temp1 = new TemporaryFolder();
    temp1.create();
    Path apiLevels = temp1.newFile("api_levels.ser").toPath();
    AndroidApiHashingDatabaseBuilderGenerator.writeEntries(databaseEntries, apiLevels);
    return new GenerateDatabaseResourceFilesResult(apiLevels);
  }

  private static Map<ApiDatabaseEntry, AndroidApiLevel> loadDatabaseEntries() throws Exception {
    Path androidJar = ToolHelper.getAndroidJar(API_LEVEL);
    List<ParsedApiClass> apiClasses =
        AndroidApiVersionsXmlParserChecked.parse(
            ToolHelper.getApiVersionsXmlFile(API_LEVEL), androidJar, API_LEVEL, false);
    DexItemFactory factory = new DexItemFactory();
    addCovariantMethods(apiClasses, factory);
    addAdditionalKnownApiReferences(apiClasses, factory);
    Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries =
        AndroidApiHashingDatabaseBuilderGenerator.generateEntries(apiClasses);
    verifyAgainstJar(apiClasses, databaseEntries, API_LEVEL);
    return databaseEntries;
  }

  private static void verifyAgainstJar(
      List<ParsedApiClass> apiClasses,
      Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries,
      AndroidApiLevel androidJarApiLevel)
      throws Exception {
    Path androidJar = ToolHelper.getAndroidJar(androidJarApiLevel);
    AndroidApp androidApp =
        AndroidApp.builder()
            .addLibraryFile(androidJar)
            .disableAndroidJarHiddenClassExtension()
            .build();
    AppView<AppInfoWithClassHierarchy> appView =
        computeAppViewWithClassHierarchy(androidApp, Timing.empty());

    ensureAllPublicMethodsAreMapped(
        appView, apiClasses, databaseEntries, androidJarApiLevel, androidJar);
  }

  private static void ensureAllPublicMethodsAreMapped(
      AppView<AppInfoWithClassHierarchy> appView,
      List<ParsedApiClass> apiClasses,
      Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries,
      AndroidApiLevel apiLevel,
      Path androidJar) {
    Map<ClassReference, ParsedApiClass> lookupMap = new HashMap<>();
    Map<ClassReference, Map<DexMethod, AndroidApiLevel>> methodMap = new HashMap<>();
    Map<ClassReference, Map<FieldTypelessReference, AndroidApiLevel>> fieldMap = new HashMap<>();
    DexItemFactory factory = appView.dexItemFactory();

    for (ParsedApiClass apiClass : apiClasses) {
      lookupMap.put(apiClass.getClassReference(), apiClass);
      Map<DexMethod, AndroidApiLevel> methodsForApiClass = new HashMap<>();
      apiClass.forEachMethod(
          (method, lvl) -> methodsForApiClass.put(factory.createMethod(method), lvl));
      methodMap.put(apiClass.getClassReference(), methodsForApiClass);

      Map<FieldTypelessReference, AndroidApiLevel> fieldsForApiClass = new HashMap<>();
      apiClass.forEachField(fieldsForApiClass::put);
      fieldMap.put(apiClass.getClassReference(), fieldsForApiClass);
    }

    Map<DexType, String> missingMemberInformation = new IdentityHashMap<>();
    for (DexLibraryClass clazz : appView.app().asDirect().libraryClasses()) {
      ParsedApiClass parsedApiClass = lookupMap.get(clazz.getClassReference());
      if (parsedApiClass == null) {
        if (clazz.isPublic()) {
          missingMemberInformation.put(clazz.getType(), "Could not be found in " + androidJar);
        }
        continue;
      }
      StringBuilder classBuilder = new StringBuilder();
      Map<FieldTypelessReference, AndroidApiLevel> fieldMapForClass =
          fieldMap.get(clazz.getClassReference());
      assert fieldMapForClass != null;
      clazz.forEachClassField(
          field -> {
            if (field.getAccessFlags().isPublic()
                && databaseEntries.get(ApiDatabaseEntry.of(field.getReference())) == null
                && !field.toSourceString().contains("this$0")) {
              classBuilder.append("  ").append(field).append(" is missing\n");
            }
          });
      Map<DexMethod, AndroidApiLevel> methodMapForClass = methodMap.get(clazz.getClassReference());
      assert methodMapForClass != null;
      clazz.forEachClassMethod(
          method -> {
            if (method.getAccessFlags().isPublic()
                && databaseEntries.get(ApiDatabaseEntry.of(method.getReference())) == null
                && !factory.objectMembers.isObjectMember(method.getReference())) {
              classBuilder.append("  ").append(method).append(" is missing\n");
            }
          });
      if (classBuilder.length() > 0) {
        missingMemberInformation.put(clazz.getType(), classBuilder.toString());
      }
    }

    Set<DexType> expectedMissingMembers = new HashSet<>();
    if (apiLevel.isLessThan(AndroidApiLevel.BAKLAVA_1)) {
      expectedMissingMembers.add(
          factory.createType("Landroid/adservices/adselection/AdSelectionOutcome;"));
      expectedMissingMembers.add(
          factory.createType("Landroid/adservices/adselection/ReportEventRequest;"));
      expectedMissingMembers.add(
          factory.createType(
              "Landroid/adservices/ondevicepersonalization/FederatedComputeScheduleRequest;"));
      expectedMissingMembers.add(
          factory.createType(
              "Landroid/adservices/ondevicepersonalization/FederatedComputeScheduleResponse;"));
      expectedMissingMembers.add(
          factory.createType(
              "Landroid/adservices/ondevicepersonalization/FederatedComputeScheduler;"));
      expectedMissingMembers.add(
          factory.createType("Landroid/adservices/ondevicepersonalization/InferenceInput;"));
      expectedMissingMembers.add(
          factory.createType(
              "Landroid/adservices/ondevicepersonalization/InferenceInput$Builder;"));
      expectedMissingMembers.add(
          factory.createType("Landroid/adservices/ondevicepersonalization/InferenceInput$Params;"));
      expectedMissingMembers.add(
          factory.createType("Landroid/adservices/ondevicepersonalization/InferenceOutput;"));
      expectedMissingMembers.add(
          factory.createType(
              "Landroid/adservices/ondevicepersonalization/InferenceOutput$Builder;"));
      expectedMissingMembers.add(
          factory.createType(
              "Landroid/adservices/ondevicepersonalization/OnDevicePersonalizationManager;"));
    }
    assertThat(missingMemberInformation.keySet(), matchesItemsOneToOne(expectedMissingMembers));
  }

  private static void addCovariantMethods(List<ParsedApiClass> apiClasses, DexItemFactory factory)
      throws Exception {
    CovariantMethodsInJarResult covariantMethodsInJar = CovariantMethodsInJarResult.create();
    for (ParsedApiClass apiClass : apiClasses) {
      Map<DexMethod, AndroidApiLevel> methodsForApiClass = new HashMap<>();
      apiClass.forEachMethod(
          (method, apiLevel) -> methodsForApiClass.put(factory.createMethod(method), apiLevel));
      covariantMethodsInJar.visitCovariantMethodsForHolder(
          apiClass.getClassReference(),
          methodReferenceWithApiLevel -> {
            DexMethod method =
                factory.createMethod(methodReferenceWithApiLevel.getMethodReference());
            if (!methodsForApiClass.containsKey(method)) {
              apiClass.registerMethod(
                  methodReferenceWithApiLevel.getMethodReference(),
                  methodReferenceWithApiLevel.getApiLevel());
              methodsForApiClass.put(method, methodReferenceWithApiLevel.getApiLevel());
            }
          });
    }
  }

  private static void addAdditionalKnownApiReferences(
      List<ParsedApiClass> apiClasses, DexItemFactory factory) {
    Map<ClassReference, ParsedApiClass> lookupMap = new HashMap<>();
    for (ParsedApiClass apiClass : apiClasses) {
      lookupMap.put(apiClass.getClassReference(), apiClass);
    }
    AndroidApiLevelDatabaseTestHelper.visitAdditionalKnownApiReferences(
        factory,
        (reference, apiLevel) -> {
          if (reference.isDexType()) {
            ClassReference classRef = reference.asDexType().asClassReference();
            assert !lookupMap.containsKey(classRef) : classRef + " is already registered";
            ParsedApiClass apiClass = new ParsedApiClass(classRef, apiLevel);
            lookupMap.put(classRef, apiClass);
            apiClasses.add(apiClass);
          } else if (reference.isDexMethod()) {
            MethodReference methodRef = reference.asDexMethod().asMethodReference();
            ClassReference holderRef = methodRef.getHolderClass();
            ParsedApiClass apiClass = lookupMap.get(holderRef);
            if (apiClass == null) {
              apiClass = new ParsedApiClass(holderRef, apiLevel);
              lookupMap.put(holderRef, apiClass);
              apiClasses.add(apiClass);
            }
            apiClass.registerMethod(methodRef, apiLevel);
          } else if (reference.isDexField()) {
            ClassReference holderRef = reference.asDexField().getHolderType().asClassReference();
            FieldTypelessReference fieldRef =
                new FieldTypelessReference(holderRef, reference.asDexField().getName().toString());
            ParsedApiClass apiClass = lookupMap.get(holderRef);
            if (apiClass == null) {
              apiClass = new ParsedApiClass(holderRef, apiLevel);
              lookupMap.put(holderRef, apiClass);
              apiClasses.add(apiClass);
            }
            apiClass.registerField(fieldRef, apiLevel);
          }
        });
  }

  @Test
  public void testParsedApiVersionsXmlSize() throws Exception {
    // This tests makes a rudimentary check on the number of classes, fields and methods in
    // api-versions.xml to ensure that the runtime tests do not vacuously succeed.
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParserChecked.parse(
            ToolHelper.getApiVersionsXmlFile(API_LEVEL),
            ToolHelper.getAndroidJar(API_LEVEL),
            API_LEVEL,
            false);
    IntBox numberOfFields = new IntBox(0);
    IntBox numberOfMethods = new IntBox(0);
    parsedApiClasses.forEach(
        apiClass -> {
          numberOfFields.increment(apiClass.fieldCount());
          numberOfMethods.increment(apiClass.methodCount());
        });
    // These numbers will change when updating api-versions.xml.
    assertEquals(6_498, parsedApiClasses.size());
    assertEquals(32_818, numberOfFields.get());
    assertEquals(49_867, numberOfMethods.get());
  }

  @Test
  public void testEntrySize() throws Exception {
    Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries = loadDatabaseEntries();
    assertEquals(252_214, databaseEntries.size());
  }

  private static final String sampleVersion4ApiVersionsXml =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<api version=\"4\">\n"
          + "        <sdk id=\"36\" shortname=\"B-ext\" name=\"Baklava Extensions\"\n"
          + "             reference=\"android/os/Build$VERSION_CODES$BAKLAVA\"/>\n"
          + "\n"
          + "        <!-- This class was introduced in Android R -->\n"
          + "        <class name=\"android/os/ext/SdkExtensions\" since=\"30.0\">\n"
          + "                <extends name=\"java/lang/Object\"/>\n"
          + "                <!-- This method was introduced in Android S. It was \"backported\""
          + " to Android R via the R extension,\n"
          + "                     version 2. It also exists in later extensions, including the"
          + " Baklava extension (id 36). -->\n"
          + "                <method name=\"getAllExtensionVersions()Ljava/util/Map;\""
          + " since=\"31.0\"\n"
          + "                        sdks=\"30:2,31:2,33:4,34:7,35:12,36:16,0:31.0\"/>\n"
          + "                <method name=\"getExtensionVersion(I)I\"/>\n"
          + "                <!-- This field was introduced in Android U. It was \"backported\""
          + " to Android R via the R extension,\n"
          + "                     version 4. It also exists in later extensions, including the"
          + " Baklava extension (id 36). -->\n"
          + "                <field name=\"AD_SERVICES\" since=\"34.0\""
          + " sdks=\"30:4,31:4,33:4,34:7,35:12,36:16,0:34.0\"/>\n"
          + "        </class>\n"
          + "\n"
          + "        <!-- This class was introduced in Baklava. It does not exist in any SDK"
          + " extension. -->\n"
          + "        <class name=\"android/os/FromBaklava\" since=\"36.0\">\n"
          + "                <extends name=\"java/lang/Object\"/>\n"
          + "                <method name=\"foo(I)V\" />\n"
          + "        </class>\n"
          + "        <class name=\"android/os/AlsoFromBaklava\" since=\"36\">\n"
          + "                <extends name=\"java/lang/Object\"/>\n"
          + "                <method name=\"foo(I)V\" />\n"
          + "        </class>\n"
          + "        <class name=\"android/os/FromBaklava1\" since=\"36.1\">\n"
          + "                <extends name=\"java/lang/Object\"/>\n"
          + "                <method name=\"foo(I)V\" />\n"
          + "        </class>\n"
          + "</api>\n";

  static class SdkExtensionsStub {
    @SuppressWarnings("unused")
    int AD_SERVICES;
  }

  static class TemplateClass {}

  private static void mockAndroidJarForSampleVersion4ApiVersionsXml(Path outputPath)
      throws Exception {
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outputPath))) {
      ZipUtils.writeToZipStream(
          out,
          "android/os/ext/SdkExtensions.class",
          transformer(SdkExtensionsStub.class)
              .setClassDescriptor("Landroid/os/ext/SdkExtensions;")
              .transform(),
          ZipEntry.STORED);
      ZipUtils.writeToZipStream(
          out,
          "android/os/FromBaklava.class",
          transformer(TemplateClass.class)
              .setClassDescriptor("Landroid/os/FromBaklava;")
              .transform(),
          ZipEntry.STORED);
      ZipUtils.writeToZipStream(
          out,
          "android/os/AlsoFromBaklava.class",
          transformer(TemplateClass.class)
              .setClassDescriptor("Landroid/os/AlsoFromBaklava;")
              .transform(),
          ZipEntry.STORED);
      ZipUtils.writeToZipStream(
          out,
          "android/os/FromBaklava1.class",
          transformer(TemplateClass.class)
              .setClassDescriptor("Landroid/os/FromBaklava1;")
              .transform(),
          ZipEntry.STORED);
    }
  }

  @Test
  public void testApiVersionsXmlVersion4() throws Exception {
    Path apiVersionsXml = temp.newFile("api-versions.xml").toPath();
    FileUtils.writeTextFile(apiVersionsXml, sampleVersion4ApiVersionsXml);
    Path apiLibrary = temp.newFile("android.jar").toPath();
    mockAndroidJarForSampleVersion4ApiVersionsXml(apiLibrary);
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParserChecked.parse(apiVersionsXml, apiLibrary, API_LEVEL, true);
    assertEquals(4, parsedApiClasses.size());
    ParsedApiClass sdkExtension = parsedApiClasses.get(0);
    assertEquals(
        sdkExtension.getClassReference(),
        Reference.classFromDescriptor("Landroid/os/ext/SdkExtensions;"));
    assertEquals(AndroidApiLevel.R, sdkExtension.getApiLevel());
    sdkExtension.forEachMethod(
        (method, apiLevel) -> {
          if (apiLevel.equals(AndroidApiLevel.R)) {
            assertEquals(
                method,
                Reference.methodFromDescriptor(
                    "Landroid/os/ext/SdkExtensions;", "getExtensionVersion", "(I)I"));
          } else if (apiLevel.equals(AndroidApiLevel.S)) {
            assertEquals(
                method,
                Reference.methodFromDescriptor(
                    "Landroid/os/ext/SdkExtensions;",
                    "getAllExtensionVersions",
                    "()Ljava/util/Map;"));
          } else {
            fail();
          }
        });
    sdkExtension.forEachField(
        (field, apiLevel) -> {
          if (apiLevel.equals(AndroidApiLevel.U)) {
            assertEquals(
                field,
                new FieldTypelessReference(
                    Reference.classFromDescriptor("Landroid/os/ext/SdkExtensions;"),
                    "AD_SERVICES"));
          } else {
            fail();
          }
        });
    checkMockClass(parsedApiClasses.get(1), "Landroid/os/FromBaklava;", AndroidApiLevel.BAKLAVA);
    checkMockClass(
        parsedApiClasses.get(2), "Landroid/os/AlsoFromBaklava;", AndroidApiLevel.BAKLAVA);
    checkMockClass(parsedApiClasses.get(3), "Landroid/os/FromBaklava1;", AndroidApiLevel.BAKLAVA_1);
  }

  private static void checkMockClass(
      ParsedApiClass apiClass, String descriptor, AndroidApiLevel apiLevel) {
    assertEquals(apiClass.getClassReference(), Reference.classFromDescriptor(descriptor));
    assertEquals(apiLevel, apiClass.getApiLevel());
    apiClass.forEachMethod(
        (method, level) -> {
          if (level.equals(apiLevel)) {
            assertEquals(method, Reference.methodFromDescriptor(descriptor, "foo", "(I)V"));
          } else {
            fail();
          }
        });
    assertEquals(1, apiClass.fieldCount() + apiClass.methodCount());
  }

  @Test
  public void testDatabaseGenerationUpToDate() throws Exception {
    GenerateDatabaseResourceFilesResult result = generateResourcesFiles(loadDatabaseEntries());
    assertTrue(TestBase.filesAreEqual(result.apiLevels, API_DATABASE));
  }

  @Test
  public void testAmendedClassesToApiDatabase() throws Exception {
    Path androidJar = ToolHelper.getAndroidJar(API_LEVEL);
    AppView<AppInfoWithClassHierarchy> appView =
        computeAppViewWithClassHierarchy(
            AndroidApp.builder().addLibraryFile(androidJar).build(), Timing.empty());
    AndroidApiLevelCompute androidApiLevelCompute = DefaultAndroidApiLevelCompute.create(appView);
    assertTrue(androidApiLevelCompute.isEnabled());
    ensureAllPublicMethodsAreMapped(appView, androidApiLevelCompute);
  }

  private static class ApiTruthLookup {
    private final Map<ClassReference, AndroidApiLevel> classApiMap;
    private final Set<ClassReference> queriedClasses = new HashSet<>();
    private final Map<FieldReference, AndroidApiLevel> fieldApiMap;
    private final Set<FieldReference> queriedFields = new HashSet<>();
    private final Map<MethodReference, AndroidApiLevel> methodApiMap;
    private final Set<MethodReference> queriedMethods = new HashSet<>();

    private ApiTruthLookup(
        Map<ClassReference, AndroidApiLevel> classApiMap,
        Map<FieldReference, AndroidApiLevel> fieldApiMap,
        Map<MethodReference, AndroidApiLevel> methodApiMap) {
      this.classApiMap = classApiMap;
      this.fieldApiMap = fieldApiMap;
      this.methodApiMap = methodApiMap;
    }

    /** Returns null if there is no error. */
    public String computeError(DexClass clazz, AndroidApiLevel foundApiLevel) {
      ClassReference reference = clazz.getClassReference();
      AndroidApiLevel expected = classApiMap.get(reference);
      if (expected == null) {
        return null;
      }
      queriedClasses.add(reference);
      if (!expected.isEqualTo(foundApiLevel)) {
        return clazz.toSourceString()
            + " has unexpected API. found: "
            + foundApiLevel
            + ", expected: "
            + expected;
      } else {
        return null;
      }
    }

    /** Returns null if there is no error. */
    public String computeError(DexField field, AndroidApiLevel foundApiLevel) {
      FieldReference reference = field.asFieldReference();
      AndroidApiLevel expected = fieldApiMap.get(reference);
      if (expected == null) {
        return null;
      }
      queriedFields.add(reference);
      if (!expected.isEqualTo(foundApiLevel)) {
        return reference.toString()
            + " has unexpected API. found: "
            + foundApiLevel
            + ", expected: "
            + expected;
      } else {
        return null;
      }
    }

    /** Returns null if there is no error. */
    public String computeError(DexMethod method, AndroidApiLevel foundApiLevel) {
      MethodReference reference = method.asMethodReference();
      AndroidApiLevel expected = methodApiMap.get(reference);
      if (expected == null) {
        return null;
      }
      queriedMethods.add(reference);
      if (!expected.isEqualTo(foundApiLevel)) {
        return reference.toString()
            + " has unexpected API. found: "
            + foundApiLevel
            + ", expected: "
            + expected;
      } else {
        return null;
      }
    }

    public List<String> unmatchedExpectedApis() {
      List<String> result = new ArrayList<>();
      for (ClassReference reference : classApiMap.keySet()) {
        if (!queriedClasses.contains(reference)) {
          result.add(reference.toString() + " was not queried");
        }
      }
      for (FieldReference reference : fieldApiMap.keySet()) {
        if (!queriedFields.contains(reference)) {
          result.add(reference.toString() + " was not queried");
        }
      }
      for (MethodReference reference : methodApiMap.keySet()) {
        if (!queriedMethods.contains(reference)) {
          result.add(reference.toString() + " was not queried");
        }
      }
      return result;
    }
  }

  private static ApiTruthLookup createExpectedApi() {
    Map<ClassReference, AndroidApiLevel> classApis = new HashMap<>();
    Map<FieldReference, AndroidApiLevel> fieldApis = new HashMap<>();
    Map<MethodReference, AndroidApiLevel> methodApis = new HashMap<>();
    SunMiscUnsafeApiTest.populateApiMaps(classApis, fieldApis, methodApis);
    return new ApiTruthLookup(classApis, fieldApis, methodApis);
  }

  private static void ensureAllPublicMethodsAreMapped(
      AppView<AppInfoWithClassHierarchy> appView, AndroidApiLevelCompute apiLevelCompute) {
    List<String> notModelledDump = new ArrayList<>();
    List<String> unexpectedApiDump = new ArrayList<>();
    Set<String> notModeledTypes = notModeledTypes();
    Set<String> notModeledFields = notModeledFields();
    Set<String> notModeledMethods = notModeledMethods();
    ApiTruthLookup expectedApi = createExpectedApi();
    for (DexLibraryClass clazz : appView.app().asDirect().libraryClasses()) {
      String typeName = clazz.getClassReference().getTypeName();
      if (notModeledTypes.contains(typeName)) {
        notModeledTypes.remove(typeName);
        continue;
      }
      ComputedApiLevel clazzApiLevel =
          apiLevelCompute.computeApiLevelForLibraryReference(clazz.getReference());
      if (clazzApiLevel.isKnownApiLevel()) {
        String error =
            expectedApi.computeError(clazz, clazzApiLevel.asKnownApiLevel().getApiLevel());
        if (error != null) {
          unexpectedApiDump.add(error);
        }
      } else {
        notModelledDump.add("notModeledTypes.add(\"" + clazz.toSourceString() + "\");");
        continue;
      }

      clazz.forEachClassField(
          field -> {
            if (field.getAccessFlags().isPublic()
                && !field.toSourceString().contains("this$0")
                && !notModeledFields.contains(field.toSourceString())) {
              ComputedApiLevel fieldApiLevel =
                  apiLevelCompute.computeApiLevelForLibraryReference(field.getReference());
              if (fieldApiLevel.isKnownApiLevel()) {
                String error =
                    expectedApi.computeError(
                        field.getReference(), fieldApiLevel.asKnownApiLevel().getApiLevel());
                if (error != null) {
                  unexpectedApiDump.add(error);
                }
              } else {
                notModelledDump.add("notModeledFields.add(\"" + field.toSourceString() + "\");");
              }
            }
            notModeledFields.remove(field.toSourceString());
          });
      clazz.forEachClassMethod(
          method -> {
            if (method.getAccessFlags().isPublic()
                && !notModeledMethods.contains(method.toSourceString())) {
              ComputedApiLevel methodApiLevel =
                  apiLevelCompute.computeApiLevelForLibraryReference(method.getReference());
              if (methodApiLevel.isKnownApiLevel()) {
                String error =
                    expectedApi.computeError(
                        method.getReference(), methodApiLevel.asKnownApiLevel().getApiLevel());
                if (error != null) {
                  unexpectedApiDump.add(error);
                }
              } else {
                notModelledDump.add("notModelledMethods.add(\"" + method.toSourceString() + "\");");
              }
            }
            notModeledMethods.remove(method.toSourceString());
          });
    }
    List<String> unmatchedExpectedApis = expectedApi.unmatchedExpectedApis();
    if (!unmatchedExpectedApis.isEmpty()) {
      String errors = unmatchedExpectedApis.stream().sorted().collect(Collectors.joining("\n"));
      fail("Some expected APIs were not found at all\n" + errors);
    }
    if (!notModelledDump.isEmpty()) {
      notModelledDump.stream().sorted().forEach(System.out::println);
      fail(
          "Some items, not found in API database. Did you forget to run main method in this class"
              + " to regenerate it?");
    }
    if (!unexpectedApiDump.isEmpty()) {
      unexpectedApiDump.stream().sorted().forEach(System.out::println);
      fail("Some items have unexpected API levels in the database");
    }

    assertTrue(
        "Not modelled types actually modeled: " + String.join(", ", notModeledTypes),
        notModeledTypes.isEmpty());
    assertTrue(
        "Not modelled fields actually modeled: " + String.join(", ", notModeledFields),
        notModeledFields.isEmpty());
    assertTrue(
        "Not modelled methods actually modeled: " + String.join(", ", notModeledMethods),
        notModeledMethods.isEmpty());
  }

  private Set<String> loadMissingApis(String resourceName) throws Exception {
    return Files.readAllLines(ToolHelper.getResourceAsReadOnlyFile(getClass(), resourceName))
        .stream()
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .collect(Collectors.toSet());
  }

  @Test
  public void testCanLookUpAllParsedApiClassesAndMembers() throws Exception {
    Set<String> knownMissingClasses = loadMissingApis("missing_classes.txt");
    Set<String> knownMissingFields = ImmutableSet.of();
    Set<String> knownMissingMethods = loadMissingApis("missing_methods.txt");
    Path androidJar = ToolHelper.getAndroidJar(API_LEVEL);
    CodeInspector inspector = new CodeInspector(androidJar);
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParserChecked.parse(
            ToolHelper.getApiVersionsXmlFile(API_LEVEL), androidJar, API_LEVEL, false);
    DexItemFactory factory = new DexItemFactory();
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    AndroidApiLevelHashingDatabaseImpl androidApiLevelDatabase =
        new AndroidApiLevelHashingDatabaseImpl(
            ImmutableList.of(), new InternalOptions(), diagnosticsHandler);

    Set<String> missingClasses = new HashSet<>();
    Set<String> missingFields = new HashSet<>();
    Set<String> missingMethods = new HashSet<>();

    parsedApiClasses.forEach(
        parsedApiClass -> {
          ClassReference classReference = parsedApiClass.getClassReference();
          ClassSubject clazz = inspector.clazz(classReference);
          if (!clazz.isPresent()) {
            missingClasses.add(classReference.getTypeName());
            return;
          }
          DexType type = factory.createType(classReference.getDescriptor());
          AndroidApiLevel apiLevel = androidApiLevelDatabase.getTypeApiLevel(type);
          assertEquals(parsedApiClass.getApiLevel(), apiLevel);
          parsedApiClass.forEachMethod(
              (methodReference, methodApiLevel) -> {
                MethodSubject methodSubject = clazz.method(methodReference);
                if (!methodSubject.isPresent()) {
                  missingMethods.add(methodReference.toSourceString());
                  return;
                }
                DexMethod method = factory.createMethod(methodReference);
                AndroidApiLevel androidApiLevel;
                if (factory.objectMembers.isObjectMember(method)) {
                  androidApiLevel = AndroidApiLevel.B;
                } else {
                  androidApiLevel = androidApiLevelDatabase.getMethodApiLevel(method);
                }
                assertTrue(androidApiLevel.isLessThanOrEqualTo(methodApiLevel));
              });
          parsedApiClass.forEachField(
              (fieldReference, fieldApiLevel) -> {
                FieldSubject fieldSubject =
                    clazz.uniqueFieldWithOriginalName(fieldReference.getFieldName());
                if (!fieldSubject.isPresent()) {
                  missingFields.add(fieldReference.toSourceString());
                  return;
                }
                DexField field = fieldSubject.getDexField();
                assertTrue(
                    androidApiLevelDatabase
                        .getFieldApiLevel(field)
                        .isLessThanOrEqualTo(fieldApiLevel));
              });
        });

    assertThat(missingClasses, matchesItemsOneToOne(knownMissingClasses));
    assertThat(missingMethods, matchesItemsOneToOne(knownMissingMethods));
    assertThat(missingFields, matchesItemsOneToOne(knownMissingFields));

    diagnosticsHandler.assertNoMessages();
  }

  /**
   * Main entry point for building a database over references in framework to the api level they
   * were introduced. Running main will generate a new jar and run tests on it to ensure it is
   * compatible with R8 sources and works as expected.
   *
   * <p>The generated jar depends on r8NoManifestWithoutDeps.
   *
   * <p>If the generated jar passes tests it will be moved and overwrite
   * third_party/api_database/api_database.ser.
   */
  public static void main(String[] args) throws Exception {
    GenerateDatabaseResourceFilesResult result = generateResourcesFiles(loadDatabaseEntries());
    API_DATABASE.toFile().mkdirs();
    Files.move(result.apiLevels, API_DATABASE, REPLACE_EXISTING);
    System.out.println(
        "Updated file in: "
            + API_DATABASE
            + "\nRemember to upload to cloud storage:"
            + "\n(cd "
            + API_DATABASE_FOLDER
            + " && upload_to_google_storage.py -a --bucket r8-deps "
            + API_DATABASE_FOLDER.getFileName()
            + ")");
  }
}
