package org.exthmui.microlauncher.duoqin;

import android.app.Application;

import org.exthmui.microlauncher.duoqin.utils.BuglyUtils;

/**
 * @author Maribel
 */
public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        BuglyUtils.initBugly(this);
    }
}
