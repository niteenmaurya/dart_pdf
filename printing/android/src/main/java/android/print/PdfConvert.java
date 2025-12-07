/*
 * Copyright (C) 2017, David PHAM-VAN <dev.nfet.net@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.print;

import android.content.Context;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class PdfConvert {
    private static final String TAG = "PdfConvert";

    public static void print(final Context context, final PrintDocumentAdapter adapter,
            final PrintAttributes attributes, final Result result) {
        
        // ✅ Basic validation
        if (context == null || adapter == null || attributes == null || result == null) {
            Log.e(TAG, "Null parameters passed");
            if (result != null) {
                result.onError("ERROR_NULL_PARAMS: Invalid parameters provided");
            }
            return;
        }

        adapter.onLayout(null, attributes, null, new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                File outputDir = context.getCacheDir();
                File outputFile;
                try {
                    outputFile = File.createTempFile("printing", "pdf", outputDir);
                } catch (IOException e) {
                    result.onError("ERROR_FILE_CREATE: " + (e.getMessage() != null ? e.getMessage() : "Failed to create temp file"));
                    return;
                }

                try {
                    final File finalOutputFile = outputFile;
                    adapter.onWrite(new PageRange[] {PageRange.ALL_PAGES},
                            ParcelFileDescriptor.open(
                                    outputFile, ParcelFileDescriptor.MODE_READ_WRITE),
                            new CancellationSignal(),
                            new PrintDocumentAdapter.WriteResultCallback() {
                                @Override
                                public void onWriteFinished(PageRange[] pages) {
                                    super.onWriteFinished(pages);

                                    // ✅ NULL CHECK
                                    if (pages == null) {
                                        cleanupFile(finalOutputFile);
                                        result.onError("ERROR_NULL_PAGES: Pages array is null");
                                        return;
                                    }

                                    // ✅ EMPTY CHECK
                                    if (pages.length == 0) {
                                        cleanupFile(finalOutputFile);
                                        result.onError("ERROR_NO_PAGES: No pages were created during PDF generation");
                                        return;
                                    }

                                    // ✅ SUCCESS - Don't delete file yet!
                                    // Flutter will read this file
                                    result.onSuccess(finalOutputFile);
                                }

                                // ✅ NEW: Handle write failures
                                @Override
                                public void onWriteFailed(CharSequence error) {
                                    super.onWriteFailed(error);
                                    cleanupFile(finalOutputFile);
                                    
                                    String errorMsg = (error != null && error.length() > 0) 
                                        ? error.toString() 
                                        : "Unknown write error";
                                    result.onError("ERROR_WRITE_FAILED: " + errorMsg);
                                }

                                // ✅ NEW: Handle write cancellation
                                @Override
                                public void onWriteCancelled() {
                                    super.onWriteCancelled();
                                    cleanupFile(finalOutputFile);
                                    result.onError("ERROR_WRITE_CANCELLED: PDF write operation was cancelled");
                                }
                            });
                } catch (FileNotFoundException e) {
                    cleanupFile(outputFile);
                    result.onError("ERROR_FILE_NOT_FOUND: " + (e.getMessage() != null ? e.getMessage() : "File not found"));
                } catch (Exception e) {
                    // ✅ Catch any other exceptions
                    cleanupFile(outputFile);
                    result.onError("ERROR_UNEXPECTED: " + (e.getMessage() != null ? e.getMessage() : "Unexpected error during PDF creation"));
                }
            }

            // ✅ NEW: Handle layout failures
            @Override
            public void onLayoutFailed(CharSequence error) {
                super.onLayoutFailed(error);
                
                String errorMsg = (error != null && error.length() > 0) 
                    ? error.toString() 
                    : "Unknown layout error";
                result.onError("ERROR_LAYOUT_FAILED: " + errorMsg);
            }

            // ✅ NEW: Handle layout cancellation
            @Override
            public void onLayoutCancelled() {
                super.onLayoutCancelled();
                result.onError("ERROR_LAYOUT_CANCELLED: PDF layout operation was cancelled");
            }
        }, null);
    }

    /**
     * ✅ Helper method to safely cleanup temp files
     */
    private static void cleanupFile(File file) {
        if (file != null && file.exists()) {
            if (!file.delete()) {
                Log.w(TAG, "Unable to delete temporary file: " + file.getAbsolutePath());
            }
        }
    }

    /**
     * Reads file content into byte array
     */
    public static byte[] readFile(File file) throws IOException {
        if (file == null) {
            throw new IOException("ERROR_NULL_FILE: File is null");
        }
        
        if (!file.exists()) {
            throw new IOException("ERROR_FILE_NOT_EXIST: File does not exist at " + file.getAbsolutePath());
        }

        long fileLength = file.length();
        if (fileLength == 0) {
            throw new IOException("ERROR_EMPTY_FILE: File is empty");
        }

        if (fileLength > Integer.MAX_VALUE) {
            throw new IOException("ERROR_FILE_TOO_LARGE: File size exceeds maximum (" + fileLength + " bytes)");
        }

        byte[] buffer = new byte[(int) fileLength];
        try (InputStream ios = new FileInputStream(file)) {
            int bytesRead = ios.read(buffer);
            if (bytesRead == -1) {
                throw new IOException("ERROR_READ_EOF: Reached EOF while reading file");
            }
            if (bytesRead != fileLength) {
                throw new IOException("ERROR_INCOMPLETE_READ: Expected " + fileLength + " bytes, read " + bytesRead + " bytes");
            }
        }
        return buffer;
    }

    public interface Result {
        void onSuccess(File file);
        void onError(String message);
    }
}
