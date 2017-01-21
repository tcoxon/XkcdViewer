package net.bytten.comicviewer;

import java.lang.ref.SoftReference;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;

public class ArchiveData {

    private static final Map<String, SoftReference<ArchiveData>> archives
        = new HashMap<String, SoftReference<ArchiveData>>();

    /* The maximum age the cache is allowed to reach in milliseconds */
    private static final long CACHE_AGE_LIMIT = 60*60*1000; // = 1 hour

    public static class ArchiveItem {
        public boolean bookmarked = false;
        public String title, comicId;

        @Override
        public String toString() {
            return  title;
        }
    }

    /* Putting the cache in a SoftReference means the system can still
     * garbage collect it if low on memory. */
    private SoftReference<List<ArchiveItem>> cache = null;
    private Date cacheModDate = null;
    private IComicDefinition comicDef;

    private ArchiveData(IComicDefinition comicDef) {
        this.comicDef = comicDef;
    }
    
    public static ArchiveData getArchive(IComicDefinition def) {
        SoftReference<ArchiveData> archRef = archives.get(def.getClass().getName());
        if (archRef == null || archRef.get() == null) {
            archRef = new SoftReference<ArchiveData>(new ArchiveData(def));
            archives.put(def.getClass().getName(), archRef);
        }
        archRef.get().comicDef = def;
        return archRef.get();
    }
    
    public boolean isCacheValid() {
        return cache != null && cache.get() != null && cacheModDate != null &&
            cacheModDate.before(new Date(cacheModDate.getTime() +
                    CACHE_AGE_LIMIT));
    }

    /* Do NOT call in a UI thread. May block until data has been fetched.
     * Do NOT add or remove elements from the returned list (but bookmarking
     *     may be altered). */
    public List<ArchiveItem> getData(Context cxt) throws Exception {
        List<ArchiveItem> cacheVal = cache == null ? null : cache.get();
        if (!isCacheValid())
            return fetchData(cxt);
        return cacheVal;
    }

    public void refresh(Context cxt) throws Exception {
        fetchData(cxt);
    }

    private List<ArchiveItem> fetchData(Context cxt) throws Exception {
        List<ArchiveItem> archiveItems = comicDef.getProvider().fetchArchive();
        
        for (ArchiveItem item: archiveItems) {
            if (BookmarksHelper.isBookmarked(cxt, item)) {
                item.bookmarked = true;
            }
        }

        if (cache != null)
            cache.clear();
        cache = new SoftReference<List<ArchiveItem>>(archiveItems);
        cacheModDate = new Date();
        return archiveItems;
    }
}
