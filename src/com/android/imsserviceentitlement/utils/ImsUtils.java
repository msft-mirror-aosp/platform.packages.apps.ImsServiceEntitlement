/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.imsserviceentitlement.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ims.ImsMmTelManager;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

/** A helper class for IMS relevant APIs with subscription id. */
public class ImsUtils {
    private static final String TAG = "IMSSE-ImsUtils";

    private final CarrierConfigManager mCarrierConfigManager;
    private final ImsMmTelManager mImsMmTelManager;
    private final int mSubId;

    // Cache subscription id associated {@link ImsUtils} objects for reusing.
    @GuardedBy("ImsUtils.class")
    private static SparseArray<ImsUtils> sInstances = new SparseArray<ImsUtils>();

    private ImsUtils(Context context, int subId) {
        mCarrierConfigManager =
                (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        mImsMmTelManager = getImsMmTelManager(context, subId);
        this.mSubId = subId;
    }

    /** Returns {@link ImsUtils} instance. */
    public static synchronized ImsUtils getInstance(Context context, int subId) {
        ImsUtils instance = sInstances.get(subId);
        if (instance != null) {
            return instance;
        }

        instance = new ImsUtils(context, subId);
        sInstances.put(subId, instance);
        return instance;
    }

    /** Changes persistent WFC enabled setting. */
    public void setWfcSetting(boolean enabled, boolean force) {
        try {
            if (force) {
                mImsMmTelManager.setVoWiFiSettingEnabled(enabled);
            }
        } catch (RuntimeException e) {
            // ignore this exception, possible exception should be NullPointerException or
            // RemoteException.
        }
    }

    /** Disables WFC and reset WFC mode to carrier default value */
    public void disableAndResetVoWiFiImsSettings() {
        try {
            disableWfc();

            // Reset WFC mode to carrier default value
            if (mCarrierConfigManager != null) {
                PersistableBundle b = mCarrierConfigManager.getConfigForSubId(mSubId);
                if (b != null) {
                    mImsMmTelManager.setVoWiFiModeSetting(
                            b.getInt(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_MODE_INT));
                    mImsMmTelManager.setVoWiFiRoamingModeSetting(
                            b.getInt(
                                    CarrierConfigManager
                                            .KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_MODE_INT));
                }
            }
        } catch (RuntimeException e) {
            // ignore this exception, possible exception should be NullPointerException or
            // RemoteException.
        }
    }

    /**
     * Returns {@link ImsMmTelManager} with specific subscription id.
     * Returns {@code null} if provided subscription id invalid.
     */
    @Nullable
    public static ImsMmTelManager getImsMmTelManager(Context context, int subId) {
        try {
            return ImsMmTelManager.createForSubscriptionId(subId);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Can't get ImsMmTelManager, IllegalArgumentException: subId = " + subId);
        }

        return null;
    }

    /** Returns whether WFC is enabled by user for current subId */
    public boolean isWfcEnabledByUser() {
        try {
            return mImsMmTelManager.isVoWiFiSettingEnabled();
        } catch (RuntimeException e) {
            // ignore this exception, possible exception should be NullPointerException or
            // RemoteException.
        }
        return false;
    }

    /** Calls {@link #disableAndResetVoWiFiImsSettings()} in background thread. */
    public static void turnOffWfc(ImsUtils imsUtils, Runnable action) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                imsUtils.disableAndResetVoWiFiImsSettings();
                return null; // To satisfy compiler
            }

            @Override
            protected void onPostExecute(Void result) {
                action.run();
            }
        }.execute();
    }

    /** Disables WFC */
    public void disableWfc() {
        setWfcSetting(false, false);
    }
}