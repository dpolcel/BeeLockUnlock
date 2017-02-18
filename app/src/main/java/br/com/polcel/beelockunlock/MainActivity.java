package br.com.polcel.beelockunlock;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Browser;
//import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.customtabs.CustomTabsCallback;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.customtabs.CustomTabsSession;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebIconDatabase;
import android.widget.Button;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends Activity implements
        LockscreenUtils.OnLockStatusChangedListener {

    private LockscreenUtils mLockscreenUtils;
    private VideoView mVideoView;
    private static final String LIVE_VIDEO_FOLDER = "BWG";
    private static final String LIVE_VIDEO_NAME = "bwg.3gp";
    private static final String FOLHA_URL_CONFIG_KEY = "url";
    private final long LIVE_VIDEO_TIMEOUT_SECONDS = 600;
    private final String TAG = "ERRO";
    private final String FOLHA_URL = "https://folha.4bee.com.br/4beeFolha/servlet/generic.servlet01c?prog=&acao=_99";
    private static final String TOOLBAR_COLOR = "#ef6c00";

    private CustomTabsSession mCustomTabsSession;
    private CustomTabsClient mClient;
    private Boolean isScreenOff = false;
    private long lastInteractionTime;
    public Runnable mInactivityRunnable;
    public Handler mInactivityHandler;
    private boolean is4beeFolhaOpened = false;
    public Button btnUnlock;
    public static boolean hasServiceStarted = false;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLockscreenUtils = new LockscreenUtils();
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();

        mFirebaseRemoteConfig.setConfigSettings(configSettings);
        mFirebaseRemoteConfig.setDefaults(R.xml.remote_config_defaults);

        toggleHideyBar();
        playVideo();

        if (getIntent() != null && getIntent().hasExtra("kill") && getIntent().getExtras().getInt("kill") == 1) {

            enableKeyguard();
            unlockHomeButton();

        } else {

            try {
                disableKeyguard();
                lockHomeButton();

                if (!hasServiceStarted) {
                    startService(new Intent(this, BeeLockUnlockService.class));
                    hasServiceStarted = true;
                }

                StateListener phoneStateListener = new StateListener();
                TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    public void toggleHideyBar() {

        // BEGIN_INCLUDE (get_current_ui_flags)
        // The UI options currently enabled are represented by a bitfield.
        // getSystemUiVisibility() gives us that bitfield.
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;
        // END_INCLUDE (get_current_ui_flags)
        // BEGIN_INCLUDE (toggle_ui_flags)
        boolean isImmersiveModeEnabled = ((uiOptions | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) == uiOptions);
        if (isImmersiveModeEnabled) {
            Log.i(TAG, "Turning immersive mode mode off. ");
        } else {
            Log.i(TAG, "Turning immersive mode mode on.");
        }

        newUiOptions ^= View.SYSTEM_UI_FLAG_LOW_PROFILE;

        // Navigation bar hiding:  Backwards compatible to ICS.
        if (Build.VERSION.SDK_INT >= 14) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }

        // Status bar hiding: Backwards compatible to Jellybean
        if (Build.VERSION.SDK_INT >= 16) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }

        // Immersive mode: Backward compatible to KitKat.
        // Note that this flag doesn't do anything by itself, it only augments the behavior
        // of HIDE_NAVIGATION and FLAG_FULLSCREEN.  For the purposes of this sample
        // all three flags are being toggled together.
        // Note that there are two immersive mode UI flags, one of which is referred to as "sticky".
        // Sticky immersive mode differs in that it makes the navigation and status bars
        // semi-transparent, and the UI flag does not get cleared when the user interacts with
        // the screen.
        if (Build.VERSION.SDK_INT >= 18) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }

        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
        //END_INCLUDE (set_ui_flags)
    }

//    public void startUserInactivityDetectThread() {
//
//        Date currentDate = new Date();
//        setLastInteractionTime(currentDate.getTime());
//
//        mInactivityRunnable = new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    Date currentDate = new Date();
//
//                    long difference = currentDate.getTime() - getLastInteractionTime();
//                    long differenceInSeconds = difference / 1000;
//
//                    if (differenceInSeconds >= LIVE_VIDEO_TIMEOUT_SECONDS) {
//                        setLastInteractionTime(new Date().getTime());
//                        mInactivityHandler.removeCallbacks(mInactivityRunnable);
//
//                        finish();
//
//                        Intent mIntent = new Intent(getApplicationContext(), MainActivity.class);
//
//                        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                        mIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                        mIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//
//                        startActivity(mIntent);
//                    } else {
//                        mInactivityHandler.postDelayed(this, 1000);
//                    }
//
//                } catch (Exception e) {
//                    Log.i(TAG, e.getMessage());
//                }
//            }
//        };
//
//        mInactivityHandler = new Handler();
//        mInactivityHandler.post(mInactivityRunnable);
//    }


//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//
//        if (event.getAction() == MotionEvent.ACTION_DOWN) {
//            Date currentDate = new Date();
//            setLastInteractionTime(currentDate.getTime());
//        }
//
//        return super.onTouchEvent(event);
//    }
//
//    public long getLastInteractionTime() {
//        return lastInteractionTime;
//    }
//
//    public void setLastInteractionTime(long lastInteractionTime) {
//        this.lastInteractionTime = lastInteractionTime;
//    }

    private void playVideo() {
        String videoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + LIVE_VIDEO_FOLDER + "/" + LIVE_VIDEO_NAME;

        mVideoView = (VideoView) findViewById(R.id.vvVideoBWG);
        mVideoView.setVideoURI(Uri.parse(videoPath));
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.setLooping(true);
                mVideoView.start();
            }
        });

        mVideoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    mVideoView.stopPlayback();
                    fetchURL();
                }

                return false;
            }
        });
    }

    private CustomTabsSession getSession() {
        if (mClient == null) {
            mCustomTabsSession = null;
        } else if (mCustomTabsSession == null) {
            mCustomTabsSession = mClient.newSession(new NavigationCallback());
            SessionHelper.setCurrentSession(mCustomTabsSession);
        }
        return mCustomTabsSession;
    }

    private void open4beeFolha(String url) {


        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(getSession());
        builder.setToolbarColor(Color.parseColor(TOOLBAR_COLOR)).setShowTitle(true);
        builder.setStartAnimations(this, R.anim.slide_in_right, R.anim.slide_out_left);
        builder.setExitAnimations(this, R.anim.slide_in_left, R.anim.slide_out_right);
        builder.setCloseButtonIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_arrow_back));

        CustomTabsIntent customTabsIntent = builder.build();
        CustomTabsHelper.addKeepAliveExtra(this, customTabsIntent.intent);
        customTabsIntent.launchUrl(this, Uri.parse(url));
    }

    private void fetchURL() {
        long cacheExpiration = 3600;

        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }

        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {

                            mFirebaseRemoteConfig.activateFetched();
                        }
                        String url = mFirebaseRemoteConfig.getString(FOLHA_URL_CONFIG_KEY);
                        open4beeFolha(url);
                    }
                });
    }

    private static class NavigationCallback extends CustomTabsCallback {
        @Override
        public void onNavigationEvent(int navigationEvent, Bundle extras) {
            Log.w("NAV", "onNavigationEvent: Code = " + navigationEvent);
        }
    }


    private void clearHistoryChrome(Context context) {
        ContentResolver cr = context.getContentResolver();
        if (canClearHistory(cr)) {
            Log.i("Can clear?", "YES");


            //deleteHistory();
            // deleteHistoryWhere(cr, null);
        }
    }

    private void deleteHistory() {
//        String[] proj = new String[]{Browser.BookmarkColumns.TITLE, Browser.BookmarkColumns.URL};
//        Uri uriCustom = Uri.parse("content://com.android.chrome.browser/bookmarks");
//        String sel = Browser.BookmarkColumns.BOOKMARK + " = 0"; // 0 = history, 1 = bookmark
//        ContentResolver cr = getContentResolver();
//        Cursor mCur = cr.query(uriCustom, proj, sel, null, null);
//        mCur.moveToFirst();
//        @SuppressWarnings("unused")
//        String title = "";
//        @SuppressWarnings("unused")
//        String url = "";
//
//        if (mCur.moveToFirst() && mCur.getCount() > 0) {
//            boolean cont = true;
//            while (mCur.isAfterLast() == false && cont) {
//                title = mCur.getString(mCur.getColumnIndex(Browser.BookmarkColumns.TITLE));
//                url = mCur.getString(mCur.getColumnIndex(Browser.BookmarkColumns.URL));
//                // Do something with title and url
//
//
//                mCur.moveToNext();
//            }
//            int count = cr.delete(uriCustom, null, null);
//
//        }
    }

    private void deleteHistoryWhere(ContentResolver cr, String whereClause) {
        Log.i("deleting?", "YES");

        String CONTENT_URI = "content://com.android.chrome.browser/history";
        Uri URI = Uri.parse(CONTENT_URI);
        Cursor cursor = null;
        try {
            cursor = cr.query(URI, new String[]{"url"}, whereClause,
                    null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    final WebIconDatabase iconDb = WebIconDatabase.getInstance();
                    do {
                        // Delete favicons
                        // TODO don't release if the URL is bookmarked
                        iconDb.releaseIconForPageUrl(cursor.getString(0));
                    } while (cursor.moveToNext());
                    cr.delete(URI, whereClause, null);
                }
            }
        } catch (IllegalStateException e) {
            Log.i("DEBUG_", "deleteHistoryWhere IllegalStateException: " + e.getMessage());
            return;
        } finally {
            if (cursor != null) cursor.close();
        }
        Log.i("DEBUG_", "deleteHistoryWhere: GOOD");
    }

    public boolean canClearHistory(ContentResolver cr) {
        String CONTENT_URI = "content://com.android.chrome.browser/history";
        Uri URI = Uri.parse(CONTENT_URI);
        String _ID = "_id";
        String VISITS = "visits";
        Cursor cursor = null;
        boolean ret = false;
        try {
            cursor = cr.query(URI,
                    new String[]{_ID, VISITS},
                    null, null, null);
            if (cursor != null) {
                ret = cursor.getCount() > 0;
            }
        } catch (IllegalStateException e) {
            Log.i("DEBUG_", "canClearHistory IllegalStateException: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        Log.i("DEBUG_", "canClearHistory: " + ret);
        return ret;
    }

    // Handle events of calls and unlock screen if necessary
    private class StateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {

            super.onCallStateChanged(state, incomingNumber);
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    unlockHomeButton();
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    break;
            }
        }
    }

    @Override
    public void onBackPressed() {
        return;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mLockscreenUtils.reset();

        ActivityManager activityManager = (ActivityManager) getApplicationContext()
                .getSystemService(Context.ACTIVITY_SERVICE);

        activityManager.moveTaskToFront(getTaskId(), 0);
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {

        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                || (keyCode == KeyEvent.KEYCODE_POWER)
                || (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                || (keyCode == KeyEvent.KEYCODE_CAMERA)) {
            return true;
        }
        if ((keyCode == KeyEvent.KEYCODE_HOME)) {

            return true;
        }

        return false;

    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
                || (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN)
                || (event.getKeyCode() == KeyEvent.KEYCODE_POWER)) {
            return false;
        }
        if ((event.getKeyCode() == KeyEvent.KEYCODE_HOME)) {

            return true;
        }
        return false;
    }

    public void lockHomeButton() {
        mLockscreenUtils.lock(MainActivity.this);
    }

    public void unlockHomeButton() {
        mLockscreenUtils.unlock();
    }

    @Override
    public void onLockStatusChanged(boolean isLocked) {
        if (!isLocked) {
            unlockDevice();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mLockscreenUtils.reset();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLockscreenUtils.reset();
    }

    @SuppressWarnings("deprecation")
    private void disableKeyguard() {
        KeyguardManager mKM = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock mKL = mKM.newKeyguardLock("IN");
        mKL.disableKeyguard();
    }

    @SuppressWarnings("deprecation")
    private void enableKeyguard() {
        KeyguardManager mKM = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock mKL = mKM.newKeyguardLock("IN");
        mKL.reenableKeyguard();
    }

    private void unlockDevice() {
        finish();
    }
}
