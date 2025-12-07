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
import android.text.TextUtils;
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
        
        // Null safety checks
        if (context == null || adapter == null || attributes == null || result == null) {
            Log.e(TAG, "Null parameters passed to print method");
            if (result != null) {
                result.onError("Invalid parameters: null context, adapter, or attributes");
            }
            return;
        }
        
        final CancellationSignal cancellationSignal = new CancellationSignal();

        adapter.onLayout(null, attributes, cancellationSignal, 
            new PrintDocumentAdapter.LayoutResultCallback() {
            
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                File outputDir = context.getCacheDir();
                File outputFile = null;
                
                try {
                    outputFile = File.createTempFile("printing", "pdf", outputDir);
                } catch (IOException e) {
                    String errorMsg = e.getMessage();
                    result.onError(errorMsg != null ? errorMsg : "Failed to create temp file");
                    return;
                }

                final File finalOutputFile = outputFile;
                
                try {
                    adapter.onWrite(
                        new PageRange[] {PageRange.ALL_PAGES},
                        ParcelFileDescriptor.open(
                            finalOutputFile, 
                            ParcelFileDescriptor.MODE_READ_WRITE
                        ),
                        cancellationSignal,
                        new PrintDocumentAdapter.WriteResultCallback() {
                            
                            @Override
                            public void onWriteFinished(PageRange[] pages) {
                                super.onWriteFinished(pages);
                                
                                // Null safety check for pages array
                                if (pages == null || pages.length == 0) {
                                    cleanupFile(finalOutputFile);
                                    result.onError("No pages created");
                                    return;
                                }
                                
                                // Success - file को अभी delete मत करो
                                // यह Dart/Flutter layer में read होगी
                                result.onSuccess(finalOutputFile);
                            }

                            @Override
                            public void onWriteFailed(CharSequence error) {
                                super.onWriteFailed(error);
                                
                                cleanupFile(finalOutputFile);
                                
                                // Safe null handling for CharSequence
                                String errorMsg = TextUtils.isEmpty(error) 
                                    ? "Write operation failed" 
                                    : error.toString();
                                
                                result.onError("Write failed: " + errorMsg);
                            }

                            @Override
                            public void onWriteCancelled() {
                                super.onWriteCancelled();
                                
                                cleanupFile(finalOutputFile);
                                result.onError("Write operation cancelled by user");
                            }
                        }
                    );
                    
                } catch (FileNotFoundException e) {
                    cleanupFile(finalOutputFile);
                    
                    String errorMsg = e.getMessage();
                    result.onError(errorMsg != null ? errorMsg : "File not found");
                    
                } catch (Exception e) {
                    // Catch any unexpected exceptions
                    cleanupFile(finalOutputFile);
                    
                    String errorMsg = e.getMessage();
                    result.onError("Unexpected error: " + 
                        (errorMsg != null ? errorMsg : "Unknown error"));
                }
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                super.onLayoutFailed(error);
                
                // Safe null handling for CharSequence
                String errorMsg = TextUtils.isEmpty(error) 
                    ? "Layout operation failed" 
                    : error.toString();
                
                result.onError("Layout failed: " + errorMsg);
            }

            @Override
            public void onLayoutCancelled() {
                super.onLayoutCancelled();
                result.onError("Layout operation cancelled by user");
            }
            
        }, null);
    }

    /**
     * Helper method to safely delete temporary files
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
     * @throws IOException if file reading fails
     */
    public static byte[] readFile(File file) throws IOException {
        if (file == null) {
            throw new IOException("File is null");
        }
        
        if (!file.exists()) {
            throw new IOException("File does not exist: " + file.getAbsolutePath());
        }
        
        long fileLength = file.length();
        if (fileLength > Integer.MAX_VALUE) {
            throw new IOException("File is too large: " + fileLength + " bytes");
        }
        
        byte[] buffer = new byte[(int) fileLength];
        
        try (InputStream ios = new FileInputStream(file)) {
            int bytesRead = ios.read(buffer);
            
            if (bytesRead == -1) {
                throw new IOException("EOF reached while trying to read the whole file");
            }
            
            if (bytesRead != fileLength) {
                throw new IOException("Could not completely read file. Expected: " + 
                    fileLength + " bytes, Read: " + bytesRead + " bytes");
            }
        }
        
        return buffer;
    }

    /**
     * Result callback interface
     */
    public interface Result {
        void onSuccess(File file);
        void onError(String message);
    }
}
