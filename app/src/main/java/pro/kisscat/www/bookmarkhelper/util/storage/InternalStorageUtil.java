package pro.kisscat.www.bookmarkhelper.util.storage;

import android.os.Environment;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import pro.kisscat.www.bookmarkhelper.exception.ConverterException;
import pro.kisscat.www.bookmarkhelper.util.Path;
import pro.kisscat.www.bookmarkhelper.util.appList.AppListUtil;
import pro.kisscat.www.bookmarkhelper.entry.command.CommandResult;
import pro.kisscat.www.bookmarkhelper.util.context.ContextUtil;
import pro.kisscat.www.bookmarkhelper.entry.file.File;
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper;
import pro.kisscat.www.bookmarkhelper.util.root.RootUtil;

/**
 * Created with Android Studio.
 * Project:BookmarkHelper
 * User:ChengLiang
 * Mail:stevenchengmask@gmail.com
 * Date:2016/10/9
 * Time:15:01
 */

public final class InternalStorageUtil extends BasicStorageUtil {
    /**
     * 获取内置SD卡路径
     */
    @Override
    public String getRootPath() {
        java.io.File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(Environment.MEDIA_MOUNTED);   //判断sd卡是否存在
        if (sdCardExist) {
            sdDir = Environment.getExternalStorageDirectory();//获取根目录
        }
        return sdDir == null ? null : sdDir.toString();
    }

    /**
     * 删除文件
     * <p>
     * 失败后重试一次
     * <p>
     * 借用root权限
     * <p>
     */
    public static boolean deleteFile(String filePath, String mark) {
        return deleteFile(true, filePath, mark);
    }

    private static boolean deleteFile(boolean needRetry, String filePath, String mark) {
        String cmd = "rm -rf " + filePath;
        boolean result = RootUtil.executeCmd(cmd);
        if (!result) {
            if (needRetry) {
                boolean retry = deleteFile(false, filePath, mark);
                if (!retry) {
                    throw new ConverterException(ContextUtil.buildFileDeleteErrorMessage(mark));
                }
            } else {
                throw new ConverterException(ContextUtil.buildFileDeleteErrorMessage(mark));
            }
        }
        return true;
    }

    /**
     * 列出dir下的所有dir
     */
    public static List<String> lsDir(String dir) {
        try {
            StringBuilder command = new StringBuilder("ls ");
            if (dir == null || dir.isEmpty()) {
                //不指定dir可能造成命令过量输出，refuse
                return null;
            }
            command.append(dir);
            CommandResult commandResult = RootUtil.executeCmd(new String[]{command.toString()}, false);
            if (commandResult != null && commandResult.isSuccess()) {
                return commandResult.getSuccessMsg();
            }
            return null;
        } catch (Exception e) {
            LogHelper.e(e.getMessage());
            return null;
        }
    }

    /**
     * 列出文件夹下，所有文件名称符合regularRule规则的文件。regularRule为null是，列出全部
     * <p>
     * 不能用find、locate等高级命令，android shell bash 自身是一个裁剪版linux,不支持
     * <p>
     * 不能用高级特性，ls -t -au等，不支持
     */
    public static List<String> lsFileAndSortByRegular(String dir, String regularRule) {
        return lsFileByRegular(dir, regularRule, true);
    }

    private static List<String> lsFileByRegular(String dir, String regularRule, boolean needSort) {
        try {
            StringBuilder command = new StringBuilder("ls ");
            if (dir == null || dir.isEmpty()) {
                //不指定dir可能造成命令过量输出，refuse
                return null;
            }
            command.append(dir);
            if (regularRule != null && !regularRule.isEmpty()) {
                command.append(regularRule);
            }
            command.append(" -l");
            CommandResult commandResult = RootUtil.executeCmd(new String[]{command.toString()}, false);
            if (commandResult != null && commandResult.isSuccess()) {
                if (needSort) {
                    return parseFileAndSortByFileChangeTimeDesc(commandResult.getSuccessMsg());
                } else {
                    return parseFile(commandResult.getSuccessMsg());
                }
            }
            return null;
        } catch (Exception e) {
            LogHelper.e(e.getMessage());
            return null;
        }
    }

    private static List<String> parseFile(List<String> fileProperty) {
        if (fileProperty == null || fileProperty.isEmpty()) {
            return null;
        }
        List<File> files = new LinkedList<>();
        for (String item : fileProperty) {
            if (item == null) {
                continue;
            }
            String[] property = item.split(" ");
            if (property.length == 0) {
                continue;
            }
            files.add(new File(property));
        }
        if (files.isEmpty()) {
            return null;
        }
        List<String> result = new LinkedList<>();
        if (files.size() == 1) {
            result.add(files.get(0).getNameWithSuffix());
            return result;
        }
        for (File item : files) {
            result.add(item.getNameWithSuffix());
        }
        return result;
    }

    /**
     * 将文件以修改时间进行倒序排序后返回
     */
    private static List<String> parseFileAndSortByFileChangeTimeDesc(List<String> fileProperty) {
        List<String> files = parseFile(fileProperty);
        Collections.sort(files);
        return files;
    }

    public static boolean remountDataDir() {
        return remountDataDir(false);
    }

    private static boolean isReadOnlyNow = true;

    /**
     * 重新挂载data目录为读写或只读
     */
    private static boolean remountDataDir(boolean isNeedReadOnly) {
        boolean readWriteable = checkReadWriteAble(Path.INNER_PATH_DATA + AppListUtil.thisAppPackageName + Path.FILE_SPLIT);
        LogHelper.v("remountDataDir 读写权限检查结果：" + readWriteable);
        if (readWriteable) {
            return true;
        }
        String command;
        if (isNeedReadOnly) {
//            if (isReadOnlyNow) {
//                LogHelper.v("unnecessary remount.isReadOnlyNow:" + isReadOnlyNow + ",isNeedReadOnly:" + isNeedReadOnly);
//                return;
//            } else {
            command = "mount -o remount, ro /data";
//            }
        } else {
//            if (!isReadOnlyNow) {
//                LogHelper.v("unnecessary remount.isReadOnlyNow:" + isReadOnlyNow + ",isNeedReadOnly:" + isNeedReadOnly);
//                return;
//            } else {
            command = "mount -o remount, rw /data";
//            }
        }
        boolean ret = RootUtil.executeCmd(command);
        if (isNeedReadOnly) {
            if (ret) {
                isReadOnlyNow = true;
            }
        } else {
            if (ret) {
                isReadOnlyNow = false;
            }
        }
        if (!ret) {
            //记录mount信息
            command = "mount";
            RootUtil.executeCmd(command);
            //尝试挂载根目录为读写
            command = "mount -o remount, rw /";
            RootUtil.executeCmd(command);
            return false;
        }
        LogHelper.v("isReadOnlyNow:" + isReadOnlyNow + ",isNeedReadOnly:" + isNeedReadOnly);
        return true;
    }
}
