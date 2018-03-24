package jerome.com.usbcamera.utils;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.arcsoft.facerecognition.AFR_FSDKEngine;
import com.arcsoft.facerecognition.AFR_FSDKError;
import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKVersion;
import com.guo.android_extend.java.ExtInputStream;
import com.guo.android_extend.java.ExtOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jerome.com.usbcamera.Application;

/**
 * Created by yanbo on 2018/3/24.
 */

public class SimpleStorage {
    private final String TAG = this.getClass().toString();

    Application mApplication = null;

    AFR_FSDKEngine rEngine;
    AFR_FSDKVersion rVersion;
    boolean mUpdate;

    String mDBPath;
    public class FaceRegister {
        public String name;
        public String uid;
        public AFR_FSDKFace face;
        public Drawable card;
    }

    private List<FaceRegister> mRegister;

    public SimpleStorage(String path, Application application) {
        mApplication = application;
        mDBPath = path;
        mRegister = new ArrayList<>();
        rVersion = new AFR_FSDKVersion();
        rEngine = new AFR_FSDKEngine();
        mUpdate = false;
        AFR_FSDKError rError = rEngine.AFR_FSDK_InitialEngine(FaceDB.appid, FaceDB.fr_key);
        if (rError.getCode() != AFR_FSDKError.MOK) {
            Log.e(TAG, "AFR_FSDK_InitialEngine fail! error code :" + rError.getCode());
        } else {
            rEngine.AFR_FSDK_GetVersion(rVersion);
            Log.d(TAG, "AFR_FSDK_GetVersion=" + rVersion.toString());
        }

        if (loadInfo()) {
            loadFaces();
            loadDetail();
        }
    }

    public boolean saveInfo(String name, String uid, Drawable card, AFR_FSDKFace face) {
        FaceRegister one = new FaceRegister();
        one.name = name;
        one.uid = uid;
        one.card = card;
        one.face = face;
        mRegister.add(one);

        Log.d(TAG, "saveInfo: " + name + uid);

        mApplication.saveInfo(name + "_uid", uid);
        BitmapDrawable bd = (BitmapDrawable)card;
        mApplication.saveDrawable(name + "_card", bd.getBitmap());

        try {
            FileOutputStream fs = new FileOutputStream(mDBPath + File.separator + name + ".data");
            ExtOutputStream bos = new ExtOutputStream(fs);
            bos.writeBytes(face.getFeatureData());
            bos.close();
            fs.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    private boolean loadInfo() {
        if (!mRegister.isEmpty()) {
            return false;
        }
        File dir = new File(mDBPath);
        File[] files = dir.listFiles();
        for (File one : files) {
            Log.d(TAG, "loadInfo: " + one.getName());
            FaceRegister reg = new FaceRegister();
            reg.name = one.getName().substring(0, one.getName().length() - 5);
            mRegister.add(reg);
        }

        return true;
    }

    private boolean loadFaces() {
        try {
            for (FaceRegister face : mRegister) {
                Log.d(TAG, "loadFaces: " + face.name);
                FileInputStream fs = new FileInputStream(mDBPath + File.separator + face.name + ".data");
                ExtInputStream bos = new ExtInputStream(fs);
                AFR_FSDKFace rFace = null;
                do {
                    if (rFace != null) {
                        if (mUpdate) {}
                        face.face = rFace;
                    }
                    rFace = new AFR_FSDKFace();
                } while (bos.readBytes(rFace.getFeatureData()));
                bos.close();
                fs.close();
                Log.d(TAG, "loadFaces: load end " + face.name);
            }
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean loadDetail() {
        for (FaceRegister one : mRegister) {
            Log.d(TAG, "loadDetail: " + one.name);
            one.uid = mApplication.getInfo(one.name + "_uid");
            one.card = mApplication.getDrawableByKey(one.name + "_card");
            Log.d(TAG, "loadDetail: end " + one.uid);
        }
        return true;
    }

    public FaceRegister getDataByUid(String uid) {
        for (FaceRegister one : mRegister) {
            if (one.uid.equals(uid)){
                return one;
            }
        }
        return null;
    }
}
