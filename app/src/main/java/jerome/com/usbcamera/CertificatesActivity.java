package jerome.com.usbcamera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.arcsoft.facedetection.AFD_FSDKEngine;
import com.arcsoft.facedetection.AFD_FSDKError;
import com.arcsoft.facedetection.AFD_FSDKFace;
import com.arcsoft.facedetection.AFD_FSDKVersion;
import com.arcsoft.facerecognition.AFR_FSDKEngine;
import com.arcsoft.facerecognition.AFR_FSDKError;
import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKMatching;
import com.arcsoft.facerecognition.AFR_FSDKVersion;
import com.arcsoft.facetracking.AFT_FSDKEngine;
import com.arcsoft.facetracking.AFT_FSDKError;
import com.arcsoft.facetracking.AFT_FSDKFace;
import com.arcsoft.facetracking.AFT_FSDKVersion;
import com.guo.android_extend.image.ImageConverter;
import com.guo.android_extend.java.AbsLoop;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yanbo on 2018/3/20.
 */

public class CertificatesActivity extends AppCompatActivity implements FaceDetectView.OnCameraListener, FaceDetectView.OnPictureSaved, View.OnClickListener {

    private final String TAG = this.getClass().getSimpleName();
    private final static int MSG_CODE = 0x1000;
    private final static int MSG_EVENT_REG = 0x1001;
    private final static int MSG_EVENT_MATCH = 0x1002;
    private UIHandler mUIHandler = new UIHandler();

    private ImageButton mCaptureButton;
    private ImageView mImageView;
    private TextView mTextView;
    private FaceDetectView mUsbCamera;

    private AFT_FSDKVersion version = new AFT_FSDKVersion();
    private AFT_FSDKEngine engine = new AFT_FSDKEngine();
    private List<AFT_FSDKFace> result = new ArrayList<>();

    private Bitmap mBitmap = null;
    private byte[] mImageNV21 = null;
    private AFR_FSDKFace mAFR_FSDKFace = null;
    private AFT_FSDKFace mAFT_FSDKFace = null;
    private boolean mCatchCertificate = false;

    private FRAbsLoop mFRAbsLoop = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.certificates_activity);

        mCaptureButton = (ImageButton) findViewById(R.id.capture_btn);
        mCaptureButton.setOnClickListener(this);

        mImageView = (ImageView) findViewById(R.id.img_certificates);
        mImageView.setVisibility(View.INVISIBLE);

        mTextView = (TextView) findViewById(R.id.text_result);
        mTextView.setVisibility(View.INVISIBLE);

        mUsbCamera = (FaceDetectView) findViewById(R.id.faceDetectView);
        mUsbCamera.setSavedCallback(this);
        mUsbCamera.setCameraListener(this);

        AFT_FSDKError err = engine.AFT_FSDK_InitialFaceEngine(FaceDB.appid, FaceDB.ft_key, AFT_FSDKEngine.AFT_OPF_0_HIGHER_EXT, 16, 5);
        Log.d(TAG, "AFT_FSDK_InitialFaceEngine = " + err.getCode());
        err = engine.AFT_FSDK_GetVersion(version);
        Log.d(TAG, "AFT_FSDK_GetVersion:" + version.toString() + "," + err.getCode());

        mCatchCertificate = false;
    }

    @Override
    public void onPictureSaved(Bitmap bmp) {
        mBitmap = bmp;
        Rect src = new Rect();
        Rect dst = new Rect();

        src.set(0, 0, bmp.getWidth(), bmp.getHeight());
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] data = new byte[mBitmap.getWidth() * mBitmap.getHeight() * 3 / 2];
                ImageConverter convert = new ImageConverter();
                convert.initial(mBitmap.getWidth(), mBitmap.getHeight(), ImageConverter.CP_PAF_NV21);
                if (convert.convert(mBitmap, data)) {
                    Log.d(TAG, "convert ok");
                }
                convert.destroy();

                AFD_FSDKEngine engine = new AFD_FSDKEngine();
                AFD_FSDKVersion version = new AFD_FSDKVersion();
                List<AFD_FSDKFace> dResult = new ArrayList<AFD_FSDKFace>();
                AFD_FSDKError err = engine.AFD_FSDK_InitialFaceEngine(FaceDB.appid, FaceDB.fd_key, AFD_FSDKEngine.AFD_OPF_0_HIGHER_EXT, 16, 5);
                Log.e(TAG, "AFD_FSDK_InitialFaceEngine = " + err.getCode());
                err = engine.AFD_FSDK_GetVersion(version);
                Log.e(TAG, "AFD_FSDK_GetVersion = " + err.getCode());
                err = engine.AFD_FSDK_StillImageFaceDetection(data, mBitmap.getWidth(), mBitmap.getHeight(), AFD_FSDKEngine.CP_PAF_NV21, dResult);
                Log.e(TAG, "AFD_FSDK_StillImageFaceDetection = " + err.getCode() + "<" + dResult.size());

                if (!dResult.isEmpty()) {
                    AFR_FSDKVersion rVersion = new AFR_FSDKVersion();
                    AFR_FSDKEngine rEngine = new AFR_FSDKEngine();
                    AFR_FSDKError rError = rEngine.AFR_FSDK_InitialEngine(FaceDB.appid, FaceDB.fr_key);
                    AFR_FSDKFace result1 = new AFR_FSDKFace();
                    Log.e(TAG, "onPreview: AFR_FSDK_InitialEngine = " + rError.getCode());
                    rError = rEngine.AFR_FSDK_GetVersion(rVersion);
                    Log.e(TAG, "onPreview: AFR_FSDK_GetVersion = " + rVersion.getFeatureLevel());
                    rError = rEngine.AFR_FSDK_ExtractFRFeature(data, mBitmap.getWidth(), mBitmap.getHeight(), AFR_FSDKEngine.CP_PAF_NV21, new Rect(dResult.get(0).getRect()), dResult.get(0).getDegree(), result1);
                    mAFR_FSDKFace = result1.clone();
                    Log.e(TAG, "onPreview: AFR_FSDK_ExtractFRFeature = " + rError.getCode());
                    rError = rEngine.AFR_FSDK_UninitialEngine();
                    Log.e(TAG, "onPreview: AFR_FSDK_UninitialEngine = " + rError.getCode());

                    AFD_FSDKFace face = dResult.get(0);
                    int width = face.getRect().width();
                    int height = face.getRect().height();
                    Bitmap face_bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                    Canvas face_canvas = new Canvas(face_bitmap);
                    face_canvas.drawBitmap(mBitmap, face.getRect(), new Rect(0, 0, width, height), null);
                    Message reg = Message.obtain();
                    reg.what = MSG_CODE;
                    reg.arg1 = MSG_EVENT_REG;
                    reg.obj = face_bitmap;
                    mUIHandler.sendMessage(reg);
                }
            }
        });
        thread.start();
    }

    @Override
    public Object onPreview(byte[] data, int width, int height) {
        AFT_FSDKError err = engine.AFT_FSDK_FaceFeatureDetect(data, width, height, AFT_FSDKEngine.CP_PAF_NV21, result);
        for (AFT_FSDKFace face : result) {
            Log.d(TAG, "Face:" + face.toString());
        }
        //copy rects
        Rect[] rects = new Rect[result.size()];
        for (int i = 0; i < result.size(); i++) {
            rects[i] = new Rect(result.get(i).getRect());
        }

        if (!result.isEmpty()) {
            if (mCatchCertificate) {
                mAFT_FSDKFace = result.get(0).clone();
                mImageNV21 = data.clone();
            }
        }

        //clear result.
        result.clear();
        //return the rects for render.
        return rects;
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
    protected void onDestroy() {
        super.onDestroy();

        AFT_FSDKError err = engine.AFT_FSDK_UninitialFaceEngine();
        Log.d(TAG, "AFT_FSDK_UninitialFaceEngine =" + err.getCode());

        mFRAbsLoop.shutdown();
    }

    class UIHandler extends Handler {
        @Override
        public void handleMessage(final Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_CODE) {
                if (msg.arg1 == MSG_EVENT_REG) {
                    final Bitmap face = (Bitmap) msg.obj;
                    mImageView.setImageBitmap(face);
                    mImageView.setVisibility(View.VISIBLE);
                    mCaptureButton.setVisibility(View.INVISIBLE);
                    mCatchCertificate = true;
                    mFRAbsLoop = new FRAbsLoop();
                    mFRAbsLoop.start();
                } else if(msg.arg1 == MSG_EVENT_MATCH) {
                    mTextView.setVisibility(View.VISIBLE);
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
        AFR_FSDKFace mResgist = null;

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
                AFR_FSDKError rError = engine.AFR_FSDK_ExtractFRFeature(mImageNV21, ImageProc.IMG_WIDTH, ImageProc.IMG_HEIGHT, AFR_FSDKEngine.CP_PAF_NV21, mAFT_FSDKFace.getRect(), mAFT_FSDKFace.getDegree(), result);
                Log.e(TAG, "AFR_FSDK_ExtractFRFeature cost :" + (System.currentTimeMillis() - time) + "ms");
                Log.e(TAG, "Face=" + result.getFeatureData()[0] + "," + result.getFeatureData()[1] + "," + result.getFeatureData()[2] + "," + rError.getCode());
                AFR_FSDKMatching score = new AFR_FSDKMatching();
                rError = engine.AFR_FSDK_FacePairMatching(result, mAFR_FSDKFace, score);
                Log.e(TAG, "Score:" + score.getScore() + ", mAFR_FSDKFace=" + (mAFR_FSDKFace == null) + ", AFR_FSDK_FacePairMatching=" + rError.getCode());
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
