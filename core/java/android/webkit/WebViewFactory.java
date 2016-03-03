/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.webkit;

import android.os.Build;
import android.os.StrictMode;
import android.os.SystemProperties;
import android.util.AndroidRuntimeException;
import android.util.Log;

import java.io.File;

/**
 * Top level factory, used creating all the main WebView implementation classes.
 *
 * @hide
 */
public final class WebViewFactory {

    public static final String FORCE_PROVIDER_PROPERTY = "persist.webview.provider";
    private static final String FORCE_PROVIDER_PROPERTY_VALUE_CHROMIUM = "chromium";
    private static final String FORCE_PROVIDER_PROPERTY_VALUE_CLASSIC = "classic";

    // Default Provider factory class name.
    private static final String CLASSIC_WEBVIEW_FACTORY = "android.webkit.WebViewClassic$Factory";
    private static final String CHROMIUM_WEBVIEW_FACTORY =
            "com.android.webview.chromium.WebViewChromiumFactoryProvider";

    private static final String CLASSIC_WEBVIEW_FILE = "/system/lib/libwebcore.so";
    private static final String CHROMIUM_WEBVIEW_FILE = "/system/lib/libwebviewchromium.so";

    private static final String LOGTAG = "WebViewFactory";

    private static final boolean DEBUG = false;

    private static class Preloader {
        static WebViewFactoryProvider sPreloadedProvider;
        static {
            try {
                sPreloadedProvider = getFactoryClass().newInstance();
            } catch (Exception e) {
                Log.w(LOGTAG, "error preloading provider", e);
            }
        }
    }

    // Cache the factory both for efficiency, and ensure any one process gets all webviews from the
    // same provider.
    private static WebViewFactoryProvider sProviderInstance;
    private static final Object sProviderLock = new Object();

    /** @hide */
    public static boolean isClassicWebViewAvailable() {
        return new File(CLASSIC_WEBVIEW_FILE).exists();
    }

    /** @hide */
    public static boolean isChromiumWebViewAvailable() {
        return new File(CHROMIUM_WEBVIEW_FILE).exists();
    }

    /** @hide */
    public static boolean useClassicWebView() {
        return FORCE_PROVIDER_PROPERTY_VALUE_CLASSIC.equals(SystemProperties.get(FORCE_PROVIDER_PROPERTY));
    }

    static WebViewFactoryProvider getProvider() {
        synchronized (sProviderLock) {
            // For now the main purpose of this function (and the factory abstraction) is to keep
            // us honest and minimize usage of WebViewClassic internals when binding the proxy.
            if (sProviderInstance != null) return sProviderInstance;

            Class<WebViewFactoryProvider> providerClass;
            try {
                providerClass = getFactoryClass();
            } catch (ClassNotFoundException e) {
                Log.e(LOGTAG, "error loading provider", e);
                throw new AndroidRuntimeException(e);
            }

            // This implicitly loads Preloader even if it wasn't preloaded at boot.
            if (Preloader.sPreloadedProvider != null &&
                Preloader.sPreloadedProvider.getClass() == providerClass) {
                sProviderInstance = Preloader.sPreloadedProvider;
                if (DEBUG) Log.v(LOGTAG, "Using preloaded provider: " + sProviderInstance);
                return sProviderInstance;
            }

            // The preloaded provider isn't the one we wanted; construct our own.
            StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
            try {
                sProviderInstance = providerClass.newInstance();
                if (DEBUG) Log.v(LOGTAG, "Loaded provider: " + sProviderInstance);
                return sProviderInstance;
            } catch (Exception e) {
                Log.e(LOGTAG, "error instantiating provider", e);
                throw new AndroidRuntimeException(e);
            } finally {
                StrictMode.setThreadPolicy(oldPolicy);
            }
        }
    }

    // We allow a system property to specify that we should use the classic Chromium powered
    // WebView. This enables us to switch between implementations at runtime.
    private static boolean isClassicWebViewEnabled() {
        String forceProviderName = SystemProperties.get(FORCE_PROVIDER_PROPERTY);
        if (forceProviderName.isEmpty()) return false;

        Log.i(LOGTAG, String.format("Provider overridden by property: %s=%s",
                FORCE_PROVIDER_PROPERTY, forceProviderName));
        if (forceProviderName.equals(FORCE_PROVIDER_PROPERTY_VALUE_CHROMIUM)) return false;
        if (forceProviderName.equals(FORCE_PROVIDER_PROPERTY_VALUE_CLASSIC)) return true;
        Log.e(LOGTAG, String.format("Unrecognized provider: %s", forceProviderName));
        return false;
    }

    private static Class<WebViewFactoryProvider> getFactoryClass() throws ClassNotFoundException {
        if (isClassicWebViewEnabled()) {
            return (Class<WebViewFactoryProvider>) Class.forName(CLASSIC_WEBVIEW_FACTORY);
        } else  {
            return (Class<WebViewFactoryProvider>) Class.forName(CHROMIUM_WEBVIEW_FACTORY);
        }
    }
}
