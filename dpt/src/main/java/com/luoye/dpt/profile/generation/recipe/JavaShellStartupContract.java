package com.luoye.dpt.profile.generation.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class JavaShellStartupContract {
    public enum EntryPoint {
        LEGACY_ATTACH_BASE_CONTEXT,
        LEGACY_ON_CREATE,
        LEGACY_CREATE_PACKAGE_CONTEXT,
        FACTORY_INSTANTIATE_CLASS_LOADER,
        FACTORY_INSTANTIATE_APPLICATION,
        FACTORY_INSTANTIATE_ACTIVITY,
        FACTORY_INSTANTIATE_SERVICE,
        FACTORY_INSTANTIATE_RECEIVER,
        FACTORY_INSTANTIATE_PROVIDER
    }

    public enum Operation {
        CALL_FRAMEWORK_SUPER,
        REQUIRE_APPLICATION_INFO,
        EXTRACT_OR_REUSE_SHELL_SO,
        LOAD_SHELL_SO,
        INITIALIZE_NATIVE_RUNTIME,
        RESTORE_DEX_AND_REPLACE_CLASS_LOADER,
        MARK_CLASS_LOADER_REPLACED,
        READ_ORIGINAL_APPLICATION_NAME,
        CREATE_AND_INSTALL_ORIGINAL_APPLICATION,
        CALL_ORIGINAL_APPLICATION_ON_CREATE,
        MARK_ORIGINAL_APPLICATION_RESTORED,
        RETURN_ORIGINAL_APPLICATION_CONTEXT,
        RESOLVE_ORIGINAL_COMPONENT_FACTORY,
        DISABLE_LEGACY_APPLICATION_RESTORE,
        SELECT_ORIGINAL_APPLICATION_CLASS,
        DELEGATE_TO_ORIGINAL_COMPONENT_FACTORY,
        FALL_BACK_TO_FRAMEWORK
    }

    public enum Condition {
        ALWAYS,
        CLASS_LOADER_NOT_REPLACED,
        ORIGINAL_APPLICATION_DECLARED,
        ORIGINAL_APPLICATION_NOT_DECLARED,
        ORIGINAL_APPLICATION_DECLARED_AND_PENDING,
        ORIGINAL_COMPONENT_FACTORY_AVAILABLE,
        ORIGINAL_COMPONENT_FACTORY_UNAVAILABLE_OR_FAILED
    }

    private static final String VERSION = "java-shell-startup-v1";
    private static final JavaShellStartupContract IMMUTABLE_V1 = createV1();

    private final String version;
    private final Map<EntryPoint, List<StartupStep>> stepsByEntryPoint;

    private JavaShellStartupContract(String version,
                                     Map<EntryPoint, List<StartupStep>> stepsByEntryPoint) {
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("Java 启动契约版本不能为空");
        }
        if (stepsByEntryPoint == null
                || stepsByEntryPoint.size() != EntryPoint.values().length) {
            throw new IllegalArgumentException("Java 启动契约必须覆盖全部入口");
        }
        EnumMap<EntryPoint, List<StartupStep>> copied = new EnumMap<>(EntryPoint.class);
        for (EntryPoint entryPoint : EntryPoint.values()) {
            List<StartupStep> steps = stepsByEntryPoint.get(entryPoint);
            if (steps == null || steps.isEmpty()) {
                throw new IllegalArgumentException("Java 启动入口缺少步骤：" + entryPoint);
            }
            copied.put(entryPoint,
                    Collections.unmodifiableList(new ArrayList<>(steps)));
        }
        this.version = version.trim();
        this.stepsByEntryPoint = Collections.unmodifiableMap(copied);
    }

    public static JavaShellStartupContract immutableV1() {
        return IMMUTABLE_V1;
    }

    public String getVersion() {
        return version;
    }

    public List<StartupStep> getSteps(EntryPoint entryPoint) {
        if (entryPoint == null) {
            throw new IllegalArgumentException("Java 启动入口不能为空");
        }
        return stepsByEntryPoint.get(entryPoint);
    }

    private static JavaShellStartupContract createV1() {
        EnumMap<EntryPoint, List<StartupStep>> paths = new EnumMap<>(EntryPoint.class);
        paths.put(EntryPoint.LEGACY_ATTACH_BASE_CONTEXT, steps(
                step(Operation.CALL_FRAMEWORK_SUPER, Condition.ALWAYS),
                step(Operation.REQUIRE_APPLICATION_INFO,
                        Condition.CLASS_LOADER_NOT_REPLACED),
                step(Operation.EXTRACT_OR_REUSE_SHELL_SO,
                        Condition.CLASS_LOADER_NOT_REPLACED),
                step(Operation.LOAD_SHELL_SO, Condition.CLASS_LOADER_NOT_REPLACED),
                step(Operation.INITIALIZE_NATIVE_RUNTIME,
                        Condition.CLASS_LOADER_NOT_REPLACED),
                step(Operation.RESTORE_DEX_AND_REPLACE_CLASS_LOADER,
                        Condition.CLASS_LOADER_NOT_REPLACED),
                step(Operation.MARK_CLASS_LOADER_REPLACED,
                        Condition.CLASS_LOADER_NOT_REPLACED),
                step(Operation.READ_ORIGINAL_APPLICATION_NAME, Condition.ALWAYS)));

        List<StartupStep> restoreApplication = steps(
                step(Operation.CREATE_AND_INSTALL_ORIGINAL_APPLICATION,
                        Condition.ORIGINAL_APPLICATION_DECLARED_AND_PENDING),
                step(Operation.CALL_ORIGINAL_APPLICATION_ON_CREATE,
                        Condition.ORIGINAL_APPLICATION_DECLARED_AND_PENDING),
                step(Operation.MARK_ORIGINAL_APPLICATION_RESTORED,
                        Condition.ORIGINAL_APPLICATION_DECLARED_AND_PENDING));
        paths.put(EntryPoint.LEGACY_ON_CREATE, prepend(
                step(Operation.CALL_FRAMEWORK_SUPER, Condition.ALWAYS), restoreApplication));
        paths.put(EntryPoint.LEGACY_CREATE_PACKAGE_CONTEXT, append(restoreApplication,
                step(Operation.RETURN_ORIGINAL_APPLICATION_CONTEXT,
                        Condition.ORIGINAL_APPLICATION_DECLARED),
                step(Operation.FALL_BACK_TO_FRAMEWORK,
                        Condition.ORIGINAL_APPLICATION_NOT_DECLARED)));

        paths.put(EntryPoint.FACTORY_INSTANTIATE_CLASS_LOADER, steps(
                step(Operation.EXTRACT_OR_REUSE_SHELL_SO, Condition.ALWAYS),
                step(Operation.LOAD_SHELL_SO, Condition.ALWAYS),
                step(Operation.INITIALIZE_NATIVE_RUNTIME, Condition.ALWAYS),
                step(Operation.RESOLVE_ORIGINAL_COMPONENT_FACTORY, Condition.ALWAYS),
                step(Operation.RESTORE_DEX_AND_REPLACE_CLASS_LOADER, Condition.ALWAYS),
                step(Operation.MARK_CLASS_LOADER_REPLACED, Condition.ALWAYS),
                step(Operation.DELEGATE_TO_ORIGINAL_COMPONENT_FACTORY,
                        Condition.ORIGINAL_COMPONENT_FACTORY_AVAILABLE),
                step(Operation.FALL_BACK_TO_FRAMEWORK,
                        Condition.ORIGINAL_COMPONENT_FACTORY_UNAVAILABLE_OR_FAILED)));

        paths.put(EntryPoint.FACTORY_INSTANTIATE_APPLICATION, steps(
                step(Operation.REQUIRE_APPLICATION_INFO,
                        Condition.CLASS_LOADER_NOT_REPLACED),
                step(Operation.EXTRACT_OR_REUSE_SHELL_SO,
                        Condition.CLASS_LOADER_NOT_REPLACED),
                step(Operation.LOAD_SHELL_SO, Condition.CLASS_LOADER_NOT_REPLACED),
                step(Operation.INITIALIZE_NATIVE_RUNTIME, Condition.ALWAYS),
                step(Operation.READ_ORIGINAL_APPLICATION_NAME, Condition.ALWAYS),
                step(Operation.RESTORE_DEX_AND_REPLACE_CLASS_LOADER,
                        Condition.CLASS_LOADER_NOT_REPLACED),
                step(Operation.MARK_CLASS_LOADER_REPLACED,
                        Condition.CLASS_LOADER_NOT_REPLACED),
                step(Operation.RESOLVE_ORIGINAL_COMPONENT_FACTORY, Condition.ALWAYS),
                step(Operation.DISABLE_LEGACY_APPLICATION_RESTORE, Condition.ALWAYS),
                step(Operation.SELECT_ORIGINAL_APPLICATION_CLASS, Condition.ALWAYS),
                step(Operation.DELEGATE_TO_ORIGINAL_COMPONENT_FACTORY,
                        Condition.ORIGINAL_COMPONENT_FACTORY_AVAILABLE),
                step(Operation.FALL_BACK_TO_FRAMEWORK,
                        Condition.ORIGINAL_COMPONENT_FACTORY_UNAVAILABLE_OR_FAILED)));

        List<StartupStep> componentDelegation = steps(
                step(Operation.RESOLVE_ORIGINAL_COMPONENT_FACTORY, Condition.ALWAYS),
                step(Operation.DELEGATE_TO_ORIGINAL_COMPONENT_FACTORY,
                        Condition.ORIGINAL_COMPONENT_FACTORY_AVAILABLE),
                step(Operation.FALL_BACK_TO_FRAMEWORK,
                        Condition.ORIGINAL_COMPONENT_FACTORY_UNAVAILABLE_OR_FAILED));
        paths.put(EntryPoint.FACTORY_INSTANTIATE_ACTIVITY, componentDelegation);
        paths.put(EntryPoint.FACTORY_INSTANTIATE_SERVICE, componentDelegation);
        paths.put(EntryPoint.FACTORY_INSTANTIATE_RECEIVER, componentDelegation);
        paths.put(EntryPoint.FACTORY_INSTANTIATE_PROVIDER, componentDelegation);
        return new JavaShellStartupContract(VERSION, paths);
    }

    private static StartupStep step(Operation operation, Condition condition) {
        return StartupStep.of(operation, condition);
    }

    private static List<StartupStep> steps(StartupStep... steps) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(steps)));
    }

    private static List<StartupStep> prepend(StartupStep first, List<StartupStep> remaining) {
        ArrayList<StartupStep> result = new ArrayList<>(remaining.size() + 1);
        result.add(first);
        result.addAll(remaining);
        return Collections.unmodifiableList(result);
    }

    private static List<StartupStep> append(List<StartupStep> first,
                                            StartupStep... remaining) {
        ArrayList<StartupStep> result = new ArrayList<>(first.size() + remaining.length);
        result.addAll(first);
        result.addAll(Arrays.asList(remaining));
        return Collections.unmodifiableList(result);
    }

    public static final class StartupStep {
        private final Operation operation;
        private final Condition condition;

        private StartupStep(Operation operation, Condition condition) {
            this.operation = Objects.requireNonNull(operation, "operation");
            this.condition = Objects.requireNonNull(condition, "condition");
        }

        public static StartupStep of(Operation operation, Condition condition) {
            return new StartupStep(operation, condition);
        }

        public Operation getOperation() {
            return operation;
        }

        public Condition getCondition() {
            return condition;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StartupStep)) {
                return false;
            }
            StartupStep that = (StartupStep) other;
            return operation == that.operation && condition == that.condition;
        }

        @Override
        public int hashCode() {
            return Objects.hash(operation, condition);
        }

        @Override
        public String toString() {
            return operation + "@" + condition;
        }
    }
}
