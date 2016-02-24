/*
 * Copyright (C) 2016 Mopria Alliance, Inc.
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.printservicestubs.stubs.gcp;

import android.annotation.NonNull;
import android.annotation.StringRes;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.util.Preconditions;
import com.android.printservicestubs.PrintServiceStub;
import com.android.printservicestubs.R;
import com.android.printservicestubs.VendorConfig;
import com.android.printservicestubs.servicediscovery.DiscoveryListener;
import com.android.printservicestubs.servicediscovery.NetworkDevice;
import com.android.printservicestubs.servicediscovery.NetworkDiscovery;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * A stub listening for mDNS results and only adding the ones that are Google Cloud Print printers
 */
public class GoogleCloudPrintStub implements PrintServiceStub, DiscoveryListener {
    private static final String LOG_TAG = "GoogleCloudPrintStub";
    private static final String PRIVET_TYPE_PRINTER = "printer";

    /**
     * Printer identifiers of the printers found.
     */
    private final @NonNull HashSet<String> printers;

    /**
     * Context of the user of this stub
     */
    private final @NonNull Context mContext;

    /**
     * Call backs to report the number of printers found.
     */
    private PrinterDiscoveryCallback mCallback;

    /**
     * Create new stub that finds all Google Cloud Print printers.
     *
     * @param context The context the plugin runs in
     */
    public GoogleCloudPrintStub(@NonNull Context context) {
        mContext = Preconditions.checkNotNull(context, "context");

        printers = new HashSet<>();
    }

    @Override
    public @NonNull Uri getInstallUri() {
        VendorConfig config = null;
        try {
            config = VendorConfig.getConfig(mContext, "@string/plugin_vendor_gcp");
        } catch (IOException | XmlPullParserException e) {
            Log.e(LOG_TAG, "Error reading vendor config", e);
        }
        return Uri.parse(mContext.getString(R.string.uri_package_details,
                config.getPackageName()));
    }

    @Override
    public void start(@NonNull PrinterDiscoveryCallback callback)
            throws Exception {
        mCallback = callback;
        NetworkDiscovery.onListenerAdded(this, mContext);
    }

    @Override
    public @StringRes int getName() {
        return R.string.plugin_vendor_gcp;
    }

    @Override
    public void stop() throws Exception {
        NetworkDiscovery.removeDiscoveryListener(this);
    }

    /**
     * Get all service names for the device.
     *
     * @param networkDevice The device
     *
     * @return The list of service names
     */
    private @NonNull ArrayList<String> getServicesNames(@NonNull NetworkDevice networkDevice) {
        ArrayList<String> mDnsServices = new ArrayList<>();

        ArrayList<NetworkDevice> instances = networkDevice.getAllDiscoveryInstances();
        final int numInstances = instances.size();
        for (int i = 0; i < numInstances; i++) {
            mDnsServices.add(instances.get(i).getBonjourService());
        }

        return mDnsServices;
    }

    /**
     * Check if a network device is a GCP printer.
     *
     * @param networkDevice The device that might be a GCP printer
     *
     * @return true iff the device is a GCP printer
     */
    private boolean isGCPPrinter(@NonNull NetworkDevice networkDevice) {
        final String privetService = mContext.getString(R.string.mdns_service_privet);

        if (!getServicesNames(networkDevice).contains(privetService)) {
            return false;
        }

        String type = networkDevice.getTxtAttributes(privetService).getString("type");

        return !TextUtils.isEmpty(type) && (type.contains(PRIVET_TYPE_PRINTER));
    }

    @Override
    public void onDeviceRemoved(@NonNull NetworkDevice networkDevice) {
        if (isGCPPrinter(networkDevice)) {
            synchronized (printers) {
                boolean removed = printers.remove(networkDevice.getDeviceIdentifier());

                if (removed) {
                    mCallback.onChanged(printers.size());
                }
            }
        }
    }

    @Override
    public void onDeviceFound(@NonNull NetworkDevice networkDevice) {
        if (isGCPPrinter(networkDevice)) {
            synchronized (printers) {
                boolean added = printers.add(networkDevice.getDeviceIdentifier());

                if (added) {
                    mCallback.onChanged(printers.size());
                }
            }
        }
    }
}