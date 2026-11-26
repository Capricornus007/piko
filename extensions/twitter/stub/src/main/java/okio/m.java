/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package okio;

/**
 * 12.46.1-prod 的 okio 類名被 R8 混淆：BufferedSource → okio.m（包名保留、
 * 類名/方法名混淆，jadx okio/m.java 實錘）。此 stub 僅供 extensions 編譯
 * （compileOnly），運行時由 APK 內的真 okio.m 提供。
 *
 * 方法名對照（12.46.1 實測）：
 * - peek() → q0 peek()（返回實現類 q0）
 * - readUtf8() → String I()（無參返回 String，標準 okio BufferedSource.readUtf8）
 */
public interface m {
    q0 peek() throws java.io.EOFException;

    String I() throws java.io.EOFException;
}
