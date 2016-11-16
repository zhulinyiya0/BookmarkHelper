package pro.kisscat.www.bookmarkhelper.converter.support.impl;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.content.ContextCompat;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import pro.kisscat.www.bookmarkhelper.R;
import pro.kisscat.www.bookmarkhelper.common.shared.MetaData;
import pro.kisscat.www.bookmarkhelper.converter.support.BasicBroswer;
import pro.kisscat.www.bookmarkhelper.converter.support.pojo.Bookmark;
import pro.kisscat.www.bookmarkhelper.database.SQLite.DBHelper;
import pro.kisscat.www.bookmarkhelper.exception.ConverterException;
import pro.kisscat.www.bookmarkhelper.util.Path;
import pro.kisscat.www.bookmarkhelper.util.context.ContextUtil;
import pro.kisscat.www.bookmarkhelper.util.json.JsonUtil;
import pro.kisscat.www.bookmarkhelper.util.log.LogHelper;
import pro.kisscat.www.bookmarkhelper.util.storage.ExternalStorageUtil;

/**
 * Created with Android Studio.
 * Project:BookmarkHelper
 * User:ChengLiang
 * Mail:stevenchengmask@gmail.com
 * Date:2016/1/16
 * Time:17:46
 */

public class BaiduBroswer extends BasicBroswer {
    private static final String TAG = "Baidu";
    private static final String packageName = "com.baidu.browser.apps";
    private final static String[] columns = new String[]{"title", "url", "parent_uuid", "sync_uuid"};
    private List<Bookmark> bookmarks;

    public String getPackageName() {
        return packageName;
    }

    @Override
    public int readBookmarkSum(Context context) {
        if (bookmarks == null) {
            readBookmark(context);
        }
        return bookmarks.size();
    }

    @Override
    public void fillDefaultIcon(Context context) {
        this.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_baidu));
    }

    @Override
    public void fillDefaultAppName(Context context) {
        this.setName(context.getString(R.string.broswer_name_show_baidu));
    }


    private static final String fileName_origin = "dbbrowser.db";
    private static final String filePath_origin = Path.INNER_PATH_DATA + packageName + "/databases/";
    private static final String filePath_cp = Path.SDCARD_ROOTPATH + Path.SDCARD_APP_ROOTPATH + Path.SDCARD_TMP_ROOTPATH + "/Baidu/";

    @Override
    public List<Bookmark> readBookmark(Context context) {
        if (bookmarks != null) {
            LogHelper.v(TAG + ":bookmarks cache is hit.");
            return bookmarks;
        }
        LogHelper.v(TAG + ":bookmarks cache is miss.");
        LogHelper.v(TAG + ":开始读取书签数据");
        try {
            ExternalStorageUtil.mkdir(context, filePath_cp, this.getName());
            List<Bookmark> bookmarksList = fetchBookmarksList(context, filePath_cp, fileName_origin);
            LogHelper.v("书签数据:" + JsonUtil.toJson(bookmarksList));
            LogHelper.v("书签条数:" + bookmarksList.size());
            LogHelper.v("总的书签条数:" + bookmarksList.size());
            bookmarks = new LinkedList<>();
            int index = 0;
            int size = bookmarksList.size();
            for (Bookmark item : bookmarksList) {
                index++;
                String bookmarkUrl = item.getUrl();
                String bookmarkFolder = item.getFolder();
                String bookmarkTitle = item.getTitle();
                if (allowPrintBookmark(index, size)) {
                    LogHelper.v("title:" + bookmarkTitle);
                    LogHelper.v("url:" + bookmarkUrl);
                }
                if (!isValidUrl(bookmarkUrl)) {
                    continue;
                }
                if (bookmarkTitle == null || bookmarkTitle.isEmpty()) {
                    LogHelper.v("url:" + bookmarkTitle + ",set to default value.");
                    bookmarkTitle = MetaData.BOOKMARK_TITLE_DEFAULT;
                }
                Bookmark bookmark = new Bookmark();
                bookmark.setTitle(bookmarkTitle);
                bookmark.setUrl(bookmarkUrl);
                if (!(bookmarkFolder == null || bookmarkFolder.isEmpty())) {
                    bookmark.setFolder(bookmarkFolder);
                }
                bookmarks.add(bookmark);
            }
            LogHelper.v("result:" + JsonUtil.toJson(bookmarks));
        } catch (ConverterException converterException) {
            converterException.printStackTrace();
            LogHelper.e(MetaData.LOG_E_DEFAULT, converterException.getMessage());
            throw converterException;
        } catch (Exception e) {
            e.printStackTrace();
            LogHelper.e(MetaData.LOG_E_DEFAULT, e.getMessage());
            throw new ConverterException(ContextUtil.buildReadBookmarksErrorMessage(context, this.getName()));
        } finally {
            LogHelper.v(TAG + ":读取书签数据结束");
        }
        return bookmarks;
    }

    private List<Bookmark> fetchBookmarksList(Context context, String dir, String fileName) {
        String targetFilePath = dir + fileName;
        LogHelper.v("targetFilePath is:" + targetFilePath);
        List<Bookmark> result = new LinkedList<>();
        try {
            ExternalStorageUtil.copyFile(context, filePath_origin + fileName_origin, targetFilePath, this.getName());
        } catch (Exception e) {
            LogHelper.e(MetaData.LOG_E_DEFAULT, e.getMessage());
            return result;
        }
        result.addAll(fetchBookmarksList(false, context, targetFilePath, "bookmark", null, null, "create_time asc"));
        result.addAll(fetchBookmarksList(false, context, targetFilePath, "homepage", null, null, "create_time asc"));
        result.addAll(fetchBookmarksList(false, context, targetFilePath, "pc_bookmark", null, null, "create_time asc"));
        return result;
    }

    private List<Bookmark> fetchBookmarksList(boolean needThrowException, Context context, String dbFilePath, String tableName, String where, String[] whereArgs, String orderBy) {
        return fetchBookmarksList(needThrowException, context, dbFilePath, tableName, columns, where, whereArgs, orderBy);
    }

    private List<Bookmark> fetchBookmarksList(boolean needThrowException, Context context, String dbFilePath, String tableName, String[] columns, String where, String[] whereArgs, String orderBy) {
        LogHelper.v(TAG + ":读取SQLite数据库开始,dbFilePath:" + dbFilePath + ",tableName:" + tableName);
        List<Bookmark> result = new LinkedList<>();
        SQLiteDatabase sqLiteDatabase = null;
        Cursor cursor = null;
        boolean tableExist;
        try {
            sqLiteDatabase = DBHelper.openReadOnlyDatabase(dbFilePath);
            tableExist = DBHelper.checkTableExist(sqLiteDatabase, tableName);
            if (!tableExist) {
                LogHelper.v(TAG + ":database table " + tableName + " not exist.");
                if (needThrowException) {
                    throw new ConverterException(ContextUtil.buildReadBookmarksTableNotExistErrorMessage(context, this.getName()));
                } else {
                    return result;
                }
            }
            cursor = sqLiteDatabase.query(false, tableName, columns, where, whereArgs, null, null, orderBy, null);
            if (cursor != null && cursor.getCount() > 0) {
                parseBookmarkWithFolder(cursor, result);
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (sqLiteDatabase != null) {
                sqLiteDatabase.close();
            }
            LogHelper.v(TAG + ":读取SQLite数据库结束,dbFilePath:" + dbFilePath);
        }
        return result;
    }

    private void parseBookmarkWithFolder(Cursor cursor, List<Bookmark> result) {
        List<BaiduBookmark> bookmarks = parseBookmark(cursor);
        Map<String, BaiduBookmark> folders = new HashMap<>();
        for (BaiduBookmark item : bookmarks) {
            String url = item.getUrl();
            String sync_uuid = item.getSync_uuid();
            if (url == null || url.isEmpty() && !(sync_uuid == null || sync_uuid.isEmpty())) {
                folders.put(item.getSync_uuid(), item);
            }
        }

        for (BaiduBookmark item : bookmarks) {
            String url = item.getUrl();
            String parent_uuid = item.getParent_uuid();
            if (!(url == null || url.isEmpty())) {
                String folderPath = null;
                if (!(parent_uuid == null || parent_uuid.isEmpty())) {
                    folderPath = trim(parseFolderPath(folders, parent_uuid));
                }
                Bookmark bookmark = new Bookmark();
                bookmark.setTitle(item.getTitle());
                bookmark.setUrl(item.getUrl());
                bookmark.setFolder(folderPath == null ? "" : folderPath);
                result.add(bookmark);
            }
        }
    }

    private String parseFolderPath(Map<String, BaiduBookmark> folders, String parent_uuid) {
        BaiduBookmark parent = folders.get(parent_uuid);
        if (parent == null) {
            return null;
        }
        return parseFolderPath(folders, parent_uuid, "");
    }

    private String parseFolderPath(Map<String, BaiduBookmark> folders, String parent_uuid, String path) {
        BaiduBookmark parent = folders.get(parent_uuid);
        if (parent == null) {
            return path;
        }
        String title = parent.getTitle();
        if (!(title == null || title.isEmpty())) {
            path = title + Path.FILE_SPLIT + path;
        }
        return parseFolderPath(folders, parent.getParent_uuid(), path);
    }

    private List<BaiduBookmark> parseBookmark(Cursor cursor) {
        List<BaiduBookmark> result = new LinkedList<>();
        while (cursor.moveToNext()) {
            BaiduBookmark item = new BaiduBookmark();
            item.setTitle(cursor.getString(cursor.getColumnIndex("title")));
            item.setUrl(cursor.getString(cursor.getColumnIndex("url")));
            item.setParent_uuid(cursor.getString(cursor.getColumnIndex("parent_uuid")));
            item.setSync_uuid(cursor.getString(cursor.getColumnIndex("sync_uuid")));
            result.add(item);
        }
        return result;
    }

    private class BaiduBookmark extends Bookmark {
        @Getter
        @Setter
        private String parent_uuid;
        @Getter
        @Setter
        private String sync_uuid;
    }

    @Override
    public int appendBookmark(Context context, List<Bookmark> bookmarks) {
        return 0;
    }
}