package com.luoye.dpt.profile.gate.behavior;

public enum ProfileBehaviorVariant {
    NO_CUSTOM(false, false),
    APPLICATION_ONLY(true, false),
    COMPONENT_FACTORY_ONLY(false, true),
    APPLICATION_AND_FACTORY(true, true);

    private final boolean customApplication;
    private final boolean customComponentFactory;

    ProfileBehaviorVariant(boolean customApplication, boolean customComponentFactory) {
        this.customApplication = customApplication;
        this.customComponentFactory = customComponentFactory;
    }

    public boolean hasCustomApplication() {
        return customApplication;
    }

    public boolean hasCustomComponentFactory() {
        return customComponentFactory;
    }
}
