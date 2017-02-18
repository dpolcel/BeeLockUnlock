package br.com.polcel.beelockunlock;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.provider.SyncStateContract.Constants;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import br.com.polcel.beelockunlock.R;

public class BeeLockUnlockService extends Service {

    private BroadcastReceiver mReceiver;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        //filter.addAction(Intent.ACTION_SCREEN_OFF);

        mReceiver = new BeeLockUnlockIntentReceiver();

        registerReceiver(mReceiver, filter);

        startForeground();

        return START_STICKY;
    }

    private void startForeground() {
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setTicker(getResources().getString(R.string.app_name))
                .setContentText("Running")
                //.setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(null)
                .setOngoing(true)
                .build();
        startForeground(9999, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }
}
