package com.decay.zxing.camera;

import com.decay.zxing.SourceData;

/**
 * Callback for camera previews.
 */
public interface PreviewCallback {
    void onPreview(SourceData sourceData);

    void onPreviewError(Exception e);
}
