package net.bytten.xkcdviewer;

import android.os.Build;

public class VersionHacks {
    //
    // Because Android 1.5 does not have android.os.Build.SDK_INT, we have to simulate
    // for 1.5 devices, but string parsing is annoying, so we will use SDK_INT if it is
    // available.
    // CREDIT: These two methods are from
    // http://sagistech.blogspot.com/2011/01/buildversionsdkint-on-android-15.html
    //
    public static int getSdkInt() {  
        if (Build.VERSION.RELEASE.startsWith("1.5"))  
            return 3;  
         
        return HelperInternal.getSdkIntInternal();  
    }  
      
    private static class HelperInternal {  
        private static int getSdkIntInternal() {  
            return Build.VERSION.SDK_INT;                 
        }  
    } 
    //
    // !CREDIT:
    //
    
    public static boolean isIncredible() {
        return Build.MODEL.toLowerCase().contains("incredible") ||
            Build.MODEL.toLowerCase().contains("adr6300");
    }
    
}
