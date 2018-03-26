package jerome.com.usbcamera;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;

import com.arcsoft.facetracking.AFT_FSDKEngine;
import com.arcsoft.facetracking.AFT_FSDKError;
import com.arcsoft.facetracking.AFT_FSDKFace;
import com.arcsoft.facetracking.AFT_FSDKVersion;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import jerome.com.usbcamera.utils.FaceDB;
import jerome.com.usbcamera.utils.FaceDetectView;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener, FaceDetectView.OnPictureSaved, FaceDetectView.OnCameraListener {

    private final String TAG = this.getClass().getSimpleName();
    private ImageButton mCaptureButton;
    private FaceDetectView mUsbCamera;

    AFT_FSDKVersion version = new AFT_FSDKVersion();
    AFT_FSDKEngine engine = new AFT_FSDKEngine();
    List<AFT_FSDKFace> result = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);

        mCaptureButton = (ImageButton) findViewById(R.id.capture_btn);
        mCaptureButton.setOnClickListener(this);

        mUsbCamera = (FaceDetectView) findViewById(R.id.camera_view);
        mUsbCamera.setSavedCallback(this);
        mUsbCamera.setCameraListener(this);

        AFT_FSDKError err = engine.AFT_FSDK_InitialFaceEngine(FaceDB.appid, FaceDB.ft_key, AFT_FSDKEngine.AFT_OPF_0_HIGHER_EXT, 16, 5);
        Log.d(TAG, "AFT_FSDK_InitialFaceEngine =" + err.getCode());
        err = engine.AFT_FSDK_GetVersion(version);
        Log.d(TAG, "AFT_FSDK_GetVersion:" + version.toString() + "," + err.getCode());
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.capture_btn:
                mUsbCamera.capturePicture();
                break;
        }
    }

    @Override
    public void onPictureSaved(Bitmap bmp) {
        Intent it = new Intent(CameraActivity.this, RegisterActivity.class);
        Bundle bundle = new Bundle();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        bundle.putByteArray("image", baos.toByteArray());
        it.putExtras(bundle);
        startActivity(it);
    }

    @Override
    public Object onPreview(Bitmap bmp, byte[] data, int width, int height) {
        AFT_FSDKError err = engine.AFT_FSDK_FaceFeatureDetect(data, width, height, AFT_FSDKEngine.CP_PAF_NV21, result);
        for (AFT_FSDKFace face : result) {
            Log.d(TAG, "Face:" + face.toString());
        }
        //copy rects
        Rect[] rects = new Rect[result.size()];
        for (int i = 0; i < result.size(); i++) {
            rects[i] = new Rect(result.get(i).getRect());
        }
        //clear result.
        result.clear();
        //return the rects for render.
        return rects;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        AFT_FSDKError err = engine.AFT_FSDK_UninitialFaceEngine();
        Log.d(TAG, "AFT_FSDK_UninitialFaceEngine =" + err.getCode());
    }
}
