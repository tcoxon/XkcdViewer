package net.bytten.xkcdviewer;

public class XkcdViewerActivity extends ComicViewerActivity {

    @Override
    protected IComicDefinition makeComicDef() {
        return new XkcdComicDefinition();
    }

    @Override
    protected Class<? extends ArchiveActivity> getArchiveActivityClass() {
        return XkcdArchiveActivity.class;
    }

}
