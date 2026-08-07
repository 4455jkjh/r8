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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.DiagnosticsMatcher;
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
import com.android.tools.r8.androidapi.SunMiscUnsafeApiTest;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsingException;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.JarTrimmer;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.ListeningDecorator;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.SkipAnswer;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.TrimmerListener;
import com.android.tools.r8.apimodel.jar.ApiClassInfo;
import com.android.tools.r8.apimodel.jar.ApiJarInfo;
import com.android.tools.r8.apimodel.jar.ApiJarReader;
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
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.TriConsumerUtils;
import com.android.tools.r8.utils.internal.collections.Pair;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hamcrest.CoreMatchers;
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

  private static Map<ApiDatabaseEntry, AndroidApiLevel> computeEntries(
      Collection<ParsedApiClass> apiClasses) throws Exception {
    Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries =
        AndroidApiHashingDatabaseBuilderGenerator.generateEntries(apiClasses);
    verifyAgainstJar(apiClasses, databaseEntries, API_LEVEL);
    return databaseEntries;
  }

  private static void saveToTmp(Collection<ParsedApiClass> classes, String fileName)
      throws IOException {
    Path tmpDir = Paths.get(ToolHelper.getProjectRoot(), "tmp");
    if (!Files.exists(tmpDir)) {
      Files.createDirectories(tmpDir);
    }
    Path file = tmpDir.resolve(fileName);
    Files.write(file, sortAndFormat(classes));
  }

  public static List<String> sortAndFormat(Collection<ParsedApiClass> classes) {
    List<ParsedApiClass> sortedClasses = new ArrayList<>(classes);
    sortedClasses.sort(Comparator.comparing(c -> c.getClassReference().getDescriptor()));
    List<String> result = new ArrayList<>();
    for (ParsedApiClass apiClass : sortedClasses) {
      result.add(apiClass.getClassReference().getDescriptor() + " " + apiClass.getRange());
      List<Pair<ClassReference, ApiRange>> sortedSupertypes = new ArrayList<>();
      apiClass.forEachSupertype(
          (reference, apiRange) -> sortedSupertypes.add(new Pair<>(reference, apiRange)));
      sortedSupertypes.sort(Comparator.comparing(p -> p.getFirst().getDescriptor()));
      for (Pair<ClassReference, ApiRange> pair : sortedSupertypes) {
        result.add("  super: " + pair.getFirst().getDescriptor() + " " + pair.getSecond());
      }
      List<Pair<ClassReference, ApiRange>> sortedInterfaces = new ArrayList<>();
      apiClass.forEachInterface(
          (reference, apiRange) -> sortedInterfaces.add(new Pair<>(reference, apiRange)));
      sortedInterfaces.sort(Comparator.comparing(p -> p.getFirst().getDescriptor()));
      for (Pair<ClassReference, ApiRange> pair : sortedInterfaces) {
        result.add("  implements: " + pair.getFirst().getDescriptor() + " " + pair.getSecond());
      }
      List<Pair<FieldTypelessReference, ApiRange>> sortedFields = new ArrayList<>();
      apiClass.forEachField(
          (reference, apiRange) -> sortedFields.add(new Pair<>(reference, apiRange)));
      sortedFields.sort(Comparator.comparing(p -> p.getFirst().toString()));
      for (Pair<FieldTypelessReference, ApiRange> pair : sortedFields) {
        result.add("  field: " + pair.getFirst().toString() + " " + pair.getSecond());
      }
      List<Pair<MethodReference, ApiRange>> sortedMethods = new ArrayList<>();
      apiClass.forEachMethod(
          (reference, apiRange) -> sortedMethods.add(new Pair<>(reference, apiRange)));
      sortedMethods.sort(Comparator.comparing(p -> p.getFirst().toString()));
      for (Pair<MethodReference, ApiRange> pair : sortedMethods) {
        result.add("  method: " + pair.getFirst().toString() + " " + pair.getSecond());
      }
    }
    return result;
  }

  private static Collection<ParsedApiClass> cachedParsedApiClasses = null;

  private static Collection<ParsedApiClass> loadParsedApiClasses()
      throws ParsingException, ApiDatabaseGeneratorException, IOException {
    if (cachedParsedApiClasses == null) {
      TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
      Collection<ParsedApiClass> apiClasses =
          AndroidApiVersionsXmlParser.parse(ToolHelper.getApiVersionsXmlFile(API_LEVEL));
      saveToTmp(apiClasses, "1_parsed.txt");
      ParsedApiClassVerifier.verify(apiClasses);
      ApiJarInfo jarInfo = ApiJarReader.read(ImmutableList.of(ToolHelper.getAndroidJar(API_LEVEL)));
      apiClasses = amendMissingData(apiClasses, jarInfo, diagnosticsHandler);
      saveToTmp(apiClasses, "2_merged.txt");
      ParsedApiClassVerifier.verify(apiClasses);
      apiClasses = ParsedApiClassFlattening.flatten(apiClasses);
      saveToTmp(apiClasses, "3_flattened.txt");
      ParsedApiClassVerifier.verify(apiClasses);
      apiClasses =
          ParsedApiClassTrimming.trim(apiClasses, new ParsedApiClassTrimming.RemovedTrimmer());
      saveToTmp(apiClasses, "4_trimmed_removed.txt");
      ParsedApiClassVerifier.verify(apiClasses);
      apiClasses = filterByJar(apiClasses, jarInfo);
      saveToTmp(apiClasses, "5_trimmed_jar.txt");
      ParsedApiClassVerifier.verify(apiClasses);
      diagnosticsHandler.assertAllDiagnosticsMatch(
          DiagnosticsMatcher.diagnosticMessage(CoreMatchers.startsWith("Duplicate class ")));
      cachedParsedApiClasses = apiClasses;
    }
    return cachedParsedApiClasses;
  }

  private static Collection<ParsedApiClass> filterByJar(
      Collection<ParsedApiClass> apiClasses, ApiJarInfo jarInfo)
      throws ApiDatabaseGeneratorException {
    JarTrimmer jarTrimmer = new JarTrimmer(jarInfo);
    Map<String, Boolean> expectedTrimmedPackages = new HashMap<>();
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
        };
    Collection<ParsedApiClass> trimResult =
        ParsedApiClassTrimming.trim(apiClasses, new ListeningDecorator<>(jarTrimmer, listener));
    for (Map.Entry<String, Boolean> entry : expectedTrimmedPackages.entrySet()) {
      assertTrue("Expected trimmed package not trimmed: " + entry.getKey(), entry.getValue());
    }
    return trimResult;
  }

  /**
   * Returns API data missing from {@code api-versions.xml} and {@code jarInfo} is mutated with data
   * missing from {@code android.jar}.
   */
  private static Collection<ParsedApiClass> amendMissingData(
      Collection<ParsedApiClass> apiClasses,
      ApiJarInfo jarInfo,
      TestDiagnosticMessagesImpl diagnosticsHandler)
      throws ApiDatabaseGeneratorException, IOException {
    Map<ClassReference, ApiRange> lookup = makeApiLookup(apiClasses);
    Collection<ParsedApiClass> missingApi = missingVersionEntries(lookup, jarInfo);
    Collection<ParsedApiClass> hiddenApi = addHiddenEntries(lookup, jarInfo);
    return ParsedApiClassMerging.merge(
        Iterables.concat(apiClasses, missingApi, hiddenApi), diagnosticsHandler);
  }

  /** These entries exist in android.jar and runtime but are missing in api-versions.xml. */
  private static Collection<ParsedApiClass> missingVersionEntries(
      Map<ClassReference, ApiRange> lookup, ApiJarInfo jarInfoForVerification) {
    SafeApiBuilder builder = new SafeApiBuilder(lookup);
    AndroidApiLevelDatabaseTestHelper.visitAdditionalKnownApiReferences(
        (methodReference, apiLevel) -> {
          try {
            assertTrue(
                methodReference + " was expected present in android.jar",
                jarInfoForVerification.hasMethod(methodReference));
          } catch (ApiDatabaseGeneratorException e) {
            throw new RuntimeException(e);
          }
          builder.addMethod(methodReference, new ApiRange(apiLevel));
        });
    return builder.build();
  }

  /**
   * These entries exist at runtime but not in android.jar or api-versions.xml.
   *
   * <p>The API entries are returned while the jar info is added to {@code jarInfo}.
   */
  private static Collection<ParsedApiClass> addHiddenEntries(
      Map<ClassReference, ApiRange> lookup, ApiJarInfo jarInfo) throws IOException {
    SafeApiBuilder builder = new SafeApiBuilder(lookup);

    // This class is not present in .xml so it is skipped.
    Map<String, Boolean> skipClasses = new HashMap<>();
    skipClasses.put("java/nio/DirectByteBuffer", false);

    AndroidApiLevelDatabaseTestHelper.visitHiddenReferences(
        (classReference, superReference, interfaces, apiLevel) -> {
          builder.addClass(classReference, new ApiRange(apiLevel));
          assertFalse(
              classReference + " already exists in the JAR", jarInfo.hasClass(classReference));
          String superName;
          if (superReference != null) {
            superName = superReference.getBinaryName();
          } else if (interfaces.isEmpty()) {
            superName = "java/lang/Object";
          } else {
            superName = null;
          }
          boolean isInterface = superReference == null;
          jarInfo.addClass(
              new ApiClassInfo(
                  classReference.getBinaryName(),
                  superName,
                  ListUtils.map(interfaces, ClassReference::getBinaryName),
                  isInterface,
                  ImmutableList.of(),
                  ImmutableList.of()));
        },
        (methodReference, isStatic, apiLevel) -> {
          if (skipClasses.containsKey(methodReference.getHolderClass().getBinaryName())) {
            skipClasses.put(methodReference.getHolderClass().getBinaryName(), true);
          } else {
            builder.addMethod(methodReference, new ApiRange(apiLevel));
            ApiClassInfo holder = jarInfo.getClassInfo(methodReference.getHolderClass());
            assertNotNull(
                methodReference + " could not be added, holder not present in JAR", holder);
            holder.addMethod(
                methodReference.getMethodName(), methodReference.getMethodDescriptor(), isStatic);
          }
        },
        (fieldReference, apilevel) -> {
          if (skipClasses.containsKey(fieldReference.getHolderClass().getBinaryName())) {
            skipClasses.put(fieldReference.getHolderClass().getBinaryName(), true);
          } else {
            builder.addField(fieldReference, new ApiRange(apilevel));
            ApiClassInfo holder = jarInfo.getClassInfo(fieldReference.getHolderClass());
            assertNotNull(
                fieldReference + " could not be added, holder not present in JAR", holder);
            holder.addField(fieldReference.getFieldName());
          }
        });
    for (Map.Entry<String, Boolean> entry : skipClasses.entrySet()) {
      assertTrue(entry.getKey() + " was never skipped", entry.getValue());
    }
    return builder.build();
  }

  private static Map<ClassReference, ApiRange> makeApiLookup(
      Collection<ParsedApiClass> apiClasses) {
    Map<ClassReference, ApiRange> lookup = new HashMap<>();
    for (ParsedApiClass apiClass : apiClasses) {
      assertFalse(
          apiClass.getClassReference() + " already found",
          lookup.containsKey(apiClass.getClassReference()));
      lookup.put(apiClass.getClassReference(), apiClass.getRange());
    }
    return lookup;
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

  private static class SafeApiBuilder {
    private final Map<ClassReference, ParsedApiClass> classes = new LinkedHashMap<>();
    private final Map<ClassReference, ApiRange> lookup;

    public SafeApiBuilder(Map<ClassReference, ApiRange> lookup) {
      this.lookup = lookup;
    }

    private ParsedApiClass getOrCreateClass(ClassReference classReference) {
      ParsedApiClass apiClass = classes.get(classReference);
      if (apiClass == null) {
        ApiRange lookupRange = lookup.get(classReference);
        assertNotNull(classReference + " not found in the lookup map", lookupRange);
        ParsedApiClass baseApiClass = new ParsedApiClass(classReference, lookupRange);
        classes.put(classReference, baseApiClass);
        apiClass = baseApiClass;
      }
      return apiClass;
    }

    public void addClass(ClassReference classReference, ApiRange apiRange) {
      assertFalse(classReference + " was already found", classes.containsKey(classReference));
      classes.put(classReference, new ParsedApiClass(classReference, apiRange));
    }

    public void addMethod(MethodReference methodReference, ApiRange apiRange) {
      ParsedApiClass apiClass = getOrCreateClass(methodReference.getHolderClass());
      assertFalse(methodReference + " was already found", apiClass.hasMethod(methodReference));
      AndroidApiLevel apiClassIntro = apiClass.getRange().intro;
      AndroidApiLevel apiClassRemoved = apiClass.getRange().removed;
      assertTrue(
          "class intro " + apiClassIntro + " must be less than method intro " + apiRange.intro,
          apiClassIntro.isLessThanOrEqualTo(apiRange.intro));
      assertTrue(
          "class removed "
              + apiClassRemoved
              + " must be greater than method removed "
              + apiRange.removed,
          apiClassRemoved == null
              || apiRange.isRemoved()
              || apiClassRemoved.isGreaterThanOrEqualTo(apiRange.removed));
      apiClass.registerMethod(methodReference, apiRange);
    }

    public void addField(FieldReference fieldReference, ApiRange apiRange) {
      FieldTypelessReference field =
          new FieldTypelessReference(
              fieldReference.getHolderClass(), fieldReference.getFieldName());
      ParsedApiClass apiClass = getOrCreateClass(field.getHolderClass());
      assertFalse(field + " was already found", apiClass.hasField(field));
      AndroidApiLevel apiClassIntro = apiClass.getRange().intro;
      AndroidApiLevel apiClassRemoved = apiClass.getRange().removed;
      assertTrue(
          "class intro " + apiClassIntro + " must be less than field intro " + apiRange.intro,
          apiClassIntro.isLessThanOrEqualTo(apiRange.intro));
      assertTrue(
          "class removed "
              + apiClassRemoved
              + " must be greater than field removed "
              + apiRange.removed,
          apiClassRemoved == null
              || apiRange.isRemoved()
              || apiClassRemoved.isGreaterThanOrEqualTo(apiRange.removed));
      apiClass.registerField(field, apiRange);
    }

    public Collection<ParsedApiClass> build() {
      return classes.values();
    }
  }

  @Test
  public void testEntrySize() throws Exception {
    Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries = computeEntries(loadParsedApiClasses());
    assertEquals(244_755, databaseEntries.size());
  }

  /** If this test fails, use {@link #testDumpDatabase} to diff the content in a readable format. */
  @Test
  public void testDatabaseGenerationUpToDate() throws Exception {
    GenerateDatabaseResourceFilesResult result =
        generateResourcesFiles(computeEntries(loadParsedApiClasses()));
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
    GenerateDatabaseResourceFilesResult result =
        generateResourcesFiles(computeEntries(loadParsedApiClasses()));
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
