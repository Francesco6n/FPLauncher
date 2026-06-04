package org.exthmui.microlauncher.duoqin.activity;

import static org.exthmui.microlauncher.duoqin.utils.Constants.launcherSettingsPref;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.CallLog;
import android.provider.Telephony;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.snackbar.Snackbar;

import org.exthmui.microlauncher.duoqin.BuildConfig;
import org.exthmui.microlauncher.duoqin.R;
import org.exthmui.microlauncher.duoqin.databinding.ActivityMainBinding;
import org.exthmui.microlauncher.duoqin.utils.Constants;
import org.exthmui.microlauncher.duoqin.utils.LauncherUtils;
import org.exthmui.microlauncher.duoqin.utils.LocaleHelper;
import org.exthmui.microlauncher.duoqin.widgets.CallSmsCounter;
import org.exthmui.microlauncher.duoqin.widgets.CarrierTextView;
import org.exthmui.microlauncher.duoqin.widgets.ClockViewManager;
import org.exthmui.microlauncher.duoqin.widgets.DateTextView;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import es.dmoral.toasty.Toasty;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final static String TAG = "ML_MainActivity";
    private static final int grant_int=1;
    private boolean carrier_enable;
    private boolean dialpad_enable;
    private boolean callsms_counter;
    private boolean torch = false;
    private boolean isShortPress;
    private boolean isLoadApp = false;
    private boolean isDarkMode;
    private String clock_locate;
    private SharedPreferences sharedPreferences;
    private CameraManager manager;
    private ContentObserver mMissedPhoneContentObserver;
    private ContentObserver mMissedMsgContentObserver;
    private ActivityMainBinding mainBinding;
    private ClockViewManager clockViewManager;
    private DateTextView date;
    private CallSmsCounter callSmsCounter;
    private CarrierTextView carrier;
    private BroadcastReceiver menuKeyReceiver;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mainBinding.getRoot());
        if (BuildConfig.DEBUG) { showFirstLogcat(); }
        GrantPermissions();
        sharedPreferences = getSharedPreferences(launcherSettingsPref,Context.MODE_PRIVATE);
        clockViewManager = new ClockViewManager(mainBinding.clock.datesLayout);
        mainBinding.contact.setOnClickListener(new mClick());
        mainBinding.menu.setOnClickListener(new mClick());
        date = new DateTextView(this);
        carrier = new CarrierTextView(this);
        clockViewManager.insertOrUpdateView(1, date);
        loadSettings(sharedPreferences);
        menuKeyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!isLoadApp) {
                    isLoadApp = true;
                    Snackbar.make(mainBinding.getRoot(), R.string.loading, Snackbar.LENGTH_SHORT).show();
                    new Handler(Looper.myLooper()).postDelayed(() -> {
                        Intent menuIt = new Intent(MainActivity.this, AppListActivity.class);
                        startActivity(menuIt);
                        isLoadApp = false;
                    }, 500);
                }
            }
        };
    }

    private void GrantPermissions(){
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE};
        if (!EasyPermissions.hasPermissions(this, perms)) {
            EasyPermissions.requestPermissions(this, getString(R.string.permission_required_title),
                    grant_int, perms);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode,permissions,grantResults,this);
    }

    @AfterPermissionGranted(grant_int)
    private void initCallSmsObserver() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_SMS)) {
            mMissedPhoneContentObserver = new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    super.onChange(selfChange);
                    runOnUiThread(() -> {
                        callSmsCounter = null;
                        callSmsCounter = new CallSmsCounter(MainActivity.this);
                        clockViewManager.insertOrUpdateView(4, callSmsCounter);
                        setClockLocate(clock_locate);
                    });
                }
            };
            mMissedMsgContentObserver = new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    super.onChange(selfChange);
                    runOnUiThread(() -> {
                        callSmsCounter = null;
                        callSmsCounter = new CallSmsCounter(MainActivity.this);
                        clockViewManager.insertOrUpdateView(4, callSmsCounter);
                        setClockLocate(clock_locate);
                    });
                }
            };
            unregisterObserver();
            getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI,
                    true, mMissedPhoneContentObserver);
            getContentResolver().registerContentObserver(Uri.parse("content://sms"),
                    true, mMissedMsgContentObserver);
            getContentResolver().registerContentObserver(Telephony.MmsSms.CONTENT_URI,
                    true, mMissedMsgContentObserver);
        }
    }

    private synchronized void unregisterObserver() {
        try {
            if (mMissedPhoneContentObserver != null) {
                getContentResolver().unregisterContentObserver(mMissedPhoneContentObserver);
            }
            if (mMissedMsgContentObserver != null) {
                getContentResolver().unregisterContentObserver(mMissedMsgContentObserver);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "unregisterObserver failed: " + e.getMessage());
        }
    }

    private void loadSettings(SharedPreferences sharedPreferences){
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        carrier_enable = sharedPreferences.getBoolean("switch_preference_carrier_name",true);
        if(carrier_enable){
            Log.d(TAG, "Enable carrier name");
            clockViewManager.insertOrUpdateView(3,carrier);
        }else{
            Log.d(TAG, "Disable carrier name");
            clockViewManager.removeView(3);
        }
        callsms_counter = sharedPreferences.getBoolean("switch_preference_callsms_counter",false);
        if (callsms_counter && EasyPermissions.hasPermissions(this, Manifest.permission.READ_CALL_LOG,Manifest.permission.READ_SMS)){
            initCallSmsObserver();
            if (callSmsCounter == null) {
                callSmsCounter = new CallSmsCounter(this);
            }
            Log.d(TAG, "Enable call/sms counter");
            clockViewManager.insertOrUpdateView(4, callSmsCounter);
        }else{
            Log.d(TAG, "Disable call/sms counter");
            clockViewManager.removeView(4);
        }
        clock_locate = (sharedPreferences.getString("list_preference_clock_locate","left"));
        setClockLocate(clock_locate);
        String clock_size = (sharedPreferences.getString("list_preference_clock_size","44"));
        mainBinding.clock.textClock.setTextSize(Float.parseFloat(clock_size));
        dialpad_enable = sharedPreferences.getBoolean("preference_dial_pad",true);
        isDarkMode = sharedPreferences.getBoolean("dark_mode",false);
        LauncherUtils.setDarkMode(getApplicationContext(), isDarkMode);
    }

    private void setClockLocate(String clockLocate) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mainBinding.clock.textClock.getLayoutParams();
        switch (clockLocate){
            case "right":
                params.gravity = Gravity.END;
                break;
            case "left":
            default:
                params.gravity = Gravity.START;
                break;
        }
        mainBinding.clock.textClock.setLayoutParams(params);
        for (int i = 1; i < 5; i++) {
            Log.d(TAG, "setClockLocate: "+i);
            clockViewManager.setLayoutParams(i, params);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        loadSettings(sharedPreferences);
    }

    class mClick implements View.OnClickListener{
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.contact) {
                Intent i = new Intent();
                i.setAction("android.intent.action.MAIN");
                i.addCategory("android.intent.category.APP_CONTACTS");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } else if (v.getId() == R.id.menu) {
                Intent menuIt = new Intent(MainActivity.this, AppListActivity.class);
                startActivity(menuIt);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (menuKeyReceiver != null) {
            LocalBroadcastManager.getInstance(this).registerReceiver(menuKeyReceiver,
                    new IntentFilter(Constants.MENU_KEY_ACTION));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (menuKeyReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(menuKeyReceiver);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterObserver();
        finish();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        unregisterObserver();
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK){
            isShortPress = false;
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK){
            if (isShortPress) {
                Intent it = new Intent();
                it.setAction("android.intent.action.MAIN");
                it.addCategory("android.intent.category.APP_CONTACTS");
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG,"这个按键的KeyCode是 "+keyCode);
        Intent it = new Intent();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SOFT_LEFT:
                if (!isLoadApp) {
                    isLoadApp = true;
                    Snackbar.make(mainBinding.getRoot(),R.string.loading,Snackbar.LENGTH_SHORT).show();
                    new Handler(Looper.myLooper()).postDelayed(() -> {
                        Intent menuIt = new Intent(MainActivity.this, AppListActivity.class);
                        startActivity(menuIt);
                        isLoadApp = false;
                    },500);
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (!isLoadApp) {
                    isLoadApp = true;
                    Snackbar.make(mainBinding.getRoot(),R.string.loading,Snackbar.LENGTH_SHORT).show();
                    new Handler(Looper.myLooper()).postDelayed(() -> {
                        Intent menuIt = new Intent(MainActivity.this, AppListActivity.class);
                        startActivity(menuIt);
                        isLoadApp = false;
                    },500);
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    event.startTracking();
                    if (event.getRepeatCount() == 0) {
                        isShortPress = true;
                        return true;
                    }
                }
                return true;
            case KeyEvent.KEYCODE_SOFT_RIGHT:
                it.setAction("android.intent.action.MAIN");
                it.addCategory("android.intent.category.APP_CONTACTS");
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                return true;
            case KeyEvent.KEYCODE_CALL:
            default:
                break;
        }
        // 7 到 16 的 keyCode 为数字键1到9，0的值
        if(keyCode >= 7 && keyCode <= 16 || keyCode == KeyEvent.KEYCODE_STAR || keyCode == KeyEvent.KEYCODE_POUND || keyCode == KeyEvent.KEYCODE_CALL){
            if(dialpad_enable){
                String number;
                if (keyCode == KeyEvent.KEYCODE_STAR) {
                    number = "*";
                } else if (keyCode == KeyEvent.KEYCODE_POUND) {
                    number = "#";
                } else if (keyCode == KeyEvent.KEYCODE_CALL) {
                    number = "";
                } else {
                    number = String.valueOf((char) event.getNumber());
                }
                try {
                    it = new Intent("android.intent.action.DIAL", Uri.parse("tel:" + number));
                    it.setClassName("com.android.dialer","com.duoqin.dialer.DialpadActivity");
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                } catch (Exception e){
                    Log.e(TAG,"没有找到拨号盘,正在尝试AOSP方式");
                    try {
                        it = new Intent("android.intent.action.DIAL", Uri.parse("tel:" + number));
                        it.setClassName("com.android.dialer","com.android.dialer.main.impl.MainActivity");
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(it);
                    } catch (Exception ex) {
                        Toasty.error(this,"没有找到拨号盘",Toasty.LENGTH_LONG).show();
                        Log.e(TAG,"没有找到拨号盘");
                        e.printStackTrace();
                    }
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void turnOnTorch(){
        if(PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)){
            manager = (CameraManager) getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
            if(torch){
                try {
                    manager.setTorchMode("0", true);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                torch=false;
            }else{
                try {
                    manager.setTorchMode("0", false);
                    manager = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                torch=true;
            }
        }else{
            Toasty.error(this,R.string.permission_denied,Toasty.LENGTH_LONG).show();
        }
    }

    private static void doInStatusBar(Context mContext) {
        try {
            @SuppressLint("WrongConstant") Object service = mContext.getSystemService("statusbar");
            Method expand = service.getClass().getMethod("expandNotificationsPanel");
            expand.invoke(service);
            Log.i(TAG,"Expand NotificationPanel");
        } catch (Exception e) {
            Log.e(TAG,"Expand NotificationPanel Error");
            e.printStackTrace();
        }
    }

    void showFirstLogcat(){
        Log.e(TAG, getPackageName()+" onCreate: Logcat start......");
        Log.i(TAG, "===================================================");
        Log.i(TAG, " ________ ___  ___  _____ ______   ________     \n");
        Log.i(TAG, "|\\  _____\\\\  \\|\\  \\|\\   _ \\  _   \\|\\   __  \\    \n");
        Log.i(TAG,"\\ \\  \\__/\\ \\  \\\\\\  \\ \\  \\\\\\__\\ \\  \\ \\  \\|\\  \\   \n");
        Log.i(TAG," \\ \\   __\\\\ \\  \\\\\\  \\ \\  \\\\|__| \\  \\ \\  \\\\\\  \\  \n");
        Log.i(TAG,"  \\ \\  \\_| \\ \\  \\\\\\  \\ \\  \\    \\ \\  \\ \\  \\\\\\  \\ \n");
        Log.i(TAG,"   \\ \\__\\   \\ \\_______\\ \\__\\    \\ \\__\\ \\_______\\\n");
        Log.i(TAG,"    \\|__|    \\|_______|\\|__|     \\|__|\\|_______|\n");
        Log.i(TAG, "===================================================");
        Log.e(TAG, " ᗜˬᗜ  Fumo enabled the Debug Mode.start to debugging~");
    }
}
