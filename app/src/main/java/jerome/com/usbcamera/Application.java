package jerome.com.usbcamera;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import jerome.com.usbcamera.utils.FaceDB;
import jerome.com.usbcamera.utils.LogCatHelper;
import jerome.com.usbcamera.utils.MyCrashHandler;

/**
 * Created by gqj3375 on 2017/4/28.
 */

public class Application extends android.app.Application {
	private final String TAG = this.getClass().toString();
	FaceDB mFaceDB;
	Uri mImage;

	@Override
	public void onCreate() {
		super.onCreate();
		mFaceDB = new FaceDB(this.getExternalCacheDir().getPath());
		mImage = null;

        MyCrashHandler myCrashHandler = MyCrashHandler.getInstance();
        myCrashHandler.init(getApplicationContext());

        LogCatHelper.getInstance(getApplicationContext(), "").start();
	}

	public void setCaptureImage(Uri uri) {
		mImage = uri;
	}

	public Uri getCaptureImage() {
		return mImage;
	}

	/**
	 * @param path
	 * @return
	 */
	public static Bitmap decodeImage(String path) {
		Bitmap res;
		try {
			ExifInterface exif = new ExifInterface(path);
			int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

			BitmapFactory.Options op = new BitmapFactory.Options();
			op.inSampleSize = 1;
			op.inJustDecodeBounds = false;
			//op.inMutable = true;
			res = BitmapFactory.decodeFile(path, op);
			//rotate and scale.
			Matrix matrix = new Matrix();

			if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
				matrix.postRotate(90);
			} else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
				matrix.postRotate(180);
			} else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
				matrix.postRotate(270);
			}

			Bitmap temp = Bitmap.createBitmap(res, 0, 0, res.getWidth(), res.getHeight(), matrix, true);
			Log.d("com.arcsoft", "check target Image:" + temp.getWidth() + "X" + temp.getHeight());

			if (!temp.equals(res)) {
				res.recycle();
			}
			return temp;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public void saveInfo(String key, String value) {
        Log.d(TAG, "saveInfo: " + key + " " + value);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
	    SharedPreferences.Editor editor = sharedPreferences.edit();
	    editor.putString(key, value);
	    editor.commit();
        Log.d(TAG, "saveInfo: " + sharedPreferences.getString(key, null));
    }

    public String getInfo(String key) {
        Log.d(TAG, "getInfo: " + key);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
	    return sharedPreferences.getString(key, null);
    }

    public void saveDrawable(String key, Bitmap bmp) {
        SharedPreferences sharedPreferences=PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor=sharedPreferences.edit();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 50, baos);
        String imageBase64 = new String(Base64.encodeToString(baos.toByteArray(),Base64.DEFAULT));
        editor.putString(key,imageBase64 );
        editor.commit();
    }

    public Drawable getDrawableByKey(String key) {
        SharedPreferences sharedPreferences=PreferenceManager.getDefaultSharedPreferences(this);
        String temp = sharedPreferences.getString(key, "");
        ByteArrayInputStream bais = new ByteArrayInputStream(Base64.decode(temp.getBytes(), Base64.DEFAULT));
        return Drawable.createFromStream(bais, "");
    }
}
