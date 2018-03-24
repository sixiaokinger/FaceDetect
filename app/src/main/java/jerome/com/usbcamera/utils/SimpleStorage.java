package jerome.com.usbcamera.utils;

import android.graphics.Bitmap;
import android.util.Log;

import com.arcsoft.facerecognition.AFR_FSDKEngine;
import com.arcsoft.facerecognition.AFR_FSDKError;
import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKVersion;
import com.guo.android_extend.java.ExtInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Enumeration;

/**
 * Created by yanbo on 2018/3/24.
 */

public class SimpleStorage {
    private final String TAG = this.getClass().toString();

    AFR_FSDKEngine rEngine;
    AFR_FSDKVersion rVersion;

    String mDBPath;
    public class FaceRegister {
        public String name;
        public String uid;
        public AFR_FSDKFace face;
        public Bitmap card;
    }

    private Dictionary<String, FaceRegister> mRegister;

    public SimpleStorage(String path) {
        mDBPath = path;
        mRegister = new Dictionary<String, FaceRegister>() {
            @Override
            public Enumeration<FaceRegister> elements() {
                return null;
            }

            @Override
            public FaceRegister get(Object key) {
                return null;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public Enumeration<String> keys() {
                return null;
            }

            @Override
            public FaceRegister put(String key, FaceRegister value) {
                return null;
            }

            @Override
            public FaceRegister remove(Object key) {
                return null;
            }

            @Override
            public int size() {
                return 0;
            }
        };
        rVersion = new AFR_FSDKVersion();
        rEngine = new AFR_FSDKEngine();
        AFR_FSDKError rError = rEngine.AFR_FSDK_InitialEngine(FaceDB.appid, FaceDB.fr_key);
        if (rError.getCode() != AFR_FSDKError.MOK) {
            Log.e(TAG, "AFR_FSDK_InitialEngine fail! error code :" + rError.getCode());
        } else {
            rEngine.AFR_FSDK_GetVersion(rVersion);
            Log.d(TAG, "AFR_FSDK_GetVersion=" + rVersion.toString());
        }
    }

    private  boolean loadInfo() {
        if (!mRegister.isEmpty()) {
            return false;
        }
        try {
            FileInputStream fs = new FileInputStream(mDBPath + File.separator + "face.txt");
            ExtInputStream bos = new ExtInputStream(fs);
            String version_saved = bos.readString();
            if (version_saved.equals(rVersion.toString())) {

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }
}
