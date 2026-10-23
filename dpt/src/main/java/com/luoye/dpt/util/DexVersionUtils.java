package com.luoye.dpt.util;

import com.android.tools.smali.dexlib2.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.zip.Adler32;

/**
 * DEX 版本工具。
 *
 * <p>dpt-shell 有若干处会「读入 DEX 再写出新 DEX」：
 * <ul>
 *   <li>dexlib2 的 {@code DexFileFactory.loadDexFile(...) + writeDexFile(...)}
 *       —— clinit 注入、-K 的 dex 拆分、壳包名重命名</li>
 *   <li>dx 的 {@code DexMerger} —— 混淆 dex 合并</li>
 * </ul>
 *
 * <p>这两条路都会丢失输入 DEX 的版本：
 * <ul>
 *   <li>dexlib2 写出时使用的版本由传入的 {@link Opcodes} 决定，而
 *       {@code Opcodes.getDefault()} 对应 API 20，也就是 <b>DEX 035</b>；</li>
 *   <li>dx 只写 <b>DEX 035</b>。</li>
 * </ul>
 *
 * <p>如果输入 DEX 是 037/038/039（Kotlin 接口默认方法、私有接口方法、
 * invoke-custom 等特性要求），被降级到 035 之后 ART 会拒绝
 * 「对接口方法使用 invoke-direct / invoke-super」，运行时抛：
 * <pre>
 * java.lang.IncompatibleClassChangeError: Found interface
 *     androidx.compose.ui.graphics.drawscope.DrawScope, but class was expected
 * </pre>
 *
 * <p>因此，所有「读入再写出」的路径都必须沿用输入 DEX 自身的版本，
 * 见 {@link #opcodesFor(File)} 与 {@link #restoreDexVersion(File, int)}。
 */
public final class DexVersionUtils {

    /** DEX 魔数 "dex\n035\0" 中版本号起始位置。 */
    public static final int DEX_VERSION_OFFSET = 4;
    /** DEX 魔数长度。 */
    public static final int DEX_MAGIC_SIZE = 8;
    /** DEX header 固定长度（0x70）。 */
    public static final int DEX_HEADER_SIZE = 112;
    /** header 中 file_size 字段偏移。 */
    private static final int FILE_SIZE_OFFSET = 32;
    /** header 中 checksum 字段偏移。 */
    private static final int CHECKSUM_OFFSET = 8;
    /** header 中 signature 字段偏移。 */
    private static final int SIGNATURE_OFFSET = 12;
    /** signature 长度（SHA-1）。 */
    private static final int SIGNATURE_SIZE = 20;

    private DexVersionUtils() {
    }

    /**
     * 读取 DEX 魔数里的版本号。
     *
     * @return 35 / 37 / 38 / 39 等；读不到时返回 {@link #DEFAULT_VERSION}
     */
    public static int readDexVersion(File dexFile) {
        try (RandomAccessFile raf = new RandomAccessFile(dexFile, "r")) {
            byte[] magic = new byte[DEX_MAGIC_SIZE];
            if (raf.read(magic) != DEX_MAGIC_SIZE) {
                return DEFAULT_VERSION;
            }
            // "dex\n" 之后三位数字
            if (magic[0] != 'd' || magic[1] != 'e' || magic[2] != 'x' || magic[3] != '\n') {
                return DEFAULT_VERSION;
            }
            String ver = new String(magic, DEX_VERSION_OFFSET, 3, java.nio.charset.StandardCharsets.US_ASCII);
            return Integer.parseInt(ver);
        } catch (Exception e) {
            LogUtils.warn("read dex version failed for %s: %s", dexFile.getName(), e);
            return DEFAULT_VERSION;
        }
    }

    /** 读不到版本时使用的最小版本。 */
    public static final int DEFAULT_VERSION = 35;

    /**
     * 返回与输入 DEX 版本匹配的 {@link Opcodes}。
     *
     * <p>用这个替代 {@code Opcodes.getDefault()}，否则写出的 DEX 会被降级到 035。
     */
    public static Opcodes opcodesFor(File dexFile) {
        return opcodesFor(readDexVersion(dexFile));
    }

    /** 按版本号构造 {@link Opcodes}。 */
    public static Opcodes opcodesFor(int dexVersion) {
        // DEX 版本号与 API level 的对应关系：
        //   035 -> API 20 及以下（默认）
        //   037 -> API 24 (Android 7.0)，接口默认方法
        //   038 -> API 26 (Android 8.0)，invoke-custom
        //   039 -> API 28 (Android 9.0)，const-method-handle 等
        switch (dexVersion) {
            case 39:
                return Opcodes.forApi(28);
            case 38:
                return Opcodes.forApi(26);
            case 37:
                return Opcodes.forApi(24);
            case 35:
            default:
                return Opcodes.getDefault();
        }
    }

    /**
     * 把写出后的 DEX 版本恢复成原值，并重算 checksum 与 signature。
     *
     * <p>dexlib2 / dx 写出的 DEX 版本被强制成 035，这里改回原版本。
     * 改完必须重算 header 里的 SHA-1 signature（覆盖 32 字节之后全部内容）
     * 与 Adler32 checksum（覆盖 12 字节之后全部内容），否则 ART 会拒绝加载。
     *
     * @param dexFile 已写出的 DEX 文件
     * @param version 原始版本号，见 {@link #readDexVersion(File)}
     */
    public static void restoreDexVersion(File dexFile, int version) {
        if (version == DEFAULT_VERSION) {
            // 本来就是 035，不需要处理
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(dexFile, "rw")) {
            long len = raf.length();
            if (len < DEX_HEADER_SIZE) {
                LogUtils.warn("dex too short, skip version restore: %s", dexFile.getName());
                return;
            }

            // 1) 改版本号
            String ver = String.format(Locale.US, "%03d", version);
            raf.seek(DEX_VERSION_OFFSET);
            raf.write(ver.getBytes(java.nio.charset.StandardCharsets.US_ASCII));

            // 2) 重算 signature（SHA-1，覆盖 [32, len)）
            byte[] body = new byte[(int) (len - SIGNATURE_OFFSET - SIGNATURE_SIZE)];
            raf.seek(SIGNATURE_OFFSET + SIGNATURE_SIZE);
            raf.readFully(body);
            byte[] sha1 = sha1(body);
            raf.seek(SIGNATURE_OFFSET);
            raf.write(sha1);

            // 3) 重算 checksum（Adler32，覆盖 [12, len)）
            byte[] afterSig = new byte[(int) (len - CHECKSUM_OFFSET - 4)];
            raf.seek(CHECKSUM_OFFSET + 4);
            raf.readFully(afterSig);
            Adler32 adler = new Adler32();
            adler.update(afterSig);
            long sum = adler.getValue();
            raf.seek(CHECKSUM_OFFSET);
            // 小端序
            raf.write(new byte[]{
                    (byte) (sum & 0xff),
                    (byte) ((sum >> 8) & 0xff),
                    (byte) ((sum >> 16) & 0xff),
                    (byte) ((sum >> 24) & 0xff),
            });
        } catch (IOException e) {
            // 不抛受检异常：调用方（splitDex / renamePackageName / mergeDex）
            // 都不在能处理 IOException 的位置，而且版本恢复失败不该让整个加固流程失败。
            LogUtils.warn("restore dex version failed for %s: %s", dexFile.getName(), e);
        }
    }

    private static byte[] sha1(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
