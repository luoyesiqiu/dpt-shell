package com.luoye.dpt.profile.gate.scan;

import com.alibaba.fastjson2.JSON;
import com.luoye.dpt.profile.model.ProfileSchemaVersions;
import com.luoye.dpt.profile.validation.ProfileModelValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Codec and strict validation for manually exported scan evidence. */
public final class ProfileScanReportCodec {
    private ProfileScanReportCodec() {
    }

    public static ProfileScanReport read(Path path) throws IOException {
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("扫描报告不存在或不是普通文件：" + path);
        }
        byte[] content = Files.readAllBytes(path);
        try {
            return deserialize(content);
        } catch (RuntimeException exception) {
            throw new IOException("扫描报告无法解析：" + path, exception);
        }
    }

    public static void write(Path path, ProfileScanReport report) throws IOException {
        if (path == null) {
            throw new IOException("扫描报告输出路径不能为空");
        }
        byte[] content = serialize(report);
        Path target = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(target)) {
            throw new IOException("扫描报告输出路径不得为符号链接：" + target);
        }
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".scan-report.", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                throw new IOException("扫描报告不支持原子替换：" + target, exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static byte[] serialize(ProfileScanReport report) {
        validate(report);
        return JSON.toJSONBytes(report);
    }

    public static ProfileScanReport deserialize(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("扫描报告内容不能为空");
        }
        try {
            ProfileScanReport report = JSON.parseObject(content, ProfileScanReport.class);
            validate(report);
            return report;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("扫描报告无法解析", exception);
        }
    }

    public static void validate(ProfileScanReport report) {
        if (report == null) {
            throw new IllegalArgumentException("扫描报告不能为空");
        }
        if (report.getSchemaVersion() != ProfileSchemaVersions.SCAN_REPORT) {
            throw new IllegalArgumentException("扫描报告版本不支持："
                    + report.getSchemaVersion());
        }
        ProfileModelValidator.validateApplicationId(report.getApplicationId());
        requireSha256("APK", report.getApkSha256());
        requirePositive("生成时间", report.getGeneratedAtEpochMillis());
        requireText("扫描时间窗口 ID", report.getWindowId());
        requirePositive("扫描窗口开始时间", report.getWindowStartEpochMillis());
        requirePositive("扫描窗口结束时间", report.getWindowEndEpochMillis());
        if (report.getWindowEndEpochMillis() < report.getWindowStartEpochMillis()) {
            throw new IllegalArgumentException("扫描时间窗口结束时间早于开始时间");
        }
        if (report.getGeneratedAtEpochMillis() < report.getWindowStartEpochMillis()
                || report.getGeneratedAtEpochMillis() > report.getWindowEndEpochMillis()) {
            throw new IllegalArgumentException("扫描报告生成时间不在声明的扫描窗口内");
        }
        Map<String, String> scanners = report.getScannerSet();
        if (scanners == null || scanners.isEmpty()) {
            throw new IllegalArgumentException("扫描器集合不能为空");
        }
        for (Map.Entry<String, String> entry : scanners.entrySet()) {
            requireText("扫描器 ID", entry.getKey());
            requireText("扫描器版本", entry.getValue());
        }
        List<ProfileScanReport.Detection> detections = report.getDetections();
        if (detections == null) {
            throw new IllegalArgumentException("扫描检测列表不能为空");
        }
        for (ProfileScanReport.Detection detection : detections) {
            if (detection == null) {
                throw new IllegalArgumentException("扫描检测项不能为 null");
            }
            requireText("检测项扫描器 ID", detection.getScannerId());
            if (!scanners.containsKey(detection.getScannerId())) {
                throw new IllegalArgumentException("检测项引用了未登记的扫描器："
                        + detection.getScannerId());
            }
            if (blank(detection.getSignature()) && blank(detection.getFamily())
                    && blank(detection.getCategory())) {
                throw new IllegalArgumentException("检测项至少需要 signature、family 或 category");
            }
        }
    }

    private static void requireSha256(String label, String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " SHA-256 格式无效");
        }
    }

    private static void requirePositive(String label, long value) {
        if (value <= 0L) {
            throw new IllegalArgumentException(label + "必须大于 0");
        }
    }

    private static void requireText(String label, String value) {
        if (blank(value)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
