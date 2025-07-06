package com.luoye.dpt.config;

public class ProtectRules {

    private static final ProtectRules INSTANCE = new ProtectRules();

    /* The following are the rules for classes that do not extract */
    private static String[] excludeRules = {
            "Landroid/.*",
            "Landroidx/.*",
            "Lcom/squareup/okhttp/.*",
            "Lokio/.*", "Lokhttp3/.*",
            "Lkotlin/.*",
            "Lkotlinx/.*",
            "Lcom/google/.*",
            "Lrx/.*",
            "Lorg/apache/.*",
            "Lretrofit2/.*",
            "Lcom/alibaba/.*",
            "Lcom/alipay/.*",
            "Lcom/amap/api/.*",
            "Lcom/sina/weibo/sdk/.*",
            "Lcom/xiaomi/.*",
            "Lcom/huawei/.*",
            "Lcom/vivo/.*",
            "Lcom/baytedance/.*",
            "Lcom/eclipsesource/.*",
            "Lcom/blankj/utilcode/.*",
            "Lcom/umeng/.*",
            "Ljavax/.*",
            "Lorg/slf4j/.*"
    };
    private ProtectRules() {
        //ignored
    }

    public static ProtectRules getsInstance() {
        return INSTANCE;
    }

    public synchronized void setExcludeRules(String[] rules) {
        excludeRules = rules;
    }

    public synchronized boolean matchRules(String fullClassDefName) {
        for(String rule : excludeRules) {
            if(fullClassDefName.matches(rule)){
                return true;
            }
        }
        return false;
    }

}
