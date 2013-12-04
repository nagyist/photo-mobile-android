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

package com.trovebox.android.common.bitmapfun.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.trovebox.android.common.BuildConfig;
import com.trovebox.android.common.R;
import com.trovebox.android.common.util.CommonUtils;
import com.trovebox.android.common.util.GuiUtils;
import com.trovebox.android.common.util.LoadingControl;
import com.trovebox.android.common.util.TrackerUtils;

/**
 * A simple subclass of {@link ImageResizer} that fetches and resizes images
 * fetched from a URL.
 */
public class ImageFetcher extends ImageResizer {
    private static final String TAG = "ImageFetcher";
    private static final int HTTP_CACHE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final String HTTP_CACHE_DIR = "http";
    boolean mCheckLoggedIn = true;

    /**
     * Initialize providing a target image width and height for the processing
     * images.
     * 
     * @param context
     * @param loadingControl
     * @param imageWidth
     * @param imageHeight
     */
    public ImageFetcher(Context context, LoadingControl loadingControl, int imageWidth,
            int imageHeight) {
        this(context, loadingControl, imageWidth, imageHeight, -1);
    }

    /**
     * Initialize providing a target image width and height for the processing
     * images.
     * 
     * @param context
     * @param loadingControl
     * @param imageWidth
     * @param imageHeight
     * @param cornerRadius radius to round image corners. Ignored if <=0
     */
    public ImageFetcher(Context context, LoadingControl loadingControl, int imageWidth,
            int imageHeight, int cornerRadius) {
        super(context, loadingControl, imageWidth, imageHeight, cornerRadius);
        init(context);
    }

    /**
     * Initialize providing a single target image size (used for both width and
     * height);
     * 
     * @param context
     * @param loadingControl
     * @param imageSize
     */
    public ImageFetcher(Context context, LoadingControl loadingControl, int imageSize) {
        super(context, loadingControl, imageSize);
        init(context);
    }

    private void init(Context context) {
        checkConnection(context);
    }

    /**
     * Simple network connection check.
     * 
     * @param context
     */
    private void checkConnection(Context context) {
        GuiUtils.checkOnline(false);
    }

    /**
     * The main process method, which will be called by the ImageWorker in the
     * AsyncTaskEx background thread.
     * 
     * @param data The data to load the bitmap, in this case, a regular http URL
     * @param processingState may be used to determine whether the processing is
     *            cancelled during long operations
     * @return The downloaded and resized bitmap
     */
    private Bitmap processBitmap(String data, ProcessingState processingState) {
        return processBitmap(data, imageWidth, imageHeight, processingState);
    }

    /**
     * The main process method, which will be called by the ImageWorker in the
     * AsyncTaskEx background thread.
     * 
     * @param data The data to load the bitmap, in this case, a regular http URL
     * @param imageWidth
     * @param imageHeight
     * @param processingState may be used to determine whether the processing is
     *            cancelled during long operations
     * @return The downloaded and resized bitmap
     */
    protected Bitmap processBitmap(String data, int imageWidth, int imageHeight,
            ProcessingState processingState) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "processBitmap - " + data);
        }

        // Download a bitmap, write it to a file
        final File f = downloadBitmap(mContext, data, mCheckLoggedIn, processingState);

        if (f != null) {
            try {
                // Return a sampled down version
                return decodeSampledBitmapFromFile(f.toString(), imageWidth, imageHeight,
                        cornerRadius);
            } catch (Exception ex) {
                GuiUtils.error(TAG, ex);
            }
        }

        return null;
    }

    @Override
    protected Bitmap processBitmap(Object data, ProcessingState processingState) {
        return processBitmap(String.valueOf(data), processingState);
    }

    /**
     * Download a bitmap from a URL, write it to a disk and return the File
     * pointer. This implementation uses a simple disk cache.
     * 
     * @param context The context to use
     * @param urlString The URL to fetch
     * @param checkLoggedIn whether to check user logged in condition
     * @param processingState may be used to determine whether the processing is
     *            cancelled during long operations
     * @return A File pointing to the fetched bitmap
     */
    public static File downloadBitmap(Context context, String urlString, boolean checkLoggedIn,
            ProcessingState processingState) {
        final File cacheDir = DiskLruCache.getDiskCacheDir(context, HTTP_CACHE_DIR);

        if (CommonUtils.TEST_CASE && urlString == null) {
            return null;
        }
        DiskLruCache cache = DiskLruCache.openCache(context, cacheDir, HTTP_CACHE_SIZE);
        // #273 additional checks
        if (cache == null) {
            CommonUtils.debug(TAG, "Failed to open http cache %1$s", cacheDir.getAbsolutePath());
            TrackerUtils.trackBackgroundEvent("httpCacheOpenFail", cacheDir.getAbsolutePath());
            // cache open may fail if there are not enough free space.
            // application will try to clear that cache dir and open cache again
            DiskLruCache.clearCache(context, HTTP_CACHE_DIR);

            // cache clear attempt finished. Let's try again to open cache
            cache = DiskLruCache.openCache(context, cacheDir, HTTP_CACHE_SIZE);
            if (cache == null) {
                CommonUtils.debug(TAG, "Failed to open http cache second time %1$s",
                        cacheDir.getAbsolutePath());
                // still unsuccessful. We can't download that bitmap. Let's warn
                // user about this.
                GuiUtils.alert(R.string.errorCouldNotStoreDownloadablePhotoNotEnoughSpace);
                TrackerUtils.trackBackgroundEvent("httpCacheSecondOpenFail",
                        cacheDir.getAbsolutePath());
                return null;
            }
        }
        if (processingState != null && processingState.isProcessingCancelled()) {
            return null;
        }
        final File cacheFile = new File(cache.createFilePath(urlString));

        if (cache.containsKey(urlString)) {
            TrackerUtils.trackBackgroundEvent("httpCachHit", TAG);
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "downloadBitmap - found in http cache - " + urlString);
            }
            return cacheFile;
        }
        if (!GuiUtils.checkOnline(true)) {
            return null;
        }
        if (checkLoggedIn && !GuiUtils.checkLoggedIn(true)) {
            return null;
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "downloadBitmap - downloading - " + urlString);
        }

        Utils.disableConnectionReuseIfNecessary();
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;

        try {
            long start = System.currentTimeMillis();
            final URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();

            final InputStream in = new BufferedInputStream(urlConnection.getInputStream(),
                    Utils.IO_BUFFER_SIZE);
            File tempFile = File.createTempFile(DiskLruCache.CACHE_FILENAME_PREFIX + "udl"
                    + cacheFile.getName(), null, cache.getCacheDir());
            out = new BufferedOutputStream(new FileOutputStream(tempFile), Utils.IO_BUFFER_SIZE);

            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
                if (processingState != null && processingState.isProcessingCancelled()) {
                    CommonUtils.debug(TAG,
                            "downloadBitmap: processing is cancelled. Removing temp file %1$s",
                            tempFile.getAbsolutePath());
                    out.close();
                    out = null;
                    tempFile.delete();
                    return null;
                }
            }
            TrackerUtils.trackDataLoadTiming(System.currentTimeMillis() - start, "downloadBitmap",
                    TAG);
            if (!cacheFile.exists()) {
                CommonUtils.debug(TAG,
                        "downloadBitmap: cache file %1$s doesn't exist, renaming downloaded data",
                        cacheFile.getAbsolutePath());
                if (!tempFile.renameTo(cacheFile)) {
                    return null;
                }
            } else {
                CommonUtils.debug(TAG,
                        "downloadBitmap: cache file %1$s exists, removing downloaded data",
                        cacheFile.getAbsolutePath());
                tempFile.delete();
            }
            return cacheFile;

        } catch (final IOException e) {
            GuiUtils.noAlertError(TAG, "Error in downloadBitmap", e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (out != null) {
                try {
                    out.close();
                } catch (final IOException e) {
                    GuiUtils.noAlertError(TAG, "Error in downloadBitmap", e);
                }
            }
        }

        return null;
    }

    public boolean isCheckLoggedIn() {
        return mCheckLoggedIn;
    }

    public void setCheckLoggedIn(boolean mCheckLoggedIn) {
        this.mCheckLoggedIn = mCheckLoggedIn;
    }
}
