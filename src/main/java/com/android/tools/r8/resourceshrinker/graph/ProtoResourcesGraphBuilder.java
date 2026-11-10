/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.r8.resourceshrinker.graph;

import static com.android.aapt.Resources.FileReference.Type.PROTO_XML;
import static com.android.utils.SdkUtils.IMAGE_EXTENSIONS;

import com.android.aapt.Resources.Array;
import com.android.aapt.Resources.CompoundValue;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Plural;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.ResourceTable;
import com.android.aapt.Resources.Style;
import com.android.aapt.Resources.Value;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlElement;
import com.android.aapt.Resources.XmlNode;
import com.android.ide.common.resources.usage.ResourceUsageModel;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.ide.common.resources.usage.WebTokenizers;
import com.android.resources.ResourceType;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerModel;
import com.android.tools.r8.resourceshrinker.ResourceTableUtil;
import com.android.tools.r8.resourceshrinker.ResourceTableUtil.EntryWrapper;
import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Builds resources graph starting from each resource in resource table in proto format and follow
 * all references to other resources inside inlined values and external files from res/ folder.
 *
 * <p>Supports external files in the following formats:
 *
 * <ul>
 *   <li>XML files compiled to proto format;
 *   <li>HTML, CSS, JS files inside res/raw folder;
 *   <li>Unknown files inside res/raw folder (only looks for {@code 'android_res/<type>/<name>'}
 *       pattern);
 * </ul>
 *
 * <p>As ID resources don't specify parent-child relations between resources but are just
 * identifiers for a resource or some part of the resource we don't gather them as references to
 * examined resource.
 */
public class ProtoResourcesGraphBuilder implements ResourcesGraphBuilder {

  public interface ResFolderFileTree {
    byte[] getEntryByName(String pathInRes) throws IOException;
  }

  private final ResFolderFileTree resourceRoot;
  private final Function<ResourceShrinkerModel, ResourceTable> resourceTableProducer;

  public ProtoResourcesGraphBuilder(
      ResFolderFileTree resourceRoot,
      Function<ResourceShrinkerModel, ResourceTable> resourceTableProducer) {
    this.resourceRoot = resourceRoot;
    this.resourceTableProducer = resourceTableProducer;
  }

  @Override
  public void buildGraph(ResourceShrinkerModel model) {
    for (EntryWrapper entryWrapper :
        ResourceTableUtil.entries(resourceTableProducer.apply(model))) {
      Resource resource = model.getResourceStore().getResource(entryWrapper.getId());
      if (resource != null) {
        new ReferencesForResourceFinder(resourceRoot, model, entryWrapper.getEntry(), resource)
            .findReferences();
      }
    }
  }

  private static final class ReferencesForResourceFinder {

    /**
     * 'android_res/' is a synthetic directory for resource references in URL format. For example:
     * file:///android_res/raw/intro_page.
     */
    private static final String ANDROID_RES = "android_res/";

    private static final String CONSTRAINT_REFERENCED_IDS = "constraint_referenced_ids";
    private static final Splitter COMMA_SPLITTER = Splitter.on(',');
    private static final Splitter SLASH_LIMIT_2_SPLITTER = Splitter.on('/').limit(2);

    private final ResFolderFileTree resourcesRoot;
    private final ResourceShrinkerModel model;
    private final Entry entry;
    private final Resource current;
    private WebTokenizers webTokenizers;

    ReferencesForResourceFinder(
        ResFolderFileTree resourcesRoot,
        ResourceShrinkerModel model,
        Entry entry,
        Resource current) {
      this.resourcesRoot = resourcesRoot;
      this.model = model;
      this.entry = entry;
      this.current = current;
    }

    private static Item asItem(Reference reference) {
      return Item.newBuilder().setRef(reference).build();
    }

    private WebTokenizers getWebTokenizers() {
      if (webTokenizers == null) {
        webTokenizers =
            new WebTokenizers(
                new WebTokenizers.WebTokensCallback() {
                  @Override
                  public void referencedHtmlAttribute(String tag, String attribute, String value) {
                    if ("href".equals(attribute) || "src".equals(attribute)) {
                      referencedUrl(value);
                    }
                  }

                  @Override
                  public void referencedJsString(String jsString) {
                    referencedStringFromWebContent(jsString);
                  }

                  @Override
                  public void referencedCssUrl(String url) {
                    referencedUrl(url);
                  }

                  private void referencedUrl(String url) {
                    // 1. if url contains '/' try to find resources from this web url.
                    // 2. if there is no '/' it might be just relative reference to another
                    // resource.
                    List<Resource> resources =
                        url.indexOf('/') != -1
                            ? model.getResourceStore().getResourcesFromWebUrl(url)
                            : model
                                .getResourceStore()
                                .getResources(ResourceType.RAW, substringBefore(url, '.'));
                    if (!resources.isEmpty()) {
                      for (Resource r : resources) {
                        current.addReference(r);
                      }
                    } else {
                      // if there is no resources found by provided url just gather this string as
                      // found inside web content to process it afterwards.
                      referencedStringFromWebContent(url);
                    }
                  }

                  private void referencedStringFromWebContent(String string) {
                    if (!string.isEmpty() && string.length() <= 80) {
                      model.addStringConstant(string);
                      model.setFoundWebContent(true);
                    }
                  }
                });
      }
      return webTokenizers;
    }

    void findReferences() {
      // Walk through all values of the entry and find all Item instances that may reference
      // other resources in resource table itself or specify external files that should be
      // analyzed for references.
      for (ConfigValue configValue : entry.getConfigValueList()) {
        Value value = configValue.getValue();
        CompoundValue compoundValue = value.getCompoundValue();
        // compoundValue.attr and compoundValue.styleable are skipped, attr defines
        // references to ID resources only, but ID and STYLEABLE resources are not supported
        // by shrinker.
        if (value.hasItem()) {
          findFromItem(value.getItem());
        } else if (compoundValue.hasStyle()) {
          Style style = compoundValue.getStyle();
          findFromItem(asItem(style.getParent()));
          for (Style.Entry styleEntry : style.getEntryList()) {
            findFromItem(styleEntry.getItem());
            findFromItem(asItem(styleEntry.getKey()));
          }
        } else if (compoundValue.hasArray()) {
          for (Array.Element element : compoundValue.getArray().getElementList()) {
            findFromItem(element.getItem());
          }
        } else if (compoundValue.hasPlural()) {
          for (Plural.Entry pluralEntry : compoundValue.getPlural().getEntryList()) {
            findFromItem(pluralEntry.getItem());
          }
        }
      }
    }

    private void findFromItem(Item item) {
      try {
        if (item.hasRef()) {
          findFromReference(item.getRef());
        } else if (item.hasFile() && item.getFile().getPath().startsWith("res/")) {
          findFromFile(item.getFile());
        }
      } catch (IOException e) {
        model
            .getDebugReporter()
            .debug(() -> "File '" + item.getFile().getPath() + "' can not be processed. Skipping.");
      }
    }

    private void findFromReference(Reference reference) {
      // Reference object may have id of referenced resource, in this case prefer resolved id.
      // In case id is not provided try to find referenced resource by name. Name is converted
      // to resource url here, because name in resource table is not normalized to R style field
      // and to find it we need normalize it first (for example, in case name in resource table is
      // MyStyle.Child in R file it is R.style.MyStyle_child).
      List<Resource> referencedResources;
      if (reference.getId() != 0) {
        Resource r = model.getResourceStore().getResource(reference.getId());
        referencedResources = r != null ? Collections.singletonList(r) : Collections.emptyList();
      } else if (!reference.getName().isEmpty()) {
        referencedResources =
            model.getResourceStore().getResourcesFromUrl("@" + reference.getName());
      } else {
        referencedResources = Collections.emptyList();
      }
      // IDs are not supported by shrinker for now, just skip it.
      for (Resource r : referencedResources) {
        if (r != null && r.type != ResourceType.ID) {
          current.addReference(r);
        }
      }
    }

    private void findFromFile(FileReference file) throws IOException {
      String path = file.getPath();
      byte[] bytes = resourcesRoot.getEntryByName(substringAfter(path, "res/"));
      String extension = Ascii.toLowerCase(substringAfterLast(path, '.'));
      if (file.getType() == PROTO_XML) {
        fillFromXmlNode(XmlNode.parseFrom(bytes));
      } else if ("html".equals(extension) || "htm".equals(extension)) {
        getWebTokenizers().tokenizeHtml(new String(bytes, StandardCharsets.UTF_8));
      } else if ("css".equals(extension)) {
        getWebTokenizers().tokenizeCss(new String(bytes, StandardCharsets.UTF_8));
      } else if ("js".equals(extension)) {
        getWebTokenizers().tokenizeJs(new String(bytes, StandardCharsets.UTF_8));
      } else if (!IMAGE_EXTENSIONS.contains(extension)) {
        maybeAndroidResUrl(new String(bytes, StandardCharsets.UTF_8), false);
      }
    }

    private void fillFromXmlNode(XmlNode node) {
      // Check for possible reference as 'android_res/<type>/<name>' pattern inside element text.
      if (current.type == ResourceType.XML) {
        maybeAndroidResUrl(node.getText(), true);
      }
      // Check special xml element <rawPathResId> which provides reference to res/raw/ apk
      // resource for wear application. Applies to all XML files for now but might be re-scoped
      // to only apply to <wearableApp> XMLs.
      maybeWearAppReference(node.getElement());
      for (XmlAttribute attribute : node.getElement().getAttributeList()) {
        fillFromAttribute(attribute);
      }
      for (XmlNode child : node.getElement().getChildList()) {
        fillFromXmlNode(child);
      }
    }

    private void fillFromAttribute(XmlAttribute attribute) {
      if (CONSTRAINT_REFERENCED_IDS.equals(attribute.getName())) {
        fillFromConstraintReferencedIds(attribute.getValue());
      }
      if (attribute.hasCompiledItem()) {
        findFromItem(attribute.getCompiledItem());
      }
      // Check for possible reference as 'android_res/<type>/<name>' pattern inside attribute val.
      if (current.type == ResourceType.XML) {
        maybeAndroidResUrl(attribute.getValue(), true);
      }
    }

    private void fillFromConstraintReferencedIds(String value) {
      if (value == null) {
        return;
      }
      for (String part : COMMA_SPLITTER.split(value)) {
        String trimmed = trimWhitespace(part);
        for (Resource r : model.getResourceStore().getResources(ResourceType.ID, trimmed)) {
          ResourceUsageModel.markReachable(r);
        }
      }
    }

    /**
     * Splits input text to parts that start with {@code 'android_res/'} and processes substrings
     * between {@code 'android_res/'} occurrences and the first whitespace after each. Manual {@link
     * String#indexOf} scanning is used instead of splitting into a sequence of substrings because
     * it does not spawn full substrings between {@code 'android_res/'} in memory when text is big
     * enough.
     */
    private void maybeAndroidResUrl(String text, boolean markAsReachable) {
      int start = 0;
      while (start < text.length()) {
        start = text.indexOf(ANDROID_RES, start);
        if (start == -1) {
          break;
        }
        int end = start + ANDROID_RES.length();
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
          end++;
        }
        String ref = text.substring(start + ANDROID_RES.length(), end);
        List<String> parts = SLASH_LIMIT_2_SPLITTER.splitToList(ref);
        if (parts.size() == 2) {
          ResourceType type = ResourceType.fromFolderName(substringBefore(parts.get(0), '-'));
          if (type != null) {
            String name = substringBefore(parts.get(1), '.');
            for (Resource r : model.getResourceStore().getResources(type, name)) {
              if (markAsReachable) {
                ResourceUsageModel.markReachable(r);
              } else {
                current.addReference(r);
              }
            }
          }
        }
        start = end;
      }
    }

    private void maybeWearAppReference(XmlElement element) {
      if ("rawPathResId".equals(element.getName())) {
        StringBuilder sb = new StringBuilder();
        for (XmlNode child : element.getChildList()) {
          sb.append(child.getText());
        }
        String rawResourceName = trimWhitespace(sb.toString());
        for (Resource r :
            model.getResourceStore().getResources(ResourceType.RAW, rawResourceName)) {
          current.addReference(r);
        }
      }
    }

    private static String trimWhitespace(String value) {
      int start = 0;
      int end = value.length();
      while (start < end && isWhitespace(value.charAt(start))) {
        start++;
      }
      while (end > start && isWhitespace(value.charAt(end - 1))) {
        end--;
      }
      return value.substring(start, end);
    }

    private static boolean isWhitespace(char c) {
      return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    private static String substringBefore(String value, char delimiter) {
      int idx = value.indexOf(delimiter);
      return idx == -1 ? value : value.substring(0, idx);
    }

    private static String substringAfter(String value, String delimiter) {
      int idx = value.indexOf(delimiter);
      return idx == -1 ? value : value.substring(idx + delimiter.length());
    }

    private static String substringAfterLast(String value, char delimiter) {
      int idx = value.lastIndexOf(delimiter);
      return idx == -1 ? value : value.substring(idx + 1);
    }
  }
}
