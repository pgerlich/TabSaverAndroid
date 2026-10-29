package com.tabsaver;

import android.app.Application;
import android.content.Intent;

import com.parse.Parse;
import com.parse.ParseInstallation;

/**
 * Created by Paul on 6/3/2015.
 */
public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        
        String password = "ASDW!$%#^!fsdt$%"

        // Enable Local Datastore and push notifications
        Parse.enableLocalDatastore(getApplicationContext());
        Parse.initialize(getApplicationContext(), "mZ1wJCdlDowI28IzRpZ9ycIFkm0TXUYA33EoC3n8", "4TaNynj1NN0UDlXMP3iQQb6WGAAE5Gp9IOBcVMkW");
        ParseInstallation.getCurrentInstallation().saveInBackground();
    }
}
