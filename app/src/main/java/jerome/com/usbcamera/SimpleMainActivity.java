package jerome.com.usbcamera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.arcsoft.facerecognition.AFR_FSDKEngine;
import com.arcsoft.facerecognition.AFR_FSDKError;
import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKMatching;
import com.arcsoft.facerecognition.AFR_FSDKVersion;
import com.arcsoft.facetracking.AFT_FSDKEngine;
import com.arcsoft.facetracking.AFT_FSDKError;
import com.arcsoft.facetracking.AFT_FSDKFace;
import com.arcsoft.facetracking.AFT_FSDKVersion;
import com.guo.android_extend.java.AbsLoop;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yanbo on 2018/3/22.
 */

public class SimpleMainActivity extends Activity implements FaceDetectView.OnCameraListener, View.OnClickListener {

    final private String TAG = this.getClass().toString();
    private final static int MSG_CODE = 0x1000;
    private final static int MSG_EVENT_REG = 0x1001;
    private final static int MSG_EVENT_MATCH = 0x1002;
    private final static int MSG_EVENT_START = 0x1003;

    private UIHandler mUIHandler = new UIHandler();
    private FRAbsLoop mFRAbsLoop = new FRAbsLoop();

    private FaceDetectView mUsbCamera;
    private ImageView mImgCertificate;
    private ImageView mImgMatched;
    private ImageButton mBtnRegister;

    private AFT_FSDKVersion tVersion = new AFT_FSDKVersion();
    private AFT_FSDKEngine tEngine = new AFT_FSDKEngine();
    private List<AFT_FSDKFace> tFaces = new ArrayList<AFT_FSDKFace>();

    private byte[] mImageNV21 = null;
    private AFR_FSDKFace rFace = null;
    private AFT_FSDKFace tFace = null;

    private boolean mGetCertificate = false;
    private boolean mMatched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);

        this.setContentView(R.layout.simple_main);
        mUsbCamera = (FaceDetectView) findViewById(R.id.detect_view);
        mUsbCamera.setCameraListener(this);

        mImgCertificate = (ImageView) findViewById(R.id.img_certificate);
        mImgCertificate.setVisibility(View.INVISIBLE);
        mImgMatched = (ImageView) findViewById(R.id.img_matched);
        mImgMatched.setVisibility(View.INVISIBLE);
        mBtnRegister = (ImageButton) findViewById(R.id.btn_register);
        mBtnRegister.setOnClickListener(this);
        mBtnRegister.setVisibility(View.VISIBLE);

        AFT_FSDKError tError = tEngine.AFT_FSDK_InitialFaceEngine(FaceDB.appid, FaceDB.ft_key, AFT_FSDKEngine.AFT_OPF_0_HIGHER_EXT, 16, 5);
        Log.d(TAG, "onCreate: AFT_FSDK_InitialFaceEngine = " + tError.getCode());
        tError = tEngine.AFT_FSDK_GetVersion(tVersion);
        Log.d(TAG, "onCreate: AFT_FSDK_GetVersion " +  tVersion.toString());

        mMatched = false;
        mGetCertificate = false;
    }

    @Override
    public Object onPreview(byte[] data, int width, int height) {
        AFT_FSDKError tError = tEngine.AFT_FSDK_FaceFeatureDetect(data, width, height, AFT_FSDKEngine.CP_PAF_NV21, tFaces);
        for (AFT_FSDKFace face : tFaces) {
            Log.d(TAG, "onPreview: Face: " + face.toString());
        }
        Rect[] rects = new Rect[tFaces.size()];
        for (int i = 0; i < tFaces.size(); i++) {
            rects[i] = new Rect(tFaces.get(i).getRect());
        }
        if (!tFaces.isEmpty()) {
            if (mGetCertificate) {
                tFace = tFaces.get(0).clone();
                mImageNV21 = data.clone();
            }
        }
        tFaces.clear();
        return rects;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_register) {
            //todo: enter register activity
        }
    }

    class UIHandler extends Handler {
        @Override
        public void handleMessage(final Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_CODE) {
                if (msg.arg1 == MSG_EVENT_REG) {
                    final Bitmap face = (Bitmap) msg.obj;
                    mImgMatched.setImageBitmap(face);
                    mImgMatched.setVisibility(View.VISIBLE);
                } else if (msg.arg1 == MSG_EVENT_START) {
                    mFRAbsLoop = new FRAbsLoop();
                    mFRAbsLoop.start();
                } else if (msg.arg1 == MSG_EVENT_MATCH) {
                    mFRAbsLoop.shutdown();
                    Log.i(TAG, "handleMessage: MSG_EVENT_MATCH");
                }
            }
        }
    }

    class FRAbsLoop extends AbsLoop {

        AFR_FSDKVersion version = new AFR_FSDKVersion();
        AFR_FSDKEngine engine = new AFR_FSDKEngine();
        AFR_FSDKFace result = new AFR_FSDKFace();

        @Override
        public void setup() {
            AFR_FSDKError error = engine.AFR_FSDK_InitialEngine(FaceDB.appid, FaceDB.fr_key);
            Log.d(TAG, "AFR_FSDK_InitialEngine = " + error.getCode());
            error = engine.AFR_FSDK_GetVersion(version);
            Log.d(TAG, "FR=" + version.toString() + "," + error.getCode());
        }

        @Override
        public void loop() {
            if (mImageNV21 != null) {
                long time = System.currentTimeMillis();
                AFR_FSDKError rError = engine.AFR_FSDK_ExtractFRFeature(mImageNV21, ImageProc.IMG_WIDTH, ImageProc.IMG_HEIGHT, AFR_FSDKEngine.CP_PAF_NV21, tFace.getRect(), tFace.getDegree(), result);
                Log.e(TAG, "AFR_FSDK_ExtractFRFeature cost :" + (System.currentTimeMillis() - time) + "ms");
                Log.e(TAG, "Face=" + result.getFeatureData()[0] + "," + result.getFeatureData()[1] + "," + result.getFeatureData()[2] + "," + rError.getCode());
                AFR_FSDKMatching score = new AFR_FSDKMatching();
                rError = engine.AFR_FSDK_FacePairMatching(result, rFace, score);
                Log.e(TAG, "Score:" + score.getScore() + ", mAFR_FSDKFace=" + (rFace == null) + ", AFR_FSDK_FacePairMatching=" + rError.getCode());
                if (score.getScore() >= 0.5) {
                    Message msg = new Message();
                    msg.what = MSG_CODE;
                    msg.arg1 = MSG_EVENT_MATCH;
                    mUIHandler.sendMessage(msg);
                }
                mImageNV21 = null;
            }
        }

        @Override
        public void over() {
            AFR_FSDKError error = engine.AFR_FSDK_UninitialEngine();
            Log.d(TAG, "AFR_FSDK_UninitialEngine : " + error.getCode());
        }
    }
}
