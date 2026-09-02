package com.luoye.dpt.profile.generation;

import com.luoye.dpt.profile.contract.ProfileJniSemantic;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplate;
import com.luoye.dpt.profile.generation.recipe.RecipeRuntimeContract;
import com.luoye.dpt.profile.model.ProfileIdentityLock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ProfileJavaSourceGenerator {
    private static final Set<String> ENTRY_UNITS = new HashSet<>(Arrays.asList(
            "application-entry", "component-factory-entry", "jni-bridge"));

    void generate(ProfileCodeGenerator.GenerationLayout layout, ProfileIdentityLock identity,
                  ProfileRecipeTemplate template) throws IOException {
        if (layout == null || identity == null || template == null) {
            throw new IllegalArgumentException("java generation arguments must not be null");
        }
        String packageName = identity.getShellPackageName();
        List<HelperNames> helpers = helperNames(identity, template);
        ActionNames actions = actionNames(identity, template);
        Map<String, String> helperSources = helperSources(packageName, helpers, actions,
                template);
        HelperNames root = rootHelper(helpers);
        RuntimeNames runtime = runtimeNames(identity, template, root, actions);
        writeSource(layout, identity.getProxyApplicationClassName(), applicationSource(
                packageName, simpleName(identity.getProxyApplicationClassName()), identity,
                root, actions, runtime));
        writeSource(layout, identity.getProxyComponentFactoryClassName(), componentFactorySource(
                packageName, simpleName(identity.getProxyComponentFactoryClassName()), identity,
                root, actions, runtime));
        writeSource(layout, identity.getJniBridgeClassName(), bridgeSource(packageName,
                simpleName(identity.getJniBridgeClassName()), identity));
        writeSource(layout, packageName + "." + actions.className,
                actionsSource(packageName, actions));
        writeSource(layout, packageName + "." + runtime.className,
                runtimeSource(packageName, identity, template, root, actions, runtime));
        new ProfileJavaLibrarySourceGenerator().generate(layout, identity, template);

        String constantsClassName = derive(identity, "java-build-constants", "K");
        writeSource(layout, packageName + "." + constantsClassName, constantsSource(
                packageName, constantsClassName, identity));
        for (HelperNames helper : helpers) {
            writeSource(layout, packageName + "." + helper.className,
                    helperSources.get(helper.unit));
        }
        writeSource(layout, identity.getJunkClassName(), junkSource(packageName,
                simpleName(identity.getJunkClassName()), identity));
        write(layout.getR8Directory().resolve("profile-rules.pro"), r8Rules(identity));
    }

    private static List<HelperNames> helperNames(ProfileIdentityLock identity,
                                                  ProfileRecipeTemplate template) {
        List<HelperNames> result = new ArrayList<>();
        for (String unit : template.getJavaSourceUnits()) {
            if (ENTRY_UNITS.contains(unit)) {
                continue;
            }
            String label = template.getRecipeId() + "-" + unit;
            result.add(new HelperNames(
                    unit,
                    derive(identity, "java-helper-class-" + label, "H"),
                    derive(identity, "java-helper-method-" + label, "m")));
        }
        return result;
    }

    private static String applicationSource(String packageName, String className,
                                            ProfileIdentityLock identity, HelperNames root,
                                            ActionNames actions, RuntimeNames runtime) {
        String coordinateMethod = derive(identity, "java-entry-coordinate-application", "m");
        return "package " + packageName + ";\n\n"
                + "import android.app.Application;\n"
                + "import android.content.Context;\n"
                + "import android.content.pm.ApplicationInfo;\n"
                + "import android.content.pm.PackageManager;\n\n"
                + "public final class " + className + " extends Application {\n"
                + "    static void " + coordinateMethod + "(" + actions.className
                + " actions) {\n"
                + "        " + root.className + "." + root.methodName + "(actions);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    protected void attachBaseContext(Context base) {\n"
                + "        super.attachBaseContext(base);\n"
                + "        ApplicationInfo info = base.getApplicationInfo();\n"
                + "        if (info == null) {\n"
                + "            throw new IllegalStateException(\"application info is null\");\n"
                + "        }\n"
                + "        " + runtime.className + "." + runtime.startMethod
                + "(base.getClassLoader(), info.sourceDir, info.dataDir, info.packageName);\n"
                + "    }\n"
                + "\n    @Override\n"
                + "    public void onCreate() {\n"
                + "        super.onCreate();\n"
                + "        " + runtime.className + "." + runtime.restoreMethod + "();\n"
                + "    }\n"
                + "\n    @Override\n"
                + "    public Context createPackageContext(String packageName, int flags)\n"
                + "            throws PackageManager.NameNotFoundException {\n"
                + "        Object restored = " + runtime.className + "." + runtime.contextMethod
                + "();\n"
                + "        if (restored instanceof Context) {\n"
                + "            return (Context) restored;\n"
                + "        }\n"
                + "        return super.createPackageContext(packageName, flags);\n"
                + "    }\n"
                + "\n    @Override\n"
                + "    public String getPackageName() {\n"
                + "        if (" + runtime.className + "." + runtime.hidePackageMethod + "()) {\n"
                + "            return \"\";\n"
                + "        }\n"
                + "        return super.getPackageName();\n"
                + "    }\n"
                + "}\n";
    }

    private static String componentFactorySource(String packageName, String className,
                                                 ProfileIdentityLock identity, HelperNames root,
                                                 ActionNames actions, RuntimeNames runtime) {
        String coordinateMethod = derive(identity,
                "java-entry-coordinate-component-factory", "m");
        return "package " + packageName + ";\n\n"
                + "import android.annotation.TargetApi;\n"
                + "import android.app.Activity;\n"
                + "import android.app.AppComponentFactory;\n\n"
                + "import android.app.Application;\n"
                + "import android.app.Service;\n"
                + "import android.content.BroadcastReceiver;\n"
                + "import android.content.ContentProvider;\n"
                + "import android.content.Intent;\n"
                + "import android.content.pm.ApplicationInfo;\n\n"
                + "@TargetApi(28)\n"
                + "public final class " + className + " extends AppComponentFactory {\n"
                + "    static void " + coordinateMethod + "(" + actions.className
                + " actions) {\n"
                + "        " + root.className + "." + root.methodName + "(actions);\n"
                + "    }\n"
                + "\n    @Override\n"
                + "    public ClassLoader instantiateClassLoader(ClassLoader cl, ApplicationInfo info) {\n"
                + "        " + runtime.className + "." + runtime.startMethod
                + "(cl, info.sourceDir, info.dataDir, info.packageName);\n"
                + "        Object target = " + runtime.className + "." + runtime.factoryMethod
                + "(cl);\n"
                + "        if (target instanceof AppComponentFactory) {\n"
                + "            try {\n"
                + "                ClassLoader result = ((AppComponentFactory) target).instantiateClassLoader(cl, info);\n"
                + "                return result == null ? super.instantiateClassLoader(cl, info) : result;\n"
                + "            } catch (Throwable ignored) {\n"
                + "            }\n"
                + "        }\n"
                + "        return super.instantiateClassLoader(cl, info);\n"
                + "    }\n"
                + "\n    @Override\n"
                + "    public Application instantiateApplication(ClassLoader cl, String className)\n"
                + "            throws ClassNotFoundException, IllegalAccessException, InstantiationException {\n"
                + "        " + runtime.className + "." + runtime.startFromRuntimeMethod + "(cl);\n"
                + "        String original = " + runtime.className + "." + runtime.applicationNameMethod + "();\n"
                + "        Object target = " + runtime.className + "." + runtime.factoryMethod + "(cl);\n"
                + "        String selected = original.length() == 0 ? className : original;\n"
                + "        if (target instanceof AppComponentFactory) {\n"
                + "            try {\n"
                + "                return ((AppComponentFactory) target).instantiateApplication(cl, selected);\n"
                + "            } catch (Throwable ignored) {\n"
                + "            }\n"
                + "        }\n"
                + "        return super.instantiateApplication(cl, selected);\n"
                + "    }\n"
                + componentFactoryComponentMethod(runtime.className, runtime.factoryMethod,
                        "Activity", "instantiateActivity", "Intent intent",
                        "((AppComponentFactory) target).instantiateActivity(cl, className, intent)")
                + componentFactoryComponentMethod(runtime.className, runtime.factoryMethod,
                        "Service", "instantiateService", "Intent intent",
                        "((AppComponentFactory) target).instantiateService(cl, className, intent)")
                + componentFactoryComponentMethod(runtime.className, runtime.factoryMethod,
                        "BroadcastReceiver", "instantiateReceiver", "Intent intent",
                        "((AppComponentFactory) target).instantiateReceiver(cl, className, intent)")
                + componentFactoryProviderMethod(runtime.className, runtime.factoryMethod)
                + "}\n";
    }

    private static String componentFactoryComponentMethod(String runtimeClass,
                                                           String factoryMethod,
                                                           String type, String method,
                                                           String extraParameter,
                                                           String delegate) {
        return "\n    @Override\n"
                + "    public " + type + " " + method
                + "(ClassLoader cl, String className, " + extraParameter + ")\n"
                + "            throws ClassNotFoundException, IllegalAccessException, InstantiationException {\n"
                + "        Object target = " + runtimeClass + "." + factoryMethod + "(cl);\n"
                + "        if (target instanceof AppComponentFactory) {\n"
                + "            try {\n"
                + "                return " + delegate + ";\n"
                + "            } catch (Throwable ignored) {\n"
                + "            }\n"
                + "        }\n"
                + "        return super." + method + "(cl, className, intent);\n"
                + "    }\n";
    }

    private static String componentFactoryProviderMethod(String runtimeClass,
                                                          String factoryMethod) {
        return "\n    @Override\n"
                + "    public ContentProvider instantiateProvider(ClassLoader cl, String className)\n"
                + "            throws ClassNotFoundException, IllegalAccessException, InstantiationException {\n"
                + "        Object target = " + runtimeClass + "." + factoryMethod + "(cl);\n"
                + "        if (target instanceof AppComponentFactory) {\n"
                + "            try {\n"
                + "                return ((AppComponentFactory) target).instantiateProvider(cl, className);\n"
                + "            } catch (Throwable ignored) {\n"
                + "            }\n"
                + "        }\n"
                + "        return super.instantiateProvider(cl, className);\n"
                + "    }\n";
    }

    private static RuntimeNames runtimeNames(ProfileIdentityLock identity,
                                             ProfileRecipeTemplate template,
                                             HelperNames root, ActionNames actions) {
        String recipe = template.getRecipeId();
        return new RuntimeNames(
                identity,
                template,
                root,
                actions,
                derive(identity, "java-runtime-coordinator", "R"),
                derive(identity, "java-runtime-start", "m"),
                derive(identity, "java-runtime-start-from-runtime", "m"),
                derive(identity, "java-runtime-restore-application", "m"),
                derive(identity, "java-runtime-application-context", "m"),
                derive(identity, "java-runtime-hide-package", "m"),
                derive(identity, "java-runtime-resolve-factory", "m"),
                derive(identity, "java-runtime-application-name", "m"),
                derive(identity, "java-runtime-started", "f"),
                derive(identity, "java-runtime-shell-prepared", "f"),
                derive(identity, "java-runtime-shell-loaded", "f"),
                derive(identity, "java-runtime-native-initialized", "f"),
                derive(identity, "java-runtime-loader", "f"),
                derive(identity, "java-runtime-source-dir", "f"),
                derive(identity, "java-runtime-data-dir", "f"),
                derive(identity, "java-runtime-package-name", "f"),
                derive(identity, "java-runtime-original-app-name", "f"),
                derive(identity, "java-runtime-original-factory-name", "f"),
                derive(identity, "java-runtime-original-factory", "f"),
                derive(identity, "java-runtime-factory-resolved", "f"),
                derive(identity, "java-runtime-original-application", "f"),
                derive(identity, "java-runtime-application-restored", "f"),
                derive(identity, "java-runtime-application-on-create", "f"),
                derive(identity, "java-runtime-lock", "f"),
                derive(identity, "java-runtime-runtime-info", "m"),
                derive(identity, "java-runtime-normalize-class", "m"),
                derive(identity, "java-library-root-" + recipe, "L"),
                derive(identity, "java-library-prepare", "m"),
                derive(identity, "java-library-load", "m"));
    }

    private static String runtimeSource(String packageName, ProfileIdentityLock identity,
                                        ProfileRecipeTemplate template, HelperNames root,
                                        ActionNames actions, RuntimeNames runtime) {
        String bridge = simpleName(identity.getJniBridgeClassName());
        String init = ProfileJniSemantic.INIT_APP.requireMethodName(identity.getJniMethods());
        String combine = ProfileJniSemantic.COMBINE_DEX_ELEMENTS
                .requireMethodName(identity.getJniMethods());
        String readFactory = ProfileJniSemantic.READ_APP_COMPONENT_FACTORY
                .requireMethodName(identity.getJniMethods());
        String readApplication = ProfileJniSemantic.READ_APPLICATION_NAME
                .requireMethodName(identity.getJniMethods());
        String replace = ProfileJniSemantic.REPLACE_APPLICATION
                .requireMethodName(identity.getJniMethods());
        String callOnCreate = ProfileJniSemantic.CALL_REAL_APPLICATION_ON_CREATE
                .requireMethodName(identity.getJniMethods());
        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n")
                .append("import android.app.Application;\n")
                .append("import android.content.pm.ApplicationInfo;\n")
                .append("import java.lang.reflect.Field;\n")
                .append("import java.lang.reflect.Method;\n\n")
                .append("final class ").append(runtime.className).append(" {\n")
                .append("    private static final Object ").append(runtime.lockField)
                .append(" = new Object();\n")
                .append("    private static boolean ").append(runtime.startedField).append(";\n")
                .append("    private static boolean ").append(runtime.preparedField).append(";\n")
                .append("    private static boolean ").append(runtime.loadedField).append(";\n")
                .append("    private static boolean ").append(runtime.nativeField).append(";\n")
                .append("    private static ClassLoader ").append(runtime.loaderField).append(";\n")
                .append("    private static ClassLoader ").append(runtime.combinedLoaderField).append(";\n")
                .append("    private static String ").append(runtime.sourceField).append(";\n")
                .append("    private static String ").append(runtime.dataField).append(";\n")
                .append("    private static String ").append(runtime.packageField).append(";\n")
                .append("    private static String ").append(runtime.applicationNameField)
                .append(" = \"\";\n")
                .append("    private static String ").append(runtime.factoryNameField)
                .append(" = \"\";\n")
                .append("    private static Object ").append(runtime.factoryField).append(";\n")
                .append("    private static boolean ").append(runtime.factoryResolvedField).append(";\n")
                .append("    private static Object ").append(runtime.applicationField).append(";\n")
                .append("    private static boolean ").append(runtime.applicationRestoredField)
                .append(";\n")
                .append("    private static boolean ").append(runtime.applicationOnCreateField)
                .append(";\n\n")
                .append("    private static final ").append(actions.className).append(" ACTIONS = new ")
                .append(actions.className).append("() {\n");

        appendAction(source, actions.methodNames.get(0),
                "if (!" + runtime.preparedField + ") {\n"
                        + "            " + runtime.libraryRootClass + "." + runtime.libraryPrepareMethod
                        + "(" + runtime.sourceField + ", " + runtime.dataField + ");\n"
                        + "            " + runtime.preparedField + " = true;\n"
                        + "        }");
        appendAction(source, actions.methodNames.get(1),
                "if (!" + runtime.loadedField + ") {\n"
                        + "            " + runtime.libraryRootClass + "." + runtime.libraryLoadMethod
                        + "(" + runtime.dataField + ");\n"
                        + "            " + runtime.loadedField + " = true;\n"
                        + "        }");
        appendAction(source, actions.methodNames.get(2),
                "if (!" + runtime.nativeField + ") {\n"
                        + "            " + bridge + "." + init + "();\n"
                        + "            " + runtime.nativeField + " = true;\n"
                        + "        }");
        appendAction(source, actions.methodNames.get(3),
                "if (" + runtime.loaderField + " != " + runtime.combinedLoaderField + ") {\n"
                        + "            " + bridge + "." + combine + "(" + runtime.loaderField + ");\n"
                        + "            " + runtime.combinedLoaderField + " = " + runtime.loaderField + ";\n"
                        + "        }");
        appendAction(source, actions.methodNames.get(4),
                runtime.factoryNameField + " = " + runtime.normalizeMethod + "(" + bridge + "."
                        + readFactory + "(), " + runtime.packageField + ");\n"
                        + "        " + runtime.factoryResolvedField + " = false;\n"
                        + "        " + runtime.factoryField + " = null;");
        appendAction(source, actions.methodNames.get(5),
                runtime.applicationNameField + " = " + runtime.normalizeMethod + "("
                        + bridge + "." + readApplication + "(), " + runtime.packageField + ");");
        source.append("    };\n\n")
                .append("    private ").append(runtime.className).append("() {\n")
                .append("    }\n\n")
                .append("    static void ").append(runtime.startMethod)
                .append("(ClassLoader loader, String sourceDir, String dataDir, String packageName) {\n")
                .append("        if (loader == null || sourceDir == null || dataDir == null\n")
                .append("                || packageName == null || packageName.length() == 0) {\n")
                .append("            throw new IllegalStateException(\"invalid Profile runtime inputs\");\n")
                .append("        }\n")
                .append("        synchronized (").append(runtime.lockField).append(") {\n")
                .append("            if (").append(runtime.startedField).append(") {\n")
                .append("                if (").append(runtime.loaderField).append(" != loader) {\n")
                .append("                    throw new IllegalStateException(\"Profile class loader changed\");\n")
                .append("                }\n")
                .append("                return;\n")
                .append("            }\n")
                .append("            ").append(runtime.loaderField).append(" = loader;\n")
                .append("            ").append(runtime.sourceField).append(" = sourceDir;\n")
                .append("            ").append(runtime.dataField).append(" = dataDir;\n")
                .append("            ").append(runtime.packageField).append(" = packageName;\n")
                .append("            ").append(root.className).append(".").append(root.methodName)
                .append("(ACTIONS);\n")
                .append("            ").append(runtime.startedField).append(" = true;\n")
                .append("        }\n")
                .append("    }\n\n")
                .append("    static void ").append(runtime.startFromRuntimeMethod)
                .append("(ClassLoader loader) {\n")
                .append("        synchronized (").append(runtime.lockField).append(") {\n")
                .append("            if (").append(runtime.startedField).append(") {\n")
                .append("                return;\n")
                .append("            }\n")
                .append("        }\n")
                .append("        ApplicationInfo info = ").append(runtime.runtimeInfoMethod).append("();\n")
                .append("        if (info == null) {\n")
                .append("            throw new IllegalStateException(\"application info is null\");\n")
                .append("        }\n")
                .append("        ").append(runtime.startMethod).append("(loader, info.sourceDir, info.dataDir, info.packageName);\n")
                .append("    }\n\n")
                .append("    static Object ").append(runtime.factoryMethod)
                .append("(ClassLoader loader) {\n")
                .append("        synchronized (").append(runtime.lockField).append(") {\n")
                .append("            if (").append(runtime.factoryResolvedField).append(") {\n")
                .append("                return ").append(runtime.factoryField).append(";\n")
                .append("            }\n")
                .append("            ").append(runtime.factoryResolvedField).append(" = true;\n")
                .append("            if (").append(runtime.factoryNameField).append(".length() == 0) {\n")
                .append("                return null;\n")
                .append("            }\n")
                .append("            try {\n")
                .append("                ").append(runtime.factoryField).append(" = Class.forName(")
                .append(runtime.factoryNameField).append(", true, loader).newInstance();\n")
                .append("            } catch (Throwable ignored) {\n")
                .append("                ").append(runtime.factoryField).append(" = null;\n")
                .append("            }\n")
                .append("            return ").append(runtime.factoryField).append(";\n")
                .append("        }\n")
                .append("    }\n\n")
                .append("    static String ").append(runtime.applicationNameMethod).append("() {\n")
                .append("        synchronized (").append(runtime.lockField).append(") {\n")
                .append("            return ").append(runtime.applicationNameField).append(";\n")
                .append("        }\n")
                .append("    }\n\n")
                .append("    static Object ").append(runtime.restoreMethod).append("() {\n")
                .append("        synchronized (").append(runtime.lockField).append(") {\n")
                .append("            if (").append(runtime.applicationNameField).append(".length() == 0) {\n")
                .append("                return null;\n")
                .append("            }\n")
                .append("            if (!").append(runtime.applicationRestoredField).append(") {\n")
                .append("                ").append(runtime.applicationField).append(" = ").append(bridge)
                .append(".").append(replace).append("(").append(runtime.applicationNameField).append(");\n")
                .append("                if (").append(runtime.applicationField).append(" == null) {\n")
                .append("                    throw new IllegalStateException(\"cannot restore original application\");\n")
                .append("                }\n")
                .append("                ").append(runtime.applicationRestoredField).append(" = true;\n")
                .append("            }\n")
                .append("            if (!").append(runtime.applicationOnCreateField).append(") {\n")
                .append("                ").append(bridge).append(".").append(callOnCreate)
                .append("(").append(runtime.applicationNameField).append(");\n")
                .append("                ").append(runtime.applicationOnCreateField).append(" = true;\n")
                .append("            }\n")
                .append("            return ").append(runtime.applicationField).append(";\n")
                .append("        }\n")
                .append("    }\n\n")
                .append("    static Object ").append(runtime.contextMethod).append("() {\n")
                .append("        synchronized (").append(runtime.lockField).append(") {\n")
                .append("            return ").append(runtime.applicationRestoredField).append(" ? ")
                .append(runtime.applicationField).append(" : null;\n")
                .append("        }\n")
                .append("    }\n\n")
                .append("    static boolean ").append(runtime.hidePackageMethod).append("() {\n")
                .append("        synchronized (").append(runtime.lockField).append(") {\n")
                .append("            return ").append(runtime.applicationRestoredField).append(" && ")
                .append(runtime.applicationNameField).append(".length() != 0;\n")
                .append("        }\n")
                .append("    }\n\n")
                .append("    private static String ").append(runtime.normalizeMethod)
                .append("(String value, String packageName) {\n")
                .append("        if (value == null) {\n")
                .append("            return \"\";\n")
                .append("        }\n")
                .append("        String result = value.trim();\n")
                .append("        if (result.length() == 0) {\n")
                .append("            return result;\n")
                .append("        }\n")
                .append("        if (result.charAt(0) == '.') {\n")
                .append("            return packageName + result;\n")
                .append("        }\n")
                .append("        return result.indexOf('.') < 0 ? packageName + \".\" + result : result;\n")
                .append("    }\n\n")
                .append("    private static ApplicationInfo ").append(runtime.runtimeInfoMethod)
                .append("() {\n")
                .append("        try {\n")
                .append("            Class<?> threadClass = Class.forName(\"android.app.ActivityThread\");\n")
                .append("            try {\n")
                .append("                Method currentApplication = threadClass.getDeclaredMethod(\"currentApplication\");\n")
                .append("                Object application = currentApplication.invoke(null);\n")
                .append("                if (application instanceof Application) {\n")
                .append("                    ApplicationInfo info = ((Application) application).getApplicationInfo();\n")
                .append("                    if (info != null) {\n")
                .append("                        return info;\n")
                .append("                    }\n")
                .append("                }\n")
                .append("            } catch (Throwable ignored) {\n")
                .append("            }\n")
                .append("            Field current = threadClass.getDeclaredField(\"sCurrentActivityThread\");\n")
                .append("            current.setAccessible(true);\n")
                .append("            Object thread = current.get(null);\n")
                .append("            Field bound = threadClass.getDeclaredField(\"mBoundApplication\");\n")
                .append("            bound.setAccessible(true);\n")
                .append("            Object bindData = bound.get(thread);\n")
                .append("            Field infoField = bindData.getClass().getDeclaredField(\"appInfo\");\n")
                .append("            infoField.setAccessible(true);\n")
                .append("            return (ApplicationInfo) infoField.get(bindData);\n")
                .append("        } catch (Throwable failure) {\n")
                .append("            throw new IllegalStateException(\"cannot resolve application info\", failure);\n")
                .append("        }\n")
                .append("    }\n")
                .append("}\n");
        return source.toString();
    }

    private static void appendAction(StringBuilder source, String methodName, String body) {
        source.append("        @Override\n")
                .append("        public void ").append(methodName).append("() {\n")
                .append("            ").append(body.replace("\n", "\n            ")).append("\n")
                .append("        }\n");
    }

    private static ActionNames actionNames(ProfileIdentityLock identity,
                                           ProfileRecipeTemplate template) {
        List<RecipeRuntimeContract.JavaStartupDependency> dependencies =
                template.getRuntimeContract().getJavaStartupDependencies();
        List<RecipeRuntimeContract.JavaStartupDependency> canonical =
                Arrays.asList(RecipeRuntimeContract.JavaStartupDependency.values());
        if (!canonical.equals(dependencies)) {
            throw new IllegalArgumentException("Recipe Java 启动动作顺序不兼容："
                    + template.getRecipeId());
        }
        List<String> methodNames = new ArrayList<>();
        for (RecipeRuntimeContract.JavaStartupDependency dependency : dependencies) {
            methodNames.add(derive(identity, "java-startup-action-" + dependency.name(), "a"));
        }
        return new ActionNames(derive(identity, "java-startup-actions", "A"), methodNames);
    }

    private static String actionsSource(String packageName, ActionNames actions) {
        StringBuilder source = new StringBuilder("package ").append(packageName)
                .append(";\n\ninterface ").append(actions.className).append(" {\n");
        for (String methodName : actions.methodNames) {
            source.append("    void ").append(methodName).append("();\n");
        }
        return source.append("}\n").toString();
    }

    private static Map<String, String> helperSources(String packageName,
                                                     List<HelperNames> helpers,
                                                     ActionNames actions,
                                                     ProfileRecipeTemplate template) {
        Map<String, HelperNames> byUnit = indexHelpers(helpers);
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        if (byUnit.containsKey("startup-delegate")) {
            requireUnits(template, byUnit, "startup-delegate", "library-delegate",
                    "component-delegate");
            HelperNames root = byUnit.get("startup-delegate");
            HelperNames library = byUnit.get("library-delegate");
            HelperNames components = byUnit.get("component-delegate");
            sources.put(root.unit, helperSource(packageName, root, actions,
                    helperCall(library), helperCall(components)));
            sources.put(library.unit, helperSource(packageName, library, actions,
                    actionCalls(actions, 0, 2)));
            sources.put(components.unit, helperSource(packageName, components, actions,
                    actionCalls(actions, 2, actions.methodNames.size())));
            return sources;
        }
        if (byUnit.containsKey("startup-pipeline")) {
            requireUnits(template, byUnit, "startup-pipeline", "library-stage",
                    "restore-stage", "component-router");
            HelperNames root = byUnit.get("startup-pipeline");
            HelperNames library = byUnit.get("library-stage");
            HelperNames restore = byUnit.get("restore-stage");
            HelperNames components = byUnit.get("component-router");
            sources.put(root.unit, helperSource(packageName, root, actions,
                    helperCall(library)));
            sources.put(library.unit, helperSource(packageName, library, actions,
                    concat(actionCalls(actions, 0, 2), helperCall(restore))));
            sources.put(restore.unit, helperSource(packageName, restore, actions,
                    concat(actionCalls(actions, 2, 4), helperCall(components))));
            sources.put(components.unit, helperSource(packageName, components, actions,
                    actionCalls(actions, 4, actions.methodNames.size())));
            return sources;
        }
        throw new IllegalArgumentException("Recipe 缺少受支持的 Java 启动根节点："
                + template.getRecipeId());
    }

    private static Map<String, HelperNames> indexHelpers(List<HelperNames> helpers) {
        LinkedHashMap<String, HelperNames> result = new LinkedHashMap<>();
        for (HelperNames helper : helpers) {
            result.put(helper.unit, helper);
        }
        return result;
    }

    private static HelperNames rootHelper(List<HelperNames> helpers) {
        Map<String, HelperNames> byUnit = indexHelpers(helpers);
        HelperNames root = byUnit.get("startup-delegate");
        if (root == null) {
            root = byUnit.get("startup-pipeline");
        }
        if (root == null) {
            throw new IllegalArgumentException("Recipe 缺少 Java 启动协调根节点");
        }
        return root;
    }

    private static void requireUnits(ProfileRecipeTemplate template,
                                     Map<String, HelperNames> helpers,
                                     String... requiredUnits) {
        Set<String> expected = new HashSet<>(Arrays.asList(requiredUnits));
        if (!helpers.keySet().equals(expected)) {
            throw new IllegalArgumentException("Recipe Java 启动类图不完整："
                    + template.getRecipeId());
        }
    }

    private static String[] actionCalls(ActionNames actions, int from, int to) {
        String[] result = new String[to - from];
        for (int index = from; index < to; index++) {
            result[index - from] = "actions." + actions.methodNames.get(index) + "();";
        }
        return result;
    }

    private static String helperCall(HelperNames helper) {
        return helper.className + "." + helper.methodName + "(actions);";
    }

    private static String[] concat(String[] first, String... remaining) {
        String[] result = Arrays.copyOf(first, first.length + remaining.length);
        System.arraycopy(remaining, 0, result, first.length, remaining.length);
        return result;
    }

    private static String bridgeSource(String packageName, String className,
                                       ProfileIdentityLock identity) {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n")
                .append("public final class ").append(className).append(" {\n")
                .append("    private ").append(className).append("() {\n")
                .append("    }\n\n");
        for (ProfileJniSemantic semantic : ProfileJniSemantic.values()) {
            source.append("    public static native ")
                    .append(javaDeclaration(semantic, identity)).append(";\n");
        }
        return source.append("}\n").toString();
    }

    private static String javaDeclaration(ProfileJniSemantic semantic,
                                          ProfileIdentityLock identity) {
        String methodName = semantic.requireMethodName(identity.getJniMethods());
        switch (semantic) {
            case CALL_REAL_APPLICATION_ON_CREATE:
                return "void " + methodName + "(String value)";
            case INIT_APP:
            case STATIC_INITIALIZE:
                return "void " + methodName + "()";
            case GET_SOURCE_DIR:
            case GET_COMPRESSED_DEXES_PATH:
            case READ_APP_COMPONENT_FACTORY:
            case READ_APPLICATION_NAME:
                return "String " + methodName + "()";
            case COMBINE_DEX_ELEMENTS:
                return "void " + methodName + "(ClassLoader loader)";
            case REMOVE_DEX_ELEMENTS:
                return "void " + methodName + "(ClassLoader loader, String elementName)";
            case REPLACE_APPLICATION:
                return "Object " + methodName + "(String applicationName)";
            default:
                throw new IllegalStateException("unsupported JNI semantic: " + semantic);
        }
    }

    private static String constantsSource(String packageName, String className,
                                          ProfileIdentityLock identity) {
        String[] labels = {"so", "config", "dex", "code", "native-dir", "runtime", "junk"};
        String[] values = {identity.getSoName(), identity.getShellConfigAssetName(),
                identity.getDexAssetName(), identity.getCodeItemAssetName(),
                identity.getNativeLibAssetDirName(), identity.getRuntimeDirName(),
                identity.getJunkClassName()};
        StringBuilder source = new StringBuilder("package ").append(packageName)
                .append(";\n\nfinal class ").append(className).append(" {\n");
        for (int index = 0; index < labels.length; index++) {
            source.append("    static final String ")
                    .append(derive(identity, "java-build-field-" + labels[index], "f"))
                    .append(" = \"").append(values[index]).append("\";\n");
        }
        return source.append("\n    private ").append(className).append("() {\n")
                .append("    }\n}\n").toString();
    }

    private static String helperSource(String packageName, HelperNames helper,
                                       ActionNames actions, String... statements) {
        StringBuilder body = new StringBuilder();
        for (String statement : statements) {
            body.append("        ").append(statement).append("\n");
        }
        return "package " + packageName + ";\n\n"
                + "final class " + helper.className + " {\n"
                + "    private " + helper.className + "() {\n"
                + "    }\n\n"
                + "    static void " + helper.methodName + "(" + actions.className
                + " actions) {\n"
                + body
                + "    }\n"
                + "}\n";
    }

    private static String junkSource(String packageName, String className,
                                     ProfileIdentityLock identity) {
        String fieldName = derive(identity, "java-junk-field", "f");
        String methodName = derive(identity, "java-junk-method", "m");
        return "package " + packageName + ";\n\n"
                + "public final class " + className + " {\n"
                + "    private boolean " + fieldName + ";\n\n"
                + "    public void " + methodName + "() {\n"
                + "        " + fieldName + " = !" + fieldName + ";\n"
                + "    }\n"
                + "}\n";
    }

    private static String r8Rules(ProfileIdentityLock identity) {
        return "-keep class " + identity.getProxyApplicationClassName() + " { *; }\n"
                + "-keep class " + identity.getProxyComponentFactoryClassName() + " { *; }\n"
                + "-keep class " + identity.getJniBridgeClassName() + " {\n"
                + "    native <methods>;\n"
                + "}\n"
                + "-keep class " + identity.getJunkClassName() + " { *; }\n";
    }

    private static void writeSource(ProfileCodeGenerator.GenerationLayout layout,
                                    String className, String content) throws IOException {
        write(layout.getJavaDirectory().resolve(className.replace('.', '/') + ".java"), content);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String derive(ProfileIdentityLock identity, String label, String prefix) {
        return ProfileStableNameDeriver.identifier(identity.getIdentitySeed(), label, prefix, 12);
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static final class HelperNames {
        private final String unit;
        private final String className;
        private final String methodName;

        private HelperNames(String unit, String className, String methodName) {
            this.unit = unit;
            this.className = className;
            this.methodName = methodName;
        }
    }

    private static final class RuntimeNames {
        private final String className;
        private final String startMethod;
        private final String startFromRuntimeMethod;
        private final String restoreMethod;
        private final String contextMethod;
        private final String hidePackageMethod;
        private final String factoryMethod;
        private final String applicationNameMethod;
        private final String startedField;
        private final String preparedField;
        private final String loadedField;
        private final String nativeField;
        private final String loaderField;
        private final String combinedLoaderField;
        private final String sourceField;
        private final String dataField;
        private final String packageField;
        private final String applicationNameField;
        private final String factoryNameField;
        private final String factoryField;
        private final String factoryResolvedField;
        private final String applicationField;
        private final String applicationRestoredField;
        private final String applicationOnCreateField;
        private final String lockField;
        private final String runtimeInfoMethod;
        private final String normalizeMethod;
        private final String libraryRootClass;
        private final String libraryPrepareMethod;
        private final String libraryLoadMethod;

        private RuntimeNames(ProfileIdentityLock identity, ProfileRecipeTemplate template,
                             HelperNames root, ActionNames actions, String className,
                             String startMethod, String startFromRuntimeMethod,
                             String restoreMethod, String contextMethod,
                             String hidePackageMethod, String factoryMethod,
                             String applicationNameMethod, String startedField,
                             String preparedField, String loadedField, String nativeField,
                             String loaderField, String sourceField, String dataField,
                             String packageField, String applicationNameField,
                             String factoryNameField, String factoryField,
                             String factoryResolvedField, String applicationField,
                             String applicationRestoredField, String applicationOnCreateField,
                             String lockField, String runtimeInfoMethod, String normalizeMethod,
                             String libraryRootClass, String libraryPrepareMethod,
                             String libraryLoadMethod) {
            this.className = className;
            this.startMethod = startMethod;
            this.startFromRuntimeMethod = startFromRuntimeMethod;
            this.restoreMethod = restoreMethod;
            this.contextMethod = contextMethod;
            this.hidePackageMethod = hidePackageMethod;
            this.factoryMethod = factoryMethod;
            this.applicationNameMethod = applicationNameMethod;
            this.startedField = startedField;
            this.preparedField = preparedField;
            this.loadedField = loadedField;
            this.nativeField = nativeField;
            this.loaderField = loaderField;
            this.combinedLoaderField = derive(identity, "java-runtime-combined-loader", "f");
            this.sourceField = sourceField;
            this.dataField = dataField;
            this.packageField = packageField;
            this.applicationNameField = applicationNameField;
            this.factoryNameField = factoryNameField;
            this.factoryField = factoryField;
            this.factoryResolvedField = factoryResolvedField;
            this.applicationField = applicationField;
            this.applicationRestoredField = applicationRestoredField;
            this.applicationOnCreateField = applicationOnCreateField;
            this.lockField = lockField;
            this.runtimeInfoMethod = runtimeInfoMethod;
            this.normalizeMethod = normalizeMethod;
            this.libraryRootClass = libraryRootClass;
            this.libraryPrepareMethod = libraryPrepareMethod;
            this.libraryLoadMethod = libraryLoadMethod;
        }

        private static String derive(ProfileIdentityLock identity, String label, String prefix) {
            return ProfileStableNameDeriver.identifier(identity.getIdentitySeed(), label,
                    prefix, 12);
        }
    }

    private static final class ActionNames {
        private final String className;
        private final List<String> methodNames;

        private ActionNames(String className, List<String> methodNames) {
            this.className = className;
            this.methodNames = methodNames;
        }
    }
}
