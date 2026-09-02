package com.luoye.dpt.profile.gate.behavior;

public final class ProfileBehaviorObservation {
    private final ProfileBehaviorCase behaviorCase;
    private final String environmentId;
    private final int sdkInt;
    private final String abi;
    private final boolean applicationPassed;
    private final boolean activityPassed;
    private final boolean servicePassed;
    private final boolean receiverPassed;
    private final boolean providerPassed;
    private final boolean classLoaderPassed;
    private final boolean componentFactoryDelegationPassed;
    private final int crashCount;
    private final int anrCount;

    public ProfileBehaviorObservation(ProfileBehaviorCase behaviorCase, String environmentId,
                                      int sdkInt, String abi, boolean applicationPassed,
                                      boolean activityPassed, boolean servicePassed,
                                      boolean receiverPassed, boolean providerPassed,
                                      boolean classLoaderPassed,
                                      boolean componentFactoryDelegationPassed,
                                      int crashCount, int anrCount) {
        if (behaviorCase == null) {
            throw new IllegalArgumentException("行为矩阵用例不能为空");
        }
        if (environmentId == null || environmentId.trim().isEmpty()) {
            throw new IllegalArgumentException("行为矩阵环境 ID 不能为空");
        }
        if (sdkInt <= 0 || abi == null || abi.trim().isEmpty()) {
            throw new IllegalArgumentException("行为矩阵 SDK 与 ABI 无效");
        }
        if (crashCount < 0 || anrCount < 0) {
            throw new IllegalArgumentException("行为矩阵崩溃和 ANR 次数不能为负数");
        }
        this.behaviorCase = behaviorCase;
        this.environmentId = environmentId.trim();
        this.sdkInt = sdkInt;
        this.abi = abi.trim();
        this.applicationPassed = applicationPassed;
        this.activityPassed = activityPassed;
        this.servicePassed = servicePassed;
        this.receiverPassed = receiverPassed;
        this.providerPassed = providerPassed;
        this.classLoaderPassed = classLoaderPassed;
        this.componentFactoryDelegationPassed = componentFactoryDelegationPassed;
        this.crashCount = crashCount;
        this.anrCount = anrCount;
    }

    public ProfileBehaviorObservation withResults(
            boolean applicationPassed, boolean activityPassed, boolean servicePassed,
            boolean receiverPassed, boolean providerPassed, boolean classLoaderPassed,
            boolean componentFactoryDelegationPassed, int crashCount, int anrCount) {
        return new ProfileBehaviorObservation(behaviorCase, environmentId, sdkInt, abi,
                applicationPassed, activityPassed, servicePassed, receiverPassed,
                providerPassed, classLoaderPassed, componentFactoryDelegationPassed,
                crashCount, anrCount);
    }

    public ProfileBehaviorCase getBehaviorCase() {
        return behaviorCase;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public int getSdkInt() {
        return sdkInt;
    }

    public String getAbi() {
        return abi;
    }

    public boolean isApplicationPassed() {
        return applicationPassed;
    }

    public boolean isActivityPassed() {
        return activityPassed;
    }

    public boolean isServicePassed() {
        return servicePassed;
    }

    public boolean isReceiverPassed() {
        return receiverPassed;
    }

    public boolean isProviderPassed() {
        return providerPassed;
    }

    public boolean isClassLoaderPassed() {
        return classLoaderPassed;
    }

    public boolean isComponentFactoryDelegationPassed() {
        return componentFactoryDelegationPassed;
    }

    public int getCrashCount() {
        return crashCount;
    }

    public int getAnrCount() {
        return anrCount;
    }
}
