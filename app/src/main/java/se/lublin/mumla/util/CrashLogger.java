package se.lublin.mumla.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashLogger implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashLogger";
    private static final String LOG_DIR = "ST12_CrashLogs";
    private final Thread.UncaughtExceptionHandler mDefaultHandler;
    private final Context mContext;

    public CrashLogger(Context context) {
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        mContext = context.getApplicationContext();
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        // Tulis log crash ke file
        writeCrashLog(t, e);
        
        // Serahkan ke handler default agar sistem tetap menampilkan dialog crash
        if (mDefaultHandler != null) {
            mDefaultHandler.uncaughtException(t, e);
        } else {
            // Jika tidak ada default handler, kill process secara manual
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }

    private void writeCrashLog(Thread thread, Throwable throwable) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), LOG_DIR);
            if (!dir.exists()) dir.mkdirs();

            String fileName = "crash_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt";
            File logFile = new File(dir, fileName);

            FileWriter writer = new FileWriter(logFile);
            writer.write("=== ST12 CRASH REPORT ===\n");
            writer.write("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "\n");
            writer.write("Thread: " + thread.getName() + " (" + thread.getId() + ")\n");
            writer.write("Device: " + android.os.Build.MODEL + " (SDK " + android.os.Build.VERSION.SDK_INT + ")\n\n");
            
            // Tulis stack trace lengkap
            java.io.PrintWriter pw = new java.io.PrintWriter(writer);
            throwable.printStackTrace(pw);
            pw.flush();
            writer.close();

            Log.e(TAG, "Crash log saved to: " + logFile.getAbsolutePath());
        } catch (IOException ex) {
            Log.e(TAG, "Failed to write crash log", ex);
        }
    }
}