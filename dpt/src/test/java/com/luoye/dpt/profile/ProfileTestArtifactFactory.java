package com.luoye.dpt.profile;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter;
import com.luoye.dpt.profile.model.JniMethodMapping;
import com.luoye.dpt.profile.model.ProfileIdentityLock;
import com.luoye.dpt.profile.model.ProfileRevisionManifest;
import com.luoye.dpt.profile.storage.ProfileBundle;
import com.luoye.dpt.profile.storage.ProfileBundleAssembler;
import com.luoye.dpt.profile.storage.ProfileIdentityStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ProfileTestArtifactFactory {
    private ProfileTestArtifactFactory() {
    }

    public static ProfileBundle assemble(Path profilesRoot, String applicationId,
                                         ProfileRevisionManifest revision, Path inputRoot)
            throws IOException {
        ProfileIdentityLock identity = new ProfileIdentityStore(profilesRoot).loadExisting(
                applicationId, revision.getProfileId());
        Files.createDirectories(inputRoot);
        Path dex = inputRoot.resolve("classes.dex");
        writeShellDex(dex, identity);
        Path nativeDirectory = inputRoot.resolve("native");
        Files.createDirectories(nativeDirectory);
        Files.write(nativeDirectory.resolve(identity.getSoName()), arm64So(identity));
        return new ProfileBundleAssembler(profilesRoot).assemble(identity, revision, dex,
                nativeDirectory);
    }

    private static void writeShellDex(Path output, ProfileIdentityLock identity)
            throws IOException {
        List<ImmutableClassDef> classes = new ArrayList<>();
        classes.add(classDef(identity.getProxyApplicationClassName(), Collections.emptyList()));
        classes.add(classDef(identity.getProxyComponentFactoryClassName(),
                Collections.emptyList()));
        List<ImmutableMethod> nativeMethods = new ArrayList<>();
        for (JniMethodMapping mapping : identity.getJniMethods()) {
            nativeMethods.add(nativeMethod(identity.getJniBridgeClassName(), mapping));
        }
        classes.add(classDef(identity.getJniBridgeClassName(), nativeMethods));
        DexFileFactory.writeDexFile(output.toString(),
                new ImmutableDexFile(Opcodes.getDefault(), classes));
    }

    private static ImmutableClassDef classDef(String className,
                                               List<ImmutableMethod> methods) {
        return new ImmutableClassDef(descriptor(className), AccessFlags.PUBLIC.getValue(),
                "Ljava/lang/Object;", Collections.emptyList(), null, null,
                Collections.emptyList(), methods);
    }

    private static ImmutableMethod nativeMethod(String className, JniMethodMapping mapping) {
        String signature = mapping.getJvmSignature();
        int close = signature.indexOf(')');
        List<ImmutableMethodParameter> parameters = parseParameters(
                signature.substring(1, close));
        return new ImmutableMethod(descriptor(className), mapping.getMethodName(), parameters,
                signature.substring(close + 1), AccessFlags.PUBLIC.getValue()
                | AccessFlags.STATIC.getValue() | AccessFlags.NATIVE.getValue(),
                null, null, null);
    }

    private static List<ImmutableMethodParameter> parseParameters(String descriptor) {
        List<ImmutableMethodParameter> parameters = new ArrayList<>();
        int offset = 0;
        while (offset < descriptor.length()) {
            int end = typeEnd(descriptor, offset);
            parameters.add(new ImmutableMethodParameter(descriptor.substring(offset, end),
                    Collections.emptySet(), null));
            offset = end;
        }
        return parameters;
    }

    private static int typeEnd(String descriptor, int offset) {
        int current = offset;
        while (descriptor.charAt(current) == '[') {
            current++;
        }
        if (descriptor.charAt(current) == 'L') {
            return descriptor.indexOf(';', current) + 1;
        }
        return current + 1;
    }

    private static byte[] arm64So(ProfileIdentityLock identity) {
        byte[] header = new byte[64];
        header[0] = 0x7f;
        header[1] = 'E';
        header[2] = 'L';
        header[3] = 'F';
        header[4] = 2;
        header[5] = 1;
        header[6] = 1;
        header[16] = 3;
        header[18] = (byte) 0xb7;

        StringBuilder contract = new StringBuilder();
        contract.append(identity.getJniBridgeClassName().replace('.', '/')).append('\0');
        for (JniMethodMapping mapping : identity.getJniMethods()) {
            contract.append(mapping.getMethodName()).append('\0')
                    .append(mapping.getJvmSignature()).append('\0');
        }
        byte[] strings = contract.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = Arrays.copyOf(header, header.length + strings.length);
        System.arraycopy(strings, 0, result, header.length, strings.length);
        return result;
    }

    private static String descriptor(String className) {
        return "L" + className.replace('.', '/') + ";";
    }
}
