package jerome.com.usbcamera;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.arcsoft.ageestimation.ASAE_FSDKAge;
import com.arcsoft.ageestimation.ASAE_FSDKEngine;
import com.arcsoft.ageestimation.ASAE_FSDKError;
import com.arcsoft.ageestimation.ASAE_FSDKFace;
import com.arcsoft.ageestimation.ASAE_FSDKVersion;
import com.arcsoft.facerecognition.AFR_FSDKEngine;
import com.arcsoft.facerecognition.AFR_FSDKError;
import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKMatching;
import com.arcsoft.facerecognition.AFR_FSDKVersion;
import com.arcsoft.facetracking.AFT_FSDKEngine;
import com.arcsoft.facetracking.AFT_FSDKError;
import com.arcsoft.facetracking.AFT_FSDKFace;
import com.arcsoft.facetracking.AFT_FSDKVersion;
import com.arcsoft.genderestimation.ASGE_FSDKEngine;
import com.arcsoft.genderestimation.ASGE_FSDKError;
import com.arcsoft.genderestimation.ASGE_FSDKFace;
import com.arcsoft.genderestimation.ASGE_FSDKGender;
import com.arcsoft.genderestimation.ASGE_FSDKVersion;
import com.guo.android_extend.java.AbsLoop;
import com.guo.android_extend.java.ExtByteArrayOutputStream;
import com.guo.android_extend.tools.CameraHelper;
import com.guo.android_extend.widget.CameraSurfaceView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by gqj3375 on 2017/4/28.
 */

public class DetecterActivity extends Activity implements View.OnTouchListener, Camera.AutoFocusCallback, View.OnClickListener, FaceDetectView.OnCameraListener {
	private final String TAG = this.getClass().getSimpleName();

	private CameraSurfaceView mSurfaceView;
	private FaceDetectView mFaceDetectView;
	private Camera mCamera;

	AFT_FSDKVersion version = new AFT_FSDKVersion();
	AFT_FSDKEngine engine = new AFT_FSDKEngine();
	ASAE_FSDKVersion mAgeVersion = new ASAE_FSDKVersion();
	ASAE_FSDKEngine mAgeEngine = new ASAE_FSDKEngine();
	ASGE_FSDKVersion mGenderVersion = new ASGE_FSDKVersion();
	ASGE_FSDKEngine mGenderEngine = new ASGE_FSDKEngine();
	List<AFT_FSDKFace> result = new ArrayList<>();
	List<ASAE_FSDKAge> ages = new ArrayList<>();
	List<ASGE_FSDKGender> genders = new ArrayList<>();

	int mCameraID;
	int mCameraRotate;
	boolean mCameraMirror;
	byte[] mImageNV21 = null;
	FRAbsLoop mFRAbsLoop = null;
	AFT_FSDKFace mAFT_FSDKFace = null;
	Handler mHandler;

	Runnable hide = new Runnable() {
		@Override
		public void run() {
			mTextView.setAlpha(0.5f);
			mImageView.setImageAlpha(128);
		}
	};

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.main_back) {
            onBackPressed();
        }
    }

    class FRAbsLoop extends AbsLoop {

		AFR_FSDKVersion version = new AFR_FSDKVersion();
		AFR_FSDKEngine engine = new AFR_FSDKEngine();
		AFR_FSDKFace result = new AFR_FSDKFace();
		List<FaceDB.FaceRegist> mResgist = ((Application)DetecterActivity.this.getApplicationContext()).mFaceDB.mRegister;
		List<ASAE_FSDKFace> face1 = new ArrayList<>();
		List<ASGE_FSDKFace> face2 = new ArrayList<>();
		
		@Override
		public void setup() {
			AFR_FSDKError error = engine.AFR_FSDK_InitialEngine(FaceDB.appid, FaceDB.fr_key);
			Log.d(TAG, "AFR_FSDK_InitialEngine = " + error.getCode());
			error = engine.AFR_FSDK_GetVersion(version);
			Log.d(TAG, "FR=" + version.toString() + "," + error.getCode()); //(210, 178 - 478, 446), degree = 1　780, 2208 - 1942, 3370
		}

		@Override
		public void loop() {
			if (mImageNV21 != null) {
				long time = System.currentTimeMillis();
				AFR_FSDKError error = engine.AFR_FSDK_ExtractFRFeature(mImageNV21, ImageProc.IMG_WIDTH, ImageProc.IMG_HEIGHT, AFR_FSDKEngine.CP_PAF_NV21, mAFT_FSDKFace.getRect(), mAFT_FSDKFace.getDegree(), result);
				Log.d(TAG, "AFR_FSDK_ExtractFRFeature cost :" + (System.currentTimeMillis() - time) + "ms");
				Log.d(TAG, "Face=" + result.getFeatureData()[0] + "," + result.getFeatureData()[1] + "," + result.getFeatureData()[2] + "," + error.getCode());
				AFR_FSDKMatching score = new AFR_FSDKMatching();
				float max = 0.0f;
				String name = null;
				for (FaceDB.FaceRegist fr : mResgist) {
					for (AFR_FSDKFace face : fr.mFaceList) {
						error = engine.AFR_FSDK_FacePairMatching(result, face, score);
						Log.d(TAG,  "Score:" + score.getScore() + ", AFR_FSDK_FacePairMatching=" + error.getCode());
						if (max < score.getScore()) {
							max = score.getScore();
							name = fr.mName;

						}
					}
				}

				//age & gender
				face1.clear();
				face2.clear();
				face1.add(new ASAE_FSDKFace(mAFT_FSDKFace.getRect(), mAFT_FSDKFace.getDegree()));
				face2.add(new ASGE_FSDKFace(mAFT_FSDKFace.getRect(), mAFT_FSDKFace.getDegree()));
				ASAE_FSDKError error1 = mAgeEngine.ASAE_FSDK_AgeEstimation_Image(mImageNV21, ImageProc.IMG_WIDTH, ImageProc.IMG_HEIGHT, AFT_FSDKEngine.CP_PAF_NV21, face1, ages);
				ASGE_FSDKError error2 = mGenderEngine.ASGE_FSDK_GenderEstimation_Image(mImageNV21, ImageProc.IMG_WIDTH, ImageProc.IMG_HEIGHT, AFT_FSDKEngine.CP_PAF_NV21, face2, genders);
				Log.e(TAG, "ASAE_FSDK_AgeEstimation_Image:" + error1.getCode() + ",ASGE_FSDK_GenderEstimation_Image:" + error2.getCode());
				Log.e(TAG, "age:" + ages.get(0).getAge() + ",gender:" + genders.get(0).getGender());
				final String age = ages.get(0).getAge() == 0 ? "年龄未知" : ages.get(0).getAge() + "岁";
				final String gender = genders.get(0).getGender() == -1 ? "性别未知" : (genders.get(0).getGender() == 0 ? "男" : "女");

				test
				//crop
				byte[] data = mImageNV21;
				YuvImage yuv = new YuvImage(data, ImageFormat.NV21, ImageProc.IMG_WIDTH, ImageProc.IMG_HEIGHT, null);
				ExtByteArrayOutputStream ops = new ExtByteArrayOutputStream();
				yuv.compressToJpeg(mAFT_FSDKFace.getRect(), 80, ops);
				final Bitmap bmp = BitmapFactory.decodeByteArray(ops.getByteArray(), 0, ops.getByteArray().length);
				try {
					ops.close();
				} catch (IOException e) {
					e.printStackTrace();
				}

				if (max > 0.6f) {
					//fr success.
					final float max_score = max;
					Log.d(TAG, "fit Score:" + max + ", NAME:" + name);
					final String mNameShow = name;
					mHandler.removeCallbacks(hide);
					mHandler.post(new Runnable() {
						@Override
						public void run() {

							mTextView.setAlpha(1.0f);
							mTextView.setText(mNameShow);
							mTextView.setTextColor(Color.WHITE);
							mTextView1.setVisibility(View.VISIBLE);
							mTextView1.setText("相似度        " + (float)((int)(max_score * 1000)) / 10.0 + "%");
							mTextView1.setTextColor(Color.WHITE);
							mImageView.setRotation(mCameraRotate - 180);
							if (mCameraMirror) {
								mImageView.setScaleY(-1);
							}
							mImageView.setImageAlpha(255);
                            Drawable drawable = ((Application)DetecterActivity.this.getApplicationContext()).getDrawableByKey(mNameShow);
							mImageView.setImageDrawable(drawable);
						}
					});
				}
				else {
					final String mNameShow = "未识别";
					DetecterActivity.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mTextView.setAlpha(1.0f);
							mTextView1.setVisibility(View.VISIBLE);
							mTextView1.setText( "相似度             0%");
							mTextView1.setTextColor(Color.WHITE);
							mTextView.setText(mNameShow);
							mTextView.setTextColor(Color.WHITE);
							mImageView.setImageAlpha(255);
							mImageView.setRotation(mCameraRotate - 180);
							if (mCameraMirror) {
								mImageView.setScaleY(-1);
							}
							Resources res = getResources();
                            Bitmap bmp = BitmapFactory.decodeResource(res, R.drawable.nobody);
							mImageView.setImageBitmap(bmp);
						}
					});
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

	private TextView mTextView;
	private TextView mTextView1;
	private ImageView mImageView;
	private Button mButtonBack;

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

//		mCameraID = getIntent().getIntExtra("Camera", 0) == 0 ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
		mCameraRotate = getIntent().getIntExtra("Camera", 0) == 0 ? 180 : 0;
		mCameraMirror = getIntent().getIntExtra("Camera", 0) == 0 ? false : true;
		mHandler = new Handler();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_camera);
        mFaceDetectView = (FaceDetectView) findViewById(R.id.face_detect_view);
        mFaceDetectView.setOnTouchListener(this);
        mFaceDetectView.setCameraListener(this);
//		mSurfaceView = (CameraSurfaceView) findViewById(R.id.surfaceView);
//		mSurfaceView.setOnCameraListener(this);
//		mSurfaceView.setupGLSurafceView(mCameraView, true, mCameraMirror, mCameraRotate);
//		mSurfaceView.debug_print_fps(true, false);

		//snap
		mTextView = (TextView) findViewById(R.id.textView);
		mTextView.setText("");
		mTextView1 = (TextView) findViewById(R.id.textView1);
		mTextView1.setText("");

		mImageView = (ImageView) findViewById(R.id.imageView);
        mButtonBack = (Button) findViewById(R.id.main_back);
        mButtonBack.setOnClickListener(this);

		AFT_FSDKError err = engine.AFT_FSDK_InitialFaceEngine(FaceDB.appid, FaceDB.ft_key, AFT_FSDKEngine.AFT_OPF_0_HIGHER_EXT, 16, 5);
		Log.d(TAG, "AFT_FSDK_InitialFaceEngine =" + err.getCode());
		err = engine.AFT_FSDK_GetVersion(version);
		Log.d(TAG, "AFT_FSDK_GetVersion:" + version.toString() + "," + err.getCode());

		ASAE_FSDKError error = mAgeEngine.ASAE_FSDK_InitAgeEngine(FaceDB.appid, FaceDB.age_key);
		Log.d(TAG, "ASAE_FSDK_InitAgeEngine =" + error.getCode());
		error = mAgeEngine.ASAE_FSDK_GetVersion(mAgeVersion);
		Log.d(TAG, "ASAE_FSDK_GetVersion:" + mAgeVersion.toString() + "," + error.getCode());

		ASGE_FSDKError error1 = mGenderEngine.ASGE_FSDK_InitgGenderEngine(FaceDB.appid, FaceDB.gender_key);
		Log.d(TAG, "ASGE_FSDK_InitgGenderEngine =" + error1.getCode());
		error1 = mGenderEngine.ASGE_FSDK_GetVersion(mGenderVersion);
		Log.d(TAG, "ASGE_FSDK_GetVersion:" + mGenderVersion.toString() + "," + error1.getCode());

		mFRAbsLoop = new FRAbsLoop();
		mFRAbsLoop.start();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		mFRAbsLoop.shutdown();
		AFT_FSDKError err = engine.AFT_FSDK_UninitialFaceEngine();
		Log.d(TAG, "AFT_FSDK_UninitialFaceEngine =" + err.getCode());

		ASAE_FSDKError err1 = mAgeEngine.ASAE_FSDK_UninitAgeEngine();
		Log.d(TAG, "ASAE_FSDK_UninitAgeEngine =" + err1.getCode());

		ASGE_FSDKError err2 = mGenderEngine.ASGE_FSDK_UninitGenderEngine();
		Log.d(TAG, "ASGE_FSDK_UninitGenderEngine =" + err2.getCode());
	}

	@Override
	public Object onPreview(byte[] data, int width, int height) {
        AFT_FSDKError err = engine.AFT_FSDK_FaceFeatureDetect(data, width, height, AFT_FSDKEngine.CP_PAF_NV21, result);
//		Log.e(TAG, "AFT_FSDK_FaceFeatureDetect =" + err.getCode());
//		Log.e(TAG, "Face=" + result.size());
		for (AFT_FSDKFace face : result) {
			Log.d(TAG, "Face:" + face.toString());
		}
		if (mImageNV21 == null) {
			if (!result.isEmpty()) {
				mAFT_FSDKFace = result.get(0).clone();
				mImageNV21 = data.clone();
			} else {
				mHandler.postDelayed(hide, 3000);
			}
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
	public boolean onTouch(View v, MotionEvent event) {
		CameraHelper.touchFocus(mCamera, event, v, this);
		return false;
	}

	@Override
	public void onAutoFocus(boolean success, Camera camera) {
		if (success) {
			Log.d(TAG, "Camera Focus SUCCESS!");
		}
	}
}
