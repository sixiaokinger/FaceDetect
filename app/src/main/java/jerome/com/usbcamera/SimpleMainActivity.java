package jerome.com.usbcamera;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
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
import com.dk.bleNfc.Tool.StringTool;
import com.guo.android_extend.image.ImageConverter;
import com.guo.android_extend.java.AbsLoop;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jerome.com.usbcamera.bleNfc.BleManager.BleManager;
import jerome.com.usbcamera.bleNfc.BleManager.ScannerCallback;
import jerome.com.usbcamera.bleNfc.BleNfcDeviceService;
import jerome.com.usbcamera.bleNfc.DeviceManager.BleNfcDevice;
import jerome.com.usbcamera.bleNfc.DeviceManager.ComByteManager;
import jerome.com.usbcamera.bleNfc.DeviceManager.DeviceManager;
import jerome.com.usbcamera.bleNfc.DeviceManager.DeviceManagerCallback;
import jerome.com.usbcamera.bleNfc.Exception.CardNoResponseException;
import jerome.com.usbcamera.bleNfc.Exception.DeviceNoResponseException;
import jerome.com.usbcamera.bleNfc.card.Iso14443bCard;
import jerome.com.usbcamera.utils.FaceDB;
import jerome.com.usbcamera.utils.FaceDetectView;
import jerome.com.usbcamera.utils.ImageProc;
import jerome.com.usbcamera.utils.SimpleStorage;

/**
 * Created by yanbo on 2018/3/22.
 */

public class SimpleMainActivity extends Activity implements FaceDetectView.OnPictureSaved, FaceDetectView.OnCameraListener, View.OnClickListener {

    final private String TAG = this.getClass().toString();
    private final static int MSG_REFREASH_TEXT = 0x0000;
    private final static int MSG_CODE = 0x1000;
    private final static int MSG_EVENT_REG = 0x1001;
    private final static int MSG_EVENT_MATCH = 0x1002;
    private final static int MSG_EVENT_START = 0x1003;
    private final static int MSG_EVENT_CATCH_CARD = 0x1004;
    private final static int MSG_NFC_CONNECTED = 0x2000;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int REQUEST_CODE_IMAGE_OP = 2;

    private UIHandler mUIHandler = new UIHandler();
    private FRAbsLoop mFRAbsLoop = new FRAbsLoop();
    private SimpleStorage mData = null;

    private FaceDetectView mUsbCamera;
    private ImageView mImgCertificate;
    private ImageView mImgMatched;
    private int mGMCount = 0;
    private boolean mIsGM = false;

    private ImageButton mBtnRegister;
    private ImageButton mBtnRegFace;
    private ImageButton mBtnRegCard;
    private TextView mTextInfo;
    private ImageButton mBtnGM;
    StringBuffer debugInfo = new StringBuffer();

    private AFT_FSDKVersion tVersion = new AFT_FSDKVersion();
    private AFT_FSDKEngine tEngine = new AFT_FSDKEngine();
    private List<AFT_FSDKFace> tFaces = new ArrayList<AFT_FSDKFace>();

    private Bitmap mBitmap = null;
    private byte[] mImageNV21 = null;
    private AFR_FSDKFace rFace = null;
    private AFT_FSDKFace tFace = null;

    private boolean mGetCertificate = false;
    private boolean mMatched = false;

    private String mCardId = null;
    private String mName = null;

    private BleNfcDevice mBleNfcDevice;
    private jerome.com.usbcamera.bleNfc.BleManager.Scanner mScanner;
    private BluetoothDevice mNearestBle = null;
    private Lock mNearestBleLock = new ReentrantLock();
    private int lastRssi = -100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);

        this.setContentView(R.layout.simple_main);
        mUsbCamera = (FaceDetectView) findViewById(R.id.detect_view);
        mUsbCamera.setCameraListener(this);
        mUsbCamera.setSavedCallback(this);

        mImgCertificate = (ImageView) findViewById(R.id.img_certificate);
        mImgCertificate.setVisibility(View.INVISIBLE);
        mImgMatched = (ImageView) findViewById(R.id.img_matched);
        mImgMatched.setVisibility(View.INVISIBLE);

        mBtnRegister = (ImageButton) findViewById(R.id.btn_register);
        mBtnRegister.setOnClickListener(this);
        mBtnRegister.setVisibility(View.INVISIBLE);
        mBtnRegFace = (ImageButton) findViewById(R.id.btn_reg_face);
        mBtnRegFace.setOnClickListener(this);
        mBtnRegFace.setVisibility(View.INVISIBLE);
        mBtnRegCard = (ImageButton) findViewById(R.id.btn_reg_card);
        mBtnRegCard.setOnClickListener(this);
        mBtnRegCard.setVisibility(View.INVISIBLE);
        mTextInfo = (TextView) findViewById(R.id.text_info);
        mTextInfo.setVisibility(View.INVISIBLE);

        mBtnGM = (ImageButton) findViewById(R.id.btn_gm);
        mBtnGM.setOnClickListener(this);
        mGMCount = 0;

        AFT_FSDKError tError = tEngine.AFT_FSDK_InitialFaceEngine(FaceDB.appid, FaceDB.ft_key, AFT_FSDKEngine.AFT_OPF_0_HIGHER_EXT, 16, 5);
        Log.d(TAG, "onCreate: AFT_FSDK_InitialFaceEngine = " + tError.getCode());
        tError = tEngine.AFT_FSDK_GetVersion(tVersion);
        Log.d(TAG, "onCreate: AFT_FSDK_GetVersion " +  tVersion.toString());

        mMatched = false;
        mGetCertificate = false;

        mData = new SimpleStorage(this.getExternalCacheDir().getPath(), (Application)(this.getApplicationContext()));

        // nfc init
//        if (!gpsIsOpen(SimpleMainActivity.this)) {
//            final AlertDialog.Builder normalDialog = new AlertDialog.Builder(SimpleMainActivity.this);
//            normalDialog.setTitle("请打开GPS");
//            normalDialog.setMessage("搜索蓝牙需要打开GPS");
//            normalDialog.setPositiveButton("确定", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int which) {
//                    Intent intent = new Intent(Settings.ACTION_LOCALE_SETTINGS);
//                    startActivityForResult(intent, 0);
//                }
//            });
//            normalDialog.setNegativeButton("关闭", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int which) {
//
//                }
//            });
//            normalDialog.show();
//        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
            }
        }

        Intent gattServiceIntent = new Intent(this, BleNfcDeviceService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unbindService(mServiceConnection);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_IMAGE_OP && resultCode == RESULT_OK) {
            Uri mPath = data.getData();
            String file = getPath(mPath);
            Bitmap bmp = Application.decodeImage(file);
            File f = new File(file);
            mName = f.getName();

            Bitmap ret = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight() / 2);

            mImgCertificate.setImageBitmap(ret);
            mImgCertificate.setVisibility(View.VISIBLE);
        }
    }

    private String getPath(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (DocumentsContract.isDocumentUri(this, uri)) {
                // ExternalStorageProvider
                if (isExternalStorageDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];

                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + "/" + split[1];
                    }

                    // TODO handle non-primary volumes
                } else if (isDownloadsDocument(uri)) {

                    final String id = DocumentsContract.getDocumentId(uri);
                    final Uri contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                    return getDataColumn(this, contentUri, null, null);
                } else if (isMediaDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];

                    Uri contentUri = null;
                    if ("image".equals(type)) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    } else if ("video".equals(type)) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    } else if ("audio".equals(type)) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    }

                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[] {
                            split[1]
                    };

                    return getDataColumn(this, contentUri, selection, selectionArgs);
                }
            }
        }
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor actualimagecursor = this.getContentResolver().query(uri, proj, null, null, null);
        int actual_image_column_index = actualimagecursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        actualimagecursor.moveToFirst();
        String img_path = actualimagecursor.getString(actual_image_column_index);
        String end = img_path.substring(img_path.length() - 4);
        if (0 != end.compareToIgnoreCase(".jpg") && 0 != end.compareToIgnoreCase(".png")) {
            return null;
        }
        return img_path;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BleNfcDeviceService mBleNfcDeviceService = ((BleNfcDeviceService.LocalBinder) service).getService();
            mBleNfcDevice = mBleNfcDeviceService.bleNfcDevice;
            mScanner = mBleNfcDeviceService.scanner;
            mBleNfcDeviceService.setDeviceManagerCallback(mDeviceManagerCallback);
            mBleNfcDeviceService.setScannerCallback(mScannerCallback);

            debugInfo.append("开始搜索设备\r\n");
            mUIHandler.sendEmptyMessage(MSG_REFREASH_TEXT);
            SearchNearestBleDevice();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private DeviceManagerCallback mDeviceManagerCallback = new DeviceManagerCallback() {
        @Override
        public void onReceiveConnectBtDevice(boolean blnIsConnectSuc) {
            super.onReceiveConnectBtDevice(blnIsConnectSuc);
            if (blnIsConnectSuc) {
                Log.d(TAG, "onReceiveConnectBtDevice: device connect successd");

                debugInfo.append("设备连接成功!\r\n");
                mUIHandler.sendEmptyMessage(MSG_REFREASH_TEXT);
                try {
                    Thread.sleep(500L);
                    mUIHandler.sendEmptyMessage(MSG_NFC_CONNECTED);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onReceiveDisConnectDevice(boolean blnIsDisConnectDevice) {
            super.onReceiveDisConnectDevice(blnIsDisConnectDevice);
        }

        @Override
        public void onReceiveConnectionStatus(boolean blnIsConnection) {
            super.onReceiveConnectionStatus(blnIsConnection);
        }

        @Override
        public void onReceiveInitCiphy(boolean blnIsInitSuc) {
            super.onReceiveInitCiphy(blnIsInitSuc);
        }

        @Override
        public void onReceiveDeviceAuth(byte[] authData) {
            super.onReceiveDeviceAuth(authData);
        }

        @Override
        public void onReceiveRfnSearchCard(boolean blnIsSus, final int cardType, byte[] bytCardSn, byte[] bytCarATS) {
            super.onReceiveRfnSearchCard(blnIsSus, cardType, bytCardSn, bytCarATS);
            if (!blnIsSus || cardType != BleNfcDevice.CARD_TYPE_ISO4443_B) {
                return;
            }
            debugInfo.append("检测到卡片\r\n");
            mUIHandler.sendEmptyMessage(MSG_REFREASH_TEXT);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean isReadWriteCardSuc;
                    try{
                        if (mBleNfcDevice.isAutoSearchCard()) {
                            mBleNfcDevice.stoptAutoSearchCard();
                            isReadWriteCardSuc = readWriteCard(cardType);
                            startAutoSearchCard();
                        }
                        else {
                            isReadWriteCardSuc = readWriteCard(cardType);
                            mBleNfcDevice.closeRf();
                        }

//                        if (isReadWriteCardSuc) {
//                            mBleNfcDevice.openBeep(50, 50, 3);
//                        }
//                        else {
//                            mBleNfcDevice.openBeep(100 ,100, 2);
//                        }

                    }
                    catch (DeviceNoResponseException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        @Override
        public void onReceiveRfmSentApduCmd(byte[] bytApduRtnData) {
            super.onReceiveRfmSentApduCmd(bytApduRtnData);
        }

        @Override
        public void onReceiveRfmSentBpduCmd(byte[] bytBpduRtnData) {
            super.onReceiveRfmSentBpduCmd(bytBpduRtnData);
        }

        @Override
        public void onReceiveRfmClose(boolean blnIsCloseSuc) {
            super.onReceiveRfmClose(blnIsCloseSuc);
        }

        @Override
        public void onReceiveRfmSuicaBalance(boolean blnIsSuc, byte[] bytBalance) {
            super.onReceiveRfmSuicaBalance(blnIsSuc, bytBalance);
        }

        @Override
        public void onReceiveRfmFelicaRead(boolean blnIsReadSuc, byte[] bytBlockData) {
            super.onReceiveRfmFelicaRead(blnIsReadSuc, bytBlockData);
        }

        @Override
        public void onReceiveRfmUltralightCmd(byte[] bytUlRtnData) {
            super.onReceiveRfmUltralightCmd(bytUlRtnData);
        }

        @Override
        public void onReceiveButtonEnter(byte keyValue) {
            super.onReceiveButtonEnter(keyValue);
        }
    };

    private ScannerCallback mScannerCallback = new ScannerCallback() {
        @Override
        public void onReceiveScanDevice(BluetoothDevice device, int rssi, byte[] scanRecord) {
            super.onReceiveScanDevice(device, rssi, scanRecord);

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                Log.d(TAG, "Activity搜到设备：" + device.getName()
                        + " 信号强度：" + rssi
                        + " scanRecord：" + StringTool.byteHexToSting(scanRecord));
            }

            if ((scanRecord != null)) {
                if (rssi < -55) {
                    return;
                }
                if (mNearestBle != null) {
                    if (rssi < lastRssi) {
                        return;
                    }
                }
                mNearestBleLock.lock();
                try {
                    mNearestBle = device;
                } finally {
                    mNearestBleLock.unlock();
                }
                lastRssi = rssi;
            }
        }

        @Override
        public void onScanDeviceStopped() {
            super.onScanDeviceStopped();
        }
    };

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
        switch (v.getId()) {
            case R.id.btn_register:
                // TODO: 2018/3/23 save info
                if (mNearestBle == null) {
                    SearchNearestBleDevice();
                    return;
                }

                mData.saveInfo(mName, mCardId, mImgCertificate.getDrawable(), rFace);

                break;
            case R.id.btn_reg_face:
                mUsbCamera.capturePicture();
                break;
            case R.id.btn_reg_card:
                Intent getImageByalbum = new Intent(Intent.ACTION_GET_CONTENT);
                getImageByalbum.addCategory(Intent.CATEGORY_OPENABLE);
                getImageByalbum.setType("image/jpeg");
                startActivityForResult(getImageByalbum, REQUEST_CODE_IMAGE_OP);
                break;
            case R.id.btn_gm:
                if (mGMCount++ > 4) {
                    mBtnRegister.setVisibility(View.VISIBLE);
                    mBtnRegFace.setVisibility(View.VISIBLE);
                    mBtnRegCard.setVisibility(View.VISIBLE);
                    mTextInfo.setVisibility(View.VISIBLE);
                    mIsGM = true;
                    mGMCount = 0;
                }
                break;
        }
    }

    private boolean startAutoSearchCard() throws DeviceNoResponseException {
        boolean isSuccess;
        int falseCnt = 0;
        do {
            isSuccess = mBleNfcDevice.startAutoSearchCard((byte) 20, ComByteManager.ISO14443_P4);
        }while (!isSuccess && (falseCnt++ < 10));

        if (!isSuccess) {
            debugInfo.append("不支持自动寻卡！\r\n");
            mUIHandler.sendEmptyMessage(MSG_REFREASH_TEXT);
        }
        return isSuccess;
    }

    private void SearchNearestBleDevice() {
        debugInfo.append("正在搜索设备\r\n");
        mUIHandler.sendEmptyMessage(MSG_REFREASH_TEXT);
        if (!mScanner.isScanning() && (mBleNfcDevice.isConnection() == BleManager.STATE_DISCONNECTED)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized (this) {
                        mScanner.startScan(0);
                        mNearestBleLock.lock();
                        try {
                            mNearestBle = null;
                        } finally {
                            mNearestBleLock.unlock();
                        }
                        lastRssi = -100;

                        int searchCnt = 0;
                        while ((mNearestBle == null)
                                && (searchCnt < 10000)
                                && (mScanner.isScanning())
                                && (mBleNfcDevice.isConnection() == BleManager.STATE_DISCONNECTED)) {
                            searchCnt++;
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        if (mScanner.isScanning() && (mBleNfcDevice.isConnection() == BleManager.STATE_DISCONNECTED)) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            mScanner.stopScan();
                            mNearestBleLock.lock();
                            try {
                                if (mNearestBle != null) {
                                    mScanner.stopScan();
                                    debugInfo.append("正在连接设备\r\n");
                                    mUIHandler.sendEmptyMessage(MSG_REFREASH_TEXT);
                                    mBleNfcDevice.requestConnectBleDevice(mNearestBle.getAddress());
                                }
                                else {
                                    debugInfo.append("未找到设备\r\n");
                                    mUIHandler.sendEmptyMessage(MSG_REFREASH_TEXT);
                                }
                            } finally {
                                mNearestBleLock.unlock();
                            }
                        }
                        else {
                            mScanner.stopScan();
                        }
                    }
                }
            }).start();
        }
    }

    public static final boolean gpsIsOpen(final Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (gps || network) {
            return true;
        }
        return false;
    }

    private boolean readWriteCard(int cardType) {
        switch (cardType) {
            case DeviceManager.CARD_TYPE_ISO4443_B:
                final Iso14443bCard iso14443bCard = (Iso14443bCard) mBleNfcDevice.getCard();
                if (iso14443bCard != null) {
                    //获取身份证UID的指令流
                    final byte[][] sfzCmdBytes = {
                            {0x00, (byte) 0x84, 0x00, 0x00, 0x08},
                            {0x00, 0x36, 0x00, 0x00, 0x08},
                    };
                    int count = 0;
                    debugInfo.append("寻到ISO14443-B卡->UID:(身份证发送0036000008指令获取UID)\r\n");
                    for (byte[] aBytes : sfzCmdBytes) {
                        try {
                            byte returnBytes[] = iso14443bCard.transceive(aBytes);
                            if (count++ > 0) {
                                onCardInfoCatch(StringTool.byteHexToSting(returnBytes));
                            }
                        } catch (CardNoResponseException e) {
                            e.printStackTrace();
                            return false;
                        }
                    }
                }
                break;
        }
        return false;
    }

    private void onCardInfoCatch(String uid) {
        debugInfo.append("读取卡片信息 " + uid + "\r\n");
        mUIHandler.sendEmptyMessage(MSG_REFREASH_TEXT);

        Log.d(TAG, "onCardInfoCatch: uid " + uid);

        mCardId = uid;
        mGetCertificate = true;

        SimpleStorage.FaceRegister data = mData.getDataByUid(uid);
        BitmapDrawable bd = (BitmapDrawable)data.card;
        Message reg = Message.obtain();
        reg.what = MSG_CODE;
        reg.arg1 = MSG_EVENT_CATCH_CARD;
        reg.obj = bd.getBitmap();
        mUIHandler.sendMessage(reg);

//        debugInfo.delete(0, debugInfo.length());
//        debugInfo.append(data.name + "\r\n" + data.uid + "\r\n");
//        mUIHandler.sendEmptyMessage(MSG_REFREASH_TEXT);


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
                ImageConverter converter = new ImageConverter();
                converter.initial(mBitmap.getWidth(), mBitmap.getHeight(), ImageConverter.CP_PAF_NV21);
                if (converter.convert(mBitmap, data)) {
                    Log.d(TAG, "run: convert ok");
                }
                converter.destroy();

                AFD_FSDKEngine dEngine = new AFD_FSDKEngine();
                AFD_FSDKVersion dVersion = new AFD_FSDKVersion();
                List<AFD_FSDKFace> dFaces = new ArrayList<AFD_FSDKFace>();
                AFD_FSDKError dError = dEngine.AFD_FSDK_InitialFaceEngine(FaceDB.appid, FaceDB.fd_key, AFD_FSDKEngine.AFD_OPF_0_HIGHER_EXT, 16, 5);
                Log.d(TAG, "AFD_FSDK_InitialFaceEngine = " + dError.getCode());
                dError = dEngine.AFD_FSDK_GetVersion(dVersion);
                Log.d(TAG, "AFD_FSDK_GetVersion = " + dError.getCode());
                dError = dEngine.AFD_FSDK_StillImageFaceDetection(data, mBitmap.getWidth(), mBitmap.getHeight(), AFD_FSDKEngine.CP_PAF_NV21, dFaces);
                Log.d(TAG, "AFD_FSDK_StillImageFaceDetection = " + dError.getCode() + "<" + dFaces.size());

                if (!dFaces.isEmpty()) {
                    AFR_FSDKVersion rVersion = new AFR_FSDKVersion();
                    AFR_FSDKEngine rEngine = new AFR_FSDKEngine();
                    AFR_FSDKError rError = rEngine.AFR_FSDK_InitialEngine(FaceDB.appid, FaceDB.fr_key);
                    AFR_FSDKFace result = new AFR_FSDKFace();
                    Log.d(TAG, "onPreview: AFR_FSDK_InitialEngine = " + rError.getCode());
                    rError = rEngine.AFR_FSDK_GetVersion(rVersion);
                    Log.d(TAG, "onPreview: AFR_FSDK_GetVersion = " + rVersion.getFeatureLevel());
                    rError = rEngine.AFR_FSDK_ExtractFRFeature(data, mBitmap.getWidth(), mBitmap.getHeight(), AFR_FSDKEngine.CP_PAF_NV21, new Rect(dFaces.get(0).getRect()), dFaces.get(0).getDegree(), result);
                    rFace = result.clone();
                    Log.d(TAG, "onPreview: AFR_FSDK_ExtractFRFeature = " + rError.getCode());
                    rError = rEngine.AFR_FSDK_UninitialEngine();
                    Log.d(TAG, "onPreview: AFR_FSDK_UninitialEngine = " + rError.getCode());

                    AFD_FSDKFace face = dFaces.get(0);
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

    class UIHandler extends Handler {
        @Override
        public void handleMessage(final Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_REFREASH_TEXT) {
                mTextInfo.setText(debugInfo);
            }
            else if (msg.what == MSG_CODE) {
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
                } else if (msg.arg1 == MSG_EVENT_CATCH_CARD) {
                    final Bitmap card = (Bitmap) msg.obj;
                    mImgCertificate.setImageBitmap(card);
                    mImgCertificate.setVisibility(View.VISIBLE);
                }
            }
            else if (msg.what == MSG_NFC_CONNECTED) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            byte versions = mBleNfcDevice.getDeviceVersions();
                            double voltage = mBleNfcDevice.getDeviceBatteryVoltage();
                            boolean isSuccess = mBleNfcDevice.androidFastParams(true);

                            startAutoSearchCard();
                        }
                        catch (DeviceNoResponseException e) {
                            e.printStackTrace();
                        }

                    }
                }).start();
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
                Log.d(TAG, "AFR_FSDK_ExtractFRFeature cost :" + (System.currentTimeMillis() - time) + "ms");
                Log.d(TAG, "Face=" + result.getFeatureData()[0] + "," + result.getFeatureData()[1] + "," + result.getFeatureData()[2] + "," + rError.getCode());
                AFR_FSDKMatching score = new AFR_FSDKMatching();
                rError = engine.AFR_FSDK_FacePairMatching(result, rFace, score);
                Log.d(TAG, "Score:" + score.getScore() + ", mAFR_FSDKFace=" + (rFace == null) + ", AFR_FSDK_FacePairMatching=" + rError.getCode());
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
