package net.bytten.xkcdviewer;

import java.util.ArrayList;
import java.util.List;

import net.bytten.xkcdviewer.ArchiveData.ArchiveItem;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class BookmarksHelper extends SQLiteOpenHelper {

    public static final String DB_NAME    = "bookmarks";
    public static final int    DB_VERSION = 1;

    public BookmarksHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bookmarks (number TEXT, title TEXT);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    protected static SQLiteDatabase dbInstance = null;
    public static SQLiteDatabase getDb(Context cxt) {
        if (dbInstance == null) {
            dbInstance = new BookmarksHelper(cxt).getWritableDatabase();
        }
        return dbInstance;
    }

    public static List<ArchiveItem> getBookmarks(Context cxt) {
        final Cursor results = getDb(cxt).rawQuery("SELECT * FROM bookmarks", new String[]{});
        try {
            List<ArchiveItem> list = new ArrayList<ArchiveItem>();
            if (results.getCount() > 0) {
                results.moveToNext();
                while (!results.isAfterLast()) {
                    ArchiveItem item = new ArchiveItem();
                    item.comicId = results.getString(0);
                    item.title = results.getString(1);
                    item.bookmarked = true;
                    list.add(item);
                    results.moveToNext();
                }
            }
            return list;
        } finally {
            results.close();
        }
    }
    public static boolean isBookmarked(Context cxt, String comicNumber) {
        final Cursor results = getDb(cxt).rawQuery(
                "SELECT * FROM bookmarks WHERE number = ?",
                new String[]{comicNumber});
        try {
            return results.getCount() > 0;
        } finally {
            results.close();
        }
    }
    public static boolean isBookmarked(Context cxt, ArchiveItem item) {
        return isBookmarked(cxt, item.comicId);
    }
    public static void addBookmark(Context cxt, String comicNumber, String title) {
        getDb(cxt).execSQL(
                "INSERT INTO bookmarks VALUES (?, ?)",
                new Object[]{comicNumber, title});
    }
    public static void addBookmark(Context cxt, ArchiveItem item) {
        addBookmark(cxt, item.comicId, item.title);
    }
    public static void removeBookmark(Context cxt, String comicNumber) {
        getDb(cxt).execSQL(
                "DELETE FROM bookmarks WHERE number = ?",
                new Object[]{comicNumber});
    }
    public static void removeBookmark(Context cxt, ArchiveItem item) {
        removeBookmark(cxt, item.comicId);
    }
}
