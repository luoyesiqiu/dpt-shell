package com.luoye.dpt.config;

import com.luoye.dpt.util.FileUtils;
import com.luoye.dpt.util.IoUtils;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class ReadOnlyConfig {
    private static ReadOnlyConfig instance;

    public String getShellPackageName() {
        return shellPackageName;
    }

    public String getSoName() {
        return soName;
    }

    private final String shellPackageName;
    private final String soName;

    private ReadOnlyConfig() {
        String path = FileUtils.getExecutablePath() + File.separator + "shell-files" + File.separator + "readonly-config.json";
        byte[] bytes = IoUtils.readFile(path);
        JSONObject jsonObject = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        this.shellPackageName = jsonObject.optString("shellPackageName", "");
        this.soName = jsonObject.optString("soName", "");
    }


    public static ReadOnlyConfig getInstance() {
        if(instance == null) {
            instance = new ReadOnlyConfig();
        }
        return instance;
    }
}
