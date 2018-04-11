package jerome.com.usbcamera.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by yanbo on 2018/3/17.
 */

public class FaceDetectView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final String TAG = "FaceDetectView";
    protected Context mContext;
    private SurfaceHolder mSurfaceHolder;

    private boolean mCameraExists = false;
    private boolean mShouldStop = false;
    private ImageProc mUsbCameraNative = new ImageProc();
    private boolean mCaptureFlag = false;

    private MediaMuxerCore mediaMuxerCore;
    private int mCurrStreamId;
    private OnPictureSaved onPictureSaved = null;
    private OnCameraListener onCameraListener = null;

    public FaceDetectView(Context context) {
        super(context);
        initFaceDetectView(context);
    }

    public FaceDetectView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        initFaceDetectView(context);
    }

    private void initFaceDetectView(Context context) {
        this.mContext = context;
        setFocusable(true);
        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceCreated");

        int ret = mUsbCameraNative.nativePrepareCamera(
                ImageProc.IMG_WIDTH,
                ImageProc.IMG_HEIGHT,
                ImageProc.CAMERA_PIX_FMT_MJPEG);
        if (ret != -1) {
            mCameraExists = true;
            new Thread(this).start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        Log.d(TAG, "surfaceChanged");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mShouldStop = true;
        Log.d(TAG, "surfaceDestroyed");
        if (mCameraExists) {
            while (mShouldStop) {
                try {
                    Thread.sleep(100);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        mUsbCameraNative.nativeStopCamera();
        mCameraExists = false;
    }

    public boolean isCameraReady() {
        return mCameraExists;
    }

    public void capturePicture() {
        if (!mCameraExists || mCaptureFlag){
            return;
        }
        mCaptureFlag = true;
    }

    @Override
    public void run() {
        int dw, dh;
        Rect rect = null;
        int winWidth = 0;
        int winHeight = 0;

        Bitmap bmp = Bitmap.createBitmap(
                ImageProc.IMG_WIDTH,
                ImageProc.IMG_HEIGHT,
                Bitmap.Config.ARGB_8888);

        Rect[] rects = null;
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);

        while (mCameraExists) {
            mUsbCameraNative.nativeProcessCamera();
            mUsbCameraNative.nativePixelToBmp(bmp);
            if (winWidth == 0) {
				winWidth = FaceDetectView.this.getWidth();
				winHeight = FaceDetectView.this.getHeight();
                rect = new Rect(0, 0, winWidth, winHeight);
            }

            if (onCameraListener != null) {
                rects = (Rect[])onCameraListener.onPreview(bmp, mUsbCameraNative.nativePixelToByteArray(), ImageProc.IMG_WIDTH, ImageProc.IMG_HEIGHT);
                Log.d(TAG, "run: rects length" + rects.length);
            }

            Canvas canvas = getHolder().lockCanvas();
            if (canvas != null) {
                canvas.drawBitmap(bmp, null, rect, null);
                if (rects != null) {
                    for (int i = 0; i < rects.length; i++){
//                        canvas.drawRect(rects[i], paint);
                    }
                }
                getHolder().unlockCanvasAndPost(canvas);
            }
            if (mCaptureFlag) {
                if (onPictureSaved != null) {
                    onPictureSaved.onPictureSaved(bmp);
                }
                mCaptureFlag = false;
            }

            if (mShouldStop) {
                mShouldStop = false;
                break;
            }
        }
    }

    public void setSavedCallback(OnPictureSaved cb) {
        onPictureSaved = cb;
    }

    public interface OnPictureSaved {
        void onPictureSaved(Bitmap bmp);
    }

    public void setCameraListener(OnCameraListener cb) {
        onCameraListener = cb;
    }

    public interface OnCameraListener {
        Object onPreview(Bitmap bmp, byte[] data, int width, int height);
    }
}
