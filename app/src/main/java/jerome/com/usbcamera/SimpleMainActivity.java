package jerome.com.usbcamera;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

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
import com.guo.android_extend.java.AbsLoop;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jerome.com.bleNfc.BleManager.BleManager;
import jerome.com.bleNfc.BleManager.ScannerCallback;
import jerome.com.bleNfc.BleNfcDeviceService;
import jerome.com.bleNfc.DeviceManager.BleNfcDevice;
import jerome.com.bleNfc.DeviceManager.ComByteManager;
import jerome.com.bleNfc.DeviceManager.DeviceManager;
import jerome.com.bleNfc.DeviceManager.DeviceManagerCallback;
import jerome.com.bleNfc.Exception.CardNoResponseException;
import jerome.com.bleNfc.Exception.DeviceNoResponseException;
import jerome.com.bleNfc.card.Iso14443bCard;

/**
 * Created by yanbo on 2018/3/22.
 */

public class SimpleMainActivity extends Activity implements FaceDetectView.OnCameraListener, View.OnClickListener {

    final private String TAG = this.getClass().toString();
    private final static int MSG_REFREASH_TEXT = 0x0000;
    private final static int MSG_CODE = 0x1000;
    private final static int MSG_EVENT_REG = 0x1001;
    private final static int MSG_EVENT_MATCH = 0x1002;
    private final static int MSG_EVENT_START = 0x1003;
    private final static int MSG_NFC_CONNECTED = 0x2000;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private UIHandler mUIHandler = new UIHandler();
    private FRAbsLoop mFRAbsLoop = new FRAbsLoop();

    private FaceDetectView mUsbCamera;
    private ImageView mImgCertificate;
    private ImageView mImgMatched;
    private ImageButton mBtnRegister;
    private TextView mTextInfo;
    StringBuffer debugInfo = new StringBuffer();

    private AFT_FSDKVersion tVersion = new AFT_FSDKVersion();
    private AFT_FSDKEngine tEngine = new AFT_FSDKEngine();
    private List<AFT_FSDKFace> tFaces = new ArrayList<AFT_FSDKFace>();

    private byte[] mImageNV21 = null;
    private AFR_FSDKFace rFace = null;
    private AFT_FSDKFace tFace = null;

    private boolean mGetCertificate = false;
    private boolean mMatched = false;

    private BleNfcDevice mBleNfcDevice;
    private jerome.com.bleNfc.BleManager.Scanner mScanner;
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

        mImgCertificate = (ImageView) findViewById(R.id.img_certificate);
        mImgCertificate.setVisibility(View.INVISIBLE);
        mImgMatched = (ImageView) findViewById(R.id.img_matched);
        mImgMatched.setVisibility(View.INVISIBLE);
        mBtnRegister = (ImageButton) findViewById(R.id.btn_register);
        mBtnRegister.setOnClickListener(this);
        mBtnRegister.setVisibility(View.VISIBLE);
        mTextInfo = (TextView) findViewById(R.id.text_info);

        AFT_FSDKError tError = tEngine.AFT_FSDK_InitialFaceEngine(FaceDB.appid, FaceDB.ft_key, AFT_FSDKEngine.AFT_OPF_0_HIGHER_EXT, 16, 5);
        Log.d(TAG, "onCreate: AFT_FSDK_InitialFaceEngine = " + tError.getCode());
        tError = tEngine.AFT_FSDK_GetVersion(tVersion);
        Log.d(TAG, "onCreate: AFT_FSDK_GetVersion " +  tVersion.toString());

        mMatched = false;
        mGetCertificate = false;

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
        if (v.getId() == R.id.btn_register) {
            //todo: enter register activity
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
                                && (searchCnt < 1000)
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
                    debugInfo.append("寻到ISO14443-B卡->UID:(身份证发送0036000008指令获取UID)\r\n");
                    for (byte[] aBytes : sfzCmdBytes) {
                        try {
                            byte returnBytes[] = iso14443bCard.transceive(aBytes);

                            debugInfo.append("读取卡片信息 " + StringTool.byteHexToSting(returnBytes) + "\r\n");
                            mUIHandler.sendEmptyMessage(MSG_REFREASH_TEXT);
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
