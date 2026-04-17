package com.deltaaim.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ErrorLogger {
    private static final String TAG = "ErrorLogger";
    private static final String LOG_DIR = "DeltaAim/logs";
    private static ErrorLogger instance;
    
    private File logDirectory;
    private SimpleDateFormat dateFormat;
    
    private ErrorLogger(Context context) {
        File filesDir = context.getExternalFilesDir(null);
        logDirectory = new File(filesDir, "logs");
        if (!logDirectory.exists()) {
            logDirectory.mkdirs();
        }
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    }
    
    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new ErrorLogger(context.getApplicationContext());
        }
    }
    
    public static ErrorLogger getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ErrorLogger not initialized. Call init() first.");
        }
        return instance;
    }
    
    public void logInfo(String tag, String message) {
        Log.i(tag, message);
        writeLog("INFO", tag, message, null);
    }
    
    public void logWarning(String tag, String message) {
        Log.w(tag, message);
        writeLog("WARN", tag, message, null);
    }
    
    public void logError(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
        writeLog("ERROR", tag, message, throwable);
    }
    
    public void logException(String tag, String context, Throwable throwable) {
        Log.e(tag, context, throwable);
        writeLog("EXCEPTION", tag, context, throwable);
    }
    
    private void writeLog(String level, String tag, String message, Throwable throwable) {
        try {
            String timestamp = dateFormat.format(new Date());
            StringBuilder logEntry = new StringBuilder();
            logEntry.append(timestamp).append(" [").append(level).append("] ");
            logEntry.append(tag).append(": ").append(message).append("\n");
            
            if (throwable != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                logEntry.append(sw.toString()).append("\n");
            }
            
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            File logFile = new File(logDirectory, "error_" + dateStr + ".log");
            
            try (FileOutputStream fos = new FileOutputStream(logFile, true)) {
                fos.write(logEntry.toString().getBytes("UTF-8"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to write log", e);
        }
    }
    
    public String getLatestLogFile() {
        File[] files = logDirectory.listFiles((dir, name) -> name.endsWith(".log"));
        if (files == null || files.length == 0) {
            return null;
        }
        
        File latest = files[0];
        for (File f : files) {
            if (f.lastModified() > latest.lastModified()) {
                latest = f;
            }
        }
        return latest.getAbsolutePath();
    }
    
    public String getLogDirectory() {
        return logDirectory.getAbsolutePath();
    }
    
    public void clearOldLogs(int keepDays) {
        File[] files = logDirectory.listFiles((dir, name) -> name.endsWith(".log"));
        if (files == null) return;
        
        long cutoff = System.currentTimeMillis() - (keepDays * 24L * 60 * 60 * 1000);
        for (File f : files) {
            if (f.lastModified() < cutoff) {
                f.delete();
                Log.d(TAG, "Deleted old log: " + f.getName());
            }
        }
    }
}
