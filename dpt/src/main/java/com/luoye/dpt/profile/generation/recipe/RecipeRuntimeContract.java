package com.luoye.dpt.profile.generation.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RecipeRuntimeContract {
    public enum JavaStartupDependency {
        EXTRACT_OR_REUSE_SHELL_SO,
        LOAD_SHELL_SO,
        INITIALIZE_NATIVE_RUNTIME,
        RESTORE_DEX_AND_REPLACE_CLASS_LOADER,
        RESOLVE_ORIGINAL_COMPONENT_FACTORY,
        RESTORE_ORIGINAL_APPLICATION
    }

    public enum NativeOnLoadDependency {
        READ_PROFILE_CONFIGURATION,
        RUN_EXISTING_RISK_CHECKS,
        REGISTER_PROFILE_JNI
    }

    public enum ConfigurationOutcome {
        JNI_MAPPING_FROM_PROFILE,
        ASSET_PATHS_FROM_PROFILE,
        RUNTIME_DIRECTORY_FROM_PROFILE,
        ORIGINAL_COMPONENTS_FROM_CONFIG
    }

    public enum FailureSemantic {
        SO_PREPARATION_RETAINS_EXISTING_TOLERANCE,
        REQUIRED_NATIVE_INITIALIZATION_STOPS_STARTUP,
        JNI_ONLOAD_FAILURE_RETURNS_ERROR,
        OPTIONAL_COMPONENT_DELEGATION_FALLS_BACK
    }

    private static final RecipeRuntimeContract IMMUTABLE_SHELL_V1 = new RecipeRuntimeContract(
            JavaShellStartupContract.immutableV1(),
            Arrays.asList(JavaStartupDependency.values()),
            Arrays.asList(NativeOnLoadDependency.values()),
            Arrays.asList(ConfigurationOutcome.values()),
            Arrays.asList(FailureSemantic.values()));

    private final JavaShellStartupContract javaStartupContract;
    private final List<JavaStartupDependency> javaStartupDependencies;
    private final List<NativeOnLoadDependency> nativeOnLoadDependencies;
    private final List<ConfigurationOutcome> configurationOutcomes;
    private final List<FailureSemantic> failureSemantics;

    private RecipeRuntimeContract(JavaShellStartupContract javaStartupContract,
                                  List<JavaStartupDependency> javaStartupDependencies,
                                  List<NativeOnLoadDependency> nativeOnLoadDependencies,
                                  List<ConfigurationOutcome> configurationOutcomes,
                                  List<FailureSemantic> failureSemantics) {
        this.javaStartupContract = Objects.requireNonNull(javaStartupContract,
                "javaStartupContract");
        this.javaStartupDependencies = immutableCopy(javaStartupDependencies);
        this.nativeOnLoadDependencies = immutableCopy(nativeOnLoadDependencies);
        this.configurationOutcomes = immutableCopy(configurationOutcomes);
        this.failureSemantics = immutableCopy(failureSemantics);
    }

    public static RecipeRuntimeContract immutableShellV1() {
        return IMMUTABLE_SHELL_V1;
    }

    public JavaShellStartupContract getJavaStartupContract() {
        return javaStartupContract;
    }

    public List<JavaStartupDependency> getJavaStartupDependencies() {
        return javaStartupDependencies;
    }

    public List<NativeOnLoadDependency> getNativeOnLoadDependencies() {
        return nativeOnLoadDependencies;
    }

    public List<ConfigurationOutcome> getConfigurationOutcomes() {
        return configurationOutcomes;
    }

    public List<FailureSemantic> getFailureSemantics() {
        return failureSemantics;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RecipeRuntimeContract)) {
            return false;
        }
        RecipeRuntimeContract that = (RecipeRuntimeContract) other;
        return javaStartupContract.equals(that.javaStartupContract)
                && javaStartupDependencies.equals(that.javaStartupDependencies)
                && nativeOnLoadDependencies.equals(that.nativeOnLoadDependencies)
                && configurationOutcomes.equals(that.configurationOutcomes)
                && failureSemantics.equals(that.failureSemantics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(javaStartupContract, javaStartupDependencies, nativeOnLoadDependencies,
                configurationOutcomes, failureSemantics);
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
