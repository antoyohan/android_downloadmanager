package com.media.downloadmanager.application;

import android.content.Context;

public class ApplicationClass {

    private static Context sContext ;

    public static void setContext(Context sContext) {
        ApplicationClass.sContext = sContext;
    }

    public static Context getContext() {
        return sContext;
    }
}
