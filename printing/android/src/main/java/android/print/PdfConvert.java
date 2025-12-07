/*
 * Copyright (C) 2017, David PHAM-VAN <dev.nfet.net@gmail.com>
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
        
        // ✅ Minimal null check
        if (context == null || adapter == null || attributes == null || result == null) {
            Log.e(TAG, "Null parameters");
            if (result != null) {
                result.onError("Invalid parameters");
            }
            return;
        }
        
        final CancellationSignal cancellationSignal = new CancellationSignal();

        adapter.onLayout(null, attributes, cancellationSignal, 
            new PrintDocumentAdapter.LayoutResultCallback() {
            
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                File outputDir = context.getCacheDir();
                File outputFile;
                
                try {
                    outputFile = File.createTempFile("printing", "pdf", outputDir);
                } catch (IOException e) {
                    result.onError(e.getMessage() != null ? e.getMessage() : "Failed to create temp file");
                    return;
                }

                final File finalOutputFile = outputFile;
                
                try {
                    adapter.onWrite(
                        new PageRange[] {PageRange.ALL_PAGES},
                        ParcelFileDescriptor.open(finalOutputFile, ParcelFileDescriptor.MODE_READ_WRITE),
                        cancellationSignal,
                        new PrintDocumentAdapter.WriteResultCallback() {
                            
                            @Override
                            public void onWriteFinished(PageRange[] pages) {
                                super.onWriteFinished(pages);
                                
                                // ✅ Simple validation
                                if (pages != null && pages.length > 0) {
                                    result.onSuccess(finalOutputFile);
                                } else {
                                    // Clean up only on error
                                    if (finalOutputFile.exists()) {
                                        finalOutputFile.delete();
                                    }
                                    result.onError("No pages created");
                                }
                            }

                            @Override
                            public void onWriteFailed(CharSequence error) {
                                super.onWriteFailed(error);
                                
                                // Clean up on failure
                                if (finalOutputFile != null && finalOutputFile.exists()) {
                                    finalOutputFile.delete();
                                }
                                
                                String errorMsg = (error != null && error.length() > 0) 
                                    ? error.toString() 
                                    : "Write failed";
                                result.onError(errorMsg);
                            }

                            @Override
                            public void onWriteCancelled() {
                                super.onWriteCancelled();
                                
                                // Clean up on cancel
                                if (finalOutputFile != null && finalOutputFile.exists()) {
                                    finalOutputFile.delete();
                                }
                                result.onError("Write cancelled");
                            }
                        }
                    );
                    
                } catch (FileNotFoundException e) {
                    if (finalOutputFile != null && finalOutputFile.exists()) {
                        finalOutputFile.delete();
                    }
                    result.onError(e.getMessage() != null ? e.getMessage() : "File not found");
                }
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                super.onLayoutFailed(error);
                
                String errorMsg = (error != null && error.length() > 0) 
                    ? error.toString() 
                    : "Layout failed";
                result.onError(errorMsg);
            }

            @Override
            public void onLayoutCancelled() {
                super.onLayoutCancelled();
                result.onError("Layout cancelled");
            }
            
        }, null);
    }

    /**
     * Reads file content into byte array
     */
    public static byte[] readFile(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("File does not exist");
        }
        
        long fileLength = file.length();
        if (fileLength > Integer.MAX_VALUE) {
            throw new IOException("File too large");
        }
        
        byte[] buffer = new byte[(int) fileLength];
        
        try (InputStream ios = new FileInputStream(file)) {
            if (ios.read(buffer) == -1) {
                throw new IOException("Could not read file");
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
