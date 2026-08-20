// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.UnorderedCollectionMatcher.matchesItemsOneToOne;
import static com.android.tools.r8.androidapi.AndroidApiLevelDatabaseHelper.notModeledTypes;
import static com.android.tools.r8.androidapi.AndroidApiLevelDatabaseTestHelper.notModeledFields;
import static com.android.tools.r8.androidapi.AndroidApiLevelDatabaseTestHelper.notModeledMethods;
import static com.android.tools.r8.androidapi.AndroidApiLevelDatabaseTestHelper.visitHiddenReferences;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.ApiDatabaseGenerator;
import com.android.tools.r8.ApiDatabaseGeneratorCommand;
import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.ApiDatabaseGeneratorTestHelper;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.AndroidApiLevelCompute.DefaultAndroidApiLevelCompute;
import com.android.tools.r8.androidapi.AndroidApiLevelHashingDatabaseImpl;
import com.android.tools.r8.androidapi.ApiDatabaseEntry;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.androidapi.SunMiscUnsafeApiTest;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.JarTrimmer;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.ListeningDecorator;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.SkipAnswer;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.Trimmer;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.TrimmerListener;
import com.android.tools.r8.apimodel.jar.ApiJarInfo;
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
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.internal.BiConsumerUtils;
import com.android.tools.r8.utils.internal.TriConsumerUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

  private static Map<ApiDatabaseEntry, AndroidApiLevel> computeEntries(
      Collection<ParsedApiClass> apiClasses) throws Exception {
    Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries =
        AndroidApiHashingDatabaseBuilderGenerator.generateEntries(apiClasses);
    verifyAgainstJar(apiClasses, databaseEntries, API_LEVEL);
    return databaseEntries;
  }

  private static Collection<ParsedApiClass> cachedParsedApiClasses = null;

  private static Collection<ParsedApiClass> loadParsedApiClasses() throws Exception {
    if (cachedParsedApiClasses == null) {
      ApiDatabaseGeneratorCommand command =
          ApiDatabaseGeneratorCommand.builder()
              .addInputPath(ToolHelper.getApiVersionsXmlFile(API_LEVEL))
              .addInputPath(ToolHelper.getAndroidJar(API_LEVEL))
              .build();
      cachedParsedApiClasses =
          ApiDatabaseGeneratorTestHelper.generateClasses(
              command, AndroidApiHashingDatabaseBuilderGeneratorTest::addJarAssertions);
    }
    return cachedParsedApiClasses;
  }

  private static Trimmer<ApiDatabaseGeneratorException> addJarAssertions(JarTrimmer jarTrimmer) {
    Map<String, Boolean> expectedTrimmedPackages = new HashMap<>();
    ApiJarInfo jarInfo = jarTrimmer.getJarInfo();
    expectedTrimmedPackages.put("junit/", false);
    expectedTrimmedPackages.put("android/test/", false);
    expectedTrimmedPackages.put("com/android/internal/", false);
    TrimmerListener listener =
        new TrimmerListener() {
          @Override
          public void skipClass(ClassReference clazz, ApiRange range, SkipAnswer answer) {
            String classBinaryName = clazz.getBinaryName();
            for (String pkg : expectedTrimmedPackages.keySet()) {
              if (classBinaryName.startsWith(pkg)) {
                assertEquals("Expected class skipped: " + clazz, SkipAnswer.SKIP, answer);
                expectedTrimmedPackages.put(pkg, true);
                return;
              }
            }
            assertEquals("Unexpected class skip: " + clazz, SkipAnswer.KEEP, answer);
          }

          @Override
          public void skipExtends(
              ClassReference clazz, ClassReference supertype, ApiRange range, SkipAnswer answer) {
            assertEquals(
                "No extends should be skipped: " + clazz + ", " + supertype,
                SkipAnswer.KEEP,
                answer);
          }

          @Override
          public void skipImplements(
              ClassReference clazz, ClassReference supertype, ApiRange range, SkipAnswer answer) {
            assertEquals(
                "No implements should be skipped: " + clazz + ", " + supertype,
                SkipAnswer.KEEP,
                answer);
          }

          @Override
          public void skipMethod(
              ClassReference clazz, MethodReference method, ApiRange range, SkipAnswer answer) {
            if (answer == SkipAnswer.SKIP) {
              try {
                assertTrue(
                    "Only falsely inherited static methods are expected to be trimmed: " + method,
                    jarInfo.hasMethodViaFalseInheritance(
                        clazz.getBinaryName(),
                        method.getMethodName(),
                        method.getMethodDescriptor()));
              } catch (ApiDatabaseGeneratorException e) {
                throw new RuntimeException(e);
              }
            }
          }

          @Override
          public void skipField(
              ClassReference clazz,
              FieldTypelessReference field,
              ApiRange range,
              SkipAnswer answer) {
            assertEquals("No fields should be skipped: " + field, SkipAnswer.KEEP, answer);
          }

          @Override
          public void done() {
            for (Map.Entry<String, Boolean> entry : expectedTrimmedPackages.entrySet()) {
              assertTrue(
                  "Expected trimmed package not trimmed: " + entry.getKey(), entry.getValue());
            }
          }
        };
    return new ListeningDecorator<>(jarTrimmer, listener);
  }

  @SuppressWarnings("SameParameterValue")
  private static void verifyAgainstJar(
      Collection<ParsedApiClass> apiClasses,
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
      Collection<ParsedApiClass> apiClasses,
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
          (method, apiRange) ->
              methodsForApiClass.put(factory.createMethod(method), apiRange.intro));
      methodMap.put(apiClass.getClassReference(), methodsForApiClass);

      Map<FieldTypelessReference, AndroidApiLevel> fieldsForApiClass = new HashMap<>();
      apiClass.forEachField(
          (fieldReference, apiRange) -> fieldsForApiClass.put(fieldReference, apiRange.intro));
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

  @Test
  public void testEntrySize() throws Exception {
    Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries = computeEntries(loadParsedApiClasses());
    assertEquals(244_755, databaseEntries.size());
  }

  /** If this test fails, use {@link #testDumpDatabase} to diff the content in a readable format. */
  @Test
  public void testDatabaseGenerationUpToDate() throws Exception {
    temp.create();
    Path apiLevels = temp.newFile("api_levels.ser").toPath();
    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.builder()
            .addInputPath(ToolHelper.getApiVersionsXmlFile(API_LEVEL))
            .addInputPath(ToolHelper.getAndroidJar(API_LEVEL))
            .setOutputPath(apiLevels)
            .build();
    ApiDatabaseGenerator.run(command);
    assertTrue(TestBase.filesAreEqual(apiLevels, API_DATABASE));
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

  @Test
  public void testCanLookUpAllParsedApiClasses() throws Exception {
    Set<String> knownMissingClasses = getKnownMissingClasses();
    Path androidJar = ToolHelper.getAndroidJar(API_LEVEL);
    CodeInspector inspector = new CodeInspector(androidJar);
    Collection<ParsedApiClass> parsedApiClasses = loadParsedApiClasses();
    DexItemFactory factory = inspector.getFactory();
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    AndroidApiLevelHashingDatabaseImpl androidApiLevelDatabase =
        new AndroidApiLevelHashingDatabaseImpl(
            ImmutableList.of(), new InternalOptions(), diagnosticsHandler);

    Set<String> missingClasses = new HashSet<>();
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
          assertEquals(parsedApiClass.getRange().intro, apiLevel);
        });

    assertThat(missingClasses, matchesItemsOneToOne(knownMissingClasses));
    diagnosticsHandler.assertNoMessages();
  }

  private static Set<String> getKnownMissingClasses() throws IOException {
    Set<String> knownMissingClasses = new HashSet<>();
    visitHiddenReferences(
        (classReference, superReference, interfaces, apiLevel) ->
            knownMissingClasses.add(classReference.getTypeName()),
        TriConsumerUtils.nothing(),
        BiConsumerUtils.nothing());
    return knownMissingClasses;
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
    TemporaryFolder temp = new TemporaryFolder();
    temp.create();
    Path apiLevels = temp.newFile("api_levels.ser").toPath();
    ApiDatabaseGeneratorCommand command =
        ApiDatabaseGeneratorCommand.builder()
            .addInputPath(ToolHelper.getApiVersionsXmlFile(API_LEVEL))
            .addInputPath(ToolHelper.getAndroidJar(API_LEVEL))
            .setOutputPath(apiLevels)
            .build();
    ApiDatabaseGenerator.run(command);
    API_DATABASE.getParent().toFile().mkdirs();
    Files.move(apiLevels, API_DATABASE, REPLACE_EXISTING);
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

  /** When the database entries change, this method can be used to see the readable diff. */
  @SuppressWarnings("unused")
  public static void testDumpDatabase() throws Exception {
    Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries = computeEntries(loadParsedApiClasses());
    List<String> formattedEntries = new ArrayList<>(databaseEntries.size());
    for (Map.Entry<ApiDatabaseEntry, AndroidApiLevel> entry : databaseEntries.entrySet()) {
      formattedEntries.add(entry.getKey().toString() + " -> " + entry.getValue().toString());
    }
    formattedEntries.sort(null);
    Path tmpDir = Paths.get(ToolHelper.getProjectRoot(), "tmp");
    Files.createDirectories(tmpDir);
    Path dumpFile = tmpDir.resolve("api_database_dump.txt");
    Files.write(dumpFile, formattedEntries);
  }
}
