package com.fusionjack.adhell3.utils;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.Log;

import com.fusionjack.adhell3.App;
import com.fusionjack.adhell3.blocker.ContentBlocker;
import com.fusionjack.adhell3.blocker.ContentBlocker57;
import com.samsung.android.knox.EnterpriseDeviceManager;
import com.samsung.android.knox.application.ApplicationPolicy;
import com.samsung.android.knox.license.KnoxEnterpriseLicenseManager;

import java.io.File;

import javax.inject.Inject;

import static com.samsung.android.knox.EnterpriseDeviceManager.KNOX_VERSION_CODES.KNOX_2_8;
import static com.samsung.android.knox.EnterpriseDeviceManager.KNOX_VERSION_CODES.KNOX_2_9;
import static com.samsung.android.knox.EnterpriseDeviceManager.KNOX_VERSION_CODES.KNOX_3_0;
import static com.samsung.android.knox.EnterpriseDeviceManager.KNOX_VERSION_CODES.KNOX_3_1;

public final class DeviceAdminInteractor {
    private static final int RESULT_ENABLE = 42;
    private static final String TAG = DeviceAdminInteractor.class.getCanonicalName();

    private static DeviceAdminInteractor instance;

    private final String KNOX_KEY = "knox_key";

    @Nullable
    @Inject
    KnoxEnterpriseLicenseManager knoxEnterpriseLicenseManager;

    @Nullable
    @Inject
    EnterpriseDeviceManager enterpriseDeviceManager;

    @Nullable
    @Inject
    DevicePolicyManager devicePolicyManager;

    @Nullable
    @Inject
    ApplicationPolicy applicationPolicy;

    @Inject
    ComponentName componentName;

    private DeviceAdminInteractor() {
        App.get().getAppComponent().inject(this);
    }

    public static DeviceAdminInteractor getInstance() {
        if (instance == null) {
            instance = new DeviceAdminInteractor();
        }
        return instance;
    }

    /**
     * Check if admin enabled
     *
     * @return void
     */
    public boolean isAdminActive() {
        return devicePolicyManager.isAdminActive(componentName);
    }

    /**
     * Force user to enadle administrator
     */
    public void forceEnableAdmin(Context context) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Policy provider");
        ((Activity) context).startActivityForResult(intent, RESULT_ENABLE);
    }

    /**
     * Force to activate Samsung KNOX Standard SDK
     */
    public void forceActivateKnox(String knoxKey, Context context) {
        KnoxEnterpriseLicenseManager.getInstance(context).activateLicense(knoxKey);
    }

    /**
     * Check if KNOX enabled
     */
    public boolean isKnoxEnabled(Context context) {
        return (context.checkCallingOrSelfPermission("com.samsung.android.knox.permission.KNOX_FIREWALL")
                == PackageManager.PERMISSION_GRANTED)
                && (context.checkCallingOrSelfPermission("com.samsung.android.knox.permission.KNOX_APP_MGMT")
                == PackageManager.PERMISSION_GRANTED);
    }

    public String getKnoxKey(SharedPreferences sharedPreferences) {
        return sharedPreferences.getString(KNOX_KEY, null);
    }

    public void setKnoxKey(SharedPreferences sharedPreferences, String key) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KNOX_KEY, key);
        editor.apply();
    }

    public boolean installApk(String pathToApk) {
        if (applicationPolicy == null) {
            Log.i(TAG, "applicationPolicy variable is null");
            return false;
        }
        try {
            File file = new File(pathToApk);
            if (!file.exists()) {
                Log.i(TAG, "apk fail does not exist: " + pathToApk);
                return false;
            }

            boolean result = applicationPolicy.installApplication(pathToApk, false);
            Log.i(TAG, "Is Application installed: " + result);
            return result;
        } catch (Throwable e) {
            Log.e(TAG, "Failed to install application", e);
            return false;
        }
    }

    public ContentBlocker getContentBlocker() {
        switch (EnterpriseDeviceManager.getAPILevel()) {
            case KNOX_2_8:
            case KNOX_2_9:
            case KNOX_3_0:
            case KNOX_3_1:
                return ContentBlocker57.getInstance();
            default:
                return null;
        }
    }

    public boolean isSupported() {
        return isSamsung() && isKnoxSupported() && isKnoxVersionSupported();
    }

    private boolean isSamsung() {
        Log.i(TAG, "Device manufacturer: " + Build.MANUFACTURER);
        return Build.MANUFACTURER.equals("samsung");
    }

    private boolean isKnoxVersionSupported() {
        if (enterpriseDeviceManager == null) {
            Log.w(TAG, "Knox is not supported: enterpriseDeviceManager is null");
            return false;
        }
        switch (EnterpriseDeviceManager.getAPILevel()) {
            case KNOX_2_8:
            case KNOX_2_9:
            case KNOX_3_0:
            case KNOX_3_1:
                return true;
            default:
                return false;
        }
    }

    private boolean isKnoxSupported() {
        if (knoxEnterpriseLicenseManager == null) {
            Log.w(TAG, "Knox is not supported: knoxEnterpriseLicenseManager is null");
            return false;
        }
        Log.i(TAG, "Knox is supported");
        return true;
    }
}
