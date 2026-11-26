/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.loggers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import app.morphe.extension.twitter.Pref;
import app.morphe.extension.crimera.PikoUtils;

public class ResponseLogger {
    private static final boolean LOG_RES;
    static{
        LOG_RES = Pref.serverResponseLogging();
        if(Pref.serverResponseLoggingOverwriteFile()){
            writeFile("",false);
//            Utils.logger("Cleared response log file!!!");
        }
    }

    public static InputStream saveInputStream(InputStream inputStream)  throws Exception {
        if(!LOG_RES) return inputStream;

        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        sb.append("\n");
        inputStream.close();
        String contentBytes = sb.toString();
        if(!(sb.indexOf("session_token") == 2 || sb.indexOf("guest_token") == 2)){
            writeFile(contentBytes,true);
         }

        return new ByteArrayInputStream(contentBytes.getBytes());
    }

    /**
     * 2026-11-26：12.46.1 移除 Jackson 後沒有 InputStream→JSON 入口；
     * 主要 API（GraphQL）改走 Apollo 的 okio BufferedSource。此方法用
     * peek() 讀全量（不消費原流、不影響後續解析），過濾 token 後寫檔，
     * 並原樣返回 source。okio.m/I() 是 12.46.1 的 R8 混淆名
     * （BufferedSource/readUtf8，見 stub okio/m.java 對照註釋）。
     */
    public static okio.m saveBufferedSource(okio.m source) {
        if(!LOG_RES) return source;
        try {
            String data = source.peek().I() + "\n";
            if(!(data.indexOf("session_token") == 2 || data.indexOf("guest_token") == 2)){
                writeFile(data,true);
            }
        } catch (Throwable t) {
            // 日誌功能絕不能影響正常解析：任何讀取/寫檔失敗都靜默放行。
        }
        return source;
    }

    private static boolean writeFile(String data,boolean append){
        String fileName = "Server-Response-Log.txt";
        return PikoUtils.pikoWriteFile(fileName,data,append);
    }

}