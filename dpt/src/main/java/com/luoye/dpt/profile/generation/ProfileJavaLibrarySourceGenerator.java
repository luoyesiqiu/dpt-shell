package com.luoye.dpt.profile.generation;

import com.luoye.dpt.profile.generation.recipe.ProfileRecipeRegistry;
import com.luoye.dpt.profile.generation.recipe.ProfileRecipeTemplate;
import com.luoye.dpt.profile.model.ProfileIdentityLock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ProfileJavaLibrarySourceGenerator {

    void generate(ProfileCodeGenerator.GenerationLayout layout, ProfileIdentityLock identity,
                  ProfileRecipeTemplate template) throws IOException {
        if (layout == null || identity == null || template == null) {
            throw new IllegalArgumentException("library generation arguments must not be null");
        }
        LibraryNames names = new LibraryNames(identity, template);
        if (ProfileRecipeRegistry.RECIPE_A.equals(template.getRecipeId())) {
            generateDelegated(layout, identity, names);
            return;
        }
        if (ProfileRecipeRegistry.RECIPE_B.equals(template.getRecipeId())) {
            generateStaged(layout, identity, names);
            return;
        }
        throw new IllegalArgumentException("Recipe 缺少 Java SO 组织："
                + template.getRecipeId());
    }

    private static void generateDelegated(ProfileCodeGenerator.GenerationLayout layout,
                                          ProfileIdentityLock identity,
                                          LibraryNames names) throws IOException {
        String locator = names.support("locator");
        String extractor = names.support("extractor");
        String permission = names.support("permission");
        String loader = names.support("loader");
        writeSource(layout, identity, names.root,
                delegatedRootSource(identity, names, locator, extractor, permission, loader));
        writeSource(layout, identity, locator, delegatedLocatorSource(identity, locator, names));
        writeSource(layout, identity, extractor,
                delegatedExtractorSource(extractor, names));
        writeSource(layout, identity, permission,
                delegatedPermissionSource(permission, names));
        writeSource(layout, identity, loader,
                delegatedLoaderSource(identity, loader, names));
    }

    private static String delegatedRootSource(ProfileIdentityLock identity, LibraryNames names,
                                              String locator, String extractor,
                                              String permission, String loader) {
        return "package " + identity.getShellPackageName() + ";\n\n"
                + "import java.io.File;\n\n"
                + "final class " + names.root + " {\n"
                + constants(identity)
                + "\n    private " + names.root + "() {\n"
                + "    }\n\n"
                + "    static void " + names.prepareMethod
                + "(String sourceDir, String dataDir) {\n"
                + "        String abi = " + locator + "." + names.abiMethod + "();\n"
                + "        File directory = " + locator + "." + names.directoryMethod
                + "(dataDir, " + names.runtimeField + ", abi);\n"
                + "        File target = new File(directory, " + names.soField + ");\n"
                + "        " + extractor + "." + names.transferMethod + "(sourceDir, "
                + names.entryPrefixField + " + abi + \"/\" + " + names.soField
                + ", target);\n"
                + "        " + permission + "." + names.permissionMethod + "(target);\n"
                + "    }\n\n"
                + "    static void " + names.loadMethod + "(String dataDir) {\n"
                + "        String abi = " + locator + "." + names.abiMethod + "();\n"
                + "        File directory = " + locator + "." + names.directoryMethod
                + "(dataDir, " + names.runtimeField + ", abi);\n"
                + "        " + loader + "." + names.loaderMethod + "(directory);\n"
                + "    }\n"
                + "}\n";
    }

    private static String delegatedLocatorSource(ProfileIdentityLock identity, String className,
                                                 LibraryNames names) {
        return "package " + identity.getShellPackageName() + ";\n\n"
                + "import java.io.File;\n"
                + "import java.lang.reflect.Method;\n\n"
                + "final class " + className + " {\n"
                + "    private " + className + "() {\n"
                + "    }\n\n"
                + abiMethod(names)
                + "\n    static File " + names.directoryMethod
                + "(String dataDir, String runtimeDir, String abi) {\n"
                + "        return new File(dataDir + File.separator + runtimeDir"
                + " + File.separator + abi);\n"
                + "    }\n"
                + "}\n";
    }

    private static String delegatedExtractorSource(String className, LibraryNames names) {
        return "package " + names.packageName + ";\n\n"
                + "import java.io.BufferedInputStream;\n"
                + "import java.io.BufferedOutputStream;\n"
                + "import java.io.File;\n"
                + "import java.io.FileInputStream;\n"
                + "import java.io.FileOutputStream;\n"
                + "import java.util.Enumeration;\n"
                + "import java.util.zip.CRC32;\n"
                + "import java.util.zip.CheckedInputStream;\n"
                + "import java.util.zip.ZipEntry;\n"
                + "import java.util.zip.ZipFile;\n\n"
                + "final class " + className + " {\n"
                + "    private " + className + "() {\n"
                + "    }\n\n"
                + "    static void " + names.transferMethod
                + "(String sourceDir, String entryName, File target) {\n"
                + "        File parent = target.getParentFile();\n"
                + "        if (!parent.exists()) {\n"
                + "            parent.mkdirs();\n"
                + "        }\n"
                + "        long localCrc = target.exists() ? " + names.crcMethod
                + "(target) : 0L;\n"
                + "        try (ZipFile zip = new ZipFile(sourceDir)) {\n"
                + "            Enumeration<? extends ZipEntry> entries = zip.entries();\n"
                + "            while (entries.hasMoreElements()) {\n"
                + "                ZipEntry entry = entries.nextElement();\n"
                + "                if (!entryName.equals(entry.getName())) {\n"
                + "                    continue;\n"
                + "                }\n"
                + "                if (localCrc != entry.getCrc()) {\n"
                + "                    if (target.exists()) {\n"
                + "                        target.setWritable(true);\n"
                + "                    }\n"
                + copyBlock("target", "zip", "entry", 20)
                + "                }\n"
                + "                break;\n"
                + "            }\n"
                + "        } catch (Exception ignored) {\n"
                + "        }\n"
                + "    }\n\n"
                + crcMethod(names)
                + "}\n";
    }

    private static String delegatedPermissionSource(String className, LibraryNames names) {
        return "package " + names.packageName + ";\n\n"
                + "import android.system.ErrnoException;\n"
                + "import android.system.Os;\n\n"
                + "import java.io.File;\n\n"
                + "final class " + className + " {\n"
                + "    private " + className + "() {\n"
                + "    }\n\n"
                + permissionMethod(names, "target")
                + "}\n";
    }

    private static String delegatedLoaderSource(ProfileIdentityLock identity, String className,
                                                LibraryNames names) {
        return "package " + names.packageName + ";\n\n"
                + "import java.io.File;\n\n"
                + "final class " + className + " {\n"
                + "    private " + className + "() {\n"
                + "    }\n\n"
                + "    static void " + names.loaderMethod + "(File directory) {\n"
                + loadBody("directory", identity.getSoName(), 8)
                + "    }\n"
                + "}\n";
    }

    private static void generateStaged(ProfileCodeGenerator.GenerationLayout layout,
                                       ProfileIdentityLock identity,
                                       LibraryNames names) throws IOException {
        String plan = names.support("plan");
        String transfer = names.support("transfer-stage");
        String loader = names.support("load-stage");
        writeSource(layout, identity, names.root,
                stagedRootSource(identity, names, plan, transfer, loader));
        writeSource(layout, identity, plan, stagedPlanSource(identity, plan, names));
        writeSource(layout, identity, transfer,
                stagedTransferSource(transfer, plan, names));
        writeSource(layout, identity, loader,
                stagedLoaderSource(identity, loader, plan, names));
    }

    private static String stagedRootSource(ProfileIdentityLock identity, LibraryNames names,
                                           String plan, String transfer, String loader) {
        return "package " + identity.getShellPackageName() + ";\n\n"
                + "final class " + names.root + " {\n"
                + constants(identity)
                + "\n    private " + names.root + "() {\n"
                + "    }\n\n"
                + "    static void " + names.prepareMethod
                + "(String sourceDir, String dataDir) {\n"
                + "        " + plan + " value = " + plan + "." + names.planMethod
                + "(dataDir, " + names.entryPrefixField + ", " + names.runtimeField
                + ", " + names.soField + ");\n"
                + "        " + transfer + "." + names.transferMethod
                + "(sourceDir, value);\n"
                + "    }\n\n"
                + "    static void " + names.loadMethod + "(String dataDir) {\n"
                + "        " + plan + " value = " + plan + "." + names.planMethod
                + "(dataDir, " + names.entryPrefixField + ", " + names.runtimeField
                + ", " + names.soField + ");\n"
                + "        " + loader + "." + names.loaderMethod + "(value);\n"
                + "    }\n"
                + "}\n";
    }

    private static String stagedPlanSource(ProfileIdentityLock identity, String className,
                                           LibraryNames names) {
        return "package " + names.packageName + ";\n\n"
                + "import java.io.File;\n"
                + "import java.lang.reflect.Method;\n\n"
                + "final class " + className + " {\n"
                + "    final String " + names.planEntryField + ";\n"
                + "    final File " + names.planDirectoryField + ";\n"
                + "    final File " + names.planTargetField + ";\n\n"
                + "    private " + className + "(String entryName, File directory,"
                + " File target) {\n"
                + "        this." + names.planEntryField + " = entryName;\n"
                + "        this." + names.planDirectoryField + " = directory;\n"
                + "        this." + names.planTargetField + " = target;\n"
                + "    }\n\n"
                + "    static " + className + " " + names.planMethod
                + "(String dataDir, String entryPrefix, String runtimeDir, String soName) {\n"
                + "        String abi = " + names.abiMethod + "();\n"
                + "        File directory = new File(dataDir + File.separator + runtimeDir"
                + " + File.separator + abi);\n"
                + "        return new " + className + "(entryPrefix + abi + \"/\" + soName,"
                + " directory, new File(directory, soName));\n"
                + "    }\n\n"
                + abiMethod(names)
                + "}\n";
    }

    private static String stagedTransferSource(String className, String plan,
                                               LibraryNames names) {
        return "package " + names.packageName + ";\n\n"
                + "import android.system.ErrnoException;\n"
                + "import android.system.Os;\n\n"
                + "import java.io.BufferedInputStream;\n"
                + "import java.io.BufferedOutputStream;\n"
                + "import java.io.File;\n"
                + "import java.io.FileInputStream;\n"
                + "import java.io.FileOutputStream;\n"
                + "import java.util.zip.CRC32;\n"
                + "import java.util.zip.CheckedInputStream;\n"
                + "import java.util.zip.ZipEntry;\n"
                + "import java.util.zip.ZipFile;\n\n"
                + "final class " + className + " {\n"
                + "    private " + className + "() {\n"
                + "    }\n\n"
                + "    static void " + names.transferMethod + "(String sourceDir, "
                + plan + " plan) {\n"
                + "        if (!plan." + names.planDirectoryField + ".exists()) {\n"
                + "            plan." + names.planDirectoryField + ".mkdirs();\n"
                + "        }\n"
                + "        long localCrc = plan." + names.planTargetField + ".exists() ? "
                + names.crcMethod + "(plan." + names.planTargetField + ") : 0L;\n"
                + "        try (ZipFile zip = new ZipFile(sourceDir)) {\n"
                + "            ZipEntry entry = zip.getEntry(plan." + names.planEntryField
                + ");\n"
                + "            if (entry != null && localCrc != entry.getCrc()) {\n"
                + "                if (plan." + names.planTargetField + ".exists()) {\n"
                + "                    plan." + names.planTargetField
                + ".setWritable(true);\n"
                + "                }\n"
                + copyBlock("plan." + names.planTargetField, "zip", "entry", 16)
                + "            }\n"
                + "        } catch (Exception ignored) {\n"
                + "        } finally {\n"
                + "            " + names.permissionMethod + "(plan."
                + names.planTargetField + ");\n"
                + "        }\n"
                + "    }\n\n"
                + crcMethod(names)
                + "\n"
                + permissionMethod(names, "target")
                + "}\n";
    }

    private static String stagedLoaderSource(ProfileIdentityLock identity, String className,
                                             String plan, LibraryNames names) {
        return "package " + names.packageName + ";\n\n"
                + "import java.io.File;\n\n"
                + "final class " + className + " {\n"
                + "    private " + className + "() {\n"
                + "    }\n\n"
                + "    static void " + names.loaderMethod + "(" + plan + " plan) {\n"
                + loadBody("plan." + names.planDirectoryField, identity.getSoName(), 8)
                + "    }\n"
                + "}\n";
    }

    private static String constants(ProfileIdentityLock identity) {
        return "    private static final String "
                + field(identity, "entry-prefix") + " = \"assets/"
                + identity.getNativeLibAssetDirName() + "/\";\n"
                + "    private static final String " + field(identity, "runtime-dir")
                + " = \"" + identity.getRuntimeDirName() + "\";\n"
                + "    private static final String " + field(identity, "so-name")
                + " = \"" + identity.getSoName() + "\";\n";
    }

    private static String abiMethod(LibraryNames names) {
        return "    static String " + names.abiMethod + "() {\n"
                + "        try {\n"
                + "            Class<?> runtimeClass = Class.forName(\"dalvik.system.VMRuntime\");\n"
                + "            Method getRuntime = runtimeClass.getDeclaredMethod(\"getRuntime\");\n"
                + "            Object runtime = getRuntime.invoke(null);\n"
                + "            Method instructionSet = runtimeClass.getDeclaredMethod("
                + "\"vmInstructionSet\");\n"
                + "            return (String) instructionSet.invoke(runtime);\n"
                + "        } catch (Exception ignored) {\n"
                + "        }\n"
                + "        return \"arm64\";\n"
                + "    }\n";
    }

    private static String crcMethod(LibraryNames names) {
        return "    private static long " + names.crcMethod + "(File file) {\n"
                + "        try (FileInputStream input = new FileInputStream(file);\n"
                + "             CheckedInputStream checked = new CheckedInputStream(input,"
                + " new CRC32())) {\n"
                + "            byte[] buffer = new byte[4096];\n"
                + "            while (checked.read(buffer) != -1) {\n"
                + "            }\n"
                + "            return checked.getChecksum().getValue();\n"
                + "        } catch (Throwable ignored) {\n"
                + "            return 0L;\n"
                + "        }\n"
                + "    }\n";
    }

    private static String permissionMethod(LibraryNames names, String targetName) {
        return "    static void " + names.permissionMethod + "(File " + targetName
                + ") {\n"
                + "        if (!" + targetName + ".exists()) {\n"
                + "            return;\n"
                + "        }\n"
                + "        try {\n"
                + "            Os.chmod(" + targetName + ".getAbsolutePath(), 0444);\n"
                + "        } catch (ErrnoException ignored) {\n"
                + "            " + targetName + ".setReadable(true, false);\n"
                + "            " + targetName + ".setWritable(false, false);\n"
                + "        }\n"
                + "    }\n";
    }

    private static String copyBlock(String target, String zip, String entry, int spaces) {
        String indent = " ".repeat(spaces);
        return indent + "try (FileOutputStream output = new FileOutputStream(" + target + ");\n"
                + indent + "     BufferedOutputStream bufferedOutput ="
                + " new BufferedOutputStream(output);\n"
                + indent + "     BufferedInputStream bufferedInput = new BufferedInputStream("
                + zip + ".getInputStream(" + entry + "))) {\n"
                + indent + "    byte[] buffer = new byte[4096];\n"
                + indent + "    int length;\n"
                + indent + "    while ((length = bufferedInput.read(buffer)) != -1) {\n"
                + indent + "        bufferedOutput.write(buffer, 0, length);\n"
                + indent + "    }\n"
                + indent + "}\n";
    }

    private static String loadBody(String directory, String soName, int spaces) {
        String indent = " ".repeat(spaces);
        return indent + "try {\n"
                + indent + "    File[] files = " + directory + ".listFiles();\n"
                + indent + "    if (files != null) {\n"
                + indent + "        for (File file : files) {\n"
                + indent + "            String path = file.getAbsolutePath();\n"
                + indent + "            if (path.endsWith(File.separator + \"" + soName
                + "\")) {\n"
                + indent + "                System.load(path);\n"
                + indent + "            }\n"
                + indent + "        }\n"
                + indent + "    }\n"
                + indent + "} catch (Throwable ignored) {\n"
                + indent + "}\n";
    }

    private static void writeSource(ProfileCodeGenerator.GenerationLayout layout,
                                    ProfileIdentityLock identity, String simpleClassName,
                                    String source) throws IOException {
        Path path = layout.getJavaDirectory().resolve(
                identity.getShellPackageName().replace('.', '/'))
                .resolve(simpleClassName + ".java");
        Files.createDirectories(path.getParent());
        Files.writeString(path, source, StandardCharsets.UTF_8);
    }

    private static String field(ProfileIdentityLock identity, String label) {
        return ProfileStableNameDeriver.identifier(identity.getIdentitySeed(),
                "java-library-field-" + label, "f", 12);
    }

    private static final class LibraryNames {
        private final ProfileIdentityLock identity;
        private final ProfileRecipeTemplate template;
        private final String packageName;
        private final String root;
        private final String prepareMethod;
        private final String loadMethod;
        private final String abiMethod;
        private final String directoryMethod;
        private final String transferMethod;
        private final String permissionMethod;
        private final String loaderMethod;
        private final String crcMethod;
        private final String planMethod;
        private final String entryPrefixField;
        private final String runtimeField;
        private final String soField;
        private final String planEntryField;
        private final String planDirectoryField;
        private final String planTargetField;

        private LibraryNames(ProfileIdentityLock identity, ProfileRecipeTemplate template) {
            this.identity = identity;
            this.template = template;
            this.packageName = identity.getShellPackageName();
            this.root = derive("java-library-root-" + template.getRecipeId(), "L");
            this.prepareMethod = derive("java-library-prepare", "m");
            this.loadMethod = derive("java-library-load", "m");
            this.abiMethod = derive("java-library-abi", "m");
            this.directoryMethod = derive("java-library-directory", "m");
            this.transferMethod = derive("java-library-transfer", "m");
            this.permissionMethod = derive("java-library-permission", "m");
            this.loaderMethod = derive("java-library-loader", "m");
            this.crcMethod = derive("java-library-crc", "m");
            this.planMethod = derive("java-library-plan", "m");
            this.entryPrefixField = field(identity, "entry-prefix");
            this.runtimeField = field(identity, "runtime-dir");
            this.soField = field(identity, "so-name");
            this.planEntryField = derive("java-library-plan-entry", "f");
            this.planDirectoryField = derive("java-library-plan-directory", "f");
            this.planTargetField = derive("java-library-plan-target", "f");
        }

        private String support(String unit) {
            return derive("java-library-class-" + template.getRecipeId() + "-" + unit, "L");
        }

        private String derive(String label, String prefix) {
            return ProfileStableNameDeriver.identifier(identity.getIdentitySeed(), label,
                    prefix, 12);
        }
    }
}
