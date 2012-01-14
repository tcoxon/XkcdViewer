package net.bytten.xkcdviewer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;

public class ArchiveData {

    private static final Pattern archiveItemPattern = Pattern.compile(
            // group(1): comic number;   group(2): date;   group(3): title
            "\\s*<a href=\"/(\\d+)/\" title=\"(\\d+-\\d+-\\d+)\">([^<]+)</a><br/>\\s*");
    private static final String ARCHIVE_URL = "http://www.xkcd.com/archive/";

    /* The maximum age the cache is allowed to reach in milliseconds */
    private static final long CACHE_AGE_LIMIT = 60*60*1000; // = 1 hour

    public static class ArchiveItem {
        public boolean bookmarked = false;
        public String title, comicId;

        @Override
        public String toString() {
            return  comicId + " - " + title;
        }
    }

    /* Putting the cache in a SoftReference means the system can still
     * garbage collect it if low on memory. */
    private static SoftReference<List<ArchiveItem>> cache = null;
    private static Date cacheModDate = null;

    public static boolean isCacheValid() {
        return cache != null && cache.get() != null && cacheModDate != null &&
            cacheModDate.before(new Date(cacheModDate.getTime() +
                    CACHE_AGE_LIMIT));
    }

    /* Do NOT call in a UI thread. May block until data has been fetched.
     * Do NOT add or remove elements from the returned list (but bookmarking
     *     may be altered). */
    public static List<ArchiveItem> getData(Context cxt) throws IOException,
        InterruptedException
    {
        if (!isCacheValid())
            fetchData(cxt);
        return cache.get();
    }

    public static void refresh(Context cxt) throws IOException,
        InterruptedException
    {
        fetchData(cxt);
    }

    private static void fetchData(Context cxt) throws IOException,
        InterruptedException
    {
        List<ArchiveItem> archiveItems = new ArrayList<ArchiveItem>();
        URL url = new URL(ARCHIVE_URL);
        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));

        try {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = archiveItemPattern.matcher(line);
                while (m.find()) {
                    ArchiveItem item = new ArchiveItem();
                    item.comicId = m.group(1);
                    item.title = m.group(3);
                    if (BookmarksHelper.isBookmarked(cxt, item))
                        item.bookmarked = true;
                    archiveItems.add(item);
                }

                Utility.allowInterrupt();
            }

            if (cache != null)
                cache.clear();
            cache = new SoftReference<List<ArchiveItem>>(archiveItems);
            cacheModDate = new Date();
        } finally {
            br.close();
        }
    }
}
