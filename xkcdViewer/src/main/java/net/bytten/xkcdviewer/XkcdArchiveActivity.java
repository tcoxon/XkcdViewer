package net.bytten.xkcdviewer;

import net.bytten.comicviewer.ArchiveActivity;
import net.bytten.comicviewer.IComicDefinition;

public class XkcdArchiveActivity extends ArchiveActivity {

    @Override
    protected IComicDefinition makeComicDef() {
        return new XkcdComicDefinition();
    }

    @Override
    protected String getStringArchive() {
        return getResources().getString(R.string.app_archive_label);
    }

    @Override
    protected String getStringBookmarks() {
        return getResources().getString(R.string.app_bookmarks_label);
    }

    @Override
    protected String getStringSearchByTitle() {
        return getResources().getString(R.string.app_search_title_label);
    }

}
