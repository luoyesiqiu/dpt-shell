package com.luoye.dpt.profile.gate.behavior;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import pxb.android.axml.AxmlParser;

public final class ProfileApkBehaviorAnalyzer {
    private static final String MANIFEST_ENTRY = "AndroidManifest.xml";
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final Set<String> COMPONENT_TAGS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "activity", "activity-alias", "service", "receiver", "provider")));
    private static final Pattern NATIVE_LIBRARY = Pattern.compile(
            "^lib/([^/]+)/([^/]+\\.so)$");
    private static final Pattern DEX_ENTRY = Pattern.compile("^classes(?:[0-9]+)?\\.dex$");
    private static final Map<String, Set<String>> NETWORK_MARKERS = markers(
            marker("java-network", "Ljava/net/", "Ljavax/net/"),
            marker("http-client", "Lokhttp3/", "Lretrofit2/"),
            marker("native-dns", "getaddrinfo"),
            marker("native-socket", "<sys/socket.h>", "AF_INET"),
            marker("embedded-http-url", "http://", "https://"));
    private static final Map<String, Set<String>> DYNAMIC_CODE_MARKERS = markers(
            marker("dex-class-loader", "Ldalvik/system/DexClassLoader;"),
            marker("in-memory-dex-loader", "Ldalvik/system/InMemoryDexClassLoader;"),
            marker("path-class-loader", "Ldalvik/system/PathClassLoader;"),
            marker("native-dynamic-loader", "android_dlopen_ext", "dlopen", "dlsym"));

    private ProfileApkBehaviorAnalyzer() {
    }

    public static ProfileApkBehaviorSnapshot analyze(Path apk,
                                                     ProfileApkBehaviorScanPolicy policy)
            throws IOException {
        requireApk(apk);
        if (policy == null) {
            throw new IllegalArgumentException("APK 静态扫描策略不能为空");
        }
        Set<String> dependencies = new LinkedHashSet<>();
        Set<String> networkCapabilities = new LinkedHashSet<>();
        Set<String> dynamicCodeCapabilities = new LinkedHashSet<>();
        ManifestData manifestData;
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            ZipEntry manifestEntry = zip.getEntry(MANIFEST_ENTRY);
            if (manifestEntry == null || manifestEntry.isDirectory()) {
                throw new IOException("APK 缺少 AndroidManifest.xml：" + apk);
            }
            manifestData = parseManifest(read(zip, manifestEntry));
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                Matcher nativeMatcher = NATIVE_LIBRARY.matcher(name);
                boolean dex = DEX_ENTRY.matcher(name).matches();
                if (!nativeMatcher.matches() && !dex) {
                    continue;
                }
                byte[] content = read(zip, entry);
                collectCapabilities(content, NETWORK_MARKERS, networkCapabilities);
                collectCapabilities(content, DYNAMIC_CODE_MARKERS,
                        dynamicCodeCapabilities);
                if (nativeMatcher.matches()) {
                    String libraryName = nativeMatcher.group(2);
                    if (!policy.getIgnoredNativeLibraries().contains(libraryName)) {
                        dependencies.add("native:" + nativeMatcher.group(1) + ":"
                                + libraryName);
                    }
                } else {
                    collectDexDependencies(content, policy, dependencies, name);
                }
            }
        }
        return new ProfileApkBehaviorSnapshot(sha256(apk), manifestData.applicationId,
                manifestData.permissions, manifestData.exportedComponents, dependencies,
                networkCapabilities, dynamicCodeCapabilities);
    }

    private static void collectDexDependencies(byte[] content,
                                               ProfileApkBehaviorScanPolicy policy,
                                               Set<String> dependencies,
                                               String entryName) throws IOException {
        DexBackedDexFile dexFile;
        try {
            dexFile = new DexBackedDexFile(Opcodes.getDefault(), content);
        } catch (RuntimeException exception) {
            throw new IOException("APK DEX 无法解析：" + entryName, exception);
        }
        for (ClassDef classDef : dexFile.getClasses()) {
            String className = descriptorToClassName(classDef.getType());
            if (className == null || isIgnoredClass(className, policy)) {
                continue;
            }
            int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                dependencies.add("dex:" + className.substring(0, lastDot));
            }
        }
    }

    private static boolean isIgnoredClass(String className,
                                          ProfileApkBehaviorScanPolicy policy) {
        for (String prefix : policy.getIgnoredClassPrefixes()) {
            if (className.equals(prefix) || className.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    private static String descriptorToClassName(String descriptor) {
        if (descriptor == null || descriptor.length() < 3 || descriptor.charAt(0) != 'L'
                || descriptor.charAt(descriptor.length() - 1) != ';') {
            return null;
        }
        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }

    private static ManifestData parseManifest(byte[] content) throws IOException {
        return isPlainXml(content) ? parsePlainManifest(content) : parseBinaryManifest(content);
    }

    private static ManifestData parsePlainManifest(byte[] content) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(content));
            Element manifest = document.getDocumentElement();
            String applicationId = manifest.getAttribute("package");
            ManifestData result = new ManifestData(requireApplicationId(applicationId));
            NodeList children = manifest.getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                Node child = children.item(index);
                if (!(child instanceof Element)) {
                    continue;
                }
                Element element = (Element) child;
                String tag = localName(element);
                if ("uses-permission".equals(tag)
                        || "uses-permission-sdk-23".equals(tag)) {
                    addNonBlank(result.permissions, androidAttribute(element, "name"));
                } else if ("application".equals(tag)) {
                    collectPlainComponents(element, result);
                }
            }
            return result;
        } catch (Exception exception) {
            throw new IOException("文本 AndroidManifest.xml 无法解析", exception);
        }
    }

    private static void collectPlainComponents(Element application, ManifestData result) {
        NodeList children = application.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (!(child instanceof Element)) {
                continue;
            }
            Element component = (Element) child;
            String tag = localName(component);
            if (!COMPONENT_TAGS.contains(tag)) {
                continue;
            }
            String exportedValue = androidAttribute(component, "exported");
            boolean exported = isTrue(exportedValue)
                    || (exportedValue.isEmpty() && hasDirectIntentFilter(component));
            if (exported) {
                addComponent(result, tag, androidAttribute(component, "name"));
            }
        }
    }

    private static ManifestData parseBinaryManifest(byte[] content) throws IOException {
        AxmlParser parser = new AxmlParser(content);
        ManifestData result = null;
        Deque<ComponentState> componentStack = new ArrayDeque<>();
        int event;
        while ((event = parser.next()) != AxmlParser.END_FILE) {
            if (event == AxmlParser.START_TAG) {
                String tag = parser.getName();
                if ("manifest".equals(tag)) {
                    result = new ManifestData(requireApplicationId(attribute(parser, "package")));
                } else if (result != null && ("uses-permission".equals(tag)
                        || "uses-permission-sdk-23".equals(tag))) {
                    addNonBlank(result.permissions, attribute(parser, "name"));
                } else if (result != null && COMPONENT_TAGS.contains(tag)) {
                    componentStack.push(new ComponentState(tag, attribute(parser, "name"),
                            nullableBoolean(attributeValue(parser, "exported"))));
                } else if ("intent-filter".equals(tag) && !componentStack.isEmpty()) {
                    componentStack.peek().hasIntentFilter = true;
                }
            } else if (event == AxmlParser.END_TAG && !componentStack.isEmpty()
                    && componentStack.peek().tag.equals(parser.getName())) {
                ComponentState component = componentStack.pop();
                boolean exported = component.exported != null
                        ? component.exported : component.hasIntentFilter;
                if (exported && result != null) {
                    addComponent(result, component.tag, component.name);
                }
            }
        }
        if (result == null) {
            throw new IOException("二进制 AndroidManifest.xml 缺少 manifest 根节点");
        }
        return result;
    }

    private static String attribute(AxmlParser parser, String name) {
        Object value = attributeValue(parser, name);
        return value == null ? "" : String.valueOf(value);
    }

    private static Object attributeValue(AxmlParser parser, String name) {
        for (int index = 0; index < parser.getAttrCount(); index++) {
            if (name.equals(parser.getAttrName(index))) {
                return parser.getAttrValue(index);
            }
        }
        return null;
    }

    private static void addComponent(ManifestData result, String tag, String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return;
        }
        String name = rawName.trim();
        if (name.startsWith(".")) {
            name = result.applicationId + name;
        } else if (name.indexOf('.') < 0) {
            name = result.applicationId + "." + name;
        }
        result.exportedComponents.add(tag + ":" + name);
    }

    private static boolean hasDirectIntentFilter(Element component) {
        NodeList children = component.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element && "intent-filter".equals(localName((Element) child))) {
                return true;
            }
        }
        return false;
    }

    private static String androidAttribute(Element element, String name) {
        String value = element.getAttributeNS(ANDROID_NAMESPACE, name);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute("android:" + name);
        }
        return value == null ? "" : value;
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private static Boolean nullableBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return isTrue(String.valueOf(value));
    }

    private static boolean isTrue(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static String requireApplicationId(String value) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("AndroidManifest.xml 缺少 applicationId");
        }
        return value.trim();
    }

    private static void addNonBlank(Set<String> target, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.add(value.trim());
        }
    }

    private static boolean isPlainXml(byte[] content) {
        int index = 0;
        while (index < content.length && Character.isWhitespace((char) content[index])) {
            index++;
        }
        return index < content.length && content[index] == '<';
    }

    private static byte[] read(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static void collectCapabilities(byte[] content,
                                            Map<String, Set<String>> markers,
                                            Set<String> result) {
        String searchable = new String(content, StandardCharsets.ISO_8859_1);
        for (Map.Entry<String, Set<String>> entry : markers.entrySet()) {
            for (String marker : entry.getValue()) {
                if (searchable.contains(marker)) {
                    result.add(entry.getKey());
                    break;
                }
            }
        }
    }

    private static Map<String, Set<String>> markers(MarkerGroup... groups) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (MarkerGroup group : groups) {
            result.put(group.capability, group.markers);
        }
        return result;
    }

    private static MarkerGroup marker(String capability, String... values) {
        return new MarkerGroup(capability,
                Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values))));
    }

    private static void requireApk(Path apk) throws IOException {
        if (apk == null || Files.isSymbolicLink(apk)
                || !Files.isRegularFile(apk, LinkOption.NOFOLLOW_LINKS)
                || Files.size(apk) <= 0L) {
            throw new IOException("APK 静态扫描输入无效：" + apk);
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private static final class ManifestData {
        private final String applicationId;
        private final Set<String> permissions = new LinkedHashSet<>();
        private final Set<String> exportedComponents = new LinkedHashSet<>();

        private ManifestData(String applicationId) {
            this.applicationId = applicationId;
        }
    }

    private static final class ComponentState {
        private final String tag;
        private final String name;
        private final Boolean exported;
        private boolean hasIntentFilter;

        private ComponentState(String tag, String name, Boolean exported) {
            this.tag = tag;
            this.name = name;
            this.exported = exported;
        }
    }

    private static final class MarkerGroup {
        private final String capability;
        private final Set<String> markers;

        private MarkerGroup(String capability, Set<String> markers) {
            this.capability = capability;
            this.markers = markers;
        }
    }
}
