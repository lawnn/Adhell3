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

public class DeviceAdminInteractor {
    private static final int RESULT_ENABLE = 42;
    private static final String TAG = DeviceAdminInteractor.class.getCanonicalName();
    private static DeviceAdminInteractor mInstance = null;

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
    ApplicationPolicy mApplicationPolicy;

    @Inject
    Context mContext;

    @Inject
    ComponentName componentName;

    private DeviceAdminInteractor() {
        App.get().getAppComponent().inject(this);
    }


    public static DeviceAdminInteractor getInstance() {
        if (mInstance == null) {
            mInstance = getSync();
        }
        return mInstance;
    }

    private static synchronized DeviceAdminInteractor getSync() {
        if (mInstance == null) {
            mInstance = new DeviceAdminInteractor();
        }
        return mInstance;
    }

    public static boolean isSamsung() {
        Log.i(TAG, "Device manufacturer: " + Build.MANUFACTURER);
        return Build.MANUFACTURER.equals("samsung");
    }

    /**
     * Check if admin enabled
     *
     * @return void
     */
    public boolean isActiveAdmin() {
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
    public void forceActivateKnox(String knoxKey) throws Exception {
        try {
            KnoxEnterpriseLicenseManager.getInstance(mContext).activateLicense(knoxKey);
        } catch (Exception e) {
            Log.e(TAG, "Failed to activate license", e);
            throw new Exception("Failed to activate license");
        }

    }

    /**
     * Check if KNOX enabled
     */
    public boolean isKnoxEnabled() {
        return (mContext.checkCallingOrSelfPermission("com.samsung.android.knox.permission.KNOX_FIREWALL")
                == PackageManager.PERMISSION_GRANTED)
                && (mContext.checkCallingOrSelfPermission("com.samsung.android.knox.permission.KNOX_APP_MGMT")
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
        if (mApplicationPolicy == null) {
            Log.i(TAG, "mApplicationPolicy variable is null");
            return false;
        }
        try {
            File file = new File(pathToApk);
            if (!file.exists()) {
                Log.i(TAG, "apk fail does not exist: " + pathToApk);
                return false;
            }

            boolean result = mApplicationPolicy.installApplication(pathToApk, false);
            Log.i(TAG, "Is Application installed: " + result);
            return result;
        } catch (Throwable e) {
            Log.e(TAG, "Failed to install application", e);
            return false;
        }
    }

    public ContentBlocker getContentBlocker() {
        Log.d(TAG, "Entering contentBlocker() method");
        try {
            switch (EnterpriseDeviceManager.getAPILevel()) {
                case KNOX_2_8:
                case KNOX_2_9:
                case KNOX_3_0:
                case KNOX_3_1:
                default:
                    return ContentBlocker57.getInstance();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to getAppsAlphabetically ContentBlocker", t);
            return null;
        }
    }

    public boolean isContentBlockerSupported() {
        return (isSamsung() && isKnoxSupported() && isKnoxVersionSupported());
    }

    private boolean isKnoxVersionSupported() {
        Log.d(TAG, "Entering isKnoxVersionSupported() method");
        if (enterpriseDeviceManager == null) {
            Log.w(TAG, "Knox not supported since enterpriseDeviceManager = null");
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

    public boolean isKnoxSupported() {
        if (knoxEnterpriseLicenseManager == null) {
            Log.w(TAG, "Knox is not supported");
            return false;
        }
        Log.i(TAG, "Knox is supported");
        return true;
    }

    public String getLicenseActivationErrorMessage(int errorCode) {
        if (errorCode == KnoxEnterpriseLicenseManager.ERROR_NULL_PARAMS) {
            return "Null params";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_UNKNOWN) {
            return "Unknown";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_INVALID_LICENSE) {
            return "Invalid license";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_LICENSE_TERMINATED) {
            return "License terminated";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_LICENSE_EXPIRED) {
            return "License expired";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_LICENSE_QUANTITY_EXHAUSTED) {
            return "License quantity exhausted";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_INVALID_PACKAGE_NAME) {
            return "Invalid package name";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_NOT_CURRENT_DATE) {
            return "Not current date";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_LICENSE_ACTIVATION_NOT_FOUND) {
            return "License not found, used for deactivation result";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_LICENSE_DEACTIVATED) {
            return "License deactivated";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_INTERNAL) {
            return "Internal";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_INTERNAL_SERVER) {
            return "Internak server";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_NETWORK_DISCONNECTED) {
            return "Network disconnected";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_NETWORK_GENERAL) {
            return "Network general";
        } else if (errorCode == KnoxEnterpriseLicenseManager.ERROR_USER_DISAGREES_LICENSE_AGREEMENT) {
            return "User disagrees license agreement";
        } else {
            return "";
        }
    }
}
