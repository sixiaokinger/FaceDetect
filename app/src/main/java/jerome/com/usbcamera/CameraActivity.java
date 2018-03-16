package jerome.com.usbcamera;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;

import java.io.IOException;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener {

    private ImageButton mCaptureButton;
    private CameraView mUsbCamera;

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

        mUsbCamera = (CameraView) findViewById(R.id.camera_view);
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.capture_btn:
                mUsbCamera.capturePicture();
                break;
        }
    }
}
