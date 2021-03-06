package pro.kisscat.www.bookmarkhelper.util.log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

import pro.kisscat.www.bookmarkhelper.BuildConfig;
import pro.kisscat.www.bookmarkhelper.common.shared.MetaData;
import pro.kisscat.www.bookmarkhelper.util.Path;
import pro.kisscat.www.bookmarkhelper.entry.log.LogEntry;
import pro.kisscat.www.bookmarkhelper.util.storage.ExternalStorageUtil;
import pro.kisscat.www.bookmarkhelper.util.storage.InternalStorageUtil;

/**
 * Created with Android Studio.
 * Project:BookmarkHelper
 * User:ChengLiang
 * Mail:stevenchengmask@gmail.com
 * Date:2016/10/11
 * Time:14:18
 */

public class LogHelper {
    private static String LOG_DIR = Path.SDCARD_APP_ROOTPATH + Path.SDCARD_LOG_ROOTPATH;// 日志聚集的目录名
    private static String MYLOG_PATH_SDCARD_DIR = null;// 日志文件在sdcard中的路径
    private static int SDCARD_LOG_FILE_SAVE_DAYS = 30;// sd卡中日志文件的最多保存天数
    private static String MYLOGFILEName = "Log.txt";// 本类输出的日志文件名称
    private static SimpleDateFormat myLogSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");// 日志的输出格式
    private static SimpleDateFormat logfile = new SimpleDateFormat("yyyy-MM-dd");// 日志文件格式

    private static ConcurrentLinkedQueue<LogEntry> logQueue = new ConcurrentLinkedQueue<>();

    public static void v(String msg) {
        v(MetaData.LOG_V_DEFAULT, msg);
    }

    public static void w(Object msg) { // 警告信息
        log(MetaData.LOG_W_DEFAULT, msg.toString(), 'w');
    }

    public static void w(String tag, Object msg) { // 警告信息
        log(tag, msg.toString(), 'w');
    }

    public static void e(Throwable throwable) { // 错误信息
        e(MetaData.LOG_E_DEFAULT, printException(throwable));
    }

    public static void e(String tag, Throwable throwable) { // 错误信息
        log(tag, printException(throwable), 'e');
    }

    private static String printException(Throwable throwable) {
        String msg = null;
        Writer result = null;
        PrintWriter printWriter = null;
        try {
            result = new StringWriter();
            printWriter = new PrintWriter(result);
            throwable.printStackTrace(printWriter);
            msg = result.toString();
        } finally {
            if (printWriter != null) {
                printWriter.flush();
                printWriter.close();
            }
            if (result != null) {
                try {
                    result.flush();
                    result.close();
                } catch (IOException e) {
                    LogHelper.e("printException finally IOException:" + e.getMessage());
                    LogHelper.write();
                    e.printStackTrace();
                }
            }
        }
        return msg == null ? "" : msg;
    }

    public static void d(String tag, Object msg) {// 调试信息
        log(tag, msg.toString(), 'd');
    }

    public static void i(String tag, Object msg) {//
        log(tag, msg.toString(), 'i');
    }

    public static void v(String tag, Object msg) {
        v(tag, msg.toString(), true);
    }

    public static void v(String msg, boolean trim) {
        v(MetaData.LOG_V_DEFAULT, msg, trim);
    }

    public static void w(String tag, String text) {
        log(tag, text, 'w');
    }

    public static void e(String tag, String text) {
        log(tag, text, 'e');
    }

    public static void e(String text) {
        e(MetaData.LOG_E_DEFAULT, text);
    }

    public static void d(String tag, String text) {
        log(tag, text, 'd');
    }

    public static void i(String tag, String text) {
        log(tag, text, 'i');
    }

    public static void v(String tag, String text, boolean trim) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (!BuildConfig.DEBUG && trim && text.length() > 1024) {
            text = text.substring(0, 1024);
            text += "...";
        }
        if (BuildConfig.DEBUG) {
            System.out.println("v    " + tag + "    " + text);
        } else {
            log(tag, text, 'v');
        }
    }

    /**
     * 根据tag, msg和等级，输出日志
     */
    private static void log(String tag, String msg, char level) {
        recordLogToQueue(String.valueOf(level), tag, msg);
    }

    public static void write() {
        if (!logQueue.isEmpty() && !WriteThread.isWriteThreadRuning) {//监察写线程是否工作中，没有 则创建
            new WriteThread().start();
        }
    }

    private static void recordLogToQueue(String level, String tag, String text) {
        logQueue.add(new LogEntry(level, tag, text));
        write();
    }

    /**
     * 打开日志文件并写入日志
     **/
    synchronized static void flush() {// 新建或打开日志文件
        if (logQueue.isEmpty()) {
            return;
        }
        if (!isInit()) {
            init();
            if (!isInit()) {
                System.out.println("LogHelper init not work.");
                return;
            }
        }
        FileWriter filerWriter = null;
        BufferedWriter bufWriter = null;
        try {
            while (!logQueue.isEmpty()) {
                LogEntry logEntry = logQueue.poll();
                if (logEntry == null) {
                    break;
                }
                Date recordTime = logEntry.getTime();
                String needWriteFile = logfile.format(recordTime);
                String needWriteMessage = myLogSdf.format(recordTime) + "    " + logEntry.getLevel() + "    " + logEntry.getTag() + "    " + logEntry.getText();
                File dir = new File(MYLOG_PATH_SDCARD_DIR);
                dir.mkdirs();
                File file = new File(MYLOG_PATH_SDCARD_DIR, needWriteFile + MYLOGFILEName);
                if (!file.exists()) {
                    if (file.createNewFile()) {
                        file.setReadable(true);
                        file.setWritable(true);
                    } else {
                        file.createNewFile();
                    }
                    filerWriter = new FileWriter(file, true);//后面这个参数代表是不是要接上文件中原来的数据，不进行覆盖
                    bufWriter = new BufferedWriter(filerWriter);
                }
                if (filerWriter == null) {
                    filerWriter = new FileWriter(file, true);
                    bufWriter = new BufferedWriter(filerWriter);
                }
                bufWriter.write(needWriteMessage);
                bufWriter.newLine();
            }
            if (bufWriter != null) {
                bufWriter.flush();
                bufWriter.close();
            }
            if (filerWriter != null) {
                filerWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bufWriter != null) {
                try {
                    bufWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (filerWriter != null) {
                try {
                    filerWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 删除指定的日志文件
     */
    public static void delFile() {// 删除日志文件
        String needDelFiel = logfile.format(getDateBefore());
        File file = new File(MYLOG_PATH_SDCARD_DIR, needDelFiel + MYLOGFILEName);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 得到现在时间前的几天日期，用来得到需要删除的日志文件名
     */
    private static Date getDateBefore() {
        Date nowtime = new Date();
        Calendar now = Calendar.getInstance();
        now.setTime(nowtime);
        now.set(Calendar.DATE, now.get(Calendar.DATE) - SDCARD_LOG_FILE_SAVE_DAYS);
        return now.getTime();
    }

    private static boolean isSuccessInit = false;

    private static boolean isInit() {
        return isSuccessInit;
    }


    public static void init() {
        String exPath = new ExternalStorageUtil().getRootPath();
        String inPath = new InternalStorageUtil().getRootPath();
        if (exPath != null && !exPath.isEmpty()) {
            Path.SDCARD_ROOTPATH = exPath;
        } else if (inPath != null && !inPath.isEmpty()) {
            Path.SDCARD_ROOTPATH = inPath;
        } else {
            throw new IllegalArgumentException("无法获取日志目录");
        }
        MYLOG_PATH_SDCARD_DIR = Path.SDCARD_ROOTPATH + LOG_DIR;
        File appDirectory = new File(MYLOG_PATH_SDCARD_DIR);
        if (!appDirectory.exists()) {
            appDirectory.mkdir();
        }
        isSuccessInit = true;
    }
}
